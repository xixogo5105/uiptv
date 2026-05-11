package com.uiptv.service

import com.uiptv.db.ConfigurationDb
import com.uiptv.model.Account
import com.uiptv.model.Configuration
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

object ConfigurationService {
    private val changeRevision = AtomicLong(1)
    private val changeListeners = CopyOnWriteArraySet<ConfigurationChangeListener>()

    const val DEFAULT_CACHE_EXPIRY_DAYS = 30
    const val DEFAULT_UI_ZOOM_PERCENT = 100
    const val DEFAULT_VLC_CACHING_MS = "1000"
    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

    @JvmField
    val FIREFOX_ZOOM_PERCENT_OPTIONS = listOf(50, 75, 80, 90, 95, 100, 105, 110, 115, 120, 125, 133, 140, 150, 170, 200, 250, 300)

    @JvmField
    val VLC_CACHING_OPTIONS_MS = listOf("", "1000", "2000", "3000", "4000", "5000", "10000", "15000", "20000", "25000", "30000", "60000")

    fun clearCache(account: Account?) {
        ConfigurationDb.get().clearCache(account)
    }

    fun clearAllCache() {
        ConfigurationDb.get().clearAllCache()
    }

    fun save(configuration: Configuration) {
        ConfigurationDb.get().save(configuration)
        notifyConfigurationChanged()
    }

    fun read(): Configuration = ConfigurationDb.get().getConfiguration()

    fun addChangeListener(listener: ConfigurationChangeListener?) {
        if (listener != null) {
            changeListeners.add(listener)
        }
    }

    fun removeChangeListener(listener: ConfigurationChangeListener?) {
        if (listener != null) {
            changeListeners.remove(listener)
        }
    }

    fun notifyConfigurationChanged() {
        val revision = changeRevision.incrementAndGet()
        changeListeners.forEach { listener ->
            try {
                listener.onConfigurationChanged(revision)
            } catch (_: Exception) {
                // Listener failures must never break configuration updates.
            }
        }
    }

    fun getCacheExpiryDays(): Int = normalizeCacheExpiryDays(read().cacheExpiryDays)

    fun getCacheExpiryMs(): Long = getCacheExpiryDays() * MILLIS_PER_DAY

    fun getUiZoomPercent(): Int = normalizeUiZoomPercent(read().uiZoomPercent)

    fun normalizeUiZoomPercent(rawZoomPercent: String?): Int {
        if (rawZoomPercent.isNullOrBlank()) {
            return DEFAULT_UI_ZOOM_PERCENT
        }
        return rawZoomPercent.trim().toIntOrNull()
            ?.takeIf(FIREFOX_ZOOM_PERCENT_OPTIONS::contains)
            ?: DEFAULT_UI_ZOOM_PERCENT
    }

    fun normalizeCacheExpiryDays(rawDays: String?): Int {
        if (rawDays.isNullOrBlank()) {
            return DEFAULT_CACHE_EXPIRY_DAYS
        }
        return rawDays.trim().toIntOrNull()
            ?.takeIf { it > 0 }
            ?: DEFAULT_CACHE_EXPIRY_DAYS
    }

    fun normalizeVlcCachingMs(rawCachingMs: String?): String {
        if (rawCachingMs == null) {
            return DEFAULT_VLC_CACHING_MS
        }
        val normalized = rawCachingMs.trim()
        if (normalized.isEmpty()) {
            return ""
        }
        return if (VLC_CACHING_OPTIONS_MS.contains(normalized)) normalized else DEFAULT_VLC_CACHING_MS
    }

    fun isVlcHttpUserAgentEnabled(): Boolean = read().enableVlcHttpUserAgent

    fun isResolveChainAndDeepRedirectsEnabled(): Boolean =
        try {
            read().resolveChainAndDeepRedirects
        } catch (_: RuntimeException) {
            false
        }

    fun isResolveChainAndDeepRedirectsEnabled(account: Account?): Boolean {
        if (isResolveChainAndDeepRedirectsEnabled()) {
            return true
        }
        return account?.resolveChainAndDeepRedirects == true
    }

    fun getPublishedM3uCategoryMode(): M3U8PublicationService.PublishedCategoryMode =
        M3U8PublicationService.PublishedCategoryMode.fromPersistedValue(read().publishedM3uCategoryMode)
}
