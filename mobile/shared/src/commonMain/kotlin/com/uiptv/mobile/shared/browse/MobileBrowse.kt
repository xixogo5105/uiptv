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
    val isBookmarked: Boolean = false
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
    val contentId: String = ""
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
    val duration: String = ""
)

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

    suspend fun removeWatchingNow(item: MobileWatchingNowItem)
}
