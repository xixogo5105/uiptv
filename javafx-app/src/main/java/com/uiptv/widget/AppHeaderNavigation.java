package com.uiptv.widget;

import com.uiptv.util.I18n;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.shape.SVGPath;

import java.util.List;

public class AppHeaderNavigation extends HBox {
    private static final String STYLE_ACTIVE = "app-header-nav-button-active";
    private static final String STYLE_ICON_ONLY = "app-header-nav-button-icon-only";
    private static final double NAV_BUTTON_SIZE = 34;

    private final HBox brand = new HBox(10);
    private final HBox tabs = new HBox(4);
    private final List<NavigationItem> navigationItems;
    private final ChangeListener<AppNavigationController.Target> navigationTargetListener =
            (_, _, _) -> Platform.runLater(this::updateNavigationButtons);
    private Node trailingAction;
    private boolean navigationListenerRegistered;
    private boolean compact;
    private boolean selectionEnabled = true;
    private boolean titleVisible = true;

    public AppHeaderNavigation(Node titleNode) {
        super(10);
        getStyleClass().add("app-header-leading");
        UiRenderQuality.optimizeLayout(this);
        UiRenderQuality.optimizeLayout(brand);
        UiRenderQuality.optimizeLayout(tabs);
        setAlignment(Pos.CENTER_LEFT);
        setMinWidth(0);
        setMaxWidth(Double.MAX_VALUE);

        configureBrand(titleNode);
        navigationItems = List.of(
                createNavigationItem(
                        AppNavigationController.Target.BOOKMARKS,
                        "Bookmarks",
                        I18n.tr("autoFavorite"),
                        AppNavigationPane.ICON_FAVORITE
                ),
                createNavigationItem(
                        AppNavigationController.Target.ACCOUNTS,
                        "Accounts",
                        I18n.tr("autoAccount"),
                        AppNavigationPane.ICON_ACCOUNT
                ),
                createNavigationItem(
                        AppNavigationController.Target.WATCHING_NOW,
                        "Watching",
                        I18n.tr("autoWatchingNow"),
                        AppNavigationPane.ICON_WATCHING
                )
        );
        tabs.getStyleClass().add("app-header-top-tabs");
        tabs.setAlignment(Pos.CENTER_LEFT);
        refreshTabs();

        HBox.setHgrow(brand, Priority.NEVER);
        HBox.setHgrow(tabs, Priority.NEVER);
        getChildren().setAll(brand, tabs);
        updateNavigationButtons();

        sceneProperty().addListener((_, oldScene, newScene) -> {
            if (oldScene == null && newScene != null) {
                registerNavigationListener();
                updateNavigationButtons();
            } else if (oldScene != null && newScene == null) {
                unregisterNavigationListener();
            }
        });
    }

    public void setCompact(boolean compact) {
        if (this.compact == compact) {
            return;
        }
        this.compact = compact;
        for (NavigationItem item : navigationItems) {
            Button button = item.button();
            button.setText(compact ? "" : item.visibleLabel());
            button.getStyleClass().remove(STYLE_ICON_ONLY);
            if (compact) {
                button.getStyleClass().add(STYLE_ICON_ONLY);
                button.setMinWidth(NAV_BUTTON_SIZE);
                button.setPrefWidth(NAV_BUTTON_SIZE);
                button.setMaxWidth(NAV_BUTTON_SIZE);
            } else {
                button.setMinWidth(Region.USE_PREF_SIZE);
                button.setPrefWidth(Region.USE_COMPUTED_SIZE);
                button.setMaxWidth(Region.USE_COMPUTED_SIZE);
            }
        }
    }

    public void setTrailingAction(Node trailingAction) {
        if (this.trailingAction == trailingAction) {
            return;
        }
        this.trailingAction = trailingAction;
        refreshTabs();
    }

    public void setSelectionEnabled(boolean selectionEnabled) {
        if (this.selectionEnabled == selectionEnabled) {
            return;
        }
        this.selectionEnabled = selectionEnabled;
        updateNavigationButtons();
    }

    public void setTitleVisible(boolean titleVisible) {
        if (this.titleVisible == titleVisible) {
            return;
        }
        this.titleVisible = titleVisible;
        refreshBrandVisibility();
    }

    private void configureBrand(Node titleNode) {
        brand.getStyleClass().add("app-header-brand");
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setMinWidth(0);

        Node title = titleNode == null ? new Label("UIPTV") : titleNode;
        if (title instanceof Label label) {
            label.getStyleClass().add("app-header-brand-title");
            label.setMinWidth(0);
            label.setMaxWidth(Double.MAX_VALUE);
            label.textProperty().addListener((_, _, _) -> refreshBrandVisibility());
        }

        HBox.setHgrow(title, Priority.SOMETIMES);
        brand.getChildren().setAll(title);
        refreshBrandVisibility();
    }

    private NavigationItem createNavigationItem(
            AppNavigationController.Target target,
            String visibleLabel,
            String accessibleLabel,
            String iconPath
    ) {
        Button button = new Button(visibleLabel);
        button.getStyleClass().add("app-header-nav-button");
        button.setAccessibleText(accessibleLabel);
        button.setTooltip(AppNavigationPane.createImmediateTooltip(accessibleLabel));
        button.setGraphic(createIcon(iconPath));
        button.setOnAction(_ -> AppNavigationController.navigate(target));
        button.setFocusTraversable(true);
        button.setMinHeight(NAV_BUTTON_SIZE);
        button.setPrefHeight(NAV_BUTTON_SIZE);
        return new NavigationItem(target, visibleLabel, button);
    }

    private Node createIcon(String iconPath) {
        SVGPath icon = new SVGPath();
        icon.setContent(iconPath);
        icon.getStyleClass().add("app-header-nav-icon");
        UiRenderQuality.optimizeTextNode(icon);
        return icon;
    }

    private void refreshTabs() {
        List<Node> tabChildren = new java.util.ArrayList<>(navigationItems.stream()
                .map(NavigationItem::button)
                .toList());
        if (trailingAction != null) {
            tabChildren.add(trailingAction);
        }
        tabs.getChildren().setAll(tabChildren);
    }

    private void updateNavigationButtons() {
        AppNavigationController.Target currentTarget = AppNavigationController.currentTarget();
        for (NavigationItem item : navigationItems) {
            Button button = item.button();
            button.getStyleClass().remove(STYLE_ACTIVE);
            if (selectionEnabled && item.target() == currentTarget) {
                button.getStyleClass().add(STYLE_ACTIVE);
            }
        }
    }

    private void refreshBrandVisibility() {
        boolean visible = titleVisible && hasVisibleTitle();
        brand.setVisible(visible);
        brand.setManaged(visible);
    }

    private boolean hasVisibleTitle() {
        for (Node node : brand.getChildren()) {
            if (node instanceof Label label) {
                String text = label.getText();
                if (text != null && !text.isBlank()) {
                    return true;
                }
            } else if (node != null) {
                return true;
            }
        }
        return false;
    }

    private void registerNavigationListener() {
        if (navigationListenerRegistered) {
            return;
        }
        AppNavigationController.currentTargetProperty().addListener(navigationTargetListener);
        navigationListenerRegistered = true;
    }

    private void unregisterNavigationListener() {
        if (!navigationListenerRegistered) {
            return;
        }
        AppNavigationController.currentTargetProperty().removeListener(navigationTargetListener);
        navigationListenerRegistered = false;
    }

    private record NavigationItem(AppNavigationController.Target target, String visibleLabel, Button button) {
    }
}
