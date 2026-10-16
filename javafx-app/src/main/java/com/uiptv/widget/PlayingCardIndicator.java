package com.uiptv.widget;

import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;

public class PlayingCardIndicator extends StackPane {
    public PlayingCardIndicator() {
        getStyleClass().add("playing-card-indicator");
        setAlignment(Pos.CENTER);
        setMinSize(28, 28);
        setPrefSize(28, 28);
        setMaxSize(28, 28);
        setMouseTransparent(true);
        setManaged(false);
        setVisible(false);

        Circle recordingDot = new Circle(4);
        recordingDot.getStyleClass().add("playing-card-recording-dot");
        getChildren().add(recordingDot);

        visibleProperty().addListener((_, _, visible) -> {
            com.uiptv.util.AppLog.addWarningLog(PlayingCardIndicator.class,
                    "[PLAYBACK-DIAG] static recording dot " + (visible ? "VISIBLE" : "HIDDEN")
                            + " indicator=" + Integer.toHexString(System.identityHashCode(this)));
        });
    }

    /** Kept for existing callers; the replacement recording dot never animates. */
    boolean isAnimationRunning() {
        return false;
    }
}
