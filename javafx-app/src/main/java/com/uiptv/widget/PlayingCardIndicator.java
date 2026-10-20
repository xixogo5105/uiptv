package com.uiptv.widget;

import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;

public class PlayingCardIndicator extends StackPane {
    public PlayingCardIndicator() {
        getStyleClass().add("playing-card-indicator");
        setAlignment(Pos.CENTER);
        setMinSize(14, 14);
        setPrefSize(14, 14);
        setMaxSize(14, 14);
        setMouseTransparent(true);
        setManaged(false);
        setVisible(false);

        Circle recordingDot = new Circle(2);
        recordingDot.getStyleClass().add("playing-card-recording-dot");
        getChildren().add(recordingDot);

    }

    /** Kept for existing callers; the replacement recording dot never animates. */
    boolean isAnimationRunning() {
        return false;
    }
}
