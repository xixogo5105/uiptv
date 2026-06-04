package com.uiptv.widget;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.geometry.Bounds;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.util.LinkedHashSet;
import java.util.Set;

public class AppNavigationPane extends TabPane {
    public static final String ICON_SETTINGS = "M19.43 12.98C19.47 12.66 19.5 12.34 19.5 12S19.47 11.34 19.43 11.02L21.54 9.37 19.54 5.91 17.05 6.91C16.54 6.52 16 6.2 15.38 5.95L15 3.27H11L10.62 5.95C10 6.2 9.46 6.52 8.95 6.91L6.46 5.91 4.46 9.37 6.57 11.02C6.53 11.34 6.5 11.66 6.5 12S6.53 12.66 6.57 12.98L4.46 14.63 6.46 18.09 8.95 17.09C9.46 17.48 10 17.8 10.62 18.05L11 20.73H15L15.38 18.05C16 17.8 16.54 17.48 17.05 17.09L19.54 18.09 21.54 14.63 19.43 12.98ZM13 15.5C11.07 15.5 9.5 13.93 9.5 12S11.07 8.5 13 8.5 16.5 10.07 16.5 12 14.93 15.5 13 15.5Z";
    public static final String ICON_ACCOUNT = "M12 12C14.21 12 16 10.21 16 8S14.21 4 12 4 8 5.79 8 8 9.79 12 12 12ZM12 14C9.33 14 4 15.34 4 18V20H20V18C20 15.34 14.67 14 12 14Z";
    public static final String ICON_IMPORT = "M5 20H19V18H5V20ZM19 9H15V3H9V9H5L12 16 19 9Z";
    public static final String ICON_LOGS = "M14 2H6C4.9 2 4 2.9 4 4V20C4 21.1 4.9 22 6 22H18C19.1 22 20 21.1 20 20V8L14 2ZM13 9V3.5L18.5 9H13ZM8 13H16V15H8V13ZM8 17H16V19H8V17Z";
    public static final String ICON_WATCHING = "M8 5V19L19 12 8 5Z";
    public static final String ICON_FAVORITE = "M17 3H7C5.9 3 5 3.9 5 5V21L12 18 19 21V5C19 3.9 18.1 3 17 3Z";
    private static final double NAV_RAIL_CURSOR_WIDTH = 82;
    private boolean cursorInstallScheduled;
    private boolean sceneCursorOverridden;
    private Cursor previousSceneCursor;

    public AppNavigationPane() {
        getStyleClass().add("uiptv-app-tabs");
        UiRenderQuality.optimizeLayout(this);
        setRotateGraphic(false);
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        setSide(Side.LEFT);
        setMinSize(0, 0);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        skinProperty().addListener((_, _, _) -> scheduleNavigationCursorInstall());
        sceneProperty().addListener((_, _, _) -> scheduleNavigationCursorInstall());
        widthProperty().addListener((_, _, _) -> scheduleNavigationCursorInstall());
        getSelectionModel().selectedItemProperty().addListener((_, _, _) -> scheduleNavigationCursorInstall());
        getTabs().addListener((ListChangeListener<Tab>) _ -> scheduleNavigationCursorInstall());
        addEventFilter(MouseEvent.MOUSE_MOVED, this::updateNavigationSceneCursor);
        addEventFilter(MouseEvent.MOUSE_ENTERED_TARGET, this::updateNavigationSceneCursor);
        addEventFilter(MouseEvent.MOUSE_EXITED, _ -> restoreNavigationSceneCursor());
        scheduleNavigationCursorInstall();
    }

    public Tab createTab(String label, String iconPath, Node content) {
        Tab tab = new Tab("", wrapContent(content));
        tab.getStyleClass().add("uiptv-nav-tab");
        tab.setClosable(false);
        tab.setTooltip(createImmediateTooltip(label));
        tab.setGraphic(createNavigationGraphic(label, iconPath));
        return tab;
    }

    public static Tooltip createImmediateTooltip(String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setShowDelay(Duration.ZERO);
        tooltip.setHideDelay(Duration.millis(80));
        tooltip.setShowDuration(Duration.seconds(6));
        return tooltip;
    }

    public static Node wrapContent(Node content) {
        StackPane wrapper = new StackPane(content);
        UiRenderQuality.optimizeLayout(wrapper);
        wrapper.getStyleClass().add("uiptv-nav-content");
        wrapper.setMinSize(0, 0);
        wrapper.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        if (content instanceof Region region) {
            region.setMinSize(0, 0);
            region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        }
        return wrapper;
    }

