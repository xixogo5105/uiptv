package com.uiptv.server.api.json;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.uiptv.model.Account;
import com.uiptv.model.Configuration;
import com.uiptv.model.ThemeMode;
import com.uiptv.service.AccountService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.testsupport.DbBackedTest;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpAccountAndConfigJsonServerTest extends DbBackedTest {

    @Test
    void accountServer_returnsPersistedAccountsAsJson() throws Exception {
        AccountService.getInstance().save(new Account("acc-one", "user1", "pass", "http://a.test", "00:11:22:33:44:55", null, null, null, null, null, AccountType.M3U8_URL, null, "http://a.test/list.m3u8", false));
        AccountService.getInstance().save(new Account("acc-two", "user2", "pass", "http://b.test", "00:11:22:33:44:56", null, null, null, null, null, AccountType.XTREME_API, null, "http://b.test/xtreme/", false));

        HttpAccountJsonServer handler = new HttpAccountJsonServer();
        StubHttpExchange exchange = new StubHttpExchange("/account", "GET");
        handler.handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        assertTrue(exchange.getResponseHeaders().getFirst("Content-Type").contains("application/json"));

        JSONArray response = new JSONArray(exchange.getResponseBodyText());
        assertEquals(2, response.length());
        assertEquals("acc-one", response.getJSONObject(0).getString("accountName"));
        assertEquals("acc-two", response.getJSONObject(1).getString("accountName"));
    }

    @Test
    void configServer_returnsTheThreeStateThemeSelection() throws Exception {
        HttpConfigJsonServer handler = new HttpConfigJsonServer();

        for (ThemeMode mode : ThemeMode.values()) {
            Configuration configuration = ConfigurationService.getInstance().read();
            configuration.applyThemeMode(mode);
            ConfigurationService.getInstance().save(configuration);

            StubHttpExchange exchange = new StubHttpExchange("/config", "GET");
            handler.handle(exchange);

            assertEquals(200, exchange.getResponseCode());
            JSONObject response = new JSONObject(exchange.getResponseBodyText());
            assertEquals(mode.persistedValue(), response.getString("themeMode"));
        }
    }

    @Test
    void configServer_rejectsAThemeWrite() throws Exception {
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.applyThemeMode(ThemeMode.DARK);
        ConfigurationService.getInstance().save(configuration);

        HttpConfigJsonServer handler = new HttpConfigJsonServer();
        StubHttpExchange exchange = new StubHttpExchange("/config", "POST", "{\"themeMode\":\"light\"}");
        handler.handle(exchange);

        // generateResponse only allows GET, so a write cannot silently downgrade the stored selection.
        assertEquals(405, exchange.getResponseCode());
        assertEquals(ThemeMode.DARK, ConfigurationService.getInstance().read().resolveThemeMode());
    }

    @Test
    void configServer_returnsThumbnailFlagFromConfiguration() throws Exception {
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setEnableThumbnails(true);
        ConfigurationService.getInstance().save(configuration);

        HttpConfigJsonServer handler = new HttpConfigJsonServer();
        StubHttpExchange exchange = new StubHttpExchange("/config", "GET");
        handler.handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        JSONObject response = new JSONObject(exchange.getResponseBodyText());
        assertTrue(response.getBoolean("enableThumbnails"));
    }

    private static class StubHttpExchange extends HttpExchange {
        private final URI requestUri;
        private final String method;
        private final String requestBody;
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private int responseCode = -1;

        StubHttpExchange(String uri, String method) {
            this(uri, method, null);
        }

        StubHttpExchange(String uri, String method, String requestBody) {
            this.requestUri = URI.create(uri);
            this.method = method;
            this.requestBody = requestBody;
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
            //not needed
        }

        @Override
        public InputStream getRequestBody() {
            return new ByteArrayInputStream(
                    requestBody == null ? new byte[0] : requestBody.getBytes(StandardCharsets.UTF_8)
            );
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
            //not needed
        }

        @Override
        public void setStreams(InputStream i, OutputStream o) {
            //not needed
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }
    }
}
