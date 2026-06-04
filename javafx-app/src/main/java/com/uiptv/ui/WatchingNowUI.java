package com.uiptv.ui;

import com.uiptv.util.I18n;
import com.uiptv.widget.PillBar;
import com.uiptv.widget.UiRenderQuality;
import javafx.application.Platform;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class WatchingNowUI extends VBox {
    private static final String SERIES_TAB = "series";
    private static final String VOD_TAB = "vod";

    private PillBar<String> modePillBar;
    private StackPane contentPane;
    private BaseWatchingNowUI seriesDelegate;
    private VodWatchingNowUI vodDelegate;
    private boolean thumbnailListenerRegistered = false;
    private final AtomicBoolean activationRefreshScheduled = new AtomicBoolean(false);
    private final ThumbnailAwareUI.ThumbnailModeListener thumbnailModeListener = enabled -> refreshThumbnailMode();

    public WatchingNowUI() {
        buildContent();
        registerThumbnailModeListener();
        registerActivationRefreshTriggers();
    }

    public void forceReload() {
        if (seriesDelegate != null) {
            seriesDelegate.markDirty();
        }
        if (vodDelegate != null) {
            vodDelegate.markDirty();
        }
        refreshIfNeeded();
    }

    public void refreshIfNeeded() {
        if (!isPageDisplayable()) {
            scheduleActivationRefresh();
            return;
        }
        if (isVodSelected()) {
            vodDelegate.refreshIfNeeded();
        } else if (seriesDelegate != null) {
            seriesDelegate.refreshIfNeeded();
        }
    }

    private void buildContent() {
        seriesDelegate = buildSeriesDelegate();
        vodDelegate = new VodWatchingNowUI();

        modePillBar = new PillBar<>(
                item -> SERIES_TAB.equals(item) ? I18n.tr("autoSeries") : I18n.tr("autoVod"),
                item -> item
        );
        modePillBar.getStyleClass().add("watching-now-mode-pill-bar");
        modePillBar.setItems(List.of(SERIES_TAB, VOD_TAB));
        modePillBar.selectedItemProperty().addListener((_, _, selected) -> showSelectedMode(selected));

        contentPane = new StackPane();
        contentPane.getStyleClass().add("watching-now-content-pane");
        contentPane.setMinWidth(0);
        contentPane.setMaxWidth(Double.MAX_VALUE);
        UiRenderQuality.optimizeLayout(contentPane);
        VBox seriesContent = buildTabContent(seriesDelegate);
        VBox vodContent = buildTabContent(vodDelegate);
        vodDelegate.setVisible(false);
        vodDelegate.setManaged(false);
        vodContent.setVisible(false);
        vodContent.setManaged(false);
        contentPane.getChildren().setAll(seriesContent, vodContent);

        if (!getStyleClass().contains("watching-now-page")) {
            getStyleClass().add("watching-now-page");
        }
        setSpacing(10);
        setMinWidth(0);
        setMaxWidth(Double.MAX_VALUE);
        getChildren().setAll(modePillBar, contentPane);
        VBox.setVgrow(contentPane, Priority.ALWAYS);
        modePillBar.setSelectedItem(SERIES_TAB);
        showSelectedMode(SERIES_TAB);
    }

    private VBox buildTabContent(BaseWatchingNowUI delegate) {
        VBox content = new VBox(8, delegate);
        content.setMinWidth(0);
        content.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(delegate, Priority.ALWAYS);
        return content;
    }

    private VBox buildTabContent(VodWatchingNowUI delegate) {
        VBox content = new VBox(8, delegate);
        content.setMinWidth(0);
        content.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(delegate, Priority.ALWAYS);
        return content;
    }

    static List<String> tabLabels() {
        return List.of(I18n.tr("autoSeries"), I18n.tr("autoVod"));
    }

    private void showSelectedMode(String selected) {
        if (contentPane == null || seriesDelegate == null || vodDelegate == null) {
            return;
        }
        boolean showVod = VOD_TAB.equals(selected);
        seriesDelegate.setVisible(!showVod);
        seriesDelegate.setManaged(!showVod);
        vodDelegate.setVisible(showVod);
        vodDelegate.setManaged(showVod);
        for (javafx.scene.Node child : contentPane.getChildren()) {
            boolean childIsVod = child instanceof VBox vbox
                    && !vbox.getChildren().isEmpty()
                    && vbox.getChildren().getFirst() == vodDelegate;
            child.setVisible(showVod == childIsVod);
            child.setManaged(showVod == childIsVod);
        }
        if (showVod) {
            vodDelegate.refreshIfNeeded();
        } else {
            seriesDelegate.refreshIfNeeded();
        }
        scheduleActivationRefresh();
    }

    private boolean isVodSelected() {
        return modePillBar != null && VOD_TAB.equals(modePillBar.getSelectedItem()) && vodDelegate != null;
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
                disposeDelegates();
            } else if (!thumbnailListenerRegistered) {
                ThumbnailAwareUI.addThumbnailModeListener(thumbnailModeListener);
                thumbnailListenerRegistered = true;
                if (seriesDelegate == null || vodDelegate == null) {
                    buildContent();
                }
            }
        });
    }

    private void registerActivationRefreshTriggers() {
        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene != null) {
                scheduleActivationRefresh();
            }
        });
        visibleProperty().addListener((_, _, visible) -> {
            if (Boolean.TRUE.equals(visible)) {
                scheduleActivationRefresh();
            }
        });
        parentProperty().addListener((_, _, _) -> scheduleActivationRefresh());
    }

    private void scheduleActivationRefresh() {
        if (!activationRefreshScheduled.compareAndSet(false, true)) {
            return;
        }
        Platform.runLater(() -> {
            activationRefreshScheduled.set(false);
            if (!isPageDisplayable()) {
                return;
            }
            refreshIfNeeded();
        });
    }

    private boolean isPageDisplayable() {
        if (getScene() == null) {
            return false;
        }
        javafx.scene.Node node = this;
        while (node != null) {
            if (!node.isVisible()) {
                return false;
            }
            node = node.getParent();
        }
        return true;
    }

    private void refreshThumbnailMode() {
        String selectedTab = modePillBar == null ? SERIES_TAB : modePillBar.getSelectedItem();
        disposeDelegates();
        buildContent();
        modePillBar.setSelectedItem(VOD_TAB.equals(selectedTab) ? VOD_TAB : SERIES_TAB);
    }

    private void disposeDelegates() {
        if (seriesDelegate != null) {
            seriesDelegate.dispose();
            seriesDelegate = null;
        }
        if (vodDelegate != null) {
            vodDelegate.dispose();
            vodDelegate = null;
        }
        contentPane = null;
        modePillBar = null;
    }
}
