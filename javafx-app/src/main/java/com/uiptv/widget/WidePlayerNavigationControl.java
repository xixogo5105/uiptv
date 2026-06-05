package com.uiptv.widget;

import javafx.application.Platform;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class WidePlayerNavigationControl {
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static boolean available;
    private static boolean collapsed;
    private static Runnable toggleHandler;

    private WidePlayerNavigationControl() {
    }

    public static boolean isAvailable() {
        return available;
    }

    public static boolean isCollapsed() {
        return collapsed;
    }

    public static void configure(boolean nextAvailable, boolean nextCollapsed, Runnable nextToggleHandler) {
        boolean changed = available != nextAvailable || collapsed != nextCollapsed || toggleHandler != nextToggleHandler;
        available = nextAvailable;
        collapsed = nextCollapsed;
        toggleHandler = nextToggleHandler;
        if (changed) {
            notifyListeners();
        }
    }

    public static void reset(Runnable ownerToggleHandler) {
        if (toggleHandler == ownerToggleHandler) {
            configure(false, false, null);
        }
    }

    public static void toggle() {
        Runnable handler = toggleHandler;
        if (available && handler != null) {
            handler.run();
        }
    }

    public static void addListener(Runnable listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }

    private static void notifyListeners() {
        Runnable notifier = () -> LISTENERS.forEach(Runnable::run);
        if (Platform.isFxApplicationThread()) {
            notifier.run();
        } else {
            Platform.runLater(notifier);
        }
    }
}
