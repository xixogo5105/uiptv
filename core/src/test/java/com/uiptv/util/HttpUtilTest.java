package com.uiptv.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpUtilTest {

    @Test
    void formatHttpLog_redactsSensitiveHeadersAndFormatsEmptyBinaryAndParams() {
        HttpUtil.HttpResult response = new HttpUtil.HttpResult(
                "post",
                "http://example.test/final",
                200,
                "abc\u0000def",
                Map.of("Authorization", List.of("secret"), "X-Test", List.of("visible")),
                Map.of("Set-Cookie", List.of("cookie"), "Content-Type", List.of("text/plain"))
        );

        String log = HttpUtil.formatHttpLog("http://example.test/original", response, Map.of("b", "2", "a", "1"));

        assertTrue(log.contains("HTTP post http://example.test/final"));
        assertTrue(log.contains("Status: 200"));
        assertTrue(log.contains("Authorization: <redacted>"));
        assertTrue(log.contains("Set-Cookie: <redacted>"));
        assertTrue(log.contains("a=\"1\""));
        assertTrue(log.contains("<binary"));
        assertEquals("HTTP request log unavailable: response was null", HttpUtil.formatHttpLog("url", null, Map.of()));
    }

    @Test
    void requestOptionsAndPrivateFormattingHelpers_coverFallbackBranches() throws Exception {
        HttpUtil.RequestOptions defaults = HttpUtil.RequestOptions.defaults();
        assertTrue(defaults.followRedirects());
        assertTrue(defaults.readBody());
        assertEquals(null, defaults.connectTimeoutSeconds());

        HttpUtil.RequestOptions custom = new HttpUtil.RequestOptions(false, false, 1, 2, 3);
        assertFalse(custom.followRedirects());
        assertFalse(custom.readBody());
        assertEquals(1, custom.connectTimeoutSeconds());
        assertEquals(2, custom.connectionRequestTimeoutSeconds());
        assertEquals(3, custom.responseTimeoutSeconds());

        assertEquals("GET", invoke("safeMethod", new Class[]{String.class}, " ").toString());
        assertEquals("POST", invoke("safeMethod", new Class[]{String.class}, " post ").toString());
        assertEquals(URI.create("http://localhost/empty-url-fallback"), invoke("toSafeUri", new Class[]{String.class}, ""));
        assertEquals("\"\"", invoke("quote", new Class[]{String.class}, (Object) null));
        assertEquals("fallback", invoke("nonBlank", new Class[]{String.class, String.class}, "", "fallback"));
    }

    private Object invoke(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = HttpUtil.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
}
