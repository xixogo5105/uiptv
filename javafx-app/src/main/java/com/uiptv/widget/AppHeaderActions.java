package com.uiptv.widget;

import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationChangeListener;
import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.AboutUI;
import com.uiptv.ui.FilterLockDialogs;
import com.uiptv.ui.ThumbnailAwareUI;
import com.uiptv.ui.util.ImageCacheManager;
import com.uiptv.ui.util.UiServerUrlUtil;
import com.uiptv.util.I18n;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.util.Duration;

import static com.uiptv.widget.UIptvAlert.showMessageAlert;

public class AppHeaderActions extends HBox {
    private static final String GUIDE_URL = "https://github.com/xixogo5105/uiptv/blob/main/GUIDE.md";
    private static final String FILTER_LOCK_UNLOCK_MANAGE_FILTERS_REASON = "filterLockUnlockManageFiltersReason";
    private static final String ICON_ABOUT = "M11 17H13V11H11V17ZM11 9H13V7H11V9ZM12 2C6.48 2 2 6.48 2 12S6.48 22 12 22 22 17.52 22 12 17.52 2 12 2ZM12 20C7.59 20 4 16.41 4 12S7.59 4 12 4 20 7.59 20 12 16.41 20 12 20Z";
    private static final String ICON_HELP = "M11 18H13V16H11V18ZM12 2C6.48 2 2 6.48 2 12S6.48 22 12 22 22 17.52 22 12 17.52 2 12 2ZM12 20C7.59 20 4 16.41 4 12S7.59 4 12 4 20 7.59 20 12 16.41 20 12 20ZM12 6C9.79 6 8 7.79 8 10H10C10 8.9 10.9 8 12 8S14 8.9 14 10C14 12 11 11.75 11 15H13C13 12.75 16 12.5 16 10 16 7.79 14.21 6 12 6Z";
    private static final String ICON_THEME = "M12 3C7.03 3 3 7.03 3 12S7.03 21 12 21C15.31 21 18.2 19.21 19.76 16.54 18.86 16.84 17.91 17 16.92 17 11.95 17 7.92 12.97 7.92 8 7.92 6.39 8.34 4.87 9.08 3.56 9.98 3.2 10.96 3 12 3Z";
    private static final String ICON_PARENTAL_LOCK = "M12 17C13.1 17 14 16.1 14 15S13.1 13 12 13 10 13.9 10 15 10.9 17 12 17ZM18 8H17V6C17 3.24 14.76 1 12 1S7 3.24 7 6V8H6C4.9 8 4 8.9 4 10V20C4 21.1 4.9 22 6 22H18C19.1 22 20 21.1 20 20V10C20 8.9 19.1 8 18 8ZM9 6C9 4.34 10.34 3 12 3S15 4.34 15 6V8H9V6ZM18 20H6V10H18V20Z";
    private static final String ICON_PARENTAL_UNLOCKED = "M12 17C13.1 17 14 16.1 14 15S13.1 13 12 13 10 13.9 10 15 10.9 17 12 17ZM18 8H9V6C9 4.34 10.34 3 12 3 13.09 3 14.05 3.58 14.58 4.45L16.32 3.45C15.44 1.99 13.84 1 12 1 9.24 1 7 3.24 7 6V8H6C4.9 8 4 8.9 4 10V20C4 21.1 4.9 22 6 22H18C19.1 22 20 21.1 20 20V10C20 8.9 19.1 8 18 8ZM18 20H6V10H18V20Z";
    private static final String ICON_HIDE_NAVIGATION = "M3 5H21V19H3V5ZM5 7V17H10V7H5ZM15.3 8.7L16.7 10.1 14.8 12 16.7 13.9 15.3 15.3 11.9 12Z";
    private static final String ICON_SHOW_NAVIGATION = "M3 5H21V19H3V5ZM5 7V17H10V7H5ZM12.7 8.7L16.1 12 12.7 15.3 11.3 13.9 13.2 12 11.3 10.1Z";

    private final HostServices hostServices;
    private final Runnable themeToggleHandler;
    private final Runnable parentalPauseChangedHandler;
    private final IconActionButton parentalPauseButton =
            new IconActionButton("Pause parental lock restrictions", ICON_PARENTAL_LOCK, this::toggleParentalPause);
    private final IconActionButton wideNavigationButton =
            new IconActionButton(I18n.tr("autoHidePlaybackNavigation"), ICON_HIDE_NAVIGATION, WidePlayerNavigationControl::toggle);
    private final Button plainTextModeButton = createPlainTextModeButton();
    private final Runnable wideNavigationListener = () -> Platform.runLater(this::updateWideNavigationButton);
    private final ConfigurationChangeListener configurationChangeListener =
            _ -> Platform.runLater(() -> {
                updateParentalPauseButton();
                updatePlainTextModeButton();
            });
    private boolean configurationListenerRegistered;

