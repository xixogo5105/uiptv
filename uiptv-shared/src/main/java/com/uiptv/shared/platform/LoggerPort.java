package com.uiptv.shared.platform;

public interface LoggerPort {
    void debug(String message);

    void info(String message);

    void warn(String message);

    void error(String message, Throwable cause);

    static LoggerPort noop() {
        return NoopLoggerPort.INSTANCE;
    }

    final class NoopLoggerPort implements LoggerPort {
        private static final NoopLoggerPort INSTANCE = new NoopLoggerPort();

        private NoopLoggerPort() {
        }

        @Override
        public void debug(String message) {
        }

        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void error(String message, Throwable cause) {
        }
    }
}
