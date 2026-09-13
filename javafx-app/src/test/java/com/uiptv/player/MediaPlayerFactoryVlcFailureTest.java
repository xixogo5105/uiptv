package com.uiptv.player;

import com.uiptv.util.AppLog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.waitForFxEvents;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaPlayerFactoryVlcFailureTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        initJavaFx();
    }

    @Test
    void handleVlcInitFailure_showsNotificationAndLogsStacktrace() throws Exception {
        CopyOnWriteArrayList<String> captured = new CopyOnWriteArrayList<>();
        java.util.function.Consumer<String> listener = captured::add;
        AppLog.registerListener(listener);

        MediaPlayerFactory.handleVlcInitFailure(new UnsatisfiedLinkError("libvlc.so missing"));
        waitForFxEvents();

        // Logs should include the UnsatisfiedLinkError stacktrace text
        assertTrue(captured.stream().anyMatch(s -> s.contains("UnsatisfiedLinkError") || s.contains("libvlc.so missing")), "Expected logs to contain UnsatisfiedLinkError stacktrace");

        AppLog.unregisterListener(listener);
    }
}
