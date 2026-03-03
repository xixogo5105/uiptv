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
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.function.Consumer;

public class MainApplicationUI extends BaseMainApplicationUI {

    public MainApplicationUI(
            Stage primaryStage,
            HostServices hostServices,
            ConfigurationService configurationService,
            Consumer<Scene> fontStyleConfigurer,
            int guidedMaxWidthPixels,
            int guidedMaxHeightPixels
    ) {
        super(primaryStage, hostServices, configurationService, fontStyleConfigurer, guidedMaxWidthPixels, guidedMaxHeightPixels);
    }

    @Override
    protected HBox buildMainContent(TabPane tabPane, AccountListUI accountListUI, boolean embeddedEnabled) {
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
        embeddedPlayer.setPadding(new javafx.geometry.Insets(2));
        embeddedPlayer.managedProperty().bind(activePlayerNode.managedProperty());
        embeddedPlayer.visibleProperty().bind(activePlayerNode.visibleProperty());

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
}
