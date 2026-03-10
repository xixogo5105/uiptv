package com.uiptv.service;

import org.junit.jupiter.api.Test;

import static com.uiptv.service.LitePlayerFfmpegService.PlaybackStrategy.COPY;
import static com.uiptv.service.LitePlayerFfmpegService.PlaybackStrategy.DIRECT;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void chooseStrategy_keepsUnsupportedHevcTransportStreamDirectWithoutTranscodeSupport() {
        LitePlayerFfmpegService.ProbeResult probe = new LitePlayerFfmpegService.ProbeResult("mpegts", "hevc", "aac");
        assertEquals(DIRECT, LitePlayerFfmpegService.chooseStrategy("http://example.test/live?extension=ts", probe, false));
    }

    @Test
    void chooseStrategy_keepsLikelyDirectMp4PlaybackDirect() {
        assertEquals(DIRECT, LitePlayerFfmpegService.chooseStrategy("http://example.test/video.mp4", false));
    }

    @Test
    void chooseStrategy_keepsUnsupportedHevcMp4DirectWithoutTranscodeSupport() {
        LitePlayerFfmpegService.ProbeResult probe = new LitePlayerFfmpegService.ProbeResult("mp4", "hevc", "aac");
        assertEquals(DIRECT, LitePlayerFfmpegService.chooseStrategy("http://example.test/video.mp4", probe, false));
    }

    @Test
    void chooseStrategy_usesCopyWhenCodecsAreCompatibleButContainerIsNotDirect() {
        LitePlayerFfmpegService.ProbeResult probe = new LitePlayerFfmpegService.ProbeResult("matroska,webm", "h264", "aac");
        assertEquals(COPY, LitePlayerFfmpegService.chooseStrategy("http://example.test/video.mkv", probe, false));
    }

    @Test
    void chooseStrategy_keepsUnsupportedVideoCodecDirectWhenContainerNeedsCompatibilityFallback() {
        LitePlayerFfmpegService.ProbeResult probe = new LitePlayerFfmpegService.ProbeResult("matroska,webm", "hevc", "aac");
        assertEquals(DIRECT, LitePlayerFfmpegService.chooseStrategy("http://example.test/video.mkv", probe, false));
    }
}
