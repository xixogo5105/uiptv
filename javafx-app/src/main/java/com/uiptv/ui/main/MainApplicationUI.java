package com.uiptv.ui.main;

import com.uiptv.model.Configuration;
import com.uiptv.player.MediaPlayerFactory;
import com.uiptv.service.ConfigurationChangeListener;
import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.AccountListUI;
import com.uiptv.util.AppLog;
import com.uiptv.util.I18n;
import com.uiptv.widget.IconActionButton;
import com.uiptv.widget.WidePlayerNavigationControl;
import javafx.application.Platform;
import javafx.application.HostServices;
import javafx.beans.value.ChangeListener;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.util.Locale;
import java.util.function.Consumer;

public class MainApplicationUI extends BaseMainApplicationUI {
    private static final double WIDE_APP_AREA_FRACTION = 0.27;
    private static final double WIDE_APP_AREA_MIN_WIDTH = 420;
    private static final double WIDE_APP_AREA_SMALL_SCREEN_MIN_WIDTH = 360;
    private static final double WIDE_APP_AREA_SMALL_SCREEN_THRESHOLD = 1300;
    private static final double WIDE_APP_AREA_MAX_WIDTH = 540;
    private static final double COMPACT_SIDE_PLAYER_WIDTH = 480;
    private static final double COMPACT_SIDE_NAVIGATION_MIN_WIDTH = 800;
    private static final double STACKED_EMBEDDED_LAYOUT_WIDTH_THRESHOLD =
            COMPACT_SIDE_PLAYER_WIDTH + COMPACT_SIDE_NAVIGATION_MIN_WIDTH;
    private static final double STACKED_EMBEDDED_PLAYER_MAX_WIDTH = 480;
    private static final double STACKED_EMBEDDED_PLAYER_ASPECT_RATIO = 9.0 / 16.0;
    private static final double STACKED_EMBEDDED_PLAYER_VERTICAL_CHROME = 32;
    private static final double STACKED_EMBEDDED_PLAYER_MIN_HEIGHT = 210;
    private static final double STACKED_EMBEDDED_PLAYER_MAX_HEIGHT = 305;
    private static final double STALE_LAYOUT_WIDTH_JUMP_THRESHOLD = COMPACT_SIDE_PLAYER_WIDTH;
    private static final String EMBEDDED_LAYOUT_DEBUG_PREFIX = "[TEMP EmbeddedLayout] ";
    private static final String ICON_SHOW_NAVIGATION = "M3 5H21V19H3V5ZM5 7V17H10V7H5ZM12.7 8.7L16.1 12 12.7 15.3 11.3 13.9 13.2 12 11.3 10.1Z";
    private final boolean embeddedEnabled;
    private final Runnable wideNavigationToggleHandler = this::toggleWidePlayerNavigation;
    private final ConfigurationChangeListener embeddedLayoutChangeListener =
            _ -> Platform.runLater(this::applyEmbeddedPlayerLayoutFromConfiguration);
    private final ChangeListener<Number> layoutWidthChangeListener =
            (_, _, _) -> onLayoutWidthChanged();
    private HBox mainContent;
    private HBox embeddedPlayer;
    private Node embeddedPlayerNode;
    private GridPane responsiveContent;
    private StackPane navigationShell;
    private StackPane collapsedNavigationHandleShell;
    private TabPane activeTabPane;
    private AccountListUI activeAccountListUI;
    private boolean navigationCollapsed;
    private boolean embeddedLayoutListenerRegistered;
    private boolean deferredEmbeddedLayoutRefreshPending;
    private double retainedWideAppAreaWidth = -1;
    private String lastEmbeddedLayoutLogSignature = "";

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
        embeddedPlayerNode = MediaPlayerFactory.getPlayerContainer();
        embeddedPlayer = createEmbeddedPlayerContainer(embeddedPlayerNode);
        embeddedPlayerNode.visibleProperty().addListener((_, oldVisible, newVisible) -> {
            logEmbeddedPlayerNodeEvent("player-node visible " + oldVisible + " -> " + newVisible);
            applyEmbeddedPlayerLayoutFromConfiguration();
        });
        embeddedPlayerNode.managedProperty().addListener((_, oldManaged, newManaged) -> {
            logEmbeddedPlayerNodeEvent("player-node managed " + oldManaged + " -> " + newManaged);
            applyEmbeddedPlayerLayoutFromConfiguration();
        });
        embeddedPlayerNode.parentProperty().addListener((_, oldParent, newParent) ->
                logEmbeddedPlayerNodeEvent("player-node parent " + nodeName(oldParent) + " -> " + nodeName(newParent)));
        navigationShell = createNavigationShell(tabPane);
        collapsedNavigationHandleShell = createCollapsedNavigationHandleShell();
        responsiveContent = createResponsiveContent();
        responsiveContent.widthProperty().addListener(layoutWidthChangeListener);

