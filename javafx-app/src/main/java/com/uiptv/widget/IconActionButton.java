package com.uiptv.widget;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

public class IconActionButton extends Button {
    private Tooltip tooltip;
    private final SVGPath icon = new SVGPath();

    public IconActionButton(String tooltipText, String iconPath, Runnable action) {
        getStyleClass().add("bookmarks-quick-action-button");
        UiRenderQuality.optimizeTextNode(this);
        UiRenderQuality.optimizeTextNode(icon);
        setFocusTraversable(true);
        setAccessibleText(tooltipText);

        if (tooltipText != null && !tooltipText.isEmpty()) {
            tooltip = new Tooltip(tooltipText);
            tooltip.setShowDelay(Duration.millis(250));
            tooltip.setHideDelay(Duration.millis(80));
            tooltip.setShowDuration(Duration.seconds(4));
            tooltip.setConsumeAutoHidingEvents(false);
            setTooltip(tooltip);
        } else {
            tooltip = null;
        }

        icon.getStyleClass().add("bookmarks-quick-action-icon");
        icon.setMouseTransparent(true);
        setIconPath(iconPath);
        setGraphic(icon);
        setOnAction(_ -> action.run());
    }

    public void setTooltipText(String tooltipText) {
        setAccessibleText(tooltipText);
        if (tooltip == null) {
            if (tooltipText != null && !tooltipText.isEmpty()) {
                tooltip = new Tooltip(tooltipText);
                tooltip.setShowDelay(Duration.millis(250));
                tooltip.setHideDelay(Duration.millis(80));
                tooltip.setShowDuration(Duration.seconds(4));
                tooltip.setConsumeAutoHidingEvents(false);
                setTooltip(tooltip);
            }
        } else {
            tooltip.setText(tooltipText);
        }
    }

    public void setIconPath(String iconPath) {
        icon.setContent(iconPath);
    }
}
