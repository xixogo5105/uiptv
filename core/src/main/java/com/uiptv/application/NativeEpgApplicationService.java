package com.uiptv.application;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.util.*;
import com.uiptv.service.HandshakeService;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Provider schedules use stream/API identities; XMLTV association is resolved separately. */
public class NativeEpgApplicationService {
    private static final Object HANDSHAKE_LOCK = new Object();
    private static final Map<Key, Cached> CACHE = new LinkedHashMap<>();
    private static final Map<Key, java.util.concurrent.CompletableFuture<List<EpgProgramme>>> IN_FLIGHT = new HashMap<>();
    private static long cacheRevision;
    private static final Map<Key, Thread> OWNERS = new HashMap<>();
    private static final java.util.concurrent.Semaphore REQUESTS = new java.util.concurrent.Semaphore(2);
    private static final HttpUtil.RequestOptions OPTIONS = new HttpUtil.RequestOptions(true, true, 5, 5, 10)
            .withMaxBodyChars(10 * 1024 * 1024);
    private record Key(String account, AccountType type, String endpoint, String user, String session, String channel) { }
    private record Cached(List<EpgProgramme> programmes, long retryAt, long bytes) {
        Cached(List<EpgProgramme> programmes, long retryAt) {
            this(programmes, retryAt, 256L + programmes.stream()
                    .mapToLong(p -> 192L + 2L * (p.title().length() + p.description().length())).sum());
        }
    }

