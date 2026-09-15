package com.uiptv.widget;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.Control;
import javafx.scene.layout.Region;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

/**
 * A modern switch button component that replaces CheckBox with a toggle switch visual.
 * Provides the same API surface as CheckBox (selectedProperty, setSelected, isSelected)
 * but renders as a sliding switch track with a thumb.
 */
public class SwitchButton extends Region {

    private static final double SWITCH_WIDTH = 44;
    private static final double SWITCH_HEIGHT = 24;
    private static final double THUMB_RADIUS = 9;
    private static final double THUMB_OFFSET = 3;

    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final Rectangle track = new Rectangle(SWITCH_WIDTH, SWITCH_HEIGHT);
    private final Circle thumb = new Circle(THUMB_RADIUS);
    private EventHandler<ActionEvent> onAction;

    public SwitchButton() {
        initializeComponents();
        setupListeners();
        updateVisualState();
    }

    public SwitchButton(String text) {
        this();
    }

    private void initializeComponents() {
        track.setArcWidth(SWITCH_HEIGHT);
        track.setArcHeight(SWITCH_HEIGHT);
        thumb.setCenterX(THUMB_OFFSET + THUMB_RADIUS);
        thumb.setCenterY(SWITCH_HEIGHT / 2);

        // Add style classes so CSS can target them
        track.getStyleClass().add("track");
        thumb.getStyleClass().add("thumb");

        getChildren().setAll(track, thumb);
        setMinWidth(SWITCH_WIDTH);
        setPrefWidth(SWITCH_WIDTH);
        setMaxWidth(SWITCH_WIDTH);
        setMinHeight(SWITCH_HEIGHT);
        setPrefHeight(SWITCH_HEIGHT);
        setMaxHeight(SWITCH_HEIGHT);
        setStyle("-fx-background-color: transparent;");
    }

    private void setupListeners() {
        selectedProperty().addListener(_ -> updateVisualState());
        setOnMousePressed(_ -> {
            if (!isDisabled()) {
                setSelected(!isSelected());
                if (onAction != null) {
                    onAction.handle(new ActionEvent(this, null));
                }
            }
        });
    }

    private void updateVisualState() {
        boolean sel = isSelected();
        double thumbX = sel
                ? SWITCH_WIDTH - THUMB_OFFSET - THUMB_RADIUS
                : THUMB_OFFSET + THUMB_RADIUS;
        thumb.setCenterX(thumbX);

        ObservableList<String> styleClasses = getStyleClass();
        styleClasses.setAll("uiptv-switch-button");
        if (sel) {
            styleClasses.add("selected");
        }
        if (isDisabled()) {
            styleClasses.add("disabled");
        }
    }

    // --- Public API (CheckBox-compatible) ---

    public final BooleanProperty selectedProperty() {
        return selected;
    }

    public final boolean isSelected() {
        return selected.get();
    }

    public final void setSelected(boolean value) {
        selected.set(value);
    }

    public final void toggle() {
        setSelected(!isSelected());
    }

    public void setText(String text) {
    }

    public String getText() {
        return "";
    }

    public void setOnAction(EventHandler<ActionEvent> handler) {
        this.onAction = handler;
    }

    public void fire() {
        setSelected(!isSelected());
        if (onAction != null) {
            onAction.handle(new ActionEvent(this, null));
        }
    }

    public void setAllowIndeterminate(boolean allow) {
    }

    public void setIndeterminate(boolean indeterminate) {
    }

    public boolean isIndeterminate() {
        return false;
    }

    public void setWrapText(boolean wrap) {
    }
}