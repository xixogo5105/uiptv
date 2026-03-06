package com.uiptv.util;

import javafx.geometry.NodeOrientation;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.scene.text.Font;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DecimalStyle;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Pattern;

public final class I18n {
    public static final String DEFAULT_LANGUAGE_TAG = "en-US";
    private static final String BUNDLE_BASE_NAME = "i18n.messages";
    private static final Set<String> RTL_LANGUAGE_CODES = Set.of(
            "ar", // Arabic
            "fa", // Persian
            "he", // Hebrew
            "ur", // Urdu
            "ps", // Pashto
            "sd", // Sindhi
            "ug", // Uyghur
            "yi", // Yiddish
            "dv", // Divehi
            "ckb" // Central Kurdish (Sorani)
    );

    private static final List<SupportedLanguage> SUPPORTED_LANGUAGES = List.of(
            new SupportedLanguage("en-US", "English (United States)"),
            new SupportedLanguage("en-GB", "English (United Kingdom)"),
            new SupportedLanguage("es-ES", "Español"),
            new SupportedLanguage("fr-FR", "Français"),
            new SupportedLanguage("de-DE", "Deutsch"),
            new SupportedLanguage("it-IT", "Italiano"),
            new SupportedLanguage("pt-BR", "Português (Brasil)"),
            new SupportedLanguage("pt-PT", "Português (Portugal)"),
            new SupportedLanguage("ru-RU", "Русский"),
            new SupportedLanguage("uk-UA", "Українська"),
            new SupportedLanguage("tr-TR", "Türkçe"),
            new SupportedLanguage("zh-CN", "简体中文"),
            new SupportedLanguage("zh-TW", "繁體中文"),
            new SupportedLanguage("ja-JP", "日本語"),
            new SupportedLanguage("ko-KR", "한국어"),
            new SupportedLanguage("id-ID", "Bahasa Indonesia"),
            new SupportedLanguage("vi-VN", "Tiếng Việt"),
            new SupportedLanguage("th-TH", "ไทย"),
            new SupportedLanguage("ar-SA", "العربية"),
            new SupportedLanguage("ur-PK", "اردو"),
            new SupportedLanguage("hi-IN", "हिन्दी"),
            new SupportedLanguage("bn-BD", "বাংলা"),
            new SupportedLanguage("pa-IN", "ਪੰਜਾਬੀ"),
            new SupportedLanguage("te-IN", "తెలుగు"),
            new SupportedLanguage("ta-IN", "தமிழ்"),
            new SupportedLanguage("ml-IN", "മലയാളം")
    );

    private static final Object LOCK = new Object();
    private static final Pattern TOKEN_ARTIFACT_PATTERN =
            Pattern.compile("(?:__\\s*T\\s*K\\d+_+|__\\d+__|ForTK\\d+__)");
    private static final String LOCALE_FONT_FAMILY_KEY = "uiptv.locale.font.family";
    private static final String LOCALE_FONT_CHILDREN_KEY = "uiptv.locale.font.children";
    private static Locale currentLocale = Locale.forLanguageTag(DEFAULT_LANGUAGE_TAG);
    private static ResourceBundle bundle = loadBundle(currentLocale);

    private I18n() {
    }

    public static void initialize(String languageTag) {
        setLocale(languageTag);
    }

    public static void setLocale(String languageTag) {
        synchronized (LOCK) {
            currentLocale = resolveLocale(languageTag);
            Locale.setDefault(currentLocale);
            ResourceBundle.clearCache();
            bundle = loadBundle(currentLocale);
        }
    }

    public static String getCurrentLanguageTag() {
        synchronized (LOCK) {
            return currentLocale.toLanguageTag();
        }
    }

    public static Locale getCurrentLocale() {
        synchronized (LOCK) {
            return currentLocale;
        }
    }

    public static List<SupportedLanguage> getSupportedLanguages() {
        return SUPPORTED_LANGUAGES;
    }

    public static SupportedLanguage resolveSupportedLanguage(String languageTag) {
        String normalizedTag = normalizeLanguageTag(languageTag);
        return SUPPORTED_LANGUAGES.stream()
                .filter(item -> item.languageTag().equalsIgnoreCase(normalizedTag))
                .findFirst()
                .orElseGet(() -> SUPPORTED_LANGUAGES.get(0));
    }

    public static String normalizeLanguageTag(String languageTag) {
        return resolveLocale(languageTag).toLanguageTag();
    }

    public static boolean isCurrentLocaleRtl() {
        synchronized (LOCK) {
            String language = currentLocale.getLanguage().toLowerCase(Locale.ROOT);
            String script = currentLocale.getScript().toLowerCase(Locale.ROOT);
            return RTL_LANGUAGE_CODES.contains(language)
                    || "arab".equals(script)
                    || "hebr".equals(script);
        }
    }

