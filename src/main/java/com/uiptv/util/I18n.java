package com.uiptv.util;

import javafx.geometry.NodeOrientation;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.PopupControl;
import javafx.scene.control.TextInputControl;
import javafx.scene.text.Font;
import javafx.stage.WindowEvent;

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
    private static final String ARIAL_UNICODE_MS = "Arial Unicode MS";
    private static final Set<String> RTL_LANGUAGE_CODES = Set.of(
            "ar", "fa", "he", "ur", "ps", "sd", "ug", "yi", "dv", "ckb"
    );

    private static final List<SupportedLanguage> SUPPORTED_LANGUAGES = List.of(
            new SupportedLanguage(DEFAULT_LANGUAGE_TAG, "English (United States)"),
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
    private static final Pattern TOKEN_ARTIFACT_PATTERN = Pattern.compile("(?:__\\s*T\\s*K\\d+_+|__\\d+__|ForTK\\d+__)");
    private static final Pattern INLINE_FONT_FAMILY_RULE_PATTERN = Pattern.compile("-fx-font-family\\s*:\\s*[^;]+;?");
    private static final String LOCALE_FONT_FAMILY_KEY = "uiptv.locale.font.family";
    private static final String LOCALE_FONT_CHILDREN_KEY = "uiptv.locale.font.children";
    
    private static Locale currentLocale = Locale.forLanguageTag(DEFAULT_LANGUAGE_TAG);
    private static ResourceBundle bundle = loadBundle(currentLocale);

    private I18n() {}

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
                .orElseGet(SUPPORTED_LANGUAGES::getFirst);
    }

    public static String normalizeLanguageTag(String languageTag) {
        return resolveLocale(languageTag).toLanguageTag();
    }

    public static boolean isCurrentLocaleRtl() {
        synchronized (LOCK) {
            String language = currentLocale.getLanguage().toLowerCase(Locale.ROOT);
            String script = currentLocale.getScript().toLowerCase(Locale.ROOT);
            return RTL_LANGUAGE_CODES.contains(language) || "arab".equals(script) || "hebr".equals(script);
        }
    }

    public static void applySceneOrientation(Scene scene) {
        if (scene == null || scene.getRoot() == null) return;
        scene.getRoot().setNodeOrientation(isCurrentLocaleRtl() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
        applyLocaleTypography(scene.getRoot());
    }

    public static void preparePopupControl(PopupControl popupControl, Node ownerNode) {
        if (popupControl == null || ownerNode == null) return;
        popupControl.addEventHandler(WindowEvent.WINDOW_SHOWING, event -> applyPopupSceneFormatting(popupControl, ownerNode));
        popupControl.addEventHandler(WindowEvent.WINDOW_SHOWN, event -> applyPopupSceneFormatting(popupControl, ownerNode));
    }

    public static String tr(String key, Object... args) {
        String pattern = lookupOrFallback(key);
        if (args == null || args.length == 0) {
            return normalizeResolvedText(pattern);
        }
        try {
            return normalizeResolvedText(MessageFormat.format(pattern, args));
        } catch (Exception e) {
            return normalizeResolvedText(pattern);
        }
    }

    public static String trEnglish(String key, Object... args) {
        return trForLocale(Locale.forLanguageTag(DEFAULT_LANGUAGE_TAG), key, args);
    }

    public static String formatDate(LocalDate date) {
        if (date == null) return "";
        Locale locale = getDisplayLocale();
        if ("en".equalsIgnoreCase(locale.getLanguage())) {
            String monthYear = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH).format(date);
            return englishOrdinal(date.getDayOfMonth()) + " " + monthYear;
        }
        String pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.LONG, null, IsoChronology.INSTANCE, locale);
        pattern = pattern.replace(",", "").replace("،", "").replaceAll("\\s+", " ").trim();
        return DateTimeFormatter.ofPattern(pattern, locale).withDecimalStyle(DecimalStyle.of(locale)).format(date);
    }

    public static String formatNumber(String value) {
        if (value == null || value.isBlank()) return "";
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
        if (!localized.isBlank()) return localized;
        return tr("autoSeasonNumber", formatNumber(seasonNumber == null || seasonNumber.isBlank() ? "1" : seasonNumber));
    }

    public static String formatEpisodeLabel(String episodeNumber) {
        if (episodeNumber == null || episodeNumber.isBlank()) return tr("autoEpisodeNumber", "-");
        String localized = LocalizedNumberLabelResolver.formatEpisodeLabel(episodeNumber, getCurrentLocale());
        if (!localized.isBlank()) return localized;
        return tr("autoEpisodeNumber", formatNumber(episodeNumber));
    }

    public static String formatTabNumberLabel(String rawNumber) {
        String localized = LocalizedNumberLabelResolver.formatTabLabel(rawNumber, getCurrentLocale());
        if (!localized.isBlank()) return localized;
        return formatNumber(rawNumber == null || rawNumber.isBlank() ? "1" : rawNumber);
    }

    private static String englishOrdinal(int dayOfMonth) {
        int mod100 = dayOfMonth % 100;
        if (mod100 >= 11 && mod100 <= 13) return dayOfMonth + "th";
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
        if (numberingSystem.isBlank()) return locale;
        return new Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", numberingSystem).build();
    }

    private static String lookupOrFallback(String key) {
        if (key == null || key.isBlank()) return "";
        synchronized (LOCK) {
            if (bundle != null && bundle.containsKey(key)) {
                return bundle.getString(key);
            }
            // Explicitly try fallback to English/Default bundle
            try {
                ResourceBundle fallbackBundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.forLanguageTag(DEFAULT_LANGUAGE_TAG), I18n.class.getClassLoader());
                if (fallbackBundle != null && fallbackBundle.containsKey(key)) {
                    return fallbackBundle.getString(key);
                }
            } catch (Exception _) {}
        }
        return key;
    }

    private static String trForLocale(Locale locale, String key, Object... args) {
        String pattern = lookupOrFallback(locale, key);
        if (args == null || args.length == 0) return normalizeResolvedText(pattern);
        try {
            return normalizeResolvedText(MessageFormat.format(pattern, args));
        } catch (Exception e) {
            return normalizeResolvedText(pattern);
        }
    }

    private static String lookupOrFallback(Locale locale, String key) {
        try {
            ResourceBundle lBundle = loadBundle(locale);
            if (lBundle != null && lBundle.containsKey(key)) {
                return lBundle.getString(key);
            }
        } catch (Exception _) {}
        return lookupOrFallback(key);
    }

    private static String normalizeResolvedText(String value) {
        if (value == null || value.isEmpty()) return "";
        String normalized = value
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\r", "\n")
                .replace("\\:", ":")
                .replace("\\=", "=")
                .replace("\\/", "/");
        return TOKEN_ARTIFACT_PATTERN.matcher(normalized).replaceAll("\n");
    }

    private static void applyLocaleTypography(Node node) {
        if (node == null) return;
        String preferredFamily = resolvePreferredFontFamily();
        if (!preferredFamily.isBlank()) applyPreferredFont(node, preferredFamily);
        else clearPreferredFont(node);
        if (node instanceof Parent parent) {
            attachTypographyListener(parent);
            for (Node child : parent.getChildrenUnmodifiable()) applyLocaleTypography(child);
        }
    }

    private static void applyPopupSceneFormatting(PopupControl popupControl, Node ownerNode) {
        if (popupControl == null || ownerNode == null) return;
        Scene popupScene = popupControl.getScene();
        if (popupScene == null || popupScene.getRoot() == null) return;
        Scene ownerScene = ownerNode.getScene();
        if (ownerScene != null) {
            String ownerRootStyle = ownerScene.getRoot() == null ? "" : ownerScene.getRoot().getStyle();
            popupControl.setStyle(ownerRootStyle);
            popupScene.getStylesheets().setAll(ownerScene.getStylesheets());
            if (ownerScene.getRoot() != null) {
                popupScene.getRoot().setStyle(ownerRootStyle);
                popupScene.getRoot().setNodeOrientation(ownerScene.getRoot().getNodeOrientation());
            }
        } else {
            popupScene.getRoot().setNodeOrientation(isCurrentLocaleRtl() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
        }
    }

    private static void attachTypographyListener(Parent parent) {
        if (parent == null || Boolean.TRUE.equals(parent.getProperties().get(LOCALE_FONT_CHILDREN_KEY))) return;
        parent.getProperties().put(LOCALE_FONT_CHILDREN_KEY, Boolean.TRUE);
        parent.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change -> {
            while (change.next()) {
                if (!change.wasAdded()) continue;
                for (Node node : change.getAddedSubList()) applyLocaleTypography(node);
            }
        });
    }

    private static void applyPreferredFont(Node node, String family) {
        if (node == null || family == null || family.isBlank() || node.styleProperty().isBound()) return;
        Object previousFamily = node.getProperties().get(LOCALE_FONT_FAMILY_KEY);
        if (family.equals(previousFamily)) return;
        node.getProperties().put(LOCALE_FONT_FAMILY_KEY, family);
        if (node instanceof Labeled labeled) labeled.setStyle(appendInlineFontFamily(labeled.getStyle(), family));
        else if (node instanceof TextInputControl textInputControl) textInputControl.setStyle(appendInlineFontFamily(textInputControl.getStyle(), family));
        else if (node instanceof Control control) control.setStyle(appendInlineFontFamily(control.getStyle(), family));
    }

    private static void clearPreferredFont(Node node) {
        if (node == null || node.styleProperty().isBound()) return;
        Object previousFamily = node.getProperties().remove(LOCALE_FONT_FAMILY_KEY);
        if (!(previousFamily instanceof String family) || family.isBlank()) return;
        if (node instanceof Labeled labeled) labeled.setStyle(removeInlineFontFamily(labeled.getStyle()));
        else if (node instanceof TextInputControl textInputControl) textInputControl.setStyle(removeInlineFontFamily(textInputControl.getStyle()));
        else if (node instanceof Control control) control.setStyle(removeInlineFontFamily(control.getStyle()));
    }

    static String appendInlineFontFamily(String existingStyle, String family) {
        String fontRule = "-fx-font-family: \"" + family.replace("\"", "") + "\";";
        if (existingStyle == null || existingStyle.isBlank()) return fontRule;
        if (existingStyle.contains("-fx-font-family")) return INLINE_FONT_FAMILY_RULE_PATTERN.matcher(existingStyle).replaceFirst(fontRule);
        return existingStyle.trim() + (existingStyle.trim().endsWith(";") ? " " : "; ") + fontRule;
    }

    static String removeInlineFontFamily(String existingStyle) {
        if (existingStyle == null || existingStyle.isBlank()) return "";
        String normalized = INLINE_FONT_FAMILY_RULE_PATTERN.matcher(existingStyle).replaceAll("").trim();
        normalized = normalizeInlineStyleSeparators(normalized);
        if (normalized.isBlank()) return "";
        return normalized.endsWith(";") ? normalized : normalized + ";";
    }

    private static String normalizeInlineStyleSeparators(String style) {
        if (style == null || style.isBlank()) return "";
        String[] parts = style.split(";");
        StringBuilder normalized = new StringBuilder();
        for (String part : parts) {
            String trimmed = part == null ? "" : part.trim();
            if (trimmed.isEmpty()) continue;
            if (!normalized.isEmpty()) normalized.append("; ");
            normalized.append(trimmed);
        }
        return normalized.toString();
    }

    private static String resolvePreferredFontFamily() {
        synchronized (LOCK) {
            String language = currentLocale.getLanguage().toLowerCase(Locale.ROOT);
            return switch (language) {
                case "hi", "mr", "ne", "sa" -> firstAvailableFontFamily("Devanagari Sangam MN", "Kohinoor Devanagari", "Devanagari MT", "ITF Devanagari", "ITF Devanagari Marathi", "Shree Devanagari 714", "Noto Sans Devanagari", ARIAL_UNICODE_MS);
                case "bn" -> firstAvailableFontFamily("Bangla Sangam MN", "Bangla MN", "Kohinoor Bangla", "Noto Sans Bengali", ARIAL_UNICODE_MS);
                case "ur" -> firstAvailableFontFamily("Noto Nastaliq Urdu UI", "DecoType Nastaleeq Urdu UI", "Nafees Naskh", "Nafees Pakistani Naskh", "Geeza Pro", "Geeza Pro Interface", "Noto Naskh Arabic UI", "Noto Sans Arabic UI", ARIAL_UNICODE_MS);
                case "pa" -> firstAvailableFontFamily("Gurmukhi Sangam MN", "Gurmukhi MN", "Raavi", "Noto Sans Gurmukhi", ARIAL_UNICODE_MS);
                case "ta" -> firstAvailableFontFamily("Tamil Sangam MN", "Tamil MN", "Noto Sans Tamil", ARIAL_UNICODE_MS);
                case "te" -> firstAvailableFontFamily("Kohinoor Telugu", "Telugu Sangam MN", "Telugu MN", "Noto Sans Telugu", ARIAL_UNICODE_MS);
                case "ml" -> firstAvailableFontFamily("Malayalam Sangam MN", "Malayalam MN", "Noto Sans Malayalam", ARIAL_UNICODE_MS);
                default -> "";
            };
        }
    }

    private static String firstAvailableFontFamily(String... candidates) {
        if (candidates == null || candidates.length == 0) return "";
        List<String> families = Font.getFamilies();
        for (String candidate : candidates) {
            if (candidate != null && families.contains(candidate)) return candidate;
        }
        return "";
    }

    private static Locale resolveLocale(String languageTag) {
        if (languageTag == null || languageTag.isBlank()) return Locale.forLanguageTag(DEFAULT_LANGUAGE_TAG);
        Locale locale = Locale.forLanguageTag(languageTag.trim());
        if (locale.getLanguage() == null || locale.getLanguage().isBlank()) return Locale.forLanguageTag(DEFAULT_LANGUAGE_TAG);
        return locale;
    }

    private static ResourceBundle loadBundle(Locale locale) {
        try {
            return ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale, I18n.class.getClassLoader());
        } catch (MissingResourceException e) {
            try {
                return ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale);
            } catch (MissingResourceException e2) {
                try {
                    return ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.forLanguageTag(DEFAULT_LANGUAGE_TAG));
                } catch (MissingResourceException e3) {
                    return null;
                }
            }
        }
    }

    public record SupportedLanguage(String languageTag, String nativeDisplayName) {
        @Override public String toString() { return nativeDisplayName; }
    }
}
