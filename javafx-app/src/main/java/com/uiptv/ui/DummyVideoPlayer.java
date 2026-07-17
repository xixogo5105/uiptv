package com.uiptv.ui;

import com.uiptv.player.api.VideoPlayerInterface;
import com.uiptv.model.PlayerResponse;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

public class DummyVideoPlayer implements VideoPlayerInterface {
    private final SimpleBooleanProperty dummyPip = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty dummyFullscreen = new SimpleBooleanProperty(false);

    @Override
    public void play(PlayerResponse response) {
        // Intentionally empty: dummy player does not render media.
    }

    @Override
    public void stop() {
        // Do nothing
    }

    @Override
    public void stopForReload() {
        // Intentionally empty: dummy player has no reload state.
    }

    @Override
    public void disposePlayer() {
        // Intentionally empty: dummy player has no resources to dispose.
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

    @Override
    public boolean isPip() {
        return false;
    }

    @Override
    public boolean isFullscreen() {
        return false;
    }

    @Override
    public ReadOnlyBooleanProperty pipProperty() {
        return dummyPip;
    }

    @Override
    public ReadOnlyBooleanProperty fullscreenProperty() {
        return dummyFullscreen;
    }
}
