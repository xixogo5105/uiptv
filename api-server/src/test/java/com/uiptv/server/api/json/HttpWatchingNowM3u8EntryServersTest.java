package com.uiptv.server.api.json;

import com.uiptv.db.SeriesWatchingNowSnapshotDb;
import com.uiptv.db.SeriesWatchStateDb;
import com.uiptv.db.VodChannelDb;
import com.uiptv.db.VodWatchStateDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.model.SeriesWatchingNowSnapshot;
import com.uiptv.server.TestHttpExchange;
import com.uiptv.service.AccountService;
import com.uiptv.service.HandshakeService;
import com.uiptv.service.M3U8PublicationService;
import com.uiptv.service.PlayerService;
import com.uiptv.testsupport.DbBackedTest;
import com.uiptv.util.AccountType;
import com.uiptv.util.WebActivityLog;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HttpWatchingNowM3u8EntryServersTest extends DbBackedTest {

    @Test
    void watchingNowSeriesEntry_missingParams_returns404() throws Exception {
        HttpWatchingNowSeriesM3u8EntryServer handler = new HttpWatchingNowSeriesM3u8EntryServer();
        TestHttpExchange exchange = new TestHttpExchange("/watchingNowSeriesEntry", "GET");
        handler.handle(exchange);
        assertEquals(404, exchange.getResponseCode());
    }

    @Test
    void watchingNowSeriesEntry_accountNotFound_returns404() throws Exception {
        HttpWatchingNowSeriesM3u8EntryServer handler = new HttpWatchingNowSeriesM3u8EntryServer();
        TestHttpExchange exchange = new TestHttpExchange("/watchingNowSeriesEntry?accountId=missing&categoryId=cat&seriesId=series", "GET");
        handler.handle(exchange);
        assertEquals(404, exchange.getResponseCode());
    }

    @Test
    void watchingNowSeriesEntry_seriesNotFound_returns404() throws Exception {
        Account account = createSeriesAccount("series-entry-account");
        HttpWatchingNowSeriesM3u8EntryServer handler = new HttpWatchingNowSeriesM3u8EntryServer();
        TestHttpExchange exchange = new TestHttpExchange(
                "/watchingNowSeriesEntry?accountId=" + account.getDbId() + "&categoryId=cat&seriesId=missing",
                "GET"
        );
        handler.handle(exchange);
        assertEquals(404, exchange.getResponseCode());
    }

    @Test
    void watchingNowSeriesEntry_success_redirectsToBingeWatchPlaylist() throws Exception {
        Account account = createSeriesAccount("series-entry-success");
        String accountId = account.getDbId();
        String categoryId = "series-cat";
        String seriesId = "series-1";

        // Create a watching now snapshot with episodes
        SeriesWatchingNowSnapshot snapshot = new SeriesWatchingNowSnapshot();
        snapshot.setAccountId(accountId);
        snapshot.setCategoryId(categoryId);
        snapshot.setSeriesId(seriesId);
        snapshot.setCategoryDbId("cat-db-1");
        snapshot.setSeriesTitle("Test Series");
        snapshot.setSeriesPoster("http://img/series.png");
        snapshot.setEpisodesJson(new org.json.JSONArray()
                .put(new org.json.JSONObject()
                        .put("channelId", "ep-1")
                        .put("name", "Episode 1")
                        .put("cmd", "http://stream/ep-1")
                        .put("season", "1")
                        .put("episodeNum", "1"))
                .put(new org.json.JSONObject()
                        .put("channelId", "ep-2")
                        .put("name", "Episode 2")
                        .put("cmd", "http://stream/ep-2")
                        .put("season", "1")
                        .put("episodeNum", "2"))
                .toString());
        snapshot.setUpdatedAt(System.currentTimeMillis());
        SeriesWatchingNowSnapshotDb.get().upsert(snapshot);

        HttpWatchingNowSeriesM3u8EntryServer handler = new HttpWatchingNowSeriesM3u8EntryServer();
        TestHttpExchange exchange = new TestHttpExchange(
                "/watchingNowSeriesEntry?accountId=" + accountId + "&categoryId=" + categoryId + "&seriesId=" + seriesId,
                "GET"
        );
        handler.handle(exchange);

        assertEquals(307, exchange.getResponseCode());
        String location = exchange.getResponseHeaders().getFirst("Location");
        assertNotNull(location);
        assertTrue(location.contains("/bingewatch.m3u8?token="));
        String description = String.valueOf(exchange.getAttribute(WebActivityLog.ACTIVITY_DESCRIPTION_ATTRIBUTE));
        assertTrue(description.contains("Downloaded a binge-watch playlist"));
    }

    @Test
    void watchingNowVodEntry_missingParams_returns404() throws Exception {
        HttpWatchingNowVodM3u8EntryServer handler = new HttpWatchingNowVodM3u8EntryServer();
        TestHttpExchange exchange = new TestHttpExchange("/watchingNowVodEntry", "GET");
        handler.handle(exchange);
        assertEquals(404, exchange.getResponseCode());
    }

    @Test
    void watchingNowVodEntry_accountNotFound_returns404() throws Exception {
        HttpWatchingNowVodM3u8EntryServer handler = new HttpWatchingNowVodM3u8EntryServer();
        TestHttpExchange exchange = new TestHttpExchange("/watchingNowVodEntry?accountId=missing&categoryId=cat&vodId=vod", "GET");
        handler.handle(exchange);
        assertEquals(404, exchange.getResponseCode());
    }

    @Test
    void watchingNowVodEntry_vodNotFound_returns404() throws Exception {
        Account account = createVodAccount("vod-entry-account");
        HttpWatchingNowVodM3u8EntryServer handler = new HttpWatchingNowVodM3u8EntryServer();
        TestHttpExchange exchange = new TestHttpExchange(
                "/watchingNowVodEntry?accountId=" + account.getDbId() + "&categoryId=cat&vodId=missing",
                "GET"
        );
        handler.handle(exchange);
        assertEquals(404, exchange.getResponseCode());
    }

    @Test
    void watchingNowVodEntry_success_redirectsToStreamUrl() throws Exception {
        Account account = createVodAccount("vod-entry-success");
        String accountId = account.getDbId();
        String categoryId = "vod-cat";
        String vodId = "vod-1";

        // Create a VOD channel in the cache
        Channel vodChannel = new Channel();
        vodChannel.setChannelId(vodId);
        vodChannel.setName("Test Movie");
        vodChannel.setCmd("http://vod-origin/movie.m3u8");
        vodChannel.setCategoryId(categoryId);
        VodChannelDb.get().saveAll(List.of(vodChannel), categoryId, account);

        PlayerService playerService = Mockito.mock(PlayerService.class);
        Mockito.when(playerService.get(Mockito.any(), Mockito.any()))
                .thenReturn(new PlayerResponse("http://resolved-stream.example/movie.m3u8"));

        HttpWatchingNowVodM3u8EntryServer handler = new HttpWatchingNowVodM3u8EntryServer();
        try (MockedStatic<HandshakeService> handshakeStatic = Mockito.mockStatic(HandshakeService.class);
             MockedStatic<PlayerService> playerStatic = Mockito.mockStatic(PlayerService.class)) {
            handshakeStatic.when(HandshakeService::getInstance).thenReturn(Mockito.mock(HandshakeService.class));
            playerStatic.when(PlayerService::getInstance).thenReturn(playerService);

            TestHttpExchange exchange = new TestHttpExchange(
                    "/watchingNowVodEntry?accountId=" + accountId + "&categoryId=" + categoryId + "&vodId=" + vodId,
                    "GET"
            );
            handler.handle(exchange);

            assertEquals(307, exchange.getResponseCode());
            assertEquals("http://resolved-stream.example/movie.m3u8", exchange.getResponseHeaders().getFirst("Location"));
            String description = String.valueOf(exchange.getAttribute(WebActivityLog.ACTIVITY_DESCRIPTION_ATTRIBUTE));
            assertTrue(description.contains("Played published M3U entry \"Test Movie\""));
            assertTrue(description.contains(account.getAccountName()));
        }
    }

    @Test
    void getPublishedM3u8_includesWatchingNowSeriesWhenSelectedAndDataExists() throws Exception {
        Account account = createSeriesAccount("published-series-account");
        String accountId = account.getDbId();
        String categoryId = "series-cat";
        String seriesId = "series-1";

        // Create watch state
        com.uiptv.model.SeriesWatchState watchState = new com.uiptv.model.SeriesWatchState();
        watchState.setAccountId(accountId);
        watchState.setMode("series");
        watchState.setCategoryId(categoryId);
        watchState.setSeriesId(seriesId);
        watchState.setEpisodeId("ep-1");
        watchState.setEpisodeName("Episode 1");
        watchState.setSeason("1");
        watchState.setEpisodeNum(1);
        watchState.setUpdatedAt(System.currentTimeMillis());
        SeriesWatchStateDb.get().upsert(watchState);

        // Create snapshot with episodes
        SeriesWatchingNowSnapshot snapshot = new SeriesWatchingNowSnapshot();
        snapshot.setAccountId(accountId);
        snapshot.setCategoryId(categoryId);
        snapshot.setSeriesId(seriesId);
        snapshot.setCategoryDbId("cat-db-1");
        snapshot.setSeriesTitle("Published Series");
        snapshot.setSeriesPoster("http://img/series.png");
        snapshot.setEpisodesJson(new org.json.JSONArray()
                .put(new org.json.JSONObject()
                        .put("channelId", "ep-1")
                        .put("name", "Episode 1")
                        .put("cmd", "http://stream/ep-1")
                        .put("season", "1")
                        .put("episodeNum", "1"))
                .toString());
        snapshot.setUpdatedAt(System.currentTimeMillis());
        SeriesWatchingNowSnapshotDb.get().upsert(snapshot);

        M3U8PublicationService.getInstance().setSelectedAccountIds(
                Set.of(M3U8PublicationService.WATCHING_NOW_SERIES_PLAYLIST_ACCOUNT_ID)
        );

        String published = M3U8PublicationService.getInstance().getPublishedM3u8("127.0.0.1:8080");
        assertTrue(published.contains("#EXTM3U"));
        assertTrue(published.contains("Watching Now - Series"));
        assertTrue(published.contains("Published Series"));
        assertTrue(published.contains("/watchingNowSeriesEntry?accountId=" + accountId));
        assertTrue(published.contains("&seriesId=" + seriesId));
    }

    @Test
    void getPublishedM3u8_includesWatchingNowVodWhenSelectedAndDataExists() throws Exception {
        Account account = createVodAccount("published-vod-account");
        String accountId = account.getDbId();
        String categoryId = "vod-cat";
        String vodId = "vod-1";

        // Create VOD watch state
        com.uiptv.model.VodWatchState vodState = new com.uiptv.model.VodWatchState();
        vodState.setAccountId(accountId);
        vodState.setCategoryId(categoryId);
        vodState.setVodId(vodId);
        vodState.setVodName("Published Movie");
        vodState.setVodCmd("http://vod-origin/movie.m3u8");
        vodState.setVodLogo("http://img/movie.png");
        vodState.setUpdatedAt(System.currentTimeMillis());
        VodWatchStateDb.get().upsert(vodState);

        // Create VOD channel in cache
        Channel vodChannel = new Channel();
        vodChannel.setChannelId(vodId);
        vodChannel.setName("Published Movie");
        vodChannel.setCmd("http://vod-origin/movie.m3u8");
        vodChannel.setCategoryId(categoryId);
        VodChannelDb.get().saveAll(List.of(vodChannel), categoryId, account);

        M3U8PublicationService.getInstance().setSelectedAccountIds(
                Set.of(M3U8PublicationService.WATCHING_NOW_VOD_PLAYLIST_ACCOUNT_ID)
        );

        String published = M3U8PublicationService.getInstance().getPublishedM3u8("127.0.0.1:8080");
        assertTrue(published.contains("#EXTM3U"));
        assertTrue(published.contains("Watching Now - VOD"));
        assertTrue(published.contains("Published Movie"));
        assertTrue(published.contains("/watchingNowVodEntry?accountId=" + accountId));
        assertTrue(published.contains("&vodId=" + vodId));
    }

    private Account createSeriesAccount(String name) {
        Account account = new Account(name, "user", "pass", "http://test.com/xtreme/", null, null, null, null, null, null, AccountType.XTREME_API, null, "http://test.com/xtreme/", false);
        account.setAction(Account.AccountAction.series);
        AccountService.getInstance().save(account);
        return AccountService.getInstance().getByName(name);
    }

    private Account createVodAccount(String name) {
        Account account = new Account(name, "user", "pass", "http://test.com/xtreme/", null, null, null, null, null, null, AccountType.XTREME_API, null, "http://test.com/xtreme/", false);
        account.setAction(Account.AccountAction.vod);
        AccountService.getInstance().save(account);
        return AccountService.getInstance().getByName(name);
    }
}
