package com.uiptv.ui;
import com.uiptv.ui.util.*;
import com.uiptv.ui.util.*;

import com.uiptv.util.I18n;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

public class WatchingNowUI extends VBox {
    private TabPane tabPane;
    private BaseWatchingNowUI seriesDelegate;
    private VodWatchingNowUI vodDelegate;
    private boolean thumbnailListenerRegistered = false;
    private final ThumbnailAwareUI.ThumbnailModeListener thumbnailModeListener = enabled -> refreshThumbnailMode();

    public WatchingNowUI() {
        this(JavaFxServices.current());
    }

    public WatchingNowUI(JavaFxServices services) {
        tabPane = buildTabs();
        getChildren().setAll(tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        registerThumbnailModeListener();
    }

    public void forceReload() {
        seriesDelegate.forceReload();
        vodDelegate.forceReload();
    }

    public void refreshIfNeeded() {
        seriesDelegate.refreshIfNeeded();
        vodDelegate.refreshIfNeeded();
    }

    static String refreshButtonLabel() {
        return I18n.tr("autoReloadCache");
    }

    private TabPane buildTabs() {
        seriesDelegate = buildSeriesDelegate();
        vodDelegate = new VodWatchingNowUI();

        TabPane tabs = new TabPane();
        List<String> tabLabels = tabLabels();
        Tab seriesTab = new Tab(tabLabels.get(0), buildTabContent(seriesDelegate));
        seriesTab.setClosable(false);
        Tab vodTab = new Tab(tabLabels.get(1), buildTabContent(vodDelegate));
        vodTab.setClosable(false);
        tabs.getTabs().setAll(seriesTab, vodTab);
        tabs.getSelectionModel().select(0);
        return tabs;
    }

    private VBox buildTabContent(BaseWatchingNowUI delegate) {
        Button refreshButton = new Button(refreshButtonLabel());
        refreshButton.setOnAction(event -> delegate.forceReload());
        VBox content = new VBox(8, refreshButton, delegate);
        VBox.setVgrow(delegate, Priority.ALWAYS);
        return content;
    }

    private VBox buildTabContent(VodWatchingNowUI delegate) {
        Button refreshButton = new Button(refreshButtonLabel());
        refreshButton.setOnAction(event -> delegate.forceReload());
        VBox content = new VBox(8, refreshButton, delegate);
        VBox.setVgrow(delegate, Priority.ALWAYS);
        return content;
    }

    static List<String> tabLabels() {
        return List.of(I18n.tr("autoSeries"), I18n.tr("autoVod"));
    }

    private BaseWatchingNowUI buildSeriesDelegate() {
        if (ThumbnailAwareUI.areThumbnailsEnabled()) {
            return new ThumbnailWatchingNowUI();
        }
        return new PlainWatchingNowUI();
    }

    private void registerThumbnailModeListener() {
        if (thumbnailListenerRegistered) {
            return;
        }
        ThumbnailAwareUI.addThumbnailModeListener(thumbnailModeListener);
        thumbnailListenerRegistered = true;
        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                ThumbnailAwareUI.removeThumbnailModeListener(thumbnailModeListener);
                thumbnailListenerRegistered = false;
            } else if (!thumbnailListenerRegistered) {
                ThumbnailAwareUI.addThumbnailModeListener(thumbnailModeListener);
                thumbnailListenerRegistered = true;
            }
        });
    }

    private void refreshThumbnailMode() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        String selectedTabText = selectedTab == null ? "" : selectedTab.getText();
        tabPane = buildTabs();
        getChildren().setAll(tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        if (I18n.tr("autoVod").equals(selectedTabText)) {
            tabPane.getSelectionModel().select(1);
        } else {
            tabPane.getSelectionModel().select(0);
        }
    }
}
