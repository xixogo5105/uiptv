package com.uiptv.api;

import com.uiptv.model.PlayerResponse;
import javafx.scene.Node;

public interface VideoPlayerInterface {

    enum PlayerType {
        VLC,
        LITE,
        DUMMY
    }
    
    void play(PlayerResponse response);
    void stop();
    void toggleFullscreen();
    Node getPlayerContainer();
    PlayerType getType();
}
