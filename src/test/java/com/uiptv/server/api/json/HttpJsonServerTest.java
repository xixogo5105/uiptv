package com.uiptv.server.api.json;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.SeriesChannelDb;
import com.uiptv.db.SeriesEpisodeDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.util.AccountType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpJsonServerTest extends DbBackedTest {

    @Test
    void categoryServer_returnsCategoriesForAccount() throws Exception {
        Account account = createAccount("cat-api");
        List<Category> categories = List.of(
                new Category("10", "Sports", "sports", false, 0),
                new Category("11", "Movies", "movies", false, 0)
        );
        CategoryDb.get().saveAll(categories, account);

        HttpCategoryJsonServer handler = new HttpCategoryJsonServer();
        StubHttpExchange exchange = new StubHttpExchange("/category?accountId=" + account.getDbId(), "GET");
        handler.handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        JSONArray response = new JSONArray(exchange.getResponseBodyText());
        assertEquals(2, response.length());
        assertTrue(exchange.getResponseHeaders().getFirst("Content-Type").contains("application/json"));
    }

    @Test
    void channelServer_allCategory_aggregatesChannelsAcrossCategories() throws Exception {
        Account account = createAccount("channel-all-api");
        CategoryDb categoryDb = CategoryDb.get();
        List<Category> categories = new ArrayList<>();
        categories.add(new Category("all", "All", "all", false, 0));
        categories.add(new Category("10", "Sports", "sports", false, 0));
        categories.add(new Category("11", "Movies", "movies", false, 0));
        categoryDb.saveAll(categories, account);

        List<Category> dbCategories = categoryDb.getCategories(account);
        Category sports = dbCategories.stream().filter(c -> "Sports".equals(c.getTitle())).findFirst().orElseThrow();
        Category movies = dbCategories.stream().filter(c -> "Movies".equals(c.getTitle())).findFirst().orElseThrow();

        ChannelDb.get().saveAll(
                List.of(new Channel("c1", "Sports One", "1", "cmd://sports", null, null, null, "logo", 0, 1, 1, null, null, null, null, null)),
                sports.getDbId(),
                account
        );
        ChannelDb.get().saveAll(
                List.of(new Channel("c2", "Movie One", "2", "cmd://movie", null, null, null, "logo", 0, 1, 1, null, null, null, null, null)),
                movies.getDbId(),
                account
        );

        HttpChannelJsonServer handler = new HttpChannelJsonServer();
        StubHttpExchange exchange = new StubHttpExchange("/channel?accountId=" + account.getDbId() + "&categoryId=All", "GET");
        handler.handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        JSONArray response = new JSONArray(exchange.getResponseBodyText());
        assertEquals(2, response.length());
    }

    @Test
    void channelServer_allCategory_withOnlyAllCategory_returnsAllCategoryChannels() throws Exception {
        Account account = createAccount("channel-all-only-api");
        CategoryDb categoryDb = CategoryDb.get();
        categoryDb.saveAll(List.of(new Category("all", "All", "all", false, 0)), account);
        Category allCategory = categoryDb.getCategories(account).get(0);

        ChannelDb.get().saveAll(
                List.of(new Channel("c-all", "All One", "1", "cmd://all", null, null, null, "logo", 0, 1, 1, null, null, null, null, null)),
                allCategory.getDbId(),
                account
        );

        HttpChannelJsonServer handler = new HttpChannelJsonServer();
        StubHttpExchange exchange = new StubHttpExchange("/channel?accountId=" + account.getDbId() + "&categoryId=All", "GET");
        handler.handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        JSONArray response = new JSONArray(exchange.getResponseBodyText());
        assertEquals(1, response.length());
        assertEquals("All One", response.getJSONObject(0).getString("name"));
    }

    @Test
    void webChannelServer_allCategory_withOnlyAllCategory_returnsAllCategoryChannels() throws Exception {
        Account account = createAccount("web-channel-all-only-api");
        CategoryDb categoryDb = CategoryDb.get();
        categoryDb.saveAll(List.of(new Category("all", "All", "all", false, 0)), account);
        Category allCategory = categoryDb.getCategories(account).get(0);

        ChannelDb.get().saveAll(
                List.of(new Channel("c-web-all", "Web All One", "1", "cmd://web-all", null, null, null, "logo", 0, 1, 1, null, null, null, null, null)),
                allCategory.getDbId(),
                account
        );

        HttpWebChannelJsonServer handler = new HttpWebChannelJsonServer();
        StubHttpExchange exchange = new StubHttpExchange("/channels?accountId=" + account.getDbId() + "&categoryId=All&page=0&pageSize=50", "GET");
        handler.handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        JSONObject response = new JSONObject(exchange.getResponseBodyText());
        JSONArray items = response.getJSONArray("items");
        assertEquals(1, items.length());
        assertEquals("Web All One", items.getJSONObject(0).getString("name"));
    }

    @Test
    void channelServer_specificCategory_returnsOnlyCategoryChannels() throws Exception {
        Account account = createAccount("channel-specific-api");
        CategoryDb categoryDb = CategoryDb.get();
        categoryDb.saveAll(List.of(new Category("10", "Sports", "sports", false, 0)), account);
        Category sports = categoryDb.getCategories(account).get(0);

        ChannelDb.get().saveAll(
                List.of(
                        new Channel("c1", "Sports One", "1", "cmd://sports-1", null, null, null, "logo", 0, 1, 1, null, null, null, null, null),
                        new Channel("c2", "Sports Two", "2", "cmd://sports-2", null, null, null, "logo", 0, 1, 1, null, null, null, null, null)
                ),
                sports.getDbId(),
                account
        );

        HttpChannelJsonServer handler = new HttpChannelJsonServer();
        StubHttpExchange exchange = new StubHttpExchange("/channel?accountId=" + account.getDbId() + "&categoryId=" + sports.getDbId(), "GET");
        handler.handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        JSONArray response = new JSONArray(exchange.getResponseBodyText());
        assertEquals(2, response.length());
        assertEquals("Sports One", response.getJSONObject(0).getString("name"));
    }

    @Test
    void channelServer_seriesRows_includeWatchedFlagFromSeriesWatchState() throws Exception {
        Account account = createXtremeAccount("series-rows-watched-api");
        account.setAction(Account.AccountAction.series);
        AccountService.getInstance().save(account);
        account = AccountService.getInstance().getByName("series-rows-watched-api");
        account.setAction(Account.AccountAction.series);

        SeriesCategoryDb.get().saveAll(List.of(
                new Category("201", "Series", "series", false, 0)
        ), account);
        Category seriesCategory = SeriesCategoryDb.get().getCategories(account).get(0);

        SeriesChannelDb.get().saveAll(List.of(
                new Channel("s-1", "Series One", "", "cmd://series-1", null, null, null, "logo", 0, 1, 1, null, null, null, null, null),
                new Channel("s-2", "Series Two", "", "cmd://series-2", null, null, null, "logo", 0, 1, 1, null, null, null, null, null)
        ), seriesCategory.getDbId(), account);

        SeriesWatchStateService.getInstance().markSeriesEpisodeManual(
                account,
                seriesCategory.getCategoryId(),
                "s-2",
                "ep-22",
                "Episode 22",
                "1",
                "22"
        );

        HttpChannelJsonServer handler = new HttpChannelJsonServer();
        StubHttpExchange exchange = new StubHttpExchange("/channel?accountId=" + account.getDbId() + "&categoryId=" + seriesCategory.getDbId() + "&mode=series", "GET");
        handler.handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        JSONArray response = new JSONArray(exchange.getResponseBodyText());
        assertEquals(2, response.length());
        Map<String, Boolean> watchedBySeriesId = new HashMap<>();
        for (int i = 0; i < response.length(); i++) {
            JSONObject row = response.getJSONObject(i);
            watchedBySeriesId.put(row.optString("channelId"), isWatched(row));
        }
        assertEquals(Boolean.FALSE, watchedBySeriesId.get("s-1"));
        assertEquals(Boolean.TRUE, watchedBySeriesId.get("s-2"));
    }

    @Test
    void seriesEpisodesServer_cachedEpisodes_includeWatchedFlagFromSeriesWatchState() throws Exception {
        Account account = createXtremeAccount("series-episodes-watched-api");
        account.setAction(Account.AccountAction.series);
        AccountService.getInstance().save(account);
        account = AccountService.getInstance().getByName("series-episodes-watched-api");
        account.setAction(Account.AccountAction.series);

        String seriesId = "series-901";
        List<Channel> episodes = List.of(
                buildEpisodeChannel("ep-1", "Episode 1", "1", "1"),
                buildEpisodeChannel("ep-2", "Episode 2", "1", "2")
        );
        SeriesEpisodeDb.get().saveAll(account, "series-category-901", seriesId, episodes);

        SeriesWatchStateService.getInstance().markSeriesEpisodeManual(
                account,
                "series-category-901",
                seriesId,
                "ep-2",
                "Episode 2",
                "1",
                "2"
        );

        HttpSeriesEpisodesJsonServer handler = new HttpSeriesEpisodesJsonServer();
        StubHttpExchange exchange = new StubHttpExchange("/seriesEpisodes?accountId=" + account.getDbId()
                + "&seriesId=" + seriesId
                + "&categoryId=series-category-901", "GET");
        handler.handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        JSONArray response = new JSONArray(exchange.getResponseBodyText());
        assertEquals(2, response.length());
        Map<String, Boolean> watchedByEpisodeId = new HashMap<>();
        for (int i = 0; i < response.length(); i++) {
            JSONObject row = response.getJSONObject(i);
            watchedByEpisodeId.put(row.optString("channelId"), isWatched(row));
        }
        assertEquals(Boolean.FALSE, watchedByEpisodeId.get("ep-1"));
        assertEquals(Boolean.TRUE, watchedByEpisodeId.get("ep-2"));
    }

    @Test
    void seriesEpisodesServer_duplicateEpisodeIdAcrossSeasons_marksOnlyMatchingSeason() throws Exception {
        Account account = createXtremeAccount("series-episodes-duplicate-id-api");
        account.setAction(Account.AccountAction.series);
        AccountService.getInstance().save(account);
        account = AccountService.getInstance().getByName("series-episodes-duplicate-id-api");
        account.setAction(Account.AccountAction.series);

        String seriesId = "series-902";
        List<Channel> episodes = List.of(
                buildEpisodeChannel("10", "Episode 10 S1", "1", "10"),
                buildEpisodeChannel("10", "Episode 10 S2", "2", "10")
        );
        SeriesEpisodeDb.get().saveAll(account, "series-category-902", seriesId, episodes);

        SeriesWatchStateService.getInstance().markSeriesEpisodeManual(
                account,
                "series-category-902",
                seriesId,
                "10",
                "Episode 10 S2",
                "2",
                "10"
        );

        HttpSeriesEpisodesJsonServer handler = new HttpSeriesEpisodesJsonServer();
        StubHttpExchange exchange = new StubHttpExchange("/seriesEpisodes?accountId=" + account.getDbId()
                + "&seriesId=" + seriesId
                + "&categoryId=series-category-902", "GET");
        handler.handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        JSONArray response = new JSONArray(exchange.getResponseBodyText());
        assertEquals(2, response.length());
        Map<String, Boolean> watchedBySeason = new HashMap<>();
        for (int i = 0; i < response.length(); i++) {
            JSONObject row = response.getJSONObject(i);
            watchedBySeason.put(row.optString("season"), isWatched(row));
        }
        assertEquals(Boolean.FALSE, watchedBySeason.get("1"));
        assertEquals(Boolean.TRUE, watchedBySeason.get("2"));
    }

    private Channel buildEpisodeChannel(String channelId, String name, String season, String episodeNum) {
        Channel channel = new Channel();
        channel.setChannelId(channelId);
        channel.setName(name);
        channel.setCmd("http://example.com/series/" + channelId + ".m3u8");
        channel.setSeason(season);
        channel.setEpisodeNum(episodeNum);
        return channel;
    }

    private boolean isWatched(JSONObject row) {
        Object watched = row.opt("watched");
        if (watched instanceof Boolean) {
            return (Boolean) watched;
        }
        String value = String.valueOf(watched == null ? "" : watched).trim().toLowerCase();
        return "1".equals(value) || "true".equals(value);
    }

    private Account createAccount(String name) {
        Account account = new Account(name, "user", "pass", "http://test.com", "00:11:22:33:44:55", null, null, null, null, null, AccountType.M3U8_URL, null, "http://test.com/playlist.m3u8", false);
        AccountService.getInstance().save(account);
        return AccountService.getInstance().getByName(name);
    }

    private Account createXtremeAccount(String name) {
        Account account = new Account(name, "user", "pass", "http://test.com/xtreme/", null, null, null, null, null, null, AccountType.XTREME_API, null, "http://test.com/xtreme/", false);
        AccountService.getInstance().save(account);
        return AccountService.getInstance().getByName(name);
    }

    private static class StubHttpExchange extends HttpExchange {
        private final URI requestUri;
        private final String method;
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private int responseCode = -1;

        StubHttpExchange(String uri, String method) {
            this.requestUri = URI.create(uri);
            this.method = method;
        }

        String getResponseBodyText() {
            return responseBody.toString(StandardCharsets.UTF_8);
        }

        @Override
        public Headers getRequestHeaders() {
            return requestHeaders;
        }

        @Override
        public Headers getResponseHeaders() {
            return responseHeaders;
        }

        @Override
        public URI getRequestURI() {
            return requestUri;
        }

        @Override
        public String getRequestMethod() {
            return method;
        }

        @Override
        public HttpContext getHttpContext() {
            return null;
        }

        @Override
        public void close() {
        }

        @Override
        public InputStream getRequestBody() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public OutputStream getResponseBody() {
            return responseBody;
        }

        @Override
        public void sendResponseHeaders(int rCode, long responseLength) {
            this.responseCode = rCode;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public void setAttribute(String name, Object value) {
        }

        @Override
        public void setStreams(InputStream i, OutputStream o) {
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }
    }
}
