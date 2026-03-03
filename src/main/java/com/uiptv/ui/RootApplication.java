package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Configuration;
import com.uiptv.player.MediaPlayerFactory;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.DatabaseSyncService;
import com.uiptv.util.EmbeddedPlayerWideViewUtil;
import com.uiptv.util.ServerUrlUtil;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.Arrays;

import static com.uiptv.util.StringUtils.isNotBlank;
import static java.lang.System.exit;

public class RootApplication extends Application {
    public final static int GUIDED_MAX_WIDTH_PIXELS = 1368;
    public final static int GUIDED_MAX_HEIGHT_PIXELS = 1920;
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

        ManageAccountUI manageAccountUI = new ManageAccountUI();
        ParseMultipleAccountUI parseMultipleAccountUI = new ParseMultipleAccountUI();
        BookmarkChannelListUI bookmarkChannelListUI = new BookmarkChannelListUI();
        WatchingNowUI watchingNowUI = new WatchingNowUI();
        AccountListUI accountListUI = new AccountListUI(embeddedWideViewEnabled);
        accountListUI.setManageAccountUI(manageAccountUI);
        configureAccountListUI(accountListUI, manageAccountUI, bookmarkChannelListUI, watchingNowUI);
        LogDisplayUI logDisplayUI = new LogDisplayUI();
        ConfigurationUI configurationUI = new ConfigurationUI(param -> {
            try {
                configureFontStyles(RootApplication.primaryStage.getScene());
                accountListUI.refresh();
                bookmarkChannelListUI.forceReload();
                watchingNowUI.forceReload();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        configureManageAccountUI(manageAccountUI, accountListUI, bookmarkChannelListUI, watchingNowUI);
        configureParseMultipleAccountUI(parseMultipleAccountUI, accountListUI);
        configureUIComponents(configurationUI, parseMultipleAccountUI, manageAccountUI, bookmarkChannelListUI, watchingNowUI, accountListUI);

        TabPane tabPane = createTabPane(manageAccountUI, accountListUI, parseMultipleAccountUI, bookmarkChannelListUI, watchingNowUI, logDisplayUI, configurationUI, embeddedWideViewEnabled);

        javafx.scene.Node playerNode = MediaPlayerFactory.getPlayerContainer();
        StackPane playerShell = createEmbeddedPlayerShell(playerNode);
        HBox embeddedPlayer = new HBox(playerShell);
        HBox.setHgrow(playerShell, Priority.ALWAYS);

        embeddedPlayer.setPadding(new javafx.geometry.Insets(5));
        HBox mainContent;
        if (embeddedEnabled) {
            if (embeddedWideViewEnabled) {
                embeddedPlayer.setMaxWidth(Double.MAX_VALUE);
                embeddedPlayer.setMaxHeight(Double.MAX_VALUE);
                HBox.setHgrow(embeddedPlayer, Priority.ALWAYS);
                tabPane.setMinWidth(445);
                tabPane.setPrefWidth(445);
                tabPane.setMaxWidth(445);
                tabPane.setMaxHeight(Double.MAX_VALUE);
                tabPane.setMinHeight(0);
                accountListUI.setMaxHeight(Double.MAX_VALUE);
                accountListUI.setMinHeight(0);
                embeddedPlayer.setMinHeight(0);
                mainContent = new HBox(tabPane, embeddedPlayer);
            } else {
                VBox containerWithEmbeddedPlayer = new VBox();
                VBox.setVgrow(tabPane, Priority.ALWAYS);
                containerWithEmbeddedPlayer.getChildren().addAll(embeddedPlayer, tabPane);
                mainContent = new HBox(containerWithEmbeddedPlayer, accountListUI);
                HBox.setHgrow(tabPane, Priority.ALWAYS);
                tabPane.setMinWidth(480);
                tabPane.setPrefWidth(480);
                tabPane.setMaxWidth(480);
            }
        } else {
            VBox containerWithEmbeddedPlayer = new VBox();
            VBox.setVgrow(tabPane, Priority.ALWAYS);
            containerWithEmbeddedPlayer.getChildren().addAll(embeddedPlayer, tabPane);
            mainContent = new HBox(containerWithEmbeddedPlayer, accountListUI); // AccountListUI as the first horizontal item
            HBox.setHgrow(tabPane, Priority.ALWAYS);
            tabPane.setMinWidth(480);
            tabPane.setPrefWidth(480);
            tabPane.setMaxWidth(480);
        }

        MenuBar menuBar = new MenuBar();
        menuBar.setUseSystemMenuBar(true);
        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About");
        aboutItem.setOnAction(e -> new AboutUI(getHostServices()));
        MenuItem updateItem = new MenuItem("Check for updates...");
        updateItem.setOnAction(e -> UpdateChecker.checkForUpdates(getHostServices()));
        helpMenu.getItems().addAll(aboutItem, updateItem);
        menuBar.getMenus().add(helpMenu);

        mainContent.setPadding(new javafx.geometry.Insets(5));
        mainContent.setSpacing(5);
        VBox rootLayout = new VBox(menuBar, mainContent);
        VBox.setVgrow(mainContent, Priority.ALWAYS);

        Scene scene = new Scene(rootLayout, GUIDED_MAX_WIDTH_PIXELS, GUIDED_MAX_HEIGHT_PIXELS);
        configureFontStyles(scene);
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

    private TabPane createTabPane(ManageAccountUI manageAccountUI, AccountListUI accountListUI, ParseMultipleAccountUI parseMultipleAccountUI, BookmarkChannelListUI bookmarkChannelListUI, WatchingNowUI watchingNowUI, LogDisplayUI logDisplayUI, ConfigurationUI configurationUI, boolean embeddedWideViewEnabled) {
        TabPane tabPane = new TabPane();

        Tab manageAccountTab = new Tab("Account", embeddedWideViewEnabled ? wrapToFill(accountListUI) : manageAccountUI);
        Tab parseMultipleAccountTab = new Tab("Import Bulk Accounts", parseMultipleAccountUI);
        Tab bookmarkChannelListTab = new Tab("Favorite", bookmarkChannelListUI);
        Tab watchingNowTab = new Tab("Watching Now", watchingNowUI);
        Tab logDisplayTab = new Tab("Logs", logDisplayUI);
        Tab configurationTab = new Tab("Settings", configurationUI);
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            LogDisplayUI.setLoggingEnabled(newTab == logDisplayTab);
            if (newTab == watchingNowTab) {
                watchingNowUI.refreshIfNeeded();
            }
        });
        tabPane.getTabs().addAll(configurationTab, manageAccountTab, parseMultipleAccountTab, logDisplayTab, watchingNowTab, bookmarkChannelListTab);
        tabPane.getSelectionModel().select(bookmarkChannelListTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setSide(Side.LEFT);
        return tabPane;
    }

    private VBox wrapToFill(javafx.scene.Node content) {
        VBox wrapper = new VBox(content);
        VBox.setVgrow(content, Priority.ALWAYS);
        wrapper.setFillWidth(true);
        return wrapper;
    }

    private StackPane createEmbeddedPlayerShell(javafx.scene.Node playerNode) {
        StackPane shell = new StackPane();
        shell.getStyleClass().add("embedded-player-shell");
        shell.setMinSize(0, 0);
        shell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        if (playerNode instanceof Region region) {
            region.setMinSize(0, 0);
            region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        }

        VBox placeholder = createEmbeddedPlayerPlaceholder();
        placeholder.setMouseTransparent(true);
        StackPane.setAlignment(placeholder, Pos.CENTER);
        StackPane.setAlignment(playerNode, Pos.CENTER);
        shell.getChildren().setAll(placeholder, playerNode);
        return shell;
    }

    private VBox createEmbeddedPlayerPlaceholder() {
        Label icon = new Label("▶");
        icon.getStyleClass().add("embedded-player-placeholder-icon");
        VBox wrapper = new VBox(icon);
        wrapper.setAlignment(Pos.CENTER);
        wrapper.getStyleClass().add("embedded-player-placeholder");
        return wrapper;
    }

    private void configureAccountListUI(AccountListUI accountListUI, ManageAccountUI manageAccountUI, BookmarkChannelListUI bookmarkChannelListUI, WatchingNowUI watchingNowUI) {
        accountListUI.addUpdateCallbackHandler(param -> manageAccountUI.editAccount((Account) param));
        accountListUI.addDeleteCallbackHandler(param -> {
            manageAccountUI.deleteAccount((Account) param);
            bookmarkChannelListUI.forceReload();
            watchingNowUI.forceReload();
        });
    }

    private void configureManageAccountUI(ManageAccountUI manageAccountUI, AccountListUI accountListUI, BookmarkChannelListUI bookmarkChannelListUI, WatchingNowUI watchingNowUI) {
        manageAccountUI.addCallbackHandler(param -> {
            try {
                accountListUI.refresh();
                bookmarkChannelListUI.forceReload();
                watchingNowUI.forceReload();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void configureParseMultipleAccountUI(ParseMultipleAccountUI parseMultipleAccountUI, AccountListUI accountListUI) {
        parseMultipleAccountUI.addCallbackHandler(param -> {
            try {
                accountListUI.refresh();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void configureUIComponents(ConfigurationUI configurationUI, ParseMultipleAccountUI parseMultipleAccountUI, ManageAccountUI manageAccountUI, BookmarkChannelListUI bookmarkChannelListUI, WatchingNowUI watchingNowUI, AccountListUI accountListUI) {
        configurationUI.setMinWidth((double) GUIDED_MAX_WIDTH_PIXELS / 4);
        parseMultipleAccountUI.setMinWidth((double) GUIDED_MAX_WIDTH_PIXELS / 4);
        manageAccountUI.setMinWidth((double) GUIDED_MAX_WIDTH_PIXELS / 4);
        bookmarkChannelListUI.setMinWidth((double) GUIDED_MAX_WIDTH_PIXELS / 4);
        watchingNowUI.setMinWidth((double) GUIDED_MAX_WIDTH_PIXELS / 4);
        accountListUI.setMinWidth((double) GUIDED_MAX_WIDTH_PIXELS / 4);
    }

    @Override
    public void stop() throws Exception {
        // Cleanup player resources before shutdown
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
