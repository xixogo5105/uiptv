package com.uiptv.shared.net;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpDataContractTest {
    @Test
    void requestNormalizesDefaultsAndDefensivelyCopiesMutableInputs() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);

        HttpRequestData request = new HttpRequestData("https://example.test", " post ", headers, body, true, 15);
        headers.put("Later", "ignored");
        body[0] = 'P';

        assertEquals("POST", request.method());
        assertEquals(Map.of("Accept", "application/json"), request.headers());
        assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), request.body());

        byte[] returnedBody = request.body();
        returnedBody[0] = 'P';
        assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), request.body());
        assertThrows(UnsupportedOperationException.class, () -> request.headers().put("X-Test", "value"));
    }

    @Test
    void requestUsesSafeDefaultsForOptionalValues() {
        HttpRequestData request = new HttpRequestData("https://example.test", " ", null, null, false, 0);

        assertEquals("GET", request.method());
        assertEquals(Map.of(), request.headers());
        assertArrayEquals(new byte[0], request.body());
    }

    @Test
    void responseNormalizesDefaultsAndDefensivelyCopiesMutableInputs() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        HttpResponseData response = new HttpResponseData(200, headers, body, null);
        headers.put("Later", "ignored");
        body[0] = '[';

        assertEquals(200, response.statusCode());
        assertEquals(Map.of("Content-Type", "application/json"), response.headers());
        assertEquals("", response.finalUrl());
        assertArrayEquals("{}".getBytes(StandardCharsets.UTF_8), response.body());

        byte[] returnedBody = response.body();
        returnedBody[0] = '[';
        assertArrayEquals("{}".getBytes(StandardCharsets.UTF_8), response.body());
        assertThrows(UnsupportedOperationException.class, () -> response.headers().put("X-Test", "value"));
    }
}
