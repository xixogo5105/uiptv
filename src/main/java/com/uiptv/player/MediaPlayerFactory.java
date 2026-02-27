package com.uiptv.player;

import com.uiptv.api.VideoPlayerInterface;
import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.DummyVideoPlayer;
import com.uiptv.ui.LogDisplayUI;
import javafx.scene.Node;
import javafx.scene.layout.Region;

public class MediaPlayerFactory {

    private static VideoPlayerInterface instance;
    public static VideoPlayerInterface.PlayerType playerType;

    public static synchronized VideoPlayerInterface getPlayer() {
        if (instance == null) {
            if (ConfigurationService.getInstance().read().isEmbeddedPlayer()) {
                try {
                    instance = new VlcVideoPlayer();
                    playerType = VideoPlayerInterface.PlayerType.VLC;
                    LogDisplayUI.addLog("VLC found. Using it for embedded player");
                } catch (Throwable bundledError) {
                    LogDisplayUI.addLog("VLC not found. Using Lite player that plays limited set of videos");
                    instance = new LiteVideoPlayer();
                    playerType = VideoPlayerInterface.PlayerType.LITE;
                }
                if (instance.getPlayerContainer() instanceof Region playerContainer) {
                    definePlayerRegion(playerContainer);
                }
            } else {
                instance = new DummyVideoPlayer();
                playerType = VideoPlayerInterface.PlayerType.DUMMY;
                if (instance.getPlayerContainer() instanceof Region playerContainer) {
                    defineDummyRegion(playerContainer);
                }
            }
        }
        return instance;
    }

    public static VideoPlayerInterface.PlayerType getPlayerType() {
        if (playerType == null) {
            getPlayer(); // Ensure player is initialized
        }
        return playerType;
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
