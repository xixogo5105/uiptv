package com.uiptv.ui;

import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class WatchingNowUI extends VBox {
    private final BaseWatchingNowUI delegate;

    public WatchingNowUI() {
        if (ThumbnailAwareUI.areThumbnailsEnabled()) {
            delegate = new ThumbnailWatchingNowUI();
        } else {
            delegate = new PlainWatchingNowUI();
        }
        getChildren().add(delegate);
        VBox.setVgrow(delegate, Priority.ALWAYS);
    }

    public void forceReload() {
        delegate.forceReload();
    }

    public void refreshIfNeeded() {
        delegate.refreshIfNeeded();
    }
}
