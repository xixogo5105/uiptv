package com.uiptv.widget;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Cursor;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

public class SwitchToggle extends StackPane {
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final StackPane track = new StackPane();
    private final Region thumb = new Region();

    public SwitchToggle() {
        getStyleClass().add("uiptv-switch-toggle");
        track.getStyleClass().add("uiptv-switch-track");
        thumb.getStyleClass().add("uiptv-switch-thumb");
        track.getChildren().add(thumb);
        getChildren().add(track);
        StackPane.setAlignment(thumb, Pos.CENTER_LEFT);
        setFocusTraversable(true);
        setCursor(Cursor.HAND);
        setAccessibleRole(AccessibleRole.TOGGLE_BUTTON);
        UiRenderQuality.optimizeLayout(this);
        UiRenderQuality.optimizeLayout(track);
        UiRenderQuality.optimizeLayout(thumb);

        selected.addListener((_, _, _) -> refreshState());
        setOnMouseClicked(event -> {
            if (!isDisabled()) {
                setSelected(!isSelected());
                event.consume();
            }
        });
        setOnKeyPressed(event -> {
            if (!isDisabled() && (event.getCode() == KeyCode.SPACE || event.getCode() == KeyCode.ENTER)) {
                setSelected(!isSelected());
                event.consume();
            }
        });
        refreshState();
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean selected) {
        this.selected.set(selected);
    }

    private void refreshState() {
        pseudoClassStateChanged(SELECTED, isSelected());
        StackPane.setAlignment(thumb, isSelected() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        setAccessibleText(isSelected() ? "On" : "Off");
    }
}
