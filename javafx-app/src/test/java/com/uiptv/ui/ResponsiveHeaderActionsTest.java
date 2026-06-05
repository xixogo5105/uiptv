package com.uiptv.ui;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponsiveHeaderActionsTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        initJavaFx();
    }

    @Test
    void actionRowSkipsNullActionsAndKeepsButtonLabelsVisible() throws Exception {
        Button reload = runOnFxThread(() -> new Button("Reload from server"));
        Region spacer = runOnFxThread(Region::new);

        FlowPane row = runOnFxThread(() -> ResponsiveHeaderActions.actionRow(reload, null, spacer));

        assertEquals(2, runOnFxThread(() -> row.getChildren().size()));
        assertSame(reload, runOnFxThread(() -> row.getChildren().get(0)));
        assertFalse(runOnFxThread(reload::isWrapText));
        assertEquals(Region.USE_PREF_SIZE, runOnFxThread(reload::getMinWidth));
        assertEquals(Region.USE_PREF_SIZE, runOnFxThread(spacer::getMaxWidth));
    }

    @Test
    void stackedTopBarUsesHeaderAndActionRowWithOptionalStyleClass() throws Exception {
        Label header = runOnFxThread(() -> new Label("Manage tabs"));
        Button close = runOnFxThread(() -> new Button("Close"));

        VBox topBar = runOnFxThread(() -> ResponsiveHeaderActions.stackedTopBar(header, "manage-tabs-topbar", close));

        assertTrue(runOnFxThread(() -> topBar.getStyleClass().contains("manage-tabs-topbar")));
        assertEquals(2, runOnFxThread(() -> topBar.getChildren().size()));
        assertSame(header, runOnFxThread(() -> topBar.getChildren().get(0)));
        assertEquals(Double.MAX_VALUE, runOnFxThread(header::getMaxWidth));
    }

    @Test
    void clearPaneClearsNestedPaneChildren() throws Exception {
        Pane root = runOnFxThread(Pane::new);
        Pane childPane = runOnFxThread(Pane::new);
        Label nestedLabel = runOnFxThread(() -> new Label("Nested"));

        runOnFxThread(() -> {
            childPane.getChildren().add(nestedLabel);
            root.getChildren().add(childPane);
            ResponsiveHeaderActions.clearPane(root);
            return null;
        });

        assertEquals(0, runOnFxThread(() -> root.getChildren().size()));
        assertEquals(0, runOnFxThread(() -> childPane.getChildren().size()));
    }
}
