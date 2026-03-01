package com.uiptv.ui;

import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class WatchingNowUI extends VBox {
    private BaseWatchingNowUI delegate;
    private boolean thumbnailListenerRegistered = false;
    private final ThumbnailAwareUI.ThumbnailModeListener thumbnailModeListener = enabled -> refreshThumbnailMode();

    public WatchingNowUI() {
        delegate = buildDelegate();
        getChildren().add(delegate);
        VBox.setVgrow(delegate, Priority.ALWAYS);
        registerThumbnailModeListener();
    }

    public void forceReload() {
        delegate.forceReload();
    }

    public void refreshIfNeeded() {
        delegate.refreshIfNeeded();
    }

    private BaseWatchingNowUI buildDelegate() {
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
        boolean isThumbnailDelegate = delegate instanceof ThumbnailWatchingNowUI;
        if (shouldUseThumbnails == isThumbnailDelegate) {
            return;
        }
        BaseWatchingNowUI next = buildDelegate();
        getChildren().setAll(next);
        VBox.setVgrow(next, Priority.ALWAYS);
        delegate = next;
    }
}
