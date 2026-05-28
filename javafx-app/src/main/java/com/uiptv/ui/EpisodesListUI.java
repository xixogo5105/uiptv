package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.shared.EpisodeList;
import com.uiptv.util.I18n;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.json.JSONObject;

import java.util.function.Consumer;

public class EpisodesListUI extends HBox {
    private final Account account;
    private final String categoryTitle;
    private final String seriesId;
    private final String seriesCategoryId;
    private BaseEpisodesListUI delegate;
    private EpisodeList lastEpisodeList;
    private SeriesWatchState lastWatchedState;
    private final MenuButton bingeWatchButton = new MenuButton();
    private final Button reloadFromServerButton = new Button();
    private boolean loadingCompleteCalled = false;
    private boolean thumbnailListenerRegistered = false;
    private boolean watchingNowDetailStylingApplied = false;
    private boolean externalBingeWatchControlRequested = false;
    private boolean externalSeriesTitleRequested = false;
    private boolean externalReloadControlRequested = false;
    private Consumer<JSONObject> seasonInfoListener;
    private Consumer<EpisodeList> portalReloadListener;
    private final ThumbnailAwareUI.ThumbnailModeListener thumbnailModeListener = enabled -> refreshThumbnailMode();

    public EpisodesListUI(EpisodeList channelList, Account account, String categoryTitle, String seriesId, String seriesCategoryId) {
        this(account, categoryTitle, seriesId, seriesCategoryId);
        setItems(channelList);
    }

    public EpisodesListUI(Account account, String categoryTitle, String seriesId, String seriesCategoryId) {
        this.account = account;
        this.categoryTitle = categoryTitle;
        this.seriesId = seriesId;
        this.seriesCategoryId = seriesCategoryId;
        configureBingeWatchButton();
        configureReloadFromServerButton();
        this.delegate = buildDelegate();
        configureDelegate(delegate);
        getChildren().add(delegate);
        setMaxHeight(Double.MAX_VALUE);
        setMinHeight(0);
        HBox.setHgrow(delegate, Priority.ALWAYS);
        registerThumbnailModeListener();
    }

    public void setItems(EpisodeList newChannelList) {
        lastEpisodeList = newChannelList;
        delegate.setItems(newChannelList);
    }

    public void setLoadingComplete() {
        loadingCompleteCalled = true;
        delegate.setLoadingComplete();
    }

    public void applyWatchingNowDetailStyling() {
        watchingNowDetailStylingApplied = true;
        applyWatchingNowDetailStyling(delegate);
    }

    public void navigateToLastWatched(SeriesWatchState state) {
        lastWatchedState = state;
        delegate.navigateToLastWatched(state);
    }

    public void setSeasonInfoListener(Consumer<JSONObject> seasonInfoListener) {
        this.seasonInfoListener = seasonInfoListener;
        withThumbnailDelegate(thumbnail -> thumbnail.setSeasonInfoListener(seasonInfoListener));
    }

    public void setReloadFromServerListener(Consumer<EpisodeList> portalReloadListener) {
        this.portalReloadListener = portalReloadListener;
        if (delegate != null) {
            delegate.setPortalReloadListener(portalReloadListener);
        }
    }

    public MenuButton getBingeWatchButton() {
        externalBingeWatchControlRequested = true;
        applyDelegateHeaderPreferences(delegate);
        return bingeWatchButton;
    }

    public Button getReloadFromServerButton() {
        externalReloadControlRequested = true;
        applyDelegateHeaderPreferences(delegate);
        updateReloadFromServerButton();
        return reloadFromServerButton;
    }

    public boolean canReloadFromServer() {
        return delegate != null && delegate.canReloadFromServer();
    }

    public void reloadFromServer() {
        if (delegate != null) {
            delegate.reloadFromServer();
        }
    }

    public void useExternalSeriesTitle() {
        externalSeriesTitleRequested = true;
        applyDelegateHeaderPreferences(delegate);
    }

    public boolean isPlainMode() {
        return delegate instanceof PlainEpisodesListUI;
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
        boolean shouldUseThumbnails = ThumbnailAwareUI.areThumbnailsEnabled();
        boolean isThumbnailDelegate = delegate instanceof ThumbnailEpisodesListUI;
        if (shouldUseThumbnails == isThumbnailDelegate) {
            return;
        }
        delegate.setBingeWatchControlRefreshListener(null);
        delegate.setReloadControlRefreshListener(null);
        delegate.setPortalReloadListener(null);
        BaseEpisodesListUI next = buildDelegate();
        delegate = next;
        configureDelegate(delegate);
        getChildren().setAll(next);
        HBox.setHgrow(next, Priority.ALWAYS);
        if (watchingNowDetailStylingApplied) {
            applyWatchingNowDetailStyling(delegate);
        }
        if (lastEpisodeList != null) {
            delegate.setItems(lastEpisodeList);
        }
        if (seasonInfoListener != null) {
            withThumbnailDelegate(thumbnail -> thumbnail.setSeasonInfoListener(seasonInfoListener));
        }
        if (lastWatchedState != null) {
            delegate.navigateToLastWatched(lastWatchedState);
        }
        if (loadingCompleteCalled) {
            delegate.setLoadingComplete();
        }
        updateBingeWatchButton();
        updateReloadFromServerButton();
    }

