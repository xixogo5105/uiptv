package com.uiptv.ui.main;

import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationChangeListener;
import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.AccountListUI;
import com.uiptv.util.I18n;
import com.uiptv.widget.IconActionButton;
import com.uiptv.widget.WidePlayerNavigationControl;
import javafx.application.Platform;
import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.util.function.Consumer;

public class MainApplicationUI extends BaseMainApplicationUI {
    private static final double WIDE_APP_AREA_FRACTION = 0.27;
    private static final double WIDE_APP_AREA_MIN_WIDTH = 420;
    private static final double WIDE_APP_AREA_SMALL_SCREEN_MIN_WIDTH = 360;
    private static final double WIDE_APP_AREA_SMALL_SCREEN_THRESHOLD = 1300;
    private static final double WIDE_APP_AREA_MAX_WIDTH = 540;
    private static final String ICON_SHOW_NAVIGATION = "M3 5H21V19H3V5ZM5 7V17H10V7H5ZM12.7 8.7L16.1 12 12.7 15.3 11.3 13.9 13.2 12 11.3 10.1Z";
    private final boolean embeddedEnabled;
    private final Runnable wideNavigationToggleHandler = this::toggleWidePlayerNavigation;
    private final ConfigurationChangeListener embeddedLayoutChangeListener =
            _ -> Platform.runLater(this::applyEmbeddedPlayerLayoutFromConfiguration);
    private HBox mainContent;
    private HBox embeddedPlayer;
    private StackPane navigationShell;
    private StackPane collapsedNavigationHandleShell;
    private TabPane activeTabPane;
    private AccountListUI activeAccountListUI;
    private boolean navigationCollapsed;
    private boolean embeddedLayoutListenerRegistered;
    private double retainedWideAppAreaWidth = -1;

    public MainApplicationUI(
            Stage primaryStage,
            HostServices hostServices,
            ConfigurationService configurationService,
            Consumer<Scene> fontStyleConfigurer,
            int guidedMaxWidthPixels,
            int guidedMaxHeightPixels,
            boolean embeddedEnabled
    ) {
        super(primaryStage, hostServices, configurationService, fontStyleConfigurer, guidedMaxWidthPixels, guidedMaxHeightPixels);
        this.embeddedEnabled = embeddedEnabled;
    }

