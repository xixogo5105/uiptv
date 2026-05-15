package com.uiptv.mobile.shared.browse

import com.uiptv.mobile.shared.accounts.MobileAccountType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MobileBrowseTest {
    @Test
    fun browseModeLabelsMatchUiNames() {
        assertEquals("Live", BrowseMode.LIVE.label)
        assertEquals("VOD", BrowseMode.VOD.label)
        assertEquals("Series", BrowseMode.SERIES.label)
    }

    @Test
    fun browseSnapshotDefaultsRepresentEmptyLiveState() {
        val snapshot = MobileBrowseSnapshot()

        assertEquals(emptyList(), snapshot.accounts)
        assertEquals(null, snapshot.selectedAccountId)
        assertEquals(BrowseMode.LIVE, snapshot.mode)
        assertEquals(emptyList(), snapshot.categories)
        assertEquals(null, snapshot.selectedCategoryRowId)
        assertEquals(emptyList(), snapshot.items)
    }

    @Test
    fun browseModelsCarryProviderAndPlaybackMetadata() {
        val account = BrowseAccountOption(7, "Portal", MobileAccountType.XTREME_API)
        val category = MobileBrowseCategory(9, "news", 7, "News", itemCount = 12)
        val item = MobileBrowseItem(
            rowId = 11,
            accountId = account.id,
            accountName = account.name,
            mode = BrowseMode.LIVE,
            categoryRowId = category.rowId,
            categoryProviderId = category.providerId,
            categoryTitle = category.title,
            channelId = "bbc",
            name = "BBC HD",
            number = "101",
            command = "http://stream.test/bbc.m3u8",
            logo = "http://image.test/bbc.png",
            drmType = "widevine",
            drmLicenseUrl = "http://license.test",
            clearKeysJson = "{}",
            inputstreamAddon = "inputstream.adaptive",
            manifestType = "hls",
            isHd = true,
            isBookmarked = true
        )

        assertEquals(MobileAccountType.XTREME_API, account.type)
        assertEquals(12, category.itemCount)
        assertEquals("BBC HD", item.name)
        assertEquals("hls", item.manifestType)
        assertTrue(item.isHd)
        assertTrue(item.isBookmarked)
    }

    @Test
    fun bookmarkAndWatchingNowModelsKeepDefaults() {
        val bookmark = MobileBookmark(
            rowId = 1,
            accountName = "Demo",
            categoryTitle = "All",
            channelId = "news",
            channelName = "News",
            command = "http://stream.test/news.ts",
            mode = BrowseMode.LIVE
        )
        val category = MobileBookmarkCategory(null, "All")
        val watchingNow = MobileWatchingNowItem(
            rowId = 2,
            accountId = 3,
            accountName = "Demo",
            mode = BrowseMode.VOD,
            title = "Movie",
            subtitle = "Half watched"
        )

        assertEquals(0, bookmark.accountId)
        assertEquals("", bookmark.logo)
        assertEquals(0, category.itemCount)
        assertEquals("", watchingNow.command)
        assertEquals("", watchingNow.contentId)
        assertFalse(watchingNow.updatedAtEpochSeconds > 0)
    }

    @Test
    fun watchingNowEpisodeCarriesSeriesAndPlaybackMetadata() {
        val episode = MobileWatchingNowEpisode(
            rowId = 0,
            parentRowId = 2,
            accountId = 3,
            accountName = "Demo",
            seriesId = "series-1",
            seriesTitle = "Demo Show",
            categoryProviderId = "cat-1",
            categoryRowId = 9,
            episodeId = "episode-1",
            title = "Pilot",
            season = "1",
            episodeNumber = "1",
            command = "http://stream.test/episode.m3u8",
            logo = "http://image.test/episode.jpg"
        )

        assertEquals("series-1", episode.seriesId)
        assertEquals("episode-1", episode.episodeId)
        assertEquals("http://stream.test/episode.m3u8", episode.command)
        assertEquals(9, episode.categoryRowId)
    }

    @Test
    fun watchingNowEpisodesBuildStableSeasonTabs() {
        val episodes = listOf(
            watchingEpisode("Special", season = "0"),
            watchingEpisode("Pilot", season = "01"),
            watchingEpisode("Finale", season = "2"),
            watchingEpisode("Interview", season = "Extras"),
            watchingEpisode("Unknown", season = "")
        )

        val tabs = episodes.seasonTabs()

        assertEquals(listOf("0", "1", "2", "Extras", "Other"), tabs.map { it.label })
        assertEquals("season:1", episodes[1].seasonTab().key)
        assertEquals("other", episodes.last().seasonTab().key)
    }

    @Test
    fun watchingNowEpisodesInferSeasonTabsFromTitleWhenSeasonIsMissing() {
        val episodes = listOf(
            watchingEpisode("Season 4 - Episode 1", season = "", episodeNumber = "1"),
            watchingEpisode("Season 5 - Episode 1", season = "", episodeNumber = "1"),
            watchingEpisode("S06E02", season = "")
        )

        assertEquals(listOf("4", "5", "6"), episodes.seasonTabs().map { it.label })
        assertEquals("4", episodes[0].resolvedSeason())
        assertEquals("2", episodes[2].resolvedEpisodeNumber())
    }

    private fun watchingEpisode(title: String, season: String, episodeNumber: String = ""): MobileWatchingNowEpisode =
        MobileWatchingNowEpisode(
            rowId = title.hashCode().toLong(),
            parentRowId = 2,
            accountId = 3,
            accountName = "Demo",
            seriesId = "series-1",
            seriesTitle = "Demo Show",
            categoryProviderId = "cat-1",
            categoryRowId = 9,
            episodeId = title,
            title = title,
            season = season,
            episodeNumber = episodeNumber
        )
}
