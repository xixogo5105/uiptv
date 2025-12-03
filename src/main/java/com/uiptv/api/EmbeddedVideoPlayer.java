package com.uiptv.api;

import javafx.scene.Node;

public interface EmbeddedVideoPlayer {
    void play(String source);
    void stop();
    void toggleFullscreen();
    Node getPlayerContainer();
}
