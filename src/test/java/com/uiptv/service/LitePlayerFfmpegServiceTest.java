package com.uiptv.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.uiptv.service.LitePlayerFfmpegService.PlaybackStrategy.COPY;
import static com.uiptv.service.LitePlayerFfmpegService.PlaybackStrategy.DIRECT;
import static com.uiptv.service.LitePlayerFfmpegService.PlaybackStrategy.TRANSCODE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LitePlayerFfmpegServiceTest {

    @Test
    void chooseStrategy_prefersCopyForTransportStreams() {
        assertEquals(COPY, LitePlayerFfmpegService.chooseStrategy("http://example.test/live?extension=ts", false));
    }

    @Test
    void chooseStrategy_keepsHlsPlaybackDirectUntilCompatibilityFallbackIsForced() {
        assertEquals(DIRECT, LitePlayerFfmpegService.chooseStrategy("http://example.test/index.m3u8", false));
    }

    @Test
    void chooseStrategy_usesTranscodeForHevcTransportStreamWhenProbeDetectsUnsupportedCodec() {
        LitePlayerFfmpegService.ProbeResult probe = new LitePlayerFfmpegService.ProbeResult("mpegts", "hevc", "aac");
        assertEquals(TRANSCODE, LitePlayerFfmpegService.chooseStrategy("http://example.test/live?extension=ts", probe, false));
    }

    @Test
    void chooseStrategy_keepsLikelyDirectMp4PlaybackDirect() {
        assertEquals(DIRECT, LitePlayerFfmpegService.chooseStrategy("http://example.test/video.mp4", false));
    }

    @Test
    void chooseStrategy_usesTranscodeForHevcMp4WhenProbeDetectsUnsupportedCodec() {
        LitePlayerFfmpegService.ProbeResult probe = new LitePlayerFfmpegService.ProbeResult("mp4", "hevc", "aac");
        assertEquals(TRANSCODE, LitePlayerFfmpegService.chooseStrategy("http://example.test/video.mp4", probe, false));
    }

    @Test
    void chooseStrategy_usesCopyWhenCodecsAreCompatibleButContainerIsNotDirect() {
        LitePlayerFfmpegService.ProbeResult probe = new LitePlayerFfmpegService.ProbeResult("matroska,webm", "h264", "aac");
        assertEquals(COPY, LitePlayerFfmpegService.chooseStrategy("http://example.test/video.mkv", probe, false));
    }

    @Test
    void chooseStrategy_usesTranscodeWhenVideoCodecIsUnsupported() {
        LitePlayerFfmpegService.ProbeResult probe = new LitePlayerFfmpegService.ProbeResult("matroska,webm", "hevc", "aac");
        assertEquals(TRANSCODE, LitePlayerFfmpegService.chooseStrategy("http://example.test/video.mkv", probe, false));
    }

    @Test
    void buildTranscodeHlsCommand_usesH264AacProfileForLiteCompatibilityMode() {
        List<String> command = AbstractFfmpegHlsService.buildTranscodeHlsCommand(
                "http://example.test/movie.mkv",
                "http://127.0.0.1:8888/hls-upload/stream.m3u8",
                true
        );

        assertTrue(command.contains("libx264"));
        assertTrue(command.contains("aac"));
        assertTrue(command.contains("yuv420p"));
        assertTrue(command.contains("ultrafast"));
        assertFalse(command.contains("copy"));
    }
}
