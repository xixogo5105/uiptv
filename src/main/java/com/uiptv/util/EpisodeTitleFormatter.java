package com.uiptv.util;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import static com.uiptv.util.StringUtils.isBlank;

public final class EpisodeTitleFormatter {
    private static final List<String> ORDINAL_URDU_PREFIXES = List.of(
            "pehla", "pehli", "doosra", "doosri", "teesra", "teesri",
            "chautha", "chauthi", "paanchva", "paanchvi", "paanchwa", "paanchwi",
            "chhata", "chhati", "saatva", "saatvi", "aathva", "aathvi",
            "nauva", "nauvi", "dasva", "dasvi"
    );
    private static final List<String> EPISODE_WORDS = List.of("qist", "kist", "episode", "ep", "kadi");

    private EpisodeTitleFormatter() {
    }

    public static String buildEpisodeDisplayTitle(String season, String episodeNumber, String title) {
        String normalizedSeason = isBlank(season) ? "1" : season;
        String seasonLabel = I18n.formatSeasonLabel(normalizedSeason);
        String episodeLabel = I18n.formatEpisodeLabel(episodeNumber);
        String cleanTitle = stripGenericEpisodeTitle(normalizedSeason, episodeNumber, title);
        if (isBlank(cleanTitle)) {
            return seasonLabel + " - " + episodeLabel;
        }
        return seasonLabel + " - " + episodeLabel + ": " + cleanTitle;
    }

    public static boolean isGenericEpisodeTitle(String title) {
        String value = safe(title).trim();
        if (isBlank(value)) {
            return true;
        }
        String normalized = normalizeAsciiWords(value);
        return matchesSimpleEpisodeNumber(normalized)
                || matchesOrdinalSeasonEpisode(normalized)
                || matchesSeasonEpisode(normalized);
    }

    static String stripGenericEpisodeTitle(String season, String episodeNumber, String title) {
        String value = safe(title).trim();
        if (isBlank(value)) {
            return "";
        }
        if (isGenericEpisodeTitle(value) || matchesLocalizedDisplayLabel(season, episodeNumber, value)) {
            return "";
        }
        return value;
    }

    static boolean matchesLocalizedDisplayLabel(String season, String episodeNumber, String title) {
        String normalizedTitle = normalizeForComparison(title);
        if (isBlank(normalizedTitle)) {
            return true;
        }
        String normalizedSeason = isBlank(season) ? "1" : season;
        String seasonLabel = I18n.formatSeasonLabel(normalizedSeason);
        String episodeLabel = I18n.formatEpisodeLabel(episodeNumber);
        String numericSeasonLabel = I18n.tr("autoSeasonNumber", I18n.formatNumber(normalizedSeason));
        String numericEpisodeLabel = I18n.tr("autoEpisodeNumber", I18n.formatNumber(isBlank(episodeNumber) ? "-" : episodeNumber));

        for (String candidate : List.of(
                seasonLabel + " - " + episodeLabel,
                seasonLabel + ": " + episodeLabel,
                seasonLabel + " " + episodeLabel,
                seasonLabel + "  " + episodeLabel,
                episodeLabel,
                seasonLabel + " - " + numericEpisodeLabel,
                seasonLabel + ": " + numericEpisodeLabel,
                seasonLabel + " " + numericEpisodeLabel,
                numericSeasonLabel + " - " + numericEpisodeLabel,
                numericSeasonLabel + ": " + numericEpisodeLabel,
                numericSeasonLabel + " " + numericEpisodeLabel,
                numericSeasonLabel + " - " + episodeLabel,
                numericSeasonLabel + ": " + episodeLabel,
                numericSeasonLabel + " " + episodeLabel,
                numericEpisodeLabel
        )) {
            if (normalizeForComparison(candidate).equals(normalizedTitle)) {
                return true;
            }
        }
        return false;
    }

    static String normalizeForComparison(String value) {
        String normalized = Normalizer.normalize(normalizeDigitsToAscii(safe(value)), Normalizer.Form.NFKC)
                .toLowerCase(I18n.getCurrentLocale());
        return normalized.replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String normalizeDigitsToAscii(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isDigit(ch)) {
                int numeric = Character.getNumericValue(ch);
                if (numeric >= 0 && numeric <= 9) {
                    out.append((char) ('0' + numeric));
                    continue;
                }
            }
            out.append(ch);
        }
        return out.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String normalizeAsciiWords(String value) {
        return normalizeDigitsToAscii(safe(value))
                .toLowerCase(Locale.ROOT)
                .replace('.', ' ')
                .replace(':', ' ')
                .replace('-', ' ')
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static boolean matchesSimpleEpisodeNumber(String value) {
        if (value.matches("^episode\\s+\\d+$")) {
            return true;
        }
        if (value.matches("^ep\\s+\\d+$")) {
            return true;
        }
        return value.matches("^e\\d+$");
    }

    private static boolean matchesOrdinalSeasonEpisode(String value) {
        String[] parts = value.split(" ");
        if (parts.length != 4 || !"season".equals(parts[1]) || !isEpisodeWord(parts[2])) {
            return false;
        }
        return ORDINAL_URDU_PREFIXES.contains(parts[0]) && isDigits(parts[3]);
    }

    private static boolean matchesSeasonEpisode(String value) {
        String[] parts = value.split(" ");
        if (parts.length != 4 || !"season".equals(parts[0]) || !isEpisodeWord(parts[2])) {
            return false;
        }
        return isDigits(parts[1]) && isDigits(parts[3]);
    }

    private static boolean isEpisodeWord(String value) {
        return EPISODE_WORDS.contains(value);
    }

    private static boolean isDigits(String value) {
        return !isBlank(value) && value.chars().allMatch(Character::isDigit);
    }
}
