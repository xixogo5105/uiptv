package com.uiptv.server.api.json;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.test.DbBackedTest;
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
import java.util.ArrayList;
import java.util.List;

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

    private Account createAccount(String name) {
        Account account = new Account(name, "user", "pass", "http://test.com", "00:11:22:33:44:55", null, null, null, null, null, AccountType.M3U8_URL, null, "http://test.com/playlist.m3u8", false);
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
