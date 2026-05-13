package com.uiptv.util;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerUtilsTest {

    @Test
    void queryBodyAndJsonHelpers_handleEncodingAndEmptyLists() throws Exception {
        StubExchange exchange = new StubExchange("/path?name=one%20two&empty=", "POST", "payload");

        assertEquals("one two", ServerUtils.getParam(exchange, "name"));
        assertEquals("", ServerUtils.getParam(exchange, "empty"));
        assertEquals("payload", ServerUtils.readRequestBodyText(exchange));
        assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), ServerUtils.readRequestBodyBytes(new StubExchange("/path", "POST", "payload")));
        assertEquals("[]", ServerUtils.objectToJson(List.of()));
        assertEquals("[{\"id\":\"1\"},{\"id\":\"2\"}]", ServerUtils.objectToJson(List.of(
                () -> "{\"id\":\"1\"}",
                () -> "{\"id\":\"2\"}"
        )));
    }

    @Test
    void responseHelpers_writeHeadersBodiesAndMethodRejections() throws Exception {
        StubExchange json = new StubExchange("/json", "GET", null);
        ServerUtils.writeJsonResponse(json, 201, "{\"ok\":true}");
        assertEquals(201, json.getResponseCode());
        assertTrue(json.getResponseHeaders().getFirst("Content-Type").contains("application/json"));
        assertEquals("{\"ok\":true}", json.getResponseBodyText());

        StubExchange text = new StubExchange("/text", "GET", null);
        ServerUtils.generateResponseText(text, 202, "accepted");
        assertEquals(202, text.getResponseCode());
        assertEquals("accepted", text.getResponseBodyText());

        StubExchange binary = new StubExchange("/bin", "GET", null);
        ServerUtils.writeBinaryResponse(binary, 206, new byte[]{1, 2, 3}, "application/octet-stream");
        assertEquals(206, binary.getResponseCode());
        assertArrayEquals(new byte[]{1, 2, 3}, binary.responseBody.toByteArray());

        StubExchange ts = new StubExchange("/video", "GET", null);
        ServerUtils.generateTs8Response(ts, "ts-data", "video.ts");
        assertEquals(200, ts.getResponseCode());
        assertEquals("attachment; filename=video.ts", ts.getResponseHeaders().getFirst("Content-Disposition"));

        StubExchange rejected = new StubExchange("/html", "POST", null);
        ServerUtils.generateHtmlResponse(rejected, "<html></html>");
        assertEquals(405, rejected.getResponseCode());
        assertEquals("GET", rejected.getResponseHeaders().getFirst("Allow"));
    }

    private static final class StubExchange extends HttpExchange {
        private final URI uri;
        private final String method;
        private final byte[] requestBody;
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private int responseCode = -1;

        private StubExchange(String uri, String method, String requestBody) {
            this.uri = URI.create(uri);
            this.method = method;
            this.requestBody = requestBody == null ? new byte[0] : requestBody.getBytes(StandardCharsets.UTF_8);
        }

        private String getResponseBodyText() {
            return responseBody.toString(StandardCharsets.UTF_8);
        }

        @Override public Headers getRequestHeaders() { return requestHeaders; }
        @Override public Headers getResponseHeaders() { return responseHeaders; }
        @Override public URI getRequestURI() { return uri; }
        @Override public String getRequestMethod() { return method; }
        @Override public HttpContext getHttpContext() { return null; }
        @Override public void close() { /* Test exchange has no external resource to close. */ }
        @Override public InputStream getRequestBody() { return new ByteArrayInputStream(requestBody); }
        @Override public OutputStream getResponseBody() { return responseBody; }
        @Override public void sendResponseHeaders(int rCode, long responseLength) { this.responseCode = rCode; }
        @Override public InetSocketAddress getRemoteAddress() { return null; }
        @Override public int getResponseCode() { return responseCode; }
        @Override public InetSocketAddress getLocalAddress() { return null; }
        @Override public String getProtocol() { return "HTTP/1.1"; }
        @Override public Object getAttribute(String name) { return null; }
        @Override public void setAttribute(String name, Object value) { /* Attributes are not needed by these tests. */ }
        @Override public void setStreams(InputStream i, OutputStream o) { /* Stream replacement is not needed by these tests. */ }
        @Override public HttpPrincipal getPrincipal() { return null; }
    }
}
