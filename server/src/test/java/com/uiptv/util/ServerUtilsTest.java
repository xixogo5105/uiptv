package com.uiptv.util;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.uiptv.api.JsonCompliant;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerUtilsTest {
    @Test
    void objectToJson_handlesNullEmptyAndMixedValues() {
        assertEquals("[]", ServerUtils.objectToJson(null));
        assertEquals("[]", ServerUtils.objectToJson(List.of()));
        assertEquals("[{\"id\":1},{\"id\":2}]", ServerUtils.objectToJson(Arrays.asList(
                () -> "{\"id\":1}",
                null,
                () -> "{\"id\":2}"
        )));
    }

    @Test
    void getParam_decodesQueryValues() {
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getRequestURI()).thenReturn(URI.create("http://localhost/test?name=John+Doe&mode=live%2Fvod"));

        assertEquals("John Doe", ServerUtils.getParam(exchange, "name"));
        assertEquals("live/vod", ServerUtils.getParam(exchange, "mode"));
        assertNull(ServerUtils.getParam(exchange, "missing"));
    }

    @Test
    void generateResponse_forGet_setsHeadersAndWritesBody() throws Exception {
        HttpExchange exchange = mock(HttpExchange.class);
        Headers headers = new Headers();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(exchange.getRequestMethod()).thenReturn("GET");
        when(exchange.getResponseHeaders()).thenReturn(headers);
        when(exchange.getResponseBody()).thenReturn(outputStream);

        ServerUtils.generateResponse(exchange, "payload", ServerUtils.CONTENT_TYPE_JSON, null);

        verify(exchange).sendResponseHeaders(200, 7L);
        assertEquals(List.of("*"), headers.get("Access-Control-Allow-Origin"));
        assertEquals(List.of(ServerUtils.CONTENT_TYPE_JSON), headers.get("Content-Type"));
        assertEquals("payload", outputStream.toString(StandardCharsets.UTF_8));
    }

    @Test
    void generateResponse_forNonGet_returnsMethodNotAllowed() throws Exception {
        HttpExchange exchange = mock(HttpExchange.class);
        Headers headers = new Headers();
        when(exchange.getRequestMethod()).thenReturn("POST");
        when(exchange.getResponseHeaders()).thenReturn(headers);

        ServerUtils.generateResponse(exchange, "ignored", ServerUtils.CONTENT_TYPE_JSON, null);

        verify(exchange).sendResponseHeaders(405, -1);
        assertEquals(List.of("GET"), headers.get("Allow"));
    }

    @Test
    void generateTs8Response_marksDownloadHeadersWhenFileNameProvided() throws Exception {
        HttpExchange exchange = mock(HttpExchange.class);
        Headers headers = new Headers();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(exchange.getRequestMethod()).thenReturn("GET");
        when(exchange.getResponseHeaders()).thenReturn(headers);
        when(exchange.getResponseBody()).thenReturn(outputStream);

        ServerUtils.generateTs8Response(exchange, "segment", "video.ts");

        assertEquals(List.of("attachment; filename=video.ts"), headers.get("Content-Disposition"));
        assertEquals(List.of("binary"), headers.get("Content-Transfer-Encoding"));
    }

    @Test
    void writeResponseHelpers_useExpectedStatusContentTypeAndBody() throws Exception {
        HttpExchange exchange = mock(HttpExchange.class);
        Headers headers = new Headers();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(exchange.getResponseHeaders()).thenReturn(headers);
        when(exchange.getResponseBody()).thenReturn(outputStream);

        ServerUtils.writeJsonResponse(exchange, 202, "{\"ok\":true}");

        verify(exchange).sendResponseHeaders(202, 11L);
        assertEquals(List.of("application/json; charset=UTF-8"), headers.get("Content-Type"));
        assertEquals("{\"ok\":true}", outputStream.toString(StandardCharsets.UTF_8));
    }

    @Test
    void generateResponseText_andWriteBinaryResponse_coverTextAndBytes() throws Exception {
        HttpExchange textExchange = mock(HttpExchange.class);
        Headers textHeaders = new Headers();
        ByteArrayOutputStream textOutput = new ByteArrayOutputStream();
        when(textExchange.getResponseHeaders()).thenReturn(textHeaders);
        when(textExchange.getResponseBody()).thenReturn(textOutput);

        ServerUtils.generateResponseText(textExchange, 201, "created");

        verify(textExchange).sendResponseHeaders(201, 7L);
        assertEquals("created", textOutput.toString(StandardCharsets.UTF_8));

        HttpExchange binaryExchange = mock(HttpExchange.class);
        Headers binaryHeaders = new Headers();
        ByteArrayOutputStream binaryOutput = new ByteArrayOutputStream();
        when(binaryExchange.getResponseHeaders()).thenReturn(binaryHeaders);
        when(binaryExchange.getResponseBody()).thenReturn(binaryOutput);

        byte[] bytes = new byte[]{1, 2, 3};
        ServerUtils.writeBinaryResponse(binaryExchange, 206, bytes, "application/octet-stream");

        verify(binaryExchange).sendResponseHeaders(206, 3L);
        assertEquals(List.of("application/octet-stream"), binaryHeaders.get("Content-Type"));
        assertArrayEquals(bytes, binaryOutput.toByteArray());
    }

    @Test
    void readRequestBody_helpers_returnBodyContent() throws Exception {
        HttpExchange exchange = mock(HttpExchange.class);
        byte[] body = "hello-body".getBytes(StandardCharsets.UTF_8);
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(body));

        assertEquals("hello-body", ServerUtils.readRequestBodyText(exchange));

        HttpExchange secondExchange = mock(HttpExchange.class);
        when(secondExchange.getRequestBody()).thenReturn(new ByteArrayInputStream(body));
        assertArrayEquals(body, ServerUtils.readRequestBodyBytes(secondExchange));
    }
}
