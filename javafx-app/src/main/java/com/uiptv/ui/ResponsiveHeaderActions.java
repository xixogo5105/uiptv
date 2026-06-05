package com.uiptv.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

final class ResponsiveHeaderActions {
    private static final double ACTION_GAP = 8;

    private ResponsiveHeaderActions() {
    }

    static FlowPane actionRow(Node... actions) {
        FlowPane row = new FlowPane(ACTION_GAP, ACTION_GAP);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);
        for (Node action : actions) {
            if (action == null) {
                continue;
            }
            keepActionLabelVisible(action);
            row.getChildren().add(action);
        }
        return row;
    }

    static VBox stackedTopBar(Node headerText, String styleClass, Node... actions) {
        FlowPane actionRow = actionRow(actions);
        VBox topBar = new VBox(ACTION_GAP, headerText, actionRow);
        if (styleClass != null && !styleClass.isBlank()) {
            topBar.getStyleClass().add(styleClass);
        }
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setFillWidth(true);
        topBar.setMinWidth(0);
        topBar.setMaxWidth(Double.MAX_VALUE);
        if (headerText instanceof Region region) {
            region.setMinWidth(0);
            region.setMaxWidth(Double.MAX_VALUE);
        }
        return topBar;
    }

    static void keepActionLabelVisible(Node action) {
        if (action instanceof Region region) {
            region.setMinWidth(Region.USE_PREF_SIZE);
            region.setMaxWidth(Region.USE_PREF_SIZE);
        }
        if (action instanceof ButtonBase button) {
            button.setWrapText(false);
        }
    }

    static void clearPane(Pane pane) {
        if (pane == null) {
            return;
        }
        for (Node child : List.copyOf(pane.getChildren())) {
            if (child instanceof Pane childPane) {
                clearPane(childPane);
            }
        }
        pane.getChildren().clear();
    }
}
