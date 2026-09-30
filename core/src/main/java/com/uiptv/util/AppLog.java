package com.uiptv.util;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AppLog {
    private static final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private static final int MAX_LOG_LENGTH = 4000;
    private static final String SHOW_LOGS_PROPERTY = "uiptv.showLogs";
    private static final String SHOW_LOGS_ARGUMENT = "show-logs";
    /**
     * slf4j-simple's own configuration key. It is read once, when the first logger is created, and
     * a system property takes precedence over {@code simplelogger.properties}, which is what lets
     * {@link #setTerminalLoggingEnabled(boolean)} raise the level for {@code --show-logs}.
     */
    private static final String SLF4J_SIMPLE_LEVEL_PROPERTY = "org.slf4j.simpleLogger.defaultLogLevel";
    /** Silent enough to hide routine library startup chatter, loud enough to keep real problems. */
    private static final String SLF4J_QUIET_LEVEL = "warn";
    private static final String SLF4J_VERBOSE_LEVEL = "info";
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

    /**
     * Whether the command line asked for verbose logging via {@code --show-logs} (the bare
     * {@code show-logs} spelling is accepted too).
     * <p>
     * Shared so that the packaged entry point and the desktop launcher agree on what the flag
     * means instead of each parsing it separately.
     */
    public static boolean isShowLogsRequested(String[] args) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if (isShowLogsArgument(arg)) {
                return true;
            }
        }
        return false;
    }

    /** The command line with any {@code --show-logs} argument removed, for the remaining parsers. */
    public static String[] removeShowLogsArg(String[] args) {
        if (args == null || args.length == 0) {
            return args;
        }
        return Arrays.stream(args)
                .filter(arg -> !isShowLogsArgument(arg))
                .toArray(String[]::new);
    }

    private static boolean isShowLogsArgument(String arg) {
        if (arg == null) {
            return false;
        }
        String normalized = arg.trim().toLowerCase(Locale.ROOT);
        // The flag has been spelled with zero, one and two leading dashes, so strip whatever is
        // there rather than enumerating the combinations.
        while (normalized.startsWith("-")) {
            normalized = normalized.substring(1);
        }
        return normalized.equals(SHOW_LOGS_ARGUMENT);
    }

    public static void setTerminalLoggingEnabled(boolean enabled) {
        terminalLoggingEnabled = enabled;
        System.setProperty(SHOW_LOGS_PROPERTY, Boolean.toString(enabled));
        // Third-party libraries (Jetty, vlcj, the SQLite driver) log straight to SLF4J and never
        // reach this class, so gating them here is the only way --show-logs can govern them.
        // Without this they printed their startup banner on every launch, because slf4j-simple
        // defaults to INFO.
        setSlf4jDefaultLevel(enabled ? SLF4J_VERBOSE_LEVEL : SLF4J_QUIET_LEVEL);
    }

    /**
     * Sets the default level of the SLF4J simple backend.
     * <p>
     * slf4j-simple resolves its configuration once, when the first logger is created, and prefers
     * this system property over {@code simplelogger.properties}. So this has to be called during
     * startup, before anything obtains a logger, or it has no effect.
     *
     * @param level one of {@code trace}, {@code debug}, {@code info}, {@code warn}, {@code error}
     */
    public static void setSlf4jDefaultLevel(String level) {
        if (level == null || level.isBlank()) {
            return;
        }
        System.setProperty(SLF4J_SIMPLE_LEVEL_PROPERTY, level);
    }

    /** The level currently requested for the SLF4J simple backend. */
    public static String getSlf4jDefaultLevel() {
        return System.getProperty(SLF4J_SIMPLE_LEVEL_PROPERTY, SLF4J_QUIET_LEVEL);
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
