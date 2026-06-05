package com.uiptv.widget;

import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static com.uiptv.testsupport.FxTestSupport.waitForFxEvents;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppHeaderActionsTest extends DbBackedUiTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        FxTestSupport.initJavaFx();
    }

    @Test
    void themeButtonRunsThemeHandler() throws Exception {
        AtomicInteger themeCount = new AtomicInteger();
        AppHeaderActions actions = runOnFxThread(() -> new AppHeaderActions(null, themeCount::incrementAndGet, null));

        runOnFxThread(() -> {
            buttonByAccessibleText(actions, "Toggle theme").fire();
            return null;
        });

        assertEquals(1, themeCount.get());
    }

    @Test
    void plainTextModeButtonTogglesExistingThumbnailConfigurationFlag() throws Exception {
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setEnableThumbnails(true);
        ConfigurationService.getInstance().save(configuration);

        AppHeaderActions actions = runOnFxThread(() -> new AppHeaderActions(null, null, null));
        Button plainTextButton = runOnFxThread(() -> buttonByStyle(actions, "plain-text-mode-header-button"));

        assertFalse(runOnFxThread(() -> plainTextButton.getStyleClass().contains("bookmarks-quick-action-button-active")));

        runOnFxThread(() -> {
            plainTextButton.fire();
            return null;
        });

        assertFalse(ConfigurationService.getInstance().read().isEnableThumbnails());
        assertTrue(runOnFxThread(() -> plainTextButton.getStyleClass().contains("bookmarks-quick-action-button-active")));

        runOnFxThread(() -> {
            plainTextButton.fire();
            return null;
        });

        assertTrue(ConfigurationService.getInstance().read().isEnableThumbnails());
        assertFalse(runOnFxThread(() -> plainTextButton.getStyleClass().contains("bookmarks-quick-action-button-active")));
    }

    @Test
    void wideNavigationButtonReflectsAvailabilityAndCollapsedState() throws Exception {
        AtomicInteger toggleCount = new AtomicInteger();
        Runnable toggleHandler = toggleCount::incrementAndGet;
        AppHeaderActions actions = runOnFxThread(() -> new AppHeaderActions(null, null, null));
        Button wideButton = runOnFxThread(() -> (Button) actions.getChildren().get(1));

        runOnFxThread(() -> {
            WidePlayerNavigationControl.configure(true, false, toggleHandler);
            actions.refreshState();
            wideButton.fire();
            WidePlayerNavigationControl.configure(true, true, toggleHandler);
            actions.refreshState();
            return null;
        });

        assertEquals(1, toggleCount.get());
        assertTrue(runOnFxThread(wideButton::isVisible));
        assertTrue(runOnFxThread(wideButton::isManaged));
        assertTrue(runOnFxThread(() -> wideButton.getStyleClass().contains("bookmarks-quick-action-button-active")));

        runOnFxThread(() -> {
            WidePlayerNavigationControl.reset(toggleHandler);
            actions.refreshState();
            return null;
        });

        assertFalse(runOnFxThread(wideButton::isVisible));
        assertFalse(runOnFxThread(wideButton::isManaged));
    }

    @Test
    void sceneLifecycleRegistersListenersAndRefreshesPlainTextStateFromConfigurationChange() throws Exception {
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setEnableThumbnails(true);
        ConfigurationService.getInstance().save(configuration);

        AppHeaderActions actions = runOnFxThread(() -> new AppHeaderActions(null, null, null));
        Button plainTextButton = runOnFxThread(() -> buttonByStyle(actions, "plain-text-mode-header-button"));
        StackPane root = runOnFxThread(() -> {
            StackPane pane = new StackPane(actions);
            new Scene(pane, 420, 80);
            return pane;
        });
        waitForFxEvents();

        Configuration updated = ConfigurationService.getInstance().read();
        updated.setEnableThumbnails(false);
        ConfigurationService.getInstance().save(updated);
        waitForFxEvents();

        assertTrue(runOnFxThread(() -> plainTextButton.getStyleClass().contains("bookmarks-quick-action-button-active")));

        runOnFxThread(() -> {
            root.getChildren().clear();
            return null;
        });
        waitForFxEvents();
    }

    private static Button buttonByAccessibleText(AppHeaderActions actions, String accessibleText) {
        return actions.getChildren().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> accessibleText.equals(button.getAccessibleText()))
                .findFirst()
                .orElseThrow();
    }

    private static Button buttonByStyle(AppHeaderActions actions, String styleClass) {
        return actions.getChildren().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> button.getStyleClass().contains(styleClass))
                .findFirst()
                .orElseThrow();
    }
}
