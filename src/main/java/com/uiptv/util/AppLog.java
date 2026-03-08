package com.uiptv.util;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AppLog {
    private static final Logger log = LoggerFactory.getLogger(AppLog.class);
    private static final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    private AppLog() {
    }

    public static void addLog(String log) {
        AppLog.log.info(log);
        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(log);
            } catch (Exception _) {
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
