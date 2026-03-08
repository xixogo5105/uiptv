package com.uiptv.util;

import java.text.Normalizer;
import java.util.List;

import static com.uiptv.util.StringUtils.isBlank;

public final class EpisodeTitleFormatter {
    private static final String GENERIC_EPISODE_SUFFIX = "\\s*[: -]?\\s*$";
    private static final String ORDINAL_URDU = "(?:pehl[ai]|doosr[ai]|teesr[ai]|chauth[ai]|paanch(?:va|vi|wa|wi)|chh(?:a|i)t[ai]|saat(?:va|vi)|aath(?:va|vi)|nau(?:va|vi)|das(?:va|vi))";
    private static final String EPISODE_WORD = "(?:qist|kist|episode|ep|kadi)";

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
        return value.matches("(?i)^episode\\s*\\d+" + GENERIC_EPISODE_SUFFIX)
                || value.matches("(?i)^ep\\.?\\s*\\d+" + GENERIC_EPISODE_SUFFIX)
                || value.matches("(?i)^e\\d+" + GENERIC_EPISODE_SUFFIX)
                || value.matches("(?i)^" + ORDINAL_URDU + "\\s+season\\s+" + EPISODE_WORD + "\\s*\\d+\\s*$")
                || value.matches("(?i)^season\\s*\\d+\\s+" + EPISODE_WORD + "\\s*\\d+\\s*$");
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
}
