package com.uiptv.util;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AppLog {
    private static final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private static final int MAX_LOG_LENGTH = 4000;

    private AppLog() {
    }

    public static void addInfoLog(Class<?> logSource, String log) {
        logWithLevel(logSource, log, LogLevel.INFO);
    }

    public static void addWarningLog(Class<?> logSource, String log) {
        logWithLevel(logSource, log, LogLevel.WARNING);
    }

    public static void addErrorLog(Class<?> logSource, String log) {
        logWithLevel(logSource, log, LogLevel.ERROR);
    }

    public static void addLog(Class<?> logSource, String log) {
        addInfoLog(logSource, log);
    }

    private static void logWithLevel(Class<?> logSource, String log, LogLevel level) {
        if (logSource == null) {
            throw new IllegalArgumentException("logSource cannot be null");
        }
        String safeLog = sanitizeLogMessage(log);
        Logger logger = LoggerFactory.getLogger(logSource);
        if (level == LogLevel.ERROR) {
            logger.error(safeLog);
        } else if (level == LogLevel.WARNING) {
            logger.warn(safeLog);
        } else {
            logger.info(safeLog);
        }
        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(safeLog);
            } catch (Exception _) {
                // Keep logging resilient if a listener fails.
            }
        }
    }

    public static String sanitizeValue(String value) {
        return sanitizeLogMessage(value);
    }

    private static String sanitizeLogMessage(String message) {
        if (message == null) {
            return "";
        }
        StringBuilder normalizedBuilder = new StringBuilder(message.length());
        for (int i = 0; i < message.length(); i++) {
            char current = message.charAt(i);
            normalizedBuilder.append(Character.isISOControl(current) ? ' ' : current);
        }
        String normalized = normalizedBuilder.toString().trim();
        if (normalized.length() <= MAX_LOG_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_LOG_LENGTH) + "...";
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

    private enum LogLevel {
        INFO,
        WARNING,
        ERROR
    }
}
