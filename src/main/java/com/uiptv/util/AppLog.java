package com.uiptv.util;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class AppLog {
    private static final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    private AppLog() {
    }

    public static void addLog(String log) {
        System.out.println(log);
        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(log);
            } catch (Exception ignored) {
                // Keep logging resilient if a listener fails.
            }
        }
    }

    public static void registerListener(Consumer<String> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public static void unregisterListener(Consumer<String> listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }
}
