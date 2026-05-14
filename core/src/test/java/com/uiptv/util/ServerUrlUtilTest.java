package com.uiptv.util;

import com.uiptv.service.ConfigurationService;
import com.uiptv.model.Configuration;
import com.uiptv.server.UIptvServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServerUrlUtilTest {

    @BeforeEach
    void resetFakeServer() {
        UIptvServer.reset();
    }

    @Test
    void testGetLocalServerUrl_Default() {
        try (MockedStatic<ConfigurationService> mockedService = Mockito.mockStatic(ConfigurationService.class)) {
            ConfigurationService service = mock(ConfigurationService.class);
            Configuration config = new Configuration();
            when(service.read()).thenReturn(config);
            mockedService.when(ConfigurationService::getInstance).thenReturn(service);

            assertEquals("http://0.0.0.0:8888", ServerUrlUtil.getLocalServerUrl());
        }
    }

    @Test
    void testGetLocalServerUrl_CustomPort() {
        try (MockedStatic<ConfigurationService> mockedService = Mockito.mockStatic(ConfigurationService.class)) {
            ConfigurationService service = mock(ConfigurationService.class);
            Configuration config = new Configuration();
            config.setServerPort("9090");
            when(service.read()).thenReturn(config);
            mockedService.when(ConfigurationService::getInstance).thenReturn(service);

            assertEquals("http://0.0.0.0:9090", ServerUrlUtil.getLocalServerUrl());
        }
    }

    @Test
    void getLocalServerUrl_fallsBackWhenConfigurationFails() {
        try (MockedStatic<ConfigurationService> mockedService = Mockito.mockStatic(ConfigurationService.class)) {
            ConfigurationService service = mock(ConfigurationService.class);
            when(service.read()).thenThrow(new IllegalStateException("db unavailable"));
            mockedService.when(ConfigurationService::getInstance).thenReturn(service);

            assertEquals("http://0.0.0.0:8888", ServerUrlUtil.getLocalServerUrl());
        }
    }

    @Test
    void serverReflectionFacade_invokesRuntimeModule() throws Exception {
        assertFalse(ServerUrlUtil.isServerRunning());

        ServerUrlUtil.installServerShutdownHook();
        ServerUrlUtil.startServer();
        ServerUrlUtil.startServerChecked();

        assertTrue(UIptvServer.running);
        assertTrue(ServerUrlUtil.isServerRunning());
        assertTrue(ServerUrlUtil.ensureServerStarted());
        UIptvServer.ensureStartedResult = false;
        assertFalse(ServerUrlUtil.ensureServerStarted());
        UIptvServer.ensureStartedResult = true;
        assertTrue(ServerUrlUtil.ensureServerForWebPlayback());

        ServerUrlUtil.stopServer();

        assertFalse(UIptvServer.running);
        assertEquals(2, UIptvServer.startCalls);
        assertEquals(3, UIptvServer.ensureStartedCalls);
        assertEquals(1, UIptvServer.stopCalls);
    }

    @Test
    void serverReflectionFacade_wrapsCheckedStartFailures() {
        UIptvServer.startFailure = new IOException("bind failed");

        UncheckedIOException thrown = assertThrows(UncheckedIOException.class, ServerUrlUtil::startServer);

        assertEquals("Unable to start local web server", thrown.getMessage());
        assertEquals("bind failed", thrown.getCause().getMessage());
        assertThrows(IOException.class, ServerUrlUtil::startServerChecked);
    }

    @Test
    void serverReflectionFacade_handlesEnsureFailureAndUncheckedRuntimeFailure() {
        UIptvServer.ensureFailure = new IOException("port busy");

        assertFalse(ServerUrlUtil.ensureServerForWebPlayback());
        assertThrows(IOException.class, ServerUrlUtil::ensureServerStarted);

        UIptvServer.ensureFailure = null;
        UIptvServer.runtimeFailure = new IllegalStateException("bad state");
        IllegalStateException thrown = assertThrows(IllegalStateException.class, ServerUrlUtil::ensureServerStarted);
        assertEquals("bad state", thrown.getMessage());
        assertFalse(ServerUrlUtil.isServerRunning());
        ServerUrlUtil.stopServer();
    }

    @Test
    void serverReflectionFacade_wrapsUnexpectedInvocationCauses() {
        UIptvServer.errorFailure = new AssertionError("boom");

        IOException checked = assertThrows(IOException.class, ServerUrlUtil::startServerChecked);
        assertEquals("Unable to invoke local web server method 'start'", checked.getMessage());
        assertInstanceOf(AssertionError.class, checked.getCause());

        IllegalStateException unchecked = assertThrows(IllegalStateException.class,
                () -> invokePrivate("invokeServerBooleanUnchecked", "isRunning"));
        assertEquals("Unable to invoke local web server method 'isRunning'", unchecked.getMessage());
    }

    private Object invokePrivate(String methodName, String argument) throws Exception {
        Method method = ServerUrlUtil.class.getDeclaredMethod(methodName, String.class);
        method.setAccessible(true);
        try {
            return method.invoke(null, argument);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }
}
