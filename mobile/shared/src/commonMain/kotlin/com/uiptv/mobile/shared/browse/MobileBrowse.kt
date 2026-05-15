package com.uiptv.mobile.shared.browse

import com.uiptv.mobile.shared.accounts.MobileAccountType

enum class BrowseMode(val label: String) {
    LIVE("Live"),
    VOD("VOD"),
    SERIES("Series")
}

data class MobileBrowseCategory(
    val rowId: Long,
    val providerId: String,
    val accountId: Long,
    val title: String,
    val itemCount: Int = 0
)

data class MobileBrowseItem(
    val rowId: Long,
    val accountId: Long,
    val accountName: String,
    val mode: BrowseMode,
    val categoryRowId: Long,
    val categoryProviderId: String,
    val categoryTitle: String,
    val channelId: String,
    val name: String,
    val number: String = "",
    val command: String = "",
    val logo: String = "",
    val drmType: String = "",
    val drmLicenseUrl: String = "",
    val clearKeysJson: String = "",
    val inputstreamAddon: String = "",
    val manifestType: String = "",
    val isHd: Boolean = false,
    val isBookmarked: Boolean = false,
    val plot: String = "",
    val releaseDate: String = "",
    val rating: String = "",
    val duration: String = "",
    val genre: String = "",
    val imdbUrl: String = ""
)

data class MobileBrowseSnapshot(
    val accounts: List<BrowseAccountOption> = emptyList(),
    val selectedAccountId: Long? = null,
    val mode: BrowseMode = BrowseMode.LIVE,
    val categories: List<MobileBrowseCategory> = emptyList(),
    val selectedCategoryRowId: Long? = null,
    val items: List<MobileBrowseItem> = emptyList()
)

data class BrowseAccountOption(
    val id: Long,
    val name: String,
    val type: MobileAccountType? = null
)

data class MobileBookmark(
    val rowId: Long,
    val accountId: Long = 0,
    val accountName: String,
    val bookmarkCategoryId: String = "",
    val categoryTitle: String,
    val channelId: String,
    val channelName: String,
    val command: String,
    val mode: BrowseMode,
    val logo: String = "",
    val drmType: String = "",
    val drmLicenseUrl: String = "",
    val clearKeysJson: String = "",
    val inputstreamAddon: String = "",
    val manifestType: String = ""
)

data class MobileBookmarkCategory(
    val id: String?,
    val name: String,
    val itemCount: Int = 0
)

data class MobileWatchingNowItem(
    val rowId: Long,
    val accountId: Long,
    val accountName: String,
    val mode: BrowseMode,
    val title: String,
    val subtitle: String,
    val command: String = "",
    val logo: String = "",
    val updatedAtEpochSeconds: Long = 0,
    val categoryProviderId: String = "",
    val categoryRowId: Long = 0,
    val contentId: String = "",
    val plot: String = "",
    val releaseDate: String = "",
    val rating: String = "",
    val duration: String = "",
    val genre: String = "",
    val imdbUrl: String = ""
)

data class MobileWatchingNowEpisode(
    val rowId: Long,
    val parentRowId: Long,
    val accountId: Long,
    val accountName: String,
    val seriesId: String,
    val seriesTitle: String,
    val categoryProviderId: String,
    val categoryRowId: Long,
    val episodeId: String,
    val title: String,
    val season: String = "",
    val episodeNumber: String = "",
    val command: String = "",
    val logo: String = "",
    val plot: String = "",
    val releaseDate: String = "",
    val rating: String = "",
    val duration: String = "",
    val isWatched: Boolean = false
)

data class MobileSeriesSeasonTab(
    val key: String,
    val label: String,
    val sortOrder: Int
)

data class MobileSeriesDetails(
    val series: MobileWatchingNowItem,
    val episodes: List<MobileWatchingNowEpisode>
)

