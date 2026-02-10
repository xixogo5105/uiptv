package com.uiptv.widget;

import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.transform.Rotate;

public class AnalogClock extends Pane {
    private final Line secondHand;
    private final Rotate secondHandRotate;

    public AnalogClock() {
        setPrefSize(20, 20);
        Circle border = new Circle(10, 10, 10);
        border.setFill(Color.TRANSPARENT);
        border.setStroke(Color.BLACK);

        secondHand = new Line(10, 10, 10, 2);
        secondHand.setStroke(Color.RED);

        secondHandRotate = new Rotate(0, 10, 10);
        secondHand.getTransforms().add(secondHandRotate);

        getChildren().addAll(border, secondHand);
    }

    public void setSeconds(int seconds) {
        double angle = (seconds % 60) * 6;
        secondHandRotate.setAngle(angle);
    }
}
