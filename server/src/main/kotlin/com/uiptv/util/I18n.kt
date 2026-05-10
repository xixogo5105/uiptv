package com.uiptv.util

import java.text.MessageFormat
import java.time.LocalDate
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.Locale
import java.util.MissingResourceException
import java.util.ResourceBundle
import java.util.regex.Pattern

object I18n {
    const val DEFAULT_LANGUAGE_TAG = "en-US"
    private const val BUNDLE_BASE_NAME = "i18n.messages"
    private val RTL_LANGUAGE_CODES = setOf("ar", "fa", "he", "ur", "ps", "sd", "ug", "yi", "dv", "ckb")
    private val SUPPORTED_LANGUAGES = listOf(
        SupportedLanguage(DEFAULT_LANGUAGE_TAG, "English (United States)"),
        SupportedLanguage("en-GB", "English (United Kingdom)"),
        SupportedLanguage("es-ES", "Español"),
        SupportedLanguage("fr-FR", "Français"),
        SupportedLanguage("de-DE", "Deutsch"),
        SupportedLanguage("it-IT", "Italiano"),
        SupportedLanguage("pt-BR", "Português (Brasil)"),
        SupportedLanguage("pt-PT", "Português (Portugal)"),
        SupportedLanguage("ru-RU", "Русский"),
        SupportedLanguage("uk-UA", "Українська"),
        SupportedLanguage("tr-TR", "Türkçe"),
        SupportedLanguage("zh-CN", "简体中文"),
        SupportedLanguage("zh-TW", "繁體中文"),
        SupportedLanguage("ja-JP", "日本語"),
        SupportedLanguage("ko-KR", "한국어"),
        SupportedLanguage("id-ID", "Bahasa Indonesia"),
        SupportedLanguage("vi-VN", "Tiếng Việt"),
        SupportedLanguage("th-TH", "ไทย"),
        SupportedLanguage("ar-SA", "العربية"),
        SupportedLanguage("ur-PK", "اردو"),
        SupportedLanguage("hi-IN", "हिन्दी"),
        SupportedLanguage("bn-BD", "বাংলা"),
        SupportedLanguage("pa-IN", "ਪੰਜਾਬੀ"),
        SupportedLanguage("te-IN", "తెలుగు"),
        SupportedLanguage("ta-IN", "தமிழ்"),
        SupportedLanguage("ml-IN", "മലയാളം")
    )
    private val LOCK = Any()
    private val TOKEN_ARTIFACT_PATTERN = Pattern.compile("(?:__\\s*T\\s*K\\d+_+|__\\d+__|ForTK\\d+__)")

    private var currentLocale: Locale = Locale.forLanguageTag(DEFAULT_LANGUAGE_TAG)
    private var bundle: ResourceBundle? = loadBundle(currentLocale)

    @JvmStatic
    fun initialize(languageTag: String?) {
        setLocale(languageTag)
    }

    @JvmStatic
    fun setLocale(languageTag: String?) {
        synchronized(LOCK) {
            currentLocale = resolveLocale(languageTag)
            Locale.setDefault(currentLocale)
            ResourceBundle.clearCache()
            bundle = loadBundle(currentLocale)
        }
    }

    @JvmStatic
    fun getCurrentLanguageTag(): String = synchronized(LOCK) { currentLocale.toLanguageTag() }

    @JvmStatic
    fun getCurrentLocale(): Locale = synchronized(LOCK) { currentLocale }

    @JvmStatic
    fun getSupportedLanguages(): List<SupportedLanguage> = SUPPORTED_LANGUAGES

    @JvmStatic
    fun resolveSupportedLanguage(languageTag: String?): SupportedLanguage {
        val normalizedTag = normalizeLanguageTag(languageTag)
        return SUPPORTED_LANGUAGES.firstOrNull { it.languageTag.equals(normalizedTag, ignoreCase = true) }
            ?: SUPPORTED_LANGUAGES.first()
    }

    @JvmStatic
    fun normalizeLanguageTag(languageTag: String?): String = resolveLocale(languageTag).toLanguageTag()

    @JvmStatic
    fun isCurrentLocaleRtl(): Boolean = synchronized(LOCK) {
        val language = currentLocale.language.lowercase(Locale.ROOT)
        val script = currentLocale.script.lowercase(Locale.ROOT)
        RTL_LANGUAGE_CODES.contains(language) || script == "arab" || script == "hebr"
    }

    @JvmStatic
    fun tr(key: String?, vararg args: Any?): String {
        val pattern = lookupOrFallback(key)
        if (args.isEmpty()) {
            return normalizeResolvedText(pattern)
        }
        return try {
            normalizeResolvedText(MessageFormat.format(pattern, *args))
        } catch (_: Exception) {
            normalizeResolvedText(pattern)
        }
    }

    @JvmStatic
    fun trEnglish(key: String?, vararg args: Any?): String = trForLocale(Locale.forLanguageTag(DEFAULT_LANGUAGE_TAG), key, *args)

