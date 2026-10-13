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

    public boolean supportsGuide(Account account) {
        return account != null && (account.getEpg() != null && !account.getEpg().isBlank()
                || (account.getType() == AccountType.M3U8_LOCAL || account.getType() == AccountType.M3U8_URL)
                && account.getM3u8Path() != null && !account.getM3u8Path().isBlank());
    }

    public void enrich(Account account, List<Channel> channels) {
        if (!supportsGuide(account) || channels == null || channels.isEmpty()) return;
        XmltvGuide guide = guide(account);
        long now = System.currentTimeMillis();
        for (Channel channel : channels) {
            channel.setEpgNow(null);
            channel.setEpgNext(null);
            for (EpgProgramme p : guide.forChannel(channel.getChannelId(), channel.getName())) {
                if (p.start() <= now && now < p.stop()) channel.setEpgNow(p);
                if (p.start() > now) { channel.setEpgNext(p); break; }
            }
        }
    }

    public List<EpgProgramme> schedule(Account account, Channel channel) {
        return guide(account).forChannel(channel.getChannelId(), channel.getName());
    }

    private XmltvGuide guide(Account account) {
        if (!supportsGuide(account)) return EMPTY;
        try {
            String source = account.getEpg() == null ? "" : account.getEpg().strip();
            if (source.isEmpty()) source = discover(account);
            if (source.isEmpty()) return EMPTY;
            synchronized (GUIDES) {
                CachedGuide cached = GUIDES.get(source);
                long now = System.currentTimeMillis();
                if (cached != null && now < cached.retryAt()) return cached.guide();
                try (InputStream raw = open(source); BufferedInputStream buffered = new BufferedInputStream(raw)) {
                    buffered.mark(2);
                    int first = buffered.read(), second = buffered.read();
                    buffered.reset();
                    InputStream xml = first == 0x1f && second == 0x8b ? new GZIPInputStream(buffered) : buffered;
                    XmltvGuide parsed = XmltvGuide.read(new LimitedInputStream(xml), Instant.now());
                    putBounded(GUIDES, source, new CachedGuide(parsed, now + Duration.ofHours(6).toMillis()));
                    return parsed;
                } catch (Exception e) {
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                    XmltvGuide previous = cached == null ? EMPTY : cached.guide();
                    putBounded(GUIDES, source, new CachedGuide(previous, now + Duration.ofMinutes(5).toMillis()));
                    AppLog.addWarningLog(EpgApplicationService.class, "EPG source unavailable; retaining previous guide.");
                    return previous;
                }
            }
        } catch (Exception e) {
            AppLog.addWarningLog(EpgApplicationService.class, "Unable to discover playlist EPG source.");
            return EMPTY;
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
        while (cache.size() > 8) cache.remove(cache.keySet().iterator().next());
    }
    private record CachedGuide(XmltvGuide guide, long retryAt) { }
    private record DiscoveredSource(String source, long retryAt) { }
    private static class LimitedInputStream extends FilterInputStream {
        private long remaining = 100L * 1024 * 1024;
        LimitedInputStream(InputStream input) { super(input); }
        private void consumed(int count) throws IOException { if (count > 0 && (remaining -= count) < 0) throw new IOException("Guide exceeds size limit"); }
        @Override public int read() throws IOException { int value = in.read(); consumed(value < 0 ? 0 : 1); return value; }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException { int count = in.read(bytes, offset, length); consumed(count); return count; }
    }
}
