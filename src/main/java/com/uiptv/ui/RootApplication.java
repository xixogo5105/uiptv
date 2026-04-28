package com.uiptv.ui;

import com.uiptv.model.Configuration;
import com.uiptv.player.MediaPlayerFactory;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.DatabaseSyncService;
import com.uiptv.ui.main.BaseMainApplicationUI;
import com.uiptv.ui.main.MainApplicationUI;
import com.uiptv.ui.main.WideMainApplicationUI;
import com.uiptv.util.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.Arrays;

import static java.lang.System.exit;

public class RootApplication extends Application {
    public static final int GUIDED_MAX_WIDTH_PIXELS = 1368;
    public static final int GUIDED_MAX_HEIGHT_PIXELS = 1920;
    private static final DatabaseSyncService databaseSyncService = DatabaseSyncService.getInstance();
    private static Stage primaryStage;
    private static String currentTheme;
    private final ConfigurationService configurationService = ConfigurationService.getInstance();

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "UIPTV");
        ServerUrlUtil.installServerShutdownHook();
        boolean showLogsEnabled = hasShowLogsArg(args);
        String[] filteredArgs = removeShowLogsArg(args);
        if (showLogsEnabled) {
            System.setProperty("uiptv.showLogs", "true");
        } else {
            disableTerminalLogs();
        }

        if (filteredArgs != null && filteredArgs.length > 0 && "sync".equalsIgnoreCase(filteredArgs[0])) {
            handleSync(filteredArgs);
            exit(0);
        } else if (filteredArgs != null && Arrays.stream(filteredArgs).anyMatch(s -> s.toLowerCase().contains("headless"))) {
            ServerUrlUtil.startServer();
        } else {
            launch();
        }
    }

    private static void handleSync(String[] args) {
        if (args.length != 3) {
            com.uiptv.util.AppLog.addErrorLog(RootApplication.class, "Usage: sync <source_db_path> <target_db_path>");
            exit(1);
        }
        String sourceDB = stripWrappingQuotes(args[1]);
        String targetDB = stripWrappingQuotes(args[2]);
        try {
            syncDatabases(sourceDB, targetDB);
            com.uiptv.util.AppLog.addInfoLog(RootApplication.class, "Sync complete!");
        } catch (SQLException e) {
            com.uiptv.util.AppLog.addErrorLog(RootApplication.class, "Error syncing tables: " + e.getMessage());
        }
    }

    public static void syncDatabases(String sourceDB, String targetDB) throws SQLException {
        databaseSyncService.syncDatabases(sourceDB, targetDB);
    }

    public static void syncDatabases(String sourceDB, String targetDB, boolean syncConfiguration, boolean syncExternalPlayerPaths) throws SQLException {
        databaseSyncService.syncDatabases(sourceDB, targetDB, syncConfiguration, syncExternalPlayerPaths);
    }

    private static String stripWrappingQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static boolean hasShowLogsArg(String[] args) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if (isShowLogsArg(arg)) {
                return true;
            }
        }
        return false;
    }

    private static String[] removeShowLogsArg(String[] args) {
        if (args == null || args.length == 0) {
            return args;
        }
        return Arrays.stream(args)
                .filter(arg -> !isShowLogsArg(arg))
                .toArray(String[]::new);
    }

    private static boolean isShowLogsArg(String arg) {
        if (arg == null) {
            return false;
        }
        return "show-logs".equalsIgnoreCase(arg) || "--show-logs".equalsIgnoreCase(arg);
    }

    private static void disableTerminalLogs() {
        PrintStream sink = new PrintStream(OutputStream.nullOutputStream(), true);
        System.setOut(sink);
        System.setErr(sink);
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void setPrimaryStage(Stage stage) {
        primaryStage = stage;
    }

    public static String getCurrentTheme() {
        return currentTheme;
    }

    public static void applyTheme(Scene scene, Class<?> themeResourceClass, boolean darkTheme, int zoomPercent) {
        if (scene == null || scene.getRoot() == null) {
            return;
        }

        currentTheme = ThemeStylesheetResolver.resolveStylesheetUrl(
                themeResourceClass,
                darkTheme,
                zoomPercent
        );
        scene.getStylesheets().clear();
        scene.getStylesheets().add(currentTheme);
        scene.getRoot().styleProperty().unbind();
        scene.getRoot().setStyle(ThemeStylesheetResolver.buildSceneRootStyle(zoomPercent));
        I18n.applySceneOrientation(scene);
        StyleClassDecorator.decorate(scene.getRoot());
    }

    @Override
    public final void start(Stage primaryStage) throws IOException {
        setPrimaryStage(primaryStage);
        ServerUrlUtil.setHostServices(getHostServices());
        Configuration bootConfiguration = configurationService.read();
        I18n.initialize(bootConfiguration == null ? null : bootConfiguration.getLanguageLocale());

        boolean embeddedEnabled = bootConfiguration != null && bootConfiguration.isEmbeddedPlayer();
        boolean embeddedWideViewEnabled = EmbeddedPlayerWideViewUtil.isWideViewEnabled();

        primaryStage.setOnCloseRequest(event -> {
            Platform.exit();
            System.exit(0);
        });
        primaryStage.setTitle(I18n.tr("appTitle"));
        applyMaximizedBounds(primaryStage);
        primaryStage.getIcons().add(new Image("file:resource/icon.ico"));
        Scene loadingScene = createLoadingScene();
        primaryStage.setScene(loadingScene);
        primaryStage.show();
        Platform.runLater(() -> applyMaximizedBounds(primaryStage));

        Platform.runLater(() -> {
            BaseMainApplicationUI mainUiRoute = selectMainUiRoute(embeddedEnabled, embeddedWideViewEnabled);
            Scene scene = mainUiRoute.buildScene();
            I18n.applySceneOrientation(scene);
            primaryStage.setScene(scene);
            applyMaximizedBounds(primaryStage);
        });
    }

    private void applyMaximizedBounds(Stage stage) {
        if (stage == null) {
            return;
        }
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
        stage.setMaximized(true);
    }

    private BaseMainApplicationUI selectMainUiRoute(boolean embeddedEnabled, boolean embeddedWideViewEnabled) {
        if (embeddedEnabled && embeddedWideViewEnabled) {
            return new WideMainApplicationUI(
                    primaryStage,
                    getHostServices(),
                    configurationService,
                    this::configureFontStyles,
                    GUIDED_MAX_WIDTH_PIXELS,
                    GUIDED_MAX_HEIGHT_PIXELS
            );
        }

        return new MainApplicationUI(
                primaryStage,
                getHostServices(),
                configurationService,
                this::configureFontStyles,
                GUIDED_MAX_WIDTH_PIXELS,
                GUIDED_MAX_HEIGHT_PIXELS,
                embeddedEnabled
        );
    }

    @Override
    public void stop() {
        try {
            MediaPlayerFactory.release();
        } catch (Exception _) {
            // Best-effort shutdown: media teardown should not prevent server cleanup or app exit.
        }
        ServerUrlUtil.stopServerWithShutdownMessage();
        try {
            super.stop();
        } catch (Exception _) {
            // Best-effort shutdown: JavaFX stop hooks should not prevent process exit.
        }
    }

    private void configureFontStyles(Scene scene) {
        Configuration configuration = configurationService.read();
        applyTheme(
                scene,
                getClass(),
                configuration.isDarkTheme(),
                configurationService.getUiZoomPercent()
        );
    }

    private Scene createLoadingScene() {
        Label loadingLabel = new Label(I18n.tr("appTitle"));
        StackPane loadingRoot = new StackPane(loadingLabel);
        Scene loadingScene = new Scene(loadingRoot, GUIDED_MAX_WIDTH_PIXELS, GUIDED_MAX_HEIGHT_PIXELS);
        I18n.applySceneOrientation(loadingScene);
        configureFontStyles(loadingScene);
        return loadingScene;
    }
}