        mainContent = new HBox(responsiveContent);
        mainContent.setFillHeight(true);
        mainContent.setMinSize(0, 0);
        mainContent.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        mainContent.widthProperty().addListener(layoutWidthChangeListener);
        HBox.setHgrow(responsiveContent, Priority.ALWAYS);
        if (primaryStage != null) {
            primaryStage.widthProperty().addListener(layoutWidthChangeListener);
            primaryStage.maximizedProperty().addListener((_, _, _) -> onLayoutWidthChanged());
        }

        tabPane.setMinWidth(0);
        tabPane.setPrefWidth(guidedMaxWidthPixels);
        tabPane.setMaxWidth(Double.MAX_VALUE);
        tabPane.setMaxHeight(Double.MAX_VALUE);
        tabPane.setMinHeight(0);

        registerEmbeddedLayoutChangeListener();
        mainContent.sceneProperty().addListener((_, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.widthProperty().removeListener(layoutWidthChangeListener);
            }
            if (newScene == null) {
                unregisterEmbeddedLayoutChangeListener();
                WidePlayerNavigationControl.reset(wideNavigationToggleHandler);
                return;
            }
            newScene.widthProperty().addListener(layoutWidthChangeListener);
            registerEmbeddedLayoutChangeListener();
            onLayoutWidthChanged();
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

    private void onLayoutWidthChanged() {
        if (!navigationCollapsed) {
            retainedWideAppAreaWidth = -1;
        }
        applyEmbeddedPlayerLayoutFromConfiguration();
        scheduleDeferredEmbeddedLayoutRefresh();
    }

    private void scheduleDeferredEmbeddedLayoutRefresh() {
        if (deferredEmbeddedLayoutRefreshPending) {
            return;
        }
        deferredEmbeddedLayoutRefreshPending = true;
        Platform.runLater(() -> {
            deferredEmbeddedLayoutRefreshPending = false;
            if (!navigationCollapsed) {
                retainedWideAppAreaWidth = -1;
            }
            applyEmbeddedPlayerLayoutFromConfiguration();
        });
    }

    private void applyEmbeddedPlayerLayoutFromConfiguration() {
        if (embeddedPlayer == null || navigationShell == null || collapsedNavigationHandleShell == null
                || activeTabPane == null || activeAccountListUI == null) {
            return;
        }
        Configuration configuration = configurationService.read();
        boolean embeddedConfigured = configuration != null && configuration.isEmbeddedPlayer();
        boolean stackedPlayerLayout = embeddedConfigured && shouldUseStackedEmbeddedLayout();
        boolean playerNodeActive = isEmbeddedPlayerNodeActive();
        boolean showEmbeddedPlayer = embeddedConfigured && (playerNodeActive || stackedPlayerLayout);
        setEmbeddedPlayerContainerVisible(showEmbeddedPlayer);
        boolean widePlayerPreferred = showEmbeddedPlayer && configuration.isWideView();
        String selectedLayout;
        if (!showEmbeddedPlayer) {
            selectedLayout = "compact-hidden";
            navigationCollapsed = false;
            retainedWideAppAreaWidth = -1;
            activeAccountListUI.setMediaDrawerMode(false);
            applyCompactEmbeddedLayout();
        } else if (stackedPlayerLayout) {
            selectedLayout = "stacked-top-player";
            activeAccountListUI.setMediaDrawerMode(false);
            applyStackedEmbeddedLayout();
        } else if (navigationCollapsed) {
            selectedLayout = "focused-player";
            activeAccountListUI.setMediaDrawerMode(false);
            applyFocusedEmbeddedLayout();
        } else if (widePlayerPreferred) {
            selectedLayout = "wide-side-player";
            activeAccountListUI.setMediaDrawerMode(true);
            applyWideEmbeddedLayout();
        } else {
            selectedLayout = "compact-side-player";
            retainedWideAppAreaWidth = -1;
            activeAccountListUI.setMediaDrawerMode(false);
            applyCompactEmbeddedLayout();
        }
        logEmbeddedLayoutState(
                selectedLayout,
                embeddedConfigured,
                stackedPlayerLayout,
                playerNodeActive,
                widePlayerPreferred
        );
        WidePlayerNavigationControl.configure(
                showEmbeddedPlayer && !stackedPlayerLayout,
                !stackedPlayerLayout && navigationCollapsed,
                wideNavigationToggleHandler
        );
        if (mainContent != null) {
            mainContent.requestLayout();
        }
    }

    private void applyCompactEmbeddedLayout() {
        applySideBySideEmbeddedArrangement();
        double navigationWidth = compactSideNavigationWidth();
        configureCompactSideBySideResponsiveGrid(navigationWidth);
        activeTabPane.setMinWidth(0);
        activeTabPane.setPrefWidth(Math.min(guidedMaxWidthPixels, navigationWidth));
        activeTabPane.setMaxWidth(Double.MAX_VALUE);
        activeTabPane.setMaxHeight(Double.MAX_VALUE);
        activeTabPane.setMinHeight(0);

        navigationShell.setMinWidth(0);
        navigationShell.setPrefWidth(navigationWidth);
        navigationShell.setMaxWidth(Double.MAX_VALUE);
        navigationShell.setVisible(true);
        navigationShell.setManaged(true);
        collapsedNavigationHandleShell.setVisible(false);
        collapsedNavigationHandleShell.setManaged(false);
        activeTabPane.setVisible(true);
        activeTabPane.setManaged(true);
        HBox.setHgrow(navigationShell, Priority.ALWAYS);
        GridPane.setHgrow(navigationShell, Priority.ALWAYS);
        GridPane.setVgrow(navigationShell, Priority.ALWAYS);
        HBox.setHgrow(collapsedNavigationHandleShell, Priority.NEVER);
        GridPane.setHgrow(collapsedNavigationHandleShell, Priority.NEVER);
        GridPane.setVgrow(collapsedNavigationHandleShell, Priority.NEVER);

        applyCompactEmbeddedPlayerSize(embeddedPlayer);
        HBox.setHgrow(embeddedPlayer, Priority.NEVER);
        GridPane.setHgrow(embeddedPlayer, Priority.NEVER);
        GridPane.setVgrow(embeddedPlayer, Priority.NEVER);
        GridPane.setValignment(embeddedPlayer, VPos.TOP);
    }

    private void applyWideEmbeddedLayout() {
        applySideBySideEmbeddedArrangement();
        configureSideBySideResponsiveGrid(Priority.NEVER, Priority.ALWAYS);
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
        GridPane.setHgrow(navigationShell, Priority.NEVER);
        GridPane.setVgrow(navigationShell, Priority.ALWAYS);

        collapsedNavigationHandleShell.setVisible(false);
        collapsedNavigationHandleShell.setManaged(false);
        HBox.setHgrow(collapsedNavigationHandleShell, Priority.NEVER);
        GridPane.setHgrow(collapsedNavigationHandleShell, Priority.NEVER);
        GridPane.setVgrow(collapsedNavigationHandleShell, Priority.NEVER);

        applyWideEmbeddedPlayerSize(embeddedPlayer);
        HBox.setHgrow(embeddedPlayer, Priority.ALWAYS);
        GridPane.setHgrow(embeddedPlayer, Priority.ALWAYS);
        GridPane.setVgrow(embeddedPlayer, Priority.ALWAYS);
        GridPane.setValignment(embeddedPlayer, VPos.CENTER);
    }

    private void applyFocusedEmbeddedLayout() {
        applySideBySideEmbeddedArrangement();
        configureSideBySideResponsiveGrid(Priority.NEVER, Priority.ALWAYS);
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
        GridPane.setHgrow(navigationShell, Priority.NEVER);
        GridPane.setVgrow(navigationShell, Priority.NEVER);

        collapsedNavigationHandleShell.setVisible(true);
        collapsedNavigationHandleShell.setManaged(true);
        HBox.setHgrow(collapsedNavigationHandleShell, Priority.NEVER);
        GridPane.setHgrow(collapsedNavigationHandleShell, Priority.NEVER);
        GridPane.setVgrow(collapsedNavigationHandleShell, Priority.NEVER);

        applyWideEmbeddedPlayerSize(embeddedPlayer);
        HBox.setHgrow(embeddedPlayer, Priority.ALWAYS);
        GridPane.setHgrow(embeddedPlayer, Priority.ALWAYS);
        GridPane.setVgrow(embeddedPlayer, Priority.ALWAYS);
        GridPane.setValignment(embeddedPlayer, VPos.CENTER);
    }

    private void applyStackedEmbeddedLayout() {
        applyStackedEmbeddedArrangement();

        activeTabPane.setMinWidth(0);
        activeTabPane.setPrefWidth(Region.USE_COMPUTED_SIZE);
        activeTabPane.setMaxWidth(Double.MAX_VALUE);
        activeTabPane.setMaxHeight(Double.MAX_VALUE);
        activeTabPane.setMinHeight(0);
        activeTabPane.setVisible(true);
        activeTabPane.setManaged(true);

        navigationShell.setMinWidth(0);
        navigationShell.setPrefWidth(Region.USE_COMPUTED_SIZE);
        navigationShell.setMaxWidth(Double.MAX_VALUE);
        navigationShell.setMinHeight(0);
        navigationShell.setMaxHeight(Double.MAX_VALUE);
        navigationShell.setVisible(true);
        navigationShell.setManaged(true);
        GridPane.setVgrow(navigationShell, Priority.ALWAYS);

        collapsedNavigationHandleShell.setVisible(false);
        collapsedNavigationHandleShell.setManaged(false);
        GridPane.setVgrow(collapsedNavigationHandleShell, Priority.NEVER);

        applyStackedEmbeddedPlayerSize();
        configureStackedResponsiveGrid();
        GridPane.setHgrow(embeddedPlayer, Priority.NEVER);
        GridPane.setVgrow(embeddedPlayer, Priority.NEVER);
        GridPane.setHalignment(embeddedPlayer, HPos.CENTER);
        GridPane.setValignment(embeddedPlayer, VPos.TOP);
    }

    private void applySideBySideEmbeddedArrangement() {
        if (responsiveContent == null || navigationShell == null || collapsedNavigationHandleShell == null
                || embeddedPlayer == null) {
            return;
        }
        boolean alreadySideBySide = rowIndex(navigationShell) == 0
                && columnIndex(navigationShell) == 0
                && rowIndex(embeddedPlayer) == 0
                && columnIndex(embeddedPlayer) == 1;
        if (alreadySideBySide) {
            return;
        }
        logEmbeddedPlayerNodeEvent("arrangement grid side-by-side");
        configureSideBySideResponsiveGrid();
        placeInGrid(navigationShell, 0, 0);
        placeInGrid(collapsedNavigationHandleShell, 0, 0);
        placeInGrid(embeddedPlayer, 1, 0);
        GridPane.setVgrow(navigationShell, Priority.ALWAYS);
        GridPane.setVgrow(collapsedNavigationHandleShell, Priority.NEVER);
        GridPane.setVgrow(embeddedPlayer, Priority.NEVER);
        GridPane.setValignment(embeddedPlayer, VPos.TOP);
    }

    private void applyStackedEmbeddedArrangement() {
        if (responsiveContent == null || navigationShell == null || embeddedPlayer == null
                || collapsedNavigationHandleShell == null) {
            return;
        }
        boolean alreadyStacked = rowIndex(embeddedPlayer) == 0
                && columnIndex(embeddedPlayer) == 0
                && rowIndex(navigationShell) == 1
                && columnIndex(navigationShell) == 0;
        if (alreadyStacked) {
            return;
        }
        logEmbeddedPlayerNodeEvent("arrangement grid stacked-top");
        configureStackedResponsiveGrid();
        placeInGrid(embeddedPlayer, 0, 0);
        placeInGrid(navigationShell, 0, 1);
        placeInGrid(collapsedNavigationHandleShell, 0, 1);
        GridPane.setHgrow(embeddedPlayer, Priority.NEVER);
        GridPane.setVgrow(embeddedPlayer, Priority.NEVER);
        GridPane.setHalignment(embeddedPlayer, HPos.CENTER);
        GridPane.setValignment(embeddedPlayer, VPos.TOP);
        GridPane.setHgrow(navigationShell, Priority.ALWAYS);
        GridPane.setVgrow(navigationShell, Priority.ALWAYS);
    }

    private GridPane createResponsiveContent() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("embedded-player-responsive-layout");
        grid.setMinSize(0, 0);
        grid.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        grid.getChildren().setAll(navigationShell, collapsedNavigationHandleShell, embeddedPlayer);
        configureSideBySideResponsiveGrid(grid, Priority.ALWAYS, Priority.NEVER);
        placeInGrid(navigationShell, 0, 0);
        placeInGrid(collapsedNavigationHandleShell, 0, 0);
        placeInGrid(embeddedPlayer, 1, 0);
        return grid;
    }

