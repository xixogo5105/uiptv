package com.uiptv.ui.util;

import com.uiptv.model.ThemeCssOverride;
import com.uiptv.service.ThemeCssOverrideService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

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
    void buildSceneRootStyle_appliesFontScaling() {
        assertTrue(ThemeStylesheetResolver.buildSceneRootStyle(100).contains("13.000"));
        assertTrue(ThemeStylesheetResolver.buildSceneRootStyle(125).contains("16.250"));
        assertTrue(ThemeStylesheetResolver.buildSceneRootStyle(150).contains("19.500"));
    }

    @Test
    void resolveStylesheetUrl_withOverrides_encodesToDataUrl() {
        ThemeCssOverrideService overrideService = Mockito.mock(ThemeCssOverrideService.class);
        ThemeCssOverride override = new ThemeCssOverride();
        override.setLightThemeCssContent(".root { -fx-accent: red; }");
        override.setDarkThemeCssContent(".root { -fx-accent: blue; }");
        Mockito.when(overrideService.read()).thenReturn(override);

        try (MockedStatic<ThemeCssOverrideService> serviceStatic = Mockito.mockStatic(ThemeCssOverrideService.class)) {
            serviceStatic.when(ThemeCssOverrideService::getInstance).thenReturn(overrideService);
            String lightUrl = ThemeStylesheetResolver.resolveStylesheetUrl(getClass(), false);
            String darkUrl = ThemeStylesheetResolver.resolveStylesheetUrl(getClass(), true);
            assertTrue(lightUrl.startsWith("data:text/css;base64,"));
            assertTrue(darkUrl.startsWith("data:text/css;base64,"));
            assertEquals(".root { -fx-accent: red; }", decodeCss(lightUrl));
            assertEquals(".root { -fx-accent: blue; }", decodeCss(darkUrl));
        }
    }

    @Test
    void resolveStylesheetUrl_withZoom_includesFontOverride() {
        ThemeCssOverrideService overrideService = Mockito.mock(ThemeCssOverrideService.class);
        ThemeCssOverride override = new ThemeCssOverride();
        override.setLightThemeCssContent(".root { -fx-accent: green; }");
        Mockito.when(overrideService.read()).thenReturn(override);

        try (MockedStatic<ThemeCssOverrideService> serviceStatic = Mockito.mockStatic(ThemeCssOverrideService.class)) {
            serviceStatic.when(ThemeCssOverrideService::getInstance).thenReturn(overrideService);
            String url = ThemeStylesheetResolver.resolveStylesheetUrl(getClass(), false, 110);
            String css = decodeCss(url);
            assertTrue(css.contains("-fx-accent: green"));
            assertTrue(css.contains("-fx-font-size: 14.300"));
        }
    }

    @Test
    void resolveStylesheetUrl_withoutOverrides_returnsResourceUrl() {
        ThemeCssOverrideService overrideService = Mockito.mock(ThemeCssOverrideService.class);
        ThemeCssOverride override = new ThemeCssOverride();
        Mockito.when(overrideService.read()).thenReturn(override);

        try (MockedStatic<ThemeCssOverrideService> serviceStatic = Mockito.mockStatic(ThemeCssOverrideService.class)) {
            serviceStatic.when(ThemeCssOverrideService::getInstance).thenReturn(overrideService);
            String lightUrl = ThemeStylesheetResolver.resolveStylesheetUrl(getClass(), false);
            String darkUrl = ThemeStylesheetResolver.resolveStylesheetUrl(getClass(), true);
            assertTrue(lightUrl.contains("application.css"));
            assertTrue(darkUrl.contains("dark-application.css"));
        }
    }

    private static String decodeCss(String dataUrl) {
        String prefix = "data:text/css;base64,";
        String payload = dataUrl.substring(prefix.length());
        return new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8);
    }
}