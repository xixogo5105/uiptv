package com.uiptv.widget;

import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationChangeListener;
import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.AboutUI;
import com.uiptv.ui.FilterLockDialogs;
import com.uiptv.ui.util.UiServerUrlUtil;
import com.uiptv.util.I18n;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import static com.uiptv.widget.UIptvAlert.showMessageAlert;

public class AppHeaderActions extends HBox {
    private static final String REPORT_BUG_URL = "https://github.com/xixogo5105/uiptv/issues";
    private static final String GUIDE_URL = "https://github.com/xixogo5105/uiptv/blob/main/GUIDE.md";
    private static final String FILTER_LOCK_UNLOCK_MANAGE_FILTERS_REASON = "filterLockUnlockManageFiltersReason";
    private static final String ICON_ABOUT = "M11 17H13V11H11V17ZM11 9H13V7H11V9ZM12 2C6.48 2 2 6.48 2 12S6.48 22 12 22 22 17.52 22 12 17.52 2 12 2ZM12 20C7.59 20 4 16.41 4 12S7.59 4 12 4 20 7.59 20 12 16.41 20 12 20Z";
    private static final String ICON_BUG = "M20 8H17.19C16.74 7.22 16.12 6.55 15.38 6.04L17 4.41 15.59 3 13.89 4.7C13.29 4.52 12.66 4.43 12 4.43S10.71 4.52 10.11 4.7L8.41 3 7 4.41 8.62 6.04C7.88 6.55 7.26 7.22 6.81 8H4V10H6.09C6.03 10.33 6 10.66 6 11V12H4V14H6V15C6 15.34 6.03 15.67 6.09 16H4V18H6.81C7.84 19.79 9.77 21 12 21S16.16 19.79 17.19 18H20V16H17.91C17.97 15.67 18 15.34 18 15V14H20V12H18V11C18 10.66 17.97 10.33 17.91 10H20V8ZM9 16.5C8.45 16.5 8 16.05 8 15.5S8.45 14.5 9 14.5 10 14.95 10 15.5 9.55 16.5 9 16.5ZM15 16.5C14.45 16.5 14 16.05 14 15.5S14.45 14.5 15 14.5 16 14.95 16 15.5 15.55 16.5 15 16.5ZM16 12H8V11C8 8.79 9.79 7 12 7S16 8.79 16 11V12Z";
    private static final String ICON_HELP = "M11 18H13V16H11V18ZM12 2C6.48 2 2 6.48 2 12S6.48 22 12 22 22 17.52 22 12 17.52 2 12 2ZM12 20C7.59 20 4 16.41 4 12S7.59 4 12 4 20 7.59 20 12 16.41 20 12 20ZM12 6C9.79 6 8 7.79 8 10H10C10 8.9 10.9 8 12 8S14 8.9 14 10C14 12 11 11.75 11 15H13C13 12.75 16 12.5 16 10 16 7.79 14.21 6 12 6Z";
    private static final String ICON_THEME = "M12 3C7.03 3 3 7.03 3 12S7.03 21 12 21C15.31 21 18.2 19.21 19.76 16.54 18.86 16.84 17.91 17 16.92 17 11.95 17 7.92 12.97 7.92 8 7.92 6.39 8.34 4.87 9.08 3.56 9.98 3.2 10.96 3 12 3Z";
    private static final String ICON_PARENTAL_LOCK = "M12 17C13.1 17 14 16.1 14 15S13.1 13 12 13 10 13.9 10 15 10.9 17 12 17ZM18 8H17V6C17 3.24 14.76 1 12 1S7 3.24 7 6V8H6C4.9 8 4 8.9 4 10V20C4 21.1 4.9 22 6 22H18C19.1 22 20 21.1 20 20V10C20 8.9 19.1 8 18 8ZM9 6C9 4.34 10.34 3 12 3S15 4.34 15 6V8H9V6ZM18 20H6V10H18V20Z";
    private static final String ICON_PARENTAL_UNLOCKED = "M12 17C13.1 17 14 16.1 14 15S13.1 13 12 13 10 13.9 10 15 10.9 17 12 17ZM18 8H9V6C9 4.34 10.34 3 12 3 13.09 3 14.05 3.58 14.58 4.45L16.32 3.45C15.44 1.99 13.84 1 12 1 9.24 1 7 3.24 7 6V8H6C4.9 8 4 8.9 4 10V20C4 21.1 4.9 22 6 22H18C19.1 22 20 21.1 20 20V10C20 8.9 19.1 8 18 8ZM18 20H6V10H18V20Z";

    private final HostServices hostServices;
    private final Runnable themeToggleHandler;
    private final Runnable parentalPauseChangedHandler;
    private final IconActionButton parentalPauseButton =
            new IconActionButton("Pause parental lock restrictions", ICON_PARENTAL_LOCK, this::toggleParentalPause);
    private final ConfigurationChangeListener configurationChangeListener =
            _ -> Platform.runLater(this::updateParentalPauseButton);
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
                new IconActionButton("Report a bug", ICON_BUG, () -> openExternalUrl(REPORT_BUG_URL)),
                new IconActionButton(I18n.tr("autoHelp"), ICON_HELP, () -> openExternalUrl(GUIDE_URL)),
                parentalPauseButton,
                new IconActionButton("Toggle theme", ICON_THEME, this::toggleTheme)
        );
        updateParentalPauseButton();
        sceneProperty().addListener((_, oldScene, newScene) -> {
            if (oldScene == null && newScene != null) {
                registerConfigurationChangeListener();
                updateParentalPauseButton();
            } else if (oldScene != null && newScene == null) {
                unregisterConfigurationChangeListener();
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
        updateParentalPauseButton();
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
