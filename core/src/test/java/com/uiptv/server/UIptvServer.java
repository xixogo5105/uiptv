package com.uiptv.server;

import java.io.IOException;

public final class UIptvServer {
    public static boolean running;
    public static boolean ensureStartedResult = true;
    public static IOException startFailure;
    public static IOException ensureFailure;
    public static RuntimeException runtimeFailure;
    public static Error errorFailure;
    public static int startCalls;
    public static int ensureStartedCalls;
    public static int stopCalls;

    private UIptvServer() {
    }

    public static void reset() {
        running = false;
        ensureStartedResult = true;
        startFailure = null;
        ensureFailure = null;
        runtimeFailure = null;
        errorFailure = null;
        startCalls = 0;
        ensureStartedCalls = 0;
        stopCalls = 0;
    }

    public static void start() throws IOException {
        startCalls++;
        throwIfConfigured(startFailure);
        running = true;
    }

    public static boolean ensureStarted() throws IOException {
        ensureStartedCalls++;
        throwIfConfigured(ensureFailure);
        running = ensureStartedResult;
        return ensureStartedResult;
    }

    public static boolean isRunning() {
        throwIfUnchecked();
        return running;
    }

    public static void stop() {
        stopCalls++;
        throwIfUnchecked();
        running = false;
    }

    private static void throwIfConfigured(IOException failure) throws IOException {
        if (failure != null) {
            throw failure;
        }
        throwIfUnchecked();
    }

    private static void throwIfUnchecked() {
        if (runtimeFailure != null) {
            throw runtimeFailure;
        }
        if (errorFailure != null) {
            throw errorFailure;
        }
    }
}
