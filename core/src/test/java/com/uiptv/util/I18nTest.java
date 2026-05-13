package com.uiptv.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class I18nTest {
    private static final Path I18N_DIR = Path.of("src/main/resources/i18n");
    private static final String BASE_BUNDLE_FILE = "messages.properties";
    private static final String BUNDLE_PREFIX = "messages";
    private static final String BUNDLE_SUFFIX = ".properties";
    private static final String SMOKE_KEY = "commonClose";
    private String originalLanguageTag;

    @BeforeEach
    void captureLocale() {
        originalLanguageTag = I18n.getCurrentLanguageTag();
    }

    @AfterEach
    void restoreLocale() {
        I18n.setLocale(originalLanguageTag);
    }

    @Test
    void allMessageBundlesHaveSameKeysAsBaseBundle() throws IOException {
        Set<String> baseKeys = loadBundle(BASE_BUNDLE_FILE).stringPropertyNames();
        assertFalse(baseKeys.isEmpty(), "Base bundle should not be empty.");

        try (Stream<Path> files = Files.list(I18N_DIR)) {
            List<Path> bundles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith(BUNDLE_PREFIX) && name.endsWith(BUNDLE_SUFFIX);
                    })
                    .sorted()
                    .toList();

            for (Path bundlePath : bundles) {
                String bundleName = bundlePath.getFileName().toString();
                Set<String> bundleKeys = loadBundle(bundleName).stringPropertyNames();
                Set<String> missingKeys = baseKeys.stream()
                        .filter(key -> !bundleKeys.contains(key))
                        .collect(Collectors.toSet());
                assertTrue(missingKeys.isEmpty(), "Missing keys in " + bundleName + ": " + missingKeys);
            }
        }
    }

    @Test
    void supportedLanguagesResolveExpectedResourceValues() throws IOException {
        for (I18n.SupportedLanguage language : I18n.getSupportedLanguages()) {
            String tag = language.languageTag();
            String bundleFile = "messages_" + tag.replace('-', '_') + ".properties";
            Path bundlePath = I18N_DIR.resolve(bundleFile);
            assertTrue(Files.exists(bundlePath), "Missing bundle file for language tag " + tag + ": " + bundleFile);

            Properties bundle = loadBundle(bundleFile);
            String expected = bundle.getProperty(SMOKE_KEY);
            assertNotNull(expected, "Missing key '" + SMOKE_KEY + "' in " + bundleFile);

            I18n.setLocale(tag);
            assertEquals(expected, I18n.tr(SMOKE_KEY), "Unexpected resource value for language tag " + tag);
            assertFalse(I18n.tr(SMOKE_KEY).isBlank(), "Resolved value should not be blank for " + tag);
        }
    }

    @Test
    void resolvedMessagesDoNotExposeEscapeArtifacts() throws IOException {
        Set<String> keys = loadBundle(BASE_BUNDLE_FILE).stringPropertyNames();
        for (I18n.SupportedLanguage language : I18n.getSupportedLanguages()) {
            I18n.setLocale(language.languageTag());
            for (String key : keys) {
                String resolved = I18n.tr(key);
                assertFalse(resolved.contains("\\n"), "Found literal \\n in " + language.languageTag() + ":" + key);
                assertFalse(resolved.contains("\\:"), "Found literal \\: in " + language.languageTag() + ":" + key);
                assertFalse(resolved.contains("\\="), "Found literal \\= in " + language.languageTag() + ":" + key);
                assertFalse(resolved.contains("__TK"), "Found token artifact in " + language.languageTag() + ":" + key);
                assertFalse(resolved.contains("__T K"), "Found token artifact in " + language.languageTag() + ":" + key);
            }
        }
    }

    @Test
    void rtlLanguagesAndScriptsAreDetectedCorrectly() {
        List<String> rtlLanguageTags = List.of(
                "ar-SA",
                "he-IL",
                "fa-IR",
                "ur-PK",
                "ps-AF",
                "sd-PK",
                "ug-CN",
                "yi-001",
                "dv-MV",
                "ckb-IQ"
        );
        for (String tag : rtlLanguageTags) {
            I18n.setLocale(tag);
            assertTrue(I18n.isCurrentLocaleRtl(), "Expected RTL for " + tag);
        }

        List<String> ltrLanguageTags = List.of("en-US", "de-DE", "es-ES", "hi-IN", "zh-CN");
        for (String tag : ltrLanguageTags) {
            I18n.setLocale(tag);
            assertFalse(I18n.isCurrentLocaleRtl(), "Expected LTR for " + tag);
        }

        I18n.setLocale("ku-Arab-IQ");
        assertTrue(I18n.isCurrentLocaleRtl(), "Expected RTL for Arabic script fallback.");

        I18n.setLocale("en-Latn-US");
        assertFalse(I18n.isCurrentLocaleRtl(), "Expected LTR for Latin script.");
    }

    @Test
    void formatDateUsesLocaleDigitsWithoutCommaForUrdu() {
        I18n.setLocale("ur-PK");

        String formatted = I18n.formatDate(LocalDate.of(1996, 1, 4));

        assertFalse(formatted.contains(","), "Urdu date should not contain ASCII comma.");
        assertFalse(formatted.contains("،"), "Urdu date should not contain Arabic comma.");
        assertFalse(formatted.matches(".*[0-9].*"), "Urdu date should use localized numerals.");
        assertTrue(formatted.contains("جنوری"), "Urdu date should use localized month name.");
        assertEquals("۱۹۹۶", I18n.formatNumber("1996"), "Urdu numbers should use localized numerals.");
    }

    @Test
    void seasonAndEpisodeLabelsUseOrdinalWordsForSupportedLanguages() {
        assertOrdinalLabels("ur-PK", "پہلا سیزن", "پہلی قسط", "گیارہواں سیزن", "گیارہویں قسط", "پچاسواں سیزن", "پچاسویں قسط", "ایک", "دو", "پچاس");
        assertOrdinalLabels("hi-IN", "पहला सीज़न", "पहली कड़ी", "ग्यारहवाँ सीज़न", "ग्यारहवीं कड़ी", "पचासवाँ सीज़न", "पचासवीं कड़ी", "एक", "दो", "पचास");
        assertOrdinalLabels("ar-SA", "الموسم الأول", "الحلقة الأولى", "الموسم الحادي عشر", "الحلقة الحادية عشرة", "الموسم الخمسون", "الحلقة الخمسون", "واحد", "اثنان", "خمسون");
        assertOrdinalLabels("en-US", "Season 1", "Episode 1", "Season 11", "Episode 11", "Season 50", "Episode 50", "1", "2", "50");
    }

    private void assertOrdinalLabels(String localeTag,
                                     String season1,
                                     String episode1,
                                     String season11,
                                     String episode11,
                                     String season50,
                                     String episode50,
                                     String tab1,
                                     String tab2,
                                     String tab50) {
        I18n.setLocale(localeTag);
        assertEquals(season1, I18n.formatSeasonLabel("1"));
        assertEquals(episode1, I18n.formatEpisodeLabel("1"));
        assertEquals(season11, I18n.formatSeasonLabel("11"));
        assertEquals(episode11, I18n.formatEpisodeLabel("11"));
        assertEquals(season50, I18n.formatSeasonLabel("50"));
        assertEquals(episode50, I18n.formatEpisodeLabel("50"));
        assertEquals(tab1, I18n.formatTabNumberLabel("1"));
        assertEquals(tab2, I18n.formatTabNumberLabel("2"));
        assertEquals(tab50, I18n.formatTabNumberLabel("50"));
    }

    private Properties loadBundle(String fileName) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(I18N_DIR.resolve(fileName), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
