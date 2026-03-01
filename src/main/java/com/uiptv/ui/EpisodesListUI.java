package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.shared.EpisodeList;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public class EpisodesListUI extends HBox {
    private final Account account;
    private final String categoryTitle;
    private final String seriesId;
    private final String seriesCategoryId;
    private BaseEpisodesListUI delegate;
    private EpisodeList lastEpisodeList;
    private boolean loadingCompleteCalled = false;
    private boolean thumbnailListenerRegistered = false;
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

    private void registerThumbnailModeListener() {
        if (thumbnailListenerRegistered) {
            return;
        }
        ThumbnailAwareUI.addThumbnailModeListener(thumbnailModeListener);
        thumbnailListenerRegistered = true;
        sceneProperty().addListener((obs, oldScene, newScene) -> {
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
}
