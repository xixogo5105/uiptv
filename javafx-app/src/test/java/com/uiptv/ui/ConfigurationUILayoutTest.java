package com.uiptv.ui;

import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.util.I18n;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static com.uiptv.testsupport.FxTestSupport.waitForFxEvents;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationUILayoutTest extends DbBackedUiTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        FxTestSupport.initJavaFx();
    }

    @Test
    void settingsPillBarReservesRowsFromFiveItemNarrowAllocation() throws Exception {
        List<Double> heights = runOnFxThread(() -> {
            ConfigurationUI ui = new ConfigurationUI(null, null, null);
            Scene scene = new Scene(ui, 520, 720);
            scene.getStylesheets().add(Objects.requireNonNull(
                    ConfigurationUILayoutTest.class.getResource("/application.css")
            ).toExternalForm());
            ui.applyCss();

            Region pillBar = (Region) findByStyle(ui, "uiptv-pill-bar");
            ui.resize(1800, 720);
            ui.layout();
            ui.layout();
            double wideLayoutHeight = pillBar.getHeight();

            ui.resize(520, 720);
            ui.layout();
            ui.layout();
            double narrowLayoutHeight = pillBar.getHeight();

            scene.setRoot(new Pane());
            return List.of(wideLayoutHeight, narrowLayoutHeight);
        });
        waitForFxEvents();

        assertEquals(40, heights.get(0), 0.01);
        assertEquals(44, heights.get(1), 0.01);
    }

    @Test
    void parentalLockAccessIsOnlyRestrictionSwitchInSettingsForm() throws Exception {
        List<Boolean> labelPresence = runOnFxThread(() -> {
            ConfigurationUI ui = new ConfigurationUI(null, null, null);
            Scene scene = new Scene(ui, 900, 720);
            scene.getStylesheets().add(Objects.requireNonNull(
                    ConfigurationUILayoutTest.class.getResource("/application.css")
            ).toExternalForm());
            ui.applyCss();
            ui.layout();

            boolean hasParentalLockAccess = containsLabeledText(ui, I18n.tr("filterLockStateToggleLabel"));
            boolean hasPauseRestrictions = containsLabeledText(ui, "Pause parental lock restrictions");

            scene.setRoot(new Pane());
            return List.of(hasParentalLockAccess, hasPauseRestrictions);
        });
        waitForFxEvents();

        assertTrue(labelPresence.get(0));
        assertFalse(labelPresence.get(1));
    }

    private static Node findByStyle(Node node, String styleClass) {
        if (node.getStyleClass().contains(styleClass)) {
            return node;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node match = findByStyle(child, styleClass);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static boolean containsLabeledText(Node node, String text) {
        if (node instanceof Labeled labeled && text.equals(labeled.getText())) {
            return true;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (containsLabeledText(child, text)) {
                    return true;
                }
            }
        }
        return false;
    }
}