    public static void applySceneOrientation(Scene scene) {
        if (scene == null || scene.getRoot() == null) {
            return;
        }
        scene.getRoot().setNodeOrientation(isCurrentLocaleRtl()
                ? NodeOrientation.RIGHT_TO_LEFT
                : NodeOrientation.LEFT_TO_RIGHT);
        applyLocaleTypography(scene.getRoot());
    }

    public static String tr(String key, Object... args) {
        String pattern = normalizeResolvedText(lookupOrFallback(key));
        if (args == null || args.length == 0) {
            return pattern;
        }
        return normalizeResolvedText(MessageFormat.format(pattern, args));
    }

    public static String formatDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        Locale locale = getDisplayLocale();
        if ("en".equalsIgnoreCase(locale.getLanguage())) {
            String monthYear = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH).format(date);
            return englishOrdinal(date.getDayOfMonth()) + " " + monthYear;
        }
        String pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                FormatStyle.LONG, null, IsoChronology.INSTANCE, locale
        );
        pattern = pattern.replace(",", "").replace("،", "").replaceAll("\\s+", " ").trim();
        return DateTimeFormatter.ofPattern(pattern, locale)
                .withDecimalStyle(DecimalStyle.of(locale))
                .format(date);
    }

    public static String formatNumber(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        Locale locale = getDisplayLocale();
        char zeroDigit = DecimalStyle.of(locale).getZeroDigit();
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= '0' && ch <= '9') {
                out.append((char) (zeroDigit + (ch - '0')));
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    public static String formatSeasonLabel(String seasonNumber) {
        String localized = LocalizedNumberLabelResolver.formatSeasonLabel(seasonNumber, getCurrentLocale());
        if (!localized.isBlank()) {
            return localized;
        }
        return tr("autoSeasonNumber", formatNumber(seasonNumber == null || seasonNumber.isBlank() ? "1" : seasonNumber));
    }

    public static String formatEpisodeLabel(String episodeNumber) {
        if (episodeNumber == null || episodeNumber.isBlank()) {
            return tr("autoEpisodeNumber", "-");
        }
        String localized = LocalizedNumberLabelResolver.formatEpisodeLabel(episodeNumber, getCurrentLocale());
        if (!localized.isBlank()) {
            return localized;
        }
        return tr("autoEpisodeNumber", formatNumber(episodeNumber));
    }

    public static String formatTabNumberLabel(String rawNumber) {
        String localized = LocalizedNumberLabelResolver.formatTabLabel(rawNumber, getCurrentLocale());
        if (!localized.isBlank()) {
            return localized;
        }
        return formatNumber(rawNumber == null || rawNumber.isBlank() ? "1" : rawNumber);
    }

    private static String englishOrdinal(int dayOfMonth) {
        int mod100 = dayOfMonth % 100;
        if (mod100 >= 11 && mod100 <= 13) {
            return dayOfMonth + "th";
        }
        return switch (dayOfMonth % 10) {
            case 1 -> dayOfMonth + "st";
            case 2 -> dayOfMonth + "nd";
            case 3 -> dayOfMonth + "rd";
            default -> dayOfMonth + "th";
        };
    }

    private static Locale getDisplayLocale() {
        Locale locale = getCurrentLocale();
        String numberingSystem = switch (locale.getLanguage()) {
            case "ar" -> "arab";
            case "ur", "fa", "ps", "sd" -> "arabext";
            case "bn" -> "beng";
            default -> "";
        };
        if (numberingSystem.isBlank()) {
            return locale;
        }
        return new Locale.Builder()
                .setLocale(locale)
                .setUnicodeLocaleKeyword("nu", numberingSystem)
                .build();
    }

    private static String lookupOrFallback(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }

        synchronized (LOCK) {
            if (bundle != null && bundle.containsKey(key)) {
                return bundle.getString(key);
            }
        }
        return key;
    }

    private static String normalizeResolvedText(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        String normalized = value
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\r", "\n")
                .replace("\\:", ":")
                .replace("\\=", "=")
                .replace("\\/", "/");

        normalized = TOKEN_ARTIFACT_PATTERN.matcher(normalized).replaceAll("\n");
        return normalized;
    }

    private static void applyLocaleTypography(Node node) {
        if (node == null) {
            return;
        }
        String preferredFamily = resolvePreferredFontFamily();
        if (!preferredFamily.isBlank()) {
            applyPreferredFont(node, preferredFamily);
        }
        if (node instanceof Parent parent) {
            attachTypographyListener(parent);
            for (Node child : parent.getChildrenUnmodifiable()) {
                applyLocaleTypography(child);
            }
        }
    }

    private static void attachTypographyListener(Parent parent) {
        if (parent == null || Boolean.TRUE.equals(parent.getProperties().get(LOCALE_FONT_CHILDREN_KEY))) {
            return;
        }
        parent.getProperties().put(LOCALE_FONT_CHILDREN_KEY, Boolean.TRUE);
        parent.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change -> {
            while (change.next()) {
                if (!change.wasAdded()) {
                    continue;
                }
                for (Node node : change.getAddedSubList()) {
                    applyLocaleTypography(node);
                }
            }
        });
    }

    private static void applyPreferredFont(Node node, String family) {
        if (node == null || family == null || family.isBlank()) {
            return;
        }
        Object previousFamily = node.getProperties().get(LOCALE_FONT_FAMILY_KEY);
        if (family.equals(previousFamily)) {
            return;
        }
        node.getProperties().put(LOCALE_FONT_FAMILY_KEY, family);

        if (node instanceof Labeled labeled) {
            labeled.setStyle(appendInlineFontFamily(labeled.getStyle(), family));
        } else if (node instanceof TextInputControl textInputControl) {
            textInputControl.setStyle(appendInlineFontFamily(textInputControl.getStyle(), family));
        } else if (node instanceof Control control) {
            control.setStyle(appendInlineFontFamily(control.getStyle(), family));
        }
    }

    private static String appendInlineFontFamily(String existingStyle, String family) {
        String fontRule = "-fx-font-family: \"" + family.replace("\"", "") + "\";";
        if (existingStyle == null || existingStyle.isBlank()) {
            return fontRule;
        }
        if (existingStyle.contains("-fx-font-family")) {
            return existingStyle;
        }
        return existingStyle.trim() + (existingStyle.trim().endsWith(";") ? " " : "; ") + fontRule;
    }

    private static String resolvePreferredFontFamily() {
        String language = getCurrentLocale().getLanguage().toLowerCase(Locale.ROOT);
        return switch (language) {
            case "hi", "mr", "ne", "sa" -> firstAvailableFontFamily(
                    "Devanagari Sangam MN",
                    "Kohinoor Devanagari",
                    "Devanagari MT",
                    "ITF Devanagari",
                    "ITF Devanagari Marathi",
                    "Shree Devanagari 714",
                    "Noto Sans Devanagari",
                    "Arial Unicode MS"
            );
            case "bn" -> firstAvailableFontFamily(
                    "Bangla Sangam MN",
                    "Bangla MN",
                    "Kohinoor Bangla",
                    "Noto Sans Bengali",
                    "Arial Unicode MS"
            );
            case "pa" -> firstAvailableFontFamily(
                    "Gurmukhi Sangam MN",
                    "Gurmukhi MN",
                    "Raavi",
                    "Noto Sans Gurmukhi",
                    "Arial Unicode MS"
            );
            case "ta" -> firstAvailableFontFamily(
                    "Tamil Sangam MN",
                    "Tamil MN",
                    "Noto Sans Tamil",
                    "Arial Unicode MS"
            );
            case "te" -> firstAvailableFontFamily(
                    "Kohinoor Telugu",
                    "Telugu Sangam MN",
                    "Telugu MN",
                    "Noto Sans Telugu",
                    "Arial Unicode MS"
            );
            case "ml" -> firstAvailableFontFamily(
                    "Malayalam Sangam MN",
                    "Malayalam MN",
                    "Noto Sans Malayalam",
                    "Arial Unicode MS"
            );
            default -> "";
        };
    }

    private static String firstAvailableFontFamily(String... candidates) {
        if (candidates == null || candidates.length == 0) {
            return "";
        }
        List<String> families = Font.getFamilies();
        for (String candidate : candidates) {
            if (candidate != null && families.contains(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private static Locale resolveLocale(String languageTag) {
        if (languageTag == null || languageTag.isBlank()) {
            return Locale.forLanguageTag(DEFAULT_LANGUAGE_TAG);
        }

        Locale locale = Locale.forLanguageTag(languageTag.trim());
        if (locale.getLanguage() == null || locale.getLanguage().isBlank()) {
            return Locale.forLanguageTag(DEFAULT_LANGUAGE_TAG);
        }
        return locale;
    }

    private static ResourceBundle loadBundle(Locale locale) {
        try {
            return ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale, I18n.class.getModule());
        } catch (MissingResourceException ex) {
            return ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.forLanguageTag(DEFAULT_LANGUAGE_TAG), I18n.class.getModule());
        }
    }

    public record SupportedLanguage(String languageTag, String nativeDisplayName) {
        @Override
        public String toString() {
            return nativeDisplayName;
        }
    }
}
