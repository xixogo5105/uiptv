package com.uiptv.ui;

import com.uiptv.api.VideoPlayerInterface;
import com.uiptv.service.ConfigurationService;
import javafx.scene.Node;
import javafx.scene.layout.Region;

public class MediaPlayerFactory {

    private static VideoPlayerInterface instance;


    public static synchronized VideoPlayerInterface getPlayer() {
        if (instance == null) {
            if (ConfigurationService.getInstance().read().isEmbeddedPlayer()) {
                try {
                    instance = new VlcVideoPlayer();
                } catch (Throwable bundledError) {
                    System.err.println("Failed to load VLC libraries. Falling back to the Lite player that can only play h264 videos");
                    instance = new LiteVideoPlayer();
                }
                if (instance.getPlayerContainer() instanceof Region playerContainer) {
                    definePlayerRegion(playerContainer);
                }
            } else {
                instance = new DummyVideoPlayer();
                if (instance.getPlayerContainer() instanceof Region playerContainer) {
                    defineDummyRegion(playerContainer);
                }
            }
        }
        return instance;
    }

    private static void definePlayerRegion(Region playerContainer) {
        playerContainer.setMinWidth(470);
        playerContainer.setPrefWidth(470);
        playerContainer.setMaxWidth(470);
        playerContainer.setMinHeight(275);
        playerContainer.setPrefHeight(275);
        playerContainer.setMaxHeight(275);

    }

    private static void defineDummyRegion(Region playerContainer) {
        playerContainer.setMinWidth(0);
        playerContainer.setPrefWidth(0);
        playerContainer.setMaxWidth(0);
        playerContainer.setMinHeight(0);
        playerContainer.setPrefHeight(0);
        playerContainer.setMaxHeight(0);
    }

    public static synchronized Node getPlayerContainer() {
        if (instance == null) getPlayer();
        return instance.getPlayerContainer();
    }
}
