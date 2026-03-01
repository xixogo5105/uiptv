package com.uiptv.ui;

import com.uiptv.service.ConfigurationService;
import javafx.application.Platform;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

/**
 * Base class providing common functionality for UI components that support both
 * thumbnail and plain text rendering modes based on configuration.
 */
public abstract class ThumbnailAwareUI {
    public interface ThumbnailModeListener {
        void onThumbnailModeChanged(boolean enabled);
    }

    private static final List<ThumbnailModeListener> THUMBNAIL_MODE_LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile boolean lastKnownThumbnailState = readThumbnailState();

    /**
     * Determines if thumbnails are enabled globally in configuration.
     * When false, UI should render plain text without images.
     */
    public static boolean areThumbnailsEnabled() {
        return readThumbnailState();
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
        if (!changed) {
            return;
        }
        Runnable notifier = () -> {
            for (ThumbnailModeListener listener : THUMBNAIL_MODE_LISTENERS) {
                try {
                    listener.onThumbnailModeChanged(enabled);
                } catch (Exception ignored) {
                }
            }
        };
        if (Platform.isFxApplicationThread()) {
            notifier.run();
        } else {
            Platform.runLater(notifier);
        }
    }

    private static boolean readThumbnailState() {
        try {
            var config = ConfigurationService.getInstance().read();
            return config != null && config.isEnableThumbnails();
        } catch (Exception ignored) {
        }
        return false;
    }
}
