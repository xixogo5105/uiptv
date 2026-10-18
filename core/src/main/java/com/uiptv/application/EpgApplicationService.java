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

/** XMLTV source discovery and streaming import; callers run this service off the UI thread. */
public class EpgApplicationService {
    private static final java.util.concurrent.locks.ReentrantLock SOURCE_LOAD_LOCK = new java.util.concurrent.locks.ReentrantLock();
    private static final Map<String, DiscoveredSource> SOURCES = new LinkedHashMap<>();
    private static final HttpUtil.RequestOptions OPTIONS = new HttpUtil.RequestOptions(true, true, 10, 10, 20);

    public void clearCache() {
        SOURCE_LOAD_LOCK.lock();
        try { synchronized (SOURCES) { SOURCES.clear(); } }
        finally { SOURCE_LOAD_LOCK.unlock(); }
        new NativeEpgApplicationService().clearCache();
        EpgCacheRefreshService.invalidate();
    }
    public String globalWarning() {
        if (!isEnabled()) return "";
        String source = com.uiptv.service.ConfigurationService.getInstance().read().getGlobalEpgSource();
        var cache = new com.uiptv.db.EpgCacheDb();
        for (String entry : globalSources(source)) {
            String warning = cache.sourceState(EpgCacheApplicationService.sourceId(entry)).warning();
            if (!warning.isBlank()) return warning;
        }
        return "";
    }
    static String warning(Exception error) {
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

    /** Full EPG card/timer/background refreshes are disabled in playback-only mode. */
    public boolean isPlaybackOnly() {
        return isEnabled() && com.uiptv.service.ConfigurationService.getInstance().read().isEpgPlaybackOnly();
    }

    public boolean isBackgroundRefreshEnabled() {
        return isEnabled() && !isPlaybackOnly();
    }

    /** Catalog rendering must never wait for playlist discovery, disk parsing or HTTP. */
    public void enrichCached(Account account, List<Channel> channels) {
        if(channels==null) return;
        if (isPlaybackOnly()) {
            // Lightweight mode deliberately renders plain catalogue cards. Do not even scan the
            // EPG cache here: the player is the only allowed entry point for EPG work.
            channels.forEach(channel -> { channel.setEpgNow(null); channel.setEpgNext(null); });
            return;
        }
        var summaries=new EpgCacheApplicationService().summaries(account,channels);
        long now=System.currentTimeMillis();
        for(Channel channel:channels) {
            channel.setEpgNow(null); channel.setEpgNext(null);
            for(EpgProgramme p:summaries.getOrDefault(channel,List.of())) {
                if(p.start()<=now && now<p.stop()) channel.setEpgNow(p);
                if(p.start()>now) { channel.setEpgNext(p); break; }
            }
        }
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
        } catch (Exception _) { /* Older catalogs may not retain structured metadata. */ }
        // Xtream stream ids are API identities, never XMLTV ids.
        return account.getType() == AccountType.XTREME_API ? "" : channel.getChannelId();
    }

    List<String> cacheSources(Account account) {
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        try { sources.add(discover(account)); }
        catch (IOException | RuntimeException _) {
            AppLog.addWarningLog(EpgApplicationService.class, "Playlist EPG discovery unavailable.");
        }
        sources.add(account.getEpg());
        sources.addAll(globalSources(com.uiptv.service.ConfigurationService.getInstance().read().getGlobalEpgSource()));
        return sources.stream().filter(source -> source != null && !source.isBlank()).toList();
    }

    /** Stream one bounded source for the background database importer. */
    XmltvGuide downloadSource(String source) throws Exception {
        try (InputStream raw=open(source); BufferedInputStream buffered=new BufferedInputStream(raw)) {
            buffered.mark(2); int first=buffered.read(),second=buffered.read(); buffered.reset();
            InputStream xml=first==0x1f && second==0x8b ? new GZIPInputStream(buffered) : buffered;
            XmltvGuide guide=XmltvGuide.read(new LimitedInputStream(xml,sourceSizeLimit()),Instant.now());
            Map<String,String> icons=new HashMap<>(); URI base=sourceUri(source);
            guide.icons().forEach((id,icon) -> {
                try {
                    URI resolved=base.resolve(icon); String scheme=resolved.getScheme();
                    if("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)
                            || "file".equalsIgnoreCase(scheme) && "file".equalsIgnoreCase(base.getScheme())) icons.put(id,resolved.toString());
                } catch(IllegalArgumentException ignored) { /* Ignore malformed optional artwork. */ }
            });
            return new XmltvGuide(guide.programmes(),guide.names(),guide.qualityNames(),Map.copyOf(icons));
        }
    }

    private String discover(Account account) throws IOException {
        if (account.getType() != AccountType.M3U8_LOCAL && account.getType() != AccountType.M3U8_URL) return "";
        String playlist = account.getM3u8Path();
        if (playlist == null || playlist.isBlank()) return "";
        try { SOURCE_LOAD_LOCK.lockInterruptibly(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new InterruptedIOException("Playlist discovery cancelled"); }
        try {
            DiscoveredSource cached;
            synchronized (SOURCES) { cached = SOURCES.get(playlist); }
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
                putBounded(SOURCES, playlist, new DiscoveredSource("", now + Duration.ofMinutes(30).toMillis()));
                throw e;
            }
            if (!found.isBlank()) {
                URI base = sourceUri(playlist);
                found = base.resolve(found).toString();
            }
            putBounded(SOURCES, playlist, new DiscoveredSource(found, now + Duration.ofMinutes(15).toMillis()));
            return found;
        } finally { SOURCE_LOAD_LOCK.unlock(); }
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
        synchronized (cache) {
            cache.put(key, value);
            while (cache.size() > 8) cache.remove(cache.keySet().iterator().next());
        }
    }
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
