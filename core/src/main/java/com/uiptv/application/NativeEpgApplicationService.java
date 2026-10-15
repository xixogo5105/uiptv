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
    private static final Map<Key, Cached> CACHE = new LinkedHashMap<>();
    private static final Map<Key, java.util.concurrent.CompletableFuture<List<EpgProgramme>>> IN_FLIGHT = new HashMap<>();
    private static long cacheRevision;
    private static final Map<Key, Thread> OWNERS = new HashMap<>();
    private static final java.util.concurrent.Semaphore REQUESTS = new java.util.concurrent.Semaphore(2);
    private static final HttpUtil.RequestOptions OPTIONS = new HttpUtil.RequestOptions(true, true, 5, 5, 10);
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
        return new Key(account.getDbId(), account.getType(), account.getUrl() + "|" + account.getM3u8Path() + "|" + account.getServerPortalUrl(),
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
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return List.of(); }
            catch (Exception e) { return previous == null ? List.of() : previous.programmes(); }
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); return result;
        } finally {
            if (acquired) REQUESTS.release();
            synchronized (CACHE) { IN_FLIGHT.remove(key, flight); OWNERS.remove(key, Thread.currentThread()); flight.complete(result); }
        }
    }
    private List<EpgProgramme> fetch(Account account, Channel channel) {
        try {
            if (account.getType() == AccountType.STALKER_PORTAL) {
                synchronized (account) {
                    if (account.isNotConnected()) HandshakeService.getInstance().connect(account);
                }
                if (account.isNotConnected()) return List.of();
            }
            List<String> actions = account.getType() == AccountType.XTREME_API
                    ? List.of("get_simple_data_table", "get_short_epg") : List.of("get_short_epg", "get_epg_info");
            for (String action : actions) {
                if (Thread.currentThread().isInterrupted() || !new EpgApplicationService().isEnabled()) return List.of();
                try {
                    String body;
                    if (account.getType() == AccountType.XTREME_API) body = XtremeApiParser.fetchEpg(account, action, channel.getChannelId());
                    else {
                        Map<String, String> params = Map.of("type", "itv", "action", action, "ch_id", channel.getChannelId(),
                                "period", "24", "size", "100", "JsHttpRequest", "1-xml");
                        var response = FetchAPI.fetchWithDiagnostics(params, account, OPTIONS);
                        if (response.statusCode() == 401 || response.statusCode() == 403) {
                            synchronized (account) { HandshakeService.getInstance().connect(account); }
                            if (account.isNotConnected()) return List.of();
                            response = FetchAPI.fetchWithDiagnostics(params, account, OPTIONS);
                        }
                        body = response.body();
                    }
                    List<EpgProgramme> result = parse(body, account.getType() == AccountType.XTREME_API, account.getTimezone(), channel.getChannelId());
                    if (EpgApplicationService.usable(result)) return result;
                } catch (Exception ignored) {
                    AppLog.addWarningLog(NativeEpgApplicationService.class, "Provider EPG action unavailable; trying fallback.");
                }
            }
        } catch (Exception ignored) {
            AppLog.addWarningLog(NativeEpgApplicationService.class, "Provider EPG session unavailable; trying XMLTV.");
        }
        return List.of();
    }

    static List<EpgProgramme> parse(String json, boolean encoded, String timezone, String channelId) {
        if (json == null || json.isBlank() || json.length() > 10 * 1024 * 1024) return List.of();
        try {
            Object data = new JSONTokener(json).nextValue();
            for (int depth = 0; depth < 5 && data instanceof JSONObject object; depth++) {
                if (object.has("js")) data = object.opt("js");
                else if (object.has("epg_listings")) data = object.opt("epg_listings");
                else if (object.has("data")) data = object.opt("data");
                else if (object.has(channelId)) data = object.opt(channelId);
                else return List.of();
            }
            if (!(data instanceof JSONArray array)) return List.of();
            ZoneId zone;
            try { zone = ZoneId.of(timezone == null || timezone.isBlank() ? "UTC" : timezone); }
            catch (Exception ignored) { zone = ZoneOffset.UTC; }
            List<EpgProgramme> result = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (int i = 0; i < Math.min(array.length(), 2000); i++) {
                JSONObject p = array.optJSONObject(i);
                if (p == null) continue;
                String associated = first(p, "ch_id", "channel_id", "stream_id");
                if (!associated.isBlank() && !associated.equals(channelId)) continue;
                long start = time(first(p, "start_timestamp", "start", "time", "start_time"), zone);
                long stop = time(first(p, "stop_timestamp", "end_timestamp", "end", "stop", "end_time"), zone);
                if (stop == 0 && start > 0) {
                    long duration = p.optLong("duration", 0);
                    if (duration > 0 && duration < 86400) stop = start + duration * 1000;
                }
                String title = truncate(text(first(p, "title", "name"), encoded), 512);
                if (start > now - 86400_000L && start < now + 7 * 86400_000L && stop > start && !title.isBlank())
                    result.add(new EpgProgramme(title, truncate(text(first(p, "description", "descr", "desc"), encoded), 2048), start, stop));
            }
            result.sort(Comparator.comparingLong(EpgProgramme::start));
            return EpgSchedules.normalize(result);
        } catch (Exception ignored) { return List.of(); }
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
            catch (Exception ignored) { /* Compatibility with providers returning plain text. */ }
        }
        return value;
    }
    static long time(String value, ZoneId zone) {
        if (value == null || value.isBlank()) return 0;
        try { long n = Long.parseLong(value); return n < 100_000_000_000L ? Math.multiplyExact(n, 1000) : n; }
        catch (Exception ignored) { }
        try { return Instant.parse(value).toEpochMilli(); } catch (Exception ignored) { }
        try { return OffsetDateTime.parse(value.replace(' ', 'T')).toInstant().toEpochMilli(); } catch (Exception ignored) { }
        try { return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")).atZone(zone).toInstant().toEpochMilli(); }
        catch (Exception ignored) { return 0; }
    }
}
