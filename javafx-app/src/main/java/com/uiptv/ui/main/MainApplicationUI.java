package com.uiptv.ui.main;

import com.uiptv.player.MediaPlayerFactory;
import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.AccountListUI;
import javafx.application.HostServices;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.function.Consumer;

public class MainApplicationUI extends BaseMainApplicationUI {
    private final boolean embeddedEnabled;

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

        MediaPlayerFactory.getPlayer();
        Node playerContainer = MediaPlayerFactory.getPlayerContainer();
        Node activePlayerNode = playerContainer;
        if (playerContainer instanceof Pane pane && !pane.getChildren().isEmpty()) {
            activePlayerNode = pane.getChildren().get(0);
        }
        if (activePlayerNode instanceof Region region) {
            // Non-wide width: 478px player + 1px spacing each side = 480px total.
            region.setMinWidth(478);
            region.setPrefWidth(478);
            region.setMaxWidth(478);
        }

        HBox embeddedPlayer = new HBox(playerContainer);
        // Keep total row width aligned with the fixed 480px left pane.
        // 478px player + 1px left/right insets = 480px.
        embeddedPlayer.setPadding(new javafx.geometry.Insets(1));
        embeddedPlayer.setMinWidth(480);
        embeddedPlayer.setPrefWidth(480);
        embeddedPlayer.setMaxWidth(480);
        embeddedPlayer.managedProperty().bind(activePlayerNode.managedProperty());
        embeddedPlayer.visibleProperty().bind(activePlayerNode.visibleProperty());

        VBox containerWithEmbeddedPlayer = new VBox();
        StackPane navigationShell = createNavigationShell(tabPane);
        VBox.setVgrow(navigationShell, Priority.ALWAYS);
        containerWithEmbeddedPlayer.getChildren().addAll(embeddedPlayer, navigationShell);
        containerWithEmbeddedPlayer.setFillWidth(true);
        containerWithEmbeddedPlayer.setMinSize(0, 0);
        containerWithEmbeddedPlayer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        HBox mainContent = new HBox(containerWithEmbeddedPlayer);
        mainContent.setFillHeight(true);
        mainContent.setMinSize(0, 0);
        mainContent.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        HBox.setHgrow(containerWithEmbeddedPlayer, Priority.ALWAYS);
        HBox.setHgrow(navigationShell, Priority.ALWAYS);
        tabPane.setMinWidth(0);
        tabPane.setPrefWidth(guidedMaxWidthPixels);
        tabPane.setMaxWidth(Double.MAX_VALUE);
        tabPane.setMaxHeight(Double.MAX_VALUE);
        tabPane.setMinHeight(0);
        return mainContent;
    }

    @Override
    protected boolean useEmbeddedAccountFlow() {
        return false;
    }
}
