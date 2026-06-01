package com.uiptv.server;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpPrincipal;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;

final class UndertowHttpHandlerAdapter implements io.undertow.server.HttpHandler {
    private final HttpHandler delegate;

    UndertowHttpHandlerAdapter(HttpHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }
        exchange.startBlocking();
        DelegatingHttpExchange httpExchange = new DelegatingHttpExchange(exchange);
        try {
            delegate.handle(httpExchange);
        } finally {
            httpExchange.finish();
        }
    }

    private static final class DelegatingHttpExchange extends HttpExchange {
        private final HttpServerExchange exchange;
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final URI requestUri;
        private final InputStream requestBody;
        private final OutputStream responseBody;
        private int responseCode = 200;
        private long responseLength = Long.MIN_VALUE;
        private boolean responseCommitted;

        private DelegatingHttpExchange(HttpServerExchange exchange) {
            this.exchange = exchange;
            copyHeaders(exchange.getRequestHeaders(), requestHeaders);
            String queryString = exchange.getQueryString();
            String requestUrl = exchange.getRequestURL();
            this.requestUri = URI.create(queryString == null || queryString.isEmpty() ? requestUrl : requestUrl + "?" + queryString);
            this.requestBody = exchange.getInputStream();
            this.responseBody = exchange.getOutputStream();
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
            return exchange.getRequestMethod().toString();
        }

        @Override
        public HttpContext getHttpContext() {
            return null;
        }

        @Override
        public void close() {
            try {
                responseBody.close();
            } catch (IOException _) {
                // The exchange is being torn down; a close failure is not actionable here.
            } finally {
                exchange.endExchange();
            }
        }

        @Override
        public InputStream getRequestBody() {
            return requestBody;
        }

        @Override
        public OutputStream getResponseBody() {
            return responseBody;
        }

        @Override
        public void sendResponseHeaders(int rCode, long responseLength) {
            if (responseCommitted) {
                return;
            }
            responseCommitted = true;
            responseCode = rCode;
            this.responseLength = responseLength;
            exchange.setStatusCode(rCode);
            copyHeaders(responseHeaders, exchange.getResponseHeaders());
            // HttpExchange uses 0 for chunked/unknown length; only positive
            // lengths should become a Content-Length header.
            if (responseLength > 0) {
                exchange.setResponseContentLength(responseLength);
            } else {
                exchange.getResponseHeaders().remove(HttpString.tryFromString("Content-Length"));
            }
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return exchange.getSourceAddress();
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return exchange.getDestinationAddress();
        }

        @Override
        public String getProtocol() {
            return exchange.getProtocol().toString();
        }

        @Override
        public Object getAttribute(String name) {
            return attributes().get(name);
        }

        @Override
        public void setAttribute(String name, Object value) {
            attributes().put(name, value);
        }

        @Override
        public void setStreams(InputStream i, OutputStream o) {
            // Undertow supplies the request and response streams for the lifetime of the exchange.
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }

        private void finish() throws IOException {
            if (!responseCommitted) {
                sendResponseHeaders(responseCode, responseLength == Long.MIN_VALUE ? -1 : responseLength);
            }
            if (responseLength <= 0) {
                exchange.endExchange();
            } else if (!exchange.isComplete()) {
                responseBody.flush();
            }
        }

        private Map<String, Object> attributes() {
            Map<String, Object> attributes = exchange.getAttachment(UndertowAttachments.attributes());
            if (attributes == null) {
                attributes = UndertowAttachments.newAttributes();
                exchange.putAttachment(UndertowAttachments.attributes(), attributes);
            }
            return attributes;
        }

        private static void copyHeaders(HeaderMap source, Headers target) {
            for (HttpString headerName : source.getHeaderNames()) {
                for (String value : source.get(headerName)) {
                    target.add(headerName.toString(), value);
                }
            }
        }

        private static void copyHeaders(Headers source, HeaderMap target) {
            for (var headerEntry : source.entrySet()) {
                String headerName = headerEntry.getKey();
                HttpString undertowHeader = HttpString.tryFromString(headerName);
                if (undertowHeader == null) {
                    undertowHeader = new HttpString(headerName);
                }
                for (String value : headerEntry.getValue()) {
                    target.add(undertowHeader, value);
                }
            }
        }
    }
}
