package com.uiptv.shared.platform;

public interface LoggerPort {
    void debug(String message);

    void info(String message);

    void warn(String message);

    void error(String message, Throwable cause);

    static LoggerPort noop() {
        return new NoopLoggerPort();
    }

    final class NoopLoggerPort implements LoggerPort {
        private NoopLoggerPort() {
        }

        @Override
        public void debug(String message) {
            // This logger intentionally discards debug messages.
        }

        @Override
        public void info(String message) {
            // This logger intentionally discards informational messages.
        }

        @Override
        public void warn(String message) {
            // This logger intentionally discards warning messages.
        }

        @Override
        public void error(String message, Throwable cause) {
            // This logger intentionally discards error messages and causes.
        }
    }
}
