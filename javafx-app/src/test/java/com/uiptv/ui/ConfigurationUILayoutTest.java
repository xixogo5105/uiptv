package com.uiptv.ui;

import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static com.uiptv.testsupport.FxTestSupport.waitForFxEvents;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals(136, heights.get(1), 0.01);
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
}