    private void configureSideBySideResponsiveGrid() {
        configureSideBySideResponsiveGrid(Priority.ALWAYS, Priority.NEVER);
    }

    private void configureSideBySideResponsiveGrid(Priority navigationColumnGrow, Priority playerColumnGrow) {
        configureSideBySideResponsiveGrid(responsiveContent, navigationColumnGrow, playerColumnGrow);
    }

    private void configureSideBySideResponsiveGrid(
            GridPane grid,
            Priority navigationColumnGrow,
            Priority playerColumnGrow
    ) {
        if (grid == null) {
            return;
        }
        ColumnConstraints navigationColumn = new ColumnConstraints();
        navigationColumn.setMinWidth(0);
        navigationColumn.setHgrow(navigationColumnGrow);
        navigationColumn.setFillWidth(true);
        ColumnConstraints playerColumn = new ColumnConstraints();
        playerColumn.setMinWidth(0);
        playerColumn.setHgrow(playerColumnGrow);
        playerColumn.setFillWidth(true);
        RowConstraints contentRow = new RowConstraints();
        contentRow.setMinHeight(0);
        contentRow.setVgrow(Priority.ALWAYS);
        contentRow.setFillHeight(true);
        grid.getColumnConstraints().setAll(navigationColumn, playerColumn);
        grid.getRowConstraints().setAll(contentRow);
    }

