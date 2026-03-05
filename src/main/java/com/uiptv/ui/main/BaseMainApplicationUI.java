package com.uiptv.ui.main;

import com.uiptv.util.I18n;

import com.uiptv.model.Account;
import com.uiptv.player.MediaPlayerFactory;
import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.AboutUI;
import com.uiptv.ui.AccountListUI;
import com.uiptv.ui.BookmarkChannelListUI;
import com.uiptv.ui.ConfigurationUI;
import com.uiptv.ui.LogDisplayUI;
import com.uiptv.ui.ManageAccountUI;
import com.uiptv.ui.ParseMultipleAccountUI;
import com.uiptv.ui.UpdateChecker;
import com.uiptv.ui.WatchingNowUI;
import javafx.application.HostServices;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.function.Consumer;

public abstract class BaseMainApplicationUI {

    protected final Stage primaryStage;
    protected final HostServices hostServices;
    protected final ConfigurationService configurationService;
    protected final Consumer<Scene> fontStyleConfigurer;
    protected final int guidedMaxWidthPixels;
    protected final int guidedMaxHeightPixels;

    protected BaseMainApplicationUI(
            Stage primaryStage,
            HostServices hostServices,
            ConfigurationService configurationService,
            Consumer<Scene> fontStyleConfigurer,
            int guidedMaxWidthPixels,
            int guidedMaxHeightPixels
    ) {
        this.primaryStage = primaryStage;
        this.hostServices = hostServices;
        this.configurationService = configurationService;
        this.fontStyleConfigurer = fontStyleConfigurer;
        this.guidedMaxWidthPixels = guidedMaxWidthPixels;
        this.guidedMaxHeightPixels = guidedMaxHeightPixels;
    }

    public Scene buildScene() throws IOException {
        ManageAccountUI manageAccountUI = new ManageAccountUI();
        ParseMultipleAccountUI parseMultipleAccountUI = new ParseMultipleAccountUI();
        BookmarkChannelListUI bookmarkChannelListUI = new BookmarkChannelListUI();
        WatchingNowUI watchingNowUI = new WatchingNowUI();
        AccountListUI accountListUI = new AccountListUI(useEmbeddedAccountFlow());
        accountListUI.setManageAccountUI(manageAccountUI);

        configureAccountListUI(accountListUI, manageAccountUI, bookmarkChannelListUI, watchingNowUI);

        LogDisplayUI logDisplayUI = new LogDisplayUI();
        ConfigurationUI configurationUI = new ConfigurationUI(param -> {
            try {
                Scene currentScene = primaryStage.getScene();
                if (currentScene != null) {
                    fontStyleConfigurer.accept(currentScene);
                }
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

        TabPane tabPane = createTabPane(
                manageAccountUI,
                accountListUI,
                parseMultipleAccountUI,
                bookmarkChannelListUI,
                watchingNowUI,
                logDisplayUI,
                configurationUI
        );

        HBox mainContent = buildMainContent(tabPane, accountListUI);

        MenuBar menuBar = createMenuBar();

        VBox rootLayout = new VBox(menuBar, mainContent);
        VBox.setVgrow(mainContent, Priority.ALWAYS);

        Scene scene = new Scene(rootLayout, guidedMaxWidthPixels, guidedMaxHeightPixels);
        I18n.applySceneOrientation(scene);
        fontStyleConfigurer.accept(scene);
        return scene;
    }

    protected abstract HBox buildMainContent(TabPane tabPane, AccountListUI accountListUI);

    protected abstract boolean useEmbeddedAccountFlow();

    protected HBox createWideMainContent(TabPane tabPane, AccountListUI accountListUI) {
        HBox embeddedPlayer = createEmbeddedPlayerContainer();
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

        return new HBox(tabPane, embeddedPlayer);
    }

    protected HBox createMainContent(TabPane tabPane, AccountListUI accountListUI) {
        VBox leftContainer = new VBox();
        leftContainer.setFillWidth(true);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        leftContainer.getChildren().add(tabPane);

        leftContainer.setMinWidth(480);
        leftContainer.setPrefWidth(480);
        leftContainer.setMaxWidth(480);
        tabPane.setMinWidth(480);
        tabPane.setPrefWidth(480);
        tabPane.setMaxWidth(480);

        HBox mainContent = new HBox(leftContainer, accountListUI);
        HBox.setHgrow(tabPane, Priority.ALWAYS);
        return mainContent;
    }

    protected HBox createEmbeddedPlayerContainer() {
        javafx.scene.Node playerNode = MediaPlayerFactory.getPlayerContainer();
        StackPane playerShell = createEmbeddedPlayerShell(playerNode);
        HBox embeddedPlayer = new HBox(playerShell);
        HBox.setHgrow(playerShell, Priority.ALWAYS);
        embeddedPlayer.setPadding(new javafx.geometry.Insets(5));
        return embeddedPlayer;
    }

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();
        menuBar.setUseSystemMenuBar(!configurationService.read().isDarkTheme());

        Menu helpMenu = new Menu(I18n.tr("autoHelp"));
        MenuItem aboutItem = new MenuItem(I18n.tr("autoAbout"));
        aboutItem.setOnAction(e -> new AboutUI(hostServices));

        MenuItem updateItem = new MenuItem(I18n.tr("autoCheckForUpdates2"));
        updateItem.setOnAction(e -> UpdateChecker.checkForUpdates(hostServices));

        helpMenu.getItems().addAll(aboutItem, updateItem);
        menuBar.getMenus().add(helpMenu);
        return menuBar;
    }

    private TabPane createTabPane(ManageAccountUI manageAccountUI, AccountListUI accountListUI, ParseMultipleAccountUI parseMultipleAccountUI, BookmarkChannelListUI bookmarkChannelListUI, WatchingNowUI watchingNowUI, LogDisplayUI logDisplayUI, ConfigurationUI configurationUI) {
        TabPane tabPane = new TabPane();

        Tab manageAccountTab = new Tab(I18n.tr("autoAccount"), useEmbeddedAccountFlow() ? wrapToFill(accountListUI) : manageAccountUI);
        Tab parseMultipleAccountTab = new Tab(I18n.tr("autoImportBulkAccounts"), parseMultipleAccountUI);
        Tab bookmarkChannelListTab = new Tab(I18n.tr("autoFavorite"), bookmarkChannelListUI);
        Tab watchingNowTab = new Tab(I18n.tr("autoWatchingNow"), watchingNowUI);
        Tab logDisplayTab = new Tab(I18n.tr("autoLogs"), logDisplayUI);
        Tab configurationTab = new Tab(I18n.tr("autoSettings"), configurationUI);

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
        configurationUI.setMinWidth((double) guidedMaxWidthPixels / 4);
        parseMultipleAccountUI.setMinWidth((double) guidedMaxWidthPixels / 4);
        manageAccountUI.setMinWidth((double) guidedMaxWidthPixels / 4);
        bookmarkChannelListUI.setMinWidth((double) guidedMaxWidthPixels / 4);
        watchingNowUI.setMinWidth((double) guidedMaxWidthPixels / 4);
        accountListUI.setMinWidth((double) guidedMaxWidthPixels / 4);
    }
}
