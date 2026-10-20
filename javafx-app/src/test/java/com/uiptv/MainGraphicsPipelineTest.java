package com.uiptv;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MainGraphicsPipelineTest {
    @Test
    void macOsUsesSafeFallbackUnlessTheUserAlreadyConfiguredARenderer() {
        String originalOs = System.getProperty("os.name");
        String originalOrder = System.getProperty("prism.order");
        try {
            System.setProperty("os.name", "Mac OS X");
            System.clearProperty("prism.order");
            Main.configureGraphicsPipeline();
            assertEquals("es2,sw", System.getProperty("prism.order"));

            System.setProperty("prism.order", "custom");
            Main.configureGraphicsPipeline();
            assertEquals("custom", System.getProperty("prism.order"));

            System.setProperty("os.name", "Linux");
            System.clearProperty("prism.order");
            Main.configureGraphicsPipeline();
            assertNull(System.getProperty("prism.order"));
        } finally {
            restoreProperty("os.name", originalOs);
            restoreProperty("prism.order", originalOrder);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
