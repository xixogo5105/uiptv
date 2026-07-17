package com.uiptv.widget;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public class SegmentedProgressBar extends HBox {
    private static final String STYLE_CLASS_CONTAINER = "progress-bar-container";
    private static final String STYLE_CLASS_EMPTY = "empty";

    public enum SegmentStatus {
        SUCCESS,
        WARNING,
        FAILURE
    }

    private int currentIndex = 0;

    public SegmentedProgressBar() {
        super(3);
        setMinHeight(12);
        setPrefHeight(12);
        setMaxHeight(12);
        setMinWidth(0);
        setMaxWidth(Double.MAX_VALUE);
        setFillHeight(true);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().addAll(STYLE_CLASS_CONTAINER, STYLE_CLASS_EMPTY);
    }

    public void setTotal(int total) {
        this.currentIndex = 0;
        Platform.runLater(() -> {
            getChildren().clear();
            setEmptyStyle(total <= 0);
            if (total > 0) {
                for (int i = 0; i < total; i++) {
                    Region segment = new Region();
                    segment.setMinWidth(2);
                    segment.setMaxWidth(Double.MAX_VALUE);
                    HBox.setHgrow(segment, Priority.ALWAYS);
                    segment.getStyleClass().add("progress-bar-segment");
                    segment.getStyleClass().add("progress-bar-segment-default");
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
                    node.getStyleClass().removeAll("progress-bar-segment-default", "success", "warning", "failure");
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
        Platform.runLater(() -> {
            getChildren().clear();
            setEmptyStyle(true);
        });
    }

    private void setEmptyStyle(boolean empty) {
        if (empty) {
            if (!getStyleClass().contains(STYLE_CLASS_EMPTY)) {
                getStyleClass().add(STYLE_CLASS_EMPTY);
            }
        } else {
            getStyleClass().remove(STYLE_CLASS_EMPTY);
        }
    }
}
