package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.util.ServerUrlUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static com.uiptv.util.StringUtils.isBlank;

public class LitePlayerFfmpegService extends AbstractFfmpegHlsService {
    enum PlaybackStrategy {
        DIRECT,
        COPY,
        TRANSCODE
    }

    public record PreparedPlayback(String sourceUrl, String playbackUrl, PlaybackStrategy strategy, long estimatedDurationMs, long startOffsetMs) {
        public PreparedPlayback(String sourceUrl, String playbackUrl, PlaybackStrategy strategy) {
            this(sourceUrl, playbackUrl, strategy, 0L, 0L);
        }

        public boolean usesFfmpeg() {
            return strategy != PlaybackStrategy.DIRECT;
        }

        public String displayModeLabel() {
            return switch (strategy) {
                case DIRECT -> "Lite direct";
                case COPY -> "Lite using Transmux";
                case TRANSCODE -> "Lite transcoding";
            };
        }
    }

    static final class ProbeResult {
        final String formatName;
        final String videoCodec;
        final String audioCodec;
        final long durationMs;

        ProbeResult(String formatName, String videoCodec, String audioCodec, long durationMs) {
            this.formatName = formatName == null ? "" : formatName;
            this.videoCodec = videoCodec == null ? "" : videoCodec;
            this.audioCodec = audioCodec == null ? "" : audioCodec;
            this.durationMs = Math.max(0L, durationMs);
        }

        ProbeResult(String formatName, String videoCodec, String audioCodec) {
            this(formatName, videoCodec, audioCodec, 0L);
        }

        boolean hasVideo() {
            return !videoCodec.isBlank();
        }

        boolean hasAudio() {
            return !audioCodec.isBlank();
        }
    }

    private LitePlayerFfmpegService() {
    }

    private static class SingletonHelper {
        private static final LitePlayerFfmpegService INSTANCE = new LitePlayerFfmpegService();
    }

    public static LitePlayerFfmpegService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public PreparedPlayback preparePlayback(String rawUri, Account account, boolean forceCompatibilityFallback, boolean allowTranscoding, long startOffsetMs) {
        String sourceUrl = normalizeSourceUrl(rawUri);
        if (isBlank(sourceUrl)) {
            return new PreparedPlayback("", "", PlaybackStrategy.DIRECT, 0L, 0L);
        }

        boolean vodStylePlaylist = account != null && Account.NOT_LIVE_TV_CHANNELS.contains(account.getAction());
        ProbeResult probe = probeSource(sourceUrl);
        long normalizedOffset = Math.max(0L, startOffsetMs);
        PlaybackStrategy strategy = chooseStrategy(sourceUrl, forceCompatibilityFallback, allowTranscoding);
        if (strategy == PlaybackStrategy.DIRECT) {
            return new PreparedPlayback(sourceUrl, normalizeDirectPlaybackUrl(sourceUrl), PlaybackStrategy.DIRECT, estimateDurationMs(probe), 0L);
        }

        if (!ServerUrlUtil.ensureServerForWebPlayback()) {
            return new PreparedPlayback(sourceUrl, normalizeDirectPlaybackUrl(sourceUrl), PlaybackStrategy.DIRECT, estimateDurationMs(probe), 0L);
        }

        try {
            boolean ready = switch (strategy) {
                case COPY -> startCopyPlayback(sourceUrl, vodStylePlaylist, normalizedOffset);
                case TRANSCODE -> startTranscodePlayback(sourceUrl, vodStylePlaylist, normalizedOffset);
                default -> false;
            };
            if (ready) {
                return new PreparedPlayback(sourceUrl, getLocalHlsPlaybackUrl(), strategy, estimateDurationMs(probe), normalizedOffset);
            }
        } catch (Exception e) {
            com.uiptv.util.AppLog.addErrorLog(LitePlayerFfmpegService.class, "LitePlayerFfmpegService: failed to prepare " + strategy + " playback: " + e.getMessage());
        }
        return new PreparedPlayback(sourceUrl, normalizeDirectPlaybackUrl(sourceUrl), PlaybackStrategy.DIRECT, estimateDurationMs(probe), 0L);
    }

    public PreparedPlayback preparePlayback(String rawUri, Account account, boolean forceCompatibilityFallback, boolean allowTranscoding) {
        return preparePlayback(rawUri, account, forceCompatibilityFallback, allowTranscoding, 0L);
    }

    public PreparedPlayback prepareDirectPlayback(String rawUri) {
        String sourceUrl = normalizeSourceUrl(rawUri);
        return new PreparedPlayback(sourceUrl, normalizeDirectPlaybackUrl(sourceUrl), PlaybackStrategy.DIRECT, estimateDurationMs(probeSource(sourceUrl)), 0L);
    }

