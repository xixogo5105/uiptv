package com.uiptv.ui.main;

import com.uiptv.model.Account;
import com.uiptv.player.MediaPlayerFactory;
import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.*;
import com.uiptv.ui.util.UiI18n;
import com.uiptv.util.I18n;
import com.uiptv.util.SystemUtils;
import com.uiptv.widget.AppNavigationController;
import com.uiptv.widget.AppNotificationCenter;
import com.uiptv.widget.AppNavigationPane;
import com.uiptv.widget.AppPageHeader;
import com.uiptv.widget.InlinePanelService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.application.HostServices;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class BaseMainApplicationUI {

    private static final Duration DEFERRED_TAB_GAP = Duration.millis(400);
    private static final double COMPACT_EMBEDDED_PLAYER_WIDTH = 480;
    private static final double COMPACT_EMBEDDED_PLAYER_HEIGHT = 305;
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

    public Scene buildScene() {
        AccountListUI accountListUI = new AccountListUI(true, hostServices, this::toggleTheme);
        BookmarkChannelListUI bookmarkChannelListUI = new BookmarkChannelListUI(hostServices, this::toggleTheme);
        AtomicReference<ConfigurationUI> configurationRef = new AtomicReference<>();
        AtomicReference<WatchingNowUI> watchingNowRef = new AtomicReference<>();
        setMinWidthForPane(bookmarkChannelListUI);
        setMinWidthForPane(accountListUI);

        AppNavigationPane tabPane = new AppNavigationPane();

        Tab manageAccountTab = tabPane.createTab(
                I18n.tr("autoAccount"),
                AppNavigationPane.ICON_ACCOUNT,
                wrapToFill(accountListUI)
        );
        Tab parseMultipleAccountTab = tabPane.createTab(I18n.tr("autoImportBulkAccounts"), AppNavigationPane.ICON_IMPORT, createDeferredPlaceholder());
        Tab bookmarkChannelListTab = tabPane.createTab(I18n.tr("autoFavorite"), AppNavigationPane.ICON_FAVORITE, bookmarkChannelListUI);
        Tab watchingNowTab = tabPane.createTab(I18n.tr("autoWatchingNow"), AppNavigationPane.ICON_WATCHING, createDeferredPlaceholder());
        Tab logDisplayTab = tabPane.createTab(I18n.tr("autoLogs"), AppNavigationPane.ICON_LOGS, createDeferredPlaceholder());
        Tab configurationTab = tabPane.createTab(I18n.tr("autoSettings"), AppNavigationPane.ICON_SETTINGS, createDeferredPlaceholder());

        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            AppNavigationController.setCurrentTarget(targetForTab(
                    newTab,
                    configurationTab,
                    manageAccountTab,
                    parseMultipleAccountTab,
                    logDisplayTab,
                    watchingNowTab,
                    bookmarkChannelListTab
            ));
            LogDisplayUI.setLoggingEnabled(newTab == logDisplayTab);
            if (newTab == configurationTab) {
                ConfigurationUI configurationUI = configurationRef.get();
                if (configurationUI != null) {
                    configurationUI.refreshFromCurrentConfiguration();
                }
            }
            if (newTab == watchingNowTab) {
                WatchingNowUI watchingNowUI = watchingNowRef.get();
                if (watchingNowUI != null) {
                    watchingNowUI.refreshIfNeeded();
                }
            }
        });

        tabPane.getTabs().addAll(configurationTab, manageAccountTab, parseMultipleAccountTab, logDisplayTab, watchingNowTab, bookmarkChannelListTab);
        configureAppNavigation(tabPane, configurationTab, manageAccountTab, parseMultipleAccountTab, logDisplayTab, watchingNowTab, bookmarkChannelListTab);
        tabPane.getSelectionModel().select(bookmarkChannelListTab);

        HBox mainContent = buildMainContent(tabPane, accountListUI);

        MenuBar menuBar = createMenuBar();

        VBox notificationHost = AppNotificationCenter.createHost();
        AppNotificationCenter.install(notificationHost);
        StackPane inlineHost = InlinePanelService.createHost(mainContent);
        InlinePanelService.install(inlineHost);

        VBox rootLayout = new VBox(notificationHost, menuBar, inlineHost);
        rootLayout.getStyleClass().add("uiptv-app-root");
        VBox.setVgrow(inlineHost, Priority.ALWAYS);

        Scene scene = new Scene(rootLayout, guidedMaxWidthPixels, guidedMaxHeightPixels);
        UiI18n.applySceneOrientation(scene);
        fontStyleConfigurer.accept(scene);
        bookmarkChannelListUI.forceReload();

        initializeDeferredTabs(new DeferredTabsContext(
                configurationTab,
                manageAccountTab,
                parseMultipleAccountTab,
                logDisplayTab,
                watchingNowTab,
                accountListUI,
                bookmarkChannelListUI,
                configurationRef,
                watchingNowRef
        ));
        return scene;
    }

    private void configureAppNavigation(TabPane tabPane,
                                        Tab configurationTab,
                                        Tab manageAccountTab,
                                        Tab parseMultipleAccountTab,
                                        Tab logDisplayTab,
                                        Tab watchingNowTab,
                                        Tab bookmarkChannelListTab) {
        EnumMap<AppNavigationController.Target, Runnable> actions = new EnumMap<>(AppNavigationController.Target.class);
        actions.put(AppNavigationController.Target.SETTINGS, () -> selectTab(tabPane, configurationTab));
        actions.put(AppNavigationController.Target.ACCOUNTS, () -> selectTab(tabPane, manageAccountTab));
        actions.put(AppNavigationController.Target.IMPORT, () -> selectTab(tabPane, parseMultipleAccountTab));
        actions.put(AppNavigationController.Target.LOGS, () -> selectTab(tabPane, logDisplayTab));
        actions.put(AppNavigationController.Target.WATCHING_NOW, () -> selectTab(tabPane, watchingNowTab));
        actions.put(AppNavigationController.Target.BOOKMARKS, () -> selectTab(tabPane, bookmarkChannelListTab));
        AppNavigationController.configure(actions, AppNavigationController.Target.BOOKMARKS);
    }

    private void selectTab(TabPane tabPane, Tab tab) {
        if (tabPane != null && tab != null) {
            tabPane.getSelectionModel().select(tab);
        }
    }

    private AppNavigationController.Target targetForTab(Tab selectedTab,
                                                        Tab configurationTab,
                                                        Tab manageAccountTab,
                                                        Tab parseMultipleAccountTab,
                                                        Tab logDisplayTab,
                                                        Tab watchingNowTab,
                                                        Tab bookmarkChannelListTab) {
        if (selectedTab == configurationTab) {
            return AppNavigationController.Target.SETTINGS;
        }
        if (selectedTab == manageAccountTab) {
            return AppNavigationController.Target.ACCOUNTS;
        }
        if (selectedTab == parseMultipleAccountTab) {
            return AppNavigationController.Target.IMPORT;
        }
        if (selectedTab == logDisplayTab) {
            return AppNavigationController.Target.LOGS;
        }
        if (selectedTab == watchingNowTab) {
            return AppNavigationController.Target.WATCHING_NOW;
        }
        if (selectedTab == bookmarkChannelListTab) {
            return AppNavigationController.Target.BOOKMARKS;
        }
        return AppNavigationController.currentTarget();
    }

    private void toggleTheme() {
        com.uiptv.model.Configuration configuration = configurationService.read();
        if (configuration == null) {
            return;
        }
        configuration.setDarkTheme(!configuration.isDarkTheme());
        configurationService.save(configuration);
        Scene currentScene = primaryStage.getScene();
        if (currentScene != null) {
            fontStyleConfigurer.accept(currentScene);
        }
    }

    protected abstract HBox buildMainContent(TabPane tabPane, AccountListUI accountListUI);

    protected void applyCompactEmbeddedPlayerSize(HBox embeddedPlayer) {
        if (embeddedPlayer == null) {
            return;
        }
        embeddedPlayer.prefHeightProperty().unbind();
        embeddedPlayer.maxHeightProperty().unbind();
        embeddedPlayer.setMinWidth(COMPACT_EMBEDDED_PLAYER_WIDTH);
        embeddedPlayer.setPrefWidth(COMPACT_EMBEDDED_PLAYER_WIDTH);
        embeddedPlayer.setMaxWidth(COMPACT_EMBEDDED_PLAYER_WIDTH);
        embeddedPlayer.setMinHeight(COMPACT_EMBEDDED_PLAYER_HEIGHT);
        embeddedPlayer.setPrefHeight(COMPACT_EMBEDDED_PLAYER_HEIGHT);
        embeddedPlayer.setMaxHeight(COMPACT_EMBEDDED_PLAYER_HEIGHT);
    }

    protected void applyWideEmbeddedPlayerSize(HBox embeddedPlayer) {
        if (embeddedPlayer == null) {
            return;
        }
        embeddedPlayer.prefHeightProperty().unbind();
        embeddedPlayer.maxHeightProperty().unbind();
        embeddedPlayer.setMinWidth(0);
        embeddedPlayer.setPrefWidth(Region.USE_COMPUTED_SIZE);
        embeddedPlayer.setMaxWidth(Double.MAX_VALUE);
        embeddedPlayer.setMinHeight(0);
        embeddedPlayer.setPrefHeight(Region.USE_COMPUTED_SIZE);
        embeddedPlayer.setMaxHeight(Double.MAX_VALUE);
        embeddedPlayer.setAlignment(Pos.CENTER);
    }

    protected HBox createMainContent(TabPane tabPane, AccountListUI accountListUI) {
        tabPane.setMinWidth(0);
        tabPane.setPrefWidth(guidedMaxWidthPixels);
        tabPane.setMaxWidth(Double.MAX_VALUE);
        tabPane.setMaxHeight(Double.MAX_VALUE);
        tabPane.setMinHeight(0);

        StackPane navigationShell = createNavigationShell(tabPane);
        HBox mainContent = new HBox(navigationShell);
        mainContent.setFillHeight(true);
        mainContent.setMinSize(0, 0);
        mainContent.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        HBox.setHgrow(navigationShell, Priority.ALWAYS);
        return mainContent;
    }

    protected StackPane createNavigationShell(TabPane tabPane) {
        StackPane shell = new StackPane(tabPane);
        shell.getStyleClass().add("uiptv-nav-shell");
        shell.setMinSize(0, 0);
        shell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        StackPane.setAlignment(tabPane, Pos.CENTER);
        return shell;
    }

    protected HBox createEmbeddedPlayerContainer() {
        return createEmbeddedPlayerContainer(MediaPlayerFactory.getPlayerContainer());
    }

    protected HBox createEmbeddedPlayerContainer(javafx.scene.Node playerNode) {
        StackPane playerShell = createEmbeddedPlayerShell(playerNode);
        HBox embeddedPlayer = new HBox(playerShell);
        embeddedPlayer.setVisible(false);
        embeddedPlayer.setManaged(false);
        embeddedPlayer.setAlignment(Pos.CENTER);
        embeddedPlayer.setFillHeight(true);
        HBox.setHgrow(playerShell, Priority.ALWAYS);
        embeddedPlayer.setPadding(new javafx.geometry.Insets(5));
        embeddedPlayer.setMinSize(0, 0);
        embeddedPlayer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return embeddedPlayer;
    }

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();
        boolean useNativeSystemMenuBar = SystemUtils.IS_OS_MAC_OSX || !configurationService.read().isDarkTheme();
        menuBar.setUseSystemMenuBar(useNativeSystemMenuBar);

        Menu helpMenu = new Menu(I18n.tr("autoHelp"));
        MenuItem aboutItem = new MenuItem(I18n.tr("autoAbout"));
        aboutItem.setOnAction(e -> AboutUI.show(hostServices));

        MenuItem updateItem = new MenuItem(I18n.tr("autoCheckForUpdates2"));
        updateItem.setOnAction(e -> UpdateChecker.checkForUpdates(hostServices));

        helpMenu.getItems().addAll(aboutItem, updateItem);
        menuBar.getMenus().add(helpMenu);
        return menuBar;
    }

    private void initializeDeferredTabs(DeferredTabsContext context) {
        Supplier<WatchingNowUI> watchingNowSupplier = context.watchingNowRef()::get;
        Runnable loadWatchingNow = () -> {
            WatchingNowUI watchingNowUI = new WatchingNowUI(hostServices, this::toggleTheme);
            setMinWidthForPane(watchingNowUI);
            context.watchingNowRef().set(watchingNowUI);
            context.watchingNowTab().setContent(AppNavigationPane.wrapContent(watchingNowUI));
            if (context.watchingNowTab().isSelected()) {
                Platform.runLater(watchingNowUI::refreshIfNeeded);
            }
        };

        Runnable loadLogDisplay = () -> {
            LogDisplayUI logDisplayUI = new LogDisplayUI(hostServices, this::toggleTheme);
            setMinWidthForPane(logDisplayUI);
            context.logDisplayTab().setContent(AppNavigationPane.wrapContent(logDisplayUI));
        };

        Runnable loadParseMultiple = () -> {
            ParseMultipleAccountUI parseMultipleAccountUI = new ParseMultipleAccountUI(hostServices, this::toggleTheme);
            setMinWidthForPane(parseMultipleAccountUI);
            configureParseMultipleAccountUI(parseMultipleAccountUI, context.accountListUI());
            context.parseMultipleAccountTab().setContent(AppNavigationPane.wrapContent(parseMultipleAccountUI));
        };

        Runnable loadManageAccount = () -> {
            ManageAccountUI manageAccountUI = new ManageAccountUI();
            setMinWidthForPane(manageAccountUI);
            context.accountListUI().setManageAccountUI(manageAccountUI);
            configureAccountListUI(
                    context.accountListUI(),
                    manageAccountUI,
                    context.manageAccountTab(),
                    context.bookmarkChannelListUI(),
                    watchingNowSupplier
            );
            configureManageAccountUI(manageAccountUI, context.accountListUI(), context.bookmarkChannelListUI(), watchingNowSupplier);
        };

        Runnable loadConfiguration = () -> {
            ConfigurationUI configurationUI = new ConfigurationUI(param -> {
                Scene currentScene = primaryStage.getScene();
                if (currentScene != null) {
                    fontStyleConfigurer.accept(currentScene);
                }
                context.accountListUI().refresh();
                context.bookmarkChannelListUI().forceReload();
                WatchingNowUI watchingNowUI = context.watchingNowRef().get();
                if (watchingNowUI != null) {
                    watchingNowUI.forceReload();
                }
            }, hostServices, this::toggleTheme);
            setMinWidthForPane(configurationUI);
            context.configurationRef().set(configurationUI);
            context.configurationTab().setContent(AppNavigationPane.wrapContent(configurationUI));
            if (context.configurationTab().isSelected()) {
                configurationUI.refreshFromCurrentConfiguration();
            }
        };

        scheduleDeferredTabLoad(loadConfiguration,
                () -> scheduleDeferredTabLoad(loadManageAccount,
                        () -> scheduleDeferredTabLoad(loadParseMultiple,
                                () -> scheduleDeferredTabLoad(loadLogDisplay,
                                        () -> scheduleDeferredTabLoad(loadWatchingNow, null)))));
    }

    private void scheduleDeferredTabLoad(Runnable loader, Runnable nextStep) {
        PauseTransition pause = new PauseTransition(DEFERRED_TAB_GAP);
        pause.setOnFinished(event -> {
            loader.run();
            if (nextStep != null) {
                nextStep.run();
            }
        });
        pause.play();
    }

    private VBox createDeferredPlaceholder() {
        VBox placeholder = new VBox();
        VBox.setVgrow(placeholder, Priority.ALWAYS);
        return placeholder;
    }

    private VBox wrapToFill(javafx.scene.Node content) {
        VBox wrapper = new VBox(content);
        VBox.setVgrow(content, Priority.ALWAYS);
        wrapper.setFillWidth(true);
        return wrapper;
    }

    protected StackPane createEmbeddedPlayerShell(javafx.scene.Node playerNode) {
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

    private void configureAccountListUI(
            AccountListUI accountListUI,
            ManageAccountUI manageAccountUI,
            Tab manageAccountTab,
            BookmarkChannelListUI bookmarkChannelListUI,
            Supplier<WatchingNowUI> watchingNowSupplier
    ) {
        accountListUI.addUpdateCallbackHandler(param -> manageAccountUI.editAccount((Account) param));
        accountListUI.addExplicitEditCallbackHandler(param -> {
            manageAccountUI.editAccount((Account) param);
            TabPane tabPane = manageAccountTab.getTabPane();
            if (tabPane != null) {
                tabPane.getSelectionModel().select(manageAccountTab);
            }
        });
        accountListUI.addDeleteCallbackHandler(param -> {
            manageAccountUI.deleteAccount((Account) param);
            bookmarkChannelListUI.forceReload();
            WatchingNowUI watchingNowUI = watchingNowSupplier.get();
            if (watchingNowUI != null) {
                watchingNowUI.forceReload();
            }
        });
    }

    private void configureManageAccountUI(ManageAccountUI manageAccountUI, AccountListUI accountListUI, BookmarkChannelListUI bookmarkChannelListUI, Supplier<WatchingNowUI> watchingNowSupplier) {
        manageAccountUI.addCallbackHandler(param -> {
            accountListUI.refresh();
            bookmarkChannelListUI.forceReload();
            WatchingNowUI watchingNowUI = watchingNowSupplier.get();
            if (watchingNowUI != null) {
                watchingNowUI.forceReload();
            }
        });
    }

    private void configureParseMultipleAccountUI(ParseMultipleAccountUI parseMultipleAccountUI, AccountListUI accountListUI) {
        parseMultipleAccountUI.addCallbackHandler(param -> accountListUI.refresh());
    }

    private void setMinWidthForPane(Region pane) {
        pane.setMinWidth((double) guidedMaxWidthPixels / 4);
    }

    private record DeferredTabsContext(
            Tab configurationTab,
            Tab manageAccountTab,
            Tab parseMultipleAccountTab,
            Tab logDisplayTab,
            Tab watchingNowTab,
            AccountListUI accountListUI,
            BookmarkChannelListUI bookmarkChannelListUI,
            AtomicReference<ConfigurationUI> configurationRef,
            AtomicReference<WatchingNowUI> watchingNowRef
    ) {
    }
}
