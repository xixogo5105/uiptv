package com.uiptv.util;

import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

public final class LocalizedNumberLabelResolver {
    private static final String BUNDLE_BASE_NAME = "i18n.ordinals.labels";
    private static final Map<String, ResourceBundle> CACHE = new ConcurrentHashMap<>();

    private LocalizedNumberLabelResolver() {
    }

    public static String formatSeasonLabel(String rawNumber, Locale locale) {
        return resolveLabel("season", rawNumber, locale);
    }

    public static String formatEpisodeLabel(String rawNumber, Locale locale) {
        return resolveLabel("episode", rawNumber, locale);
    }

    public static String formatTabLabel(String rawNumber, Locale locale) {
        return resolveLabel("tab", rawNumber, locale);
    }

    private static String resolveLabel(String prefix, String rawNumber, Locale locale) {
        int number = parseNumber(rawNumber);
        if (number <= 0) {
            return "";
        }
        ResourceBundle bundle = loadBundle(locale);
        if (bundle == null) {
            return "";
        }
        String key = prefix + "." + number;
        return bundle.containsKey(key) ? bundle.getString(key) : "";
    }

    private static ResourceBundle loadBundle(Locale locale) {
        if (locale == null || StringUtils.isBlank(locale.getLanguage())) {
            return null;
        }
        String language = locale.getLanguage().toLowerCase(Locale.ROOT);
        return CACHE.computeIfAbsent(language, LocalizedNumberLabelResolver::readBundle);
    }

    private static ResourceBundle readBundle(String language) {
        try {
            return ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.forLanguageTag(language));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int parseNumber(String rawNumber) {
        if (StringUtils.isBlank(rawNumber)) {
            return -1;
        }
        try {
            return Integer.parseInt(rawNumber.trim());
        } catch (Exception ignored) {
            return -1;
        }
    }
}
