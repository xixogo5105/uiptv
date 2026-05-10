package com.uiptv.util

import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

object EpisodeTitleFormatter {
    private val ORDINAL_URDU_PREFIXES = listOf(
        "pehla", "pehli", "doosra", "doosri", "teesra", "teesri",
        "chautha", "chauthi", "paanchva", "paanchvi", "paanchwa", "paanchwi",
        "chhata", "chhati", "saatva", "saatvi", "aathva", "aathvi",
        "nauva", "nauvi", "dasva", "dasvi"
    )
    private val EPISODE_WORDS = listOf("qist", "kist", "episode", "ep", "kadi")

    @JvmStatic
    fun buildEpisodeDisplayTitle(season: String?, episodeNumber: String?, title: String?): String {
        val normalizedSeason = if (StringUtils.isBlank(season)) "1" else season!!
        val seasonLabel = I18n.formatSeasonLabel(normalizedSeason)
        val episodeLabel = I18n.formatEpisodeLabel(episodeNumber)
        var cleanTitle = stripGenericEpisodeTitle(normalizedSeason, episodeNumber, title)
        cleanTitle = stripLeadingEpisodeMarker(normalizedSeason, episodeNumber, cleanTitle)
        return if (StringUtils.isBlank(cleanTitle)) {
            "$seasonLabel - $episodeLabel"
        } else {
            "$seasonLabel - $episodeLabel: $cleanTitle"
        }
    }

    @JvmStatic
    fun isGenericEpisodeTitle(title: String?): Boolean {
        val value = safe(title).trim()
        if (StringUtils.isBlank(value)) {
            return true
        }
        val normalized = normalizeAsciiWords(value)
        return matchesSimpleEpisodeNumber(normalized) ||
            matchesOrdinalSeasonEpisode(normalized) ||
            matchesSeasonEpisode(normalized) ||
            matchesLocalizedEpisodeWordTitle(normalized)
    }

    @JvmStatic
    fun stripGenericEpisodeTitle(season: String?, episodeNumber: String?, title: String?): String {
        val value = safe(title).trim()
        if (StringUtils.isBlank(value)) {
            return ""
        }
        return if (isGenericEpisodeTitle(value) || matchesLocalizedDisplayLabel(season, episodeNumber, value)) "" else value
    }

    @JvmStatic
    fun stripLeadingEpisodeMarker(season: String?, episodeNumber: String?, title: String?): String {
        val value = safe(title).trim()
        if (StringUtils.isBlank(value)) {
            return ""
        }
        val seasonDigits = normalizeDigitsToAscii(safe(season)).replace("\\D+".toRegex(), "")
        val episodeDigits = normalizeDigitsToAscii(safe(episodeNumber)).replace("\\D+".toRegex(), "")
        if (StringUtils.isBlank(episodeDigits)) {
            return value
        }
        var cleaned = value
        val separator = "\\s*[-:]*\\s*"
        if (StringUtils.isNotBlank(seasonDigits)) {
            cleaned = cleaned.replaceFirst("(?i)^\\s*season\\s*$seasonDigits\\s*(?:episode|ep|e)\\s*$episodeDigits\\b$separator".toRegex(), "")
            cleaned = cleaned.replaceFirst("(?i)^\\s*s\\s*$seasonDigits\\s*e\\s*$episodeDigits\\b$separator".toRegex(), "")
            cleaned = cleaned.replaceFirst("(?i)^\\s*$seasonDigits\\s*x\\s*$episodeDigits\\b$separator".toRegex(), "")
        }
        cleaned = cleaned.replaceFirst("(?i)^\\s*(?:episode|ep|e)\\s*$episodeDigits\\b$separator".toRegex(), "")
        return cleaned.trim()
    }

