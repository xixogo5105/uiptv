package com.uiptv.util;

import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmbeddedPlayerWideViewUtilTest {

    @Test
    void wideView_disabled_whenConfigurationMissing() {
        try (MockedStatic<ConfigurationService> mockedService = Mockito.mockStatic(ConfigurationService.class)) {
            ConfigurationService service = mock(ConfigurationService.class);
            when(service.read()).thenReturn(null);
            mockedService.when(ConfigurationService::getInstance).thenReturn(service);

            assertFalse(EmbeddedPlayerWideViewUtil.isWideViewEnabled());
        }
    }

    @Test
    void wideView_disabled_whenEmbeddedPlayerDisabled_evenIfWideViewEnabled() {
        try (MockedStatic<ConfigurationService> mockedService = Mockito.mockStatic(ConfigurationService.class)) {
            ConfigurationService service = mock(ConfigurationService.class);
            Configuration configuration = new Configuration();
            configuration.setEmbeddedPlayer(false);
            configuration.setWideView(true);
            when(service.read()).thenReturn(configuration);
            mockedService.when(ConfigurationService::getInstance).thenReturn(service);

            assertFalse(EmbeddedPlayerWideViewUtil.isWideViewEnabled());
        }
    }

    @Test
    void wideView_disabled_whenWideViewFlagDisabled() {
        try (MockedStatic<ConfigurationService> mockedService = Mockito.mockStatic(ConfigurationService.class)) {
            ConfigurationService service = mock(ConfigurationService.class);
            Configuration configuration = new Configuration();
            configuration.setEmbeddedPlayer(true);
            configuration.setWideView(false);
            when(service.read()).thenReturn(configuration);
            mockedService.when(ConfigurationService::getInstance).thenReturn(service);

            assertFalse(EmbeddedPlayerWideViewUtil.isWideViewEnabled());
        }
    }

    @Test
    void wideView_enabled_only_whenEmbeddedAndWideViewAreBothEnabled() {
        try (MockedStatic<ConfigurationService> mockedService = Mockito.mockStatic(ConfigurationService.class)) {
            ConfigurationService service = mock(ConfigurationService.class);
            Configuration configuration = new Configuration();
            configuration.setEmbeddedPlayer(true);
            configuration.setWideView(true);
            when(service.read()).thenReturn(configuration);
            mockedService.when(ConfigurationService::getInstance).thenReturn(service);

            assertTrue(EmbeddedPlayerWideViewUtil.isWideViewEnabled());
        }
    }
}

