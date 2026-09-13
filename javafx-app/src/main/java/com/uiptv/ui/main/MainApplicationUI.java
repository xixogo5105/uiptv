package com.uiptv.ui.main;

import com.uiptv.player.MediaPlayerFactory;
import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.AccountListUI;
import javafx.application.HostServices;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
        if (playerContainer.getParent() instanceof Pane parent) {
            parent.getChildren().remove(playerContainer);
        }

        Node activePlayerNode = playerContainer;
        if (playerContainer instanceof Pane pane && !pane.getChildren().isEmpty()) {
            activePlayerNode = pane.getChildren().get(0);
        }
        if (activePlayerNode instanceof Region region) {
            region.minHeightProperty().unbind();
            region.prefHeightProperty().unbind();
            // Non-wide width: 478px player + 1px spacing each side = 480px total.
            region.setMinWidth(478);
            region.setPrefWidth(478);
            region.setMaxWidth(478);
        }
        if (playerContainer instanceof Region region) {
            region.minHeightProperty().unbind();
            region.prefHeightProperty().unbind();
            region.setMinWidth(478);
            region.setPrefWidth(478);
            region.setMaxWidth(478);
        }

        HBox embeddedPlayer = new HBox(playerContainer);
        embeddedPlayer.setPadding(new Insets(1));
        embeddedPlayer.setMinWidth(480);
        embeddedPlayer.setPrefWidth(480);
        embeddedPlayer.setMaxWidth(480);

        // DO NOT PRE-ALLOCATE SPACE:
        // Bound to player visibility so it takes 0 space when idle, and only appears when playing
        embeddedPlayer.managedProperty().bind(activePlayerNode.managedProperty());
        embeddedPlayer.visibleProperty().bind(activePlayerNode.visibleProperty());

        // Allocate 275px height when active so it is never squashed by VBox
        embeddedPlayer.minHeightProperty().bind(
                Bindings.when(activePlayerNode.visibleProperty())
                        .then(275.0)
                        .otherwise(0.0)
        );
        embeddedPlayer.prefHeightProperty().bind(
                Bindings.when(activePlayerNode.visibleProperty())
                        .then(275.0)
                        .otherwise(0.0)
        );

        VBox containerWithEmbeddedPlayer = new VBox();
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        containerWithEmbeddedPlayer.getChildren().addAll(embeddedPlayer, tabPane);

        HBox mainContent = new HBox(containerWithEmbeddedPlayer, accountListUI);
        HBox.setHgrow(tabPane, Priority.ALWAYS);
        tabPane.setMinWidth(480);
        tabPane.setPrefWidth(480);
        tabPane.setMaxWidth(480);
        return mainContent;
    }

    @Override
    protected boolean useEmbeddedAccountFlow() {
        return false;
    }
}

