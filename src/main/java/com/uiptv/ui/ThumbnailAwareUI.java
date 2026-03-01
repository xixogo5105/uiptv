package com.uiptv.ui;

import com.uiptv.service.ConfigurationService;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

/**
 * Base class providing common functionality for UI components that support both
 * thumbnail and plain text rendering modes based on configuration.
 */
public abstract class ThumbnailAwareUI {

    /**
     * Determines if thumbnails are enabled globally in configuration.
     * When false, UI should render plain text without images.
     */
    public static boolean areThumbnailsEnabled() {
        try {
            var config = ConfigurationService.getInstance().read();
            return config != null && config.isEnableThumbnails();
        } catch (Exception _) {
        }
        return false;
    }
}

