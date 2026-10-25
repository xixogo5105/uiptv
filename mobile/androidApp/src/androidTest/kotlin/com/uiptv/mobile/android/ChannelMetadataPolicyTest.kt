package com.uiptv.mobile.android

import com.uiptv.mobile.shared.cache.ChannelExtraJsonCompactor
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** No Activity or installed database is opened by these checks. */
class ChannelMetadataPolicyTest {
    @Test fun providerFieldsUseSharedDesktopPolicyAndPreserveTypes() {
        val source = JSONObject().put("cmds", JSONArray().put("x".repeat(50_000)))
            .put("cmd", "command").put("cmd_1", "alternate")
            .put("xmltv_id", "guide").put("epg_channel_id", "xtreme-guide")
            .put("year", 2026).put("rating", 4.5).put("plot", "Details")
            .put("releaseDate", "2026-10-25").put("genre", "News").put("imdbUrl", "imdb")
            .put("screenshot_uri", "fallback-image").put("series", JSONArray().put(1))
        val compact = JSONObject(ChannelExtraJsonCompactor.compact(source))
        listOf("cmds", "cmd", "cmd_1", "series").forEach { assertFalse(compact.has(it)) }
        assertEquals("guide", compact.getString("xmltv_id"))
        assertEquals("xtreme-guide", compact.getString("epg_channel_id"))
        assertEquals(2026, compact.getInt("year"))
        assertTrue(compact.get("year") is Number)
        assertEquals(4.5, compact.getDouble("rating"), 0.0)
        assertEquals("Details", compact.getString("plot"))
        assertEquals("2026-10-25", compact.getString("releaseDate"))
        assertEquals("News", compact.getString("genre"))
        assertEquals("imdb", compact.getString("imdbUrl"))
        assertEquals("fallback-image", compact.getString("screenshot_uri"))
        assertEquals(compact.toString(), JSONObject(ChannelExtraJsonCompactor.compact(compact)).toString())
    }

    @Test fun m3uAndEpisodeArtworkKeepTheirOwnShapes() {
        val options = JSONObject("""{"tvg-shift":"1.5","http-user-agent":"Agent","http-referrer":"Referrer","extvlcopt":{"http-cookie":"cookie"},"custom":{"unknown":[1,true]}}""")
        val source = JSONObject().put("m3u", options)
        assertEquals(source.toString(), JSONObject(ChannelExtraJsonCompactor.compact(source)).toString())
        val episode = JSONObject().put("id", 42).put("title", "Episode")
            .put("info", JSONObject().put("movie_image", "artwork").put("plot", "Plot").put("duration_secs", 3600)
                .put("tmdb", 42).put("air_date", "2026-10-25").put("imdb_rating", "8")
                .put("video", JSONObject().put("unconsumed", "x".repeat(20_000))))
            .put("series", JSONArray().put(1)).put("cmds", JSONArray().put("unused"))
        val result = JSONObject(ChannelExtraJsonCompactor.compactEpisode(episode))
        assertEquals(42, result.getInt("id"))
        assertEquals("artwork", result.getJSONObject("info").getString("movie_image"))
        assertEquals(3600, result.getJSONObject("info").getInt("duration_secs"))
        assertEquals(42, result.getJSONObject("info").getInt("tmdb"))
        assertEquals("2026-10-25", result.getJSONObject("info").getString("air_date"))
        assertEquals("8", result.getJSONObject("info").getString("imdb_rating"))
        assertFalse(result.getJSONObject("info").has("video"))
        assertFalse(result.has("series"))
        assertFalse(result.has("cmds"))
    }
}
