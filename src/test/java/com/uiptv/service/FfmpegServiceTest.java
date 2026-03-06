package com.uiptv.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfmpegServiceTest {

    @Test
    void buildHlsCommand_usesCopyProfileForWebTransmuxing() {
        List<String> command = FfmpegService.buildHlsCommand(
                "http://example.test/live.ts",
                "http://127.0.0.1:8888/hls-upload/stream.m3u8",
                false
        );

        assertTrue(command.contains("-c"));
        assertTrue(command.contains("copy"));
        assertFalse(command.contains("libx264"));
        assertFalse(command.contains("aac"));
    }
}
