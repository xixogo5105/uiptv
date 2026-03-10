package com.uiptv.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.uiptv.service.LitePlayerFfmpegService.PlaybackStrategy.COPY;
import static com.uiptv.service.LitePlayerFfmpegService.PlaybackStrategy.DIRECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LitePlayerFfmpegServiceCoverageTest {

    @TempDir
    Path tempDir;

    @Test
    void preparedPlayback_helpersExposeStrategyState() {
        LitePlayerFfmpegService.PreparedPlayback direct = new LitePlayerFfmpegService.PreparedPlayback("src", "play", DIRECT);
        LitePlayerFfmpegService.PreparedPlayback copy = new LitePlayerFfmpegService.PreparedPlayback("src", "play", COPY);

        assertFalse(direct.usesFfmpeg());
        assertTrue(copy.usesFfmpeg());
        assertEquals("Lite direct", direct.displayModeLabel());
        assertEquals("Lite using Transmux", copy.displayModeLabel());
    }

    @Test
    void prepareDirectPlayback_normalizesExistingFilesAndManagedUrls() throws Exception {
        Path media = Files.writeString(tempDir.resolve("episode.ts"), "demo");
        LitePlayerFfmpegService service = LitePlayerFfmpegService.getInstance();

        LitePlayerFfmpegService.PreparedPlayback playback = service.prepareDirectPlayback(media.toString());

        assertTrue(playback.sourceUrl().startsWith("file:"));
        Method localUrlMethod = AbstractFfmpegHlsService.class.getDeclaredMethod("getLocalHlsPlaybackUrl");
        localUrlMethod.setAccessible(true);
        String localPlaybackUrl = (String) localUrlMethod.invoke(service);
        assertTrue(service.isManagedPlaybackUrl(localPlaybackUrl));
        assertFalse(service.isManagedPlaybackUrl("http://example.com/hls/stream.m3u8"));
    }

    @Test
    void chooseStrategy_andCompatibilityHelpers_coverContainerBranches() throws Exception {
        assertEquals(DIRECT, LitePlayerFfmpegService.chooseStrategy("http://127.0.0.1:8080/hls/stream.m3u8", false));
        assertEquals(DIRECT, LitePlayerFfmpegService.chooseStrategy("http://example.test/video.mp3", false));
        assertEquals(COPY, LitePlayerFfmpegService.chooseStrategy("http://example.test/live.ts", new LitePlayerFfmpegService.ProbeResult("mpegts", "h264", "aac"), false));
        assertEquals(COPY, LitePlayerFfmpegService.chooseStrategy("http://example.test/video.mkv", new LitePlayerFfmpegService.ProbeResult("matroska", "h264", "aac"), true));
        assertEquals(DIRECT, LitePlayerFfmpegService.chooseStrategy("http://example.test/video.mkv", new LitePlayerFfmpegService.ProbeResult("matroska", "hevc", "opus"), false));
        assertEquals(DIRECT, LitePlayerFfmpegService.chooseStrategy("http://example.test/unknown.bin", new LitePlayerFfmpegService.ProbeResult("", "hevc", "aac"), true));

        assertTrue(invokeBoolean("isLikelyDirectPlayableContainer", "http://example.test/video.m4v"));
        assertFalse(invokeBoolean("isLikelyDirectPlayableContainer", "http://example.test/video.mkv"));
        assertTrue(invokeBoolean("isDirectPlayableFormat", "applehttp"));
        assertFalse(invokeBoolean("isDirectPlayableFormat", "matroska"));
        assertTrue(invokeBoolean("isLiteCompatibleVideoCodec", "avc1"));
        assertFalse(invokeBoolean("isLiteCompatibleVideoCodec", "hevc"));
        assertTrue(invokeBoolean("isLiteCompatibleAudioCodec", "mp4a.40.2"));
        assertFalse(invokeBoolean("isLiteCompatibleAudioCodec", "opus"));
    }

    @Test
    void probeJsonConversion_helpers_extractCodecsAndMapProbeResult() throws Exception {
        JSONArray streams = new JSONArray()
                .put(new JSONObject().put("codec_type", "video").put("codec_name", "h264"))
                .put(new JSONObject().put("codec_type", "audio").put("codec_name", "aac"));
        JSONObject root = new JSONObject()
                .put("format", new JSONObject().put("format_name", "mp4"))
                .put("streams", streams);

        Object probe = invoke("toProbeResult", new Class[]{JSONObject.class}, root);
        Method hasVideo = probe.getClass().getDeclaredMethod("hasVideo");
        Method hasAudio = probe.getClass().getDeclaredMethod("hasAudio");
        hasVideo.setAccessible(true);
        hasAudio.setAccessible(true);

        assertTrue((Boolean) hasVideo.invoke(probe));
        assertTrue((Boolean) hasAudio.invoke(probe));
    }

    @Test
    void ffmpegCommands_includeVodAndLiveSpecificArguments() {
        List<String> liveCopy = FfmpegService.buildHlsCommand("http://example.test/live.ts", "http://127.0.0.1:8888/hls-upload/stream.m3u8", false);
        List<String> vodCopy = FfmpegService.buildHlsCommand("http://example.test/vod.ts", "http://127.0.0.1:8888/hls-upload/stream.m3u8", true);

        assertTrue(FfmpegService.getInstance().isTransmuxingNeeded("http://example.test/live?extension=ts"));
        assertFalse(FfmpegService.getInstance().isTransmuxingNeeded("http://example.test/live.m3u8"));
        assertTrue(liveCopy.contains("delete_segments"));
        assertTrue(vodCopy.contains("event"));
        assertTrue(vodCopy.contains("+genpts"));
    }

    private static boolean invokeBoolean(String name, String argument) throws Exception {
        return (Boolean) invoke(name, new Class[]{String.class}, argument);
    }

    private static Object invoke(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = LitePlayerFfmpegService.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
}
