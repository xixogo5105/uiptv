package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.util.ServerUrlUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static com.uiptv.util.StringUtils.isBlank;

public class LitePlayerFfmpegService extends AbstractFfmpegHlsService {
    enum PlaybackStrategy {
        DIRECT,
        COPY,
        TRANSCODE
    }

    public record PreparedPlayback(String sourceUrl, String playbackUrl, PlaybackStrategy strategy) {
        public boolean usesFfmpeg() {
            return strategy != PlaybackStrategy.DIRECT;
        }

        public String displayModeLabel() {
            return switch (strategy) {
                case DIRECT -> "Lite direct";
                case COPY -> "Lite using Transmux";
                case TRANSCODE -> "Lite software decoding";
            };
        }
    }

    static final class ProbeResult {
        final String formatName;
        final String videoCodec;
        final String audioCodec;

        ProbeResult(String formatName, String videoCodec, String audioCodec) {
            this.formatName = formatName == null ? "" : formatName;
            this.videoCodec = videoCodec == null ? "" : videoCodec;
            this.audioCodec = audioCodec == null ? "" : audioCodec;
        }

        boolean hasVideo() {
            return !videoCodec.isBlank();
        }

        boolean hasAudio() {
            return !audioCodec.isBlank();
        }
    }

    private static LitePlayerFfmpegService instance;

    private LitePlayerFfmpegService() {
    }

    public static synchronized LitePlayerFfmpegService getInstance() {
        if (instance == null) {
            instance = new LitePlayerFfmpegService();
        }
        return instance;
    }

    public PreparedPlayback preparePlayback(String rawUri, Account account, boolean forceCompatibilityFallback) {
        String sourceUrl = normalizeSourceUrl(rawUri);
        if (isBlank(sourceUrl)) {
            return new PreparedPlayback("", "", PlaybackStrategy.DIRECT);
        }

        boolean vodStylePlaylist = account != null && Account.NOT_LIVE_TV_CHANNELS.contains(account.getAction());
        PlaybackStrategy strategy = chooseStrategy(sourceUrl, forceCompatibilityFallback);
        if (strategy == PlaybackStrategy.DIRECT) {
            return new PreparedPlayback(sourceUrl, normalizeDirectPlaybackUrl(sourceUrl), PlaybackStrategy.DIRECT);
        }

        if (!ServerUrlUtil.ensureServerForWebPlayback()) {
            return new PreparedPlayback(sourceUrl, normalizeDirectPlaybackUrl(sourceUrl), PlaybackStrategy.DIRECT);
        }

        try {
            boolean ready = strategy == PlaybackStrategy.COPY
                    ? startCopyPlayback(sourceUrl, vodStylePlaylist)
                    : startTranscodePlayback(sourceUrl, vodStylePlaylist);
            if (ready) {
                return new PreparedPlayback(sourceUrl, getLocalHlsPlaybackUrl(), strategy);
            }
        } catch (Exception e) {
            com.uiptv.util.AppLog.addLog("LitePlayerFfmpegService: failed to prepare " + strategy + " playback: " + e.getMessage());
        }
        return new PreparedPlayback(sourceUrl, normalizeDirectPlaybackUrl(sourceUrl), PlaybackStrategy.DIRECT);
    }

    public PreparedPlayback prepareDirectPlayback(String rawUri) {
        String sourceUrl = normalizeSourceUrl(rawUri);
        return new PreparedPlayback(sourceUrl, normalizeDirectPlaybackUrl(sourceUrl), PlaybackStrategy.DIRECT);
    }

    public boolean isManagedPlaybackUrl(String url) {
        return !isBlank(url) && url.toLowerCase(Locale.ROOT).startsWith(getLocalHlsPlaybackUrl().toLowerCase(Locale.ROOT));
    }

    public void stopPlayback() {
        stopManagedHlsStream();
    }

    boolean startCopyPlayback(String inputUrl, boolean vodStylePlaylist) throws IOException {
        String outputUrl = ServerUrlUtil.getLocalServerUrl() + "/hls-upload/" + STREAM_FILENAME;
        return startManagedHlsStream(buildCopyHlsCommand(inputUrl, outputUrl, vodStylePlaylist));
    }

    boolean startTranscodePlayback(String inputUrl, boolean vodStylePlaylist) throws IOException {
        String outputUrl = ServerUrlUtil.getLocalServerUrl() + "/hls-upload/" + STREAM_FILENAME;
        return startManagedHlsStream(buildTranscodeHlsCommand(inputUrl, outputUrl, vodStylePlaylist));
    }

    static PlaybackStrategy chooseStrategy(String sourceUrl, boolean forceCompatibilityFallback) {
        if (isBlank(sourceUrl)) {
            return PlaybackStrategy.DIRECT;
        }
        String lower = sourceUrl.toLowerCase(Locale.ROOT);
        if (lower.startsWith(ServerUrlUtil.getLocalServerUrl().toLowerCase(Locale.ROOT) + "/hls/")) {
            return PlaybackStrategy.DIRECT;
        }
        if (!forceCompatibilityFallback && isLikelyDirectPlayableContainer(lower)) {
            return PlaybackStrategy.DIRECT;
        }

        ProbeResult probe = probeSource(sourceUrl);
        if (probe != null) {
            return chooseStrategy(sourceUrl, probe, forceCompatibilityFallback);
        }

        if (looksLikeTransportStream(lower)) {
            return PlaybackStrategy.COPY;
        }

        return forceCompatibilityFallback ? PlaybackStrategy.TRANSCODE : PlaybackStrategy.DIRECT;
    }