    public AppHeaderActions(HostServices hostServices, Runnable themeToggleHandler, Runnable parentalPauseChangedHandler) {
        super(6);
        this.hostServices = hostServices;
        this.themeToggleHandler = themeToggleHandler;
        this.parentalPauseChangedHandler = parentalPauseChangedHandler;
        getStyleClass().add("bookmarks-quick-actions");
        UiRenderQuality.optimizeLayout(this);
        setAlignment(Pos.CENTER_RIGHT);
        setMinWidth(Region.USE_PREF_SIZE);
        setMaxWidth(Region.USE_PREF_SIZE);
        setPickOnBounds(false);
        getChildren().addAll(
                new IconActionButton(I18n.tr("autoAbout"), ICON_ABOUT, this::showAbout),
                wideNavigationButton,
                new IconActionButton(I18n.tr("autoHelp"), ICON_HELP, () -> openExternalUrl(GUIDE_URL)),
                parentalPauseButton,
                new IconActionButton("Toggle theme", ICON_THEME, this::toggleTheme),
                plainTextModeButton
        );
        updateWideNavigationButton();
        updateParentalPauseButton();
        updatePlainTextModeButton();
        sceneProperty().addListener((_, oldScene, newScene) -> {
            if (oldScene == null && newScene != null) {
                registerConfigurationChangeListener();
                WidePlayerNavigationControl.addListener(wideNavigationListener);
                updateWideNavigationButton();
                updateParentalPauseButton();
                updatePlainTextModeButton();
            } else if (oldScene != null && newScene == null) {
                unregisterConfigurationChangeListener();
                WidePlayerNavigationControl.removeListener(wideNavigationListener);
            }
        });
    }

    private void toggleParentalPause() {
        if (!FilterLockDialogs.ensureUnlocked(this, FILTER_LOCK_UNLOCK_MANAGE_FILTERS_REASON)) {
            updateParentalPauseButton();
            return;
        }
        Configuration configuration = ConfigurationService.getInstance().read();
        if (configuration == null) {
            return;
        }
        configuration.setPauseFiltering(!configuration.isPauseFiltering());
        ConfigurationService.getInstance().save(configuration);
        updateParentalPauseButton();
        if (parentalPauseChangedHandler != null) {
            parentalPauseChangedHandler.run();
        }
        showMessageAlert(configuration.isPauseFiltering()
                ? "Parental lock restrictions paused."
                : "Parental lock restrictions resumed.");
    }

    public void refreshState() {
        updateWideNavigationButton();
        updateParentalPauseButton();
        updatePlainTextModeButton();
    }

    private void updateWideNavigationButton() {
        boolean available = WidePlayerNavigationControl.isAvailable();
        boolean collapsed = WidePlayerNavigationControl.isCollapsed();
        wideNavigationButton.setVisible(available);
        wideNavigationButton.setManaged(available);
        wideNavigationButton.setIconPath(collapsed ? ICON_SHOW_NAVIGATION : ICON_HIDE_NAVIGATION);
        wideNavigationButton.setTooltipText(collapsed
                ? I18n.tr("autoShowPlaybackNavigation")
                : I18n.tr("autoHidePlaybackNavigation"));
        wideNavigationButton.getStyleClass().remove("bookmarks-quick-action-button-active");
        if (collapsed) {
            wideNavigationButton.getStyleClass().add("bookmarks-quick-action-button-active");
        }
    }

    private void updateParentalPauseButton() {
        Configuration configuration = ConfigurationService.getInstance().read();
        boolean paused = configuration != null && configuration.isPauseFiltering();
        parentalPauseButton.setTooltipText(paused
                ? "Filtering is paused. Resume parental lock restrictions"
                : "Filtering is active. Pause parental lock restrictions");
        parentalPauseButton.setIconPath(paused ? ICON_PARENTAL_UNLOCKED : ICON_PARENTAL_LOCK);
        parentalPauseButton.getStyleClass().remove("bookmarks-quick-action-button-active");
        parentalPauseButton.getStyleClass().remove("bookmarks-quick-action-button-lock-ok");
        parentalPauseButton.getStyleClass().remove("bookmarks-quick-action-button-lock-paused");
        if (paused) {
            parentalPauseButton.getStyleClass().add("bookmarks-quick-action-button-lock-paused");
        } else {
            parentalPauseButton.getStyleClass().add("bookmarks-quick-action-button-lock-ok");
        }
    }

    private Button createPlainTextModeButton() {
        Button button = new Button();
        button.getStyleClass().addAll("bookmarks-quick-action-button", "plain-text-mode-header-button");
        button.setFocusTraversable(true);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setMaxWidth(Region.USE_PREF_SIZE);

        Label icon = new Label("1");
        icon.getStyleClass().add("plain-text-mode-quick-icon");
        icon.setMinSize(22, 22);
        icon.setPrefSize(22, 22);
        icon.setMaxSize(22, 22);
        button.setGraphic(icon);

        Tooltip tooltip = new Tooltip();
        tooltip.setShowDelay(Duration.millis(250));
        tooltip.setHideDelay(Duration.millis(80));
        tooltip.setShowDuration(Duration.seconds(4));
        button.setTooltip(tooltip);
        button.setOnAction(_ -> togglePlainTextMode());
        return button;
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
        updatePlainTextModeButton();
    }

    private void updatePlainTextModeButton() {
        Configuration configuration = ConfigurationService.getInstance().read();
        boolean plainTextMode = configuration != null && !configuration.isEnableThumbnails();
        plainTextModeButton.getStyleClass().remove("bookmarks-quick-action-button-active");
        if (plainTextMode) {
            plainTextModeButton.getStyleClass().add("bookmarks-quick-action-button-active");
        }
        String tooltipText = plainTextMode
                ? I18n.tr("autoDisablePlainTextMode")
                : I18n.tr("autoEnablePlainTextMode");
        plainTextModeButton.setAccessibleText(tooltipText);
        if (plainTextModeButton.getTooltip() != null) {
            plainTextModeButton.getTooltip().setText(tooltipText);
        }
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

    private void toggleTheme() {
        if (themeToggleHandler != null) {
            themeToggleHandler.run();
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
}
