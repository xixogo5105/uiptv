package com.uiptv.mobile.shared.playback

import com.uiptv.mobile.shared.browse.BrowseMode
import com.uiptv.mobile.shared.settings.AndroidPlayerPreference

data class PlaybackLaunchResult(
    val launched: Boolean,
    val message: String
)

data class PlayerChoice(
    val player: AndroidPlayerPreference,
    val label: String,
    val installed: Boolean = true,
    val packageName: String = "",
    val storeUrl: String = ""
)

data class PlaybackTarget(
    val accountId: Long,
    val accountName: String,
    val mode: BrowseMode,
    val categoryProviderId: String,
    val categoryRowId: Long,
    val channelId: String,
    val title: String,
    val url: String,
    val logo: String = "",
    val drmType: String = "",
    val drmLicenseUrl: String = "",
    val clearKeysJson: String = "",
    val inputstreamAddon: String = "",
    val manifestType: String = ""
)

fun extractPlayableStreamUrl(raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) {
        return ""
    }
    val lower = value.lowercase()
    val withoutPrefix = when {
        lower.startsWith("ffmpeg ") -> value.substring("ffmpeg ".length).trim()
        lower.startsWith("ffmpeg+") -> value.substring("ffmpeg+".length).trim()
        lower.startsWith("ffmpeg%20") -> value.substring("ffmpeg%20".length).trim()
        else -> value
    }
    val parts = withoutPrefix.split(Regex("\\s+")).filter { it.isNotBlank() }
    return if (parts.size > 1) parts.last() else withoutPrefix
}
