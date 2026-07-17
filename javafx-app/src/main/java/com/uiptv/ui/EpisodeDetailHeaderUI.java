package com.uiptv.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

final class EpisodeDetailHeaderUI {
    private static final String STRONG_LABEL = "strong-label";

    private EpisodeDetailHeaderUI() {
    }

    static void configurePlainHeader(VBox header, Button backButton, Label title, MenuButton bingeWatchButton) {
        configurePlainHeader(header, backButton, title, bingeWatchButton, null);
    }

    static void configurePlainHeader(VBox header, Button backButton, Label title, MenuButton bingeWatchButton, Button reloadButton) {
        if (header == null || backButton == null || title == null || bingeWatchButton == null) {
            return;
        }
        ResponsiveHeaderActions.clearPane(header);
        FlowPane actions = buildActionsRow(backButton, bingeWatchButton, reloadButton);
        applyPlainTitleStyle(title);
        header.setMaxWidth(Double.MAX_VALUE);
        header.getChildren().setAll(actions, title);
    }

    static void clearPlainHeader(VBox header) {
        if (header == null) {
            return;
        }
        ResponsiveHeaderActions.clearPane(header);
    }

    static void configureBackTitleHeader(HBox header, Button backButton, Label title) {
        configureBackTitleHeader(header, backButton, null, title);
    }

    static void configureBackTitleHeader(HBox header, Button backButton, Button reloadButton, Label title) {
        if (header == null || backButton == null || title == null) {
            return;
        }
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(Insets.EMPTY);
        header.setMaxWidth(Double.MAX_VALUE);
        ResponsiveHeaderActions.clearPane(header);
        if (reloadButton != null) {
            FlowPane actions = buildActionsRow(backButton, null, reloadButton);
            VBox content = new VBox(6, actions, title);
            content.setMinWidth(0);
            content.setMaxWidth(Double.MAX_VALUE);
            applyWrappedInlineTitleStyle(title);
            HBox.setHgrow(content, Priority.ALWAYS);
            header.getChildren().setAll(content);
            return;
        }
        applyInlineTitleStyle(title);
        ResponsiveHeaderActions.keepActionLabelVisible(backButton);
        header.getChildren().add(backButton);
        HBox.setHgrow(title, Priority.ALWAYS);
        header.getChildren().add(title);
    }

    static void configureBackOnlyHeader(HBox header, Button backButton) {
        if (header == null || backButton == null) {
            return;
        }
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(Insets.EMPTY);
        header.setMaxWidth(Double.MAX_VALUE);
        ResponsiveHeaderActions.clearPane(header);
        ResponsiveHeaderActions.keepActionLabelVisible(backButton);
        header.getChildren().setAll(backButton);
    }

    private static FlowPane buildActionsRow(Button backButton, MenuButton bingeWatchButton, Button reloadButton) {
        FlowPane actions = ResponsiveHeaderActions.actionRow(backButton);
        actions.setPadding(Insets.EMPTY);
        if (reloadButton != null) {
            actions.getChildren().add(reloadButton);
            ResponsiveHeaderActions.keepActionLabelVisible(reloadButton);
        }
        if (bingeWatchButton != null) {
            actions.getChildren().add(bingeWatchButton);
            ResponsiveHeaderActions.keepActionLabelVisible(bingeWatchButton);
        }
        return actions;
    }

    private static void applyPlainTitleStyle(Label title) {
        title.setWrapText(true);
        title.setAlignment(Pos.CENTER);
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);
        if (!title.getStyleClass().contains(STRONG_LABEL)) {
            title.getStyleClass().add(STRONG_LABEL);
        }
    }

    private static void applyWrappedInlineTitleStyle(Label title) {
        title.setWrapText(true);
        title.setAlignment(Pos.CENTER_LEFT);
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);
        title.getStyleClass().remove(STRONG_LABEL);
    }

    private static void applyInlineTitleStyle(Label title) {
        title.setWrapText(false);
        title.setAlignment(Pos.CENTER_LEFT);
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);
        title.getStyleClass().remove(STRONG_LABEL);
    }
}
