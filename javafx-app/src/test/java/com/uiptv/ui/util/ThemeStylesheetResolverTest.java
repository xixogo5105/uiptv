package com.uiptv.ui.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeStylesheetResolverTest {

    @Test
    void getDefaultResourcePath_resolvesCorrectPaths() {
        assertEquals("/application.css", ThemeStylesheetResolver.getDefaultResourcePath(false));
        assertEquals("/dark-application.css", ThemeStylesheetResolver.getDefaultResourcePath(true));
    }

    @Test
    void readDefaultStylesheetContent_loadsCssFromResources() throws Exception {
        String lightContent = ThemeStylesheetResolver.readDefaultStylesheetContent(getClass(), false);
        assertTrue(lightContent.contains(".root"));
        assertTrue(lightContent.contains("-fx-font-size"));

        String darkContent = ThemeStylesheetResolver.readDefaultStylesheetContent(getClass(), true);
        assertTrue(darkContent.contains(".root"));
        assertTrue(darkContent.contains("-fx-base"));
    }

    @Test
    void profileJsonTextAreaOverridesFocusedAccentOutline() throws Exception {
        String lightContent = ThemeStylesheetResolver.readDefaultStylesheetContent(getClass(), false);
        String darkContent = ThemeStylesheetResolver.readDefaultStylesheetContent(getClass(), true);

        assertProfileJsonTextAreaFocusOverride(lightContent);
        assertProfileJsonTextAreaFocusOverride(darkContent);
    }

    @Test
    void buildSceneRootStyle_appliesFontScaling() {
        assertTrue(ThemeStylesheetResolver.buildSceneRootStyle(100).contains("13.000"));
        assertTrue(ThemeStylesheetResolver.buildSceneRootStyle(125).contains("16.250"));
        assertTrue(ThemeStylesheetResolver.buildSceneRootStyle(150).contains("19.500"));
    }

    @Test
    void resolveStylesheetUrl_withZoom_includesFontOverride() {
        String url = ThemeStylesheetResolver.resolveStylesheetUrl(getClass(), false, 110);
        String css = decodeCss(url);
        assertTrue(css.contains(".root"));
        assertTrue(css.contains("-fx-font-size: 14.300"));
    }

    @Test
    void resolveStylesheetUrl_returnsBuiltInResourceUrl() {
        String lightUrl = ThemeStylesheetResolver.resolveStylesheetUrl(getClass(), false);
        String darkUrl = ThemeStylesheetResolver.resolveStylesheetUrl(getClass(), true);
        assertTrue(lightUrl.contains("application.css"));
        assertTrue(darkUrl.contains("dark-application.css"));
    }

    private static String decodeCss(String dataUrl) {
        String prefix = "data:text/css;base64,";
        String payload = dataUrl.substring(prefix.length());
        return new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8);
    }

    private static void assertProfileJsonTextAreaFocusOverride(String css) {
        assertTrue(css.contains(".account-info-profile-text-area:focused"));
        assertTrue(css.contains("-fx-focus-color: transparent"));
        assertTrue(css.contains("-fx-faint-focus-color: transparent"));
        assertTrue(css.contains("-fx-background-radius: 0"));
        assertTrue(css.contains("-fx-border-radius: 0"));
        assertTrue(css.contains(".account-info-profile-text-area > .scroll-pane,\n.account-info-profile-text-area > .scroll-pane > .viewport"));
        assertTrue(css.contains(".account-info-profile-text-area:focused {\n    -fx-background-color: -uiptv-surface;\n    -fx-background-insets: 0;\n    -fx-border-color: -uiptv-border-subtle;\n    -fx-effect: none;"));
    }
}
