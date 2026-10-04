package com.uiptv.ui.util;

import com.uiptv.model.ThemeMode;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeManagerTest extends DbBackedUiTest {
    private static final int ZOOM_PERCENT = 100;

    @BeforeAll
    static void setUpJavaFx() throws Exception {
        FxTestSupport.initJavaFx();
    }

    @Test
    void lightModePinsTheLightPalette() throws Exception {
        Scene scene = runOnFxThread(() -> new Scene(new StackPane(), 800, 600));

        runOnFxThread(() -> {
            ThemeManager.applyTheme(scene, getClass(), ThemeMode.LIGHT, ZOOM_PERCENT);
            return null;
        });

        runOnFxThread(() -> {
            assertEquals(ColorScheme.LIGHT, scene.getPreferences().getColorScheme());
            // An explicit selection must not follow the operating system.
            assertFalse(scene.getPreferences().colorSchemeProperty().isBound());
            assertEquals(expectedStylesheet(false), singleStylesheet(scene));
            return null;
        });
    }

    @Test
    void darkModePinsTheDarkPalette() throws Exception {
        Scene scene = runOnFxThread(() -> new Scene(new StackPane(), 800, 600));

        runOnFxThread(() -> {
            ThemeManager.applyTheme(scene, getClass(), ThemeMode.DARK, ZOOM_PERCENT);
            return null;
        });

        runOnFxThread(() -> {
            assertEquals(ColorScheme.DARK, scene.getPreferences().getColorScheme());
            assertFalse(scene.getPreferences().colorSchemeProperty().isBound());
            assertEquals(expectedStylesheet(true), singleStylesheet(scene));
            return null;
        });
    }

    @Test
    void systemModeFollowsTheOperatingSystemColorScheme() throws Exception {
        Scene scene = runOnFxThread(() -> new Scene(new StackPane(), 800, 600));
        // Platform.getPreferences() is FX-thread-only, so read it where ThemeManager reads it.
        ColorScheme platformScheme = runOnFxThread(
                () -> Platform.getPreferences().getColorScheme());

        runOnFxThread(() -> {
            ThemeManager.applyTheme(scene, getClass(), ThemeMode.SYSTEM, ZOOM_PERCENT);
            return null;
        });

        runOnFxThread(() -> {
            assertTrue(scene.getPreferences().colorSchemeProperty().isBound(),
                    "System mode must bind the scene colour scheme to the platform preference");
            assertEquals(platformScheme, scene.getPreferences().getColorScheme());
            assertEquals(expectedStylesheet(platformScheme == ColorScheme.DARK), singleStylesheet(scene));
            return null;
        });
    }

    @Test
    void systemModeStillFollowsTheSystemWhenAppliedOffTheFxThread() throws Exception {
        Scene scene = runOnFxThread(() -> new Scene(new StackPane(), 800, 600));

        // Reading Platform.getPreferences() off the FX thread throws IllegalStateException, so an
        // off-thread caller must be marshalled rather than silently downgraded to the light palette.
        ThemeManager.applyTheme(scene, getClass(), ThemeMode.SYSTEM, ZOOM_PERCENT);

        ColorScheme platformScheme = runOnFxThread(
                () -> Platform.getPreferences().getColorScheme());
        runOnFxThread(() -> {
            assertTrue(scene.getPreferences().colorSchemeProperty().isBound());
            assertEquals(platformScheme, scene.getPreferences().getColorScheme());
            assertEquals(expectedStylesheet(platformScheme == ColorScheme.DARK), singleStylesheet(scene));
            return null;
        });
    }

    @Test
    void explicitModesAppliedOffTheFxThreadStillPinThePalette() throws Exception {
        Scene scene = runOnFxThread(() -> new Scene(new StackPane(), 800, 600));

        ThemeManager.applyTheme(scene, getClass(), ThemeMode.DARK, ZOOM_PERCENT);

        runOnFxThread(() -> {
            assertEquals(ColorScheme.DARK, scene.getPreferences().getColorScheme());
            assertFalse(scene.getPreferences().colorSchemeProperty().isBound());
            assertEquals(expectedStylesheet(true), singleStylesheet(scene));
            return null;
        });
    }

    @Test
    void selectingAnExplicitModeUnbindsTheSystemPreference() throws Exception {
        Scene scene = runOnFxThread(() -> new Scene(new StackPane(), 800, 600));

        runOnFxThread(() -> {
            ThemeManager.applyTheme(scene, getClass(), ThemeMode.SYSTEM, ZOOM_PERCENT);
            ThemeManager.applyTheme(scene, getClass(), ThemeMode.LIGHT, ZOOM_PERCENT);
            return null;
        });

        runOnFxThread(() -> {
            assertFalse(scene.getPreferences().colorSchemeProperty().isBound());
            assertEquals(ColorScheme.LIGHT, scene.getPreferences().getColorScheme());
            assertEquals(expectedStylesheet(false), singleStylesheet(scene));
            return null;
        });
    }

    @Test
    void systemModeReappliesTheStylesheetWhenTheColorSchemeChanges() throws Exception {
        Scene scene = runOnFxThread(() -> new Scene(new StackPane(), 800, 600));

        runOnFxThread(() -> {
            ThemeManager.applyTheme(scene, getClass(), ThemeMode.SYSTEM, ZOOM_PERCENT);
            // Stand in for the operating system pushing a new scheme: the scene property is the single
            // input the real binding feeds, so driving it directly exercises the same listener.
            scene.getPreferences().colorSchemeProperty().unbind();
            scene.getPreferences().setColorScheme(ColorScheme.DARK);
            return null;
        });

        runOnFxThread(() -> {
            assertEquals(expectedStylesheet(true), singleStylesheet(scene));
            return null;
        });
    }

    @Test
    void explicitModeIgnoresAColorSchemeChangeItDidNotAskFor() throws Exception {
        Scene scene = runOnFxThread(() -> new Scene(new StackPane(), 800, 600));

        runOnFxThread(() -> {
            ThemeManager.applyTheme(scene, getClass(), ThemeMode.LIGHT, ZOOM_PERCENT);
            scene.getPreferences().setColorScheme(ColorScheme.DARK);
            return null;
        });

        runOnFxThread(() -> {
            // The listener stays installed for the lifetime of the scene, so it has to ignore changes that
            // do not come from the system binding.
            assertEquals(expectedStylesheet(false), singleStylesheet(scene));
            return null;
        });
    }

    @Test
    void eachSceneTracksItsOwnSelection() throws Exception {
        Scene lightScene = runOnFxThread(() -> new Scene(new StackPane(), 400, 300));
        Scene darkScene = runOnFxThread(() -> new Scene(new StackPane(), 400, 300));

        runOnFxThread(() -> {
            ThemeManager.applyTheme(lightScene, getClass(), ThemeMode.LIGHT, ZOOM_PERCENT);
            ThemeManager.applyTheme(darkScene, getClass(), ThemeMode.DARK, ZOOM_PERCENT);
            return null;
        });

        runOnFxThread(() -> {
            // One manager, several windows: a single shared listener would leave one of them stale.
            assertEquals(expectedStylesheet(false), singleStylesheet(lightScene));
            assertEquals(expectedStylesheet(true), singleStylesheet(darkScene));
            return null;
        });
    }

    @Test
    void zoomIsPreservedOnEveryReapply() throws Exception {
        Scene scene = runOnFxThread(() -> new Scene(new StackPane(), 800, 600));

        runOnFxThread(() -> {
            ThemeManager.applyTheme(scene, getClass(), ThemeMode.DARK, 150);
            return null;
        });
        String darkAtOneFifty = runOnFxThread(() -> singleStylesheet(scene));

        runOnFxThread(() -> {
            ThemeManager.applyTheme(scene, getClass(), ThemeMode.DARK, 100);
            return null;
        });
        String darkAtOneHundred = runOnFxThread(() -> singleStylesheet(scene));

        runOnFxThread(() -> {
            assertFalse(darkAtOneFifty.equals(darkAtOneHundred), "Zoom level must stay part of the stylesheet");
            assertEquals(expectedStylesheet(true), darkAtOneHundred);
            return null;
        });
    }

    @Test
    void aNullSelectionFallsBackToTheLightTheme() throws Exception {
        Scene scene = runOnFxThread(() -> new Scene(new StackPane(), 400, 300));

        runOnFxThread(() -> {
            ThemeManager.applyTheme(scene, getClass(), (ThemeMode) null, ZOOM_PERCENT);
            return null;
        });

        runOnFxThread(() -> {
            assertEquals(ColorScheme.LIGHT, scene.getPreferences().getColorScheme());
            assertEquals(expectedStylesheet(false), singleStylesheet(scene));
            return null;
        });
    }

    @Test
    void currentThemeIsPublishedForSecondaryWindows() throws Exception {
        Scene scene = runOnFxThread(() -> new Scene(new StackPane(), 400, 300));

        runOnFxThread(() -> {
            ThemeManager.applyTheme(scene, getClass(), ThemeMode.DARK, ZOOM_PERCENT);
            return null;
        });

        // Settings popups and detached log windows copy this URL instead of resolving the theme again.
        assertEquals(expectedStylesheet(true), ThemeManager.getCurrentTheme());
        assertNotNull(ThemeManager.getCurrentTheme());
    }

    private String expectedStylesheet(boolean darkTheme) {
        return ThemeStylesheetResolver.resolveStylesheetUrl(getClass(), darkTheme, ZOOM_PERCENT);
    }

    private String singleStylesheet(Scene scene) {
        assertEquals(1, scene.getStylesheets().size(), "Theme application must leave exactly one stylesheet");
        return scene.getStylesheets().get(0);
    }
}
