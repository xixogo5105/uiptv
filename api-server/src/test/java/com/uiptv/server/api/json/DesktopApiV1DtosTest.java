package com.uiptv.server.api.json;

import com.uiptv.model.Channel;
import com.uiptv.model.Bookmark;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DesktopApiV1DtosTest {
    @Test
    void channelDtoExposesLocalIdAndDisplayFieldsWithoutProviderCommands() {
        Channel channel = new Channel();
        channel.setDbId("local-channel-row");
        channel.setChannelId("provider-channel-id");
        channel.setCategoryId("provider-category-id");
        channel.setName("News");
        channel.setCmd("https://provider.test/live/user/password/123");
        channel.setExtraJson("{\"private\":\"value\"}");
        channel.setDrmLicenseUrl("https://provider.test/license");

        var dto = DesktopApiV1Dtos.channel(channel);

        assertEquals("local-channel-row", dto.getString("dbId"));
        assertEquals("News", dto.getString("name"));
        assertFalse(dto.has("channelId"));
        assertFalse(dto.has("categoryId"));
        assertFalse(dto.has("cmd"));
        assertFalse(dto.has("extraJson"));
        assertFalse(dto.has("drmLicenseUrl"));
    }

    @Test
    void uncachedChannelDtoExposesOnlyOpaquePlaybackReference() {
        Channel channel = new Channel();
        channel.setChannelId("provider-channel-id");
        channel.setCmd("https://provider.test/live/user/password/stream.m3u8");

        var dto = DesktopApiV1Dtos.channel(channel, "opaque-playback-reference");

        assertEquals("", dto.getString("dbId"));
        assertEquals("opaque-playback-reference", dto.getString("playbackId"));
        assertFalse(dto.has("channelId"));
        assertFalse(dto.has("cmd"));
    }

    @Test
    void bookmarkDtoExposesDisplayFieldsWithoutStoredStreamDetails() {
        Bookmark bookmark = new Bookmark(
                "Provider account",
                "News",
                "provider-channel",
                "Station",
                "https://provider.test/live/user/password/stream.m3u8",
                "https://provider.test",
                "provider-category"
        );
        bookmark.setDbId("local-favourite");
        bookmark.setLogo("https://images.test/logo.png");

        var dto = DesktopApiV1Dtos.bookmark(bookmark);

        assertEquals("local-favourite", dto.getString("dbId"));
        assertEquals("Station", dto.getString("channelName"));
        assertEquals("Provider account", dto.getString("accountName"));
        assertFalse(dto.has("channelId"));
        assertFalse(dto.has("cmd"));
        assertFalse(dto.has("serverPortalUrl"));
    }

    @Test
    void watchingEpisodeDtoExposesOnlyDisplayDataAndOpaquePlaybackId() {
        Channel episode = new Channel();
        episode.setChannelId("provider-episode-id");
        episode.setCategoryId("provider-category-id");
        episode.setCmd("https://provider.test/live/user/password/episode");
        episode.setName("Episode 3");
        episode.setSeason("2");
        episode.setEpisodeNum("3");
        episode.setLogo("https://images.test/episode.png");

        var dto = DesktopApiV1Dtos.watchingNowEpisode(episode, "opaque-episode-reference");

        assertEquals("Episode 3", dto.getString("title"));
        assertEquals("2", dto.getString("season"));
        assertEquals("3", dto.getString("episodeNum"));
        assertEquals("opaque-episode-reference", dto.getString("playbackId"));
        assertFalse(dto.has("channelId"));
        assertFalse(dto.has("categoryId"));
        assertFalse(dto.has("cmd"));
    }
}