    static PlaybackStrategy chooseStrategy(String sourceUrl, ProbeResult probe, boolean forceCompatibilityFallback) {
        String lower = sourceUrl == null ? "" : sourceUrl.toLowerCase(Locale.ROOT);
        if (probe == null) {
            return forceCompatibilityFallback ? PlaybackStrategy.TRANSCODE : PlaybackStrategy.DIRECT;
        }
        boolean codecsCompatible = isLiteCompatibleVideoCodec(probe.videoCodec) && isLiteCompatibleAudioCodec(probe.audioCodec);
        if (!codecsCompatible) {
            return PlaybackStrategy.TRANSCODE;
        }

        if (looksLikeTransportStream(lower) || probe.formatName.toLowerCase(Locale.ROOT).contains("mpegts")) {
            return PlaybackStrategy.COPY;
        }

        if (!forceCompatibilityFallback && (isLikelyDirectPlayableContainer(lower) || isDirectPlayableFormat(probe.formatName))) {
            return PlaybackStrategy.DIRECT;
        }
        return PlaybackStrategy.COPY;
    }

    static boolean isLikelyDirectPlayableContainer(String sourceUrl) {
        if (isBlank(sourceUrl)) {
            return false;
        }
        String lower = sourceUrl.toLowerCase(Locale.ROOT);
        return lower.contains(".m3u8")
                || lower.contains("extension=m3u8")
                || lower.contains(".mp4")
                || lower.contains("extension=mp4")
                || lower.contains(".m4v")
                || lower.contains("extension=m4v")
                || lower.contains(".m4a")
                || lower.contains(".aac")
                || lower.contains(".mp3");
    }

    private static boolean looksLikeTransportStream(String sourceUrl) {
        return sourceUrl.contains("extension=ts")
                || sourceUrl.matches(".*\\.ts(\\?.*)?$");
    }

    private static boolean isDirectPlayableFormat(String formatName) {
        String lower = formatName == null ? "" : formatName.toLowerCase(Locale.ROOT);
        return lower.contains("hls")
                || lower.contains("applehttp")
                || lower.contains("mp4")
                || lower.contains("mov");
    }

    private static boolean isLiteCompatibleVideoCodec(String codec) {
        if (isBlank(codec)) {
            return true;
        }
        String lower = codec.toLowerCase(Locale.ROOT);
        return lower.contains("h264") || lower.contains("avc");
    }

    private static boolean isLiteCompatibleAudioCodec(String codec) {
        if (isBlank(codec)) {
            return true;
        }
        String lower = codec.toLowerCase(Locale.ROOT);
        return lower.contains("aac") || lower.contains("mp3") || lower.contains("mp4a");
    }

    private static String normalizeSourceUrl(String rawUri) {
        String sourceUrl = rawUri == null ? "" : rawUri.trim();
        if (!sourceUrl.startsWith("http") && !sourceUrl.startsWith("file:")) {
            File file = new File(sourceUrl);
            if (file.exists()) {
                sourceUrl = file.toURI().toString();
            }
        }
        return sourceUrl;
    }

    private static String normalizeDirectPlaybackUrl(String sourceUrl) {
        return sourceUrl.replace("extension=ts", "extension=m3u8");
    }

    static ProbeResult probeSource(String sourceUrl) {
        try {
            Process process = new ProcessBuilder(
                    "ffprobe",
                    "-v", "error",
                    "-print_format", "json",
                    "-show_format",
                    "-show_streams",
                    sourceUrl
            ).start();

            if (!process.waitFor(4, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) {
                return null;
            }

            String json = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (json.isBlank()) {
                return null;
            }

            JSONObject root = new JSONObject(json);
            JSONObject format = root.optJSONObject("format");
            String formatName = format == null ? "" : format.optString("format_name", "");

            String videoCodec = "";
            String audioCodec = "";
            JSONArray streams = root.optJSONArray("streams");
            if (streams != null) {
                for (int i = 0; i < streams.length(); i++) {
                    JSONObject stream = streams.optJSONObject(i);
                    if (stream == null) {
                        continue;
                    }
                    String codecType = stream.optString("codec_type", "");
                    if ("video".equalsIgnoreCase(codecType) && videoCodec.isBlank()) {
                        videoCodec = stream.optString("codec_name", "");
                    } else if ("audio".equalsIgnoreCase(codecType) && audioCodec.isBlank()) {
                        audioCodec = stream.optString("codec_name", "");
                    }
                }
            }
            return new ProbeResult(formatName, videoCodec, audioCodec);
        } catch (Exception ignored) {
            return null;
        }
    }
}
