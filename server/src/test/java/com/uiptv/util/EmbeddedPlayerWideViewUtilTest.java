package com.uiptv.util;

import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmbeddedPlayerWideViewUtilTest {

    @AfterEach
    void tearDown() {
        EmbeddedPlayerWideViewUtil.resetDependencies();
    }

    @Test
    void wideView_disabled_whenConfigurationMissing() {
        ConfigurationService service = mock(ConfigurationService.class);
        when(service.read()).thenReturn(null);
        EmbeddedPlayerWideViewUtil.configureDependencies(service);

        assertFalse(EmbeddedPlayerWideViewUtil.isWideViewEnabled());
    }

    @Test
    void wideView_disabled_whenEmbeddedPlayerDisabled_evenIfWideViewEnabled() {
        ConfigurationService service = mock(ConfigurationService.class);
        Configuration configuration = new Configuration();
        configuration.setEmbeddedPlayer(false);
        configuration.setWideView(true);
        when(service.read()).thenReturn(configuration);
        EmbeddedPlayerWideViewUtil.configureDependencies(service);

        assertFalse(EmbeddedPlayerWideViewUtil.isWideViewEnabled());
    }

    @Test
    void wideView_disabled_whenWideViewFlagDisabled() {
        ConfigurationService service = mock(ConfigurationService.class);
        Configuration configuration = new Configuration();
        configuration.setEmbeddedPlayer(true);
        configuration.setWideView(false);
        when(service.read()).thenReturn(configuration);
        EmbeddedPlayerWideViewUtil.configureDependencies(service);

        assertFalse(EmbeddedPlayerWideViewUtil.isWideViewEnabled());
    }

    @Test
    void wideView_enabled_only_whenEmbeddedAndWideViewAreBothEnabled() {
        ConfigurationService service = mock(ConfigurationService.class);
        Configuration configuration = new Configuration();
        configuration.setEmbeddedPlayer(true);
        configuration.setWideView(true);
        when(service.read()).thenReturn(configuration);
        EmbeddedPlayerWideViewUtil.configureDependencies(service);

        assertTrue(EmbeddedPlayerWideViewUtil.isWideViewEnabled());
    }
}
