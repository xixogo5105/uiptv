package com.uiptv.player;

import com.uiptv.api.VideoPlayerInterface;
import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.DummyVideoPlayer;
import com.uiptv.util.AppLog;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

public class MediaPlayerFactory {
    private static VideoPlayerInterface instance;
    private static VideoPlayerInterface.PlayerType playerType;
    private static final StackPane playerHostContainer = new StackPane();
    private static boolean hostConfigured = false;

    static {
        playerHostContainer.visibleProperty().addListener((_, _, _) -> requestLayoutHierarchy());
        playerHostContainer.managedProperty().addListener((_, _, _) -> requestLayoutHierarchy());
        playerHostContainer.parentProperty().addListener((_, _, _) -> requestLayoutHierarchy());
    }

    private MediaPlayerFactory() {
    }

    public static synchronized VideoPlayerInterface getPlayer() {
        if (instance == null) {
            // Lazy initialization - player only created when first needed
            initializePlayer();
        }
        return instance;
    }

    private static void initializePlayer() {
        Configuration config = ConfigurationService.getInstance().read();
        if (config != null && config.isEmbeddedPlayer()) {
            try {
                instance = new VlcVideoPlayer();
                playerType = VideoPlayerInterface.PlayerType.VLC;
                AppLog.addInfoLog(MediaPlayerFactory.class, "VLC found. Using it for embedded player");
            } catch (Exception e) {
                AppLog.addWarningLog(MediaPlayerFactory.class, "VLC not found. Using Lite player that plays limited set of videos. Error: " + e.getMessage());
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
        syncHostToPlayerNode(instance.getPlayerContainer());
        playerHostContainer.getChildren().setAll(instance.getPlayerContainer());
        requestLayoutHierarchy();
        hostConfigured = true;
    }

    public static VideoPlayerInterface.PlayerType getPlayerType() {
        if (playerType == null) {
            getPlayer(); // Ensure player is initialized
        }
        return playerType;
    }

    public static synchronized VideoPlayerInterface.PlayerType getInitializedPlayerType() {
        return playerType;
    }

    private static void definePlayerRegion(Region playerContainer) {
        playerContainer.setMinSize(0, 0);
        playerContainer.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        playerContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
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
        if (!hostConfigured) {
            Configuration config = ConfigurationService.getInstance().read();
            if (config != null && config.isEmbeddedPlayer()) {
                definePlayerRegion(playerHostContainer);
            } else {
                defineDummyRegion(playerHostContainer);
            }
            playerHostContainer.setVisible(false);
            playerHostContainer.setManaged(false);
            requestLayoutHierarchy();
            hostConfigured = true;
        }
        return playerHostContainer;
    }

    private static void syncHostToPlayerNode(Node playerNode) {
        if (playerNode == null) {
            playerHostContainer.visibleProperty().unbind();
            playerHostContainer.managedProperty().unbind();
            playerHostContainer.setVisible(false);
            playerHostContainer.setManaged(false);
            return;
        }
        playerHostContainer.visibleProperty().unbind();
        playerHostContainer.managedProperty().unbind();
        playerHostContainer.visibleProperty().bind(playerNode.visibleProperty());
        playerHostContainer.managedProperty().bind(playerNode.managedProperty());
        requestLayoutHierarchy();
    }

    private static void requestLayoutHierarchy() {
        playerHostContainer.requestLayout();
        Parent parent = playerHostContainer.getParent();
        while (parent != null) {
            parent.requestLayout();
            parent = parent.getParent();
        }
        if (playerHostContainer.getScene() != null && playerHostContainer.getScene().getRoot() != null) {
            playerHostContainer.getScene().getRoot().requestLayout();
        }
    }

    /**
     * Releases and disposes the player singleton.
     * Must be called on application shutdown to prevent memory leaks.
     * Cleans up all resources held by the player.
     */
    public static synchronized void release() {
        if (instance != null) {
            try {
                // Call player-specific disposal (especially important for VLC)
                instance.disposePlayer();
            } catch (Exception e) {
                // Best-effort shutdown: continue releasing shared state even if player teardown fails.
                AppLog.addErrorLog(MediaPlayerFactory.class, "Failed to dispose player: " + e.getMessage());
            }

            try {
                // Clear player container
                playerHostContainer.getChildren().clear();
            } catch (Exception e) {
                // Best-effort shutdown: stale JavaFX nodes should not block process exit.
                AppLog.addErrorLog(MediaPlayerFactory.class, "Failed to clear player container: " + e.getMessage());
            }

            instance = null;
        }
        syncHostToPlayerNode(null);
        playerType = null;
        hostConfigured = false;
    }
}
