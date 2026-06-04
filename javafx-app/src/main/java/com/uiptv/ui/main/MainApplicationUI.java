package com.uiptv.ui.main;

import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.AccountListUI;
import javafx.application.HostServices;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
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

        HBox embeddedPlayer = createEmbeddedPlayerContainer();
        applyCompactEmbeddedPlayerSize(embeddedPlayer);

        StackPane navigationShell = createNavigationShell(tabPane);
        HBox.setHgrow(navigationShell, Priority.ALWAYS);

        HBox mainContent = new HBox(navigationShell, embeddedPlayer);
        mainContent.setFillHeight(true);
        mainContent.setMinSize(0, 0);
        mainContent.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
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
