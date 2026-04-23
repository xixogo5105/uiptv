package com.uiptv.ui;

import com.uiptv.util.XtremeCredentialsJson;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class XtremeCredentialsManagementPopupTest {
    private static final AtomicBoolean FX_STARTED = new AtomicBoolean(false);

    @BeforeAll
    static void initJavaFx() throws Exception {
        Assumptions.assumeTrue(!isHeadlessEnvironment(), "Headless environment cannot initialize JavaFX");
        if (FX_STARTED.compareAndSet(false, true)) {
            CountDownLatch latch = new CountDownLatch(1);
            try {
                Platform.startup(latch::countDown);
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("JavaFX platform failed to start");
                }
            } catch (IllegalStateException e) {
                if (e.getMessage().contains("Toolkit already initialized")) {
                    // Ignore if already initialized, likely by another test or setup.
                    // No need to wait on the latch as this startup call didn't initiate it.
                    System.err.println("JavaFX Toolkit already initialized, proceeding with tests.");
                } else {
                    throw e; // Re-throw other IllegalStateExceptions
                }
            }
        }
    }

    private static boolean isHeadlessEnvironment() {
        if (GraphicsEnvironment.isHeadless()) {
            return true;
        }
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("linux")) {
            String display = System.getenv("DISPLAY");
            String wayland = System.getenv("WAYLAND_DISPLAY");
            return (display == null || display.isBlank()) && (wayland == null || wayland.isBlank());
        }
        return false;
    }

    @Test
    void addUpdateAndSetDefaultCredential() throws Exception {
        XtremeCredentialsManagementPopup popup = runOnFxThread(() -> new XtremeCredentialsManagementPopup(
                null,
                List.of(
                        new XtremeCredentialsJson.Entry("alpha", "passA", true),
                        new XtremeCredentialsJson.Entry("beta", "passB", false)
                ),
                "alpha",
                (entries, def) -> {
                }
        ));

        runOnFxThread(() -> {
            popup.setInputForTest("gamma", "passC");
            popup.addCredentialForTest();
            popup.selectIndexForTest(1);
            popup.setInputForTest("beta-updated", "passB2");
            popup.updateSelectedForTest();
            popup.setDefaultForTest();
            return null;
        });

        List<XtremeCredentialsJson.Entry> entries = runOnFxThread(popup::entriesForTest);
        assertEquals(3, entries.size());
        XtremeCredentialsJson.Entry defaultEntry = XtremeCredentialsJson.resolveDefault(entries);
        assertNotNull(defaultEntry);
        assertEquals("beta-updated", defaultEntry.username());
        assertEquals("passB2", defaultEntry.password());
    }

    @Test
    void bulkDeleteKeepsOneAndResetsDefault() throws Exception {
        XtremeCredentialsManagementPopup popup = runOnFxThread(() -> new XtremeCredentialsManagementPopup(
                null,
                List.of(
                        new XtremeCredentialsJson.Entry("alpha", "passA", true),
                        new XtremeCredentialsJson.Entry("beta", "passB", false),
                        new XtremeCredentialsJson.Entry("gamma", "passC", false)
                ),
                "alpha",
                (entries, def) -> {
                }
        ));

        runOnFxThread(() -> {
            popup.setItemSelectedForTest(0, true);
            popup.setItemSelectedForTest(1, true);
            popup.removeSelectedForTest();
            return null;
        });

        int count = runOnFxThread(popup::itemCountForTest);
        assertEquals(1, count);
        String defaultUsername = runOnFxThread(popup::defaultUsernameForTest);
        assertEquals("gamma", defaultUsername);
        boolean deleteDisabled = runOnFxThread(popup::isDeleteDisabledForTest);
        boolean defaultDisabled = runOnFxThread(popup::isDefaultDisabledForTest);
        assertEquals(true, deleteDisabled);
        assertEquals(true, defaultDisabled);
    }

    private static <T> T runOnFxThread(FxCallable<T> task) throws Exception {
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
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for FX task");
        }
        if (failure.get() != null) {
            throw new RuntimeException(failure.get());
        }
        return result.get();
    }

    @FunctionalInterface
    private interface FxCallable<T> {
        T call() throws Exception;
    }
}
