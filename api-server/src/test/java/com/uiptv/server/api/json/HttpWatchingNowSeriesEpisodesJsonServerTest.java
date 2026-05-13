package com.uiptv.server.api.json;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.uiptv.db.SeriesWatchingNowSnapshotDb;
import com.uiptv.model.Account;
import com.uiptv.model.SeriesWatchingNowSnapshot;
import com.uiptv.service.AccountService;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.testsupport.DbBackedTest;
import com.uiptv.util.AccountType;
import org.json.JSONArray;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpWatchingNowSeriesEpisodesJsonServerTest extends DbBackedTest {

    @Test
    void handle_fallsBackToWatchingNowSnapshotWhenEpisodeCacheIsMissing() throws Exception {
        Account account = createAccount("watching-now-episodes-snapshot");
        SeriesWatchStateService.getInstance().markSeriesEpisodeManual(
                account,
                "series-cat",
                "series-1",
                "ep-2",
                "Episode Two",
                "1",
                "2"
        );

        SeriesWatchingNowSnapshot snapshot = new SeriesWatchingNowSnapshot();
        snapshot.setAccountId(account.getDbId());
        snapshot.setCategoryId("series-cat");
        snapshot.setSeriesId("series-1");
        snapshot.setCategoryDbId("series-cat-db");
        snapshot.setSeriesTitle("Series One");
        snapshot.setSeriesPoster("http://img/series-1.png");
        snapshot.setEpisodesJson(new JSONArray()
                .put("{\"channelId\":\"ep-1\",\"name\":\"Episode One\",\"cmd\":\"http://stream/ep-1\",\"season\":\"1\",\"episodeNum\":\"1\"}")
                .put("{\"channelId\":\"ep-2\",\"name\":\"Episode Two\",\"cmd\":\"http://stream/ep-2\",\"season\":\"1\",\"episodeNum\":\"2\"}")
                .toString());
        snapshot.setUpdatedAt(123L);
        SeriesWatchingNowSnapshotDb.get().upsert(snapshot);

        HttpWatchingNowSeriesEpisodesJsonServer handler = new HttpWatchingNowSeriesEpisodesJsonServer();
        StubHttpExchange exchange = new StubHttpExchange("/watchingNowSeriesEpisodes?accountId="
                + account.getDbId() + "&categoryId=series-cat&seriesId=series-1", "GET");
        handler.handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        JSONArray response = new JSONArray(exchange.getResponseBodyText());
        assertEquals(2, response.length());
        assertEquals("ep-1", response.getJSONObject(0).getString("channelId"));
        assertEquals("0", response.getJSONObject(0).getString("watched"));
        assertEquals("1", response.getJSONObject(1).getString("watched"));
    }

    private Account createAccount(String name) {
        Account account = new Account(name, "user", "pass", "http://test.com/xtreme/", null, null, null, null, null, null, AccountType.XTREME_API, null, "http://test.com/xtreme/", false);
        account.setAction(Account.AccountAction.series);
        AccountService.getInstance().save(account);
        Account persisted = AccountService.getInstance().getByName(name);
        persisted.setAction(Account.AccountAction.series);
        return persisted;
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
            // No-op for test stub.
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
            // No-op for test stub.
        }

        @Override
        public void setStreams(InputStream i, OutputStream o) {
            // No-op for test stub.
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }
    }
}
