package com.uiptv.shared.platform;

public class BackgroundJobException extends Exception {
    public BackgroundJobException(String message) {
        super(message);
    }

    public BackgroundJobException(String message, Throwable cause) {
        super(message, cause);
    }
}
