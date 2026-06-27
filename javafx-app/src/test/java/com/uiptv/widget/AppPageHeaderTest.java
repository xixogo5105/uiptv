package com.uiptv.widget;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppPageHeaderTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        initJavaFx();
    }

    @Test
    void wideLayoutShowsNavigationAndActions() throws Exception {
        Button action = runOnFxThread(() -> new Button("Reload"));
        AppPageHeader header = runOnFxThread(() -> new AppPageHeader("Channels", action));

        runOnFxThread(() -> {
            header.resize(1200, 80);
            header.layout();
            return null;
        });

        assertEquals(2, runOnFxThread(() -> header.getChildren().size()));
        HBox wideRow = runOnFxThread(() -> (HBox) header.getChildren().get(1));
        assertTrue(runOnFxThread(() -> containsNode(wideRow, action)));
    }

    @Test
    void compactLayoutShowsNavigationAndActions() throws Exception {
        Button action = runOnFxThread(() -> new Button("Back"));
        AppPageHeader header = runOnFxThread(() -> new AppPageHeader("Episodes", action));

        runOnFxThread(() -> {
            header.resize(800, 80);
            header.layout();
            return null;
        });

        assertEquals(2, runOnFxThread(() -> header.getChildren().size()));
        assertInstanceOf(HBox.class, runOnFxThread(() -> header.getChildren().get(0)));
        assertInstanceOf(HBox.class, runOnFxThread(() -> header.getChildren().get(1)));
        HBox wideRow = runOnFxThread(() -> (HBox) header.getChildren().get(1));
        assertTrue(runOnFxThread(() -> containsNode(wideRow, action)));
    }

    @Test
    void titleCanBeUpdatedAndNullTitleIsSafe() throws Exception {
        AppPageHeader header = runOnFxThread(() -> new AppPageHeader(null, new Button("Action")));

        Label title = runOnFxThread(() -> findTitle(header));
        assertEquals("", runOnFxThread(title::getText));
        assertTrue(runOnFxThread(() -> !title.isVisible()));
        assertTrue(runOnFxThread(() -> !title.isManaged()));

        runOnFxThread(() -> {
            header.setTitle("Settings");
            return null;
        });

        assertEquals("Settings", runOnFxThread(title::getText));
        assertTrue(runOnFxThread(() -> !title.isVisible()));
        assertTrue(runOnFxThread(() -> !title.isManaged()));
    }

    @Test
    void headerTitleIsHiddenByDefaultAndCanBeShownExplicitly() throws Exception {
        AppPageHeader header = runOnFxThread(() -> new AppPageHeader("Settings", new Button("Action")));

        Label title = runOnFxThread(() -> findTitle(header));
        assertEquals("Settings", runOnFxThread(title::getText));
        assertTrue(runOnFxThread(() -> !title.isVisible()));
        assertTrue(runOnFxThread(() -> !title.isManaged()));

        runOnFxThread(() -> {
            header.setHeaderTitleVisible(true);
            return null;
        });

        assertTrue(runOnFxThread(title::isVisible));
        assertTrue(runOnFxThread(title::isManaged));
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

    private static boolean containsNode(Node root, Node target) {
        if (root == target) {
            return true;
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (containsNode(child, target)) {
                    return true;
                }
            }
        }
        return false;
    }
}
