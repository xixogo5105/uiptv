package com.uiptv.widget;

import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static com.uiptv.testsupport.FxTestSupport.waitForFxEvents;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppNavigationPaneTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        initJavaFx();
    }

    @Test
    void constructorConfiguresNavigationRailDefaults() throws Exception {
        AppNavigationPane pane = runOnFxThread(AppNavigationPane::new);
        waitForFxEvents();

        assertTrue(runOnFxThread(() -> pane.getStyleClass().contains("uiptv-app-tabs")));
        assertEquals(Side.LEFT, runOnFxThread(pane::getSide));
        assertFalse(runOnFxThread(pane::isRotateGraphic));
        assertEquals(javafx.scene.control.TabPane.TabClosingPolicy.UNAVAILABLE,
                runOnFxThread(pane::getTabClosingPolicy));
    }

    @Test
    void createTabWrapsContentAndUsesImmediateTooltip() throws Exception {
        AppNavigationPane pane = runOnFxThread(AppNavigationPane::new);
        Label content = runOnFxThread(() -> new Label("Settings content"));

        Tab tab = runOnFxThread(() -> pane.createTab("Settings", AppNavigationPane.ICON_SETTINGS, content));

        assertFalse(runOnFxThread(tab::isClosable));
        assertTrue(runOnFxThread(() -> tab.getStyleClass().contains("uiptv-nav-tab")));
        assertEquals("Settings", runOnFxThread(() -> tab.getTooltip().getText()));
        assertEquals(Duration.ZERO, runOnFxThread(() -> tab.getTooltip().getShowDelay()));

        StackPane wrapper = runOnFxThread(() -> (StackPane) tab.getContent());
        assertTrue(runOnFxThread(() -> wrapper.getStyleClass().contains("uiptv-nav-content")));
        assertSame(content, runOnFxThread(() -> wrapper.getChildren().get(0)));
        assertEquals(Double.MAX_VALUE, runOnFxThread(content::getMaxWidth));
        assertEquals(Double.MAX_VALUE, runOnFxThread(content::getMaxHeight));

        Node graphic = runOnFxThread(tab::getGraphic);
        assertInstanceOf(StackPane.class, graphic);
        assertEquals("Settings", runOnFxThread(() -> findAccessibleText(graphic)));
        assertInstanceOf(SVGPath.class, runOnFxThread(() -> findDescendant(graphic, SVGPath.class)));
    }

    @Test
    void wrapContentHandlesNonRegionNodesAndTooltipDurationsAreStable() throws Exception {
        SVGPath content = runOnFxThread(SVGPath::new);

        StackPane wrapper = runOnFxThread(() -> (StackPane) AppNavigationPane.wrapContent(content));
        Tooltip tooltip = runOnFxThread(() -> AppNavigationPane.createImmediateTooltip("Favorites"));

        assertSame(content, runOnFxThread(() -> wrapper.getChildren().get(0)));
        assertTrue(runOnFxThread(() -> wrapper.getStyleClass().contains("uiptv-nav-content")));
        assertEquals(Duration.ZERO, runOnFxThread(tooltip::getShowDelay));
        assertEquals(Duration.millis(80), runOnFxThread(tooltip::getHideDelay));
        assertEquals(Duration.seconds(6), runOnFxThread(tooltip::getShowDuration));
    }

    @Test
    void sceneCursorChangesOnlyWhenPointerIsOverNavigationRail() throws Exception {
        AppNavigationPane pane = runOnFxThread(() -> {
            AppNavigationPane navPane = new AppNavigationPane();
            navPane.getTabs().add(navPane.createTab("Settings", AppNavigationPane.ICON_SETTINGS, new Label("Settings")));
            new Scene(navPane, 320, 240);
            navPane.resize(320, 240);
            navPane.layout();
            return navPane;
        });
        waitForFxEvents();

        runOnFxThread(() -> {
            javafx.event.Event.fireEvent(pane, mouseEvent(MouseEvent.MOUSE_MOVED, pane, 16, 20));
            return null;
        });
        assertEquals(Cursor.HAND, runOnFxThread(() -> pane.getScene().getCursor()));

        runOnFxThread(() -> {
            javafx.event.Event.fireEvent(pane, mouseEvent(MouseEvent.MOUSE_MOVED, pane, 120, 20));
            return null;
        });
        assertEquals(null, runOnFxThread(() -> pane.getScene().getCursor()));
    }

    private static <T extends Node> T findDescendant(Node root, Class<T> type) {
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                T found = findDescendant(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String findAccessibleText(Node root) {
        if (root.getAccessibleText() != null) {
            return root.getAccessibleText();
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                String accessibleText = findAccessibleText(child);
                if (accessibleText != null) {
                    return accessibleText;
                }
            }
        }
        return null;
    }

    private static MouseEvent mouseEvent(javafx.event.EventType<MouseEvent> eventType,
                                         Node target,
                                         double sceneX,
                                         double sceneY) {
        return new MouseEvent(
                eventType,
                sceneX,
                sceneY,
                sceneX,
                sceneY,
                MouseButton.NONE,
                0,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                new PickResult(target, sceneX, sceneY)
        );
    }
}