    private void configureCompactSideBySideResponsiveGrid(double navigationWidth) {
        if (responsiveContent == null) {
            return;
        }
        double playerWidth = embeddedPlayer != null && embeddedPlayer.isManaged()
                ? COMPACT_SIDE_PLAYER_WIDTH
                : 0;
        ColumnConstraints navigationColumn = new ColumnConstraints();
        navigationColumn.setMinWidth(0);
        navigationColumn.setPrefWidth(Math.max(0, navigationWidth));
        navigationColumn.setHgrow(Priority.ALWAYS);
        navigationColumn.setFillWidth(true);
        ColumnConstraints playerColumn = new ColumnConstraints();
        playerColumn.setMinWidth(playerWidth);
        playerColumn.setPrefWidth(playerWidth);
        playerColumn.setMaxWidth(playerWidth);
        playerColumn.setHgrow(Priority.NEVER);
        playerColumn.setFillWidth(true);
        RowConstraints contentRow = new RowConstraints();
        contentRow.setMinHeight(0);
        contentRow.setVgrow(Priority.ALWAYS);
        contentRow.setFillHeight(true);
        responsiveContent.getColumnConstraints().setAll(navigationColumn, playerColumn);
        responsiveContent.getRowConstraints().setAll(contentRow);
    }

    private void configureStackedResponsiveGrid() {
        if (responsiveContent == null) {
            return;
        }
        double playerHeight = stackedEmbeddedPlayerHeight();
        ColumnConstraints contentColumn = new ColumnConstraints();
        contentColumn.setMinWidth(0);
        contentColumn.setHgrow(Priority.ALWAYS);
        contentColumn.setFillWidth(true);
        RowConstraints playerRow = new RowConstraints();
        playerRow.setMinHeight(playerHeight);
        playerRow.setPrefHeight(playerHeight);
        playerRow.setMaxHeight(playerHeight);
        playerRow.setVgrow(Priority.NEVER);
        playerRow.setFillHeight(true);
        RowConstraints navigationRow = new RowConstraints();
        navigationRow.setMinHeight(0);
        navigationRow.setVgrow(Priority.ALWAYS);
        navigationRow.setFillHeight(true);
        responsiveContent.getColumnConstraints().setAll(contentColumn);
        responsiveContent.getRowConstraints().setAll(playerRow, navigationRow);
    }

