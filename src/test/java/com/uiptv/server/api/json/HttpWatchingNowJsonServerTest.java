package com.uiptv.server.api.json;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.SeriesChannelDb;
import com.uiptv.db.SeriesWatchStateDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.service.AccountService;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpWatchingNowJsonServerTest extends DbBackedTest {

    @Test
    void handle_dedupesLatestState_resolvesCachedSeriesMetadata_andSortsRows() throws Exception {
        Account account = createSeriesAccount("watching-now-dedupe");

        SeriesCategoryDb.get().saveAll(List.of(
                new Category("cat-api-1", "Drama", "drama", false, 0),
                new Category("cat-api-2", "Comedy", "comedy", false, 0)
        ), account);
        List<Category> categories = SeriesCategoryDb.get().getCategories(account);
        Category drama = categories.stream().filter(c -> "Drama".equals(c.getTitle())).findFirst().orElseThrow();
        Category comedy = categories.stream().filter(c -> "Comedy".equals(c.getTitle())).findFirst().orElseThrow();

        SeriesChannelDb.get().saveAll(List.of(
                channel("series-a", "Alpha Show", "https://img/alpha.png"),
                channel("series-b", "Beta Show", "https://img/beta.png")
        ), drama.getDbId(), account);
        SeriesChannelDb.get().saveAll(List.of(
                channel("series-c", "Comedy Nights", "https://img/comedy.png")
        ), comedy.getDbId(), account);

        SeriesWatchStateDb.get().upsert(state(account, drama.getCategoryId(), "series-a", "ep-old", "Old Episode", "1", 1, 100L));
        SeriesWatchStateDb.get().upsert(state(account, drama.getCategoryId(), "series-a", "ep-new", "New Episode", "1", 2, 300L));
        SeriesWatchStateDb.get().upsert(state(account, comedy.getDbId(), "series-c", "ep-comedy", "Comedy Episode", "2", 4, 200L));

        HttpWatchingNowJsonServer handler = new HttpWatchingNowJsonServer();
        StubHttpExchange exchange = new StubHttpExchange("/watching-now", "GET");
        handler.handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        JSONArray response = new JSONArray(exchange.getResponseBodyText());
        assertEquals(2, response.length());

        JSONObject first = response.getJSONObject(0);
        assertEquals("series-a", first.getString("seriesId"));
        assertEquals("ep-new", first.getString("episodeId"));
        assertEquals("Alpha Show", first.getString("seriesTitle"));
        assertEquals("https://img/alpha.png", first.getString("seriesPoster"));
        assertEquals(drama.getDbId(), first.getString("categoryDbId"));
        assertEquals(300L, first.getLong("updatedAt"));

        JSONObject second = response.getJSONObject(1);
        assertEquals("series-c", second.getString("seriesId"));
        assertEquals("Comedy Nights", second.getString("seriesTitle"));
        assertEquals(comedy.getDbId(), second.getString("categoryDbId"));
        assertEquals(200L, second.getLong("updatedAt"));
    }

    @Test
    void handle_skipsNumericSeriesIdsWithoutCachedMetadata_butKeepsNonNumericFallbackRows() throws Exception {
        Account account = createSeriesAccount("watching-now-fallback");

        SeriesWatchStateDb.get().upsert(state(account, "unknown-category", "12345", "ep-1", "Episode 1", "1", 1, 100L));
        SeriesWatchStateDb.get().upsert(state(account, "unknown-category", "series-slug", "ep-2", "Episode 2", "1", 2, 200L));

        HttpWatchingNowJsonServer handler = new HttpWatchingNowJsonServer();
        StubHttpExchange exchange = new StubHttpExchange("/watching-now", "GET");
        handler.handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        JSONArray response = new JSONArray(exchange.getResponseBodyText());
        assertEquals(1, response.length());

        JSONObject row = response.getJSONObject(0);
        assertEquals("series-slug", row.getString("seriesId"));
        assertEquals("series-slug", row.getString("seriesTitle"));
        assertEquals("", row.getString("categoryDbId"));
        assertEquals("", row.getString("seriesPoster"));
    }

    private Account createSeriesAccount(String name) {
        Account account = new Account(name, "user", "pass", "http://test.com/xtreme/", null, null, null, null, null, null, AccountType.XTREME_API, null, "http://test.com/xtreme/", false);
        account.setAction(Account.AccountAction.series);
        AccountService.getInstance().save(account);
        Account persisted = AccountService.getInstance().getByName(name);
        persisted.setAction(Account.AccountAction.series);
        return persisted;
    }

    private Channel channel(String channelId, String name, String logo) {
        Channel channel = new Channel();
        channel.setChannelId(channelId);
        channel.setName(name);
        channel.setLogo(logo);
        channel.setCmd("http://example.com/" + channelId);
        return channel;
    }

    private SeriesWatchState state(Account account, String categoryId, String seriesId, String episodeId,
                                   String episodeName, String season, int episodeNum, long updatedAt) {
        SeriesWatchState state = new SeriesWatchState();
        state.setAccountId(account.getDbId());
        state.setMode("series");
        state.setCategoryId(categoryId);
        state.setSeriesId(seriesId);
        state.setEpisodeId(episodeId);
        state.setEpisodeName(episodeName);
        state.setSeason(season);
        state.setEpisodeNum(episodeNum);
        state.setUpdatedAt(updatedAt);
        state.setSource("MANUAL");
        state.setSeriesCategorySnapshot("");
        state.setSeriesChannelSnapshot("");
        state.setSeriesEpisodeSnapshot("");
        return state;
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
            // No resources to release in the test double.
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
            // Attributes are not needed by these tests.
        }

        @Override
        public void setStreams(InputStream i, OutputStream o) {
            // The test exchange uses in-memory streams created at construction time.
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }
    }
}
