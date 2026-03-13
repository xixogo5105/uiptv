package com.uiptv.server.api.json;

import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.SeriesEpisodeDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.server.TestHttpExchange;
import com.uiptv.service.AccountService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeInfo;
import com.uiptv.shared.EpisodeList;
import com.uiptv.ui.XtremeParser;
import com.uiptv.util.AccountType;
import org.json.JSONArray;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpSeriesEpisodesJsonServerTest extends DbBackedTest {

    @Test
    void handle_returnsEmptyForMissingAccountOrSeries() throws Exception {
        HttpSeriesEpisodesJsonServer handler = new HttpSeriesEpisodesJsonServer();

        TestHttpExchange missingAccount = new TestHttpExchange("/seriesEpisodes?accountId=missing&seriesId=1", "GET");
        handler.handle(missingAccount);
        assertEquals(200, missingAccount.getResponseCode());
        assertEquals("[]", missingAccount.getResponseBodyText());

        Account account = createSeriesAccount("series-empty");
        TestHttpExchange missingSeries = new TestHttpExchange("/seriesEpisodes?accountId=" + account.getDbId() + "&seriesId=", "GET");
        handler.handle(missingSeries);
        assertEquals(200, missingSeries.getResponseCode());
        assertEquals("[]", missingSeries.getResponseBodyText());
    }

    @Test
    void handle_returnsCachedEpisodes_withWatchedFlags() throws Exception {
        Account account = createSeriesAccount("series-cached");

        Category category = new Category("api-cat", "Series", "series", false, 0);
        SeriesCategoryDb.get().saveAll(List.of(category), account);
        Category savedCategory = SeriesCategoryDb.get().getCategories(account).get(0);

        Channel episode1 = new Channel();
        episode1.setChannelId("ep-1");
        episode1.setName("Episode 1");
        episode1.setSeason("1");
        episode1.setEpisodeNum("1");
        Channel episode2 = new Channel();
        episode2.setChannelId("ep-2");
        episode2.setName("Episode 2");
        episode2.setSeason("1");
        episode2.setEpisodeNum("2");

        SeriesEpisodeDb.get().saveAll(account, savedCategory.getCategoryId(), "series-1", List.of(episode1, episode2));

        SeriesWatchStateService.getInstance().markSeriesEpisodeManual(
                account,
                savedCategory.getCategoryId(),
                "series-1",
                "ep-2",
                "Episode 2",
                "1",
                "2"
        );

        HttpSeriesEpisodesJsonServer handler = new HttpSeriesEpisodesJsonServer();
        TestHttpExchange exchange = new TestHttpExchange(
                "/seriesEpisodes?accountId=" + account.getDbId() + "&categoryId=" + savedCategory.getDbId() + "&seriesId=series-1",
                "GET"
        );
        handler.handle(exchange);

        JSONArray response = new JSONArray(exchange.getResponseBodyText());
        assertEquals(2, response.length());
        assertEquals("1", response.getJSONObject(1).optString("watched"));
    }

    @Test
    void handle_usesFreshestCategoryCache_forXtreme() throws Exception {
        Account account = createSeriesAccount("series-freshest");

        Category category = new Category("cat-api", "Series", "series", false, 0);
        SeriesCategoryDb.get().saveAll(List.of(category), account);
        Category savedCategory = SeriesCategoryDb.get().getCategories(account).get(0);

        Channel episode = new Channel();
        episode.setChannelId("ep-11");
        episode.setName("Episode 11");
        SeriesEpisodeDb.get().saveAll(account, savedCategory.getCategoryId(), "series-11", List.of(episode));

        HttpSeriesEpisodesJsonServer handler = new HttpSeriesEpisodesJsonServer();
        TestHttpExchange exchange = new TestHttpExchange(
                "/seriesEpisodes?accountId=" + account.getDbId() + "&categoryId=missing-cat&seriesId=series-11",
                "GET"
        );
        handler.handle(exchange);

        JSONArray response = new JSONArray(exchange.getResponseBodyText());
        assertEquals(1, response.length());
        assertEquals("ep-11", response.getJSONObject(0).getString("channelId"));
    }

    @Test
    void handle_loadsEpisodesFromProvider_whenCacheEmpty() throws Exception {
        Account account = createSeriesAccount("series-provider");

        Episode episode = new Episode();
        episode.setId("ep-21");
        episode.setTitle("Episode 21");
        episode.setCmd("http://origin/ep-21.m3u8");
        episode.setSeason("2");
        episode.setEpisodeNum("1");
        EpisodeInfo info = new EpisodeInfo();
        info.setMovieImage("http://img/ep-21.png");
        info.setPlot("Plot");
        info.setReleaseDate("2020");
        info.setRating("7.5");
        info.setDuration("45");
        info.setSeason("2");
        episode.setInfo(info);

        EpisodeList list = new EpisodeList();
        list.getEpisodes().add(episode);

        try (MockedStatic<XtremeParser> xtremeParser = Mockito.mockStatic(XtremeParser.class)) {
            xtremeParser.when(() -> XtremeParser.parseEpisodes(Mockito.eq("series-21"), Mockito.any(Account.class))).thenReturn(list);

            HttpSeriesEpisodesJsonServer handler = new HttpSeriesEpisodesJsonServer();
            TestHttpExchange exchange = new TestHttpExchange(
                    "/seriesEpisodes?accountId=" + account.getDbId() + "&categoryId=&seriesId=series-21",
                    "GET"
            );
            handler.handle(exchange);

            String body = exchange.getResponseBodyText();
            assertTrue(body.contains("Episode 21"));
            assertTrue(body.contains("ep-21"));
        }
    }

    private Account createSeriesAccount(String name) {
        Account account = new Account(name, "user", "pass", "http://test.com/xtreme/", null, null, null, null, null, null,
                AccountType.XTREME_API, null, "http://test.com/xtreme/", false);
        account.setAction(Account.AccountAction.series);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName(name);
        saved.setAction(Account.AccountAction.series);
        return saved;
    }
}
