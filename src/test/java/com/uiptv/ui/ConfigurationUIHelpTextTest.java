package com.uiptv.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationUIHelpTextTest {
    @Test
    void resolveChainHelpText_mentionsUseCasesAndDelayTradeoff() {
        String help = ConfigurationUI.resolveChainHelpText();

        assertTrue(help.contains("master playlist"));
        assertTrue(help.contains("relative HLS variants"));
        assertTrue(help.contains("lowest startup latency"));
        assertTrue(help.contains("small delay before playback starts"));
    }

    @Test
    void wideViewHelpText_mentionsLargeScreensAndLayoutTradeoff() {
        String help = ConfigurationUI.wideViewHelpText();

        assertTrue(help.contains("larger screens"));
        assertTrue(help.contains("small windows"));
        assertTrue(help.contains("not meant to change playback quality"));
    }

    @Test
    void ffmpegHelpTexts_coverFallbackAndTradeoffs() {
        String transcoding = ConfigurationUI.ffmpegTranscodingHelpText();
        String lite = ConfigurationUI.litePlayerFfmpegHelpText();

        assertTrue(transcoding.contains("re-encode streams"));
        assertTrue(transcoding.contains("lowest CPU usage"));
        assertTrue(lite.contains("fallback path"));
        assertTrue(lite.contains("extra playback delay"));
    }

    @Test
    void stripTrailingHelp_removesParentheticalSuffixOnly() {
        assertTrue(ConfigurationUI.stripTrailingHelp("Enable FFmpeg Transcoding (High CPU Usage)").equals("Enable FFmpeg Transcoding"));
        assertTrue(ConfigurationUI.stripTrailingHelp("Wide View").equals("Wide View"));
    }
}
