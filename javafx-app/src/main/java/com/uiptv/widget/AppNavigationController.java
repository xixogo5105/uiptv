package com.uiptv.widget;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.EnumMap;
import java.util.Map;

public final class AppNavigationController {
    public enum Target {
        BOOKMARKS,
        ACCOUNTS,
        WATCHING_NOW,
        SETTINGS,
        IMPORT,
        LOGS
    }

    private static final Map<Target, Runnable> ACTIONS = new EnumMap<>(Target.class);
    private static final ObjectProperty<Target> CURRENT_TARGET = new SimpleObjectProperty<>(Target.BOOKMARKS);

    private AppNavigationController() {
    }

    public static void configure(Map<Target, Runnable> actions, Target initialTarget) {
        ACTIONS.clear();
        if (actions != null) {
            ACTIONS.putAll(actions);
        }
        setCurrentTarget(initialTarget);
    }

    public static void navigate(Target target) {
        Runnable action = ACTIONS.get(target);
        if (action != null) {
            action.run();
        }
    }

    public static void setCurrentTarget(Target target) {
        if (target != null) {
            CURRENT_TARGET.set(target);
        }
    }

    public static Target currentTarget() {
        return CURRENT_TARGET.get();
    }

    public static ReadOnlyObjectProperty<Target> currentTargetProperty() {
        return CURRENT_TARGET;
    }

    public static void reset() {
        ACTIONS.clear();
        CURRENT_TARGET.set(Target.BOOKMARKS);
    }
}
