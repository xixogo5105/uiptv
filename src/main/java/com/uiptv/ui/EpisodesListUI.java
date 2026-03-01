package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.shared.EpisodeList;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public class EpisodesListUI extends HBox {
    private final BaseEpisodesListUI delegate;

    public EpisodesListUI(EpisodeList channelList, Account account, String categoryTitle, String seriesId, String seriesCategoryId) {
        this(account, categoryTitle, seriesId, seriesCategoryId);
        delegate.setItems(channelList);
    }

    public EpisodesListUI(Account account, String categoryTitle, String seriesId, String seriesCategoryId) {
        if (ThumbnailAwareUI.areThumbnailsEnabled()) {
            delegate = new ThumbnailEpisodesListUI(account, categoryTitle, seriesId, seriesCategoryId);
        } else {
            delegate = new PlainEpisodesListUI(account, categoryTitle, seriesId, seriesCategoryId);
        }
        getChildren().add(delegate);
        HBox.setHgrow(delegate, Priority.ALWAYS);
    }

    public void setItems(EpisodeList newChannelList) {
        delegate.setItems(newChannelList);
    }

    public void setLoadingComplete() {
        delegate.setLoadingComplete();
    }
}
