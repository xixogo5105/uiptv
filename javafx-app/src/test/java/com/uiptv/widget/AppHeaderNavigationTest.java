package com.uiptv.widget;

import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.util.I18n;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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

class AppHeaderNavigationTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        FxTestSupport.initJavaFx();
    }

    @AfterEach
    void resetSharedControllers() {
        AppNavigationController.reset();
    }

    @Test
    void navigationButtonsFollowWebHeaderOrderAndUseController() throws Exception {
        AtomicReference<AppNavigationController.Target> selectedTarget = new AtomicReference<>();
        EnumMap<AppNavigationController.Target, Runnable> actionsMap = new EnumMap<>(AppNavigationController.Target.class);
        actionsMap.put(AppNavigationController.Target.BOOKMARKS, () -> selectedTarget.set(AppNavigationController.Target.BOOKMARKS));
        actionsMap.put(AppNavigationController.Target.ACCOUNTS, () -> selectedTarget.set(AppNavigationController.Target.ACCOUNTS));
        actionsMap.put(AppNavigationController.Target.WATCHING_NOW, () -> selectedTarget.set(AppNavigationController.Target.WATCHING_NOW));
        AppNavigationController.configure(actionsMap, AppNavigationController.Target.BOOKMARKS);

        AppHeaderNavigation navigation = runOnFxThread(() -> new AppHeaderNavigation(new Label("UIPTV")));

        assertEquals(List.of("Bookmarks", "Accounts", "Watching"), runOnFxThread(() -> navigationButtons(navigation).stream()
                .map(Button::getText)
                .toList()));
        assertEquals(I18n.tr("autoFavorite"), runOnFxThread(() -> navigationButtons(navigation).get(0).getAccessibleText()));
        assertEquals(I18n.tr("autoAccount"), runOnFxThread(() -> navigationButtons(navigation).get(1).getAccessibleText()));
        assertEquals(I18n.tr("autoWatchingNow"), runOnFxThread(() -> navigationButtons(navigation).get(2).getAccessibleText()));
        assertFalse(runOnFxThread(() -> hasStyleClass(navigation, "app-header-brand-mark")));

        runOnFxThread(() -> {
            navigationButtons(navigation).get(1).fire();
            return null;
        });

        assertEquals(AppNavigationController.Target.ACCOUNTS, selectedTarget.get());
    }

    @Test
    void compactModeKeepsIconsButHidesLabels() throws Exception {
        AppHeaderNavigation navigation = runOnFxThread(() -> new AppHeaderNavigation(new Label("UIPTV")));

        runOnFxThread(() -> {
            navigation.setCompact(true);
            return null;
        });

        for (Button button : runOnFxThread(() -> navigationButtons(navigation))) {
            assertEquals("", runOnFxThread(button::getText));
            assertTrue(runOnFxThread(() -> button.getStyleClass().contains("app-header-nav-button-icon-only")));
        }
    }

    @Test
    void secondaryTargetsDoNotSelectMainNavigationButtons() throws Exception {
        for (AppNavigationController.Target target : List.of(
                AppNavigationController.Target.SETTINGS,
                AppNavigationController.Target.IMPORT,
                AppNavigationController.Target.LOGS
        )) {
            AppNavigationController.configure(new EnumMap<>(AppNavigationController.Target.class), target);
            AppHeaderNavigation navigation = runOnFxThread(() -> new AppHeaderNavigation(new Label("UIPTV")));

            assertFalse(runOnFxThread(() -> hasActiveNavigationButton(navigation)));
        }
    }

    @Test
    void selectionCanBeDisabledForSecondaryPageHeaders() throws Exception {
        AppNavigationController.configure(new EnumMap<>(AppNavigationController.Target.class), AppNavigationController.Target.BOOKMARKS);
        AppHeaderNavigation navigation = runOnFxThread(() -> new AppHeaderNavigation(new Label("UIPTV")));

        assertTrue(runOnFxThread(() -> hasActiveNavigationButton(navigation)));

        runOnFxThread(() -> {
            navigation.setSelectionEnabled(false);
            return null;
        });

        assertFalse(runOnFxThread(() -> hasActiveNavigationButton(navigation)));
    }

    @Test
    void previousNavigationSelectionIsClearedAfterSceneRegistration() throws Exception {
        AppNavigationController.configure(new EnumMap<>(AppNavigationController.Target.class), AppNavigationController.Target.BOOKMARKS);
        AppHeaderNavigation navigation = runOnFxThread(() -> {
            AppHeaderNavigation headerNavigation = new AppHeaderNavigation(new Label("UIPTV"));
            new Scene(headerNavigation, 800, 72);
            return headerNavigation;
        });

        assertEquals(1L, runOnFxThread(() -> activeStyleCount(navigationButtons(navigation).get(0))));

        runOnFxThread(() -> {
            AppNavigationController.setCurrentTarget(AppNavigationController.Target.ACCOUNTS);
            return null;
        });
        FxTestSupport.waitForFxEvents();

        assertEquals(List.of(0L, 1L, 0L), runOnFxThread(() -> navigationButtons(navigation).stream()
                .map(AppHeaderNavigationTest::activeStyleCount)
                .toList()));
    }

    private static List<Button> navigationButtons(AppHeaderNavigation navigation) {
        return findDescendants(navigation, Button.class).stream()
                .filter(button -> button.getStyleClass().contains("app-header-nav-button"))
                .toList();
    }

    private static <T extends Node> List<T> findDescendants(Node root, Class<T> type) {
        List<T> matches = new java.util.ArrayList<>();
        collectDescendants(root, type, matches);
        return matches;
    }

    private static <T extends Node> void collectDescendants(Node root, Class<T> type, List<T> matches) {
        if (type.isInstance(root)) {
            matches.add(type.cast(root));
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectDescendants(child, type, matches);
            }
        }
    }

    private static boolean hasStyleClass(Node root, String styleClass) {
        if (root.getStyleClass().contains(styleClass)) {
            return true;
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (hasStyleClass(child, styleClass)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasActiveNavigationButton(AppHeaderNavigation navigation) {
        return navigationButtons(navigation).stream()
                .anyMatch(button -> button.getStyleClass().contains("app-header-nav-button-active"));
    }

    private static long activeStyleCount(Button button) {
        return button.getStyleClass().stream()
                .filter("app-header-nav-button-active"::equals)
                .count();
    }
}