    private void applyStackedEmbeddedPlayerSize() {
        embeddedPlayer.prefHeightProperty().unbind();
        embeddedPlayer.maxHeightProperty().unbind();
        double playerWidth = stackedEmbeddedPlayerWidth();
        embeddedPlayer.setMinWidth(0);
        embeddedPlayer.setPrefWidth(playerWidth);
        embeddedPlayer.setMaxWidth(playerWidth);
        double playerHeight = stackedEmbeddedPlayerHeight();
        embeddedPlayer.setMinHeight(playerHeight);
        embeddedPlayer.setPrefHeight(playerHeight);
        embeddedPlayer.setMaxHeight(playerHeight);
        embeddedPlayer.setAlignment(Pos.CENTER);
    }

    private double stackedEmbeddedPlayerWidth() {
        double width = availableLayoutWidth();
        if (responsiveContent != null && responsiveContent.getWidth() > 0) {
            width = Math.min(width, responsiveContent.getWidth());
        }
        return Math.max(0, Math.min(STACKED_EMBEDDED_PLAYER_MAX_WIDTH, width));
    }

    private double stackedEmbeddedPlayerHeight() {
        double width = stackedEmbeddedPlayerWidth();
        return Math.max(
                STACKED_EMBEDDED_PLAYER_MIN_HEIGHT,
                Math.min(
                        STACKED_EMBEDDED_PLAYER_MAX_HEIGHT,
                        width * STACKED_EMBEDDED_PLAYER_ASPECT_RATIO + STACKED_EMBEDDED_PLAYER_VERTICAL_CHROME
                )
        );
    }

