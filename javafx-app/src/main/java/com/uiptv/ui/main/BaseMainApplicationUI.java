package com.uiptv.ui.main;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.player.MediaPlayerFactory;
import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.*;
import com.uiptv.ui.util.UiI18n;
import com.uiptv.util.I18n;
import com.uiptv.util.SystemUtils;
import javafx.animation.PauseTransition;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class BaseMainApplicationUI {

    private static final Duration DEFERRED_TAB_GAP = Duration.millis(400);
    // Tab index order: configurationTab=0, manageAccountTab=1, parseMultipleAccountTab=2,
    //                  logDisplayTab=3, watchingNowTab=4, bookmarkChannelListTab=5
    private static final int DEFAULT_TAB_INDEX = 5; // bookmarkChannelListTab
    // Preserved across scene rebuilds (wide/narrow toggle) so user stays on same tab.
    private static int lastSelectedTabIndex = DEFAULT_TAB_INDEX;

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
        AccountListUI accountListUI = new AccountListUI(useEmbeddedAccountFlow());
        BookmarkChannelListUI bookmarkChannelListUI = new BookmarkChannelListUI();
        AtomicReference<WatchingNowUI> watchingNowRef = new AtomicReference<>();
        setMinWidthForPane(bookmarkChannelListUI);
        setMinWidthForPane(accountListUI);

        TabPane tabPane = new TabPane();

        Tab manageAccountTab = new Tab(I18n.tr("autoAccount"),
                useEmbeddedAccountFlow() ? wrapToFill(accountListUI) : createDeferredPlaceholder());
        Tab parseMultipleAccountTab = new Tab(I18n.tr("autoImportBulkAccounts"), createDeferredPlaceholder());
        Tab bookmarkChannelListTab = new Tab(I18n.tr("autoFavorite"), bookmarkChannelListUI);
        Tab watchingNowTab = new Tab(I18n.tr("autoWatchingNow"), createDeferredPlaceholder());
        Tab logDisplayTab = new Tab(I18n.tr("autoLogs"), createDeferredPlaceholder());
        Tab configurationTab = new Tab(I18n.tr("autoSettings"), createDeferredPlaceholder());

        tabPane.getTabs().addAll(configurationTab, manageAccountTab, parseMultipleAccountTab, logDisplayTab, watchingNowTab, bookmarkChannelListTab);

        // Restore the tab the user was on before any wide/narrow rebuild.
        // Tabs on the left must ALWAYS remain where they are!
        int tabToSelect = (lastSelectedTabIndex >= 0 && lastSelectedTabIndex < tabPane.getTabs().size())
                ? lastSelectedTabIndex : DEFAULT_TAB_INDEX;
        tabPane.getSelectionModel().select(tabToSelect);

        // Register listener AFTER initial selection so lastSelectedTabIndex is never overwritten during scene build
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            int idx = tabPane.getTabs().indexOf(newTab);
            if (idx >= 0) {
                lastSelectedTabIndex = idx;
            }
            LogDisplayUI.setLoggingEnabled(newTab == logDisplayTab);
            if (newTab == watchingNowTab) {
                WatchingNowUI watchingNowUI = watchingNowRef.get();
                if (watchingNowUI != null) {
                    watchingNowUI.refreshIfNeeded();
                }
            }
        });

        // Restore account navigation state (account -> categories -> channels/VOD/series)
        Platform.runLater(() -> {
            AccountNavigationSession.Level level = AccountNavigationSession.getLevel();
            Account targetAccount = AccountNavigationSession.getAccount();
            PlaybackUIService.PlaybackOrigin playbackOrigin = PlaybackUIService.getLastPlaybackOrigin();
            if (targetAccount == null && playbackOrigin != PlaybackUIService.PlaybackOrigin.NONE) {
                targetAccount = PlaybackUIService.getLastPlaybackAccount();
                String catId = PlaybackUIService.getLastPlaybackCategoryId();
                Channel ch = PlaybackUIService.getLastPlaybackChannel();
                if (targetAccount != null) {
                    if (playbackOrigin == PlaybackUIService.PlaybackOrigin.ACCOUNT || playbackOrigin == PlaybackUIService.PlaybackOrigin.BOOKMARK || playbackOrigin == PlaybackUIService.PlaybackOrigin.WATCHING_NOW) {
                        accountListUI.openAccountAndChannel(targetAccount, catId, ch);
                    }
                }
                return;
            }
            if (targetAccount != null) {
                String catId = AccountNavigationSession.getCategoryId();
                if (level == AccountNavigationSession.Level.CHANNELS) {
                    Channel ch = AccountNavigationSession.getChannel();
                    accountListUI.openAccountAndChannel(targetAccount, catId, ch);
                } else if (level == AccountNavigationSession.Level.CATEGORIES) {
                    accountListUI.openAccountAndSelectCategory(targetAccount, catId);
                } else {
                    accountListUI.showAccountListView();
                }
            }
        });

        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setSide(Side.LEFT);

        HBox mainContent = buildMainContent(tabPane, accountListUI);

        MenuBar menuBar = createMenuBar();

        VBox rootLayout = new VBox(menuBar, mainContent);
        VBox.setVgrow(mainContent, Priority.ALWAYS);
        HBox.setHgrow(mainContent, Priority.ALWAYS);

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
                watchingNowRef
        ));
        return scene;
    }

    protected abstract HBox buildMainContent(TabPane tabPane, AccountListUI accountListUI);

    protected abstract boolean useEmbeddedAccountFlow();

    protected HBox createWideMainContent(TabPane tabPane, AccountListUI accountListUI) {
        HBox embeddedPlayer = createEmbeddedPlayerContainer();
        embeddedPlayer.setMaxWidth(Double.MAX_VALUE);
        embeddedPlayer.setMaxHeight(Double.MAX_VALUE);
        HBox.setHgrow(embeddedPlayer, Priority.ALWAYS);

        // Enforce minimum width for tab pane to prevent excessive shrinking
        tabPane.setMinWidth(445);
        tabPane.setPrefWidth(445);
        tabPane.setMaxWidth(445);
        tabPane.setMaxHeight(Double.MAX_VALUE);
        tabPane.setMinHeight(0);

        // Enforce minimum width for account list
        accountListUI.setMaxHeight(Double.MAX_VALUE);
        accountListUI.setMinHeight(0);
        accountListUI.setMinWidth(300);
        accountListUI.setPrefWidth(300);

        embeddedPlayer.setMinHeight(0);

        HBox content = new HBox(tabPane, embeddedPlayer);
        HBox.setHgrow(embeddedPlayer, Priority.ALWAYS);
        return content;
    }

    protected HBox createMainContent(TabPane tabPane, AccountListUI accountListUI) {
        VBox leftContainer = new VBox();
        leftContainer.setFillWidth(true);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        leftContainer.getChildren().add(tabPane);

        // Enforce minimum width to prevent excessive shrinking
        leftContainer.setMinWidth(480);
        leftContainer.setPrefWidth(480);
        leftContainer.setMaxWidth(Double.MAX_VALUE);
        leftContainer.setMinHeight(0);
        leftContainer.setPrefHeight(Double.MAX_VALUE);
        leftContainer.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(leftContainer, Priority.ALWAYS);

        tabPane.setMinWidth(480);
        tabPane.setPrefWidth(480);
        tabPane.setMaxWidth(Double.MAX_VALUE);
        tabPane.setMaxHeight(Double.MAX_VALUE);
        tabPane.setMinHeight(0);

        // Enforce minimum width for account list
        accountListUI.setMinWidth(300);
        accountListUI.setPrefWidth(300);
        accountListUI.setMaxWidth(Double.MAX_VALUE);
        accountListUI.setMaxHeight(Double.MAX_VALUE);
        accountListUI.setMinHeight(0);

        HBox mainContent = new HBox(leftContainer, accountListUI);
        HBox.setHgrow(leftContainer, Priority.ALWAYS);
        HBox.setHgrow(accountListUI, Priority.ALWAYS);
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
            WatchingNowUI watchingNowUI = new WatchingNowUI();
            setMinWidthForPane(watchingNowUI);
            context.watchingNowRef().set(watchingNowUI);
            context.watchingNowTab().setContent(watchingNowUI);
        };

        Runnable loadLogDisplay = () -> {
            LogDisplayUI logDisplayUI = new LogDisplayUI();
            setMinWidthForPane(logDisplayUI);
            context.logDisplayTab().setContent(logDisplayUI);
        };

        Runnable loadParseMultiple = () -> {
            ParseMultipleAccountUI parseMultipleAccountUI = new ParseMultipleAccountUI();
            setMinWidthForPane(parseMultipleAccountUI);
            configureParseMultipleAccountUI(parseMultipleAccountUI, context.accountListUI());
            context.parseMultipleAccountTab().setContent(parseMultipleAccountUI);
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
            if (!useEmbeddedAccountFlow()) {
                context.manageAccountTab().setContent(manageAccountUI);
            }
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
            });
            setMinWidthForPane(configurationUI);
            context.configurationTab().setContent(configurationUI);
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
        if (playerNode != null && playerNode.getParent() instanceof Pane parent) {
            parent.getChildren().remove(playerNode);
        }
        StackPane shell = new StackPane();
        shell.getStyleClass().add("embedded-player-shell");
        shell.setMinSize(0, 0);
        shell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        if (playerNode instanceof Region region) {
            region.minHeightProperty().unbind();
            region.prefHeightProperty().unbind();
            region.setMinWidth(0);
            region.setPrefWidth(Region.USE_COMPUTED_SIZE);
            region.setMaxWidth(Double.MAX_VALUE);
            region.setMinHeight(0);
            region.setPrefHeight(Region.USE_COMPUTED_SIZE);
            region.setMaxHeight(Double.MAX_VALUE);
        }
        if (playerNode instanceof Pane pane && !pane.getChildren().isEmpty()) {
            javafx.scene.Node active = pane.getChildren().get(0);
            if (active instanceof Region region) {
                region.minHeightProperty().unbind();
                region.prefHeightProperty().unbind();
                region.setMinWidth(0);
                region.setPrefWidth(Region.USE_COMPUTED_SIZE);
                region.setMaxWidth(Double.MAX_VALUE);
                region.setMinHeight(0);
                region.setPrefHeight(Region.USE_COMPUTED_SIZE);
                region.setMaxHeight(Double.MAX_VALUE);
            }
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
            if (!useEmbeddedAccountFlow()) {
                TabPane tabPane = manageAccountTab.getTabPane();
                if (tabPane != null) {
                    tabPane.getSelectionModel().select(manageAccountTab);
                }
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
            AtomicReference<WatchingNowUI> watchingNowRef
    ) {
    }
}
