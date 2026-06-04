package com.uiptv.widget;

import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

public class PlayMenuButton extends Button {
    private static final double ICON_SIZE = 24.0;

    public PlayMenuButton(String accessibleText) {
        getStyleClass().setAll("button", "play-menu-button");
        setMnemonicParsing(false);
        setFocusTraversable(true);
        setAccessibleText(accessibleText);
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

        Tooltip tooltip = new Tooltip(accessibleText);
        tooltip.setShowDelay(Duration.millis(250));
        tooltip.setHideDelay(Duration.millis(80));
        tooltip.setShowDuration(Duration.seconds(4));
        setTooltip(tooltip);

        Pane icon = new Pane();
        icon.getStyleClass().add("play-menu-icon");
        icon.setMouseTransparent(true);
        icon.setMinSize(ICON_SIZE, ICON_SIZE);
        icon.setPrefSize(ICON_SIZE, ICON_SIZE);
        icon.setMaxSize(ICON_SIZE, ICON_SIZE);

        Circle ring = new Circle(12.0, 12.0, 8.5);
        ring.getStyleClass().add("play-menu-icon-ring");
        icon.getChildren().addAll(
                ring,
                createDot(8.0),
                createDot(12.0),
                createDot(16.0)
        );
        setGraphic(icon);
    }

    private static Circle createDot(double centerX) {
        Circle dot = new Circle(centerX, 12.0, 1.25);
        dot.getStyleClass().add("play-menu-icon-dot");
        return dot;
    }
}
