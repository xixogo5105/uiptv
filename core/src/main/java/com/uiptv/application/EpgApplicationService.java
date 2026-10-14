package com.uiptv.application;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.util.AccountType;
import com.uiptv.util.AppLog;
import com.uiptv.util.HttpUtil;
import com.uiptv.util.M3uPlaylistUtils;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Shared guide loading; callers run this service off the UI thread. */
public class EpgApplicationService {
    private static final Map<String, CachedGuide> GUIDES = new LinkedHashMap<>();
    private static final Map<String, DiscoveredSource> SOURCES = new LinkedHashMap<>();
    private static final HttpUtil.RequestOptions OPTIONS = new HttpUtil.RequestOptions(true, true, 10, 10, 20);
    private static final XmltvGuide EMPTY = new XmltvGuide(Map.of(), Map.of());

    public void clearCache() {
        synchronized (GUIDES) { GUIDES.clear(); EpgDiskCache.clear(); }
        synchronized (SOURCES) { SOURCES.clear(); }
        new NativeEpgApplicationService().clearCache();
    }
    public String globalWarning() {
        String source = com.uiptv.service.ConfigurationService.getInstance().read().getGlobalEpgSource();
        synchronized (GUIDES) {
            for (String entry : globalSources(source)) {
                CachedGuide cached = GUIDES.get(entry);
                if (cached != null && !cached.warning().isBlank()) return cached.warning();
            }
            return "";
        }
    }
    private static String warning(Exception error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains("Guide exceeds size limit")) return "epgSourceTooLarge";
            if (cause.getMessage() != null && cause.getMessage().contains("Guide exceeds programme limit")) return "epgSourceProgrammeLimit";
        }
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains("JAXP000100")) return "epgSourceXmlLimit";
            if (cause instanceof IOException) return "epgSourceUnavailable";
        }
        if (error instanceof javax.xml.stream.XMLStreamException) return "epgSourceInvalid";
        return "epgSourceUnavailable";
    }

    /** One source per line; existing single-source settings remain compatible. */
    public static List<String> globalSources(String value) {
        if (value == null || value.isBlank()) return List.of();
        return value.lines().map(String::strip).filter(line -> !line.isBlank()).distinct().toList();
    }

    public boolean isEnabled() {
        return com.uiptv.service.ConfigurationService.getInstance().read().isEpgEnabled();
    }

    public boolean supportsGuide(Account account) {
        return isEnabled() && account != null && account.getAction() == Account.AccountAction.itv;
    }

    public void enrich(Account account, List<Channel> channels) {
        if (channels == null) return;
        for (Channel channel : channels) {
            channel.setEpgNow(null);
            channel.setEpgNext(null);
        }
        if (!supportsGuide(account) || channels.isEmpty()) return;
        // Catalog enrichment shares XMLTV sources; native schedules are requested on guide/player demand.
        long now = System.currentTimeMillis();
        ResolutionContext context = context(account);
        for (Channel channel : channels) {
            List<EpgProgramme> nativeSchedule = new NativeEpgApplicationService().cached(account, channel);
            for (EpgProgramme p : usable(nativeSchedule) ? nativeSchedule : xmlSchedule(account, channel, context)) {
                if (p.start() <= now && now < p.stop()) channel.setEpgNow(p);
                if (p.start() > now) { channel.setEpgNext(p); break; }
            }
        }
    }

    public List<EpgProgramme> cachedSchedule(Account account, Channel channel) {
        if (!supportsGuide(account) || channel == null) return List.of();
        List<EpgProgramme> nativeSchedule = new NativeEpgApplicationService().cached(account, channel);
        return usable(nativeSchedule) ? nativeSchedule : xmlSchedule(account, channel);
    }

    public List<EpgProgramme> schedule(Account account, Channel channel) {
        if (!supportsGuide(account) || channel == null || Thread.currentThread().isInterrupted()) return List.of();
        List<EpgProgramme> nativeSchedule = new NativeEpgApplicationService().schedule(account, channel);
        return usable(nativeSchedule) ? nativeSchedule : xmlSchedule(account, channel);
    }

    public Map<Channel, List<EpgProgramme>> catalogSchedules(Account account, List<Channel> channels) {
        if (!supportsGuide(account)) return Map.of();
        ResolutionContext context = context(account);
        Map<Channel, List<EpgProgramme>> result = new IdentityHashMap<>();
        for (Channel channel : channels) {
            List<EpgProgramme> nativeSchedule = new NativeEpgApplicationService().cached(account, channel);
            result.put(channel, usable(nativeSchedule) ? nativeSchedule : xmlSchedule(account, channel, context));
        }
        return result;
    }
    private record ResolutionContext(List<String> sources, org.json.JSONObject mappings) { }
    private ResolutionContext context(Account account) {
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        try { sources.add(discover(account)); }
        catch (Exception e) { AppLog.addWarningLog(EpgApplicationService.class, "Playlist EPG discovery unavailable."); }
        sources.add(account.getEpg());
        var config = com.uiptv.service.ConfigurationService.getInstance().read();
        sources.addAll(globalSources(config.getGlobalEpgSource()));
        org.json.JSONObject mappings;
        try { mappings = new org.json.JSONObject(config.getEpgMappingsJson()); }
        catch (Exception ignored) { mappings = new org.json.JSONObject(); }
        return new ResolutionContext(sources.stream().filter(source -> source != null && !source.isBlank()).toList(), mappings);
    }
    private List<EpgProgramme> xmlSchedule(Account account, Channel channel) {
        return xmlSchedule(account, channel, context(account));
    }
    private List<EpgProgramme> xmlSchedule(Account account, Channel channel, ResolutionContext context) {
        org.json.JSONObject mappings = account.getDbId() == null ? null : context.mappings().optJSONObject(account.getDbId());
        String override = mappings == null ? "" : mappings.optString(channel.getChannelId(), "");
        String id = override.isBlank() ? guideId(account, channel) : override;
        List<String> sources = context.sources();
        for (String source : sources) {
            if (Thread.currentThread().isInterrupted()) return List.of();
            if (source == null || source.isBlank()) continue;
            List<EpgProgramme> programmes = guide(source.strip()).forChannel(id, override.isBlank() ? channel.getName() : "");
            if (usable(programmes)) return programmes;
        }
        return List.of();
    }

    static boolean usable(List<EpgProgramme> programmes) {
        long now = System.currentTimeMillis();
        return programmes.stream().anyMatch(p -> p.stop() > now && p.stop() > p.start() && !p.title().isBlank());
    }

    public static String guideId(Account account, Channel channel) {
        try {
            org.json.JSONObject metadata = new org.json.JSONObject(channel.getExtraJson());
            for (String field : account.getType() == AccountType.XTREME_API
                    ? List.of("epg_channel_id") : List.of("xmltv_id", "epg_id", "tvg-id")) {
                String id = metadata.optString(field, "").strip();
                if (!id.isBlank()) return id;
            }
        } catch (Exception ignored) { /* Older catalogs may not retain structured metadata. */ }
        // Xtream stream ids are API identities, never XMLTV ids.
        return account.getType() == AccountType.XTREME_API ? "" : channel.getChannelId();
    }

    private XmltvGuide guide(String source) {
            synchronized (GUIDES) {
                CachedGuide cached = GUIDES.get(source);
                long now = System.currentTimeMillis();
                if (cached != null && now < cached.retryAt()) return cached.guide();
                boolean remote = source.matches("(?i)^https?://.*");
                XmltvGuide disk = remote ? EpgDiskCache.read(source, false) : null;
                if (disk != null) {
                    putBounded(GUIDES, source, new CachedGuide(disk, EpgDiskCache.freshUntil(source), ""));
                    return disk;
                }
                try (InputStream raw = open(source); BufferedInputStream buffered = new BufferedInputStream(raw)) {
                    buffered.mark(2);
                    int first = buffered.read(), second = buffered.read();
                    buffered.reset();
                    InputStream xml = first == 0x1f && second == 0x8b ? new GZIPInputStream(buffered) : buffered;
                    InputStream limited = new LimitedInputStream(xml, sourceSizeLimit());
                    XmltvGuide parsed = remote ? EpgDiskCache.parseAndSave(source, limited) : XmltvGuide.read(limited, Instant.now());
                    putBounded(GUIDES, source, new CachedGuide(parsed, now + EpgDiskCache.ttlMillis(), ""));
                    return parsed;
                } catch (Exception e) {
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                    XmltvGuide previous = cached == null ? (remote ? EpgDiskCache.read(source, true) : null) : cached.guide();
                    if (previous == null) previous = EMPTY;
                    putBounded(GUIDES, source, new CachedGuide(previous, now + Duration.ofMinutes(5).toMillis(), warning(e)));
                    AppLog.addWarningLog(EpgApplicationService.class, "EPG source unavailable: " + com.uiptv.util.I18n.tr(warning(e)) + " Retaining previous guide.");
                    return previous;
                }
            }
    }

    private String discover(Account account) throws IOException {
        if (account.getType() != AccountType.M3U8_LOCAL && account.getType() != AccountType.M3U8_URL) return "";
        String playlist = account.getM3u8Path();
        if (playlist == null || playlist.isBlank()) return "";
        synchronized (SOURCES) {
            DiscoveredSource cached = SOURCES.get(playlist);
            long now = System.currentTimeMillis();
            if (cached != null && now < cached.retryAt()) return cached.source();
            String found = "";
            try (InputStream input = open(playlist); BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                for (int i = 0; i < 20; i++) {
                    String line = reader.readLine();
                    if (line == null) break;
                    line = line.replace("\uFEFF", "").strip();
                    if (!line.startsWith("#EXTM3U")) continue;
                    for (String attribute : List.of("x-tvg-url", "url-tvg", "tvg-url")) {
                        found = M3uPlaylistUtils.parseAttribute(line, attribute);
                        if (!found.isBlank()) { found = found.split(",")[0].strip(); break; }
                    }
                    break;
                }
            } catch (IOException | RuntimeException e) {
                putBounded(SOURCES, playlist, new DiscoveredSource("", now + Duration.ofMinutes(5).toMillis()));
                throw e;
            }
            if (!found.isBlank()) {
                URI base = sourceUri(playlist);
                found = base.resolve(found).toString();
            }
            putBounded(SOURCES, playlist, new DiscoveredSource(found, now + Duration.ofMinutes(15).toMillis()));
            return found;
        }
    }

    private static URI sourceUri(String source) {
        if (source.matches("(?i)^https?://.*") || source.startsWith("file:")) return URI.create(source);
        return Path.of(source).toAbsolutePath().toUri();
    }

    static long sourceSizeLimit() {
        return 200L * 1024 * 1024;
    }

    private static InputStream open(String source) throws IOException {
        URI uri = sourceUri(source);
        if ("file".equalsIgnoreCase(uri.getScheme())) return Files.newInputStream(Path.of(uri));
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) throw new IOException("Unsupported EPG source");
        HttpUtil.StreamResult response = HttpUtil.openStream(uri.toString(), null, "GET", null, OPTIONS);
        if (response.statusCode() < 200 || response.statusCode() >= 300) { response.close(); throw new IOException("EPG HTTP failure"); }
        return new FilterInputStream(response.bodyStream()) {
            @Override public void close() throws IOException { response.close(); }
        };
    }

    private static <T> void putBounded(Map<String, T> cache, String key, T value) {
        cache.put(key, value);
        int capacity = cache == (Object) GUIDES ? 32 : 8;
        while (cache.size() > capacity) cache.remove(cache.keySet().iterator().next());
    }
    private record CachedGuide(XmltvGuide guide, long retryAt, String warning) { }
    private record DiscoveredSource(String source, long retryAt) { }
    static class LimitedInputStream extends FilterInputStream {
        private long remaining;
        private final long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        LimitedInputStream(InputStream input, long limit) { super(input); remaining = limit; }
        private void consumed(int count) throws IOException {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Guide loading cancelled");
            if (System.nanoTime() > deadline) throw new IOException("Guide loading deadline exceeded");
            if (count > 0 && (remaining -= count) < 0) throw new IOException("Guide exceeds size limit");
        }
        @Override public int read() throws IOException { int value = in.read(); consumed(value < 0 ? 0 : 1); return value; }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException { int count = in.read(bytes, offset, length); consumed(count); return count; }
    }
}
