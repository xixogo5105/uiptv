package com.uiptv.widget;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static com.uiptv.testsupport.FxTestSupport.waitForFxEvents;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppNotificationCenterTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        initJavaFx();
    }

    @Test
    void createHostStartsCollapsedAndShowInfoDisplaysCard() throws Exception {
        VBox host = runOnFxThread(AppNotificationCenter::createHost);
        assertFalse(runOnFxThread(host::isVisible));
        assertFalse(runOnFxThread(host::isManaged));

        boolean accepted = runOnFxThread(() -> {
            AppNotificationCenter.install(host);
            return AppNotificationCenter.showInfo("Loaded", null);
        });
        waitForFxEvents();

        assertTrue(accepted);
        assertTrue(runOnFxThread(host::isVisible));
        assertTrue(runOnFxThread(host::isManaged));
        assertEquals(1, runOnFxThread(() -> host.getChildren().size()));
        assertTrue(runOnFxThread(() -> host.getChildren().get(0).getStyleClass().contains("info")));
        assertTrue(runOnFxThread(() -> labelsUnder(host).contains("Loaded")));
    }

    @Test
    void showErrorAddsErrorCardAndKeepsMaximumVisibleNotifications() throws Exception {
        VBox host = runOnFxThread(AppNotificationCenter::createHost);

        runOnFxThread(() -> {
            AppNotificationCenter.install(host);
            AppNotificationCenter.showInfo("One", null);
            AppNotificationCenter.showInfo("Two", null);
            AppNotificationCenter.showInfo("Three", null);
            AppNotificationCenter.showError("Four", null);
            return null;
        });
        waitForFxEvents();

        assertEquals(3, runOnFxThread(() -> host.getChildren().size()));
        assertTrue(runOnFxThread(() -> host.getChildren().stream()
                .map(Node::getStyleClass)
                .anyMatch(styles -> styles.contains("error"))));
        assertTrue(runOnFxThread(() -> labelsUnder(host).contains("Four")));
    }

    private static List<String> labelsUnder(Node root) {
        if (root instanceof Label label) {
            return List.of(label.getText());
        }
        if (root instanceof javafx.scene.Parent parent) {
            return parent.getChildrenUnmodifiable().stream()
                    .flatMap(child -> labelsUnder(child).stream())
                    .toList();
        }
        if (root instanceof StackPane stackPane) {
            return stackPane.getChildren().stream()
                    .flatMap(child -> labelsUnder(child).stream())
                    .toList();
        }
        return List.of();
    }
}
