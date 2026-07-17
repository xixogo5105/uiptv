package com.uiptv.widget;

import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
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
    void constructorConfiguresHiddenNavigationHostDefaults() throws Exception {
        AppNavigationPane pane = runOnFxThread(AppNavigationPane::new);
        waitForFxEvents();

        assertTrue(runOnFxThread(() -> pane.getStyleClass().contains("uiptv-app-tabs")));
        assertEquals(Side.TOP, runOnFxThread(pane::getSide));
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

}
