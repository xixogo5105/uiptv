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
