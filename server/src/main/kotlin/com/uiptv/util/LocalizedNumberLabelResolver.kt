package com.uiptv.util

import java.util.Locale
import java.util.ResourceBundle
import java.util.concurrent.ConcurrentHashMap

object LocalizedNumberLabelResolver {
    private const val BUNDLE_BASE_NAME = "i18n.ordinals.labels"
    private val CACHE = ConcurrentHashMap<String, ResourceBundle?>()

    @JvmStatic
    fun formatSeasonLabel(rawNumber: String, locale: Locale?): String = resolveLabel("season", rawNumber, locale)

    @JvmStatic
    fun formatEpisodeLabel(rawNumber: String, locale: Locale?): String = resolveLabel("episode", rawNumber, locale)

    @JvmStatic
    fun formatTabLabel(rawNumber: String, locale: Locale?): String = resolveLabel("tab", rawNumber, locale)

    private fun resolveLabel(prefix: String, rawNumber: String, locale: Locale?): String {
        val number = parseNumber(rawNumber)
        if (number <= 0) {
            return ""
        }
        val bundle = loadBundle(locale) ?: return ""
        val key = "$prefix.$number"
        return if (bundle.containsKey(key)) bundle.getString(key) else ""
    }

    private fun loadBundle(locale: Locale?): ResourceBundle? {
        if (locale == null || StringUtils.isBlank(locale.language)) {
            return null
        }
        val language = locale.language.lowercase(Locale.ROOT)
        return CACHE.computeIfAbsent(language, ::readBundle)
    }

    private fun readBundle(language: String): ResourceBundle? {
        return try {
            ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.forLanguageTag(language))
        } catch (_: Exception) {
            null
        }
    }

    private fun parseNumber(rawNumber: String): Int {
        if (StringUtils.isBlank(rawNumber)) {
            return -1
        }
        return try {
            rawNumber.trim().toInt()
        } catch (_: Exception) {
            -1
        }
    }
}
