package com.uiptv.server;

import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import com.uiptv.testsupport.DbBackedTest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class UIptvServerTest extends DbBackedTest {

    @Test
    void startEnsureStop_lifecycleWorks() throws Exception {
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setServerPort("0");
        ConfigurationService.getInstance().save(configuration);

        try {
            assertTrue(UIptvServer.ensureStarted());
            assertFalse(UIptvServer.ensureStarted());
            assertTrue(UIptvServer.isRunning());
        } finally {
            UIptvServer.stop();
        }

        assertFalse(UIptvServer.isRunning());
        UIptvServer.stop();
    }

    @Test
    void getHttpPort_defaultsWhenBlank() throws Exception {
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setServerPort("");
        ConfigurationService.getInstance().save(configuration);

        Method method = UIptvServer.class.getDeclaredMethod("getHttpPort");
        method.setAccessible(true);
        assertEquals("8888", method.invoke(null));
    }

    @Test
    void startUsesConfiguredPort() throws Exception {
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setServerPort("0");
        ConfigurationService.getInstance().save(configuration);

        try {
            UIptvServer.start();
            assertTrue(UIptvServer.isRunning());
        } finally {
            UIptvServer.stop();
        }
    }
}
