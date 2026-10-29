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

        Circle recordingRing = new Circle(5);
        recordingRing.getStyleClass().add("playing-card-recording-ring");
        Circle recordingDot = new Circle(1.4);
        recordingDot.getStyleClass().add("playing-card-recording-dot");
        getChildren().addAll(recordingRing, recordingDot);

    }

    /** Kept for existing callers; the playing marker never animates. */
    boolean isAnimationRunning() {
        return false;
    }
}