    private void placeInGrid(Node node, int column, int row) {
        GridPane.setColumnIndex(node, column);
        GridPane.setRowIndex(node, row);
        GridPane.setColumnSpan(node, 1);
        GridPane.setRowSpan(node, 1);
    }

    private int columnIndex(Node node) {
        Integer index = GridPane.getColumnIndex(node);
        return index == null ? 0 : index;
    }

    private int rowIndex(Node node) {
        Integer index = GridPane.getRowIndex(node);
        return index == null ? 0 : index;
    }

    private boolean shouldUseStackedEmbeddedLayout() {
        return availableLayoutWidth() < STACKED_EMBEDDED_LAYOUT_WIDTH_THRESHOLD;
    }

    private double compactSideNavigationWidth() {
        double availableWidth = availableLayoutWidth();
        if (embeddedPlayer == null || !embeddedPlayer.isManaged()) {
            return availableWidth;
        }
        return Math.max(0, availableWidth - COMPACT_SIDE_PLAYER_WIDTH);
    }

    private boolean isEmbeddedPlayerNodeActive() {
        return embeddedPlayerNode != null && (embeddedPlayerNode.isVisible() || embeddedPlayerNode.isManaged());
    }

    private void setEmbeddedPlayerContainerVisible(boolean visible) {
        if (embeddedPlayer == null) {
            return;
        }
        if (embeddedPlayer.isVisible() != visible || embeddedPlayer.isManaged() != visible) {
            logEmbeddedPlayerNodeEvent("wrapper visible/managed -> " + visible);
        }
        embeddedPlayer.setVisible(visible);
        embeddedPlayer.setManaged(visible);
    }

