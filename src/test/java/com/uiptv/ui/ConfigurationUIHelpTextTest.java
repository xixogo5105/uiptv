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
}
