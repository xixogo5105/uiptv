package com.uiptv.server.api.json;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.uiptv.db.VodCategoryDb;
import com.uiptv.db.VodChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.service.ImdbMetadataService;
import com.uiptv.util.AccountType;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.uiptv.model.Account.AccountAction.vod;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpVodDetailsJsonServerTest extends DbBackedTest {

    @Test
    void handle_mergesProviderFallbackWithImdbMetadata() throws Exception {
        Account account = createVodAccount("vod-details");
        VodCategoryDb.get().saveAll(List.of(new Category("vod-cat", "Movies", "movies", false, 0)), account);
        Category category = VodCategoryDb.get().getCategories(account).getFirst();
        VodChannelDb.get().saveAll(List.of(channel("vod-9", "Provider Title", "https://img/provider.png")), category.getDbId(), account);

        ImdbMetadataService imdbService = Mockito.mock(ImdbMetadataService.class);
        JSONObject imdb = new JSONObject();
        imdb.put("plot", "IMDB Plot");
        imdb.put("rating", "8.7");
        imdb.put("releaseDate", "2024-06-01");
        imdb.put("cover", "https://img/imdb.png");
        imdb.put("imdbUrl", "https://www.imdb.com/title/tt1234567/");
        Mockito.when(imdbService.findBestEffortMovieDetails(Mockito.anyString(), Mockito.anyString(), Mockito.anyList()))
                .thenReturn(imdb);

        try (MockedStatic<ImdbMetadataService> imdbStatic = Mockito.mockStatic(ImdbMetadataService.class)) {
            imdbStatic.when(ImdbMetadataService::getInstance).thenReturn(imdbService);

            HttpVodDetailsJsonServer handler = new HttpVodDetailsJsonServer();
            StubHttpExchange exchange = new StubHttpExchange("/vodDetails?accountId=" + account.getDbId() + "&categoryId=" + category.getDbId() + "&channelId=vod-9&vodName=Movie+Nine", "GET");
            handler.handle(exchange);

            assertEquals(200, exchange.getResponseCode());
            JSONObject response = new JSONObject(exchange.getResponseBodyText()).getJSONObject("vodInfo");
            assertEquals("Movie Nine", response.getString("name"));
            assertEquals("https://img/provider.png", response.getString("cover"));
            assertEquals("IMDB Plot", response.getString("plot"));
            assertEquals("8.7", response.getString("rating"));
            assertEquals("2024-06-01", response.getString("releaseDate"));
            assertEquals("https://www.imdb.com/title/tt1234567/", response.getString("imdbUrl"));
        }
    }

    private Account createVodAccount(String name) {
        Account account = new Account(name, "user", "pass", "http://test.com/xtreme/", null, null, null, null, null, null, AccountType.XTREME_API, null, "http://test.com/xtreme/", false);
        account.setAction(vod);
        AccountService.getInstance().save(account);
        Account persisted = AccountService.getInstance().getByName(name);
        persisted.setAction(vod);
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
