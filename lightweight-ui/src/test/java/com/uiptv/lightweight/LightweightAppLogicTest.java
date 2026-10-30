package com.uiptv.lightweight;

import com.uiptv.application.ConfigurationApplicationService;
import com.uiptv.model.ThemeMode;
import com.uiptv.util.ServerUrlUtil;
import com.uiptv.service.ConfigurationService;
import javafx.application.Platform;
import javafx.scene.layout.BorderPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
    @BeforeAll
    static void initFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (IllegalStateException alreadyStarted) {
            assertTrue(alreadyStarted.getMessage().contains("Toolkit already initialized"));
        }
    }
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

    @Test
    void buildsAndUpdatesControlsWithoutShowingAWindow() throws Exception {
        runFx(() -> {
            LightweightApp app = new LightweightApp();
            ConfigurationService configuration = mock(ConfigurationService.class);
            ConfigurationApplicationService application = mock(ConfigurationApplicationService.class);
            when(configuration.read()).thenReturn(new com.uiptv.model.Configuration());
            when(application.isServerRunning()).thenReturn(false, true);
            set(app, "configurationService", configuration);
            set(app, "configurationApplicationService", application);
            try (MockedStatic<ServerUrlUtil> urls = mockStatic(ServerUrlUtil.class)) {
                urls.when(ServerUrlUtil::getConfiguredServerPort).thenReturn("8080");
                urls.when(ServerUrlUtil::isHttpsServerEnabled).thenReturn(false);
                urls.when(ServerUrlUtil::getConfiguredHttpsServerPort).thenReturn("8443");
                BorderPane root = (BorderPane) invoke(app, "buildRoot");
                assertEquals(2, ((javafx.scene.layout.VBox) root.getTop()).getChildren().size());
                invoke(app, "appendLog", "hello");
                invoke(app, "setLogsVisible", true);
                invoke(app, "updateClearButtonVisibility");
                invoke(app, "refreshServerStatus");
                assertEquals("Webserver Running", ((Label) get(app, "serverStatusLabel")).getText());
                assertEquals("HTTP: 8080", ((Label) get(app, "httpPortLabel")).getText());
                assertFalse(((Label) get(app, "httpsPortLabel")).isVisible());
                assertEquals(1, ((ListView<?>) get(app, "logListView")).getItems().size());
                invoke(app, "setLogsVisible", false);
                assertFalse(((Button) get(app, "clearButton")).isVisible());
            }
            return null;
        });
    }

    private static Object get(Object target, String field) throws Exception {
        Field value = target.getClass().getDeclaredField(field);
        value.setAccessible(true);
        return value.get(target);
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field property = target.getClass().getDeclaredField(field);
        property.setAccessible(true);
        property.set(target, value);
    }

    private static Object invoke(Object target, String name, Object... args) throws Exception {
        for (Method method : target.getClass().getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                method.setAccessible(true);
                return method.invoke(target, args);
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static <T> T runFx(java.util.concurrent.Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) return action.call();
        CountDownLatch latch = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<T> result = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        Platform.runLater(() -> {
            try { result.set(action.call()); } catch (Throwable error) { failure.set(error); } finally { latch.countDown(); }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new RuntimeException(failure.get());
        return result.get();
    }
}
