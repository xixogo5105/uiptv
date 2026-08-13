package com.uiptv.player;

import com.uiptv.util.AppLog;
import com.uiptv.widget.AppNotificationCenter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static com.uiptv.testsupport.FxTestSupport.waitForFxEvents;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaPlayerFactoryVlcFailureTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        initJavaFx();
    }

    @Test
    void handleVlcInitFailure_showsNotificationAndLogsStacktrace() throws Exception {
        javafx.scene.layout.VBox host = runOnFxThread(() -> AppNotificationCenter.createHost());
        runOnFxThread(() -> { AppNotificationCenter.install(host); return null; });

        CopyOnWriteArrayList<String> captured = new CopyOnWriteArrayList<>();
        java.util.function.Consumer<String> listener = captured::add;
        AppLog.registerListener(listener);

        MediaPlayerFactory.handleVlcInitFailure(new UnsatisfiedLinkError("libvlc.so missing"));
        waitForFxEvents();

        // Notification should contain the user-facing message
        boolean notificationShown = runOnFxThread(() -> labelsUnder(host).stream().anyMatch(s -> s.contains("VLC native libraries")));
        assertTrue(notificationShown, "Expected user-facing VLC missing notification to be shown");

        // Logs should include the UnsatisfiedLinkError stacktrace text
        assertTrue(captured.stream().anyMatch(s -> s.contains("UnsatisfiedLinkError") || s.contains("libvlc.so missing")), "Expected logs to contain UnsatisfiedLinkError stacktrace");

        AppLog.unregisterListener(listener);
    }

    private static List<String> labelsUnder(javafx.scene.Node root) {
        if (root instanceof javafx.scene.control.Label l) {
            return List.of(l.getText());
        }
        if (root instanceof javafx.scene.Parent parent) {
            return parent.getChildrenUnmodifiable().stream()
                    .flatMap(child -> labelsUnder(child).stream())
                    .toList();
        }
        if (root instanceof javafx.scene.layout.StackPane stackPane) {
            return stackPane.getChildren().stream()
                    .flatMap(child -> labelsUnder(child).stream())
                    .toList();
        }
        return List.of();
    }
}
