package com.uiptv.ui.main;

import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationChangeListener;
import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.AccountListUI;
import javafx.application.Platform;
import javafx.application.HostServices;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.util.function.Consumer;

public class MainApplicationUI extends BaseMainApplicationUI {
    private static final double WIDE_APP_AREA_FRACTION = 0.44;
    private static final double WIDE_APP_AREA_MIN_WIDTH = 720;
    private static final double WIDE_APP_AREA_SMALL_SCREEN_MIN_WIDTH = 560;
    private static final double WIDE_APP_AREA_SMALL_SCREEN_THRESHOLD = 1300;
    private static final double WIDE_APP_AREA_MAX_WIDTH = 900;
    private final boolean embeddedEnabled;
    private final ConfigurationChangeListener embeddedLayoutChangeListener =
            _ -> Platform.runLater(this::applyEmbeddedPlayerLayoutFromConfiguration);
    private HBox mainContent;
    private HBox embeddedPlayer;
    private StackPane navigationShell;
    private TabPane activeTabPane;

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
        embeddedPlayer = createEmbeddedPlayerContainer();
        embeddedPlayer.managedProperty().addListener((_, _, _) -> applyEmbeddedPlayerLayoutFromConfiguration());
        navigationShell = createNavigationShell(tabPane);

        mainContent = new HBox(navigationShell, embeddedPlayer);
        mainContent.setFillHeight(true);
        mainContent.setMinSize(0, 0);
        mainContent.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        mainContent.widthProperty().addListener((_, _, _) -> applyEmbeddedPlayerLayoutFromConfiguration());

        tabPane.setMinWidth(0);
        tabPane.setPrefWidth(guidedMaxWidthPixels);
        tabPane.setMaxWidth(Double.MAX_VALUE);
        tabPane.setMaxHeight(Double.MAX_VALUE);
        tabPane.setMinHeight(0);

        configurationService.addChangeListener(embeddedLayoutChangeListener);
        applyEmbeddedPlayerLayoutFromConfiguration();
        return mainContent;
    }

    private void applyEmbeddedPlayerLayoutFromConfiguration() {
        if (embeddedPlayer == null || navigationShell == null || activeTabPane == null) {
            return;
        }
        Configuration configuration = configurationService.read();
        boolean widePlayerVisible = configuration != null
                && configuration.isEmbeddedPlayer()
                && configuration.isWideView()
                && embeddedPlayer.isManaged();
        if (widePlayerVisible) {
            applyWideEmbeddedLayout();
        } else {
            applyCompactEmbeddedLayout();
        }
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
        HBox.setHgrow(navigationShell, Priority.ALWAYS);

        applyCompactEmbeddedPlayerSize(embeddedPlayer);
        HBox.setHgrow(embeddedPlayer, Priority.NEVER);
    }

    private void applyWideEmbeddedLayout() {
        double appAreaWidth = preferredWideAppAreaWidth();
        activeTabPane.setMinWidth(0);
        activeTabPane.setPrefWidth(appAreaWidth);
        activeTabPane.setMaxWidth(Double.MAX_VALUE);
        activeTabPane.setMaxHeight(Double.MAX_VALUE);
        activeTabPane.setMinHeight(0);

        navigationShell.setMinWidth(0);
        navigationShell.setPrefWidth(appAreaWidth);
        navigationShell.setMaxWidth(appAreaWidth);
        HBox.setHgrow(navigationShell, Priority.NEVER);

        applyWideEmbeddedPlayerSize(embeddedPlayer);
        HBox.setHgrow(embeddedPlayer, Priority.ALWAYS);
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
