package com.uiptv.widget;

import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

public class CloseIconButton extends Button {
    private static final String CLOSE_ICON_PATH = "M18.3 5.71 12 12l6.3 6.29-1.41 1.41-6.3-6.29-6.29 6.29-1.41-1.41L9.17 12 2.88 5.71 4.29 4.29l6.3 6.3 6.29-6.3z";

    public CloseIconButton(String tooltipText) {
        String safeTooltipText = tooltipText == null ? "" : tooltipText;
        SVGPath icon = new SVGPath();
        icon.setContent(CLOSE_ICON_PATH);
        icon.getStyleClass().add("uiptv-circular-close-icon");
        UiRenderQuality.optimizeTextNode(icon);

        getStyleClass().add("uiptv-circular-close-button");
        UiRenderQuality.optimizeTextNode(this);
        setText("");
        setGraphic(icon);
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        setFocusTraversable(false);
        setAccessibleText(safeTooltipText);

        Tooltip tooltip = new Tooltip(safeTooltipText);
        tooltip.setShowDelay(Duration.millis(250));
        tooltip.setHideDelay(Duration.millis(80));
        tooltip.setShowDuration(Duration.seconds(4));
        setTooltip(tooltip);
    }
}