    public void clearCache() {
        synchronized (CACHE) {
            cacheRevision++; CACHE.clear();
            OWNERS.values().forEach(Thread::interrupt); OWNERS.clear();
            IN_FLIGHT.values().forEach(future -> future.complete(List.of())); IN_FLIGHT.clear();
        }
    }
    void expire(Account account,Channel channel) { synchronized(CACHE) { CACHE.remove(key(account,channel)); } }
    private Key key(Account account, Channel channel) {
        return new Key(account.getDbId(), account.getType(), account.getUrl() + "|" + account.getM3u8Path() + "|" + account.getServerPortalUrl() + "|epg-utc-v2",
                account.getUsername(), account.getType() == AccountType.STALKER_PORTAL ? account.getMacAddress() : account.getPassword(), channel.getChannelId());
    }
    public List<EpgProgramme> schedule(Account account, Channel channel) {
        if (account.getType() != AccountType.XTREME_API && account.getType() != AccountType.STALKER_PORTAL
                || channel.getChannelId() == null || channel.getChannelId().isBlank()) return List.of();
        Key key = key(account, channel);
        Cached previous;
        java.util.concurrent.CompletableFuture<List<EpgProgramme>> flight;
        boolean owner;
        long revision;
        long now = System.currentTimeMillis();
        synchronized (CACHE) {
            revision = cacheRevision;
            previous = CACHE.get(key);
            if (previous != null && now < previous.retryAt()) return previous.programmes();
            flight = IN_FLIGHT.get(key);
            owner = flight == null;
            if (owner) { flight = new java.util.concurrent.CompletableFuture<>(); IN_FLIGHT.put(key, flight); OWNERS.put(key, Thread.currentThread()); }
        }
        if (!owner) {
            try { return flight.get(25, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException _) { Thread.currentThread().interrupt(); return List.of(); }
            catch (Exception _) { return previous == null ? List.of() : previous.programmes(); }
        }
        boolean acquired = false;
        List<EpgProgramme> result = previous == null ? List.of() : previous.programmes();
        try {
            acquired = REQUESTS.tryAcquire(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!acquired || Thread.currentThread().isInterrupted()) return result;
            result = fetch(account, channel);
            boolean usable = EpgApplicationService.usable(result);
            if (!usable && previous != null && EpgApplicationService.usable(previous.programmes())) result = previous.programmes();
            synchronized (CACHE) {
                if (revision != cacheRevision) return List.of();
                CACHE.put(key, new Cached(result, now + 30 * 60_000L));
                long bytes = CACHE.values().stream().mapToLong(Cached::bytes).sum();
                var entries = CACHE.entrySet().iterator();
                while (entries.hasNext() && (CACHE.size() > 256 || bytes > 16L * 1024 * 1024)) {
                    bytes -= entries.next().getValue().bytes(); entries.remove();
                }
            }
            return result;
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt(); return result;
        } finally {
            if (acquired) REQUESTS.release();
            synchronized (CACHE) { IN_FLIGHT.remove(key, flight); OWNERS.remove(key, Thread.currentThread()); flight.complete(result); }
        }
    }
    private List<EpgProgramme> fetch(Account account, Channel channel) {
        try {
            if (!ensureSession(account)) return List.of();
            List<String> actions = account.getType() == AccountType.XTREME_API
                    ? List.of("get_simple_data_table", "get_short_epg") : List.of("get_short_epg", "get_epg_info");
            for (String action : actions) {
                if (Thread.currentThread().isInterrupted() || !new EpgApplicationService().isEnabled()) return List.of();
                try {
                    List<EpgProgramme> result = fetchAction(account, channel, action);
                    if (EpgApplicationService.usable(result)) return result;
                } catch (Exception _) {
                    AppLog.addWarningLog(NativeEpgApplicationService.class, "Provider EPG action unavailable; trying fallback.");
                }
            }
        } catch (Exception _) {
            AppLog.addWarningLog(NativeEpgApplicationService.class, "Provider EPG session unavailable; trying XMLTV.");
        }
        return List.of();
    }

    private boolean ensureSession(Account account) {
        if (account.getType() != AccountType.STALKER_PORTAL) return true;
        synchronized (HANDSHAKE_LOCK) {
            if (account.isNotConnected()) HandshakeService.getInstance().connect(account);
        }
        return !account.isNotConnected();
    }

    private List<EpgProgramme> fetchAction(Account account, Channel channel, String action) throws java.io.IOException {
        boolean xtreme = account.getType() == AccountType.XTREME_API;
        String body = xtreme ? XtremeApiParser.fetchEpg(account, action, channel.getChannelId())
                : fetchStalkerAction(account, channel, action);
        return parse(body, xtreme, "UTC", channel.getChannelId());
    }

    private String fetchStalkerAction(Account account, Channel channel, String action) {
        Map<String, String> params = Map.of("type", "itv", "action", action, "ch_id", channel.getChannelId(),
                "period", "24", "size", "100", "JsHttpRequest", "1-xml");
        var response = FetchAPI.fetchWithDiagnostics(params, utcRequestAccount(account), OPTIONS);
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            synchronized (HANDSHAKE_LOCK) { HandshakeService.getInstance().connect(account); }
            if (account.isNotConnected()) return "";
            response = FetchAPI.fetchWithDiagnostics(params, utcRequestAccount(account), OPTIONS);
        }
        return response.body();
    }

    private static Account utcRequestAccount(Account account) {
        Account request = Objects.requireNonNull(AccountCopyUtil.detachedCopy(account), "account copy");
        request.setTimezone("UTC");
        return request;
    }
    private static ZoneId sourceZone(Object data, ZoneId fallback) {
        if (!(data instanceof JSONObject object)) return fallback;
        String name = object.optString("timezone", "");
        JSONObject server = object.optJSONObject("server_info");
        if (server != null) name = server.optString("timezone", name);
        try { return name.isBlank() ? fallback : ZoneId.of(name); }
        catch (DateTimeException _) { return fallback; }
    }

    private record ParsedPayload(Object data, ZoneId zone) { }
    private record ParseContext(boolean encoded, ZoneId zone, String channelId, long now) { }

    static List<EpgProgramme> parse(String json, boolean encoded, String sourceTimezone, String channelId) {
        if (json == null || json.isBlank() || json.length() > 10 * 1024 * 1024) return List.of();
        try {
            Object data = new JSONTokener(json).nextValue();
            ZoneId zone;
            try { zone = ZoneId.of(sourceTimezone == null || sourceTimezone.isBlank() ? "UTC" : sourceTimezone); }
            catch (Exception _) { zone = ZoneOffset.UTC; }
            ParsedPayload payload = unwrapPayload(data, zone, channelId);
            if (!(payload.data() instanceof JSONArray array)) return List.of();
            return parseEntries(array, new ParseContext(encoded, payload.zone(), channelId, System.currentTimeMillis()));
        } catch (Exception _) { return List.of(); }
    }

