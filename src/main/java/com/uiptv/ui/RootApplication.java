package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Configuration;
import com.uiptv.player.MediaPlayerFactory;
import com.uiptv.server.UIptvServer;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.DatabaseSyncService;
import com.uiptv.widget.UIptvAlert;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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
    private static HostServices hostServices;
    private final ConfigurationService configurationService = ConfigurationService.getInstance();
    private static final DatabaseSyncService databaseSyncService = DatabaseSyncService.getInstance();

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "UIPTV");
        addShutdownHook();
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
            startServer();
        } else {
            System.setProperty("file.encoding", "UTF-8");
            java.nio.charset.Charset.defaultCharset();
            launch();
        }
    }

    private static void handleSync(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: sync <first_db_path> <second_db_path>");
            exit(1);
        }
        String firstDB = args[1].replaceAll("^'|'$", "").replaceAll("^\"|\"$", "");
        String secondDB = args[2].replaceAll("^'|'$", "").replaceAll("^\"|\"$", "");
        try {
            syncDatabases(firstDB, secondDB);
            System.out.println("Sync complete!");
        } catch (SQLException e) {
            System.err.println("Error syncing tables: " + e.getMessage());
        }
    }

    public static void syncDatabases(String firstDB, String secondDB) throws SQLException {
        databaseSyncService.syncDatabases(firstDB, secondDB);
    }

    private static void startServer() {
        try {
            UIptvServer.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                UIptvServer.stop();
                UIptvAlert.showMessage("UIPTV Shutting down");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    public static HostServices getStaticHostServices() {
        return hostServices;
    }

    public static boolean ensureServerForWebPlayback() {
        try {
            UIptvServer.ensureStarted();
            return true;
        } catch (Exception e) {
            UIptvAlert.showError("Unable to start local web server for playback.", e);
            return false;
        }
    }

    public static void openInBrowser(String url) {
        if (isNotBlank(url) && hostServices != null) {
            hostServices.showDocument(url);
            return;
        }
        UIptvAlert.showError("Unable to open browser for DRM playback.");
    }

    @Override
    public final void start(Stage primaryStage) throws IOException {
        RootApplication.primaryStage = primaryStage;
        RootApplication.hostServices = getHostServices();


        ManageAccountUI manageAccountUI = new ManageAccountUI();
        ParseMultipleAccountUI parseMultipleAccountUI = new ParseMultipleAccountUI();
        BookmarkChannelListUI bookmarkChannelListUI = new BookmarkChannelListUI();
        AccountListUI accountListUI = new AccountListUI();
        configureAccountListUI(accountListUI, manageAccountUI, bookmarkChannelListUI);
        LogDisplayUI logDisplayUI = new LogDisplayUI();
        ConfigurationUI configurationUI = new ConfigurationUI(param -> {
            try {
                configureFontStyles(RootApplication.primaryStage.getScene());
                accountListUI.refresh();
                bookmarkChannelListUI.forceReload();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        configureManageAccountUI(manageAccountUI, accountListUI, bookmarkChannelListUI);
        configureParseMultipleAccountUI(parseMultipleAccountUI, accountListUI);
        configureUIComponents(configurationUI, parseMultipleAccountUI, manageAccountUI, bookmarkChannelListUI, accountListUI);

        TabPane tabPane = createTabPane(manageAccountUI, parseMultipleAccountUI, bookmarkChannelListUI, logDisplayUI, configurationUI);

        HBox embeddedPlayer = new HBox(MediaPlayerFactory.getPlayerContainer()); // Usage updated

        embeddedPlayer.setPadding(new javafx.geometry.Insets(5));
        VBox containerWithEmbeddedPlayer = new VBox();
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        containerWithEmbeddedPlayer.getChildren().addAll(embeddedPlayer, tabPane);
        HBox mainContent = new HBox(containerWithEmbeddedPlayer, accountListUI); // AccountListUI as the first horizontal item
        HBox.setHgrow(tabPane, Priority.ALWAYS);
        tabPane.setMinWidth(480);
        tabPane.setPrefWidth(480);
        tabPane.setMaxWidth(480);

        MenuBar menuBar = new MenuBar();
        menuBar.setUseSystemMenuBar(true);
        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About");
        aboutItem.setOnAction(e -> new AboutUI(getHostServices()));
        MenuItem updateItem = new MenuItem("Check for updates...");
        updateItem.setOnAction(e -> UpdateChecker.checkForUpdates(getHostServices()));
        helpMenu.getItems().addAll(aboutItem, updateItem);
        menuBar.getMenus().add(helpMenu);

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

    private TabPane createTabPane(ManageAccountUI manageAccountUI, ParseMultipleAccountUI parseMultipleAccountUI, BookmarkChannelListUI bookmarkChannelListUI, LogDisplayUI logDisplayUI, ConfigurationUI configurationUI) {
        TabPane tabPane = new TabPane();

        Tab manageAccountTab = new Tab("Account", manageAccountUI);
        Tab parseMultipleAccountTab = new Tab("Import Bulk Accounts", parseMultipleAccountUI);
        Tab bookmarkChannelListTab = new Tab("Favorite", bookmarkChannelListUI);
        Tab logDisplayTab = new Tab("Logs", logDisplayUI);
        Tab configurationTab = new Tab("Settings", configurationUI);
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> LogDisplayUI.setLoggingEnabled(newTab == logDisplayTab));
        tabPane.getTabs().addAll(configurationTab, manageAccountTab, parseMultipleAccountTab, logDisplayTab, bookmarkChannelListTab);
        tabPane.getSelectionModel().select(bookmarkChannelListTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setSide(Side.LEFT);
        return tabPane;
    }

    private void configureAccountListUI(AccountListUI accountListUI, ManageAccountUI manageAccountUI, BookmarkChannelListUI bookmarkChannelListUI) {
        accountListUI.addUpdateCallbackHandler(param -> manageAccountUI.editAccount((Account) param));
        accountListUI.addDeleteCallbackHandler(param -> {
            manageAccountUI.deleteAccount((Account) param);
            bookmarkChannelListUI.forceReload();
        });
    }

    private void configureManageAccountUI(ManageAccountUI manageAccountUI, AccountListUI accountListUI, BookmarkChannelListUI bookmarkChannelListUI) {
        manageAccountUI.addCallbackHandler(param -> {
            try {
                accountListUI.refresh();
                bookmarkChannelListUI.forceReload();
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

    private void configureUIComponents(ConfigurationUI configurationUI, ParseMultipleAccountUI parseMultipleAccountUI, ManageAccountUI manageAccountUI, BookmarkChannelListUI bookmarkChannelListUI, AccountListUI accountListUI) {
        configurationUI.setMinWidth((double) GUIDED_MAX_WIDTH_PIXELS / 4);
        parseMultipleAccountUI.setMinWidth((double) GUIDED_MAX_WIDTH_PIXELS / 4);
        manageAccountUI.setMinWidth((double) GUIDED_MAX_WIDTH_PIXELS / 4);
        bookmarkChannelListUI.setMinWidth((double) GUIDED_MAX_WIDTH_PIXELS / 4);
        accountListUI.setMinWidth((double) GUIDED_MAX_WIDTH_PIXELS / 4);
    }

    @Override
    public void stop() throws Exception {
        try {
            UIptvServer.stop();
            UIptvAlert.showMessage("UIPTV Shutting down");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
