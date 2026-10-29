package com.uiptv.service;

import com.uiptv.model.Bookmark;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.Account;
import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.model.PlayerResponse;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeInfo;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        assertEquals("http://encoded", resolver.resolveBookmarkChannel(channelBookmark).getCmd());
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
        account.setCacheEnabled(false);
        assertEquals("api-cat", resolver.resolveSeriesCategoryId(account, "live:resolver-series-id:api-cat"));
        account.setAction(Account.AccountAction.itv);
        assertEquals("", resolver.resolveSeriesCategoryId(account, saved.getDbId()));
    }

    @Test void bookmarkSourceCategoryIsIndependentOfItsUserGroup() {
        Account account = new Account(); account.setDbId("account"); account.setAction(Account.AccountAction.series);
        SeriesCategoryDb.get().saveAll(java.util.List.of(new Category("provider-category", "Series", "", false, 0)), account);
        Category source = SeriesCategoryDb.get().getCategories(account).getFirst();
        Bookmark bookmark = new Bookmark("account", "Series", "episode", "Episode", "cmd", "portal", "user-group");
        bookmark.setCategoryJson(source.toJson());
        PlayerRequestResolver resolver = new PlayerRequestResolver();
        assertEquals("provider-category", resolver.resolveBookmarkSourceCategoryId(account, bookmark));
        bookmark.setCategoryId(null);
        assertEquals("provider-category", resolver.resolveBookmarkSourceCategoryId(account, bookmark));
    }

    @Test
    void bookmarkSourceCategoryUsesProviderIdWhenCachingIsDisabledAndCategoryRowsAreAbsent() {
        Account account = new Account("no-cache-series", "user", "pass", "http://test", null, null, null, null, null, null,
                AccountType.XTREME_API, null, "http://test", false);
        account.setDbId("no-cache-series-id");
        account.setAction(Account.AccountAction.series);
        account.setCacheEnabled(false);

        Category liveCategory = new Category("provider-category", "Shows", "shows", false, 0);
        liveCategory.setDbId("live:no-cache-series-id:provider-category");
        Bookmark bookmark = new Bookmark("no-cache-series", "Shows", "series-1", "Series One", "cmd://series-1", "portal", "favorites");
        bookmark.setCategoryJson(liveCategory.toJson());

        PlayerRequestResolver resolver = new PlayerRequestResolver();

        assertEquals("provider-category", resolver.resolveBookmarkSourceCategoryId(account, bookmark));
        assertEquals(java.util.List.of(), SeriesCategoryDb.get().getCategories(account));
    }

    @Test
    void bookmarkPlaybackUsesSavedSnapshotsWithoutLoadingCategoriesOrChannels() throws Exception {
        Account account = new Account("bookmark-no-cache", "user", "pass", "http://test", null, null, null, null, null, null,
                AccountType.XTREME_API, null, "http://test", false);
        account.setDbId("bookmark-no-cache-id");
        account.setAction(Account.AccountAction.series);
        account.setCacheEnabled(false);

        Category liveCategory = new Category("provider-series", "Shows", "shows", false, 0);
        liveCategory.setDbId("live:bookmark-no-cache-id:provider-series");
        Channel savedChannel = new Channel();
        savedChannel.setChannelId("episode-1");
        savedChannel.setName("Episode One");
        savedChannel.setCmd("http://stream/episode-1.mkv");
        Bookmark bookmark = new Bookmark("bookmark-no-cache", "Shows", "episode-1", "Episode One", "cmd", "portal", "favorites");
        bookmark.setAccountAction(Account.AccountAction.series);
        bookmark.setCategoryJson(liveCategory.toJson());
        bookmark.setChannelJson(savedChannel.toJson());

        BookmarkService bookmarkService = mock(BookmarkService.class);
        AccountService accountService = mock(AccountService.class);
        PlayerService playerService = mock(PlayerService.class);
        PlayerResponse response = new PlayerResponse("http://resolved/episode-1.mkv");
        try (MockedStatic<BookmarkService> bookmarkStatic = mockStatic(BookmarkService.class);
             MockedStatic<AccountService> accountStatic = mockStatic(AccountService.class);
             MockedStatic<PlayerService> playerStatic = mockStatic(PlayerService.class);
             MockedStatic<CategoryService> categoryStatic = mockStatic(CategoryService.class);
             MockedStatic<ChannelService> channelStatic = mockStatic(ChannelService.class)) {
            bookmarkStatic.when(BookmarkService::getInstance).thenReturn(bookmarkService);
            accountStatic.when(AccountService::getInstance).thenReturn(accountService);
            playerStatic.when(PlayerService::getInstance).thenReturn(playerService);
            when(bookmarkService.getBookmark("bookmark-id")).thenReturn(bookmark);
            when(accountService.getAll()).thenReturn(Map.of(account.getAccountName(), account));
            when(playerService.get(any(Account.class), any(Channel.class), eq("episode-1"), eq("show-1"), eq("provider-series")))
                    .thenReturn(response);

            PlayerResponse resolved = new PlayerRequestResolver().resolveBookmarkPlayback("bookmark-id", "series", "show-1");

            ArgumentCaptor<Account> playbackAccount = ArgumentCaptor.forClass(Account.class);
            ArgumentCaptor<Channel> playbackChannel = ArgumentCaptor.forClass(Channel.class);
            verify(playerService).get(playbackAccount.capture(), playbackChannel.capture(), eq("episode-1"), eq("show-1"), eq("provider-series"));
            assertFalse(playbackAccount.getValue().isCacheEnabled());
            assertEquals("http://stream/episode-1.mkv", playbackChannel.getValue().getCmd());
            assertEquals(response, resolved);
            categoryStatic.verifyNoInteractions();
            channelStatic.verifyNoInteractions();
        }
    }
}
