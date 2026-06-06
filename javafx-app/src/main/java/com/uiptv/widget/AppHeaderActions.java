package com.uiptv.widget;

import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationChangeListener;
import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.AboutUI;
import com.uiptv.ui.FilterLockDialogs;
import com.uiptv.ui.ThumbnailAwareUI;
import com.uiptv.ui.util.ImageCacheManager;
import com.uiptv.ui.util.UiI18n;
import com.uiptv.ui.util.UiServerUrlUtil;
import com.uiptv.util.I18n;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

import static com.uiptv.widget.UIptvAlert.showMessageAlert;

public class AppHeaderActions extends HBox {
    private static final String GUIDE_URL = "https://github.com/xixogo5105/uiptv/blob/main/GUIDE.md";
    private static final String FILTER_LOCK_UNLOCK_MANAGE_FILTERS_REASON = "filterLockUnlockManageFiltersReason";
    private static final String ICON_ABOUT = "M11 17H13V11H11V17ZM11 9H13V7H11V9ZM12 2C6.48 2 2 6.48 2 12S6.48 22 12 22 22 17.52 22 12 17.52 2 12 2ZM12 20C7.59 20 4 16.41 4 12S7.59 4 12 4 20 7.59 20 12 16.41 20 12 20Z";
    private static final String ICON_GEAR = "M19.43 12.98C19.47 12.66 19.5 12.34 19.5 12S19.47 11.34 19.43 11.02L21.54 9.37 19.54 5.91 17.05 6.91C16.54 6.52 16 6.2 15.38 5.95L15 3.27H11L10.62 5.95C10 6.2 9.46 6.52 8.95 6.91L6.46 5.91 4.46 9.37 6.57 11.02C6.53 11.34 6.5 11.66 6.5 12S6.53 12.66 6.57 12.98L4.46 14.63 6.46 18.09 8.95 17.09C9.46 17.48 10 17.8 10.62 18.05L11 20.73H15L15.38 18.05C16 17.8 16.54 17.48 17.05 17.09L19.54 18.09 21.54 14.63 19.43 12.98ZM13 15.5C11.07 15.5 9.5 13.93 9.5 12S11.07 8.5 13 8.5 16.5 10.07 16.5 12 14.93 15.5 13 15.5Z";
    private static final String ICON_HELP = "M11 18H13V16H11V18ZM12 2C6.48 2 2 6.48 2 12S6.48 22 12 22 22 17.52 22 12 17.52 2 12 2ZM12 20C7.59 20 4 16.41 4 12S7.59 4 12 4 20 7.59 20 12 16.41 20 12 20ZM12 6C9.79 6 8 7.79 8 10H10C10 8.9 10.9 8 12 8S14 8.9 14 10C14 12 11 11.75 11 15H13C13 12.75 16 12.5 16 10 16 7.79 14.21 6 12 6Z";
    private static final String ICON_PARENTAL_LOCK = "M12 17C13.1 17 14 16.1 14 15S13.1 13 12 13 10 13.9 10 15 10.9 17 12 17ZM18 8H17V6C17 3.24 14.76 1 12 1S7 3.24 7 6V8H6C4.9 8 4 8.9 4 10V20C4 21.1 4.9 22 6 22H18C19.1 22 20 21.1 20 20V10C20 8.9 19.1 8 18 8ZM9 6C9 4.34 10.34 3 12 3S15 4.34 15 6V8H9V6ZM18 20H6V10H18V20Z";
    private static final String ICON_PARENTAL_UNLOCKED = "M12 17C13.1 17 14 16.1 14 15S13.1 13 12 13 10 13.9 10 15 10.9 17 12 17ZM18 8H9V6C9 4.34 10.34 3 12 3 13.09 3 14.05 3.58 14.58 4.45L16.32 3.45C15.44 1.99 13.84 1 12 1 9.24 1 7 3.24 7 6V8H6C4.9 8 4 8.9 4 10V20C4 21.1 4.9 22 6 22H18C19.1 22 20 21.1 20 20V10C20 8.9 19.1 8 18 8ZM18 20H6V10H18V20Z";
    private static final String ICON_HIDE_NAVIGATION = "M3 5H21V19H3V5ZM5 7V17H10V7H5ZM15.3 8.7L13.9 10.1 15.8 12 13.9 13.9 15.3 15.3 18.7 12Z";
    private static final String ICON_SHOW_NAVIGATION = "M3 5H21V19H3V5ZM5 7V17H10V7H5ZM12.7 8.7L16.1 12 12.7 15.3 11.3 13.9 13.2 12 11.3 10.1Z";

