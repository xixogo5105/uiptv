package com.uiptv.util;

import com.uiptv.model.ThemeCssOverride;
import com.uiptv.service.ThemeCssOverrideService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class ThemeStylesheetResolver {
    private static final String LIGHT_THEME_RESOURCE = "/application.css";
    private static final String DARK_THEME_RESOURCE = "/dark-application.css";

    private ThemeStylesheetResolver() {
    }

    public static String resolveStylesheetUrl(Class<?> resourceAnchor, boolean darkTheme) {
        ThemeCssOverride override = ThemeCssOverrideService.getInstance().read();
        String overrideCss = darkTheme ? override.getDarkThemeCssContent() : override.getLightThemeCssContent();
        if (overrideCss != null && !overrideCss.isBlank()) {
            return toDataUrl(overrideCss);
        }
        String resourcePath = getDefaultResourcePath(darkTheme);
        java.net.URL themeUrl = resourceAnchor.getResource(resourcePath);
        return themeUrl != null ? themeUrl.toExternalForm() : resourcePath;
    }

    public static String getDefaultResourcePath(boolean darkTheme) {
        return darkTheme ? DARK_THEME_RESOURCE : LIGHT_THEME_RESOURCE;
    }

    public static String readDefaultStylesheetContent(Class<?> resourceAnchor, boolean darkTheme) throws IOException {
        String resourcePath = getDefaultResourcePath(darkTheme);
        try (InputStream inputStream = resourceAnchor.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Missing theme resource: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String toDataUrl(String cssContent) {
        String encoded = Base64.getEncoder().encodeToString(cssContent.getBytes(StandardCharsets.UTF_8));
        return "data:text/css;base64," + encoded;
    }
}
