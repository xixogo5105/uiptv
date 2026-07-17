package com.uiptv.widget;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

public class LoadingStateView extends HBox {
    private static final double DEFAULT_INDICATOR_SIZE = 18;
    private final Label label = new Label();

    public LoadingStateView(String message) {
        this(message, DEFAULT_INDICATOR_SIZE);
    }

    public LoadingStateView(String message, double indicatorSize) {
        getStyleClass().add("uiptv-loading-state");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(8);
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        ProgressIndicator indicator = new ProgressIndicator();
        indicator.getStyleClass().add("uiptv-progress-indicator");
        indicator.setPrefSize(indicatorSize, indicatorSize);
        indicator.setMinSize(indicatorSize, indicatorSize);
        indicator.setMaxSize(indicatorSize, indicatorSize);

        label.getStyleClass().add("uiptv-loading-state-label");
        label.setWrapText(true);
        setMessage(message);

        getChildren().addAll(indicator, label);
    }

    public void setMessage(String message) {
        label.setText(message == null ? "" : message);
    }
}
