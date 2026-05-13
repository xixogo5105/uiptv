package com.uiptv.shared.persistence;

public class DatabasePortException extends Exception {
    public DatabasePortException(String message) {
        super(message);
    }

    public DatabasePortException(String message, Throwable cause) {
        super(message, cause);
    }
}
