package com.uiptv.service;

import com.uiptv.model.Bookmark;
import com.uiptv.model.Channel;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlayerRequestResolverTest {

    @Test
    void resolveBookmarkChannel_prefersSeriesSnapshot() {
        EpisodeInfo info = new EpisodeInfo();
        info.setMovieImage("http://poster/ep.png");
        Episode episode = new Episode();
        episode.setId("ep-1");
        episode.setTitle("Episode One");
        episode.setCmd("http://stream/ep1.mkv");
        episode.setInfo(info);

        Bookmark bookmark = new Bookmark("acc", "Series", "series-1", "Series One", "cmd", "http://portal", "cat-1");
        bookmark.setSeriesJson(episode.toJson());

        PlayerRequestResolver resolver = new PlayerRequestResolver();
        Channel channel = resolver.resolveBookmarkChannel(bookmark);

        assertNotNull(channel);
        assertEquals("ep-1", channel.getChannelId());
        assertEquals("Episode One", channel.getName());
        assertEquals("http://stream/ep1.mkv", channel.getCmd());
        assertEquals("http://poster/ep.png", channel.getLogo());
    }

    @Test
    void mergeRequestChannel_fillsMissingFields() {
        Channel base = new Channel();
        base.setChannelId("ch-1");

        Channel request = new Channel();
        request.setName("Channel One");
        request.setLogo("http://logo.png");
        request.setCmd("http://stream.m3u8");
        request.setDrmType("widevine");
        request.setDrmLicenseUrl("http://license");
        request.setManifestType("hls");

        PlayerRequestResolver resolver = new PlayerRequestResolver();
        Channel merged = resolver.mergeRequestChannel(base, request);

        assertEquals("Channel One", merged.getName());
        assertEquals("http://logo.png", merged.getLogo());
        assertEquals("http://stream.m3u8", merged.getCmd());
        assertEquals("widevine", merged.getDrmType());
        assertEquals("http://license", merged.getDrmLicenseUrl());
        assertEquals("hls", merged.getManifestType());
    }
}