    private BaseEpisodesListUI buildDelegate() {
        if (ThumbnailAwareUI.areThumbnailsEnabled()) {
            return new ThumbnailEpisodesListUI(account, categoryTitle, seriesId, seriesCategoryId);
        }
        return new PlainEpisodesListUI(account, categoryTitle, seriesId, seriesCategoryId);
    }

    private void configureDelegate(BaseEpisodesListUI target) {
        if (target == null) {
            return;
        }
        target.setBingeWatchControlRefreshListener(this::updateBingeWatchButton);
        target.setReloadControlRefreshListener(this::updateReloadFromServerButton);
        target.setPortalReloadListener(portalReloadListener);
        applyDelegateHeaderPreferences(target);
    }

    private void applyDelegateHeaderPreferences(BaseEpisodesListUI target) {
        if (target == null) {
            return;
        }
        target.setInternalBingeWatchControlVisible(!externalBingeWatchControlRequested);
        target.setInternalSeriesTitleVisible(!externalSeriesTitleRequested);
        target.setInternalReloadControlVisible(!externalReloadControlRequested);
    }

    private void configureBingeWatchButton() {
        bingeWatchButton.setFocusTraversable(true);
        bingeWatchButton.getStyleClass().setAll("button");
        bingeWatchButton.getStyleClass().add("binge-watch-menu-button");
        bingeWatchButton.setMinWidth(Region.USE_PREF_SIZE);
        bingeWatchButton.setMaxWidth(Region.USE_PREF_SIZE);
        bingeWatchButton.setOnShowing(event -> {
            ContextMenu menu = bingeWatchButton.getContextMenu();
            if (menu != null && !menu.getStyleClass().contains("binge-watch-context-menu")) {
                menu.getStyleClass().add("binge-watch-context-menu");
            }
        });
        updateBingeWatchButton();
    }

    private void updateBingeWatchButton() {
        if (delegate == null) {
            bingeWatchButton.setText("Binge Watch S01");
            bingeWatchButton.setDisable(true);
            return;
        }
        bingeWatchButton.setText(delegate.selectedBingeWatchMenuLabel());
        bingeWatchButton.getItems().clear();
        for (PlaybackUIService.PlayerOption option : PlaybackUIService.getConfiguredPlayerOptions()) {
            MenuItem playerItem = new MenuItem(option.label());
            playerItem.getStyleClass().add("binge-watch-menu-item");
            playerItem.setOnAction(event -> delegate.playSelectedBingeWatchSeason(option.playerPath()));
            bingeWatchButton.getItems().add(playerItem);
        }
        bingeWatchButton.setDisable(!delegate.hasBingeWatchEpisodes());
    }

    private void configureReloadFromServerButton() {
        reloadFromServerButton.setFocusTraversable(true);
        reloadFromServerButton.getStyleClass().setAll("button");
        reloadFromServerButton.setMinWidth(Region.USE_PREF_SIZE);
        reloadFromServerButton.setMaxWidth(Region.USE_PREF_SIZE);
        reloadFromServerButton.setOnAction(event -> reloadFromServer());
        updateReloadFromServerButton();
    }

    private void updateReloadFromServerButton() {
        if (delegate == null) {
            reloadFromServerButton.setText(I18n.tr("autoReloadFromServer"));
            reloadFromServerButton.setDisable(true);
            return;
        }
        reloadFromServerButton.setText(delegate.reloadFromServerButtonText());
        reloadFromServerButton.setDisable(delegate.reloadFromServerButtonDisabled());
    }

    private void applyWatchingNowDetailStyling(BaseEpisodesListUI target) {
        if (target instanceof ThumbnailEpisodesListUI thumbnail) {
            thumbnail.applyWatchingNowDetailStyling();
        } else if (target instanceof PlainEpisodesListUI plain) {
            plain.applyWatchingNowDetailStyling();
        }
    }

    private void withThumbnailDelegate(java.util.function.Consumer<ThumbnailEpisodesListUI> action) {
        if (action != null && delegate instanceof ThumbnailEpisodesListUI thumbnail) {
            action.accept(thumbnail);
        }
    }
}