    private final HostServices hostServices;
    private final Runnable parentalPauseChangedHandler;
    private final IconActionButton widePlayerNavigationButton = new IconActionButton(
            I18n.tr("autoHidePlaybackNavigation"),
            ICON_HIDE_NAVIGATION,
            WidePlayerNavigationControl::toggle
    );
    private final IconActionButton bookmarkButton = createNavigationButton(
            I18n.tr("autoFavorite"),
            AppNavigationPane.ICON_FAVORITE,
            AppNavigationController.Target.BOOKMARKS
    );
    private final IconActionButton accountButton = createNavigationButton(
            I18n.tr("autoAccount"),
            AppNavigationPane.ICON_ACCOUNT,
            AppNavigationController.Target.ACCOUNTS
    );
    private final IconActionButton watchingNowButton = createNavigationButton(
            I18n.tr("autoWatchingNow"),
            AppNavigationPane.ICON_WATCHING,
            AppNavigationController.Target.WATCHING_NOW
    );
    private final IconActionButton gearButton = new IconActionButton(I18n.tr("autoSettings"), ICON_GEAR, this::showGearMenu);
    private final ChangeListener<AppNavigationController.Target> navigationTargetListener =
            (_, _, _) -> Platform.runLater(this::updateNavigationButtons);
    private final ConfigurationChangeListener configurationChangeListener =
            _ -> Platform.runLater(() -> {
                if (getScene() != null) {
                    refreshState();
                }
            });
    private final Runnable widePlayerNavigationListener =
            () -> Platform.runLater(this::updateWidePlayerNavigationButton);
    private boolean configurationListenerRegistered;
    private boolean navigationListenerRegistered;
    private boolean widePlayerNavigationListenerRegistered;

    public AppHeaderActions(HostServices hostServices, Runnable themeToggleHandler, Runnable parentalPauseChangedHandler) {
        super(6);
        this.hostServices = hostServices;
        this.parentalPauseChangedHandler = parentalPauseChangedHandler;
        getStyleClass().add("bookmarks-quick-actions");
        UiRenderQuality.optimizeLayout(this);
        setAlignment(Pos.CENTER_RIGHT);
        setMinWidth(Region.USE_PREF_SIZE);
        setMaxWidth(Region.USE_PREF_SIZE);
        setPickOnBounds(false);
        getChildren().addAll(widePlayerNavigationButton, bookmarkButton, accountButton, watchingNowButton, gearButton);
        refreshState();
        sceneProperty().addListener((_, oldScene, newScene) -> {
            if (oldScene == null && newScene != null) {
                registerConfigurationChangeListener();
                registerNavigationListener();
                registerWidePlayerNavigationListener();
                refreshState();
            } else if (oldScene != null && newScene == null) {
                unregisterConfigurationChangeListener();
                unregisterNavigationListener();
                unregisterWidePlayerNavigationListener();
            }
        });
    }

    public void refreshState() {
        updateWidePlayerNavigationButton();
        updateNavigationButtons();
        updateGearButton();
    }

    private IconActionButton createNavigationButton(String tooltip, String iconPath, AppNavigationController.Target target) {
        IconActionButton button = new IconActionButton(tooltip, iconPath, () -> AppNavigationController.navigate(target));
        button.setAccessibleText(tooltip);
        return button;
    }

