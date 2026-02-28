package com.uiptv.util;

import com.uiptv.service.ConfigurationService;
import com.uiptv.model.Configuration;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ServerUrlUtilTest {

    @Test
    public void testGetLocalServerUrl_Default() {
        try (MockedStatic<ConfigurationService> mockedService = Mockito.mockStatic(ConfigurationService.class)) {
            ConfigurationService service = mock(ConfigurationService.class);
            Configuration config = new Configuration();
            when(service.read()).thenReturn(config);
            mockedService.when(ConfigurationService::getInstance).thenReturn(service);

            assertEquals("http://127.0.0.1:8888", ServerUrlUtil.getLocalServerUrl());
        }
    }

    @Test
    public void testGetLocalServerUrl_CustomPort() {
        try (MockedStatic<ConfigurationService> mockedService = Mockito.mockStatic(ConfigurationService.class)) {
            ConfigurationService service = mock(ConfigurationService.class);
            Configuration config = new Configuration();
            config.setServerPort("9090");
            when(service.read()).thenReturn(config);
            mockedService.when(ConfigurationService::getInstance).thenReturn(service);

            assertEquals("http://127.0.0.1:9090", ServerUrlUtil.getLocalServerUrl());
        }
    }
}