    public boolean isManagedPlaybackUrl(String url) {
        return !isBlank(url) && url.toLowerCase(Locale.ROOT).startsWith(getLocalHlsPlaybackUrl().toLowerCase(Locale.ROOT));
    }

    public void stopPlayback() {
        stopManagedHlsStream();
    }

    boolean startCopyPlayback(String inputUrl, boolean vodStylePlaylist, long startOffsetMs) throws IOException {
        String outputUrl = ServerUrlUtil.getLocalServerUrl() + "/hls-upload/" + STREAM_FILENAME;
        return startManagedHlsStream(buildCopyHlsCommand(inputUrl, outputUrl, vodStylePlaylist, startOffsetMs));
    }

    boolean startTranscodePlayback(String inputUrl, boolean vodStylePlaylist, long startOffsetMs) throws IOException {
        String outputUrl = ServerUrlUtil.getLocalServerUrl() + "/hls-upload/" + STREAM_FILENAME;
        return startManagedHlsStream(buildTranscodeHlsCommand(inputUrl, outputUrl, vodStylePlaylist, startOffsetMs));
    }

    static PlaybackStrategy chooseStrategy(String sourceUrl, boolean forceCompatibilityFallback, boolean allowTranscoding) {
        if (isBlank(sourceUrl)) {
            return PlaybackStrategy.DIRECT;
        }
        String lower = sourceUrl.toLowerCase(Locale.ROOT);
        ProbeResult probe = probeSource(sourceUrl);
        if (lower.startsWith(ServerUrlUtil.getLocalServerUrl().toLowerCase(Locale.ROOT) + "/hls/")) {
            return PlaybackStrategy.DIRECT;
        }
        if (!forceCompatibilityFallback && isLikelyDirectPlayableContainer(lower)) {
            return PlaybackStrategy.DIRECT;
        }

        if (probe != null) {
            return chooseStrategy(sourceUrl, probe, forceCompatibilityFallback, allowTranscoding);
        }

        if (looksLikeTransportStream(lower)) {
            return PlaybackStrategy.COPY;
        }

        return PlaybackStrategy.DIRECT;
    }

    static PlaybackStrategy chooseStrategy(String sourceUrl, ProbeResult probe, boolean forceCompatibilityFallback, boolean allowTranscoding) {
        String lower = sourceUrl == null ? "" : sourceUrl.toLowerCase(Locale.ROOT);
        if (probe == null) {
            return PlaybackStrategy.DIRECT;
        }
        boolean codecsCompatible = isLiteCompatibleVideoCodec(probe.videoCodec) && isLiteCompatibleAudioCodec(probe.audioCodec);
        if (!codecsCompatible) {
            return allowTranscoding ? PlaybackStrategy.TRANSCODE : PlaybackStrategy.DIRECT;
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
            Process process = buildProbeProcess(sourceUrl).start();
            if (!awaitSuccessfulProbe(process)) {
                return null;
            }

            String json = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (json.isBlank()) {
                return null;
            }

            return toProbeResult(new JSONObject(json));
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException | JSONException _) {
            return null;
        }
    }

    private static ProcessBuilder buildProbeProcess(String sourceUrl) {
        return new ProcessBuilder(
                "ffprobe",
                "-v", "error",
                "-print_format", "json",
                "-show_format",
                "-show_streams",
                sourceUrl
        );
    }

    private static boolean awaitSuccessfulProbe(Process process) throws InterruptedException {
        if (!process.waitFor(4, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return false;
        }
        return process.exitValue() == 0;
    }

    private static ProbeResult toProbeResult(JSONObject root) {
        JSONObject format = root.optJSONObject("format");
        String formatName = format == null ? "" : format.optString("format_name", "");
        long durationMs = parseDurationMs(format == null ? null : format.opt("duration"));
        CodecPair codecs = extractCodecs(root.optJSONArray("streams"));
        return new ProbeResult(formatName, codecs.videoCodec(), codecs.audioCodec(), durationMs);
    }

    private static long parseDurationMs(Object rawDuration) {
        if (rawDuration == null) {
            return 0L;
        }
        try {
            double seconds = Double.parseDouble(String.valueOf(rawDuration));
            if (!Double.isFinite(seconds) || seconds <= 0) {
                return 0L;
            }
            return Math.round(seconds * 1000.0);
        } catch (NumberFormatException _) {
            return 0L;
        }
    }

    private static long estimateDurationMs(ProbeResult probe) {
        return probe == null ? 0L : probe.durationMs;
    }

    private static CodecPair extractCodecs(JSONArray streams) {
        String videoCodec = "";
        String audioCodec = "";
        if (streams == null) {
            return new CodecPair(videoCodec, audioCodec);
        }
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
        return new CodecPair(videoCodec, audioCodec);
    }

    private record CodecPair(String videoCodec, String audioCodec) {
    }
}
