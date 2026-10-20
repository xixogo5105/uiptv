package com.uiptv.lightweight;

import com.uiptv.application.ConfigurationApplicationService;
import com.uiptv.db.SQLConnection;
import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import com.uiptv.util.ServerUrlUtil;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightweightAppCoverageTest {
    @TempDir
    Path tempDir;

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.startup(latch::countDown);
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @BeforeEach
    void setUpDatabase() {
        SQLConnection.setDatabasePath(tempDir.resolve("lightweight.db").toString());
    }

    @AfterEach
    void releaseDatabase() {
        SQLConnection.releaseMemory();
    }

    @Test
    void buildsSectionsTogglesLogsAndAppliesThemes() throws Exception {
        runOnFx(() -> {
            LightweightApp app = new LightweightApp();
            setField(app, "configurationService", ConfigurationService.getInstance());
            setField(app, "configurationApplicationService", ConfigurationApplicationService.getInstance());

            BorderPane root = invoke(app, "buildRoot", BorderPane.class);
            assertEquals(2, ((javafx.scene.layout.VBox) root.getTop()).getChildren().size());
            Label status = field(app, "serverStatusLabel", Label.class);
            assertEquals("Webserver Stopped", status.getText());

            invoke(app, "setLogsVisible", void.class, true);
            invoke(app, "appendLog", void.class, "hello");
            ListView<?> logs = field(app, "logListView", ListView.class);
            assertTrue(logs.isVisible());
            assertEquals(1, logs.getItems().size());
            assertTrue(field(app, "clearButton", Button.class).isVisible());

            invoke(app, "setLogsVisible", void.class, false);
            assertFalse(logs.isVisible());
            invoke(app, "appendLog", void.class, "background");
            invoke(app, "updateClearButtonVisibility", void.class);
            assertTrue(field(app, "clearButton", Button.class).isVisible());

            Configuration configuration = ConfigurationService.getInstance().read();
            configuration.applyThemeMode(com.uiptv.model.ThemeMode.DARK);
            ConfigurationService.getInstance().save(configuration);
            invoke(app, "applyTheme", void.class, root);
            assertTrue(root.getStyleClass().contains("dark-theme"));

            configuration.applyThemeMode(com.uiptv.model.ThemeMode.LIGHT);
            ConfigurationService.getInstance().save(configuration);
            invoke(app, "applyTheme", void.class, root);
            assertFalse(root.getStyleClass().contains("dark-theme"));

            try (MockedStatic<ServerUrlUtil> _ = mockStatic(ServerUrlUtil.class)) {
                when(ServerUrlUtil.isServerRunning()).thenReturn(false, true);
                when(ServerUrlUtil.getConfiguredServerPort()).thenReturn("8080");
                when(ServerUrlUtil.getConfiguredHttpsServerPort()).thenReturn("8443");
                when(ServerUrlUtil.isHttpsServerEnabled()).thenReturn(false, true);
                invoke(app, "refreshServerStatus", void.class);
                invoke(app, "toggleServerAction", void.class);
                invoke(app, "refreshServerStatus", void.class);
                invoke(app, "toggleServerAction", void.class);
                assertTrue(field(app, "httpsPortLabel", Label.class).isVisible());
            }

            Stage stage = new Stage();
            invoke(app, "start", void.class, stage);
            stage.close();

            Hyperlink toggle = find(root, Hyperlink.class);
            assertNotNull(toggle);
            toggle.fire();
            toggle.fire();
            return null;
        });
    }

    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        Field field = LightweightApp.class.getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = LightweightApp.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T invoke(Object target, String name, Class<T> returnType, Object... args) throws Exception {
        Method method = findMethod(name, args.length);
        method.setAccessible(true);
        Object result = method.invoke(target, args);
        return returnType == void.class ? null : returnType.cast(result);
    }

    private static Method findMethod(String name, int parameterCount) {
        for (Method method : LightweightApp.class.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        throw new IllegalArgumentException("Missing method " + name);
    }

    private static <T extends Node> T find(Node node, Class<T> type) {
        if (type.isInstance(node)) {
            return type.cast(node);
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                T result = find(child, type);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static <T> T runOnFx(FxTask<T> task) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return task.call();
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(task.call());
            } catch (Throwable failure) {
                error.set(failure);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
        return result.get();
    }

    @FunctionalInterface
    private interface FxTask<T> {
        T call() throws Exception;
    }
}
