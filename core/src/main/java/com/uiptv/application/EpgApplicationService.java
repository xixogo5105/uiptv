package com.uiptv.application;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.service.ConfigurationService;
import com.uiptv.util.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Account-scoped, live guide requests. No database, file, memory or negative-result cache. */
public class EpgApplicationService {
    private static final HttpUtil.RequestOptions OPTIONS = new HttpUtil.RequestOptions(true, true, 10, 10, 20);
    public boolean isEnabled() { return ConfigurationService.getInstance().read().isEpgEnabled(); }
    public boolean isPlaybackOnly() { return true; }
    public boolean isBackgroundRefreshEnabled() { return false; }

    /** Catalogue reads never load a guide or attach programme details. */
    public void enrichCached(List<Channel> channels) {
        if (channels != null) channels.forEach(channel -> { channel.setEpgNow(null); channel.setEpgNext(null); });
    }

    public List<EpgProgramme> schedule(Account account, Channel channel) {
        if (!isEnabled() || account == null || channel == null || account.getAction() != Account.AccountAction.itv
                || Thread.currentThread().isInterrupted()) return List.of();
        List<EpgProgramme> nativeGuide = new NativeEpgApplicationService().schedule(account, channel);
        if (usable(nativeGuide)) return shifted(channel, nativeGuide);
        List<EpgProgramme> xmltvGuide = scheduleFromXmltvSources(account, channel);
        return xmltvGuide.isEmpty() ? shifted(channel, nativeGuide) : xmltvGuide;
    }

    private List<EpgProgramme> scheduleFromXmltvSources(Account account, Channel channel) {
        for (String source : accountSources(account)) {
            if (Thread.currentThread().isInterrupted()) return List.of();
            List<EpgProgramme> programmes = readXmltvSource(source, account, channel);
            if (Thread.currentThread().isInterrupted()) return List.of();
            if (!programmes.isEmpty()) return shifted(channel, programmes);
        }
        return List.of();
    }

    private List<EpgProgramme> readXmltvSource(String source, Account account, Channel channel) {
        try (BufferedInputStream buffered = new BufferedInputStream(open(source))) {
            buffered.mark(2);
            int first = buffered.read();
            int second = buffered.read();
            buffered.reset();
            InputStream xml = first == 0x1f && second == 0x8b ? new GZIPInputStream(buffered) : buffered;
            return LiveXmltvReader.read(new LimitedInputStream(xml, sourceSizeLimit()),
                    guideId(account, channel), channel.getName(), Instant.now());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception _) {
            AppLog.addWarningLog(EpgApplicationService.class, "Account programme guide unavailable.");
            return List.of();
        }
    }

    private List<EpgProgramme> shifted(Channel channel, List<EpgProgramme> programmes) {
        try {
            double hours = new org.json.JSONObject(channel.getExtraJson()).optJSONObject("m3u").optDouble("tvg-shift", 0);
            if (!Double.isFinite(hours) || Math.abs(hours) > 24 || hours == 0) return programmes;
            long offset = (long) (hours * 3_600_000);
            return programmes.stream().map(p -> new EpgProgramme(p.title(),p.description(),p.start()+offset,p.stop()+offset)).toList();
        } catch (RuntimeException _) { return programmes; }
    }

    static boolean usable(List<EpgProgramme> programmes) {
        long now = System.currentTimeMillis();
        return programmes.stream().anyMatch(p -> p.stop() > now && p.stop() > p.start()
                && !p.title().isBlank() && !EpgSchedules.isGenericTitle(p.title()));
    }
    public static List<String> parseSources(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("(?:\\r?\\n|\\r|[,;|]+|\\t+| {2,})"))
                .map(String::strip).filter(source -> !source.isBlank()).distinct().toList();
    }
    public static String guideId(Account account, Channel channel) {
        try {
            org.json.JSONObject metadata = new org.json.JSONObject(channel.getExtraJson());
            for (String field : account.getType() == AccountType.XTREME_API
                    ? List.of("epg_channel_id") : List.of("xmltv_id", "epg_id", "tvg-id")) {
                String id = metadata.optString(field, "").strip();
                if (!id.isBlank()) return id;
            }
        } catch (Exception _) {
            // Provider metadata may be empty or malformed; use the provider channel id below.
        }
        return account.getType() == AccountType.XTREME_API ? "" : channel.getChannelId();
    }
    List<String> accountSources(Account account) {
        List<String> configured = parseSources(account.getEpg());
        if (!configured.isEmpty()) return configured;
        if (account.getType() != AccountType.M3U8_LOCAL && account.getType() != AccountType.M3U8_URL) return List.of();
        String playlist = account.getM3u8Path();
        if (playlist == null || playlist.isBlank()) return List.of();
        try (var reader = new BufferedReader(new InputStreamReader(open(playlist), StandardCharsets.UTF_8))) {
            for (int i = 0; i < 20; i++) {
                String line = reader.readLine();
                if (line == null) return List.of();
                if (line.replace("\uFEFF", "").strip().startsWith("#EXTM3U")) {
                    for (String attribute : List.of("x-tvg-url", "url-tvg", "tvg-url")) {
                        var sources = parseSources(M3uPlaylistUtils.parseAttribute(line, attribute));
                        if (!sources.isEmpty()) return sources.stream().map(source -> sourceUri(playlist).resolve(source).toString()).toList();
                    }
                    return List.of();
                }
            }
        } catch (Exception _) {
            // A malformed or unreadable local playlist has no usable auto-discovered guide URL.
        }
        return List.of();
    }
    private static URI sourceUri(String source) {
        return source.matches("(?i)^https?://.*") || source.startsWith("file:") ? URI.create(source) : Path.of(source).toAbsolutePath().toUri();
    }
    static long sourceSizeLimit() { return 200L * 1024 * 1024; }
    @SuppressWarnings("java:S2095")
    private static InputStream open(String source) throws IOException {
        URI uri = sourceUri(source);
        if ("file".equalsIgnoreCase(uri.getScheme())) return Files.newInputStream(Path.of(uri));
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) throw new IOException("Unsupported EPG source");
        HttpUtil.StreamResult response = HttpUtil.openStream(uri.toString(), null, "GET", null, OPTIONS);
        if (response.statusCode() < 200 || response.statusCode() >= 300) { response.close(); throw new IOException("EPG HTTP failure"); }
        return new FilterInputStream(response.bodyStream()) { @Override public void close() throws IOException { response.close(); } };
    }
    static class LimitedInputStream extends FilterInputStream {
        private long remaining;
        private final long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        LimitedInputStream(InputStream input, long limit) { super(input); remaining = limit; }
        private void consumed(int count) throws IOException {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Guide loading cancelled");
            if (System.nanoTime() > deadline) throw new IOException("Guide loading deadline exceeded");
            if (count > 0) remaining -= count;
            if (remaining < 0) throw new IOException("Guide exceeds size limit");
        }
        @Override public int read() throws IOException { int value = in.read(); consumed(value < 0 ? 0 : 1); return value; }
        @Override public int read(byte[] b, int o, int l) throws IOException { int count = in.read(b,o,l); consumed(count); return count; }
    }
}
