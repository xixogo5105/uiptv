package com.uiptv.server.api.json;

import com.uiptv.model.Account;
import com.uiptv.util.AccountType;
import org.json.JSONObject;
import com.uiptv.util.HttpUtil;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

/** Identifies common web media formats from HTTP metadata and a small ranged read. */
final class PlaybackMediaInspector {
    private static final int SNIFF_BYTES = 2048;
    private static final String STRATEGY_UNKNOWN = "unknown";
    private static final String STRATEGY_MPEGTS = "mpegts";
    private static final String STRATEGY_NATIVE = "native";
    private static final String STRATEGY_UNSUPPORTED = "unsupported";
    private static final String STALKER_USER_AGENT = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3";
    private static final String WEB_USER_AGENT = "Mozilla/5.0";

    record MediaInfo(String strategy, String formatName, String videoCodec, String audioCodec, long durationMs) {
        JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("strategy", strategy);
            json.put("formatName", formatName);
            json.put("videoCodec", videoCodec);
            json.put("audioCodec", audioCodec);
            json.put("durationMs", durationMs);
            return json;
        }
    }

    private PlaybackMediaInspector() { }

    static MediaInfo inspect(String sourceUrl, Account account) {
        String url = sourceUrl == null ? "" : sourceUrl.trim();
        if (url.isEmpty()) return null;
        MediaInfo hint = inferUrlFormatHint(url);
        if (hint != null) return hint;

        try (HttpUtil.StreamResult response = HttpUtil.openStream(url,
                buildProbeHeaders(url, account),
                "GET", null, new HttpUtil.RequestOptions(true, true, 4, 4, 4))) {
            if (response.statusCode() < 200 || response.statusCode() >= 400) {
                MediaInfo fallback = inferStalkerOnDemandFormat(url, account);
                if (fallback != null) return fallback;
                return new MediaInfo(STRATEGY_UNKNOWN, "", "", "", 0L);
            }
            String contentType = firstHeader(response.responseHeaders(), "content-type").toLowerCase(Locale.ROOT);
            byte[] prefix = response.bodyStream().readNBytes(SNIFF_BYTES);
            MediaInfo detected = classifyResponse(contentType, prefix);
            if (STRATEGY_UNKNOWN.equals(detected.strategy()) && isStalkerOnDemandUrl(url, account)) {
                return inferStalkerOnDemandFormat(url, account);
            }
            return detected;
        } catch (IOException | RuntimeException _) {
            MediaInfo fallback = inferStalkerOnDemandFormat(url, account);
            if (fallback != null) return fallback;
            return new MediaInfo(STRATEGY_UNKNOWN, "", "", "", 0L);
        }
    }

    static MediaInfo inferUrlFormatHint(String url) {
        if (url == null || url.isBlank()) return null;
        String path = pathOf(url).toLowerCase(Locale.ROOT);
        String extension = queryParameter(url, "extension").toLowerCase(Locale.ROOT);
        if (path.endsWith(".m3u8") || "m3u8".equals(extension)) {
            return new MediaInfo("hls", "hls", "", "", 0L);
        }
        if (path.endsWith(".mpd") || "mpd".equals(queryParameter(url, "manifest_type").toLowerCase(Locale.ROOT))) {
            return new MediaInfo("dash", "dash", "", "", 0L);
        }
        if (path.endsWith(".ts") || path.endsWith(".m2ts") || "ts".equals(extension) || STRATEGY_MPEGTS.equals(extension)) {
            return new MediaInfo(STRATEGY_MPEGTS, STRATEGY_MPEGTS, "", "", 0L);
        }
        if (path.endsWith(".mp4") || path.endsWith(".m4v")) {
            return new MediaInfo(STRATEGY_NATIVE, "mp4", "", "", 0L);
        }
        if (path.endsWith(".mkv") || path.endsWith(".avi") || path.endsWith(".wmv")) {
            return new MediaInfo(STRATEGY_UNSUPPORTED, "container", "", "", 0L);
        }
        return null;
    }

    static MediaInfo inferStalkerOnDemandFormat(String url, Account account) {
        return isStalkerOnDemandUrl(url, account)
                ? new MediaInfo(STRATEGY_MPEGTS, STRATEGY_MPEGTS + " (Stalker on-demand)", "", "", 0L) : null;
    }

    static Map<String, String> buildProbeHeaders(String url, Account account) {
        boolean stalker = account != null && account.getType() == AccountType.STALKER_PORTAL
                && isStalkerPortalUrl(url);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Range", "bytes=0-2047");
        headers.put("Accept", "*/*");
        headers.put("Accept-Encoding", "identity");
        headers.put("User-Agent", stalker ? STALKER_USER_AGENT : WEB_USER_AGENT);
        if (stalker) {
            headers.put("X-User-Agent", "Model: MAG250; Link: WiFi");
            headers.put("Pragma", "no-cache");
            String origin = originOf(url);
            if (!origin.isEmpty()) {
                headers.put("Origin", origin);
                headers.put("Referer", origin + "/");
            }
            String mac = queryParameter(url, "mac");
            if (!mac.isEmpty()) {
                String timezone = account.getTimezone();
                if (timezone == null || timezone.isBlank()) timezone = "Europe/London";
                headers.put("Cookie", "mac=" + mac + "; stb_lang=en; timezone=" + timezone + ";");
            }
        }
        return headers;
    }

    static MediaInfo classifyResponse(String contentType, byte[] prefix) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        byte[] bytes = prefix == null ? new byte[0] : prefix;
        String format = "";
        String strategy = STRATEGY_UNKNOWN;
        String video = codecParameter(type, "video");
        String audio = codecParameter(type, "audio");
        if (type.contains("mpegurl") || startsWithText(bytes, "#EXTM3U")) {
            format = "hls"; strategy = "hls";
        } else if (type.contains("dash+xml")) {
            format = "dash"; strategy = "dash";
        } else if (type.contains("mp2t") || hasTransportStreamSync(bytes)) {
            format = STRATEGY_MPEGTS; strategy = STRATEGY_MPEGTS;
        } else if (type.contains("video/mp4") || hasMp4Signature(bytes)) {
            format = "mp4"; strategy = STRATEGY_NATIVE;
        } else if (type.contains("matroska") || hasMatroskaSignature(bytes)) {
            format = "matroska"; strategy = STRATEGY_UNSUPPORTED;
        } else if (type.contains("mpeg") || hasMpegProgramStreamSignature(bytes)) {
            format = "mpeg"; strategy = STRATEGY_UNSUPPORTED;
        }
        if (video.isEmpty() && audio.isEmpty()) {
            String codecs = codecListParameter(type);
            if (!codecs.isEmpty()) {
                String[] parts = codecs.split(",");
                for (String codec : parts) {
                    if (codec.startsWith("avc") || codec.startsWith("hvc") || codec.startsWith("hev")) video = codec;
                    else if (codec.startsWith("mp4a") || codec.startsWith("aac")) audio = codec;
                }
            }
        }
        if (strategy.equals(STRATEGY_NATIVE) && ((!video.isEmpty() && !isBrowserVideoCodec(video))
                || (!audio.isEmpty() && !isBrowserAudioCodec(audio)))) strategy = STRATEGY_UNSUPPORTED;
        return new MediaInfo(strategy, format, video, audio, 0L);
    }

    private static boolean isBrowserVideoCodec(String codec) {
        String value = codec.toLowerCase(Locale.ROOT);
        return value.contains("avc") || value.contains("h264");
    }

    private static boolean isBrowserAudioCodec(String codec) {
        String value = codec.toLowerCase(Locale.ROOT);
        return value.contains("aac") || value.contains("mp4a") || value.contains("mp3");
    }

    private static String firstHeader(Map<String, java.util.List<String>> headers, String name) {
        if (headers == null) return "";
        return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                .flatMap(e -> e.getValue().stream()).findFirst().orElse("");
    }

    private static boolean isStalkerOnDemandUrl(String url, Account account) {
        if (account == null || account.getType() != AccountType.STALKER_PORTAL || url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("/play/movie.php")
                || lower.contains("/vod/play/") || lower.contains("/series/play/");
    }

    private static boolean isStalkerPortalUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        return (lower.contains("/live/play/") || lower.contains("/play/live.php") || lower.contains("/play/movie.php"))
                && (lower.contains("play_token=") || lower.contains("mac="));
    }

    private static String originOf(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getScheme() + "://" + uri.getRawAuthority();
        } catch (RuntimeException _) {
            return "";
        }
    }

    private static String pathOf(String url) {
        try {
            return URI.create(url).getRawPath();
        } catch (RuntimeException _) {
            return "";
        }
    }

    private static String queryParameter(String url, String name) {
        try {
            String query = URI.create(url).getRawQuery();
            if (query == null) return "";
            for (String item : query.split("&")) {
                int equals = item.indexOf('=');
                if (equals > 0 && item.substring(0, equals).equalsIgnoreCase(name)) {
                    return java.net.URLDecoder.decode(item.substring(equals + 1), StandardCharsets.UTF_8);
                }
            }
        } catch (RuntimeException _) {
            return "";
        }
        return "";
    }

    private static boolean startsWithText(byte[] bytes, String text) {
        String prefix = new String(bytes, 0, Math.min(bytes.length, 32), StandardCharsets.US_ASCII).stripLeading();
        return prefix.startsWith(text);
    }

    private static boolean hasTransportStreamSync(byte[] bytes) {
        return bytes.length > 376 && (bytes[0] & 0xff) == 0x47
                && (bytes[188] & 0xff) == 0x47 && (bytes[376] & 0xff) == 0x47;
    }

    private static boolean hasMp4Signature(byte[] bytes) {
        return bytes.length >= 8 && bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p';
    }

    private static boolean hasMatroskaSignature(byte[] bytes) {
        return bytes.length >= 4 && (bytes[0] & 0xff) == 0x1a && (bytes[1] & 0xff) == 0x45
                && (bytes[2] & 0xff) == 0xdf && (bytes[3] & 0xff) == 0xa3;
    }

    private static boolean hasMpegProgramStreamSignature(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == 0 && bytes[1] == 0 && bytes[2] == 1 && (bytes[3] & 0xff) == 0xba;
    }

    private static String codecParameter(String contentType, String kind) {
        String codecs = codecListParameter(contentType);
        for (String codec : codecs.split(",")) {
            String value = codec.trim().toLowerCase(Locale.ROOT);
            if (kind.equals("video") && (value.startsWith("avc") || value.startsWith("hvc") || value.startsWith("hev"))) return value;
            if (kind.equals("audio") && (value.startsWith("mp4a") || value.startsWith("aac"))) return value;
        }
        return "";
    }

    private static String codecListParameter(String contentType) {
        int codecsIndex = contentType.indexOf("codecs=");
        if (codecsIndex < 0) return "";
        String codecs = contentType.substring(codecsIndex + 7).trim();
        if (codecs.startsWith("\"")) codecs = codecs.substring(1);
        int quote = codecs.indexOf('"');
        int semicolon = codecs.indexOf(';');
        int end = quote >= 0 ? quote : semicolon;
        return (end >= 0 ? codecs.substring(0, end) : codecs).trim();
    }
}
