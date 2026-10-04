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

import java.util.ArrayList;
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

    @Test
    void themePillBarKeepsTheLabelOnTheLeftAndTheOptionsOnOneRow() throws Exception {
        List<double[]> metrics = runOnFxThread(() -> {
            ConfigurationUI ui = new ConfigurationUI(null, null, null);
            Scene scene = new Scene(ui, 520, 720);
            scene.getStylesheets().add(Objects.requireNonNull(
                    ConfigurationUILayoutTest.class.getResource("/application.css")
            ).toExternalForm());
            ui.applyCss();
            ui.resize(900, 900);
            ui.layout();
            ui.layout();

            // The theme row is the one whose direct children are a label and a pill bar.
            Node row = null;
            Node label = null;
            Node pill = null;
            List<Node> rows = new ArrayList<>();
            collectRowsWithLabelAndPill(ui, rows);
            for (Node candidate : rows) {
                for (Node child : ((Parent) candidate).getChildrenUnmodifiable()) {
                    if (child instanceof Labeled) {
                        label = child;
                    } else if (child.getStyleClass().contains("uiptv-pill-bar")) {
                        pill = child;
                    }
                }
                if (label != null && pill != null) {
                    row = candidate;
                    break;
                }
            }

            // Measured from a pill so the height assertion is independent of the applied font size.
            double baseFontSize = 13;
            double rowWidth = row == null ? 0 : row.getLayoutBounds().getWidth();
            double labelWidth = label == null ? 0 : label.getLayoutBounds().getWidth();
            double pillHeight = pill instanceof Region r2 ? r2.getHeight() : 0;
            double pillWidth = pill == null ? 0 : pill.getLayoutBounds().getWidth();

            scene.setRoot(new Pane());
            return List.of(new double[]{rowWidth, labelWidth, pillHeight, pillWidth, baseFontSize});
        });
        waitForFxEvents();

        double[] m = metrics.get(0);
        double rowWidth = m[0];
        double labelWidth = m[1];
        double pillHeight = m[2];
        double pillWidth = m[3];
        double scale = m[4] / 13.0;

        // Row and children must be visible with real widths: the earlier percentage-column layout collapsed
        // the label to 1px and the pill bar to 3px, rendering a tall narrow strip instead of the options.
        assertTrue(rowWidth > 300, "Theme row should span the settings card, was " + rowWidth);
        assertTrue(labelWidth > 80, "Theme label should keep a readable width, was " + labelWidth);
        assertTrue(pillWidth > 200, "Theme pill bar should take the remaining width, was " + pillWidth);

        // 35% of the usable width (row width minus the 12px gap), allowing for rounding.
        double usable = rowWidth - 12;
        assertEquals(usable * 0.35, labelWidth, 1.0);

        // One row of options, not a wrapped multi-row block.
        assertEquals(40 * scale, pillHeight, 0.01);
    }

    private static void collectRowsWithLabelAndPill(Node node, List<Node> found) {
        if (node instanceof Parent parent) {
            boolean hasLabel = parent.getChildrenUnmodifiable().stream().anyMatch(c -> c instanceof Labeled);
            boolean hasPill = parent.getChildrenUnmodifiable().stream()
                    .anyMatch(c -> c.getStyleClass().contains("uiptv-pill-bar"));
            if (hasLabel && hasPill) {
                found.add(node);
            }
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectRowsWithLabelAndPill(child, found);
            }
        }
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
