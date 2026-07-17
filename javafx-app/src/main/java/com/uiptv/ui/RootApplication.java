package com.uiptv.ui;

import com.uiptv.application.ConfigurationApplicationService;
import com.uiptv.model.Configuration;
import com.uiptv.player.MediaPlayerFactory;
import com.uiptv.service.ConfigurationChangeListener;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.DatabaseSyncService;
import com.uiptv.service.remotesync.RemoteSyncSessionService;
import com.uiptv.ui.main.BaseMainApplicationUI;
import com.uiptv.ui.main.MainApplicationUI;
import com.uiptv.ui.util.StyleClassDecorator;
import com.uiptv.ui.util.ThemeStylesheetResolver;
import com.uiptv.ui.util.UiI18n;
import com.uiptv.ui.util.UiServerUrlUtil;
import com.uiptv.util.AppLog;
import com.uiptv.util.I18n;
import com.uiptv.util.ServerUrlUtil;
import com.uiptv.widget.AppNavigationController;
import com.uiptv.widget.AppFonts;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Arrays;

import static java.lang.System.exit;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;

public class RootApplication extends Application {
    public static final int GUIDED_MAX_WIDTH_PIXELS = 1368;
    public static final int GUIDED_MAX_HEIGHT_PIXELS = 1920;
    private static final double TOP_PLAYER_EXPERIMENT_MIN_STAGE_WIDTH = 480;
    private static final String PRODUCT_TITLE = "UIPTV";
    private static final Duration TITLE_STATUS_REFRESH_INTERVAL = Duration.seconds(30);
    private static final int[] TITLEBAR_MARK_ICON_SIZES = {16, 24, 32};
    private static final double[] PRIMARY_STAGE_ICON_SIZES = {48, 64, 128, 256};
    private static final Color TITLEBAR_MARK_BACKGROUND = Color.rgb(91, 204, 214);
    private static final Color TITLEBAR_MARK_FOREGROUND = Color.WHITE;
    private static final DatabaseSyncService databaseSyncService = DatabaseSyncService.getInstance();
    private static final ConfigurationApplicationService configurationApplicationService = ConfigurationApplicationService.getInstance();
    private static Stage primaryStage;
    private static String currentTheme;
    private final ConfigurationService configurationService = ConfigurationService.getInstance();
    private final ConfigurationChangeListener titleConfigurationChangeListener =
            _ -> scheduleTitleUpdate();
    private final ChangeListener<AppNavigationController.Target> titleNavigationTargetChangeListener =
            (_, _, _) -> scheduleTitleUpdate();
    private Timeline titleStatusTimeline;

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "UIPTV");
        ServerUrlUtil.installServerShutdownHook();
        boolean showLogsEnabled = hasShowLogsArg(args);
        String[] filteredArgs = removeShowLogsArg(args);
        boolean syncMode = filteredArgs != null && filteredArgs.length > 0 && "sync".equalsIgnoreCase(filteredArgs[0]);
        boolean headlessMode = filteredArgs != null && Arrays.stream(filteredArgs).anyMatch(s -> s.toLowerCase().contains("headless"));
        System.setProperty("uiptv.headless", Boolean.toString(headlessMode));
        AppLog.setTerminalLoggingEnabled(showLogsEnabled || headlessMode || syncMode);

        if (syncMode) {
            handleSync(filteredArgs);
            exit(0);
        } else if (headlessMode) {
            AppLog.addInfoLog(RootApplication.class, "Starting UIPTV in headless mode.");
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

    public static DatabaseSyncService.DatabaseSyncReport syncDatabasesWithReport(String sourceDB,
                                                                                 String targetDB,
                                                                                 boolean syncConfiguration,
                                                                                 boolean syncExternalPlayerPaths,
                                                                                 DatabaseSyncService.SyncProgressListener progressListener) throws SQLException {
        return databaseSyncService.syncDatabasesWithReport(sourceDB, targetDB, syncConfiguration, syncExternalPlayerPaths, progressListener);
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

        AppFonts.load();
        currentTheme = ThemeStylesheetResolver.resolveStylesheetUrl(
                themeResourceClass,
                darkTheme,
                zoomPercent
        );
        scene.getStylesheets().clear();
        scene.getStylesheets().add(currentTheme);
        scene.getRoot().styleProperty().unbind();
        scene.getRoot().setStyle(ThemeStylesheetResolver.buildSceneRootStyle(zoomPercent));
        UiI18n.applySceneOrientation(scene);
        StyleClassDecorator.decorate(scene.getRoot());
    }

    @Override
    public final void start(Stage primaryStage) throws IOException {
        setPrimaryStage(primaryStage);
        UiServerUrlUtil.setHostServices(getHostServices());
        Configuration bootConfiguration = configurationService.read();
        I18n.initialize(bootConfiguration == null ? null : bootConfiguration.getLanguageLocale());
        FxRemoteSyncUiBridge remoteSyncUiBridge = new FxRemoteSyncUiBridge();
        RemoteSyncSessionService.getInstance().setApprovalPrompt(remoteSyncUiBridge);
        RemoteSyncSessionService.getInstance().setNotifier(remoteSyncUiBridge);

        boolean embeddedEnabled = bootConfiguration != null && bootConfiguration.isEmbeddedPlayer();

        primaryStage.setOnCloseRequest(event -> {
            Platform.exit();
            System.exit(0);
        });
        primaryStage.setMinWidth(TOP_PLAYER_EXPERIMENT_MIN_STAGE_WIDTH);
        registerTitleStatusUpdater();
        updatePrimaryStageTitle();
        applyMaximizedBounds(primaryStage);
        configurePrimaryStageIcon(primaryStage);
        Scene loadingScene = createLoadingScene();
        primaryStage.setScene(loadingScene);
        primaryStage.show();
        Platform.runLater(() -> applyMaximizedBounds(primaryStage));
        autoStartInternalServer(bootConfiguration);

        Platform.runLater(() -> {
            BaseMainApplicationUI mainUiRoute = selectMainUiRoute(embeddedEnabled);
            Scene scene = mainUiRoute.buildScene();
            UiI18n.applySceneOrientation(scene);
            primaryStage.setScene(scene);
            applyMaximizedBounds(primaryStage);
        });
    }

    private void configurePrimaryStageIcon(Stage stage) {
        if (stage == null) {
            return;
        }
        try {
            if (addPackagedIconVariants(stage)) {
                return;
            }
        } catch (IOException e) {
            AppLog.addWarningLog(RootApplication.class, "Failed to load packaged app icon: " + e.getMessage());
        }
        stage.getIcons().add(new Image("file:resource/icon.ico"));
    }

    private boolean addPackagedIconVariants(Stage stage) throws IOException {
        addTitlebarMarkIcons(stage);
        boolean loaded = !stage.getIcons().isEmpty();
        for (double size : PRIMARY_STAGE_ICON_SIZES) {
            try (InputStream stream = getClass().getResourceAsStream("/icon.png")) {
                if (stream == null) {
                    return loaded;
                }
                Image icon = new Image(stream, size, size, true, size > 48);
                if (!icon.isError()) {
                    stage.getIcons().add(icon);
                    loaded = true;
                }
            }
        }
        return loaded;
    }

    private void addTitlebarMarkIcons(Stage stage) {
        for (int size : TITLEBAR_MARK_ICON_SIZES) {
            stage.getIcons().add(createTitlebarMarkIcon(size));
        }
    }

    private Image createTitlebarMarkIcon(int size) {
        WritableImage image = new WritableImage(size, size);
        PixelWriter writer = image.getPixelWriter();
        double inset = Math.max(1, size * 0.06);
        double max = size - inset - 1;
        double radius = size * 0.24;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (insideRoundedRect(x, y, inset, inset, max, max, radius)) {
                    writer.setColor(x, y, TITLEBAR_MARK_BACKGROUND);
                }
            }
        }

        int stroke = Math.max(2, Math.round(size * 0.13f));
        int left = Math.round(size * 0.31f);
        int right = Math.round(size * 0.62f);
        int top = Math.round(size * 0.27f);
        int bottom = Math.round(size * 0.72f);
        fillRect(writer, left, top, stroke, bottom - top, TITLEBAR_MARK_FOREGROUND, size);
        fillRect(writer, right, top, stroke, bottom - top, TITLEBAR_MARK_FOREGROUND, size);
        fillRect(writer, left, bottom - stroke, right - left + stroke, stroke, TITLEBAR_MARK_FOREGROUND, size);
        return image;
    }

    private boolean insideRoundedRect(int x, int y, double left, double top, double right, double bottom, double radius) {
        double nearestX = Math.max(left + radius, Math.min(x, right - radius));
        double nearestY = Math.max(top + radius, Math.min(y, bottom - radius));
        double dx = x - nearestX;
        double dy = y - nearestY;
        return x >= left && x <= right && y >= top && y <= bottom && dx * dx + dy * dy <= radius * radius;
    }

    private void fillRect(PixelWriter writer, int x, int y, int width, int height, Color color, int imageSize) {
        for (int yy = Math.max(0, y); yy < Math.min(imageSize, y + height); yy++) {
            for (int xx = Math.max(0, x); xx < Math.min(imageSize, x + width); xx++) {
                writer.setColor(xx, yy, color);
            }
        }
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

    private BaseMainApplicationUI selectMainUiRoute(boolean embeddedEnabled) {
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

    private void autoStartInternalServer(Configuration configuration) {
        if (configuration == null || !configuration.isAutoRunServerOnStartup()) {
            return;
        }
        Platform.runLater(() -> {
            try {
                configurationApplicationService.ensureServerStarted();
            } catch (IOException e) {
                showErrorAlert(I18n.tr("configAutoRunServerStartupFailed", e.getMessage()));
            }
        });
    }

    @Override
    public void stop() {
        unregisterTitleStatusUpdater();
        try {
            MediaPlayerFactory.release();
        } catch (Exception _) {
            // Best-effort shutdown: media teardown should not prevent server cleanup or app exit.
        }
        UiServerUrlUtil.stopServerWithShutdownMessage();
        try {
            super.stop();
        } catch (Exception _) {
            // Best-effort shutdown: JavaFX stop hooks should not prevent process exit.
        }
    }

    private void registerTitleStatusUpdater() {
        configurationService.addChangeListener(titleConfigurationChangeListener);
        AppNavigationController.currentTargetProperty().addListener(titleNavigationTargetChangeListener);
        titleStatusTimeline = new Timeline(new KeyFrame(TITLE_STATUS_REFRESH_INTERVAL, _ -> updatePrimaryStageTitle()));
        titleStatusTimeline.setCycleCount(Animation.INDEFINITE);
        titleStatusTimeline.play();
    }

    private void unregisterTitleStatusUpdater() {
        configurationService.removeChangeListener(titleConfigurationChangeListener);
        AppNavigationController.currentTargetProperty().removeListener(titleNavigationTargetChangeListener);
        if (titleStatusTimeline != null) {
            titleStatusTimeline.stop();
            titleStatusTimeline = null;
        }
    }

    private void updatePrimaryStageTitle() {
        if (!Platform.isFxApplicationThread()) {
            scheduleTitleUpdate();
            return;
        }
        if (primaryStage != null) {
            primaryStage.setTitle(buildApplicationTitle());
        }
    }

    private void scheduleTitleUpdate() {
        try {
            Platform.runLater(this::updatePrimaryStageTitle);
        } catch (IllegalStateException _) {
            // The JavaFX runtime may already be shutting down.
        }
    }

    private String buildApplicationTitle() {
        String pageTitle = titleForNavigationTarget(AppNavigationController.currentTarget());
        if (pageTitle == null || pageTitle.isBlank()) {
            return PRODUCT_TITLE;
        }
        return pageTitle + " - " + PRODUCT_TITLE;
    }

    private String titleForNavigationTarget(AppNavigationController.Target target) {
        if (target == null) {
            return "";
        }
        return switch (target) {
            case BOOKMARKS -> "Favourite";
            case ACCOUNTS -> I18n.tr("autoAccount");
            case WATCHING_NOW -> I18n.tr("autoWatchingNow");
            case SETTINGS -> I18n.tr("autoSettings");
            case IMPORT -> I18n.tr("autoImportBulkAccounts");
            case LOGS -> I18n.tr("autoLogs");
        };
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
        Label loadingLabel = new Label(PRODUCT_TITLE);
        StackPane loadingRoot = new StackPane(loadingLabel);
        Scene loadingScene = new Scene(loadingRoot, GUIDED_MAX_WIDTH_PIXELS, GUIDED_MAX_HEIGHT_PIXELS);
        UiI18n.applySceneOrientation(loadingScene);
        configureFontStyles(loadingScene);
        return loadingScene;
    }
}
