package com.uiptv.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

final class EpisodeDetailHeaderUI {
    private static final String STRONG_LABEL = "strong-label";

    private EpisodeDetailHeaderUI() {
    }

    static void configurePlainHeader(VBox header, Button backButton, Label title, MenuButton bingeWatchButton) {
        if (header == null || backButton == null || title == null || bingeWatchButton == null) {
            return;
        }
        clearPlainHeader(header);
        HBox actions = buildActionsRow(backButton, bingeWatchButton);
        applyPlainTitleStyle(title);
        header.setMaxWidth(Double.MAX_VALUE);
        header.getChildren().setAll(actions, title);
    }

    static void clearPlainHeader(VBox header) {
        if (header == null) {
            return;
        }
        for (Node child : header.getChildren()) {
            if (child instanceof HBox row) {
                row.getChildren().clear();
            }
        }
        header.getChildren().clear();
    }

    static void configureBackTitleHeader(HBox header, Button backButton, Label title) {
        if (header == null || backButton == null || title == null) {
            return;
        }
        applyInlineTitleStyle(title);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(Insets.EMPTY);
        header.setMaxWidth(Double.MAX_VALUE);
        header.getChildren().clear();
        header.getChildren().setAll(backButton, title);
    }

    static void configureBackOnlyHeader(HBox header, Button backButton) {
        if (header == null || backButton == null) {
            return;
        }
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(Insets.EMPTY);
        header.setMaxWidth(Double.MAX_VALUE);
        header.getChildren().clear();
        header.getChildren().setAll(backButton);
    }

    private static HBox buildActionsRow(Button backButton, MenuButton bingeWatchButton) {
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(Insets.EMPTY);
        actions.setMaxWidth(Double.MAX_VALUE);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        actions.getChildren().setAll(backButton, spacer, bingeWatchButton);
        return actions;
    }

    private static void applyPlainTitleStyle(Label title) {
        title.setWrapText(true);
        title.setAlignment(Pos.CENTER);
        title.setMaxWidth(Double.MAX_VALUE);
        if (!title.getStyleClass().contains(STRONG_LABEL)) {
            title.getStyleClass().add(STRONG_LABEL);
        }
    }

    private static void applyInlineTitleStyle(Label title) {
        title.setWrapText(false);
        title.setAlignment(Pos.CENTER_LEFT);
        title.setMaxWidth(Region.USE_COMPUTED_SIZE);
        title.getStyleClass().remove(STRONG_LABEL);
    }
}
