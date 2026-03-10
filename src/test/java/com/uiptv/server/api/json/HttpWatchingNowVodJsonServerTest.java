package com.uiptv.server.api.json;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.uiptv.db.VodChannelDb;
import com.uiptv.db.VodWatchStateDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.VodWatchState;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpWatchingNowVodJsonServerTest extends DbBackedTest {

    @Test
    void handle_returnsSortedVodRows_withProviderMetadata() throws Exception {
        Account account = createVodAccount("watching-vod-json");
        String categoryId = "vod-cat-1";

        Channel vodA = vodChannel("vod-1", "Provider One", "https://img/one.png");
        vodA.setDescription("Plot One");
        vodA.setReleaseDate("2021");
        vodA.setRating("7.1");
        vodA.setDuration("101");
        Channel vodB = vodChannel("vod-2", "Provider Two", "https://img/two.png");
        vodB.setDescription("Plot Two");
        vodB.setReleaseDate("2022");
        vodB.setRating("8.2");
        vodB.setDuration("120");
        VodChannelDb.get().saveAll(List.of(vodA, vodB), categoryId, account);

        VodWatchStateDb.get().upsert(vodState(account, categoryId, "vod-1", "Fallback One", 100L));
        VodWatchStateDb.get().upsert(vodState(account, categoryId, "vod-2", "Fallback Two", 200L));

        HttpWatchingNowVodJsonServer handler = new HttpWatchingNowVodJsonServer();
        StubHttpExchange exchange = new StubHttpExchange("/watchingNowVod", "GET", null);
        handler.handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        JSONArray rows = new JSONArray(exchange.getResponseBodyText());
        assertEquals(2, rows.length());

        JSONObject first = rows.getJSONObject(0);
        assertEquals("vod-2", first.getString("vodId"));
        assertEquals("Provider Two", first.getString("vodName"));
        assertEquals("https://img/two.png", first.getString("vodLogo"));
        assertEquals("", first.getString("plot"));
        assertEquals("", first.getString("releaseDate"));
        assertEquals("", first.getString("rating"));
        assertEquals("", first.getString("duration"));
        assertEquals(200L, first.getLong("updatedAt"));
        assertTrue(first.has("playItem"));

        JSONObject second = rows.getJSONObject(1);
        assertEquals("vod-1", second.getString("vodId"));
        assertEquals("Provider One", second.getString("vodName"));
    }

    @Test
    void handle_keepsFallbackRows_whenProviderMissing_andRejectsNonGet() throws Exception {
        Account account = createVodAccount("watching-vod-fallback");
        VodWatchStateDb.get().upsert(vodState(account, "missing-cat", "vod-missing", "Missing Title", 50L));

        HttpWatchingNowVodJsonServer handler = new HttpWatchingNowVodJsonServer();
        StubHttpExchange getExchange = new StubHttpExchange("/watchingNowVod", "GET", null);
        handler.handle(getExchange);

        assertEquals(200, getExchange.getResponseCode());
        JSONArray rows = new JSONArray(getExchange.getResponseBodyText());
        assertEquals(1, rows.length());
        assertEquals("vod-missing", rows.getJSONObject(0).getString("vodId"));
        assertEquals("Missing Title", rows.getJSONObject(0).getString("vodName"));
        assertTrue(rows.getJSONObject(0).has("playItem"));

        StubHttpExchange postExchange = new StubHttpExchange("/watchingNowVod", "POST", null);
        handler.handle(postExchange);
        assertEquals(405, postExchange.getResponseCode());
    }

    private Account createVodAccount(String name) {
        Account account = new Account(name, "user", "pass", "http://test.com/xtreme/", null, null, null, null, null, null, AccountType.XTREME_API, null, "http://test.com/xtreme/", false);
        account.setAction(Account.AccountAction.vod);
        AccountService.getInstance().save(account);
        Account persisted = AccountService.getInstance().getByName(name);
        persisted.setAction(Account.AccountAction.vod);
        return persisted;
    }

    private Channel vodChannel(String channelId, String name, String logo) {
        Channel channel = new Channel();
        channel.setChannelId(channelId);
        channel.setName(name);
        channel.setLogo(logo);
        channel.setCmd("http://vod/" + channelId);
        return channel;
    }

    private VodWatchState vodState(Account account, String categoryId, String vodId, String vodName, long updatedAt) {
        VodWatchState state = new VodWatchState();
        state.setAccountId(account.getDbId());
        state.setCategoryId(categoryId);
        state.setVodId(vodId);
        state.setVodName(vodName);
        state.setVodCmd("http://vod/" + vodId);
        state.setVodLogo("https://img/" + vodId + ".png");
        state.setUpdatedAt(updatedAt);
        return state;
    }

    private static class StubHttpExchange extends HttpExchange {
        private final URI requestUri;
        private final String method;
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private final byte[] requestBodyBytes;
        private int responseCode = -1;

        StubHttpExchange(String uri, String method, String body) {
            this.requestUri = URI.create(uri);
            this.method = method;
            this.requestBodyBytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
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
            // No-op.
        }

        @Override
        public InputStream getRequestBody() {
            return new ByteArrayInputStream(requestBodyBytes);
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
