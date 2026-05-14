package com.uiptv.mobile.shared.settings

data class AndroidFilterSettings(
    val categoryFilters: String = "",
    val channelFilters: String = "",
    val paused: Boolean = false,
    val enableThumbnails: Boolean = false
)

interface AndroidFilterSettingsRepository {
    suspend fun load(): AndroidFilterSettings

    suspend fun save(settings: AndroidFilterSettings)

    suspend fun setPaused(paused: Boolean)

    suspend fun setEnableThumbnails(enabled: Boolean)
}
