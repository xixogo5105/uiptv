package com.uiptv.ui;

import com.uiptv.model.Configuration;
import com.uiptv.player.MediaPlayerFactory;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.DatabaseSyncService;
import com.uiptv.ui.main.BaseMainApplicationUI;
import com.uiptv.ui.main.MainApplicationUI;
import com.uiptv.ui.main.WideMainApplicationUI;
import com.uiptv.util.EmbeddedPlayerWideViewUtil;
import com.uiptv.util.ServerUrlUtil;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.Arrays;

import static com.uiptv.util.StringUtils.isNotBlank;
import static java.lang.System.exit;

public class RootApplication extends Application {
    public static final int GUIDED_MAX_WIDTH_PIXELS = 1368;
    public static final int GUIDED_MAX_HEIGHT_PIXELS = 1920;
    public static Stage primaryStage;
    public static String currentTheme;
    private final ConfigurationService configurationService = ConfigurationService.getInstance();
    private static final DatabaseSyncService databaseSyncService = DatabaseSyncService.getInstance();

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "UIPTV");
        ServerUrlUtil.installServerShutdownHook();

        if (args == null || Arrays.stream(args).noneMatch(s -> s.equalsIgnoreCase("--show-logs"))) {
            PrintStream dummyStream = new PrintStream(new OutputStream() {
                @Override
                public void write(int b) {
                    // NO-OP
                }
            });
            System.setOut(dummyStream);
            System.setErr(dummyStream);
        }

        if (args != null && args.length > 0 && "sync".equalsIgnoreCase(args[0])) {
            handleSync(args);
            exit(0);
        } else if (args != null && Arrays.stream(args).anyMatch(s -> s.toLowerCase().contains("headless"))) {
            ServerUrlUtil.startServer();
        } else {
            System.setProperty("file.encoding", "UTF-8");
            java.nio.charset.Charset.defaultCharset();
            launch();
        }
    }

    private static void handleSync(String[] args) {
        if (args.length != 3) {
            LogDisplayUI.addLog("Usage: sync <first_db_path> <second_db_path>");
            exit(1);
        }
        String firstDB = args[1].replaceAll("^'|'$", "").replaceAll("^\"|\"$", "");
        String secondDB = args[2].replaceAll("^'|'$", "").replaceAll("^\"|\"$", "");
        try {
            syncDatabases(firstDB, secondDB);
            LogDisplayUI.addLog("Sync complete!");
        } catch (SQLException e) {
            LogDisplayUI.addLog("Error syncing tables: " + e.getMessage());
        }
    }

    public static void syncDatabases(String firstDB, String secondDB) throws SQLException {
        databaseSyncService.syncDatabases(firstDB, secondDB);
    }

    @Override
    public final void start(Stage primaryStage) throws IOException {
        RootApplication.primaryStage = primaryStage;
        ServerUrlUtil.setHostServices(getHostServices());

        boolean embeddedEnabled = configurationService.read().isEmbeddedPlayer();
        boolean embeddedWideViewEnabled = EmbeddedPlayerWideViewUtil.isWideViewEnabled();

        BaseMainApplicationUI mainUiRoute = selectMainUiRoute(embeddedEnabled, embeddedWideViewEnabled);
        Scene scene = mainUiRoute.buildScene(embeddedEnabled, embeddedWideViewEnabled);

        primaryStage.setOnCloseRequest(event -> {
            Platform.exit();
            System.exit(0);
        });
        primaryStage.setTitle("UIPTV");
        primaryStage.setMaximized(true);
        primaryStage.setScene(scene);
        primaryStage.getIcons().add(new Image("file:resource/icon.ico"));
        primaryStage.show();
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
                GUIDED_MAX_HEIGHT_PIXELS
        );
    }

    @Override
    public void stop() throws Exception {
        try {
            MediaPlayerFactory.release();
        } catch (Exception ignored) {
        }
        ServerUrlUtil.stopServerWithShutdownMessage();
        super.stop();
    }

    private void configureFontStyles(Scene scene) {
        Configuration configuration = configurationService.read();
        String customStylesheet = "";

        if (isNotBlank(configuration.getFontFamily())) {
            customStylesheet += Bindings.format("-fx-font-family: %s;", new SimpleStringProperty(configuration.getFontFamily())).getValueSafe();
        }
        if (isNotBlank(configuration.getFontSize())) {
            customStylesheet += Bindings.format("-fx-font-size: %s;", new SimpleStringProperty(configuration.getFontSize())).getValueSafe();
        }
        if (isNotBlank(configuration.getFontWeight())) {
            customStylesheet += Bindings.format("-fx-font-weight: %s;", new SimpleStringProperty(configuration.getFontWeight())).getValueSafe();
        }

        scene.getStylesheets().clear();
        String themeFileName = configuration.isDarkTheme() ? "dark-application.css" : "application.css";
        java.net.URL themeUrl = getClass().getResource("/" + themeFileName);
        currentTheme = themeUrl != null ? themeUrl.toExternalForm() : themeFileName;
        scene.getStylesheets().add(currentTheme);
        scene.getRoot().styleProperty().bind(Bindings.format(customStylesheet));
    }
}
