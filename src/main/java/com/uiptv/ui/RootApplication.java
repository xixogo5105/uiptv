package com.uiptv.ui;

import com.uiptv.db.ConfigurationDb;
import com.uiptv.db.DatabaseUtils;
import com.uiptv.model.Account;
import com.uiptv.model.Configuration;
import com.uiptv.server.UIptvServer;
import com.uiptv.service.ConfigurationService;
import com.uiptv.widget.UIptvAlert;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;

import static com.uiptv.util.SQLiteTableSync.syncTables;
import static com.uiptv.util.StringUtils.isNotBlank;

public class RootApplication extends Application {
    public final static int GUIDED_MAX_WIDTH_PIXELS = 1368;
    public final static int GUIDED_MAX_HEIGHT_PIXELS = 1920;
    public static Stage primaryStage;
    private final ConfigurationService configurationService = ConfigurationService.getInstance();

    public static void main(String[] args) {
        if (args != null && args.length > 0 && "sync".equalsIgnoreCase(args[0])) {
            handleSync(args);
        } else if (args != null && Arrays.stream(args).anyMatch(s -> s.toLowerCase().contains("headless"))) {
            startServer();
        } else {
            System.setProperty("file.encoding", "UTF-8");
            java.nio.charset.Charset.defaultCharset();
            launch();
        }
        addShutdownHook();
    }

    private static void handleSync(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: sync <first_db_path> <second_db_path>");
            System.exit(1);
        }
        String firstDB = args[1];
        String secondDB = args[2];
        try {
            for (DatabaseUtils.DbTable tableName : DatabaseUtils.DbTable.values()) {
                if (DatabaseUtils.Syncable.contains(tableName)) {
                    syncTables(firstDB, secondDB, tableName.getTableName());
                }
            }
            ConfigurationDb.get().clearCache();
            LogDisplayUI.addLog("Sync complete!");
        } catch (SQLException e) {
            System.err.println("Error syncing tables: " + e.getMessage());
        }
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

    @Override
    public final void start(Stage primaryStage) throws IOException {
        RootApplication.primaryStage = primaryStage;

        ManageAccountUI manageAccountUI = new ManageAccountUI();
        ParseMultipleAccountUI parseMultipleAccountUI = new ParseMultipleAccountUI();
        BookmarkChannelListUI bookmarkChannelListUI = new BookmarkChannelListUI();
        AccountListUI accountListUI = new AccountListUI(bookmarkChannelListUI);
        configureAccountListUI(accountListUI, manageAccountUI, bookmarkChannelListUI);
        LogDisplayUI logDisplayUI = new LogDisplayUI();
        ConfigurationUI configurationUI = new ConfigurationUI(param -> {
            try {
                configureFontStyles(RootApplication.primaryStage.getScene());
                accountListUI.refresh();
                bookmarkChannelListUI.refresh();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        configureManageAccountUI(manageAccountUI, accountListUI, bookmarkChannelListUI);
        configureParseMultipleAccountUI(parseMultipleAccountUI, accountListUI);
        configureUIComponents(configurationUI, parseMultipleAccountUI, manageAccountUI, bookmarkChannelListUI, accountListUI);

        TabPane tabPane = createTabPane(manageAccountUI, parseMultipleAccountUI, bookmarkChannelListUI, logDisplayUI, configurationUI);

        HBox mainContent = new HBox(tabPane, accountListUI); // AccountListUI as the first horizontal item
        HBox.setHgrow(tabPane, Priority.ALWAYS);
        tabPane.setMinWidth(480);
        tabPane.setPrefWidth(480);
        tabPane.setMaxWidth(480);
        mainContent.setMinWidth(480);
        mainContent.setPrefWidth(480);
        mainContent.setMaxWidth(480);
        Scene scene = new Scene(mainContent, GUIDED_MAX_WIDTH_PIXELS, GUIDED_MAX_HEIGHT_PIXELS);
        configureFontStyles(scene);
        primaryStage.setTitle("UIPTV");
        primaryStage.setMaximized(true);
        primaryStage.setScene(scene);
        primaryStage.getIcons().add(new Image("file:icon.ico"));
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
            bookmarkChannelListUI.refresh();
        });
    }

    private void configureManageAccountUI(ManageAccountUI manageAccountUI, AccountListUI accountListUI, BookmarkChannelListUI bookmarkChannelListUI) {
        manageAccountUI.addCallbackHandler(param -> {
            try {
                accountListUI.refresh();
                bookmarkChannelListUI.refresh();
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
        scene.getStylesheets().add(configuration.isDarkTheme() ? "dark-application.css" : "application.css");
        scene.getRoot().styleProperty().bind(Bindings.format(customStylesheet));
    }
}