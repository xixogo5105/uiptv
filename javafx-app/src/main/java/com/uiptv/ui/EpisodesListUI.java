package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.shared.EpisodeList;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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
    private boolean loadingCompleteCalled = false;
    private boolean thumbnailListenerRegistered = false;
    private Consumer<JSONObject> seasonInfoListener;
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
        this.delegate = buildDelegate();
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
        withThumbnailDelegate(ThumbnailEpisodesListUI::applyWatchingNowDetailStyling);
    }

    public void navigateToLastWatched(SeriesWatchState state) {
        lastWatchedState = state;
        delegate.navigateToLastWatched(state);
    }

    public boolean canReloadFromServer() {
        return delegate instanceof ThumbnailEpisodesListUI;
    }

    public void reloadFromServer() {
        withThumbnailDelegate(ThumbnailEpisodesListUI::reloadFromServer);
    }

    public void setSeasonInfoListener(Consumer<JSONObject> seasonInfoListener) {
        this.seasonInfoListener = seasonInfoListener;
        withThumbnailDelegate(thumbnail -> thumbnail.setSeasonInfoListener(seasonInfoListener));
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
        BaseEpisodesListUI next = buildDelegate();
        getChildren().setAll(next);
        HBox.setHgrow(next, Priority.ALWAYS);
        delegate = next;
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
    }

    private BaseEpisodesListUI buildDelegate() {
        if (ThumbnailAwareUI.areThumbnailsEnabled()) {
            return new ThumbnailEpisodesListUI(account, categoryTitle, seriesId, seriesCategoryId);
        }
        return new PlainEpisodesListUI(account, categoryTitle, seriesId, seriesCategoryId);
    }

    private void withThumbnailDelegate(java.util.function.Consumer<ThumbnailEpisodesListUI> action) {
        if (action != null && delegate instanceof ThumbnailEpisodesListUI thumbnail) {
            action.accept(thumbnail);
        }
    }
}
