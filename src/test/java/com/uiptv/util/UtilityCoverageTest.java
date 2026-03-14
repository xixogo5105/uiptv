package com.uiptv.util;

import com.uiptv.model.ThemeCssOverride;
import com.uiptv.service.ThemeCssOverrideService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtilityCoverageTest {

    @Test
    void appLog_notifiesHealthyListenersAndIgnoresFailingOnes() {
        AtomicInteger calls = new AtomicInteger();
        Consumer<String> good = msg -> calls.incrementAndGet();
        Consumer<String> bad = msg -> { throw new IllegalStateException("boom"); };

        AppLog.registerListener(good);
        AppLog.registerListener(bad);
        AppLog.addInfoLog(UtilityCoverageTest.class, "test-entry");
        AppLog.unregisterListener(good);
        AppLog.unregisterListener(bad);
        AppLog.unregisterListener(null);

        assertEquals(1, calls.get());
    }

    @Test
    void platform_resolvesPathsAndToleratesCommandFailure() {
        assertFalse(Platform.getUserHomeDirPath().isBlank());
        assertTrue(Platform.getWebServerRootPath().endsWith("/web") || Platform.getWebServerRootPath().endsWith("\\web"));
        Platform.executeCommand("definitely-missing-binary", "--version");
    }

    @Test
    void themeStylesheetResolver_readsDefaultsAndSupportsOverrides() throws Exception {
        assertEquals("/application.css", ThemeStylesheetResolver.getDefaultResourcePath(false));
        assertEquals("/dark-application.css", ThemeStylesheetResolver.getDefaultResourcePath(true));
        assertTrue(ThemeStylesheetResolver.readDefaultStylesheetContent(ThemeStylesheetResolver.class, false).contains(".root"));
        assertTrue(ThemeStylesheetResolver.buildSceneRootStyle(125).contains("16.250"));

        ThemeCssOverrideService overrideService = Mockito.mock(ThemeCssOverrideService.class);
        ThemeCssOverride override = new ThemeCssOverride();
        override.setLightThemeCssContent(".root { -fx-base: red; }");
        override.setDarkThemeCssContent(".root { -fx-base: blue; }");
        Mockito.when(overrideService.read()).thenReturn(override);

        try (MockedStatic<ThemeCssOverrideService> serviceStatic = Mockito.mockStatic(ThemeCssOverrideService.class)) {
            serviceStatic.when(ThemeCssOverrideService::getInstance).thenReturn(overrideService);
            String lightUrl = ThemeStylesheetResolver.resolveStylesheetUrl(ThemeStylesheetResolver.class, false);
            String darkZoomUrl = ThemeStylesheetResolver.resolveStylesheetUrl(ThemeStylesheetResolver.class, true, 110);
            assertTrue(lightUrl.startsWith("data:text/css;base64,"));
            assertTrue(darkZoomUrl.startsWith("data:text/css;base64,"));
        }
    }
}
