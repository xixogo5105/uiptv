package com.uiptv.testsupport;

import javafx.application.Platform;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class FxTestSupport {
    private static final AtomicBoolean FX_STARTED = new AtomicBoolean(false);
    private static final long FX_WAIT_TIMEOUT_SECONDS = 3L;

    private FxTestSupport() {
    }

    public static void initJavaFx() throws Exception {
        if (FX_STARTED.compareAndSet(false, true)) {
            CountDownLatch latch = new CountDownLatch(1);
            try {
                Platform.startup(latch::countDown);
                if (!latch.await(FX_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("JavaFX platform failed to start");
                }
            } catch (IllegalStateException e) {
                if (!e.getMessage().contains("Toolkit already initialized")) {
                    throw e;
                }
            }
        }
    }

    public static <T> T runOnFxThread(FxCallable<T> task) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return task.call();
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(task.call());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(FX_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for FX task");
        }
        if (failure.get() != null) {
            throw new RuntimeException(failure.get());
        }
        return result.get();
    }

    public static void waitForFxEvents() throws Exception {
        runOnFxThread(() -> null);
    }

    @FunctionalInterface
    public interface FxCallable<T> {
        T call() throws Exception;
    }
}
