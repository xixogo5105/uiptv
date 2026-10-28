package com.uiptv.lightweight;

import com.uiptv.application.ConfigurationApplicationService;
import com.uiptv.model.ThemeMode;
import com.uiptv.util.ServerUrlUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LightweightAppLogicTest {
    @Test
    void readsRunningStateAndConfiguredPortsWithoutStartingServer() throws IOException {
        ConfigurationApplicationService service = mock(ConfigurationApplicationService.class);
        when(service.isServerRunning()).thenReturn(true);

        try (MockedStatic<ServerUrlUtil> urls = mockStatic(ServerUrlUtil.class)) {
            urls.when(ServerUrlUtil::getConfiguredServerPort).thenReturn("8080");
            urls.when(ServerUrlUtil::isHttpsServerEnabled).thenReturn(true);
            urls.when(ServerUrlUtil::getConfiguredHttpsServerPort).thenReturn("8443");

            assertEquals(new LightweightAppLogic.ServerStatus(true, "8080", true, "8443"),
                    LightweightAppLogic.readServerStatus(service));

            verify(service, never()).startServer();
            verify(service, never()).stopServer();
        }
    }

    @Test
    void readsStoppedStateWhenHttpsIsDisabled() throws IOException {
        ConfigurationApplicationService service = mock(ConfigurationApplicationService.class);
        when(service.isServerRunning()).thenReturn(false);

        try (MockedStatic<ServerUrlUtil> urls = mockStatic(ServerUrlUtil.class)) {
            urls.when(ServerUrlUtil::getConfiguredServerPort).thenReturn("9090");
            urls.when(ServerUrlUtil::isHttpsServerEnabled).thenReturn(false);
            urls.when(ServerUrlUtil::getConfiguredHttpsServerPort).thenReturn("9443");

            assertEquals(new LightweightAppLogic.ServerStatus(false, "9090", false, "9443"),
                    LightweightAppLogic.readServerStatus(service));

            verify(service, never()).startServer();
            verify(service, never()).stopServer();
        }
    }

    @Test
    void toggleStartsOnlyWhenServiceReportsStopped() throws IOException {
        ConfigurationApplicationService service = mock(ConfigurationApplicationService.class);
        when(service.isServerRunning()).thenReturn(false);

        LightweightAppLogic.toggleServer(service);

        verify(service).startServer();
        verify(service, never()).stopServer();
    }

    @Test
    void toggleStopsOnlyWhenServiceReportsRunning() throws IOException {
        ConfigurationApplicationService service = mock(ConfigurationApplicationService.class);
        when(service.isServerRunning()).thenReturn(true);

        LightweightAppLogic.toggleServer(service);

        verify(service).stopServer();
        verify(service, never()).startServer();
    }

    @Test
    void togglePropagatesStartFailure() throws IOException {
        ConfigurationApplicationService service = mock(ConfigurationApplicationService.class);
        when(service.isServerRunning()).thenReturn(false);
        IOException failure = new IOException("unavailable");
        doThrow(failure).when(service).startServer();

        IOException actual = org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> LightweightAppLogic.toggleServer(service));

        assertEquals(failure, actual);
    }

    @Test
    void clearButtonRemainsAvailableForVisibleLogsOrExistingEntries() {
        assertFalse(LightweightAppLogic.shouldShowClearButton(false, 0));
        assertTrue(LightweightAppLogic.shouldShowClearButton(true, 0));
        assertTrue(LightweightAppLogic.shouldShowClearButton(false, 1));
        assertTrue(LightweightAppLogic.shouldShowClearButton(true, 1));
    }

    @Test
    void resolvesLightDarkAndSystemThemeChoices() {
        assertFalse(LightweightAppLogic.isDarkTheme(ThemeMode.LIGHT, true));
        assertTrue(LightweightAppLogic.isDarkTheme(ThemeMode.DARK, false));
        assertFalse(LightweightAppLogic.isDarkTheme(ThemeMode.SYSTEM, false));
        assertTrue(LightweightAppLogic.isDarkTheme(ThemeMode.SYSTEM, true));
    }
}
