package com.uiptv.server.api.json;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.server.TestHttpExchange;
import com.uiptv.service.AccountService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.service.ImdbMetadataService;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeInfo;
import com.uiptv.shared.EpisodeList;
import com.uiptv.shared.SeasonInfo;
import com.uiptv.ui.XtremeParser;
import com.uiptv.util.AccountType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpSeriesDetailsJsonServerTest extends DbBackedTest {

    @Test
    void handle_returnsDefaultsWhenAccountMissing() throws Exception {
        HttpSeriesDetailsJsonServer handler = new HttpSeriesDetailsJsonServer();
        TestHttpExchange exchange = new TestHttpExchange("/seriesDetails?accountId=missing&seriesId=s1", "GET");
        handler.handle(exchange);
        JSONObject response = new JSONObject(exchange.getResponseBodyText());
        assertTrue(response.has("seasonInfo"));
        assertTrue(response.has("episodes"));
        assertEquals(0, response.getJSONArray("episodes").length());
    }

    @Test
    void handle_mergesProviderAndImdbMetadata() throws Exception {
        Account account = new Account("series-acc", "user", "pass", "http://xtreme", null, null, null, null, null, null,
                AccountType.XTREME_API, null, "http://xtreme", false);
        account.setDbId("acc-1");
        account.setAction(Account.AccountAction.series);

        Episode episode = new Episode();
        episode.setId("ep-1");
        episode.setTitle("Episode 1");
        episode.setCmd("http://origin/ep-1.m3u8");
        episode.setSeason("1");
        episode.setEpisodeNum("1");
        EpisodeInfo info = new EpisodeInfo();
        info.setMovieImage("http://img/ep-1.png");
        info.setPlot("Provider plot");
        info.setReleaseDate("2019");
        info.setRating("8.1");
        info.setDuration("42");
        episode.setInfo(info);

        SeasonInfo seasonInfo = new SeasonInfo();
        seasonInfo.setName("Provider Season");
        seasonInfo.setReleaseDate("2018");

        EpisodeList episodeList = new EpisodeList();
        episodeList.setSeasonInfo(seasonInfo);
        episodeList.getEpisodes().add(episode);

        JSONObject imdbFirst = new JSONObject()
                .put("name", "IMDB Title")
                .put("cover", "http://cover")
                .put("episodesMeta", new JSONArray().put(new JSONObject()
                        .put("season", "1")
                        .put("episodeNum", "1")
                        .put("plot", "Meta plot")
                        .put("logo", "http://logo")
                        .put("title", "Episode 1")));
        JSONObject imdbFallback = new JSONObject()
                .put("plot", "Fallback plot")
                .put("releaseDate", "2021");

        AccountService accountService = Mockito.mock(AccountService.class);
        ImdbMetadataService imdbMetadataService = Mockito.mock(ImdbMetadataService.class);

        try (MockedStatic<AccountService> accountStatic = Mockito.mockStatic(AccountService.class);
             MockedStatic<XtremeParser> xtremeParser = Mockito.mockStatic(XtremeParser.class);
             MockedStatic<ImdbMetadataService> imdbStatic = Mockito.mockStatic(ImdbMetadataService.class)) {
            accountStatic.when(AccountService::getInstance).thenReturn(accountService);
            Mockito.when(accountService.getById("acc-1")).thenReturn(account);

            xtremeParser.when(() -> XtremeParser.parseEpisodes("series-1", account)).thenReturn(episodeList);

            imdbStatic.when(ImdbMetadataService::getInstance).thenReturn(imdbMetadataService);
            Mockito.when(imdbMetadataService.findBestEffortDetails(Mockito.anyString(), Mockito.anyString(), Mockito.anyList()))
                    .thenReturn(imdbFirst, imdbFallback);
            Mockito.when(imdbMetadataService.findBestEffortDetails(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(imdbFirst, imdbFallback);

            HttpSeriesDetailsJsonServer handler = new HttpSeriesDetailsJsonServer();
            TestHttpExchange exchange = new TestHttpExchange(
                    "/seriesDetails?accountId=acc-1&seriesId=series-1&categoryId=cat-1&seriesName=Show%20(2021)",
                    "GET"
            );
            handler.handle(exchange);

            JSONObject response = new JSONObject(exchange.getResponseBodyText());
            assertTrue(response.has("seasonInfo"));
            JSONObject season = response.getJSONObject("seasonInfo");
            assertEquals("IMDB Title", season.getString("name"));
            assertEquals("2018", season.getString("releaseDate"));

            JSONArray episodes = response.getJSONArray("episodes");
            assertEquals(1, episodes.length());
            JSONObject ep = episodes.getJSONObject(0);
            assertEquals("Episode 1", ep.getString("name"));
            assertTrue(ep.getString("description").contains("Meta") || ep.getString("description").contains("Provider"));
            assertTrue(ep.getString("logo").contains("http://"));
        }
    }

    @Test
    void helperMethods_coverBranches() throws Exception {
        HttpSeriesDetailsJsonServer handler = new HttpSeriesDetailsJsonServer();

        Method normalize = HttpSeriesDetailsJsonServer.class.getDeclaredMethod("normalize", String.class);
        normalize.setAccessible(true);
        assertEquals("", normalize.invoke(handler, " "));
        assertEquals("hello world", normalize.invoke(handler, "Hello, World!"));

        Method safeNumeric = HttpSeriesDetailsJsonServer.class.getDeclaredMethod("safeNumeric", String.class);
        safeNumeric.setAccessible(true);
        assertEquals("", safeNumeric.invoke(handler, ""));
        assertEquals("012", safeNumeric.invoke(handler, "S01E2"));

        Method applyNameYearFallback = HttpSeriesDetailsJsonServer.class.getDeclaredMethod("applyNameYearFallback", JSONObject.class, String.class);
        applyNameYearFallback.setAccessible(true);
        JSONObject seasonInfo = new JSONObject();
        applyNameYearFallback.invoke(handler, seasonInfo, "My Show (2020)");
        assertEquals("My Show", seasonInfo.getString("name"));
        assertEquals("2020", seasonInfo.getString("releaseDate"));

        Method indexEpisodesMeta = HttpSeriesDetailsJsonServer.class.getDeclaredMethod("indexEpisodesMeta", JSONArray.class);
        indexEpisodesMeta.setAccessible(true);
        JSONArray meta = new JSONArray().put(new JSONObject().put("season", "1").put("episodeNum", "2").put("title", "Pilot"));
        Map<String, JSONObject> indexed = (Map<String, JSONObject>) indexEpisodesMeta.invoke(handler, meta);
        assertNotNull(indexed.get("1:2"));
        assertNotNull(indexed.get("title:pilot"));

        Method enrichEpisode = HttpSeriesDetailsJsonServer.class.getDeclaredMethod("enrichEpisode", Channel.class, Map.class);
        enrichEpisode.setAccessible(true);
        Channel channel = new Channel();
        channel.setSeason("1");
        channel.setEpisodeNum("2");
        enrichEpisode.invoke(handler, channel, indexed);
        assertEquals("", channel.getDescription());

        Method buildFuzzyHints = HttpSeriesDetailsJsonServer.class.getDeclaredMethod("buildFuzzyHints", String.class, JSONObject.class, JSONArray.class);
        buildFuzzyHints.setAccessible(true);
        JSONObject season = new JSONObject().put("name", "Show Season").put("plot", "Plot").put("releaseDate", "2021");
        JSONArray episodes = new JSONArray().put(new JSONObject().put("name", "S01E02").put("releaseDate", "2021"));
        List<String> hints = (List<String>) buildFuzzyHints.invoke(handler, "Show", season, episodes);
        assertTrue(hints.contains("Show"));

        Method firstNonBlank = HttpSeriesDetailsJsonServer.class.getDeclaredMethod("firstNonBlank", String[].class);
        firstNonBlank.setAccessible(true);
        assertEquals("b", firstNonBlank.invoke(handler, (Object) new String[]{"", "b", "c"}));
    }
}
