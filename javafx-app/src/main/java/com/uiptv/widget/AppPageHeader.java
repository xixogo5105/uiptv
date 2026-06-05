package com.uiptv.widget;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

public class AppPageHeader extends VBox {
    private static final double COMPACT_ENTER_WIDTH = 940;
    private static final double WIDE_ENTER_WIDTH = 1040;
    private static final double WIDE_SEARCH_WIDTH = 560;

    private final Label titleLabel = new Label();
    private final TextField searchField;
    private final HBox actions = new HBox(6);
    private final StackPane wideHeaderRow = new StackPane();
    private final HBox compactTitleRow = new HBox(10);
    private final HBox compactControlsRow = new HBox(8);
    private boolean compactLayout;

    public AppPageHeader(String title, TextField searchField, List<? extends Node> actionNodes) {
        this(title, searchField, createActionContainer(actionNodes));
    }

    public AppPageHeader(String title, Node actionNode) {
        this(title, null, actionNode);
    }

    public AppPageHeader(String title, TextField searchField, Node actionNode) {
        this.searchField = searchField;
        getStyleClass().add("uiptv-page-header");
        UiRenderQuality.optimizeLayout(this);
        UiRenderQuality.optimizeLayout(actions);
        UiRenderQuality.optimizeLayout(wideHeaderRow);
        UiRenderQuality.optimizeLayout(compactTitleRow);
        UiRenderQuality.optimizeLayout(compactControlsRow);
        UiRenderQuality.optimizeTextNode(titleLabel);
        if (this.searchField != null) {
            UiRenderQuality.optimizeTextNode(this.searchField);
        }
        setFillWidth(true);
        setMaxWidth(Double.MAX_VALUE);

        titleLabel.setText(title == null ? "" : title);
        titleLabel.getStyleClass().add("uiptv-page-title");
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setPickOnBounds(false);

        if (this.searchField != null) {
            this.searchField.getStyleClass().add("uiptv-page-search-field");
            this.searchField.setMinWidth(180);
            this.searchField.setPrefWidth(WIDE_SEARCH_WIDTH);
            this.searchField.setMaxWidth(WIDE_SEARCH_WIDTH);
        }

        actions.getStyleClass().add("uiptv-page-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setMinWidth(Region.USE_PREF_SIZE);
        actions.setMaxWidth(Region.USE_PREF_SIZE);
        actions.setPickOnBounds(false);
        if (actionNode != null) {
            actions.getChildren().setAll(actionNode);
        }

        wideHeaderRow.setAlignment(Pos.CENTER_LEFT);
        wideHeaderRow.setMaxWidth(Double.MAX_VALUE);
        compactTitleRow.setAlignment(Pos.CENTER_LEFT);
        compactTitleRow.setMaxWidth(Double.MAX_VALUE);
        compactControlsRow.setAlignment(Pos.CENTER_RIGHT);
        compactControlsRow.setMaxWidth(Double.MAX_VALUE);

        applyLayout(false);
        widthProperty().addListener((_, _, width) -> applyLayoutForWidth(width.doubleValue()));
    }

    public TextField getSearchField() {
        return searchField;
    }

    public void setTitle(String title) {
        titleLabel.setText(title == null ? "" : title);
    }

    private void applyLayoutForWidth(double width) {
        if (compactLayout) {
            if (width > WIDE_ENTER_WIDTH) {
                applyLayout(false);
            }
            return;
        }
        if (width < COMPACT_ENTER_WIDTH) {
            applyLayout(true);
        }
    }

    private void applyLayout(boolean compact) {
        if (compactLayout == compact && !getChildren().isEmpty()) {
            return;
        }

        wideHeaderRow.getChildren().clear();
        compactTitleRow.getChildren().clear();
        compactControlsRow.getChildren().clear();
        getChildren().clear();
        compactLayout = compact;

        if (compact) {
            HBox.setHgrow(titleLabel, Priority.ALWAYS);
            compactTitleRow.getChildren().setAll(titleLabel);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            compactControlsRow.getChildren().setAll(spacer, actions);
            if (searchField != null) {
                searchField.setMinWidth(0);
                searchField.setMaxWidth(Double.MAX_VALUE);
                getChildren().setAll(compactTitleRow, compactControlsRow, searchField);
            } else {
                getChildren().setAll(compactTitleRow, compactControlsRow);
            }
            return;
        }

        StackPane.setAlignment(titleLabel, Pos.CENTER_LEFT);
        StackPane.setAlignment(actions, Pos.CENTER_RIGHT);
        if (searchField == null) {
            wideHeaderRow.getChildren().setAll(titleLabel, actions);
        } else {
            searchField.setMinWidth(180);
            searchField.setMaxWidth(WIDE_SEARCH_WIDTH);
            StackPane.setAlignment(searchField, Pos.CENTER);
            wideHeaderRow.getChildren().setAll(titleLabel, searchField, actions);
        }
        getChildren().setAll(wideHeaderRow);
    }

    private static HBox createActionContainer(List<? extends Node> actionNodes) {
        HBox actionContainer = new HBox(6);
        UiRenderQuality.optimizeLayout(actionContainer);
        actionContainer.setAlignment(Pos.CENTER_RIGHT);
        if (actionNodes != null) {
            actionContainer.getChildren().setAll(actionNodes);
        }
        return actionContainer;
    }
}