    @JvmStatic
    fun matchesLocalizedDisplayLabel(season: String?, episodeNumber: String?, title: String?): Boolean {
        val normalizedTitle = normalizeForComparison(title)
        if (StringUtils.isBlank(normalizedTitle)) {
            return true
        }
        val normalizedSeason = if (StringUtils.isBlank(season)) "1" else season!!
        val seasonLabel = I18n.formatSeasonLabel(normalizedSeason)
        val episodeLabel = I18n.formatEpisodeLabel(episodeNumber)
        val numericSeasonLabel = I18n.tr("autoSeasonNumber", I18n.formatNumber(normalizedSeason))
        val numericEpisodeLabel = I18n.tr("autoEpisodeNumber", I18n.formatNumber(if (StringUtils.isBlank(episodeNumber)) "-" else episodeNumber))

        for (candidate in listOf(
            "$seasonLabel - $episodeLabel",
            "$seasonLabel: $episodeLabel",
            "$seasonLabel $episodeLabel",
            "$seasonLabel  $episodeLabel",
            episodeLabel,
            "$seasonLabel - $numericEpisodeLabel",
            "$seasonLabel: $numericEpisodeLabel",
            "$seasonLabel $numericEpisodeLabel",
            "$numericSeasonLabel - $numericEpisodeLabel",
            "$numericSeasonLabel: $numericEpisodeLabel",
            "$numericSeasonLabel $numericEpisodeLabel",
            "$numericSeasonLabel - $episodeLabel",
            "$numericSeasonLabel: $episodeLabel",
            "$numericSeasonLabel $episodeLabel",
            numericEpisodeLabel
        )) {
            if (normalizeForComparison(candidate) == normalizedTitle) {
                return true
            }
        }
        val localizedEpisodeWord = localizedEpisodeWord(episodeLabel)
        if (StringUtils.isNotBlank(localizedEpisodeWord)) {
            val genericLocalizedEpisode = localizedEpisodeWord + " " + I18n.formatNumber(episodeNumber)
            if (normalizeForComparison(genericLocalizedEpisode) == normalizedTitle) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun normalizeForComparison(value: String?): String {
        val normalized = Normalizer.normalize(normalizeDigitsToAscii(safe(value)), Normalizer.Form.NFKC)
            .lowercase(I18n.getCurrentLocale())
        return normalized.replace("[^\\p{L}\\p{N}]+".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun normalizeDigitsToAscii(value: String): String {
        val out = StringBuilder(value.length)
        for (ch in value) {
            if (Character.isDigit(ch)) {
                val numeric = Character.getNumericValue(ch)
                if (numeric in 0..9) {
                    out.append(('0'.code + numeric).toChar())
                    continue
                }
            }
            out.append(ch)
        }
        return out.toString()
    }

    private fun safe(value: String?): String = value ?: ""

    private fun normalizeAsciiWords(value: String): String =
        normalizeDigitsToAscii(safe(value))
            .lowercase(Locale.ROOT)
            .replace('.', ' ')
            .replace(':', ' ')
            .replace('-', ' ')
            .trim()
            .replace("\\s+".toRegex(), " ")

    private fun matchesSimpleEpisodeNumber(value: String): Boolean =
        value.matches("^episode\\s+\\d+$".toRegex()) || value.matches("^ep\\s+\\d+$".toRegex()) || value.matches("^e\\d+$".toRegex())

    private fun matchesOrdinalSeasonEpisode(value: String): Boolean {
        val parts = value.split(" ")
        if (parts.size != 4 || parts[1] != "season" || !isEpisodeWord(parts[2])) {
            return false
        }
        return ORDINAL_URDU_PREFIXES.contains(parts[0]) && isDigits(parts[3])
    }

    private fun matchesSeasonEpisode(value: String): Boolean {
        val parts = value.split(" ")
        if (parts.size != 4 || parts[0] != "season" || !isEpisodeWord(parts[2])) {
            return false
        }
        return isDigits(parts[1]) && isDigits(parts[3])
    }

    private fun isEpisodeWord(value: String): Boolean = EPISODE_WORDS.contains(value)

    private fun isDigits(value: String): Boolean = StringUtils.isNotBlank(value) && value.chars().allMatch(Character::isDigit)

    private fun matchesLocalizedEpisodeWordTitle(normalizedTitle: String): Boolean {
        val localizedEpisodeWord = localizedEpisodeWord(I18n.formatEpisodeLabel("11"))
        if (StringUtils.isBlank(localizedEpisodeWord)) {
            return false
        }
        return normalizedTitle.matches(("^" + Pattern.quote(localizedEpisodeWord) + "\\s+\\d+$").toRegex())
    }

    private fun localizedEpisodeWord(episodeLabel: String?): String {
        val normalizedLabel = normalizeForComparison(episodeLabel)
        if (StringUtils.isBlank(normalizedLabel)) {
            return ""
        }
        val parts = normalizedLabel.split(" ")
        if (parts.size < 2) {
            return ""
        }
        val lastWord = parts.last()
        return if (lastWord.any { Character.isLetter(it) }) lastWord else ""
    }
}
