package com.uiptv.widget;

import javafx.geometry.Pos;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

public class PlayingCardIndicator extends StackPane {
    private static final String INNER_ARC = "M8 15c2.2-2.2 5.8-2.2 8 0";
    private static final String OUTER_ARC = "M4.5 11.5c4.1-4.1 10.9-4.1 15 0";
    private final SVGPath innerArc;
    private final SVGPath outerArc;
    private final Timeline broadcastAnimation;

    public PlayingCardIndicator() {
        getStyleClass().add("playing-card-indicator");
        setAlignment(Pos.CENTER);
        setMinSize(28, 28);
        setPrefSize(28, 28);
        setMaxSize(28, 28);
        setMouseTransparent(true);
        setManaged(false);
        setVisible(false);

        innerArc = createArc(INNER_ARC);
        outerArc = createArc(OUTER_ARC);
        Circle broadcastDot = new Circle(12, 19, 1.7);
        broadcastDot.getStyleClass().add("playing-card-broadcast-dot");
        getChildren().add(new Group(innerArc, outerArc, broadcastDot));

        broadcastAnimation = new Timeline(
                new KeyFrame(Duration.ZERO, pulseStates(innerArc, 0.35, 0.92, outerArc, 0.35, 0.92)),
                new KeyFrame(Duration.millis(350), pulseStates(innerArc, 0.95, 1.08, outerArc, 0.35, 0.92)),
                new KeyFrame(Duration.millis(700), pulseStates(innerArc, 0.35, 0.92, outerArc, 0.95, 1.08)),
                new KeyFrame(Duration.millis(1050), pulseState(outerArc, 0.35, 0.92)),
                new KeyFrame(Duration.millis(1400), pulseStates(innerArc, 0.35, 0.92, outerArc, 0.35, 0.92))
        );
        broadcastAnimation.setCycleCount(Animation.INDEFINITE);
        visibleProperty().addListener((_, _, visible) -> {
            if (visible) {
                broadcastAnimation.playFromStart();
            } else {
                broadcastAnimation.stop();
            }
        });
    }

    boolean isAnimationRunning() {
        return broadcastAnimation.getStatus() == Animation.Status.RUNNING;
    }

    private KeyValue[] pulseState(SVGPath arc, double opacity, double scale) {
        return new KeyValue[]{
                new KeyValue(arc.opacityProperty(), opacity, Interpolator.EASE_BOTH),
                new KeyValue(arc.scaleXProperty(), scale, Interpolator.EASE_BOTH),
                new KeyValue(arc.scaleYProperty(), scale, Interpolator.EASE_BOTH)
        };
    }

    private KeyValue[] pulseStates(SVGPath firstArc,
                                  double firstOpacity,
                                  double firstScale,
                                  SVGPath secondArc,
                                  double secondOpacity,
                                  double secondScale) {
        KeyValue[] firstState = pulseState(firstArc, firstOpacity, firstScale);
        KeyValue[] secondState = pulseState(secondArc, secondOpacity, secondScale);
        KeyValue[] combined = new KeyValue[firstState.length + secondState.length];
        System.arraycopy(firstState, 0, combined, 0, firstState.length);
        System.arraycopy(secondState, 0, combined, firstState.length, secondState.length);
        return combined;
    }

    private SVGPath createArc(String path) {
        SVGPath arc = new SVGPath();
        arc.setContent(path);
        arc.setStrokeWidth(1.7);
        arc.getStyleClass().add("playing-card-broadcast-arc");
        return arc;
    }
}
