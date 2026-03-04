package com.uiptv.service;

import com.uiptv.model.ThemeCssOverride;
import com.uiptv.util.ThemeStylesheetResolver;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ThemeCssOverrideFlowTest extends DbBackedTest {

    @Test
    void uploadEquivalentSave_persistsOverrides_andResolverUsesThem() {
        ThemeCssOverrideService service = ThemeCssOverrideService.getInstance();

        ThemeCssOverride override = new ThemeCssOverride();
        override.setLightThemeCssName("light.css");
        override.setLightThemeCssContent(".root { -fx-accent: #123456; }");
        override.setDarkThemeCssName("dark.css");
        override.setDarkThemeCssContent(".root { -fx-accent: #abcdef; }");
        service.save(override);

        ThemeCssOverride saved = service.read();
        assertEquals("light.css", saved.getLightThemeCssName());
        assertEquals("dark.css", saved.getDarkThemeCssName());
        assertEquals(".root { -fx-accent: #123456; }", saved.getLightThemeCssContent());
        assertEquals(".root { -fx-accent: #abcdef; }", saved.getDarkThemeCssContent());

        String lightUrl = ThemeStylesheetResolver.resolveStylesheetUrl(getClass(), false);
        String darkUrl = ThemeStylesheetResolver.resolveStylesheetUrl(getClass(), true);
        assertTrue(lightUrl.startsWith("data:text/css;base64,"));
        assertTrue(darkUrl.startsWith("data:text/css;base64,"));
        assertEquals(".root { -fx-accent: #123456; }", decodeCss(lightUrl));
        assertEquals(".root { -fx-accent: #abcdef; }", decodeCss(darkUrl));
    }

    @Test
    void resetEquivalentSave_clearsOverrides_andResolverFallsBackToBundledCss() {
        ThemeCssOverrideService service = ThemeCssOverrideService.getInstance();

        ThemeCssOverride override = new ThemeCssOverride();
        override.setLightThemeCssName("light.css");
        override.setLightThemeCssContent(".root { -fx-accent: #123456; }");
        override.setDarkThemeCssName("dark.css");
        override.setDarkThemeCssContent(".root { -fx-accent: #abcdef; }");
        service.save(override);

        ThemeCssOverride reset = service.read();
        reset.setLightThemeCssName(null);
        reset.setLightThemeCssContent(null);
        reset.setDarkThemeCssName(null);
        reset.setDarkThemeCssContent(null);
        service.save(reset);

        ThemeCssOverride afterReset = service.read();
        assertNull(afterReset.getLightThemeCssName());
        assertNull(afterReset.getLightThemeCssContent());
        assertNull(afterReset.getDarkThemeCssName());
        assertNull(afterReset.getDarkThemeCssContent());

        String lightUrl = ThemeStylesheetResolver.resolveStylesheetUrl(getClass(), false);
        String darkUrl = ThemeStylesheetResolver.resolveStylesheetUrl(getClass(), true);
        assertNotNull(lightUrl);
        assertNotNull(darkUrl);
        assertTrue(lightUrl.contains("application.css"));
        assertTrue(darkUrl.contains("dark-application.css"));
    }

    private static String decodeCss(String dataUrl) {
        String prefix = "data:text/css;base64,";
        String payload = dataUrl.substring(prefix.length());
        return new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8);
    }
}
