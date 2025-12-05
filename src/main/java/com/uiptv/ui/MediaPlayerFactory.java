package com.uiptv.ui;

import com.uiptv.api.DummyEmbeddedVideoPlayer;
import com.uiptv.api.EmbeddedVideoPlayer;
import com.uiptv.service.ConfigurationService;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

public class MediaPlayerFactory {

    private static EmbeddedVideoPlayer instance;
    private static final Pane PLAYER_PANE = new Pane();


    public static synchronized EmbeddedVideoPlayer getPlayer() {
        if (instance == null) {
            if (ConfigurationService.getInstance().read().isEmbeddedPlayer()) {
                try {
                    instance = new VlcVideoPlayer();
                } catch (Exception ignored) {
                    System.err.println("Failed to load VLC libraries. Falling back to JavaFX media player. Falling back to the Native player that can only play h264 videos");
                    instance = new LiteVideoPlayer();
                }
                if (instance.getPlayerContainer() instanceof Region playerContainer) {
                    definePlayerRegion(playerContainer);
                    PLAYER_PANE.getChildren().clear();
                    PLAYER_PANE.getChildren().addAll(playerContainer);
                    definePlayerRegion(PLAYER_PANE);
                }
            } else {
                instance = new DummyEmbeddedVideoPlayer();
                if (instance.getPlayerContainer() instanceof Region playerContainer) {
                    defineDummyRegion(playerContainer);
                    PLAYER_PANE.getChildren().clear();
                    PLAYER_PANE.getChildren().addAll(playerContainer);
                    defineDummyRegion(PLAYER_PANE);
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
        playerContainer.setManaged(true);

    }

    private static void defineDummyRegion(Region playerContainer) {
        playerContainer.setMinWidth(0);
        playerContainer.setPrefWidth(0);
        playerContainer.setMaxWidth(0);
        playerContainer.setMinHeight(0);
        playerContainer.setPrefHeight(0);
        playerContainer.setMaxHeight(0);
        playerContainer.setManaged(false);
    }

    public static synchronized Node getPlayerContainer() {
        return PLAYER_PANE;
    }
}
