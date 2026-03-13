package com.uiptv.server;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class TestHttpExchange extends HttpExchange {
    private final URI requestUri;
    private final String method;
    private final Headers requestHeaders = new Headers();
    private final Headers responseHeaders = new Headers();
    private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
    private final byte[] requestBodyBytes;
    private int responseCode = -1;

    public TestHttpExchange(String uri, String method) {
        this(uri, method, null);
    }

    public TestHttpExchange(String uri, String method, String body) {
        this.requestUri = URI.create(uri);
        this.method = method;
        this.requestBodyBytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
    }

    public String getResponseBodyText() {
        return responseBody.toString(StandardCharsets.UTF_8);
    }

    public byte[] getResponseBodyBytes() {
        return responseBody.toByteArray();
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
        // No-op for test double.
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
        // No-op.
    }

    @Override
    public void setStreams(InputStream i, OutputStream o) {
        // No-op.
    }

    @Override
    public HttpPrincipal getPrincipal() {
        return null;
    }
}
