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

import java.util.List;
import java.util.Objects;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppHeaderActionsTest extends DbBackedUiTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        FxTestSupport.initJavaFx();
    }

    @AfterEach
    void resetSharedControllers() {
        AppNavigationController.reset();
    }

    @Test
    void rightSideActionsOnlyShowSettingsWhenPlayerNavigationUnavailable() throws Exception {
        AppHeaderActions actions = runOnFxThread(() -> new AppHeaderActions(null, null, null));

        assertEquals(1, runOnFxThread(() -> visibleButtons(actions).size()));
        assertEquals(I18n.tr("autoSettings"), runOnFxThread(() -> visibleButtonAt(actions, 0).getAccessibleText()));
    }

    @Test
    void gearButtonReflectsParentalPauseState() throws Exception {
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setPauseFiltering(false);
        ConfigurationService.getInstance().save(configuration);

        AppHeaderActions actions = runOnFxThread(() -> new AppHeaderActions(null, null, null));
        Button gearButton = runOnFxThread(() -> visibleButtonAt(actions, 0));

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
        new com.uiptv.application.EpgSettingsApplicationService().setEnabled(false);
        awaitEpg(false);
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setEnableThumbnails(true);
        configuration.setPauseFiltering(false);
        ConfigurationService.getInstance().save(configuration);

        List<String> labels = runOnFxThread(() -> {
            AppHeaderActions actions = new AppHeaderActions(null, null, null);
            ContextMenu menu = actions.createGearMenu();
            return menu.getItems().stream()
                    .map(MenuItem::getText)
                    .filter(Objects::nonNull)
                    .toList();
        });

        assertEquals(List.of(
                I18n.tr("autoSettings"),
                I18n.tr("autoImportBulkAccounts"),
                I18n.tr("autoReloadAccountsCache"),
                I18n.tr("autoLogs"),
                "Disable parental lock access",
                I18n.tr("autoEnablePlainTextMode"),
                I18n.tr("epgEnableMode"),
                I18n.tr("autoEnableDarkTheme"),
                I18n.tr("autoStayOnTop"),
                I18n.tr("configLightweightModeSwitchTitle"),
                I18n.tr("autoHelp"),
                I18n.tr("autoCheckForUpdates2"),
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
            return actions.createGearMenu().getItems().stream()
                    .filter(item -> I18n.tr("autoEnablePlainTextMode").equals(item.getText()))
                    .findFirst()
                    .orElseThrow();
        });

        runOnFxThread(() -> {
            plainTextItem.fire();
            return null;
        });

        assertFalse(ConfigurationService.getInstance().read().isEnableThumbnails());
    }

    @Test
    void epgGearActionPersistsTheSettingsFlagWithoutReplacingVirtualGrids() throws Exception {
        new com.uiptv.application.EpgSettingsApplicationService().setEnabled(false);
        awaitEpg(false);
        var root = runOnFxThread(() -> {
            var bookmarks = new com.uiptv.ui.BookmarkChannelListUI(null, null);
            var channels = new com.uiptv.ui.ChannelListUI(new com.uiptv.model.Account(), "All", "all", com.uiptv.model.Account.AccountAction.itv);
            var movies = new com.uiptv.ui.ChannelListUI(new com.uiptv.model.Account(), "Movies", "movies", com.uiptv.model.Account.AccountAction.vod);
            var container = new javafx.scene.layout.VBox(bookmarks, channels, movies);
            new javafx.scene.Scene(container, 900, 800);
            return container;
        });
        try {
            runOnFxThread(() -> {
                var actions = new AppHeaderActions(null, null, null);
                var item = actions.createGearMenu().getItems().stream().filter(entry -> I18n.tr("epgEnableMode").equals(entry.getText())).findFirst().orElseThrow();
                assertFalse(item.isDisable()); item.fire(); return null;
            });
            awaitEpg(true); assertTrue(ConfigurationService.getInstance().read().isEpgEnabled());
            runOnFxThread(() -> {
                root.applyCss(); root.layout();
                assertTrue(root.getChildren().get(0).lookupAll(".live-guide").isEmpty());
                assertTrue(root.getChildren().get(1).lookupAll(".live-guide").isEmpty());
                assertTrue(root.getChildren().get(2).lookupAll(".live-guide").isEmpty());
                var actions = new AppHeaderActions(null, null, null);
                actions.createGearMenu().getItems().stream().filter(item -> I18n.tr("epgDisableMode").equals(item.getText())).findFirst().orElseThrow().fire();
                return null;
            });
            awaitEpg(false); assertFalse(ConfigurationService.getInstance().read().isEpgEnabled());
            runOnFxThread(() -> {
                assertTrue(root.getChildren().get(0).lookupAll(".live-guide").isEmpty());
                assertTrue(root.getChildren().get(1).lookupAll(".live-guide").isEmpty()); return null;
            });
        } finally {
            runOnFxThread(() -> {
                ((com.uiptv.ui.ChannelListUI) root.getChildren().get(1)).dispose();
                ((com.uiptv.ui.ChannelListUI) root.getChildren().get(2)).dispose(); root.getChildren().clear(); return null;
            });
        }
    }
    private void awaitEpg(boolean expected) throws Exception {
        for (int i = 0; i < 200 && runOnFxThread(com.uiptv.ui.EpgModeUI::isFeatureEnabled) != expected; i++) Thread.sleep(10);
        assertEquals(expected, runOnFxThread(com.uiptv.ui.EpgModeUI::isFeatureEnabled));
    }

    private static Button visibleButtonAt(AppHeaderActions actions, int index) {
        return visibleButtons(actions)
                .get(index);
    }

    private static List<Button> visibleButtons(AppHeaderActions actions) {
        return actions.getChildren().stream()
                .filter(node -> node.isVisible() && node.isManaged())
                .map(Button.class::cast)
                .toList();
    }
}