    private static ParsedPayload unwrapPayload(Object data, ZoneId zone, String channelId) {
        zone = sourceZone(data, zone);
        for (int depth = 0; depth < 5 && data instanceof JSONObject object; depth++) {
            zone = sourceZone(object, zone);
            if (object.has("js")) data = object.opt("js");
            else if (object.has("epg_listings")) data = object.opt("epg_listings");
            else if (object.has("data")) data = object.opt("data");
            else if (object.has(channelId)) data = object.opt(channelId);
            else return new ParsedPayload(List.of(), zone);
        }
        return new ParsedPayload(data, zone);
    }

    private static List<EpgProgramme> parseEntries(JSONArray array, ParseContext context) {
        List<EpgProgramme> result = new ArrayList<>();
        for (int i = 0; i < Math.min(array.length(), 2000); i++) {
            JSONObject programme = array.optJSONObject(i);
            EpgProgramme parsed = parseEntry(programme, context);
            if (parsed != null) result.add(parsed);
        }
        result.sort(Comparator.comparingLong(EpgProgramme::start));
        return EpgSchedules.normalize(result);
    }

    private static EpgProgramme parseEntry(JSONObject programme, ParseContext context) {
        if (programme == null) return null;
        String associated = first(programme, "ch_id", "channel_id", "stream_id");
        if (!associated.isBlank() && !associated.equals(context.channelId())) return null;
        ZoneId zone = sourceZone(programme, context.zone());
        long start = programmeTime(programme, zone, "start_timestamp", "start", "time", "start_time", "start_datetime");
        long stop = programmeTime(programme, zone, "stop_timestamp", "end_timestamp", "end", "stop", "end_time", "time_to", "stop_datetime");
        if (stop == 0 && start > 0) stop = durationStop(programme, start);
        String title = truncate(text(first(programme, "title", "name"), context.encoded()), 512);
        if (start <= context.now() - 86400_000L || start >= context.now() + 7 * 86400_000L
                || stop <= start || title.isBlank()) return null;
        return new EpgProgramme(title, truncate(text(first(programme, "description", "descr", "desc"), context.encoded()), 2048), start, stop);
    }

    private static long durationStop(JSONObject programme, long start) {
        long duration = programme.optLong("duration", 0);
        return duration > 0 && duration < 86400 ? start + duration * 1000 : 0;
    }

    private static long programmeTime(JSONObject programme, ZoneId zone, String... fields) {
        for (String field : fields) {
            long value = time(programme.optString(field, ""), zone);
            if (value > 0) return value;
        }
        return 0;
    }

    private static String truncate(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    private static String first(JSONObject p, String... fields) {
        for (String field : fields) { String value = p.optString(field, ""); if (!value.isBlank()) return value; }
        return "";
    }
    private static String text(String value, boolean encoded) {
        if (encoded && !value.isBlank()) {
            try { byte[] bytes = Base64.getDecoder().decode(value);
                return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString(); }
            catch (Exception _) { /* Compatibility with providers returning plain text. */ }
        }
        return value;
    }
    static long time(String value, ZoneId zone) {
        if (value == null || value.isBlank()) return 0;
        value = value.strip();
        try { long n = Long.parseLong(value); return n < 100_000_000_000L ? Math.multiplyExact(n, 1000) : n; }
        catch (Exception _) { /* Try the next supported timestamp representation. */ }
        try { return Instant.parse(value).toEpochMilli(); } catch (Exception _) { /* Try the next representation. */ }
        try { return OffsetDateTime.parse(value.replace(' ', 'T')).toInstant().toEpochMilli(); } catch (Exception _) { /* Try local time next. */ }
        try { return LocalDateTime.parse(value.replace(' ', 'T'), DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(zone).toInstant().toEpochMilli(); }
        catch (Exception _) { return 0; }
    }
}
