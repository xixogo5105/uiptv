package com.uiptv.service;

import com.uiptv.model.Bookmark;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.Account;
import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeInfo;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlayerRequestResolverTest extends DbBackedTest {

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

    @Test
    void resolveBookmarkChannel_fallsBackThroughChannelVodAndLegacySnapshots() {
        Channel channelSnapshot = new Channel();
        channelSnapshot.setChannelId("ch-1");
        channelSnapshot.setName("Channel Snapshot");
        Bookmark channelBookmark = new Bookmark("acc", "Live", "ch-1", "Legacy", "http%3A%2F%2Fencoded", "http://portal", "cat");
        channelBookmark.setChannelJson(channelSnapshot.toJson());

        Channel vodSnapshot = new Channel();
        vodSnapshot.setChannelId("vod-1");
        vodSnapshot.setName("VOD Snapshot");
        Bookmark vodBookmark = new Bookmark("acc", "Movies", "vod-1", "Legacy", "http%3A%2F%2Fencoded", "http://portal", "cat");
        vodBookmark.setVodJson(vodSnapshot.toJson());

        Bookmark legacy = new Bookmark("acc", "Legacy", "legacy-1", "Legacy Name", "http%3A%2F%2Fstream", "http://portal", "cat");
        legacy.setDrmType("widevine");
        legacy.setManifestType("hls");

        PlayerRequestResolver resolver = new PlayerRequestResolver();

        assertEquals("Channel Snapshot", resolver.resolveBookmarkChannel(channelBookmark).getName());
        assertEquals("VOD Snapshot", resolver.resolveBookmarkChannel(vodBookmark).getName());
        Channel legacyChannel = resolver.resolveBookmarkChannel(legacy);
        assertEquals("http://stream", legacyChannel.getCmd());
        assertEquals("widevine", legacyChannel.getDrmType());
        assertEquals("hls", legacyChannel.getManifestType());
    }

    @Test
    void resolveSeriesCategoryId_mapsDbIdToApiCategoryForSeriesAccounts() {
        Account account = new Account("resolver-series", "user", "pass", "http://test", null, null, null, null, null, null,
                AccountType.XTREME_API, null, "http://test", false);
        account.setDbId("resolver-series-id");
        account.setAction(Account.AccountAction.series);
        Category category = new Category("api-cat", "Series", "series", false, 0);
        SeriesCategoryDb.get().saveAll(java.util.List.of(category), account);
        Category saved = SeriesCategoryDb.get().getCategories(account).getFirst();

        PlayerRequestResolver resolver = new PlayerRequestResolver();

        assertEquals("api-cat", resolver.resolveSeriesCategoryId(account, saved.getDbId()));
        assertEquals("raw-cat", resolver.resolveSeriesCategoryId(account, "raw-cat"));
        account.setAction(Account.AccountAction.itv);
        assertEquals("", resolver.resolveSeriesCategoryId(account, saved.getDbId()));
    }
}
