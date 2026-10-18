package com.uiptv.player.api;

import com.uiptv.model.PlayerResponse;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.scene.Node;

public interface VideoPlayerInterface {

    enum PlayerType {
        VLC,
        DUMMY
    }

    void play(PlayerResponse response);

    void stop();

    void stopForReload();

    /** Completes when the current native session is prepared; retired sessions clean up independently. */
    default java.util.concurrent.CompletionStage<Void> playbackReady() {
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    void showLoading();

    default void showPlaybackError() { }

    void updateChannelInfo(com.uiptv.model.Channel channel, com.uiptv.model.Account account);

    void disposePlayer();

    void toggleFullscreen();

    Node getPlayerContainer();

    PlayerType getType();

    boolean isPip();

    boolean isFullscreen();

    ReadOnlyBooleanProperty pipProperty();

    ReadOnlyBooleanProperty fullscreenProperty();
}
