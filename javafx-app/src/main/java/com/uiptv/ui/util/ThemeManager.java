package com.uiptv.ui.util;

import com.uiptv.model.ThemeMode;
import com.uiptv.util.AppLog;
import com.uiptv.widget.AppFonts;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.scene.Scene;

/**
 * Single owner of the application stylesheet.
 * <p>
 * Three states are supported, matching {@link ThemeMode}:
 * <ul>
 *     <li>{@code LIGHT} / {@code DARK}: the scene colour-scheme preference is <em>unbound</em> and pinned, so
 *     operating system changes cannot move the application off the chosen palette.</li>
 *     <li>{@code SYSTEM}: the scene colour-scheme preference is <em>bound</em> to
 *     {@link Platform#getPreferences()}'s colour scheme. The binding pushes operating system changes into
 *     the scene, the per-scene listener installed here re-applies the stylesheet, and no manual listener
 *     bookkeeping is needed.</li>
 * </ul>
 * The stylesheet is never swapped by hand. {@link #applyResolvedTheme} keeps going through
 * {@link ThemeStylesheetResolver} so the data-URL stylesheet, the zoom override, the RTL orientation and
 * {@link StyleClassDecorator} all stay consistent - clearing the stylesheet list and adding a raw resource
 * URL would silently drop the zoom override and the decoration pass.
 * <p>
 * State is kept per {@link Scene} in the scene's own property map, so the main stage, detached log windows
 * and settings popups each track their own anchor, zoom and follow-mode, and everything is collectable with
 * its scene.
 */
public final class ThemeManager {
    private static final String SCENE_STATE_KEY = ThemeManager.class.getName() + ".sceneState";
    private static volatile String currentTheme;
    private static volatile boolean systemPreferencesUnavailableLogged;

    private ThemeManager() {
    }

    /** Per-scene bookkeeping. Stored in {@link Scene#getProperties()}, so it dies with the scene. */
    private record SceneThemeState(Class<?> resourceAnchor, int zoomPercent, boolean followsSystem) {
    }

    /**
     * Stylesheet URL most recently applied, used by secondary windows that copy the current theme.
     */
    public static String getCurrentTheme() {
        return currentTheme;
    }

    /**
     * Applies the three-state mode to a scene. Safe to call from any thread; when called off the JavaFX
     * application thread the scene work is marshalled with {@link Platform#runLater(Runnable)} because both
     * the scene preference and {@link Platform#getPreferences()} are FX-thread-only, so the swap then
     * completes on the next pulse instead of before this call returns.
     */
    public static void applyTheme(Scene scene, Class<?> resourceAnchor, ThemeMode mode, int zoomPercent) {
        if (scene == null) {
            return;
        }
        ThemeMode requested = mode == null ? ThemeMode.LIGHT : mode;
        runOnFxThread(() -> applyOnFxThread(scene, resourceAnchor, requested, zoomPercent));
    }

    private static void applyOnFxThread(Scene scene, Class<?> resourceAnchor, ThemeMode mode, int zoomPercent) {
        if (scene.getRoot() == null) {
            return;
        }
        ObjectProperty<ColorScheme> sceneColorScheme = scene.getPreferences().colorSchemeProperty();
        ensureSceneListener(scene, resourceAnchor, zoomPercent);

        if (mode.isSystem()) {
            ReadOnlyObjectProperty<ColorScheme> systemColorScheme = systemColorScheme();
            if (systemColorScheme != null) {
                storeState(scene, new SceneThemeState(resourceAnchor, zoomPercent, true));
                // Native binding: later operating system changes arrive through the scene property and the
                // listener installed above re-applies the stylesheet.
                sceneColorScheme.bind(systemColorScheme);
                applyResolvedTheme(scene, resourceAnchor, zoomPercent, systemColorScheme.get());
                return;
            }
            // No operating system signal (platform does not report one): stay on an explicit palette rather
            // than following nothing.
            mode = ThemeMode.LIGHT;
        }

        storeState(scene, new SceneThemeState(resourceAnchor, zoomPercent, false));
        sceneColorScheme.unbind();
        sceneColorScheme.set(mode == ThemeMode.DARK ? ColorScheme.DARK : ColorScheme.LIGHT);
        applyResolvedTheme(scene, resourceAnchor, zoomPercent, sceneColorScheme.get());
    }

    /**
     * Runs the action on the JavaFX application thread. Work triggered by JavaFX itself (property
     * invalidation, user interaction) already runs there and is executed inline, which keeps the
     * stylesheets list and the scene graph consistent within the current event. Anything arriving from
     * another thread - a background service, an operating system callback delivered off-thread - is
     * marshalled with {@link Platform#runLater(Runnable)}.
     */
    public static void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        Platform.runLater(action);
    }

    private static void applyResolvedTheme(Scene scene, Class<?> resourceAnchor, int zoomPercent, ColorScheme colorScheme) {
        runOnFxThread(() -> {
            if (scene.getRoot() == null) {
                return;
            }
            AppFonts.load();
            currentTheme = ThemeStylesheetResolver.resolveStylesheetUrl(
                    resourceAnchor,
                    colorScheme == ColorScheme.DARK,
                    zoomPercent
            );
            scene.getStylesheets().clear();
            scene.getStylesheets().add(currentTheme);
            scene.getRoot().styleProperty().unbind();
            scene.getRoot().setStyle(ThemeStylesheetResolver.buildSceneRootStyle(zoomPercent));
            UiI18n.applySceneOrientation(scene);
            StyleClassDecorator.decorate(scene.getRoot());
        });
    }

    private static void ensureSceneListener(Scene scene, Class<?> resourceAnchor, int zoomPercent) {
        if (scene.getProperties().get(SCENE_STATE_KEY) != null) {
            return;
        }
        storeState(scene, new SceneThemeState(resourceAnchor, zoomPercent, false));
        scene.getPreferences().colorSchemeProperty().addListener((_, _, newColorScheme) -> {
            SceneThemeState state = currentState(scene);
            // An explicit Light/Dark selection pins the scene property, so ignore anything that would move it.
            if (state == null || !state.followsSystem()) {
                return;
            }
            applyResolvedTheme(scene, state.resourceAnchor(), state.zoomPercent(), newColorScheme);
        });
    }

    private static SceneThemeState currentState(Scene scene) {
        Object state = scene.getProperties().get(SCENE_STATE_KEY);
        return state instanceof SceneThemeState sceneThemeState ? sceneThemeState : null;
    }

    private static void storeState(Scene scene, SceneThemeState state) {
        scene.getProperties().put(SCENE_STATE_KEY, state);
    }

    private static ReadOnlyObjectProperty<ColorScheme> systemColorScheme() {
        try {
            return Platform.getPreferences().colorSchemeProperty();
        } catch (IllegalStateException | UnsupportedOperationException | NullPointerException e) {
            // IllegalStateException: toolkit not started yet (early bootstrap, headless tests).
            // UnsupportedOperationException: the platform does not implement the preference.
            if (!systemPreferencesUnavailableLogged) {
                systemPreferencesUnavailableLogged = true;
                AppLog.addWarningLog(ThemeManager.class,
                        "System colour scheme unavailable, system theme falls back to the light palette: " + e);
            }
            return null;
        }
    }
}