    private void updateNavigationButtons() {
        AppNavigationController.Target currentTarget = AppNavigationController.currentTarget();
        updateNavigationButton(bookmarkButton, currentTarget == AppNavigationController.Target.BOOKMARKS);
        updateNavigationButton(accountButton, currentTarget == AppNavigationController.Target.ACCOUNTS);
        updateNavigationButton(watchingNowButton, currentTarget == AppNavigationController.Target.WATCHING_NOW);
    }

    private void updateNavigationButton(IconActionButton button, boolean active) {
        button.getStyleClass().remove("bookmarks-quick-action-button-active");
        if (active) {
            button.getStyleClass().add("bookmarks-quick-action-button-active");
        }
    }

    private void updateWidePlayerNavigationButton() {
        boolean available = WidePlayerNavigationControl.isAvailable();
        boolean collapsed = WidePlayerNavigationControl.isCollapsed();
        widePlayerNavigationButton.setVisible(available);
        widePlayerNavigationButton.setManaged(available);
        widePlayerNavigationButton.setIconPath(collapsed ? ICON_SHOW_NAVIGATION : ICON_HIDE_NAVIGATION);
        widePlayerNavigationButton.setTooltipText(I18n.tr(collapsed
                ? "autoShowPlaybackNavigation"
                : "autoHidePlaybackNavigation"));
        updateNavigationButton(widePlayerNavigationButton, collapsed);
    }

    private void updateGearButton() {
        Configuration configuration = readConfigurationSafely();
        boolean paused = configuration != null && configuration.isPauseFiltering();
        gearButton.setTooltipText(I18n.tr("autoSettings"));
        gearButton.getStyleClass().remove("bookmarks-quick-action-button-lock-ok");
        gearButton.getStyleClass().remove("bookmarks-quick-action-button-lock-paused");
        gearButton.getStyleClass().add(paused
                ? "bookmarks-quick-action-button-lock-paused"
                : "bookmarks-quick-action-button-lock-ok");
    }

    private void showGearMenu() {
        ContextMenu menu = createGearMenu();
        UiI18n.preparePopupControl(menu, gearButton);
        menu.show(gearButton, Side.BOTTOM, 0, 0);
    }

    ContextMenu createGearMenu() {
        return new ContextMenu(
                createNavigationMenuItem(I18n.tr("autoSettings"), AppNavigationPane.ICON_SETTINGS, AppNavigationController.Target.SETTINGS),
                createNavigationMenuItem(I18n.tr("autoImportBulkAccounts"), AppNavigationPane.ICON_IMPORT, AppNavigationController.Target.IMPORT),
                createNavigationMenuItem(I18n.tr("autoLogs"), AppNavigationPane.ICON_LOGS, AppNavigationController.Target.LOGS),
                createMenuItem(parentalLockMenuText(), parentalLockIcon(), this::toggleParentalPause),
                createPlainTextModeMenuItem(),
                createMenuItem(I18n.tr("autoHelp"), ICON_HELP, () -> openExternalUrl(GUIDE_URL)),
                createMenuItem(I18n.tr("autoAbout"), ICON_ABOUT, this::showAbout)
        );
    }

    private MenuItem createNavigationMenuItem(String text, String iconPath, AppNavigationController.Target target) {
        return createMenuItem(text, iconPath, () -> AppNavigationController.navigate(target));
    }

    private MenuItem createMenuItem(String text, String iconPath, Runnable action) {
        MenuItem item = new MenuItem(text, createMenuIcon(iconPath));
        item.setOnAction(_ -> action.run());
        return item;
    }

    private MenuItem createPlainTextModeMenuItem() {
        MenuItem item = new MenuItem(plainTextModeMenuText(), createPlainTextModeMenuIcon());
        item.setOnAction(_ -> togglePlainTextMode());
        return item;
    }

    private String parentalLockMenuText() {
        Configuration configuration = ConfigurationService.getInstance().read();
        boolean paused = configuration != null && configuration.isPauseFiltering();
        return paused
                ? "Resume parental lock restrictions"
                : "Pause parental lock restrictions";
    }

    private Configuration readConfigurationSafely() {
        try {
            return ConfigurationService.getInstance().read();
        } catch (RuntimeException _) {
            return null;
        }
    }

