package com.uiptv.player;

import com.uiptv.api.VideoPlayerInterface;
import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.DummyVideoPlayer;
import javafx.scene.Node;
import javafx.scene.layout.Region;

public class MediaPlayerFactory {

    private static VideoPlayerInterface instance;


    public static synchronized VideoPlayerInterface getPlayer() {
        if (instance == null) {
            if (ConfigurationService.getInstance().read().isEmbeddedPlayer()) {
                try {
                    instance = new VlcVideoPlayer();
                    System.out.println("VLC found. Using it for embedded player");
                } catch (Throwable bundledError) {
                    System.out.println("VLC not found. Using Lite player that plays limited set of videos");
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