    private void logEmbeddedLayoutState(
            String layout,
            boolean embeddedConfigured,
            boolean stackedPlayerLayout,
            boolean playerNodeActive,
            boolean widePlayerPreferred
    ) {
        double availableWidth = availableLayoutWidth();
        long widthBucket = Math.round(availableWidth / 25.0) * 25;
        String signature = layout
                + "|bucket=" + widthBucket
                + "|configured=" + embeddedConfigured
                + "|stacked=" + stackedPlayerLayout
                + "|node=" + visibleManagedState(embeddedPlayerNode)
                + "|wrapper=" + visibleManagedState(embeddedPlayer)
                + "|nodeParent=" + nodeName(parentOf(embeddedPlayerNode))
                + "|wrapperParent=" + nodeName(parentOf(embeddedPlayer))
                + "|mainChildren=" + childSummary(mainContent)
                + "|gridChildren=" + childSummary(responsiveContent);
        if (signature.equals(lastEmbeddedLayoutLogSignature)) {
            return;
        }
        lastEmbeddedLayoutLogSignature = signature;
        AppLog.addInfoLog(MainApplicationUI.class, EMBEDDED_LAYOUT_DEBUG_PREFIX
                + "layout=" + layout
                + ", availableWidth=" + formatDouble(availableWidth)
                + ", sceneWidth=" + formatDouble(sceneWidth())
                + ", stageWidth=" + formatDouble(stageWidth())
                + ", mainWidth=" + sizeOf(mainContent)
                + ", configured=" + embeddedConfigured
                + ", stacked=" + stackedPlayerLayout
                + ", nodeActive=" + playerNodeActive
                + ", widePreferred=" + widePlayerPreferred
                + ", playerNode=" + nodeState(embeddedPlayerNode)
                + ", wrapper=" + nodeState(embeddedPlayer)
                + ", navigation=" + nodeState(navigationShell)
                + ", collapsedHandle=" + nodeState(collapsedNavigationHandleShell)
                + ", responsiveGrid=" + nodeState(responsiveContent)
                + ", mainChildren=" + childSummary(mainContent)
                + ", gridChildren=" + childSummary(responsiveContent));
    }

    private void logEmbeddedPlayerNodeEvent(String event) {
        AppLog.addInfoLog(MainApplicationUI.class, EMBEDDED_LAYOUT_DEBUG_PREFIX
                + event
                + ", availableWidth=" + formatDouble(availableLayoutWidth())
                + ", playerNode=" + nodeState(embeddedPlayerNode)
                + ", wrapper=" + nodeState(embeddedPlayer)
                + ", mainChildren=" + childSummary(mainContent)
                + ", gridChildren=" + childSummary(responsiveContent));
    }

    private double sceneWidth() {
        if (mainContent != null && mainContent.getScene() != null) {
            return mainContent.getScene().getWidth();
        }
        if (primaryStage != null && primaryStage.getScene() != null) {
            return primaryStage.getScene().getWidth();
        }
        return -1;
    }

    private double stageWidth() {
        return primaryStage == null ? -1 : primaryStage.getWidth();
    }

    private Parent parentOf(Node node) {
        return node == null ? null : node.getParent();
    }