    @JvmStatic
    fun formatDate(date: LocalDate?): String {
        if (date == null) return ""
        val locale = getDisplayLocale()
        if (locale.language.equals("en", ignoreCase = true)) {
            val monthYear = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH).format(date)
            return englishOrdinal(date.dayOfMonth) + " " + monthYear
        }
        var pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.LONG, null, IsoChronology.INSTANCE, locale)
        pattern = pattern.replace(",", "").replace("،", "").replace("\\s+".toRegex(), " ").trim()
        return DateTimeFormatter.ofPattern(pattern, locale).withDecimalStyle(DecimalStyle.of(locale)).format(date)
    }

    @JvmStatic
    fun formatNumber(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val locale = getDisplayLocale()
        val zeroDigit = DecimalStyle.of(locale).zeroDigit
        val out = StringBuilder(value.length)
        for (ch in value) {
            if (ch in '0'..'9') {
                out.append((zeroDigit.code + (ch - '0')).toChar())
            } else {
                out.append(ch)
            }
        }
        return out.toString()
    }

    @JvmStatic
    fun formatSeasonLabel(seasonNumber: String?): String {
        val localized = LocalizedNumberLabelResolver.formatSeasonLabel(seasonNumber.orEmpty(), getCurrentLocale())
        if (localized.isNotBlank()) return localized
        return tr("autoSeasonNumber", formatNumber(if (seasonNumber.isNullOrBlank()) "1" else seasonNumber))
    }

    @JvmStatic
    fun formatEpisodeLabel(episodeNumber: String?): String {
        if (episodeNumber.isNullOrBlank()) return tr("autoEpisodeNumber", "-")
        val localized = LocalizedNumberLabelResolver.formatEpisodeLabel(episodeNumber, getCurrentLocale())
        if (localized.isNotBlank()) return localized
        return tr("autoEpisodeNumber", formatNumber(episodeNumber))
    }

    @JvmStatic
    fun formatTabNumberLabel(rawNumber: String?): String {
        val localized = LocalizedNumberLabelResolver.formatTabLabel(rawNumber.orEmpty(), getCurrentLocale())
        if (localized.isNotBlank()) return localized
        return formatNumber(if (rawNumber.isNullOrBlank()) "1" else rawNumber)
    }

    private fun englishOrdinal(dayOfMonth: Int): String {
        val mod100 = dayOfMonth % 100
        if (mod100 in 11..13) return "${dayOfMonth}th"
        return when (dayOfMonth % 10) {
            1 -> "${dayOfMonth}st"
            2 -> "${dayOfMonth}nd"
            3 -> "${dayOfMonth}rd"
            else -> "${dayOfMonth}th"
        }
    }

    private fun getDisplayLocale(): Locale {
        val locale = getCurrentLocale()
        val numberingSystem = when (locale.language) {
            "ar" -> "arab"
            "ur", "fa", "ps", "sd" -> "arabext"
            "bn" -> "beng"
            else -> ""
        }
        if (numberingSystem.isBlank()) return locale
        return Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", numberingSystem).build()
    }

    private fun lookupOrFallback(key: String?): String {
        if (key.isNullOrBlank()) return ""
        synchronized(LOCK) {
            if (bundle != null && bundle!!.containsKey(key)) {
                return bundle!!.getString(key)
            }
            try {
                val fallbackBundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.forLanguageTag(DEFAULT_LANGUAGE_TAG), I18n::class.java.classLoader)
                if (fallbackBundle.containsKey(key)) {
                    return fallbackBundle.getString(key)
                }
            } catch (_: Exception) {
            }
        }
        return key
    }

    private fun trForLocale(locale: Locale, key: String?, vararg args: Any?): String {
        val pattern = lookupOrFallback(locale, key)
        if (args.isEmpty()) return normalizeResolvedText(pattern)
        return try {
            normalizeResolvedText(MessageFormat.format(pattern, *args))
        } catch (_: Exception) {
            normalizeResolvedText(pattern)
        }
    }

    private fun lookupOrFallback(locale: Locale, key: String?): String {
        return try {
            val localBundle = loadBundle(locale)
            if (localBundle != null && localBundle.containsKey(key)) localBundle.getString(key) else lookupOrFallback(key)
        } catch (_: Exception) {
            lookupOrFallback(key)
        }
    }

    private fun normalizeResolvedText(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        val normalized = value
            .replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\\r", "\n")
            .replace("\\:", ":")
            .replace("\\=", "=")
            .replace("\\/", "/")
        return TOKEN_ARTIFACT_PATTERN.matcher(normalized).replaceAll("\n")
    }

    private fun resolveLocale(languageTag: String?): Locale {
        if (languageTag.isNullOrBlank()) return Locale.forLanguageTag(DEFAULT_LANGUAGE_TAG)
        val locale = Locale.forLanguageTag(languageTag.trim())
        if (locale.language.isBlank()) return Locale.forLanguageTag(DEFAULT_LANGUAGE_TAG)
        return locale
    }

    private fun loadBundle(locale: Locale): ResourceBundle? {
        return try {
            ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale, I18n::class.java.classLoader)
        } catch (_: MissingResourceException) {
            try {
                ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale)
            } catch (_: MissingResourceException) {
                try {
                    ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.forLanguageTag(DEFAULT_LANGUAGE_TAG))
                } catch (_: MissingResourceException) {
                    null
                }
            }
        }
    }

    data class SupportedLanguage(val languageTag: String, val nativeDisplayName: String) {
        fun languageTag(): String = languageTag

        fun nativeDisplayName(): String = nativeDisplayName

        override fun toString(): String = nativeDisplayName
    }
}
