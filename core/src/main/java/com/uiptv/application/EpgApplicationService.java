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
    private static final Object SOURCES_LOCK = new Object();
    private static final HttpUtil.RequestOptions OPTIONS = new HttpUtil.RequestOptions(true, true, 10, 10, 20);

    public void clearCache() {
        clearCache(_ -> { });
    }

    /** Clears saved guide data off the UI thread; progress values are i18n keys. */
    public void clearCache(java.util.function.Consumer<String> progress) {
        try (var pause = EpgCacheRefreshService.pauseForCatalogueReload()) {
            EpgCacheRefreshService.invalidate(progress);
            progress.accept("epgClearPreparing");
            SOURCE_LOAD_LOCK.lock();
            try { synchronized (SOURCES_LOCK) { SOURCES.clear(); } }
            finally { SOURCE_LOAD_LOCK.unlock(); }
            new NativeEpgApplicationService().clearCache();
        }
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

    /** Accept common list separators while preserving each URL verbatim and in priority order. */
    public static List<String> globalSources(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("(?:\\r?\\n|\\r|[,;|]+|\\t+| {2,})"))
                .map(String::strip).filter(source -> !source.isBlank()).distinct().toList();
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
        var summaries = new EpgCacheApplicationService().summaries(account, channels);
        long now = System.currentTimeMillis();
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
        return programmes.stream().anyMatch(p -> p.stop() > now && p.stop() > p.start()
                && !p.title().isBlank() && !EpgSchedules.isGenericTitle(p.title()));
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
        try { sources.addAll(discover(account)); }
        catch (IOException | RuntimeException _) {
            AppLog.addWarningLog(EpgApplicationService.class, "Playlist EPG discovery unavailable.");
        }
        sources.addAll(globalSources(account.getEpg()));
        sources.addAll(globalSources(com.uiptv.service.ConfigurationService.getInstance().read().getGlobalEpgSource()));
        return sources.stream().filter(source -> source != null && !source.isBlank()).toList();
    }

    /** Stream one bounded source for the background database importer. */
    XmltvGuide downloadSource(String source) throws IOException, javax.xml.stream.XMLStreamException, InterruptedException {
        return downloadSource(source, _ -> { });
    }

    /** Same bounded importer, with phase updates for a user-visible refresh job. */
    XmltvGuide downloadSource(String source, java.util.function.Consumer<String> phase)
            throws IOException, javax.xml.stream.XMLStreamException, InterruptedException {
        return downloadSource(source, phase, _ -> { });
    }

    XmltvGuide downloadSource(String source, java.util.function.Consumer<String> phase,
                              java.util.function.LongConsumer bytesRead)
            throws IOException, javax.xml.stream.XMLStreamException, InterruptedException {
        phase.accept("Downloading");
        try (BufferedInputStream buffered=new BufferedInputStream(new CountingInputStream(open(source), bytesRead))) {
            buffered.mark(2);
            int first=buffered.read();
            int second=buffered.read();
            buffered.reset();
            boolean comprehensive = isComprehensiveSource(source);
            InputStream compressed = comprehensive ? new LimitedInputStream(buffered, 512L * 1024 * 1024) : buffered;
            InputStream xml=first==0x1f && second==0x8b ? new GZIPInputStream(compressed) : compressed;
            phase.accept("Parsing");
            XmltvGuide guide=XmltvGuide.read(new LimitedInputStream(xml,sourceSizeLimit(source)),Instant.now(),
                    comprehensive ? XmltvGuide.Limits.comprehensive() : XmltvGuide.Limits.standard());
            return new XmltvGuide(guide.programmes(), guide.names(), guide.qualityNames(),
                    resolveArtwork(guide.icons(), sourceUri(source)));
        }
    }

    private static final class CountingInputStream extends FilterInputStream {
        private final java.util.function.LongConsumer progress;
        private long count;
        private long reported;

        CountingInputStream(InputStream input, java.util.function.LongConsumer progress) {
            super(input);
            this.progress = progress;
        }

        private void counted(int bytes) {
            if (bytes <= 0) return;
            count += bytes;
            if (count - reported >= 256 * 1024) {
                reported = count;
                progress.accept(count);
            }
        }

        @Override public int read() throws IOException {
            int value = in.read();
            counted(value < 0 ? 0 : 1);
            return value;
        }

        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            int result = in.read(bytes, offset, length);
            counted(result);
            return result;
        }

        @Override public void close() throws IOException {
            try { if (count > reported) progress.accept(count); }
            finally { super.close(); }
        }
    }

    private Map<String, String> resolveArtwork(Map<String, String> icons, URI base) {
        Map<String, String> resolvedIcons = new HashMap<>();
        icons.forEach((id, icon) -> addArtwork(resolvedIcons, id, icon, base));
        return Map.copyOf(resolvedIcons);
    }

    private void addArtwork(Map<String, String> icons, String id, String icon, URI base) {
        try {
            URI resolved = base.resolve(icon);
            String scheme = resolved.getScheme();
            boolean remote = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            boolean local = "file".equalsIgnoreCase(scheme) && "file".equalsIgnoreCase(base.getScheme());
            if (remote || local) icons.put(id, resolved.toString());
        } catch (IllegalArgumentException _) {
            // Ignore malformed optional artwork.
        }
    }

    private List<String> discover(Account account) throws IOException {
        if (account.getType() != AccountType.M3U8_LOCAL && account.getType() != AccountType.M3U8_URL) return List.of();
        String playlist = account.getM3u8Path();
        if (playlist == null || playlist.isBlank()) return List.of();
        try { SOURCE_LOAD_LOCK.lockInterruptibly(); }
        catch (InterruptedException _) { Thread.currentThread().interrupt(); throw new InterruptedIOException("Playlist discovery cancelled"); }
        try {
            long now = System.currentTimeMillis();
            DiscoveredSource cached = discoveredSource(playlist);
            if (cached != null && now < cached.retryAt()) return cached.sources();
            return loadDiscoveredSource(playlist, now);
        } finally { SOURCE_LOAD_LOCK.unlock(); }
    }

    private DiscoveredSource discoveredSource(String playlist) {
        synchronized (SOURCES_LOCK) {
            return SOURCES.get(playlist);
        }
    }

    private List<String> loadDiscoveredSource(String playlist, long now) throws IOException {
        List<String> found;
        try {
            found = readPlaylistEpgSource(playlist);
        } catch (IOException | RuntimeException error) {
            putBounded(SOURCES, playlist, new DiscoveredSource(List.of(), now + Duration.ofMinutes(30).toMillis()));
            throw error;
        }
        URI base = sourceUri(playlist);
        List<String> resolved = found.stream().map(source -> {
            try { return base.resolve(source).toString(); }
            catch (IllegalArgumentException _) { return ""; }
        }).filter(source -> !source.isBlank()).distinct().toList();
        putBounded(SOURCES, playlist, new DiscoveredSource(resolved, now + Duration.ofMinutes(15).toMillis()));
        return resolved;
    }

    private List<String> readPlaylistEpgSource(String playlist) throws IOException {
        try (InputStream input = open(playlist);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            for (int i = 0; i < 20; i++) {
                String line = reader.readLine();
                if (line == null) return List.of();
                String header = line.replace("\uFEFF", "").strip();
                if (header.startsWith("#EXTM3U")) return playlistAttribute(header);
            }
            return List.of();
        }
    }

    private List<String> playlistAttribute(String header) {
        for (String attribute : List.of("x-tvg-url", "url-tvg", "tvg-url")) {
            String value = M3uPlaylistUtils.parseAttribute(header, attribute);
            if (!value.isBlank()) return globalSources(value);
        }
        return List.of();
    }

    private static URI sourceUri(String source) {
        if (source.matches("(?i)^https?://.*") || source.startsWith("file:")) return URI.create(source);
        return Path.of(source).toAbsolutePath().toUri();
    }

    static long sourceSizeLimit() {
        return 200L * 1024 * 1024;
    }

    static long sourceSizeLimit(String source) {
        return isComprehensiveSource(source) ? 4L * 1024 * 1024 * 1024 : sourceSizeLimit();
    }

    private static boolean isComprehensiveSource(String source) {
        if (source == null) return false;
        String value = source.strip();
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        int separator = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        String fileName = value.substring(separator + 1);
        return fileName.matches("(?i)epg_ripper_all_sources\\d*\\.xml(?:\\.gz)?");
    }

    // The returned filter stream owns the HTTP response and closes it when the caller closes the stream.
    @SuppressWarnings("java:S2095")
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
        synchronized (SOURCES_LOCK) {
            cache.put(key, value);
            while (cache.size() > 8) cache.remove(cache.keySet().iterator().next());
        }
    }
    private record DiscoveredSource(List<String> sources, long retryAt) { }
    static class LimitedInputStream extends FilterInputStream {
        private long remaining;
        private final long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        LimitedInputStream(InputStream input, long limit) { super(input); remaining = limit; }
        private void consumed(int count) throws IOException {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Guide loading cancelled");
            if (System.nanoTime() > deadline) throw new IOException("Guide loading deadline exceeded");
            if (count > 0) {
                remaining -= count;
                if (remaining < 0) throw new IOException("Guide exceeds size limit");
            }
        }
        @Override public int read() throws IOException { int value = in.read(); consumed(value < 0 ? 0 : 1); return value; }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException { int count = in.read(bytes, offset, length); consumed(count); return count; }
    }
}