    private Node createNavigationGraphic(String labelText, String iconPath) {
        SVGPath icon = new SVGPath();
        icon.setContent(iconPath);
        icon.getStyleClass().add("uiptv-nav-icon");
        icon.setCursor(Cursor.HAND);

        StackPane item = new StackPane(icon);
        UiRenderQuality.optimizeLayout(item);
        UiRenderQuality.optimizeTextNode(icon);
        item.getStyleClass().add("uiptv-nav-item");
        item.setAlignment(Pos.CENTER);
        item.setAccessibleText(labelText);
        item.setCursor(Cursor.HAND);
        item.setMinSize(48, 48);
        item.setPrefSize(48, 48);
        item.setMaxSize(48, 48);

        StackPane hitbox = new StackPane(item);
        UiRenderQuality.optimizeLayout(hitbox);
        hitbox.getStyleClass().add("uiptv-nav-hitbox");
        hitbox.setAlignment(Pos.CENTER);
        hitbox.setCursor(Cursor.HAND);
        hitbox.setMinSize(62, 48);
        hitbox.setPrefSize(62, 48);
        hitbox.setMaxSize(62, 48);
        return hitbox;
    }

    private void scheduleNavigationCursorInstall() {
        if (cursorInstallScheduled) {
            return;
        }
        cursorInstallScheduled = true;
        Platform.runLater(() -> {
            cursorInstallScheduled = false;
            installNavigationCursors();
        });
    }

    private void installNavigationCursors() {
        applyCss();
        Set<Node> cursorNodes = new LinkedHashSet<>();
        cursorNodes.addAll(lookupAll(".uiptv-nav-tab"));
        cursorNodes.addAll(lookupAll(".uiptv-nav-hitbox"));
        cursorNodes.addAll(lookupAll(".tab").stream()
                .filter(this::isNavigationRailNode)
                .toList());
        cursorNodes.forEach(this::applyHandCursorRecursively);
    }

    private boolean isNavigationRailNode(Node node) {
        if (node == null || getScene() == null || node.getScene() != getScene()) {
            return false;
        }
        Bounds nodeBounds = node.localToScene(node.getBoundsInLocal());
        Bounds paneBounds = localToScene(getBoundsInLocal());
        if (nodeBounds == null || paneBounds == null) {
            return false;
        }
        return nodeBounds.getMaxX() <= paneBounds.getMinX() + NAV_RAIL_CURSOR_WIDTH;
    }

    private void applyHandCursorRecursively(Node node) {
        if (node == null) {
            return;
        }
        node.setCursor(Cursor.HAND);
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                applyHandCursorRecursively(child);
            }
        }
    }

    private void updateNavigationSceneCursor(MouseEvent event) {
        if (event != null && isOverNavigationTab(event)) {
            applyNavigationSceneCursor();
        } else {
            restoreNavigationSceneCursor();
        }
    }

    private boolean isOverNavigationTab(MouseEvent event) {
        if (event == null || getScene() == null) {
            return false;
        }
        double sceneX = event.getSceneX();
        double sceneY = event.getSceneY();
        for (Node tabNode : lookupAll(".tab")) {
            if (isNavigationRailNode(tabNode) && containsScenePoint(tabNode, sceneX, sceneY)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsScenePoint(Node node, double sceneX, double sceneY) {
        if (node == null) {
            return false;
        }
        Bounds bounds = node.localToScene(node.getBoundsInLocal());
        return bounds != null && bounds.contains(sceneX, sceneY);
    }

    private void applyNavigationSceneCursor() {
        if (getScene() == null) {
            return;
        }
        Cursor current = getScene().getCursor();
        if (!sceneCursorOverridden) {
            previousSceneCursor = current;
            sceneCursorOverridden = true;
        }
        if (current != Cursor.HAND) {
            getScene().setCursor(Cursor.HAND);
        }
    }

    private void restoreNavigationSceneCursor() {
        if (!sceneCursorOverridden || getScene() == null) {
            return;
        }
        if (getScene().getCursor() == Cursor.HAND) {
            getScene().setCursor(previousSceneCursor);
        }
        previousSceneCursor = null;
        sceneCursorOverridden = false;
    }
}
