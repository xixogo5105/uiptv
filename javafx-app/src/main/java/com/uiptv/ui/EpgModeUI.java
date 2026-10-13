package com.uiptv.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;

/** Session-wide display mode shared by account and bookmark views. */
public final class EpgModeUI {
    private static final BooleanProperty enabled = new SimpleBooleanProperty(false);
    private EpgModeUI() { }
    public static boolean isEnabled() { return enabled.get(); }
    public static void setEnabled(boolean value) { enabled.set(value); }
    public static void observe(Node owner, Runnable refresh) {
        ChangeListener<Boolean> listener = (_, _, _) -> refresh.run();
        owner.sceneProperty().addListener((_, oldScene, newScene) -> {
            if (oldScene != null) enabled.removeListener(listener);
            if (newScene != null) { enabled.addListener(listener); refresh.run(); }
        });
        if (owner.getScene() != null) enabled.addListener(listener);
    }
}
