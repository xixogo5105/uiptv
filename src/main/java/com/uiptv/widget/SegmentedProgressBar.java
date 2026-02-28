package com.uiptv.widget;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public class SegmentedProgressBar extends HBox {
    public enum SegmentStatus {
        SUCCESS,
        WARNING,
        FAILURE
    }

    private int currentIndex = 0;

    public SegmentedProgressBar() {
        super(0); // Spacing 0
        setMinHeight(20);
        setPrefHeight(20);
        setMaxHeight(20);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("progress-bar-container");
        // Use theme-aware colors
        setStyle("-fx-background-color: -fx-control-inner-background; -fx-padding: 2; -fx-border-color: -fx-box-border; -fx-border-width: 1; -fx-border-radius: 3;");
    }

    public void setTotal(int total) {
        this.currentIndex = 0;
        Platform.runLater(() -> {
            getChildren().clear();
            if (total > 0) {
                for (int i = 0; i < total; i++) {
                    Region segment = new Region();
                    HBox.setHgrow(segment, Priority.ALWAYS);
                    segment.getStyleClass().add("progress-bar-segment");
                    // Use theme-aware colors
                    segment.setStyle("-fx-background-color: derive(-fx-control-inner-background, -10%); -fx-background-radius: 0;");
                    getChildren().add(segment);
                }
            }
        });
    }

    public void updateSegment(int index, boolean success) {
        updateSegment(index, success ? SegmentStatus.SUCCESS : SegmentStatus.FAILURE);
    }

    public void updateSegment(int index, SegmentStatus status) {
        Platform.runLater(() -> {
            if (index >= 0 && index < getChildren().size()) {
                Node node = getChildren().get(index);
                if (node instanceof Region) {
                    SegmentStatus effective = status == null ? SegmentStatus.FAILURE : status;
                    String color = switch (effective) {
                        case SUCCESS -> "#4CAF50";
                        case WARNING -> "#F59E0B";
                        case FAILURE -> "#F44336";
                    };
                    node.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 0;");
                    node.getStyleClass().add(switch (effective) {
                        case SUCCESS -> "success";
                        case WARNING -> "warning";
                        case FAILURE -> "failure";
                    });
                }
            }
        });
    }

    public void addResult(boolean success) {
        updateSegment(currentIndex, success);
        currentIndex++;
    }

    public void reset() {
        currentIndex = 0;
        Platform.runLater(this::getChildrenClear);
    }
    
    private void getChildrenClear() {
        getChildren().clear();
    }
}