    private String parentalLockIcon() {
        Configuration configuration = ConfigurationService.getInstance().read();
        boolean paused = configuration != null && configuration.isPauseFiltering();
        return paused ? ICON_PARENTAL_UNLOCKED : ICON_PARENTAL_LOCK;
    }

    private String plainTextModeMenuText() {
        Configuration configuration = ConfigurationService.getInstance().read();
        boolean plainTextMode = configuration != null && !configuration.isEnableThumbnails();
        return plainTextMode
                ? I18n.tr("autoDisablePlainTextMode")
                : I18n.tr("autoEnablePlainTextMode");
    }

    private Node createMenuIcon(String iconPath) {
        SVGPath icon = new SVGPath();
        icon.setContent(iconPath);
        icon.getStyleClass().add("bookmarks-quick-action-icon");
        UiRenderQuality.optimizeTextNode(icon);
        StackPane wrapper = new StackPane(icon);
        wrapper.setMinSize(20, 20);
        wrapper.setPrefSize(20, 20);
        wrapper.setMaxSize(20, 20);
        return wrapper;
    }

    private Node createPlainTextModeMenuIcon() {
        Label icon = new Label("1");
        icon.getStyleClass().add("plain-text-mode-quick-icon");
        icon.setMinSize(20, 20);
        icon.setPrefSize(20, 20);
        icon.setMaxSize(20, 20);
        return icon;
    }

    private void toggleParentalPause() {
        if (!FilterLockDialogs.ensureUnlocked(this, FILTER_LOCK_UNLOCK_MANAGE_FILTERS_REASON)) {
            updateGearButton();
            return;
        }
        Configuration configuration = ConfigurationService.getInstance().read();
        if (configuration == null) {
            return;
        }
        configuration.setPauseFiltering(!configuration.isPauseFiltering());
        ConfigurationService.getInstance().save(configuration);
        updateGearButton();
        if (parentalPauseChangedHandler != null) {
            parentalPauseChangedHandler.run();
        }
        showMessageAlert(configuration.isPauseFiltering()
                ? "Parental lock restrictions paused."
                : "Parental lock restrictions resumed.");
    }

    private void togglePlainTextMode() {
        Configuration configuration = ConfigurationService.getInstance().read();
        if (configuration == null) {
            return;
        }
        boolean thumbnailsEnabled = !configuration.isEnableThumbnails();
        configuration.setEnableThumbnails(thumbnailsEnabled);
        ConfigurationService.getInstance().save(configuration);
        if (thumbnailsEnabled) {
            ImageCacheManager.clearTransientFailures();
        }
        ThumbnailAwareUI.notifyThumbnailModeChanged(thumbnailsEnabled);
    }

    private void openExternalUrl(String url) {
        if (hostServices != null) {
            hostServices.showDocument(url);
            return;
        }
        UiServerUrlUtil.openInBrowser(url);
    }

    private void showAbout() {
        if (hostServices != null) {
            AboutUI.show(hostServices);
        }
    }

    private void registerConfigurationChangeListener() {
        if (configurationListenerRegistered) {
            return;
        }
        ConfigurationService.getInstance().addChangeListener(configurationChangeListener);
        configurationListenerRegistered = true;
    }

    private void unregisterConfigurationChangeListener() {
        if (!configurationListenerRegistered) {
            return;
        }
        ConfigurationService.getInstance().removeChangeListener(configurationChangeListener);
        configurationListenerRegistered = false;
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

    private void registerWidePlayerNavigationListener() {
        if (widePlayerNavigationListenerRegistered) {
            return;
        }
        WidePlayerNavigationControl.addListener(widePlayerNavigationListener);
        widePlayerNavigationListenerRegistered = true;
    }

    private void unregisterWidePlayerNavigationListener() {
        if (!widePlayerNavigationListenerRegistered) {
            return;
        }
        WidePlayerNavigationControl.removeListener(widePlayerNavigationListener);
        widePlayerNavigationListenerRegistered = false;
    }
}
