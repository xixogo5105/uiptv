package com.uiptv.widget;

import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.util.I18n;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppHeaderActionsTest extends DbBackedUiTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        FxTestSupport.initJavaFx();
    }

    @AfterEach
    void resetNavigationController() {
        AppNavigationController.reset();
    }

    @Test
    void directNavigationButtonsFollowRequestedOrderAndUseController() throws Exception {
        AtomicReference<AppNavigationController.Target> selectedTarget = new AtomicReference<>();
        EnumMap<AppNavigationController.Target, Runnable> actionsMap = new EnumMap<>(AppNavigationController.Target.class);
        actionsMap.put(AppNavigationController.Target.BOOKMARKS, () -> selectedTarget.set(AppNavigationController.Target.BOOKMARKS));
        actionsMap.put(AppNavigationController.Target.ACCOUNTS, () -> selectedTarget.set(AppNavigationController.Target.ACCOUNTS));
        actionsMap.put(AppNavigationController.Target.WATCHING_NOW, () -> selectedTarget.set(AppNavigationController.Target.WATCHING_NOW));
        AppNavigationController.configure(actionsMap, AppNavigationController.Target.BOOKMARKS);

        AppHeaderActions actions = runOnFxThread(() -> new AppHeaderActions(null, null, null));

        assertEquals(I18n.tr("autoFavorite"), runOnFxThread(() -> buttonAt(actions, 0).getAccessibleText()));
        assertEquals(I18n.tr("autoAccount"), runOnFxThread(() -> buttonAt(actions, 1).getAccessibleText()));
        assertEquals(I18n.tr("autoWatchingNow"), runOnFxThread(() -> buttonAt(actions, 2).getAccessibleText()));
        assertEquals(I18n.tr("autoSettings"), runOnFxThread(() -> buttonAt(actions, 3).getAccessibleText()));

        runOnFxThread(() -> {
            buttonAt(actions, 1).fire();
            return null;
        });

        assertEquals(AppNavigationController.Target.ACCOUNTS, selectedTarget.get());
    }

    @Test
    void gearButtonReflectsParentalPauseState() throws Exception {
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setPauseFiltering(false);
        ConfigurationService.getInstance().save(configuration);

        AppHeaderActions actions = runOnFxThread(() -> new AppHeaderActions(null, null, null));
        Button gearButton = runOnFxThread(() -> buttonAt(actions, 3));

        assertTrue(runOnFxThread(() -> gearButton.getStyleClass().contains("bookmarks-quick-action-button-lock-ok")));
        assertFalse(runOnFxThread(() -> gearButton.getStyleClass().contains("bookmarks-quick-action-button-lock-paused")));

        configuration = ConfigurationService.getInstance().read();
        configuration.setPauseFiltering(true);
        ConfigurationService.getInstance().save(configuration);
        runOnFxThread(() -> {
            actions.refreshState();
            return null;
        });

        assertFalse(runOnFxThread(() -> gearButton.getStyleClass().contains("bookmarks-quick-action-button-lock-ok")));
        assertTrue(runOnFxThread(() -> gearButton.getStyleClass().contains("bookmarks-quick-action-button-lock-paused")));
    }

    @Test
    void gearMenuUsesRequestedOptionOrder() throws Exception {
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setEnableThumbnails(true);
        configuration.setPauseFiltering(false);
        ConfigurationService.getInstance().save(configuration);

        List<String> labels = runOnFxThread(() -> {
            AppHeaderActions actions = new AppHeaderActions(null, null, null);
            ContextMenu menu = actions.createGearMenu();
            return menu.getItems().stream().map(MenuItem::getText).toList();
        });

        assertEquals(List.of(
                I18n.tr("autoSettings"),
                I18n.tr("autoImportBulkAccounts"),
                I18n.tr("autoLogs"),
                "Pause parental lock restrictions",
                I18n.tr("autoEnablePlainTextMode"),
                I18n.tr("autoHelp"),
                I18n.tr("autoAbout")
        ), labels);
    }

    @Test
    void plainTextModeMenuItemTogglesExistingThumbnailConfigurationFlag() throws Exception {
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setEnableThumbnails(true);
        ConfigurationService.getInstance().save(configuration);

        MenuItem plainTextItem = runOnFxThread(() -> {
            AppHeaderActions actions = new AppHeaderActions(null, null, null);
            return actions.createGearMenu().getItems().get(4);
        });

        runOnFxThread(() -> {
            plainTextItem.fire();
            return null;
        });

        assertFalse(ConfigurationService.getInstance().read().isEnableThumbnails());
    }

    private static Button buttonAt(AppHeaderActions actions, int index) {
        return (Button) actions.getChildren().get(index);
    }
}
