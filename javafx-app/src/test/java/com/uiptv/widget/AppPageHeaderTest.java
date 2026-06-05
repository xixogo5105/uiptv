package com.uiptv.widget;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppPageHeaderTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        initJavaFx();
    }

    @Test
    void wideLayoutCentersSearchAndKeepsActionsAtRight() throws Exception {
        TextField search = runOnFxThread(TextField::new);
        Button action = runOnFxThread(() -> new Button("Reload"));
        AppPageHeader header = runOnFxThread(() -> new AppPageHeader("Channels", search, List.of(action)));

        runOnFxThread(() -> {
            header.resize(1200, 80);
            header.layout();
            return null;
        });

        assertEquals(1, runOnFxThread(() -> header.getChildren().size()));
        StackPane wideRow = runOnFxThread(() -> (StackPane) header.getChildren().get(0));
        assertTrue(runOnFxThread(() -> wideRow.getChildren().contains(search)));
        assertTrue(runOnFxThread(() -> search.getStyleClass().contains("uiptv-page-search-field")));
        assertEquals(560.0, runOnFxThread(search::getMaxWidth));
        assertSame(search, runOnFxThread(header::getSearchField));
    }

    @Test
    void compactLayoutStacksSearchBelowTitleRowAndCanReturnWide() throws Exception {
        TextField search = runOnFxThread(TextField::new);
        Button action = runOnFxThread(() -> new Button("Back"));
        AppPageHeader header = runOnFxThread(() -> new AppPageHeader("Episodes", search, List.of(action)));

        runOnFxThread(() -> {
            header.resize(800, 80);
            header.layout();
            return null;
        });

        assertEquals(2, runOnFxThread(() -> header.getChildren().size()));
        assertInstanceOf(HBox.class, runOnFxThread(() -> header.getChildren().get(0)));
        assertSame(search, runOnFxThread(() -> header.getChildren().get(1)));
        assertEquals(Double.MAX_VALUE, runOnFxThread(search::getMaxWidth));

        runOnFxThread(() -> {
            header.resize(1100, 80);
            header.layout();
            return null;
        });

        assertEquals(1, runOnFxThread(() -> header.getChildren().size()));
        assertInstanceOf(StackPane.class, runOnFxThread(() -> header.getChildren().get(0)));
    }

    @Test
    void titleCanBeUpdatedAndNullTitleIsSafe() throws Exception {
        AppPageHeader header = runOnFxThread(() -> new AppPageHeader(null, new Button("Action")));

        assertEquals("", runOnFxThread(() -> findTitle(header).getText()));

        runOnFxThread(() -> {
            header.setTitle("Settings");
            return null;
        });

        assertEquals("Settings", runOnFxThread(() -> findTitle(header).getText()));
    }

    private static Label findTitle(Node root) {
        if (root instanceof Label label && label.getStyleClass().contains("uiptv-page-title")) {
            return label;
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Label found = findTitle(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
