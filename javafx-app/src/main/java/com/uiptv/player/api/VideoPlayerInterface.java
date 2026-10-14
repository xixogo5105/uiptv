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

    /** Completes after teardown and native preparation; await off the JavaFX thread before requesting a short-lived URL. */
    default java.util.concurrent.CompletionStage<Void> playbackReady() {
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    void showLoading();

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
