package com.uiptv.mobile.shared.playback

import com.uiptv.mobile.shared.browse.BrowseMode
import com.uiptv.mobile.shared.browse.MobileBookmark
import com.uiptv.mobile.shared.browse.MobileBrowseItem
import com.uiptv.mobile.shared.settings.AndroidPlayerPreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MobilePlaybackTest {
    @Test
    fun playbackModelsCarryLaunchAndPlayerMetadata() {
        val result = PlaybackLaunchResult(launched = true, message = "Opening")
        val choice = PlayerChoice(
            player = AndroidPlayerPreference.MX_PLAYER_FREE,
            label = "MX Player",
            installed = false,
            packageName = "com.mxtech.videoplayer.ad",
            storeUrl = "https://play.google.com/store/apps/details?id=com.mxtech.videoplayer.ad"
        )

        assertEquals(true, result.launched)
        assertEquals("Opening", result.message)
        assertEquals(AndroidPlayerPreference.MX_PLAYER_FREE, choice.player)
        assertEquals(false, choice.installed)
        assertEquals("com.mxtech.videoplayer.ad", choice.packageName)
    }

    @Test
    fun playbackTargetKeepsDefaultsForOptionalMetadata() {
        val target = PlaybackTarget(
            accountId = 1,
            accountName = "Demo",
            mode = BrowseMode.SERIES,
            categoryProviderId = "series",
            categoryRowId = 2,
            channelId = "episode",
            title = "Episode",
            url = "http://stream.test/episode.mkv"
        )

        assertEquals("", target.logo)
        assertEquals("", target.drmType)
        assertEquals("", target.drmLicenseUrl)
        assertEquals("", target.clearKeysJson)
        assertEquals("", target.inputstreamAddon)
        assertEquals("", target.manifestType)
        assertEquals("", target.seriesId)
        assertEquals("", target.episodeId)
    }

    @Test
    fun playbackSurfQueuePreservesAllPlayableVisibleItemsAndCurrentPosition() {
        val visibleItems = (0..8).map { index ->
            surfItem(index, command = if (index == 4) "" else "https://stream.test/$index.m3u8")
        }
        val current = visibleItems[5]

        val queue = createPlaybackSurfQueue(
            visibleItems,
            current,
            isPlayable = { it.command.isNotBlank() },
            toTarget = { it.toPlaybackTarget() }
        )

        val validQueue = assertNotNull(queue)
        assertEquals(
            listOf("channel-0", "channel-1", "channel-2", "channel-3", "channel-5", "channel-6", "channel-7", "channel-8"),
            validQueue.targets.map { it.channelId }
        )
        assertEquals(4, validQueue.currentIndex)
    }

    @Test
    fun playbackSurfQueueIsUnavailableWithoutMultiplePlayableItemsOrPlayableCurrent() {
        val playable = surfItem(0, "https://stream.test/0.m3u8")
        val unavailable = surfItem(1, "")

        assertNull(createPlaybackSurfQueue(listOf(playable), playable, { it.command.isNotBlank() }, { it.toPlaybackTarget() }))
        assertNull(createPlaybackSurfQueue(listOf(playable, unavailable), unavailable, { it.command.isNotBlank() }, { it.toPlaybackTarget() }))
    }

    @Test
    fun playingIndicatorMatchesOnlyTheActiveAccountModeAndChannel() {
        val target = PlaybackTarget(
            accountId = 7,
            accountName = "Demo",
            mode = BrowseMode.LIVE,
            categoryProviderId = "live",
            categoryRowId = 2,
            channelId = "channel-1",
            title = "Channel",
            url = "https://stream.test/channel.m3u8"
        )
        MobilePlaybackIndicator.set(target)

        assertEquals(true, MobilePlaybackIndicator.isActive(7, BrowseMode.LIVE, " channel-1 "))
        assertEquals(false, MobilePlaybackIndicator.isActive(8, BrowseMode.LIVE, "channel-1"))
        assertEquals(false, MobilePlaybackIndicator.isActive(7, BrowseMode.VOD, "channel-1"))
        assertEquals(false, MobilePlaybackIndicator.isActive(7, BrowseMode.LIVE, "channel-2"))

        MobilePlaybackIndicator.clear(target)
        assertEquals(false, MobilePlaybackIndicator.isActive(7, BrowseMode.LIVE, "channel-1"))
    }

    @Test
    fun playbackTargetDetectsDesktopStyleDrmMetadata() {
        val base = PlaybackTarget(
            accountId = 1,
            accountName = "Demo",
            mode = BrowseMode.LIVE,
            categoryProviderId = "live",
            categoryRowId = 2,
            channelId = "channel",
            title = "Channel",
            url = "http://stream.test/channel.m3u8"
        )

        assertEquals(false, base.isDrmProtected())
        assertEquals(true, base.copy(drmType = "widevine").isDrmProtected())
        assertEquals(true, base.copy(drmLicenseUrl = "https://license.test/wv").isDrmProtected())
        assertEquals(true, base.copy(clearKeysJson = "{\"kid\":\"key\"}").isDrmProtected())
        assertEquals(true, base.copy(inputstreamAddon = "inputstream.adaptive").isDrmProtected())
        assertEquals(true, base.copy(manifestType = "mpd").isDrmProtected())
    }

    @Test
    fun extractPlayableStreamUrlReturnsBlankForBlankInput() {
        assertEquals("", extractPlayableStreamUrl("   "))
    }

    @Test
    fun extractPlayableStreamUrlRemovesFfmpegPrefixes() {
        assertEquals("https://stream.test/live.m3u8", extractPlayableStreamUrl("ffmpeg https://stream.test/live.m3u8"))
        assertEquals("https://stream.test/live.m3u8", extractPlayableStreamUrl("ffmpeg+https://stream.test/live.m3u8"))
        assertEquals("https://stream.test/live.m3u8", extractPlayableStreamUrl("ffmpeg%20https://stream.test/live.m3u8"))
    }

    @Test
    fun extractPlayableStreamUrlUsesLastTokenForCommandStyleInput() {
        assertEquals(
            "http://stream.test/live.ts",
            extractPlayableStreamUrl("ffmpeg -headers 'User-Agent: Test' http://stream.test/live.ts")
        )
    }

    @Test
    fun extractPlayableStreamUrlKeepsSingleUrlWithoutPrefix() {
        assertEquals(
            "https://stream.test/movie.mp4?token=abc",
            extractPlayableStreamUrl("  https://stream.test/movie.mp4?token=abc  ")
        )
        assertEquals("https://stream.test/live.ts", extractPlayableStreamUrl("token ignored https://stream.test/live.ts"))
    }

    @Test
    fun bookmarkPlaybackTargetIncludesItsArtworkForChannelSurfing() {
        val target = MobileBookmark(
            rowId = 2,
            accountId = 7,
            accountName = "Demo",
            categoryTitle = "News",
            channelId = "news-1",
            channelName = "News One",
            command = "https://stream.test/news.m3u8",
            mode = BrowseMode.LIVE,
            logo = "https://images.test/news.png"
        ).toPlaybackTarget()

        assertEquals("https://images.test/news.png", target.logo)
    }

    @Test
    fun shouldResolveStalkerPortalCommandDetectsPortalLocalCommands() {
        assertEquals(true, shouldResolveStalkerPortalCommand("ffrt http://localhost/ch/23849"))
        assertEquals(true, shouldResolveStalkerPortalCommand("http://127.0.0.1/ch/23849"))
        assertEquals(false, shouldResolveStalkerPortalCommand("https://stream.test/live.ts"))
    }

    private fun surfItem(index: Int, command: String): MobileBrowseItem = MobileBrowseItem(
        rowId = index.toLong(),
        accountId = 7,
        accountName = "Demo",
        mode = BrowseMode.LIVE,
        categoryRowId = 2,
        categoryProviderId = "news",
        categoryTitle = "News",
        channelId = "channel-$index",
        name = "Channel $index",
        command = command
    )
}
