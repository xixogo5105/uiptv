package com.uiptv.ui;

import com.uiptv.util.I18n;
import com.uiptv.widget.AppHeaderActions;
import com.uiptv.widget.SearchFieldBehavior;
import com.uiptv.widget.AppPageHeader;
import com.uiptv.widget.PillBar;
import com.uiptv.widget.UiRenderQuality;
import javafx.animation.PauseTransition;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class WatchingNowUI extends VBox {
    private static final String SERIES_TAB = "series";
    private static final String VOD_TAB = "vod";
    private static final Duration SEARCH_DEBOUNCE_DELAY = Duration.millis(180);

    private PillBar<String> modePillBar;
    private StackPane contentPane;
    private BaseWatchingNowUI seriesDelegate;
    private VodWatchingNowUI vodDelegate;
    private final TextField searchTextField = new TextField();
    private final HBox searchRow = new HBox(8);
    private final PauseTransition searchDebounce = new PauseTransition(SEARCH_DEBOUNCE_DELAY);
    private final HostServices hostServices;
    private final Runnable themeToggleHandler;
    private boolean thumbnailListenerRegistered = false;
    private String pendingSearchQuery = "";
    private final AtomicBoolean activationRefreshScheduled = new AtomicBoolean(false);
    private final ThumbnailAwareUI.ThumbnailModeListener thumbnailModeListener = enabled -> refreshThumbnailMode();

    public WatchingNowUI() {
        this(null, null);
    }

    public WatchingNowUI(HostServices hostServices, Runnable themeToggleHandler) {
        this.hostServices = hostServices;
        this.themeToggleHandler = themeToggleHandler;
        searchTextField.setPromptText(I18n.tr("commonSearch"));
        searchDebounce.setOnFinished(_ -> applyPendingSearchQuery());
        searchTextField.textProperty().addListener((_, _, query) -> scheduleSearchQuery(query));
        searchTextField.setOnAction(_ -> applyPendingSearchQuery());
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

    public void requestContentFocus() {
        requestActiveContentFocus();
    }

    private void buildContent() {
        seriesDelegate = buildSeriesDelegate();
        vodDelegate = new VodWatchingNowUI();

        modePillBar = new PillBar<>(
                item -> SERIES_TAB.equals(item) ? I18n.tr("autoSeries") : I18n.tr("autoVod"),
                item -> item
        );
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

        searchTextField.setPromptText(I18n.tr("commonSearch"));
        SearchFieldBehavior.installMouseClear(searchTextField);
        searchRow.getChildren().setAll(searchTextField);
        searchRow.getStyleClass().add("search-row");
        searchRow.setMinWidth(0);
        searchRow.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(searchTextField, Priority.ALWAYS);
        AppPageHeader header = new AppPageHeader(
                I18n.tr("autoWatchingNow"),
                new AppHeaderActions(hostServices, themeToggleHandler, null)
        );
        header.getStyleClass().add("watching-now-header");

        if (!getStyleClass().contains("watching-now-page")) {
            getStyleClass().add("watching-now-page");
        }
        setSpacing(10);
        setMinWidth(0);
        setMaxWidth(Double.MAX_VALUE);
        getChildren().setAll(header, modePillBar, searchRow, contentPane);
        VBox.setVgrow(contentPane, Priority.ALWAYS);
        modePillBar.setSelectedItem(SERIES_TAB);
        showSelectedMode(SERIES_TAB);
    }

    private void scheduleSearchQuery(String query) {
        pendingSearchQuery = normalizeSearchInput(query);
        searchDebounce.stop();
        if (pendingSearchQuery.trim().isEmpty()) {
            applyPendingSearchQuery();
            return;
        }
        searchDebounce.playFromStart();
    }

    private void applyPendingSearchQuery() {
        applySearchQueryToSelectedMode(pendingSearchQuery);
    }

    private void applySearchQueryToSelectedMode(String query) {
        String activeQuery = normalizeSearchInput(query);
        if (isVodSelected()) {
            SearchTarget.apply(vodDelegate, activeQuery);
            return;
        }
        SearchTarget.apply(seriesDelegate, activeQuery);
    }

    private String currentSearchQuery() {
        String currentText = searchTextField == null ? "" : searchTextField.getText();
        pendingSearchQuery = normalizeSearchInput(currentText);
        return pendingSearchQuery;
    }

    private String normalizeSearchInput(String query) {
        return query == null ? "" : query;
    }

    private void detachFromParent(Node node) {
        if (node == null || node.getParent() == null) {
            return;
        }
        if (node.getParent() instanceof Pane pane) {
            pane.getChildren().remove(node);
        }
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
        applySearchQueryToSelectedMode(currentSearchQuery());
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
        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                unregisterThumbnailModeListener();
                disposeDelegates();
            } else {
                registerThumbnailModeListenerIfNeeded();
                syncThumbnailModeWithConfiguration();
            }
        });
        if (getScene() != null) {
            registerThumbnailModeListenerIfNeeded();
        }
    }

    private void registerThumbnailModeListenerIfNeeded() {
        if (thumbnailListenerRegistered) {
            return;
        }
        ThumbnailAwareUI.addThumbnailModeListener(thumbnailModeListener);
        thumbnailListenerRegistered = true;
    }

    private void unregisterThumbnailModeListener() {
        if (!thumbnailListenerRegistered) {
            return;
        }
        ThumbnailAwareUI.removeThumbnailModeListener(thumbnailModeListener);
        thumbnailListenerRegistered = false;
    }

    private void syncThumbnailModeWithConfiguration() {
        if (seriesDelegate == null || vodDelegate == null || modePillBar == null) {
            buildContent();
            return;
        }
        boolean shouldUseThumbnails = ThumbnailAwareUI.areThumbnailsEnabled();
        boolean usingThumbnailDelegate = seriesDelegate instanceof ThumbnailWatchingNowUI;
        if (shouldUseThumbnails != usingThumbnailDelegate) {
            refreshThumbnailMode();
        }
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
            requestActiveContentFocus();
        });
    }

    private void requestActiveContentFocus() {
        Platform.runLater(() -> {
            if (!isPageDisplayable()) {
                return;
            }
            if (isVodSelected()) {
                vodDelegate.requestContentFocus();
            } else if (seriesDelegate != null) {
                seriesDelegate.requestContentFocus();
            }
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
        searchDebounce.stop();
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