    @Override
    protected HBox buildMainContent(TabPane tabPane, AccountListUI accountListUI) {
        if (!embeddedEnabled) {
            WidePlayerNavigationControl.configure(false, false, null);
            return createMainContent(tabPane, accountListUI);
        }

        activeTabPane = tabPane;
        activeAccountListUI = accountListUI;
        embeddedPlayer = createEmbeddedPlayerContainer();
        embeddedPlayer.managedProperty().addListener((_, _, _) -> applyEmbeddedPlayerLayoutFromConfiguration());
        navigationShell = createNavigationShell(tabPane);
        collapsedNavigationHandleShell = createCollapsedNavigationHandleShell();

        mainContent = new HBox(navigationShell, collapsedNavigationHandleShell, embeddedPlayer);
        mainContent.setFillHeight(true);
        mainContent.setMinSize(0, 0);
        mainContent.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        mainContent.widthProperty().addListener((_, _, _) -> {
            if (!navigationCollapsed) {
                retainedWideAppAreaWidth = -1;
            }
            applyEmbeddedPlayerLayoutFromConfiguration();
        });

        tabPane.setMinWidth(0);
        tabPane.setPrefWidth(guidedMaxWidthPixels);
        tabPane.setMaxWidth(Double.MAX_VALUE);
        tabPane.setMaxHeight(Double.MAX_VALUE);
        tabPane.setMinHeight(0);

        registerEmbeddedLayoutChangeListener();
        mainContent.sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                unregisterEmbeddedLayoutChangeListener();
                WidePlayerNavigationControl.reset(wideNavigationToggleHandler);
                return;
            }
            registerEmbeddedLayoutChangeListener();
            applyEmbeddedPlayerLayoutFromConfiguration();
        });
        applyEmbeddedPlayerLayoutFromConfiguration();
        return mainContent;
    }

    private void registerEmbeddedLayoutChangeListener() {
        if (embeddedLayoutListenerRegistered) {
            return;
        }
        configurationService.addChangeListener(embeddedLayoutChangeListener);
        embeddedLayoutListenerRegistered = true;
    }

    private void unregisterEmbeddedLayoutChangeListener() {
        if (!embeddedLayoutListenerRegistered) {
            return;
        }
        configurationService.removeChangeListener(embeddedLayoutChangeListener);
        embeddedLayoutListenerRegistered = false;
    }

    private void applyEmbeddedPlayerLayoutFromConfiguration() {
        if (embeddedPlayer == null || navigationShell == null || collapsedNavigationHandleShell == null
                || activeTabPane == null || activeAccountListUI == null) {
            return;
        }
        Configuration configuration = configurationService.read();
        boolean embeddedPlayerVisible = configuration != null
                && configuration.isEmbeddedPlayer()
                && embeddedPlayer.isManaged();
        boolean widePlayerPreferred = embeddedPlayerVisible && configuration.isWideView();
        if (!embeddedPlayerVisible) {
            navigationCollapsed = false;
            retainedWideAppAreaWidth = -1;
            activeAccountListUI.setMediaDrawerMode(false);
            applyCompactEmbeddedLayout();
        } else if (navigationCollapsed) {
            activeAccountListUI.setMediaDrawerMode(false);
            applyFocusedEmbeddedLayout();
        } else if (widePlayerPreferred) {
            activeAccountListUI.setMediaDrawerMode(true);
            applyWideEmbeddedLayout();
        } else {
            retainedWideAppAreaWidth = -1;
            activeAccountListUI.setMediaDrawerMode(false);
            applyCompactEmbeddedLayout();
        }
        WidePlayerNavigationControl.configure(embeddedPlayerVisible, navigationCollapsed, wideNavigationToggleHandler);
        if (mainContent != null) {
            mainContent.requestLayout();
        }
    }

    private void applyCompactEmbeddedLayout() {
        activeTabPane.setMinWidth(0);
        activeTabPane.setPrefWidth(guidedMaxWidthPixels);
        activeTabPane.setMaxWidth(Double.MAX_VALUE);
        activeTabPane.setMaxHeight(Double.MAX_VALUE);
        activeTabPane.setMinHeight(0);

        navigationShell.setMinWidth(0);
        navigationShell.setPrefWidth(Region.USE_COMPUTED_SIZE);
        navigationShell.setMaxWidth(Double.MAX_VALUE);
        navigationShell.setVisible(true);
        navigationShell.setManaged(true);
        collapsedNavigationHandleShell.setVisible(false);
        collapsedNavigationHandleShell.setManaged(false);
        activeTabPane.setVisible(true);
        activeTabPane.setManaged(true);
        HBox.setHgrow(navigationShell, Priority.ALWAYS);
        HBox.setHgrow(collapsedNavigationHandleShell, Priority.NEVER);

        applyCompactEmbeddedPlayerSize(embeddedPlayer);
        HBox.setHgrow(embeddedPlayer, Priority.NEVER);
    }

    private void applyWideEmbeddedLayout() {
        double expandedAppAreaWidth = retainedWideAppAreaWidth();
        activeTabPane.setMinWidth(expandedAppAreaWidth);
        activeTabPane.setPrefWidth(expandedAppAreaWidth);
        activeTabPane.setMaxWidth(expandedAppAreaWidth);
        activeTabPane.setMaxHeight(Double.MAX_VALUE);
        activeTabPane.setMinHeight(0);
        activeTabPane.setVisible(true);
        activeTabPane.setManaged(true);

        navigationShell.setMinWidth(expandedAppAreaWidth);
        navigationShell.setPrefWidth(expandedAppAreaWidth);
        navigationShell.setMaxWidth(expandedAppAreaWidth);
        navigationShell.setVisible(true);
        navigationShell.setManaged(true);
        HBox.setHgrow(navigationShell, Priority.NEVER);

        collapsedNavigationHandleShell.setVisible(false);
        collapsedNavigationHandleShell.setManaged(false);
        HBox.setHgrow(collapsedNavigationHandleShell, Priority.NEVER);

        applyWideEmbeddedPlayerSize(embeddedPlayer);
        HBox.setHgrow(embeddedPlayer, Priority.ALWAYS);
    }

    private void applyFocusedEmbeddedLayout() {
        activeTabPane.setMinWidth(0);
        activeTabPane.setPrefWidth(0);
        activeTabPane.setMaxWidth(0);
        activeTabPane.setMaxHeight(Double.MAX_VALUE);
        activeTabPane.setMinHeight(0);
        activeTabPane.setVisible(false);
        activeTabPane.setManaged(false);

        navigationShell.setMinWidth(0);
        navigationShell.setPrefWidth(0);
        navigationShell.setMaxWidth(0);
        navigationShell.setVisible(false);
        navigationShell.setManaged(false);
        HBox.setHgrow(navigationShell, Priority.NEVER);

        collapsedNavigationHandleShell.setVisible(true);
        collapsedNavigationHandleShell.setManaged(true);
        HBox.setHgrow(collapsedNavigationHandleShell, Priority.NEVER);

        applyWideEmbeddedPlayerSize(embeddedPlayer);
        HBox.setHgrow(embeddedPlayer, Priority.ALWAYS);
    }

    private StackPane createCollapsedNavigationHandleShell() {
        IconActionButton showButton = new IconActionButton(
                I18n.tr("autoShowPlaybackNavigation"),
                ICON_SHOW_NAVIGATION,
                this::toggleWidePlayerNavigation
        );
        StackPane shell = new StackPane(showButton);
        shell.getStyleClass().add("wide-player-navigation-restore-shell");
        shell.setAlignment(Pos.TOP_CENTER);
        shell.setPadding(new Insets(24, 4, 0, 4));
        shell.setMinWidth(42);
        shell.setPrefWidth(42);
        shell.setMaxWidth(42);
        shell.setVisible(false);
        shell.setManaged(false);
        return shell;
    }

    private void toggleWidePlayerNavigation() {
        if (embeddedPlayer == null || !embeddedPlayer.isManaged()) {
            WidePlayerNavigationControl.configure(false, false, wideNavigationToggleHandler);
            return;
        }
        if (!navigationCollapsed) {
            retainedWideAppAreaWidth = retainedWideAppAreaWidth();
        }
        navigationCollapsed = !navigationCollapsed;
        applyEmbeddedPlayerLayoutFromConfiguration();
    }

    private double retainedWideAppAreaWidth() {
        if (retainedWideAppAreaWidth <= 0) {
            retainedWideAppAreaWidth = preferredWideAppAreaWidth();
        }
        return retainedWideAppAreaWidth;
    }

    private double preferredWideAppAreaWidth() {
        double availableWidth = mainContent == null ? 0 : mainContent.getWidth();
        if (availableWidth <= 0 && primaryStage != null) {
            availableWidth = primaryStage.getWidth();
        }
        if (availableWidth <= 0) {
            availableWidth = guidedMaxWidthPixels;
        }
        double minWidth = availableWidth < WIDE_APP_AREA_SMALL_SCREEN_THRESHOLD
                ? WIDE_APP_AREA_SMALL_SCREEN_MIN_WIDTH
                : WIDE_APP_AREA_MIN_WIDTH;
        return Math.max(minWidth, Math.min(WIDE_APP_AREA_MAX_WIDTH, availableWidth * WIDE_APP_AREA_FRACTION));
    }
}
