package com.uiptv.util;

import org.apache.hc.client5.http.config.RequestConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;

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

        RequestConfig requestConfig = (RequestConfig) invoke(
                "buildRequestConfig", new Class[]{HttpUtil.RequestOptions.class}, custom);
        assertFalse(requestConfig.isRedirectsEnabled());
        assertEquals(1, requestConfig.getConnectTimeout().toSeconds());
        assertEquals(2, requestConfig.getConnectionRequestTimeout().toSeconds());
        assertEquals(3, requestConfig.getResponseTimeout().toSeconds());

        assertEquals("GET", invoke("safeMethod", new Class[]{String.class}, " ").toString());
        assertEquals("POST", invoke("safeMethod", new Class[]{String.class}, " post ").toString());
        assertEquals(URI.create("http://localhost/empty-url-fallback"), invoke("toSafeUri", new Class[]{String.class}, ""));
        assertEquals("\"\"", invoke("quote", new Class[]{String.class}, (Object) null));
        assertEquals("fallback", invoke("nonBlank", new Class[]{String.class, String.class}, "", "fallback"));
    }

    @Test
    void cancellableFuture_cancelsDelegateWhetherAssignedBeforeOrAfterCancellation() {
        AtomicReference<Future<?>> lateDelegateRef = new AtomicReference<>();
        HttpUtil.CancellableFuture<String> cancelledBeforeAssignment = new HttpUtil.CancellableFuture<>(lateDelegateRef);
        FutureTask<Void> lateDelegate = new FutureTask<>(() -> null);

        assertTrue(cancelledBeforeAssignment.cancel(true));
        cancelledBeforeAssignment.setDelegate(lateDelegate);
        assertTrue(lateDelegate.isCancelled());

        AtomicReference<Future<?>> earlyDelegateRef = new AtomicReference<>();
        FutureTask<Void> earlyDelegate = new FutureTask<>(() -> null);
        earlyDelegateRef.set(earlyDelegate);
        HttpUtil.CancellableFuture<String> cancelledAfterAssignment = new HttpUtil.CancellableFuture<>(earlyDelegateRef);

        assertTrue(cancelledAfterAssignment.cancel(true));
        assertTrue(earlyDelegate.isCancelled());
    }

    private Object invoke(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = HttpUtil.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
}
