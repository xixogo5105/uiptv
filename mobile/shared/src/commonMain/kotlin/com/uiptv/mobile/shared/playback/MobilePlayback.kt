package com.uiptv.mobile.shared.playback

import com.uiptv.mobile.shared.browse.BrowseMode
import com.uiptv.mobile.shared.browse.MobileBookmark
import com.uiptv.mobile.shared.browse.MobileBrowseItem
import com.uiptv.mobile.shared.browse.MobileWatchingNowEpisode
import com.uiptv.mobile.shared.browse.MobileWatchingNowItem
import com.uiptv.mobile.shared.browse.resolvedEpisodeNumber
import com.uiptv.mobile.shared.browse.resolvedSeason
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
    val manifestType: String = "",
    val seriesId: String = "",
    val seriesTitle: String = "",
    val episodeId: String = "",
    val season: String = "",
    val episodeNumber: String = ""
)

/** The filtered list to browse while the embedded player is open. */
data class PlaybackSurfQueue(val targets: List<PlaybackTarget>, val currentIndex: Int)

fun <T> createPlaybackSurfQueue(
    items: List<T>,
    current: T,
    isPlayable: (T) -> Boolean,
    toTarget: (T) -> PlaybackTarget
): PlaybackSurfQueue? {
    val playable = items.filter(isPlayable)
    val currentIndex = playable.indexOf(current)
    return if (playable.size > 1 && currentIndex >= 0) {
        PlaybackSurfQueue(playable.map(toTarget), currentIndex)
    } else null
}

fun MobileBrowseItem.toPlaybackTarget(): PlaybackTarget = PlaybackTarget(
    accountId = accountId,
    accountName = accountName,
    mode = mode,
    categoryProviderId = categoryProviderId,
    categoryRowId = categoryRowId,
    channelId = channelId,
    title = name,
    url = command,
    logo = logo,
    drmType = drmType,
    drmLicenseUrl = drmLicenseUrl,
    clearKeysJson = clearKeysJson,
    inputstreamAddon = inputstreamAddon,
    manifestType = manifestType
)

fun MobileBookmark.toPlaybackTarget(): PlaybackTarget = PlaybackTarget(
    accountId = accountId,
    accountName = accountName,
    mode = mode,
    categoryProviderId = "",
    categoryRowId = 0,
    channelId = channelId,
    title = channelName,
    url = command,
    logo = logo,
    drmType = drmType,
    drmLicenseUrl = drmLicenseUrl,
    clearKeysJson = clearKeysJson,
    inputstreamAddon = inputstreamAddon,
    manifestType = manifestType
)

fun MobileWatchingNowItem.toPlaybackTarget(): PlaybackTarget = PlaybackTarget(
    accountId = accountId,
    accountName = accountName,
    mode = mode,
    categoryProviderId = categoryProviderId,
    categoryRowId = categoryRowId,
    channelId = contentId.ifBlank { rowId.toString() },
    title = title,
    url = command,
    logo = logo
)

fun MobileWatchingNowEpisode.toPlaybackTarget(): PlaybackTarget = PlaybackTarget(
    accountId = accountId,
    accountName = accountName,
    mode = BrowseMode.SERIES,
    categoryProviderId = categoryProviderId,
    categoryRowId = categoryRowId,
    channelId = episodeId,
    title = title,
    url = command,
    logo = logo,
    seriesId = seriesId,
    seriesTitle = seriesTitle,
    episodeId = episodeId,
    season = resolvedSeason(),
    episodeNumber = resolvedEpisodeNumber()
)

fun PlaybackTarget.isDrmProtected(): Boolean =
    drmType.isCleanNotBlank() ||
        drmLicenseUrl.isCleanNotBlank() ||
        clearKeysJson.isCleanNotBlank() ||
        inputstreamAddon.isCleanNotBlank() ||
        manifestType.isCleanNotBlank()

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

private fun String.isCleanNotBlank(): Boolean {
    val value = trim()
    return value.isNotBlank() &&
        !value.equals("null", ignoreCase = true) &&
        !value.equals("undefined", ignoreCase = true)
}

fun shouldResolveStalkerPortalCommand(raw: String): Boolean {
    val value = raw.trim()
    if (value.isBlank()) {
        return false
    }
    val lower = value.lowercase()
    if (lower.startsWith("ffrt ") || lower.startsWith("ffmpeg ") || lower.startsWith("ffmpeg+") || lower.startsWith("ffmpeg%20")) {
        return true
    }
    val playable = extractPlayableStreamUrl(value).lowercase()
    return playable.startsWith("http://localhost/") ||
        playable.startsWith("https://localhost/") ||
        playable.startsWith("http://127.0.0.1/") ||
        playable.startsWith("https://127.0.0.1/")
}
