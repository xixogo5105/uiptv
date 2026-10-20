package com.uiptv.application;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data-driven territory recognition for EPG channel labels and source metadata.
 *
 * Channel names are not authoritative identities. This class only extracts an
 * explicit, bounded territory marker; the resolver still prefers provider and
 * XMLTV identities before falling back to names.
 */
public final class EpgTerritory {
    /** Bump when the alias vocabulary or extraction policy changes. */
    public static final String SIGNATURE_VERSION = "territory-v1";
    public record Context(String name, Set<String> territories, boolean ambiguous) {
        public Context {
            name = Objects.toString(name, "").strip();
            territories = Set.copyOf(territories == null ? Set.of() : territories);
        }
        public String territory() { return territories.size() == 1 ? territories.iterator().next() : ""; }
    }

    private static final Pattern GROUP = Pattern.compile("^\\s*[┃│|]?\\s*([^┃│|]+?)\\s*[┃│|]\\s*");
    private static final Pattern BRACKET = Pattern.compile("[\\(\\[\\{]([^\\)\\]\\}]{1,160})[\\)\\]\\}]");
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}+]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern QUALITY_SUFFIX = Pattern.compile("(?:\\s|[-_/])+(?:sd|hd|fhd|uhd|4k|8k|\\d{3,4}p|\\d{3,4}i)$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> ISO_LANGUAGE_CODES = Arrays.stream(Locale.getISOLanguages())
            .map(value -> value.toUpperCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Set<String> COMMON_TERRITORY_MARKERS = Set.of("UK", "IN", "US", "PK", "AU", "NZ", "CA");
    // SD is both the ISO-3166 alpha-2 code for South Sudan and a ubiquitous
    // playlist quality label. Treat it as a country only when the label makes
    // the intent explicit (for example "Channel (SD)" or source metadata).
    private static final Set<String> QUALITY_CODE_COLLISIONS = Set.of("SD");
    private static final List<String> LEGAL_PREFIXES = List.of(
            "the ", "islamic republic of ", "republic of ", "democratic republic of ",
            "federal republic of ", "people's republic of ", "socialist republic of ",
            "united republic of ", "plurinational state of ", "bolivarian republic of ",
            "kingdom of ", "state of ", "commonwealth of ", "federation of ",
            "grand duchy of ", "principality of ", "sultanate of ");

    private static final Map<String, Set<String>> ALIASES = buildAliases();
    private static final Map<String, Set<String>> ALIAS_TO_CODES = invert(ALIASES);
    private static final List<String> SORTED_ALIASES = ALIAS_TO_CODES.keySet().stream()
            .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()))
            .toList();

    private EpgTerritory() { }

    private static Map<String, Set<String>> buildAliases() {
        Map<String, Set<String>> result = new TreeMap<>();
        for (String alpha2 : Locale.getISOCountries()) {
            Locale locale = new Locale("", alpha2);
            Set<String> aliases = new LinkedHashSet<>();
            add(aliases, alpha2);
            try { add(aliases, locale.getISO3Country()); } catch (MissingResourceException ignored) { }
            String display = locale.getDisplayCountry(Locale.ENGLISH);
            add(aliases, display);
            addLegalVariants(aliases, display);
            result.put(alpha2, Set.copyOf(aliases));
        }

        // Common IPTV and internationally used names which are not guaranteed
        // to be emitted by the JDK's locale database.
        addAliases(result, "GB", "UK", "GBR", "Great Britain", "United Kingdom of Great Britain and Northern Ireland");
        addAliases(result, "US", "USA", "United States of America");
        addAliases(result, "IN", "IND", "Bharat", "Hindustan", "Republic of India");
        addAliases(result, "PK", "PAK", "Pakistan", "Islamic Republic of Pakistan");
        addAliases(result, "AE", "UAE", "United Arab Emirates");
        addAliases(result, "SA", "KSA", "Kingdom of Saudi Arabia");
        addAliases(result, "AU", "AUS");
        addAliases(result, "CA", "CAN");
        addAliases(result, "NZ", "NZL", "Aotearoa");
        addAliases(result, "DE", "GER", "Deutschland");
        addAliases(result, "IT", "ITA", "Italia");
        addAliases(result, "ES", "ESP", "España");
        addAliases(result, "PT", "PRT");
        addAliases(result, "NL", "NLD", "Holland");
        addAliases(result, "CH", "CHE", "Swiss");
        addAliases(result, "AT", "AUT", "Österreich");
        addAliases(result, "IE", "IRL", "Eire");
        addAliases(result, "ZA", "ZAF", "Republic of South Africa");
        addAliases(result, "BR", "BRA", "Brasil");
        addAliases(result, "JP", "JPN", "Nippon");
        addAliases(result, "CN", "CHN", "PRC", "People's Republic of China");
        addAliases(result, "KR", "KOR", "South Korea", "Republic of Korea");
        addAliases(result, "KP", "PRK", "North Korea", "Democratic People's Republic of Korea");
        addAliases(result, "RU", "RUS", "Russian Federation");
        addAliases(result, "TR", "TUR", "Türkiye", "Turkey");
        addAliases(result, "IR", "IRN", "Islamic Republic of Iran");
        addAliases(result, "CZ", "CZE", "Czechia", "Czech Republic");
        addAliases(result, "GR", "GRC", "Hellenic Republic");
        addAliases(result, "CI", "CIV", "Côte d'Ivoire", "Ivory Coast");
        addAliases(result, "CD", "COD", "DRC", "Democratic Republic of the Congo");
        addAliases(result, "CG", "COG", "Republic of the Congo");
        addAliases(result, "BO", "BOL", "Plurinational State of Bolivia");
        addAliases(result, "VE", "VEN", "Bolivarian Republic of Venezuela");
        addAliases(result, "TZ", "TZA", "United Republic of Tanzania");
        addAliases(result, "MD", "MDA", "Republic of Moldova");
        addAliases(result, "MK", "MKD", "North Macedonia", "Macedonia");
        return Map.copyOf(result);
    }

    private static void addAliases(Map<String, Set<String>> result, String code, String... aliases) {
        Set<String> merged = new LinkedHashSet<>(result.getOrDefault(code, Set.of()));
        for (String alias : aliases) { add(merged, alias); addLegalVariants(merged, alias); }
        result.put(code, Set.copyOf(merged));
    }

    private static void add(Set<String> target, String value) {
        String normalized = normalizeToken(value);
        if (!normalized.isBlank()) target.add(normalized);
    }

    private static void addLegalVariants(Set<String> target, String value) {
        String normalized = normalizeToken(value);
        if (normalized.isBlank()) return;
        for (String prefix : LEGAL_PREFIXES) {
            String normalizedPrefix = normalizeToken(prefix);
            if (normalized.startsWith(normalizedPrefix) && normalized.length() > normalizedPrefix.length()) {
                add(target, normalized.substring(normalizedPrefix.length()));
            }
        }
    }

    private static Map<String, Set<String>> invert(Map<String, Set<String>> byCode) {
        Map<String, Set<String>> result = new HashMap<>();
        byCode.forEach((code, aliases) -> aliases.forEach(alias ->
                result.computeIfAbsent(alias, _ -> new LinkedHashSet<>()).add(code)));
        return result.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }

    /** Normalize names for exact alias lookup while retaining Unicode letters. */
    public static String normalizeToken(String value) {
        if (value == null) return "";
        String folded = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replace('’', '\'');
        return SEPARATORS.matcher(folded).replaceAll(" ").strip();
    }

    /**
     * Remove a leading playlist/index number only as a fallback matching form.
     * Numbers elsewhere remain meaningful: "Star Gold 2" must stay distinct.
     */
    public static String stripLeadingPlaylistIndex(String value) {
        if (value == null) return "";
        return value.strip().replaceFirst("^\\d{1,4}\\s+(?=\\p{L})", "").strip();
    }

    private static Set<String> matchingCodes(String value) {
        String normalized = normalizeToken(value);
        if (normalized.isBlank()) return Set.of();
        return ALIAS_TO_CODES.getOrDefault(normalized, Set.of());
    }

    private static Set<String> matchingCodesInSegment(String value) {
        return matchingCodesInSegment(value, true);
    }

    @SuppressWarnings("javabugs:S2259")
    private static Set<String> matchingCodesInSegment(String value, boolean strongMarker) {
        // normalizeToken and matchingCodes both return non-null values; the guard below also
        // documents that contract for analyzers that do not follow those helper guarantees.
        String normalized = normalizeToken(value);
        String core = normalized.replaceAll("(?:\\s|[-_/])+(?:HD|FHD|UHD|4K|8K|\\d{3,4}P|\\d{3,4}I)$", "");
        if (!strongMarker) core = core.replaceAll("(?:\\s|[-_/])+SD$", "");
        Set<String> result = new LinkedHashSet<>();
        Set<String> exact = matchingCodes(core);
        if (exact == null) exact = Set.of();
        if (strongMarker || core.length() != 2 || !ISO_LANGUAGE_CODES.contains(core)
                || COMMON_TERRITORY_MARKERS.contains(core)) result.addAll(exact);
        if (exact.isEmpty() && !normalized.isBlank()) {
            for (String token : normalized.split(" ")) {
                if (Set.of("SD", "HD", "FHD", "UHD", "4K", "8K").contains(token)) continue;
                if (!strongMarker && token.length() == 2 && ISO_LANGUAGE_CODES.contains(token)
                        && !COMMON_TERRITORY_MARKERS.contains(token)) continue;
                Set<String> tokenCodes = matchingCodes(token);
                if (tokenCodes.size() == 1) result.addAll(tokenCodes);
            }
        }
        return Set.copyOf(result);
    }

    /** Parse an explicit channel-label territory without treating language labels as countries. */
    @SuppressWarnings("javabugs:S2259")
    public static Context parse(String value) {
        String original = value == null ? "" : value.strip();
        String normalized = XmltvGuide.normalize(original);
        Set<String> found = new LinkedHashSet<>();

        Matcher group = GROUP.matcher(original);
        if (group.find()) {
            Set<String> codes = matchingCodesInSegment(group.group(1), false);
            found.addAll(codes);
            // IPTV category/language prefixes are presentation metadata even
            // when they are not a recognized country, so remove them from the
            // name used for schedule lookup in either case.
            normalized = XmltvGuide.normalize(original.substring(group.end()));
        } else {
            int pipe = original.indexOf('|');
            if (pipe > 0) {
                Set<String> codes = matchingCodesInSegment(original.substring(0, pipe), false);
                found.addAll(codes);
                normalized = XmltvGuide.normalize(original.substring(pipe + 1));
            }
        }

        Matcher bracket = BRACKET.matcher(original);
        while (bracket.find()) {
            String bracketText = XmltvGuide.normalize(bracket.group(1));
            if (bracketText.equals("geo-blocked") || bracketText.equals("geo blocked")) continue;
            Set<String> codes = matchingCodesInSegment(bracket.group(1));
            found.addAll(codes);
            if (!codes.isEmpty()) normalized = normalized.replace(XmltvGuide.normalize(bracket.group(0)), " ");
        }

        normalized = XmltvGuide.normalizeDisplayName(normalized);
        String withoutQuality = normalizeToken(normalized);
        Matcher quality = QUALITY_SUFFIX.matcher(withoutQuality);
        while (quality.find()) {
            String suffix = withoutQuality.substring(quality.start()).strip();
            if (matchingCodes(suffix).size() == 1) break;
            withoutQuality = withoutQuality.substring(0, quality.start()).strip();
            quality = QUALITY_SUFFIX.matcher(withoutQuality);
        }
        for (String alias : SORTED_ALIASES) {
            if (alias.length() < 2 || withoutQuality.length() <= alias.length()) continue;
            if (QUALITY_CODE_COLLISIONS.contains(alias)) continue;
            if (withoutQuality.endsWith(" " + alias)) {
                found.addAll(ALIAS_TO_CODES.getOrDefault(alias, Set.of()));
                int start = withoutQuality.length() - alias.length();
                normalized = XmltvGuide.normalize(withoutQuality.substring(0, start).strip());
                break;
            }
        }

        boolean ambiguous = found.size() > 1;
        return new Context(XmltvGuide.normalize(normalized), found, ambiguous);
    }

    /** Parse source metadata such as "IN,GB" or full country names. */
    public static Set<String> parseCountries(String value) {
        if (value == null || value.isBlank()) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (String part : value.split("[,;|]")) result.addAll(matchingCodesInSegment(part));
        return Set.copyOf(result);
    }

    /** Infer a territory from a DNS-like XMLTV/provider identity when possible. */
    public static Set<String> identifierCountries(String value) {
        if (value == null || value.isBlank()) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (String part : value.split("[.\\-_/ :]+")) result.addAll(matchingCodes(part));
        return Set.copyOf(result);
    }

    /** Prefer declared source territory; otherwise use the guide or source identity convention. */
    public static Set<String> sourceCountries(String declared, String sourceId, String guideId) {
        Set<String> result = parseCountries(declared);
        if (!result.isEmpty()) return result;
        result = identifierCountries(guideId);
        return result.isEmpty() ? identifierCountries(sourceId) : result;
    }

    public static boolean conflicts(Set<String> expected, Set<String> actual) {
        return !expected.isEmpty() && !actual.isEmpty() && Collections.disjoint(expected, actual);
    }

    public static boolean disjointKnown(Set<String> left, Set<String> right) {
        return !left.isEmpty() && !right.isEmpty() && Collections.disjoint(left, right);
    }

    public static String fingerprintValue() { return SIGNATURE_VERSION; }
}
