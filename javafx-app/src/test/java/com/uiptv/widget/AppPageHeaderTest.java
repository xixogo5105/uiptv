package com.uiptv.widget;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
    void wideLayoutPlacesSearchBetweenNavigationAndActions() throws Exception {
        TextField search = runOnFxThread(TextField::new);
        Button action = runOnFxThread(() -> new Button("Reload"));
        AppPageHeader header = runOnFxThread(() -> new AppPageHeader("Channels", search, List.of(action)));

        runOnFxThread(() -> {
            header.resize(1200, 80);
            header.layout();
            return null;
        });

        assertEquals(1, runOnFxThread(() -> header.getChildren().size()));
        HBox wideRow = runOnFxThread(() -> (HBox) header.getChildren().get(0));
        assertTrue(runOnFxThread(() -> wideRow.getChildren().contains(search)));
        assertTrue(runOnFxThread(() -> search.getStyleClass().contains("uiptv-page-search-field")));
        assertEquals(420.0, runOnFxThread(search::getMaxWidth));
        assertSame(search, runOnFxThread(header::getSearchField));
    }

    @Test
    void compactLayoutStacksTitleActionsAndSearchThenCanReturnWide() throws Exception {
        TextField search = runOnFxThread(TextField::new);
        Button action = runOnFxThread(() -> new Button("Back"));
        AppPageHeader header = runOnFxThread(() -> new AppPageHeader("Episodes", search, List.of(action)));

        runOnFxThread(() -> {
            header.resize(800, 80);
            header.layout();
            return null;
        });

        assertEquals(3, runOnFxThread(() -> header.getChildren().size()));
        assertInstanceOf(HBox.class, runOnFxThread(() -> header.getChildren().get(0)));
        assertInstanceOf(HBox.class, runOnFxThread(() -> header.getChildren().get(1)));
        assertSame(search, runOnFxThread(() -> header.getChildren().get(2)));
        assertTrue(runOnFxThread(() -> containsNode(header.getChildren().get(1), action)));
        assertEquals(0.0, runOnFxThread(search::getMinWidth));
        assertEquals(Double.MAX_VALUE, runOnFxThread(search::getMaxWidth));

        runOnFxThread(() -> {
            header.resize(1300, 80);
            header.layout();
            return null;
        });

        assertEquals(1, runOnFxThread(() -> header.getChildren().size()));
        assertInstanceOf(HBox.class, runOnFxThread(() -> header.getChildren().get(0)));
        assertEquals(140.0, runOnFxThread(search::getMinWidth));
        assertEquals(420.0, runOnFxThread(search::getMaxWidth));
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
    void searchFieldClearsOnClickAndPreservesExistingMouseHandler() throws Exception {
        AtomicBoolean existingHandlerCalled = new AtomicBoolean(false);
        TextField search = runOnFxThread(() -> {
            TextField field = new TextField("previous query");
            field.setOnMousePressed(_ -> existingHandlerCalled.set(true));
            return field;
        });
        runOnFxThread(() -> new AppPageHeader("Watching", search, List.of(new Button("Action"))));

        runOnFxThread(() -> {
            search.getOnMousePressed().handle(primaryMousePressedEvent());
            return null;
        });

        assertTrue(existingHandlerCalled.get());
        assertEquals("", runOnFxThread(search::getText));
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

    private static MouseEvent primaryMousePressedEvent() {
        return new MouseEvent(
                MouseEvent.MOUSE_PRESSED,
                0,
                0,
                0,
                0,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                null
        );
    }
}
