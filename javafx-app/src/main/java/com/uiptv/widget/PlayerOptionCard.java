package com.uiptv.widget;

import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class PlayerOptionCard extends HBox {
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private final RadioButton selector;
    private final Label titleLabel = new Label();

    public PlayerOptionCard(String title, String description, RadioButton selector, Node content) {
        this(title, description, selector, content, null);
    }

    public PlayerOptionCard(String title, String description, RadioButton selector, Node content, Node titleAccessory) {
        this.selector = selector;
        getStyleClass().add("settings-player-card");
        UiRenderQuality.optimizeLayout(this);
        setAlignment(Pos.TOP_LEFT);
        setSpacing(12);
        setMinWidth(0);
        setMaxWidth(Double.MAX_VALUE);
        setCursor(Cursor.HAND);
        addEventHandler(MouseEvent.MOUSE_CLICKED, this::selectFromCardClick);

        selector.getStyleClass().add("settings-player-default-radio");
        selector.setText("");
        selector.setCursor(Cursor.HAND);
        selector.setMinWidth(Region.USE_PREF_SIZE);
        selector.setMaxWidth(Region.USE_PREF_SIZE);

        setTitle(title);
        titleLabel.getStyleClass().add("settings-player-card-title");
        UiRenderQuality.optimizeTextNode(titleLabel);

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setMinWidth(0);
        titleRow.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);
        titleRow.getChildren().add(titleLabel);
        if (titleAccessory != null) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            titleRow.getChildren().addAll(spacer, titleAccessory);
        }

        VBox body = new VBox(5);
        body.setMinWidth(0);
        body.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(body, Priority.ALWAYS);
        UiRenderQuality.optimizeLayout(body);
        body.getChildren().add(titleRow);

        if (description != null && !description.isBlank()) {
            Label descriptionLabel = new Label(description);
            descriptionLabel.getStyleClass().add("settings-player-card-description");
            descriptionLabel.setWrapText(true);
            descriptionLabel.setMinWidth(0);
            descriptionLabel.setMaxWidth(Double.MAX_VALUE);
            UiRenderQuality.optimizeTextNode(descriptionLabel);
            body.getChildren().add(descriptionLabel);
        }
        if (content != null) {
            body.getChildren().add(content);
        }

        getChildren().addAll(selector, body);
        refreshSelectedState(selector.isSelected());
        selector.selectedProperty().addListener((_, _, selected) -> refreshSelectedState(selected));
    }

    public void setTitle(String title) {
        titleLabel.setText(title == null ? "" : title);
    }

    private void selectFromCardClick(MouseEvent event) {
        if (isInteractiveTarget(event.getPickResult().getIntersectedNode())) {
            return;
        }
        selector.setSelected(true);
        event.consume();
    }

    private boolean isInteractiveTarget(Node node) {
        Node current = node;
        while (current != null && current != this) {
            if (current instanceof ButtonBase || current instanceof TextInputControl) {
                return true;
            }
            Parent parent = current.getParent();
            current = parent instanceof Node parentNode ? parentNode : null;
        }
        return false;
    }

    private void refreshSelectedState(boolean selected) {
        pseudoClassStateChanged(SELECTED, selected);
    }
}