fun MobileWatchingNowEpisode.seasonTab(): MobileSeriesSeasonTab {
    val value = resolvedSeason()
    if (value.isBlank()) {
        return MobileSeriesSeasonTab(SEASON_OTHER_KEY, "Other", SEASON_OTHER_SORT)
    }
    val number = value.toIntOrNull()
        ?: Regex("""\d+""").find(value)?.value?.toIntOrNull()
    if (number != null) {
        return MobileSeriesSeasonTab(
            key = "season:$number",
            label = number.toString(),
            sortOrder = number
        )
    }
    return MobileSeriesSeasonTab(
        key = "label:${value.lowercase()}",
        label = value,
        sortOrder = SEASON_NAMED_SORT
    )
}

fun List<MobileWatchingNowEpisode>.seasonTabs(): List<MobileSeriesSeasonTab> =
    map { it.seasonTab() }
        .distinctBy { it.key }
        .sortedWith(compareBy<MobileSeriesSeasonTab> { it.sortOrder }.thenBy { it.label.lowercase() })

fun MobileWatchingNowEpisode.resolvedSeason(): String =
    season.trim().ifBlank { inferSeasonFromText(title) }

fun MobileWatchingNowEpisode.resolvedEpisodeNumber(): String =
    episodeNumber.trim().ifBlank { inferEpisodeFromText(title) }

interface BrowseRepository {
    suspend fun loadBrowse(
        accountId: Long?,
        mode: BrowseMode,
        categoryRowId: Long?,
        query: String
    ): MobileBrowseSnapshot

    suspend fun listBookmarkCategories(): List<MobileBookmarkCategory>

    suspend fun listBookmarks(query: String, bookmarkCategoryId: String?): List<MobileBookmark>

    suspend fun toggleBookmark(item: MobileBrowseItem): Boolean

    suspend fun removeBookmark(bookmarkId: Long)

    suspend fun listWatchingNow(query: String): List<MobileWatchingNowItem>

    suspend fun listWatchingNowEpisodes(item: MobileWatchingNowItem): List<MobileWatchingNowEpisode>

    suspend fun enrichWatchingNowItem(item: MobileWatchingNowItem): MobileWatchingNowItem

    suspend fun enrichSeriesDetails(
        series: MobileWatchingNowItem,
        episodes: List<MobileWatchingNowEpisode>
    ): MobileSeriesDetails

    suspend fun markWatchingNowEpisode(episode: MobileWatchingNowEpisode)

    suspend fun clearWatchingNowEpisode(episode: MobileWatchingNowEpisode)

    suspend fun removeWatchingNow(item: MobileWatchingNowItem)
}

private const val SEASON_OTHER_KEY = "other"
private const val SEASON_NAMED_SORT = Int.MAX_VALUE - 1
private const val SEASON_OTHER_SORT = Int.MAX_VALUE

private val SEASON_PATTERNS = listOf(
    Regex("""(?i)\bseason\s*([0-9]{1,3})\b"""),
    Regex("""(?i)\bs\s*([0-9]{1,3})\s*e\s*[0-9]{1,3}\b"""),
    Regex("""(?i)\b([0-9]{1,3})\s*x\s*[0-9]{1,3}\b""")
)

private val EPISODE_PATTERNS = listOf(
    Regex("""(?i)\bepisode\s*([0-9]{1,3})\b"""),
    Regex("""(?i)\be[p.]?\s*([0-9]{1,3})\b"""),
    Regex("""(?i)\bs\s*[0-9]{1,3}\s*e\s*([0-9]{1,3})\b"""),
    Regex("""(?i)\b[0-9]{1,3}\s*x\s*([0-9]{1,3})\b""")
)

private fun inferSeasonFromText(value: String): String =
    inferNumber(value, SEASON_PATTERNS)

private fun inferEpisodeFromText(value: String): String =
    inferNumber(value, EPISODE_PATTERNS)

private fun inferNumber(value: String, patterns: List<Regex>): String {
    val text = value.trim()
    if (text.isBlank()) {
        return ""
    }
    for (pattern in patterns) {
        val number = pattern.find(text)?.groupValues?.getOrNull(1)?.trimStart('0').orEmpty()
        if (number.isNotBlank()) {
            return number
        }
    }
    return ""
}
