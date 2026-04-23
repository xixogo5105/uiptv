package com.uiptv.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtilityCoverageTest {

    @Test
    void appLog_notifiesHealthyListenersAndIgnoresFailingOnes() {
        AtomicInteger calls = new AtomicInteger();
        Consumer<String> good = msg -> calls.incrementAndGet();
        Consumer<String> bad = msg -> { throw new IllegalStateException("boom"); };

        AppLog.registerListener(good);
        AppLog.registerListener(bad);
        AppLog.addInfoLog(UtilityCoverageTest.class, "test-entry");
        AppLog.unregisterListener(good);
        AppLog.unregisterListener(bad);
        AppLog.unregisterListener(null);

        assertEquals(1, calls.get());
    }

    @Test
    void platform_resolvesPathsAndToleratesCommandFailure() {
        assertFalse(Platform.getUserHomeDirPath().isBlank());
        assertTrue(Platform.getWebServerRootPath().endsWith("/web") || Platform.getWebServerRootPath().endsWith("\\web"));
        Platform.executeCommand("definitely-missing-binary", "--version");
    }
}
