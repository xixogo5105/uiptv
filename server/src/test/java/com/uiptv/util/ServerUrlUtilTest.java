package com.uiptv.util;

import com.uiptv.service.ConfigurationService;
import com.uiptv.model.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServerUrlUtilTest {

    @AfterEach
    void tearDown() {
        ServerUrlUtil.resetDependencies();
    }

    @Test
    void testGetLocalServerUrl_Default() {
        ConfigurationService service = mock(ConfigurationService.class);
        Configuration config = new Configuration();
        when(service.read()).thenReturn(config);
        ServerUrlUtil.configureDependencies(() -> service);

        assertEquals("http://127.0.0.1:8888", ServerUrlUtil.getLocalServerUrl());
    }

    @Test
    void testGetLocalServerUrl_CustomPort() {
        ConfigurationService service = mock(ConfigurationService.class);
        Configuration config = new Configuration();
        config.setServerPort("9090");
        when(service.read()).thenReturn(config);
        ServerUrlUtil.configureDependencies(() -> service);

        assertEquals("http://127.0.0.1:9090", ServerUrlUtil.getLocalServerUrl());
    }
}
