package com.uiptv.mobile.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals

class MobilePlaybackTest {
    @Test
    fun extractsPlayableUrlFromDesktopStyleCommands() {
        assertEquals("https://stream.test/live.m3u8", extractPlayableStreamUrl("ffmpeg https://stream.test/live.m3u8"))
        assertEquals("https://stream.test/live.m3u8", extractPlayableStreamUrl("ffmpeg+https://stream.test/live.m3u8"))
        assertEquals("https://stream.test/live.m3u8", extractPlayableStreamUrl("ffmpeg%20https://stream.test/live.m3u8"))
        assertEquals("https://stream.test/live.ts", extractPlayableStreamUrl("token ignored https://stream.test/live.ts"))
    }

    @Test
    fun leavesSimpleUrlsUntouched() {
        assertEquals("https://stream.test/movie.mp4?token=abc", extractPlayableStreamUrl(" https://stream.test/movie.mp4?token=abc "))
        assertEquals("", extractPlayableStreamUrl("   "))
    }
}
