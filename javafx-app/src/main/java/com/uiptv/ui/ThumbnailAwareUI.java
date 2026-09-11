package com.uiptv.ui;

import com.uiptv.service.ConfigurationService;
import javafx.application.Platform;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Base class providing common functionality for UI components that support both
 * thumbnail and plain text rendering modes based on configuration.
 */
public abstract class ThumbnailAwareUI {
    private static final long THUMBNAIL_STATE_REFRESH_MS =
            Long.getLong("uiptv.thumbnail.state.refresh.ms", 1_000L);

    public interface ThumbnailModeListener {
        void onThumbnailModeChanged(boolean enabled);
    }

    private static final List<ThumbnailModeListener> THUMBNAIL_MODE_LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile boolean lastKnownThumbnailState = readThumbnailState();
    private static volatile long lastThumbnailStateReadMs = System.currentTimeMillis();

    /**
     * Determines if thumbnails are enabled globally in configuration.
     * When false, UI should render plain text without images.
     */
    public static boolean areThumbnailsEnabled() {
        refreshThumbnailStateIfStale();
        return lastKnownThumbnailState;
    }

    public static void addThumbnailModeListener(ThumbnailModeListener listener) {
        if (listener == null) {
            return;
        }
        THUMBNAIL_MODE_LISTENERS.add(listener);
    }

    public static void removeThumbnailModeListener(ThumbnailModeListener listener) {
        if (listener == null) {
            return;
        }
        THUMBNAIL_MODE_LISTENERS.remove(listener);
    }

    public static void notifyThumbnailModeChanged(boolean enabled) {
        boolean changed = enabled != lastKnownThumbnailState;
        lastKnownThumbnailState = enabled;
        lastThumbnailStateReadMs = System.currentTimeMillis();
        if (!changed) {
            return;
        }
        Runnable notifier = () -> {
            for (ThumbnailModeListener listener : THUMBNAIL_MODE_LISTENERS) {
                try {
                    listener.onThumbnailModeChanged(enabled);
                } catch (Exception _) {
                    // Isolate listener failures so one broken view does not block thumbnail updates globally.
                }
            }
        };
        if (Platform.isFxApplicationThread()) {
            notifier.run();
        } else {
            Platform.runLater(notifier);
        }
    }

    private static void refreshThumbnailStateIfStale() {
        long now = System.currentTimeMillis();
        if (now - lastThumbnailStateReadMs < THUMBNAIL_STATE_REFRESH_MS) {
            return;
        }
        lastKnownThumbnailState = readThumbnailState(lastKnownThumbnailState);
        lastThumbnailStateReadMs = now;
    }

    private static boolean readThumbnailState() {
        return readThumbnailState(true);
    }

    private static boolean readThumbnailState(boolean fallback) {
        try {
            var config = ConfigurationService.getInstance().read();
            return config == null || config.isEnableThumbnails();
        } catch (Exception _) {
            // Keep the last known state if configuration is temporarily unavailable during startup.
        }
        return fallback;
    }
}
