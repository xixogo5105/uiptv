package com.uiptv.util;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AppLog {
    private static final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private static final int MAX_LOG_LENGTH = 4000;
    private static final String SHOW_LOGS_PROPERTY = "uiptv.showLogs";
    private static volatile boolean terminalLoggingEnabled =
            Boolean.parseBoolean(System.getProperty(SHOW_LOGS_PROPERTY, "false"));

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

    public static void addErrorLog(Class<?> logSource, String log, Throwable throwable) {
        logWithLevel(logSource, log, LogLevel.ERROR, throwable);
    }

    public static void addLog(Class<?> logSource, String log) {
        addInfoLog(logSource, log);
    }

    private static void logWithLevel(Class<?> logSource, String log, LogLevel level) {
        logWithLevel(logSource, log, level, null);
    }

    private static void logWithLevel(Class<?> logSource, String log, LogLevel level, Throwable throwable) {
        if (logSource == null) {
            throw new IllegalArgumentException("logSource cannot be null");
        }
        String safeLog = sanitizeLogMessage(log);
        if (isTerminalLoggingEnabled()) {
            Logger logger = LoggerFactory.getLogger(logSource);
            switch (level) {
                case ERROR -> {
                    if (throwable == null) {
                        logger.error(safeLog);
                    } else {
                        logger.error(safeLog, throwable);
                    }
                }
                case WARNING -> logger.warn(safeLog);
                case INFO -> logger.info(safeLog);
            }
        }
        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(safeLog);
            } catch (Exception e) {
                // Keep logging resilient if a listener fails.
                Logger logger = LoggerFactory.getLogger(AppLog.class);
                logger.warn("Log listener failed: {}", e.toString());
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

    public static void setTerminalLoggingEnabled(boolean enabled) {
        terminalLoggingEnabled = enabled;
        System.setProperty(SHOW_LOGS_PROPERTY, Boolean.toString(enabled));
    }

    public static boolean isTerminalLoggingEnabled() {
        return terminalLoggingEnabled || Boolean.parseBoolean(System.getProperty(SHOW_LOGS_PROPERTY, "false"));
    }

    private enum LogLevel {
        INFO,
        WARNING,
        ERROR
    }
}
