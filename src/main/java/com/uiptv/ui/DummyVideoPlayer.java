package com.uiptv.ui;

import com.uiptv.api.VideoPlayerInterface;
import com.uiptv.model.PlayerResponse;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

public class DummyVideoPlayer implements VideoPlayerInterface {
    @Override
    public void play(PlayerResponse response) {

    }

    @Override
    public void stop() {
        // Do nothing
    }

    @Override
    public void toggleFullscreen() {
        // Do nothing
    }

    @Override
    public Node getPlayerContainer() {
        Pane pane = new Pane();
        pane.setPrefSize(0, 0);
        return pane;
    }

    @Override
    public PlayerType getType() {
        return PlayerType.DUMMY;
    }
}
