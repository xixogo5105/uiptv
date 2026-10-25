package com.uiptv;

import org.junit.jupiter.api.Test;
import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MainGraphicsPipelineTest {
    @Test
    void mainStartsLightweightMode() {
        Configuration configuration = new Configuration();
        configuration.setLightweightModeEnabled(true);
        ConfigurationService service = mock(ConfigurationService.class);
        when(service.read()).thenReturn(configuration);
        try (MockedStatic<ConfigurationService> configurationStatic = mockStatic(ConfigurationService.class);
             MockedStatic<com.uiptv.util.AppLog> appLog = mockStatic(com.uiptv.util.AppLog.class);
             MockedStatic<com.uiptv.lightweight.LightweightApp> lightweight = mockStatic(com.uiptv.lightweight.LightweightApp.class)) {
            configurationStatic.when(ConfigurationService::getInstance).thenReturn(service);
            appLog.when(() -> com.uiptv.util.AppLog.isShowLogsRequested(new String[]{"--show-logs"})).thenReturn(true);
            Main.main(new String[]{"--show-logs"});
            lightweight.verify(com.uiptv.lightweight.LightweightApp::launch);
        }
    }

    @Test
    void mainFallsBackToFullApplicationWhenLightweightModeIsDisabled() {
        Configuration configuration = new Configuration();
        configuration.setLightweightModeEnabled(false);
        ConfigurationService service = mock(ConfigurationService.class);
        when(service.read()).thenReturn(configuration);
        try (MockedStatic<ConfigurationService> configurationStatic = mockStatic(ConfigurationService.class);
             MockedStatic<com.uiptv.util.AppLog> appLog = mockStatic(com.uiptv.util.AppLog.class);
             MockedStatic<com.uiptv.ui.RootApplication> root = mockStatic(com.uiptv.ui.RootApplication.class)) {
            configurationStatic.when(ConfigurationService::getInstance).thenReturn(service);
            appLog.when(() -> com.uiptv.util.AppLog.isShowLogsRequested(new String[0])).thenReturn(false);
            Main.main(new String[0]);
            root.verify(() -> com.uiptv.ui.RootApplication.main(new String[0]));
        }
    }

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
