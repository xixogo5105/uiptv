package com.uiptv.api;

import javafx.scene.Node;

public interface EmbeddedVideoPlayer {

    enum PlayerType {
        VLC,
        LITE,
        DUMMY
    }
    
    void play(String source);
    void stop();
    void toggleFullscreen();
    Node getPlayerContainer();
    PlayerType getType();
}
