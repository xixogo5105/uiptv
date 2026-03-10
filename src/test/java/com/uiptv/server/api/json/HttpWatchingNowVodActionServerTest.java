package com.uiptv.server.api.json;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.uiptv.db.VodWatchStateDb;
import com.uiptv.model.Account;
import com.uiptv.model.VodWatchState;
import com.uiptv.service.AccountService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.util.AccountType;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpWatchingNowVodActionServerTest extends DbBackedTest {

    @Test
    void handle_rejectsUnsupportedMethod() throws Exception {
        HttpWatchingNowVodActionServer handler = new HttpWatchingNowVodActionServer();
        StubHttpExchange exchange = new StubHttpExchange("/watchingNowVodAction", "GET", null);
        handler.handle(exchange);
        assertEquals(405, exchange.getResponseCode());
    }

    @Test
    void handle_postAndDelete_roundTripWorks() throws Exception {
        Account account = createAccount("vod-action");
        HttpWatchingNowVodActionServer handler = new HttpWatchingNowVodActionServer();

        JSONObject missing = new JSONObject();
        StubHttpExchange missingExchange = new StubHttpExchange("/watchingNowVodAction", "POST", missing.toString());
        handler.handle(missingExchange);
        assertEquals(400, missingExchange.getResponseCode());

        JSONObject notFound = new JSONObject();
        notFound.put("accountId", "missing");
        notFound.put("vodId", "vod-1");
        StubHttpExchange notFoundExchange = new StubHttpExchange("/watchingNowVodAction", "POST", notFound.toString());
        handler.handle(notFoundExchange);
        assertEquals(404, notFoundExchange.getResponseCode());

        JSONObject create = new JSONObject();
        create.put("accountId", account.getDbId());
        create.put("categoryId", "vod-cat");
        create.put("vodId", "vod-1");
        create.put("vodName", "Movie One");
        create.put("vodCmd", "http://vod/1");
        create.put("vodLogo", "http://img/1.png");
        StubHttpExchange createExchange = new StubHttpExchange("/watchingNowVodAction", "POST", create.toString());
        handler.handle(createExchange);
        assertEquals(200, createExchange.getResponseCode());

        VodWatchState saved = VodWatchStateDb.get().getByVod(account.getDbId(), "vod-cat", "vod-1");
        assertNotNull(saved);
        assertEquals("Movie One", saved.getVodName());

        JSONObject deleteMissing = new JSONObject();
        deleteMissing.put("accountId", account.getDbId());
        StubHttpExchange deleteMissingExchange = new StubHttpExchange("/watchingNowVodAction", "DELETE", deleteMissing.toString());
        handler.handle(deleteMissingExchange);
        assertEquals(400, deleteMissingExchange.getResponseCode());

        JSONObject delete = new JSONObject();
        delete.put("accountId", account.getDbId());
        delete.put("categoryId", "vod-cat");
        delete.put("vodId", "vod-1");
        StubHttpExchange deleteExchange = new StubHttpExchange("/watchingNowVodAction", "DELETE", delete.toString());
        handler.handle(deleteExchange);
        assertEquals(200, deleteExchange.getResponseCode());
        assertTrue(VodWatchStateDb.get().getByVod(account.getDbId(), "vod-cat", "vod-1") == null);
    }

    private Account createAccount(String name) {
        Account account = new Account(name, "user", "pass", "http://test.com/xtreme/", null, null, null, null, null, null, AccountType.XTREME_API, null, "http://test.com/xtreme/", false);
        account.setAction(Account.AccountAction.vod);
        AccountService.getInstance().save(account);
        return AccountService.getInstance().getByName(name);
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
