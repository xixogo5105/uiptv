package com.uiptv.widget;

import com.uiptv.service.AccountChangeListener;
import com.uiptv.service.AccountService;
import com.uiptv.service.BookmarkChangeListener;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.ConfigurationChangeListener;
import com.uiptv.service.ConfigurationService;
import com.uiptv.model.Configuration;
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
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AppHeaderNavigation extends HBox {
    private static final String STYLE_ACTIVE = "app-header-nav-button-active";
    private static final String STYLE_ICON_ONLY = "app-header-nav-button-icon-only";
    private static final double NAV_BUTTON_SIZE = 34;

    private final HBox brand = new HBox(10);
    private final HBox tabs = new HBox(4);
    private final List<NavigationItem> navigationItems;
    private final Label bookmarksBadge = createBadgeLabel();
    private final Label accountsBadge = createBadgeLabel();

    private final BookmarkChangeListener bookmarkChangeListener = (_, _) -> refreshBadgeCounts();
    private final AccountChangeListener accountChangeListener = _ -> refreshBadgeCounts();
    private final ConfigurationChangeListener configurationChangeListener = _ -> Platform.runLater(this::refreshBadgeCounts);
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
                        AppNavigationPane.ICON_FAVORITE,
                        bookmarksBadge
                ),
                createNavigationItem(
                        AppNavigationController.Target.ACCOUNTS,
                        "Accounts",
                        I18n.tr("autoAccount"),
                        AppNavigationPane.ICON_ACCOUNT,
                        accountsBadge
                ),
                createNavigationItem(
                        AppNavigationController.Target.WATCHING_NOW,
                        "Watching",
                        I18n.tr("autoWatchingNow"),
                        AppNavigationPane.ICON_WATCHING,
                        null
                )
        );
        tabs.getStyleClass().add("app-header-top-tabs");
        tabs.setAlignment(Pos.CENTER_LEFT);
        refreshTabs();

        HBox.setHgrow(brand, Priority.NEVER);
        HBox.setHgrow(tabs, Priority.NEVER);
        getChildren().setAll(brand, tabs);
        registerNavigationListener();
        updateNavigationButtons();
        refreshBadgeCounts();

        sceneProperty().addListener((_, oldScene, newScene) -> {
            if (oldScene == null && newScene != null) {
                registerNavigationListener();
                updateNavigationButtons();
                refreshBadgeCounts();
            } else if (oldScene != null && newScene == null) {
                unregisterNavigationListener();
            }
        });
        visibleProperty().addListener((_, _, visible) -> {
            if (Boolean.TRUE.equals(visible)) {
                registerNavigationListener();
                updateNavigationButtons();
                refreshBadgeCounts();
            }
        });
        parentProperty().addListener((_, _, newParent) -> {
            if (newParent != null) {
                registerNavigationListener();
                updateNavigationButtons();
                refreshBadgeCounts();
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
            button.getStyleClass().removeAll(STYLE_ICON_ONLY);
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

    private static volatile int cachedBookmarkCount = -1;
    private static volatile int cachedAccountCount = -1;

    public static void setCachedBadgeCountsForTest(int bookmarkCount, int accountCount) {
        cachedBookmarkCount = bookmarkCount;
        cachedAccountCount = accountCount;
    }

    public void refreshBadgeCounts() {
        // Check if badge counts should be shown based on configuration
        ConfigurationService configurationService = ConfigurationService.getInstance();
        Configuration configuration = configurationService.read();
        if (configuration != null && !configuration.isShowBookmarkAndAccountCounts()) {
            // Hide badges if setting is disabled
            updateBadgeCounts(0, 0);
            return;
        }
        
        if (cachedBookmarkCount >= 0 || cachedAccountCount >= 0) {
            updateBadgeCounts(cachedBookmarkCount, cachedAccountCount);
        }
        CompletableFuture.runAsync(() -> {
            int bookmarks = -1;
            int accounts = -1;
            try {
                bookmarks = BookmarkService.getInstance().read().size();
            } catch (Exception _) {
            }
            try {
                accounts = AccountService.getInstance().getAll().size();
            } catch (Exception _) {
            }

            if (bookmarks >= 0) {
                cachedBookmarkCount = bookmarks;
            }
            if (accounts >= 0) {
                cachedAccountCount = accounts;
            }

            final int finalBookmarks = cachedBookmarkCount;
            final int finalAccounts = cachedAccountCount;

            if (finalBookmarks >= 0 || finalAccounts >= 0) {
                Platform.runLater(() -> updateBadgeCounts(finalBookmarks, finalAccounts));
            }
        });
    }

    public void updateBadgeCounts(int bookmarkCount, int accountCount) {
        if (bookmarkCount >= 0) {
            cachedBookmarkCount = bookmarkCount;
            updateBadgeLabel(bookmarksBadge, bookmarkCount);
        }
        if (accountCount >= 0) {
            cachedAccountCount = accountCount;
            updateBadgeLabel(accountsBadge, accountCount);
        }
    }

    public Label getBookmarksBadge() {
        return bookmarksBadge;
    }

    public Label getAccountsBadge() {
        return accountsBadge;
    }

    private static Label createBadgeLabel() {
        Label badge = new Label();
        badge.getStyleClass().add("app-header-nav-badge");
        badge.setMouseTransparent(true);
        badge.setVisible(false);
        return badge;
    }

    private void updateBadgeLabel(Label badge, int count) {
        if (badge == null || count < 0) {
            return;
        }
        if (count > 0) {
            badge.setText(String.valueOf(count));
            badge.setVisible(true);
        } else {
            badge.setText("");
            badge.setVisible(false);
        }
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
            String iconPath,
            Label badgeLabel
    ) {
        Button button = new Button(visibleLabel);
        button.getStyleClass().add("app-header-nav-button");
        button.setAccessibleText(accessibleLabel);
        button.setGraphic(createIconWithBadge(iconPath, badgeLabel));
        button.setOnAction(_ -> AppNavigationController.navigate(target));
        button.setFocusTraversable(true);
        button.setMinHeight(NAV_BUTTON_SIZE);
        button.setPrefHeight(NAV_BUTTON_SIZE);
        return new NavigationItem(target, visibleLabel, button, badgeLabel);
    }

    private Node createIconWithBadge(String iconPath, Label badgeLabel) {
        SVGPath icon = new SVGPath();
        icon.setContent(iconPath);
        icon.getStyleClass().add("app-header-nav-icon");
        UiRenderQuality.optimizeTextNode(icon);

        if (badgeLabel == null) {
            return icon;
        }
        return new BadgeIconContainer(icon, badgeLabel);
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
            button.getStyleClass().removeAll(STYLE_ACTIVE);
            if (selectionEnabled && item.target() == currentTarget) {
                button.getStyleClass().add(STYLE_ACTIVE);
            }
        }
        refreshBadgeCounts();
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
        try {
            BookmarkService.getInstance().addChangeListener(bookmarkChangeListener);
            AccountService.getInstance().addChangeListener(accountChangeListener);
            ConfigurationService.getInstance().addChangeListener(configurationChangeListener);
        } catch (Exception _) {
        }
        navigationListenerRegistered = true;
    }

    private void unregisterNavigationListener() {
        if (!navigationListenerRegistered) {
            return;
        }
        AppNavigationController.currentTargetProperty().removeListener(navigationTargetListener);
        try {
            BookmarkService.getInstance().removeChangeListener(bookmarkChangeListener);
            AccountService.getInstance().removeChangeListener(accountChangeListener);
            ConfigurationService.getInstance().removeChangeListener(configurationChangeListener);
        } catch (Exception _) {
        }
        navigationListenerRegistered = false;
    }

    private static class BadgeIconContainer extends StackPane {
        private final Label badgeLabel;

        public BadgeIconContainer(SVGPath icon, Label badgeLabel) {
            this.badgeLabel = badgeLabel;
            getChildren().add(icon);
            if (badgeLabel != null) {
                getChildren().add(badgeLabel);
                badgeLabel.setManaged(false);
            }
            UiRenderQuality.optimizeLayout(this);
        }

        @Override
        protected void layoutChildren() {
            super.layoutChildren();
            if (badgeLabel != null && badgeLabel.isVisible()) {
                double badgeWidth = badgeLabel.prefWidth(-1);
                double badgeHeight = badgeLabel.prefHeight(-1);
                double x = getWidth() - (badgeWidth / 2.0) + 4;
                double y = - (badgeHeight / 2.0) - 4;
                badgeLabel.resizeRelocate(x, y, badgeWidth, badgeHeight);
            }
        }
    }

    private record NavigationItem(AppNavigationController.Target target, String visibleLabel, Button button, Label badgeLabel) {
    }
}

