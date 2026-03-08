package com.uiptv.server.api.json;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.uiptv.model.Account;
import com.uiptv.service.AccountService;
import com.uiptv.service.CategoryService;
import com.uiptv.util.AccountType;
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

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.model.Account.AccountAction.series;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpCategoryJsonServerTest {

    @Test
    void handle_returnsEmptyArrayWhenAccountIsMissing() throws Exception {
        try (MockedStatic<AccountService> accountServiceStatic = Mockito.mockStatic(AccountService.class)) {
            AccountService accountService = Mockito.mock(AccountService.class);
            accountServiceStatic.when(AccountService::getInstance).thenReturn(accountService);
            Mockito.when(accountService.getById("404")).thenReturn(null);

            StubHttpExchange exchange = new StubHttpExchange("/category?accountId=404", "GET");
            new HttpCategoryJsonServer().handle(exchange);

            assertEquals(200, exchange.getResponseCode());
            assertEquals("[]", exchange.getResponseBodyText());
        }
    }

    @Test
    void handle_appliesModeAndFallsBackToItvForInvalidValues() throws Exception {
        Account account = new Account("categories", "user", "pass", "http://127.0.0.1/mock", null, null, null, null, null, null, AccountType.XTREME_API, null, "http://127.0.0.1/mock", false);

        try (MockedStatic<AccountService> accountServiceStatic = Mockito.mockStatic(AccountService.class);
             MockedStatic<CategoryService> categoryServiceStatic = Mockito.mockStatic(CategoryService.class)) {
            AccountService accountService = Mockito.mock(AccountService.class);
            CategoryService categoryService = Mockito.mock(CategoryService.class);
            accountServiceStatic.when(AccountService::getInstance).thenReturn(accountService);
            categoryServiceStatic.when(CategoryService::getInstance).thenReturn(categoryService);
            Mockito.when(accountService.getById("1")).thenReturn(account);
            Mockito.when(categoryService.readToJson(account)).thenReturn("[{\"id\":\"10\"}]");

            StubHttpExchange validMode = new StubHttpExchange("/category?accountId=1&mode=SERIES", "GET");
            new HttpCategoryJsonServer().handle(validMode);
            assertEquals(series, account.getAction());
            assertEquals("[{\"id\":\"10\"}]", validMode.getResponseBodyText());

            StubHttpExchange invalidMode = new StubHttpExchange("/category?accountId=1&mode=not-a-mode", "GET");
            new HttpCategoryJsonServer().handle(invalidMode);
            assertEquals(itv, account.getAction());
            assertEquals("[{\"id\":\"10\"}]", invalidMode.getResponseBodyText());
        }
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
            responseBody.reset();
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
            responseCode = rCode;
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
            throw new UnsupportedOperationException("Attributes are not used in this test exchange");
        }

        @Override
        public void setStreams(InputStream i, OutputStream o) {
            throw new UnsupportedOperationException("Custom streams are not supported in this test exchange");
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }
    }
}