    private String nodeState(Node node) {
        if (node == null) {
            return "null";
        }
        return nodeName(node)
                + "{visible=" + node.isVisible()
                + ", managed=" + node.isManaged()
                + ", parent=" + nodeName(node.getParent())
                + ", size=" + sizeOf(node)
                + ", layoutBounds=" + formatDouble(node.getLayoutBounds().getWidth())
                + "x" + formatDouble(node.getLayoutBounds().getHeight())
                + "}";
    }

    private String visibleManagedState(Node node) {
        return node == null ? "null" : node.isVisible() + "/" + node.isManaged();
    }

    private String sizeOf(Node node) {
        if (node instanceof Region region) {
            return formatDouble(region.getWidth()) + "x" + formatDouble(region.getHeight())
                    + " pref=" + formatDouble(region.getPrefWidth()) + "x" + formatDouble(region.getPrefHeight())
                    + " min=" + formatDouble(region.getMinWidth()) + "x" + formatDouble(region.getMinHeight())
                    + " max=" + formatDouble(region.getMaxWidth()) + "x" + formatDouble(region.getMaxHeight());
        }
        if (node == null) {
            return "null";
        }
        return formatDouble(node.getBoundsInParent().getWidth()) + "x" + formatDouble(node.getBoundsInParent().getHeight());
    }

    private String childSummary(javafx.scene.Parent parent) {
        if (parent == null) {
            return "null";
        }
        return parent.getChildrenUnmodifiable().stream()
                .map(this::nodeName)
                .toList()
                .toString();
    }

    private String nodeName(Node node) {
        if (node == null) {
            return "null";
        }
        String id = node.getId();
        return node.getClass().getSimpleName() + (id == null || id.isBlank() ? "" : "#" + id);
    }

    private String formatDouble(double value) {
        if (value == Double.MAX_VALUE) {
            return "MAX";
        }
        if (value == Region.USE_COMPUTED_SIZE) {
            return "COMPUTED";
        }
        if (value == Region.USE_PREF_SIZE) {
            return "PREF";
        }
        return String.format(Locale.ROOT, "%.1f", value);
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
        if (embeddedPlayer == null || !embeddedPlayer.isManaged() || !isEmbeddedPlayerNodeActive()) {
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
        double availableWidth = availableLayoutWidth();
        double minWidth = availableWidth < WIDE_APP_AREA_SMALL_SCREEN_THRESHOLD
                ? WIDE_APP_AREA_SMALL_SCREEN_MIN_WIDTH
                : WIDE_APP_AREA_MIN_WIDTH;
        return Math.max(minWidth, Math.min(WIDE_APP_AREA_MAX_WIDTH, availableWidth * WIDE_APP_AREA_FRACTION));
    }

    private double availableLayoutWidth() {
        double width = Double.MAX_VALUE;
        double sceneWidth = sceneWidth();
        width = minPositive(width, sceneWidth);
        width = minPositive(width, stageWidth());
        width = minPositive(width, regionWidth(mainContent));
        width = minPositive(width, regionWidth(responsiveContent));
        if (shouldUseExpandedSceneWidth(sceneWidth, width)) {
            return sceneWidth;
        }
        if (width != Double.MAX_VALUE) {
            return width;
        }
        return guidedMaxWidthPixels;
    }

    private boolean shouldUseExpandedSceneWidth(double sceneWidth, double measuredWidth) {
        if (sceneWidth < STACKED_EMBEDDED_LAYOUT_WIDTH_THRESHOLD || measuredWidth == Double.MAX_VALUE
                || measuredWidth <= 0) {
            return false;
        }
        if (primaryStage != null && primaryStage.isMaximized()) {
            return true;
        }
        return measuredWidth < STACKED_EMBEDDED_LAYOUT_WIDTH_THRESHOLD
                && sceneWidth - measuredWidth >= STALE_LAYOUT_WIDTH_JUMP_THRESHOLD;
    }

    private double minPositive(double current, double candidate) {
        return candidate > 0 ? Math.min(current, candidate) : current;
    }

    private double regionWidth(Region region) {
        return region == null ? -1 : region.getWidth();
    }
}
