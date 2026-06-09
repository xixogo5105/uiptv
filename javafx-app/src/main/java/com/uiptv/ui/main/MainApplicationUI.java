package com.uiptv.ui.main;

import com.uiptv.model.Configuration;
import com.uiptv.player.MediaPlayerFactory;
import com.uiptv.service.ConfigurationChangeListener;
import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.AccountListUI;
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
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MainApplicationUI extends BaseMainApplicationUI {
    private static final double WIDE_APP_AREA_FRACTION = 0.27;
    private static final double WIDE_APP_AREA_MIN_WIDTH = 420;
    private static final double WIDE_APP_AREA_SMALL_SCREEN_MIN_WIDTH = 360;
    private static final double WIDE_APP_AREA_SMALL_SCREEN_THRESHOLD = 1300;
    private static final double WIDE_APP_AREA_MAX_WIDTH = 540;
    private static final double STACKED_EMBEDDED_PLAYER_MAX_WIDTH = 480;
    private static final double STACKED_EMBEDDED_PLAYER_ASPECT_RATIO = 9.0 / 16.0;
    private static final double STACKED_EMBEDDED_PLAYER_VERTICAL_CHROME = 32;
    private static final double STACKED_EMBEDDED_PLAYER_MIN_HEIGHT = 210;
    private static final double STACKED_EMBEDDED_PLAYER_MAX_HEIGHT = 305;
    private static final double PLAYER_ADJACENT_CONTROLS_MIN_WIDTH = 720;
    private static final double PLAYER_ADJACENT_CONTROLS_RIGHT_INSET = 14;
    private static final double ACCOUNT_BROWSER_DRAWER_WIDTH_THRESHOLD = 900;
    private final boolean embeddedEnabled;
    private final ConfigurationChangeListener embeddedLayoutChangeListener =
            _ -> Platform.runLater(this::applyEmbeddedPlayerLayoutFromConfiguration);
    private final ChangeListener<Number> layoutWidthChangeListener =
            (_, _, _) -> onLayoutWidthChanged();
    private HBox mainContent;
    private HBox embeddedPlayer;
    private Node embeddedPlayerNode;
    private GridPane responsiveContent;
    private StackPane navigationShell;
    private VBox playerAdjacentControls;
    private TabPane activeTabPane;
    private AccountListUI activeAccountListUI;
    private boolean embeddedLayoutListenerRegistered;
    private boolean deferredEmbeddedLayoutRefreshPending;
    private boolean wideEmbeddedLayoutActive;
    private double retainedWideAppAreaWidth = -1;
    private DockedTopControls dockedTopControls;

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
            return createMainContent(tabPane, accountListUI);
        }

        activeTabPane = tabPane;
        activeAccountListUI = accountListUI;
        embeddedPlayerNode = MediaPlayerFactory.getPlayerContainer();
        embeddedPlayer = createEmbeddedPlayerContainer(embeddedPlayerNode);
        embeddedPlayerNode.visibleProperty().addListener((_, _, _) -> applyEmbeddedPlayerLayoutFromConfiguration());
        embeddedPlayerNode.managedProperty().addListener((_, _, _) -> applyEmbeddedPlayerLayoutFromConfiguration());
        navigationShell = createNavigationShell(tabPane);
        playerAdjacentControls = createPlayerAdjacentControls();
        responsiveContent = createResponsiveContent();
        responsiveContent.widthProperty().addListener(layoutWidthChangeListener);

        mainContent = new HBox(responsiveContent);
        mainContent.setFillHeight(true);
        mainContent.setMinSize(0, 0);
        mainContent.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        mainContent.addEventFilter(KeyEvent.KEY_PRESSED, this::handleEmbeddedChannelNavigationKeyPressed);
        mainContent.widthProperty().addListener(layoutWidthChangeListener);
        HBox.setHgrow(responsiveContent, Priority.ALWAYS);
        if (primaryStage != null) {
            primaryStage.widthProperty().addListener(layoutWidthChangeListener);
            primaryStage.maximizedProperty().addListener((_, _, _) -> onLayoutWidthChanged());
        }
        tabPane.getSelectionModel().selectedItemProperty().addListener((_, _, _) -> {
            restoreDockedTopControls();
            applyEmbeddedPlayerLayoutFromConfiguration();
        });

        tabPane.setMinWidth(0);
        tabPane.setPrefWidth(guidedMaxWidthPixels);
        tabPane.setMaxWidth(Double.MAX_VALUE);
        tabPane.setMaxHeight(Double.MAX_VALUE);
        tabPane.setMinHeight(0);

        registerEmbeddedLayoutChangeListener();
        mainContent.sceneProperty().addListener((_, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.widthProperty().removeListener(layoutWidthChangeListener);
                oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, this::handleEmbeddedChannelNavigationKeyPressed);
            }
            if (newScene == null) {
                unregisterEmbeddedLayoutChangeListener();
                return;
            }
            newScene.widthProperty().addListener(layoutWidthChangeListener);
            newScene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleEmbeddedChannelNavigationKeyPressed);
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
        retainedWideAppAreaWidth = -1;
        restoreDockedTopControls();
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
            retainedWideAppAreaWidth = -1;
            applyEmbeddedPlayerLayoutFromConfiguration();
        });
    }

    private void applyEmbeddedPlayerLayoutFromConfiguration() {
        if (embeddedPlayer == null || navigationShell == null || activeTabPane == null || activeAccountListUI == null) {
            return;
        }
        Configuration configuration = configurationService.read();
        boolean embeddedConfigured = configuration != null && configuration.isEmbeddedPlayer();
        boolean playerNodeActive = isEmbeddedPlayerNodeActive();
        boolean showEmbeddedPlayer = embeddedConfigured && playerNodeActive;
        setEmbeddedPlayerContainerVisible(showEmbeddedPlayer);
        boolean widePlayerPreferred = showEmbeddedPlayer && configuration.isWideView();
        setWideEmbeddedLayoutActive(widePlayerPreferred);
        if (!showEmbeddedPlayer) {
            restoreDockedTopControls();
            retainedWideAppAreaWidth = -1;
            activeAccountListUI.setMediaDrawerMode(false);
            applyNavigationOnlyEmbeddedLayout();
        } else if (widePlayerPreferred) {
            restoreDockedTopControls();
            activeAccountListUI.setMediaDrawerMode(true);
            applyWideEmbeddedLayout();
        } else {
            retainedWideAppAreaWidth = -1;
            activeAccountListUI.setMediaDrawerMode(shouldUseAccountMediaDrawerMode());
            restoreDockedTopControls();
            applyStackedEmbeddedLayout();
        }
        if (mainContent != null) {
            mainContent.requestLayout();
        }
        activeAccountListUI.scrollFocusedContentIntoView();
    }

    private void handleEmbeddedChannelNavigationKeyPressed(KeyEvent event) {
        if (event == null
                || event.isConsumed()
                || activeAccountListUI == null
                || !isEmbeddedPlayerNodeActive()
                || !isChannelNavigationKey(event.getCode())) {
            return;
        }
        Node focusOwner = mainContent == null || mainContent.getScene() == null
                ? null
                : mainContent.getScene().getFocusOwner();
        if (focusOwner instanceof TextInputControl) {
            return;
        }
        activeAccountListUI.handleActiveChannelNavigationKey(event);
    }

    private boolean isChannelNavigationKey(KeyCode keyCode) {
        return keyCode == KeyCode.LEFT
                || keyCode == KeyCode.RIGHT
                || keyCode == KeyCode.UP
                || keyCode == KeyCode.DOWN
                || keyCode == KeyCode.HOME
                || keyCode == KeyCode.END;
    }

    @Override
    protected boolean isSingleColumnNavigationMode() {
        return wideEmbeddedLayoutActive;
    }

    private void setWideEmbeddedLayoutActive(boolean active) {
        if (wideEmbeddedLayoutActive == active) {
            return;
        }
        wideEmbeddedLayoutActive = active;
        requestManageAccountResponsiveColumnsUpdate();
    }

    private void applyNavigationOnlyEmbeddedLayout() {
        applyNavigationOnlyEmbeddedArrangement();
        setPlayerAdjacentControlsVisible(false);
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
        activeTabPane.setVisible(true);
        activeTabPane.setManaged(true);
        HBox.setHgrow(navigationShell, Priority.ALWAYS);
        GridPane.setHgrow(navigationShell, Priority.ALWAYS);
        GridPane.setVgrow(navigationShell, Priority.ALWAYS);

        HBox.setHgrow(embeddedPlayer, Priority.NEVER);
        GridPane.setHgrow(embeddedPlayer, Priority.NEVER);
        GridPane.setVgrow(embeddedPlayer, Priority.NEVER);
        GridPane.setValignment(embeddedPlayer, VPos.TOP);
    }

    private void applyWideEmbeddedLayout() {
        applySideBySideEmbeddedArrangement();
        setPlayerAdjacentControlsVisible(false);
        double wideAppAreaWidth = retainedWideAppAreaWidth();
        configureWideResponsiveGrid(wideAppAreaWidth);
        activeTabPane.setMinWidth(wideAppAreaWidth);
        activeTabPane.setPrefWidth(wideAppAreaWidth);
        activeTabPane.setMaxWidth(wideAppAreaWidth);
        activeTabPane.setMaxHeight(Double.MAX_VALUE);
        activeTabPane.setMinHeight(0);
        activeTabPane.setVisible(true);
        activeTabPane.setManaged(true);

        navigationShell.setMinWidth(wideAppAreaWidth);
        navigationShell.setPrefWidth(wideAppAreaWidth);
        navigationShell.setMaxWidth(wideAppAreaWidth);
        navigationShell.setVisible(true);
        navigationShell.setManaged(true);
        HBox.setHgrow(navigationShell, Priority.NEVER);
        GridPane.setHgrow(navigationShell, Priority.NEVER);
        GridPane.setVgrow(navigationShell, Priority.ALWAYS);

        applyWideEmbeddedPlayerSize(embeddedPlayer);
        HBox.setHgrow(embeddedPlayer, Priority.ALWAYS);
        GridPane.setHgrow(embeddedPlayer, Priority.ALWAYS);
        GridPane.setVgrow(embeddedPlayer, Priority.ALWAYS);
        GridPane.setValignment(embeddedPlayer, VPos.CENTER);
    }

    private void applyStackedEmbeddedLayout() {
        applyStackedEmbeddedArrangement();
        setPlayerAdjacentControlsVisible(false);

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

        applyStackedEmbeddedPlayerSize();
        configureStackedResponsiveGrid();
        GridPane.setHgrow(embeddedPlayer, Priority.NEVER);
        GridPane.setVgrow(embeddedPlayer, Priority.NEVER);
        GridPane.setHalignment(embeddedPlayer, HPos.LEFT);
        GridPane.setValignment(embeddedPlayer, VPos.TOP);
    }

    private void applyPlayerAdjacentTopControlsEmbeddedLayout() {
        if (!dockSelectedPageTopControls()) {
            applyStackedEmbeddedLayout();
            return;
        }
        applyPlayerAdjacentTopControlsEmbeddedArrangement();

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
        GridPane.setHgrow(navigationShell, Priority.ALWAYS);
        GridPane.setVgrow(navigationShell, Priority.ALWAYS);

        applyStackedEmbeddedPlayerSize();
        configurePlayerAdjacentTopControlsResponsiveGrid();
        GridPane.setHgrow(embeddedPlayer, Priority.NEVER);
        GridPane.setVgrow(embeddedPlayer, Priority.NEVER);
        GridPane.setHalignment(embeddedPlayer, HPos.LEFT);
        GridPane.setValignment(embeddedPlayer, VPos.TOP);

        setPlayerAdjacentControlsVisible(true);
        playerAdjacentControls.setMaxHeight(stackedEmbeddedPlayerHeight());
        GridPane.setHgrow(playerAdjacentControls, Priority.ALWAYS);
        GridPane.setVgrow(playerAdjacentControls, Priority.NEVER);
        GridPane.setHalignment(playerAdjacentControls, HPos.LEFT);
        GridPane.setValignment(playerAdjacentControls, VPos.TOP);
    }

    private void applyNavigationOnlyEmbeddedArrangement() {
        if (responsiveContent == null || navigationShell == null || embeddedPlayer == null || playerAdjacentControls == null) {
            return;
        }
        configureNavigationOnlyResponsiveGrid();
        placeInGrid(navigationShell, 0, 0);
        placeInGrid(embeddedPlayer, 0, 0);
        placeInGrid(playerAdjacentControls, 0, 0);
        GridPane.setHgrow(navigationShell, Priority.ALWAYS);
        GridPane.setVgrow(navigationShell, Priority.ALWAYS);
        GridPane.setHgrow(embeddedPlayer, Priority.NEVER);
        GridPane.setVgrow(embeddedPlayer, Priority.NEVER);
        GridPane.setHgrow(playerAdjacentControls, Priority.NEVER);
        GridPane.setVgrow(playerAdjacentControls, Priority.NEVER);
        GridPane.setValignment(embeddedPlayer, VPos.TOP);
    }

    private void applySideBySideEmbeddedArrangement() {
        if (responsiveContent == null || navigationShell == null || embeddedPlayer == null || playerAdjacentControls == null) {
            return;
        }
        boolean alreadySideBySide = rowIndex(navigationShell) == 0
                && columnIndex(navigationShell) == 0
                && rowIndex(embeddedPlayer) == 0
                && columnIndex(embeddedPlayer) == 1;
        if (alreadySideBySide) {
            return;
        }
        configureSideBySideResponsiveGrid();
        placeInGrid(navigationShell, 0, 0);
        placeInGrid(embeddedPlayer, 1, 0);
        placeInGrid(playerAdjacentControls, 0, 0);
        GridPane.setVgrow(navigationShell, Priority.ALWAYS);
        GridPane.setVgrow(embeddedPlayer, Priority.NEVER);
        GridPane.setVgrow(playerAdjacentControls, Priority.NEVER);
        GridPane.setValignment(embeddedPlayer, VPos.TOP);
    }

    private void applyStackedEmbeddedArrangement() {
        if (responsiveContent == null || navigationShell == null || embeddedPlayer == null || playerAdjacentControls == null) {
            return;
        }
        boolean alreadyStacked = rowIndex(embeddedPlayer) == 0
                && columnIndex(embeddedPlayer) == 0
                && rowIndex(navigationShell) == 1
                && columnIndex(navigationShell) == 0;
        if (alreadyStacked) {
            return;
        }
        configureStackedResponsiveGrid();
        placeInGrid(embeddedPlayer, 0, 0);
        placeInGrid(navigationShell, 0, 1);
        placeInGrid(playerAdjacentControls, 0, 0);
        GridPane.setHgrow(embeddedPlayer, Priority.NEVER);
        GridPane.setVgrow(embeddedPlayer, Priority.NEVER);
        GridPane.setHalignment(embeddedPlayer, HPos.LEFT);
        GridPane.setValignment(embeddedPlayer, VPos.TOP);
        GridPane.setHgrow(navigationShell, Priority.ALWAYS);
        GridPane.setVgrow(navigationShell, Priority.ALWAYS);
        GridPane.setHgrow(playerAdjacentControls, Priority.NEVER);
        GridPane.setVgrow(playerAdjacentControls, Priority.NEVER);
    }

    private void applyPlayerAdjacentTopControlsEmbeddedArrangement() {
        if (responsiveContent == null || navigationShell == null || embeddedPlayer == null || playerAdjacentControls == null) {
            return;
        }
        boolean alreadyAdjacent = rowIndex(embeddedPlayer) == 0
                && columnIndex(embeddedPlayer) == 0
                && rowIndex(playerAdjacentControls) == 0
                && columnIndex(playerAdjacentControls) == 1
                && rowIndex(navigationShell) == 1
                && columnIndex(navigationShell) == 0;
        if (alreadyAdjacent) {
            return;
        }
        configurePlayerAdjacentTopControlsResponsiveGrid();
        placeInGrid(embeddedPlayer, 0, 0);
        placeInGrid(playerAdjacentControls, 1, 0);
        placeInGrid(navigationShell, 0, 1);
        GridPane.setColumnSpan(navigationShell, 2);
        GridPane.setHgrow(embeddedPlayer, Priority.NEVER);
        GridPane.setVgrow(embeddedPlayer, Priority.NEVER);
        GridPane.setHalignment(embeddedPlayer, HPos.LEFT);
        GridPane.setValignment(embeddedPlayer, VPos.TOP);
        GridPane.setHgrow(playerAdjacentControls, Priority.ALWAYS);
        GridPane.setVgrow(playerAdjacentControls, Priority.NEVER);
        GridPane.setHgrow(navigationShell, Priority.ALWAYS);
        GridPane.setVgrow(navigationShell, Priority.ALWAYS);
    }

    private GridPane createResponsiveContent() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("embedded-player-responsive-layout");
        grid.setMinSize(0, 0);
        grid.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        grid.getChildren().setAll(navigationShell, embeddedPlayer, playerAdjacentControls);
        configureSideBySideResponsiveGrid(grid, Priority.ALWAYS, Priority.NEVER);
        placeInGrid(navigationShell, 0, 0);
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

    private void configureWideResponsiveGrid(double navigationWidth) {
        if (responsiveContent == null) {
            return;
        }
        ColumnConstraints navigationColumn = new ColumnConstraints();
        navigationColumn.setMinWidth(navigationWidth);
        navigationColumn.setPrefWidth(navigationWidth);
        navigationColumn.setMaxWidth(navigationWidth);
        navigationColumn.setHgrow(Priority.NEVER);
        navigationColumn.setFillWidth(true);
        ColumnConstraints playerColumn = new ColumnConstraints();
        playerColumn.setMinWidth(0);
        playerColumn.setHgrow(Priority.ALWAYS);
        playerColumn.setFillWidth(true);
        RowConstraints contentRow = new RowConstraints();
        contentRow.setMinHeight(0);
        contentRow.setVgrow(Priority.ALWAYS);
        contentRow.setFillHeight(true);
        responsiveContent.getColumnConstraints().setAll(navigationColumn, playerColumn);
        responsiveContent.getRowConstraints().setAll(contentRow);
    }

    private void configureNavigationOnlyResponsiveGrid() {
        if (responsiveContent == null) {
            return;
        }
        ColumnConstraints contentColumn = new ColumnConstraints();
        contentColumn.setMinWidth(0);
        contentColumn.setHgrow(Priority.ALWAYS);
        contentColumn.setFillWidth(true);
        RowConstraints contentRow = new RowConstraints();
        contentRow.setMinHeight(0);
        contentRow.setVgrow(Priority.ALWAYS);
        contentRow.setFillHeight(true);
        responsiveContent.getColumnConstraints().setAll(contentColumn);
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

    private void configurePlayerAdjacentTopControlsResponsiveGrid() {
        if (responsiveContent == null) {
            return;
        }
        double playerWidth = stackedEmbeddedPlayerWidth();
        double playerHeight = stackedEmbeddedPlayerHeight();
        ColumnConstraints playerColumn = new ColumnConstraints();
        playerColumn.setMinWidth(playerWidth);
        playerColumn.setPrefWidth(playerWidth);
        playerColumn.setMaxWidth(playerWidth);
        playerColumn.setHgrow(Priority.NEVER);
        playerColumn.setFillWidth(true);
        ColumnConstraints navigationColumn = new ColumnConstraints();
        navigationColumn.setMinWidth(0);
        navigationColumn.setHgrow(Priority.ALWAYS);
        navigationColumn.setFillWidth(true);
        RowConstraints controlsRow = new RowConstraints();
        controlsRow.setMinHeight(playerHeight);
        controlsRow.setPrefHeight(playerHeight);
        controlsRow.setMaxHeight(playerHeight);
        controlsRow.setVgrow(Priority.NEVER);
        controlsRow.setFillHeight(true);
        RowConstraints navigationRow = new RowConstraints();
        navigationRow.setMinHeight(0);
        navigationRow.setVgrow(Priority.ALWAYS);
        navigationRow.setFillHeight(true);
        responsiveContent.getColumnConstraints().setAll(playerColumn, navigationColumn);
        responsiveContent.getRowConstraints().setAll(controlsRow, navigationRow);
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

    private boolean isEmbeddedPlayerNodeActive() {
        return embeddedPlayerNode != null && (embeddedPlayerNode.isVisible() || embeddedPlayerNode.isManaged());
    }

    private boolean shouldUsePlayerAdjacentTopControlsLayout() {
        return availableLayoutWidth() >= STACKED_EMBEDDED_PLAYER_MAX_WIDTH + PLAYER_ADJACENT_CONTROLS_MIN_WIDTH;
    }

    private boolean shouldUseAccountMediaDrawerMode() {
        return availableLayoutWidth() < ACCOUNT_BROWSER_DRAWER_WIDTH_THRESHOLD;
    }

    private void setEmbeddedPlayerContainerVisible(boolean visible) {
        if (embeddedPlayer == null) {
            return;
        }
        embeddedPlayer.setVisible(visible);
        embeddedPlayer.setManaged(visible);
    }

    private VBox createPlayerAdjacentControls() {
        VBox controls = new VBox(8);
        controls.getStyleClass().add("player-adjacent-page-controls");
        controls.setFillWidth(true);
        controls.setPadding(new Insets(0, PLAYER_ADJACENT_CONTROLS_RIGHT_INSET, 0, 0));
        controls.setMinSize(0, 0);
        controls.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        controls.setVisible(false);
        controls.setManaged(false);
        return controls;
    }

    private void setPlayerAdjacentControlsVisible(boolean visible) {
        if (playerAdjacentControls == null) {
            return;
        }
        playerAdjacentControls.setVisible(visible);
        playerAdjacentControls.setManaged(visible);
    }

    private boolean dockSelectedPageTopControls() {
        Tab selectedTab = activeTabPane == null ? null : activeTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null || playerAdjacentControls == null) {
            restoreDockedTopControls();
            return false;
        }
        if (dockedTopControls != null && dockedTopControls.tab() == selectedTab) {
            return !playerAdjacentControls.getChildren().isEmpty();
        }

        restoreDockedTopControls();
        List<DockedNode> dockedNodes = findDockableTopControls(selectedTab.getContent());
        if (dockedNodes.isEmpty()) {
            setPlayerAdjacentControlsVisible(false);
            return false;
        }
        for (DockedNode dockedNode : dockedNodes) {
            dockNode(dockedNode);
            playerAdjacentControls.getChildren().add(dockedNode.dockNode());
        }
        dockedTopControls = new DockedTopControls(selectedTab, dockedNodes);
        return true;
    }

    private void restoreDockedTopControls() {
        if (dockedTopControls == null || playerAdjacentControls == null) {
            return;
        }
        for (DockedNode dockedNode : dockedTopControls.nodes()) {
            restoreDockNode(dockedNode);
        }
        dockedTopControls = null;
        playerAdjacentControls.getChildren().clear();
        setPlayerAdjacentControlsVisible(false);
    }

    private List<DockedNode> findDockableTopControls(Node root) {
        List<DockedNode> controls = new ArrayList<>();
        Node header = firstNodeWithStyle(root, "uiptv-page-header");
        Node dockedHeader = ancestorWithStyle(header, "bookmarks-header-area");
        addDockedNode(controls, dockedHeader == null ? header : dockedHeader);

        addDockedNode(controls, firstNodeWithStyle(root, "bookmark-category-row"));
        addDockedNode(controls, firstNodeWithStyle(root, "account-toolbar"));

        Node sibling = nextDockableSibling(dockedHeader == null ? header : dockedHeader);
        addDockedNode(controls, sibling);
        return controls;
    }

    private void addDockedNode(List<DockedNode> controls, Node node) {
        if (node == null || containsDockedNode(controls, node)) {
            return;
        }
        Parent parent = node.getParent();
        if (!(parent instanceof Pane pane)) {
            return;
        }
        int index = pane.getChildren().indexOf(node);
        if (index < 0) {
            return;
        }
        controls.add(createDockedNode(pane, index, node));
    }

    private DockedNode createDockedNode(Pane parent, int index, Node node) {
        if (shouldInlineDockChildren(node) && node instanceof Pane sourcePane && !sourcePane.getChildren().isEmpty()) {
            HBox dockRow = new HBox(8);
            dockRow.getStyleClass().add("player-adjacent-inline-row");
            dockRow.setAlignment(Pos.CENTER_LEFT);
            dockRow.setFillHeight(false);
            dockRow.setMinWidth(0);
            dockRow.setMaxWidth(Double.MAX_VALUE);
            return new DockedNode(parent, index, node, dockRow, sourcePane, new ArrayList<>(sourcePane.getChildren()));
        }
        return new DockedNode(parent, index, node, node, null, List.of());
    }

    private void dockNode(DockedNode dockedNode) {
        dockedNode.parent().getChildren().remove(dockedNode.originalNode());
        if (dockedNode.childSource() == null) {
            return;
        }
        dockedNode.childSource().getChildren().clear();
        boolean firstChild = true;
        for (Node child : dockedNode.movedChildren()) {
            dockedNode.dockRowChildren().add(child);
            if (child instanceof Region region) {
                region.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(region, firstChild ? Priority.ALWAYS : Priority.NEVER);
            }
            firstChild = false;
        }
    }

    private void restoreDockNode(DockedNode dockedNode) {
        playerAdjacentControls.getChildren().remove(dockedNode.dockNode());
        if (dockedNode.childSource() != null) {
            dockedNode.dockRowChildren().removeAll(dockedNode.movedChildren());
            dockedNode.childSource().getChildren().setAll(dockedNode.movedChildren());
        }
        if (!dockedNode.parent().getChildren().contains(dockedNode.originalNode())) {
            int index = Math.min(dockedNode.index(), dockedNode.parent().getChildren().size());
            dockedNode.parent().getChildren().add(index, dockedNode.originalNode());
        }
    }

    private boolean shouldInlineDockChildren(Node node) {
        return hasStyle(node, "bookmark-category-row") || hasStyle(node, "account-toolbar");
    }

    private boolean containsDockedNode(List<DockedNode> controls, Node node) {
        for (DockedNode control : controls) {
            if (control.originalNode() == node || isAncestor(control.originalNode(), node)
                    || isAncestor(node, control.originalNode())) {
                return true;
            }
        }
        return false;
    }

    private Node nextDockableSibling(Node node) {
        if (node == null || !(node.getParent() instanceof Pane pane)) {
            return null;
        }
        int index = pane.getChildren().indexOf(node);
        if (index < 0 || index + 1 >= pane.getChildren().size()) {
            return null;
        }
        Node sibling = pane.getChildren().get(index + 1);
        if (hasStyle(sibling, "uiptv-pill-bar")) {
            return sibling;
        }
        return null;
    }

    private Node firstNodeWithStyle(Node root, String styleClass) {
        if (root == null) {
            return null;
        }
        if (hasStyle(root, styleClass)) {
            return root;
        }
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node match = firstNodeWithStyle(child, styleClass);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private Node ancestorWithStyle(Node node, String styleClass) {
        Parent parent = node == null ? null : node.getParent();
        while (parent != null) {
            if (hasStyle(parent, styleClass)) {
                return parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    private boolean isAncestor(Node ancestor, Node node) {
        Parent parent = node == null ? null : node.getParent();
        while (parent != null) {
            if (parent == ancestor) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private boolean hasStyle(Node node, String styleClass) {
        return node != null && node.getStyleClass().contains(styleClass);
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
        double sceneWidth = sceneWidth();
        double mainWidth = regionWidth(mainContent);
        double contentWidth = regionWidth(responsiveContent);
        if (sceneWidth > 0) {
            double measuredWidth = contentWidth > 0 ? contentWidth : mainWidth;
            if (measuredWidth <= 0) {
                return sceneWidth;
            }
            if (mainWidth > 0 && Math.abs(sceneWidth - mainWidth) > 1) {
                return sceneWidth;
            }
            return Math.min(measuredWidth, sceneWidth);
        }
        if (contentWidth > 0) {
            return contentWidth;
        }
        if (mainWidth > 0) {
            return mainWidth;
        }
        return guidedMaxWidthPixels;
    }

    private double sceneWidth() {
        Scene scene = mainContent == null ? null : mainContent.getScene();
        return scene == null ? -1 : scene.getWidth();
    }

    private double regionWidth(Region region) {
        return region == null ? -1 : region.getWidth();
    }

    private record DockedNode(
            Pane parent,
            int index,
            Node originalNode,
            Node dockNode,
            Pane childSource,
            List<Node> movedChildren
    ) {
        private List<Node> dockRowChildren() {
            return dockNode instanceof Pane pane ? pane.getChildren() : List.of();
        }
    }

    private record DockedTopControls(Tab tab, List<DockedNode> nodes) {
    }
}
