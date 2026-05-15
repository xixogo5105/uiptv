package com.uiptv.mobile.shared.playback

import com.uiptv.mobile.shared.browse.BrowseMode
import com.uiptv.mobile.shared.settings.AndroidPlayerPreference
import kotlin.test.Test
import kotlin.test.assertEquals

class MobilePlaybackTest {
    @Test
    fun playbackModelsCarryLaunchAndPlayerMetadata() {
        val result = PlaybackLaunchResult(launched = true, message = "Opening")
        val choice = PlayerChoice(
            player = AndroidPlayerPreference.VLC,
            label = "VLC",
            installed = false,
            packageName = "org.videolan.vlc",
            storeUrl = "https://play.google.com/store/apps/details?id=org.videolan.vlc"
        )

        assertEquals(true, result.launched)
        assertEquals("Opening", result.message)
        assertEquals(AndroidPlayerPreference.VLC, choice.player)
        assertEquals(false, choice.installed)
        assertEquals("org.videolan.vlc", choice.packageName)
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
}
