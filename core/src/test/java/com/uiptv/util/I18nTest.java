package com.uiptv.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.TreeSet;
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
    void everyTranslationKeyUsedInCodeExistsInTheBundles() throws IOException {
        // allMessageBundlesHaveSameKeysAsBaseBundle only proves the bundles agree with each other.
        // A key that every bundle spells the same wrong way still passes that check while rendering
        // as its own raw name, because I18n.lookupOrFallback returns the key when it cannot
        // resolve it. This test closes the other half: code against bundles.
        //
        // The bar is the en-US bundle, not messages.properties. I18n.lookupOrFallback resolves the
        // display locale first and then falls back to DEFAULT_LANGUAGE_TAG (en-US), so a key
        // present in en-US is renderable in every locale. messages.properties is only the
        // locale-less base of the ResourceBundle chain and legitimately holds fewer keys.
        Set<String> bundleKeys = loadBundle("messages_en_US.properties").stringPropertyNames();
        Set<String> lowerCasedKeys = new HashSet<>();
        for (String key : bundleKeys) {
            lowerCasedKeys.add(key.toLowerCase(Locale.ROOT));
        }

        Set<String> unresolved = new TreeSet<>();
        Set<String> caseMismatches = new TreeSet<>();
        for (Path javaFile : productionSourceFiles()) {
            String source = Files.readString(javaFile, StandardCharsets.UTF_8);
            // Resolve string constants declared in the same file, so I18n.tr(SOME_CONST) is
            // checked against the literal it actually refers to.
            Map<String, String> constants = new HashMap<>();
            Matcher constMatcher = CONSTANT_PATTERN.matcher(source);
            while (constMatcher.find()) {
                constants.put(constMatcher.group(1), constMatcher.group(2));
            }
            Matcher usageMatcher = TR_CALL_PATTERN.matcher(source);
            while (usageMatcher.find()) {
                String literal = usageMatcher.group(1);
                String constant = usageMatcher.group(2);
                String key = literal != null ? literal : constants.get(constant);
                if (key == null || key.isBlank()) {
                    // A computed key cannot be checked statically; nothing to assert.
                    continue;
                }
                if (bundleKeys.contains(key)) {
                    continue;
                }
                if (lowerCasedKeys.contains(key.toLowerCase(Locale.ROOT))) {
                    caseMismatches.add(key + "  (" + javaFile + ")");
                } else {
                    unresolved.add(key + "  (" + javaFile + ")");
                }
            }
        }

        assertTrue(caseMismatches.isEmpty(),
                "Translation keys used in code differ from the bundles only by case, so I18n.tr "
                        + "renders the raw key name. Rename the key in every bundle: " + caseMismatches);
        assertTrue(unresolved.isEmpty(),
                "Translation keys used in code are missing from every bundle, so I18n.tr renders "
                        + "the raw key name: " + unresolved);
    }

    private static final Pattern CONSTANT_PATTERN =
            Pattern.compile("String\\s+([A-Z][A-Z0-9_]+)\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern TR_CALL_PATTERN = Pattern.compile(
            "\\b(?:I18n|UiI18n)\\.tr(?:English)?\\(\\s*(?:\"([^\"]+)\"|([A-Z][A-Z0-9_]+))\\s*[,)]");

    /**
     * Java sources of every module that resolves translations through {@link I18n}. Paths are
     * relative to this module's basedir, which is where surefire runs.
     */
    private static List<Path> productionSourceFiles() throws IOException {
        List<Path> roots = List.of(
                Path.of("src/main/java"),
                Path.of("..", "javafx-app", "src/main/java"),
                Path.of("..", "api-server", "src/main/java"),
                Path.of("..", "lightweight-ui", "src/main/java"));
        List<Path> files = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> found = Files.walk(root)) {
                found.filter(path -> path.toString().endsWith(".java")).sorted().forEach(files::add);
            }
        }
        assertFalse(files.isEmpty(), "Expected to find production sources to audit.");
        return files;
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
        // Use a more efficient pattern: check for any digit without leading .*
        assertFalse(formatted.matches("[^0-9]*[0-9].*"), "Urdu date should use localized numerals.");
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

    @Test
    void publicFormattingAndFallbackBranchesAreCovered() {
        I18n.initialize("en-US");

        assertEquals("en-US", I18n.resolveSupportedLanguage("en-US").languageTag());
        assertEquals("en-US", I18n.resolveSupportedLanguage("not-a-real-tag").languageTag());
        assertEquals("en-US", I18n.normalizeLanguageTag(null));
        assertEquals("en-US", I18n.normalizeLanguageTag(""));
        assertEquals("en-US", I18n.getCurrentLocale().toLanguageTag());
        assertFalse(I18n.getSupportedLanguages().isEmpty());
        assertEquals("English (United States)", I18n.getSupportedLanguages().getFirst().toString());

        assertEquals("", I18n.tr(null));
        assertEquals("missing.key", I18n.tr("missing.key"));
        assertEquals("missing.key", I18n.trEnglish("missing.key"));
        assertEquals("", I18n.formatDate(null));
        assertEquals("1st January 2024", I18n.formatDate(LocalDate.of(2024, 1, 1)));
        assertEquals("2nd January 2024", I18n.formatDate(LocalDate.of(2024, 1, 2)));
        assertEquals("3rd January 2024", I18n.formatDate(LocalDate.of(2024, 1, 3)));
        assertEquals("4th January 2024", I18n.formatDate(LocalDate.of(2024, 1, 4)));
        assertEquals("11th January 2024", I18n.formatDate(LocalDate.of(2024, 1, 11)));
        assertEquals("", I18n.formatNumber(null));
        assertEquals("", I18n.formatNumber(""));
        assertEquals("A12B", I18n.formatNumber("A12B"));
        assertEquals("Season 1", I18n.formatSeasonLabel(""));
        assertEquals("Episode -", I18n.formatEpisodeLabel(""));
        assertEquals("1", I18n.formatTabNumberLabel(""));

        I18n.setLocale("bn-BD");
        assertEquals("১২", I18n.formatNumber("12"));
        I18n.setLocale("fa-IR");
        assertEquals("۱۲", I18n.formatNumber("12"));
    }

    @Test
    void privateFallbackHelpersNormalizeArtifactsAndInvalidLocales() throws Exception {
        assertEquals("\nline\nnext\nthird\nkey=value/path\n",
                invokeString("normalizeResolvedText", "\\r\\nline\\nnext\\rthird\\nkey\\=value\\/path__TK1__"));
        assertEquals("", invokeString("normalizeResolvedText", ""));
        assertEquals("en-US", ((Locale) invoke("resolveLocale", new Class[]{String.class}, " ")).toLanguageTag());
        assertEquals("en-US", ((Locale) invoke("resolveLocale", new Class[]{String.class}, "und")).toLanguageTag());
        assertEquals("missing.private.key", invokeString("lookupOrFallback", "missing.private.key"));
        assertEquals("missing.private.key", invoke("lookupOrFallback", new Class[]{Locale.class, String.class},
                Locale.forLanguageTag("zz-ZZ"), "missing.private.key"));
        assertEquals("missing.private.key", invoke("trForLocale", new Class[]{Locale.class, String.class, Object[].class},
                Locale.forLanguageTag("zz-ZZ"), "missing.private.key", new Object[]{"ignored"}));
        assertEquals("Close", invoke("lookupOrFallback", new Class[]{Locale.class, String.class},
                Locale.forLanguageTag("zz-ZZ"), "commonClose"));
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

    private String invokeString(String name, Object... args) throws Exception {
        Class<?>[] parameterTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            parameterTypes[i] = args[i].getClass();
        }
        Object result = invoke(name, parameterTypes, args);
        return result == null ? null : result.toString();
    }

    private Object invoke(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = I18n.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }
}
