package com.uiptv.ui;

import com.uiptv.api.EmbeddedVideoPlayer;
import javafx.scene.layout.Region;

public class MediaPlayerFactory {

    private static EmbeddedVideoPlayer instance;

    public static synchronized EmbeddedVideoPlayer createMediaPlayer() {
        if (instance == null) {
            try {
                instance = new VlcVideoPlayer();
            } catch (Exception ignored) {
                System.err.println("Failed to load VLC libraries. Falling back to JavaFX media player. Falling back to the Native player that can only play h264 videos");
                instance = new LiteVideoPlayer();
            }

            if (instance.getPlayerContainer() instanceof Region playerContainer) {
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
