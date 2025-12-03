package com.uiptv.ui;

import com.uiptv.api.EmbeddedVideoPlayer;
import javafx.scene.layout.Region;
// import com.uiptv.ui.JavafxMediaPlayer; // Uncomment if you want to switch to JavafxMediaPlayer

public class MediaPlayerFactory {

    private static EmbeddedVideoPlayer instance; // Holds the single instance of MediaPlayer

    // Private constructor removed as the factory itself is no longer a singleton

    // Returns a single instance of MediaPlayer
    public static synchronized EmbeddedVideoPlayer createMediaPlayer() { // Made static
        if (instance == null) {
            // For now, we'll default to VlcVideoPlayer.
            // In a more complex scenario, you might read a configuration
            // to decide which implementation to return (e.g., based on user preference or system capabilities).
//            instance = new JavafxEmbeddedVideoPlayer();
            instance = new VlcVideoPlayer();
            if (instance.getPlayerContainer() instanceof Region playerContainer) { // Usage updated
                // Usage updated
                playerContainer.setMinWidth(470);
                playerContainer.setPrefWidth(470);
                playerContainer.setMaxWidth(470);
                playerContainer.setMinHeight(275);
                playerContainer.setPrefHeight(275);
                playerContainer.setMaxHeight(275);
            }
        }
        return instance;
    }
}
