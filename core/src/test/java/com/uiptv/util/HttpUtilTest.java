package com.uiptv.util;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpUtilTest {

    @Test
    void formatHttpLog_redactsSensitiveHeadersAndFormatsEmptyBinaryAndParams() {
        HttpUtil.HttpResult response = new HttpUtil.HttpResult(
                "post",
                "http://example.test/final?username=alice&token=secret&visible=yes",
                200,
                "abc\u0000def",
                Map.of("Authorization", List.of("secret"), "X-Test", List.of("visible")),
                Map.of("Set-Cookie", List.of("cookie"), "Content-Type", List.of("text/plain"))
        );

        String log = HttpUtil.formatHttpLog("http://example.test/original", response, Map.of("b", "2", "password", "secret", "a", "1"));

        assertTrue(log.contains("HTTP post http://example.test/final?username=<redacted>&token=<redacted>&visible=yes"));
        assertTrue(log.contains("Status: 200"));
        assertTrue(log.contains("Authorization: <redacted>"));
        assertTrue(log.contains("Set-Cookie: <redacted>"));
        assertTrue(log.contains("a=\"1\""));
        assertTrue(log.contains("password=\"<redacted>\""));
        assertFalse(log.contains("alice"));
        assertFalse(log.contains("secret"));
        assertTrue(log.contains("<binary"));
        assertEquals("HTTP request log unavailable: response was null", HttpUtil.formatHttpLog("url", null, Map.of()));
    }

    @Test
    void sanitizeUrlForLog_redactsCredentialsAndSensitiveQueryValues() {
        String sanitized = HttpUtil.sanitizeUrlForLog(
                "https://alice:password@example.test/live?mac=00:11&play-token=abc&stream=7");

        assertEquals("https://<redacted>@example.test/live?mac=<redacted>&play-token=<redacted>&stream=7", sanitized);
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
        assertFalse(custom.responseTimeoutDisabled());
        assertEquals(0, custom.maxBodyChars());
        assertEquals(1024, custom.withMaxBodyChars(1024).maxBodyChars());
        assertFalse(custom.withMaxBodyChars(1024).responseTimeoutDisabled());

        RequestConfig requestConfig = (RequestConfig) invoke(
                "buildRequestConfig", new Class[]{HttpUtil.RequestOptions.class}, custom);
        assertFalse(requestConfig.isRedirectsEnabled());
        assertEquals(1, requestConfig.getConnectTimeout().toSeconds());
        assertEquals(2, requestConfig.getConnectionRequestTimeout().toSeconds());
        assertEquals(3, requestConfig.getResponseTimeout().toSeconds());

        HttpUtil.RequestOptions streamingProxy = HttpUtil.RequestOptions.streamingProxy();
        assertFalse(streamingProxy.followRedirects());
        assertTrue(streamingProxy.readBody());
        assertTrue(streamingProxy.responseTimeoutDisabled());
        RequestConfig streamingProxyConfig = (RequestConfig) invoke(
                "buildRequestConfig", new Class[]{HttpUtil.RequestOptions.class}, streamingProxy);
        assertTrue(streamingProxyConfig.getResponseTimeout().isDisabled());

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

    @Test
    void requestAndResponseHelpers_coverBodiesHeadersAndFallbackUris() throws Exception {
        HttpUriRequestBase withoutContentType = (HttpUriRequestBase) invoke(
                "buildRequest",
                new Class[]{String.class, Map.class, String.class, String.class, HttpUtil.RequestOptions.class},
                "http://example.test/path", Map.of("X-Test", "value"), "post", "a=1", HttpUtil.RequestOptions.defaults());
        assertEquals("POST", withoutContentType.getMethod());
        assertEquals("application/x-www-form-urlencoded", withoutContentType.getFirstHeader("Content-Type").getValue());
        assertEquals("value", withoutContentType.getFirstHeader("X-Test").getValue());

        HttpUriRequestBase withContentType = (HttpUriRequestBase) invoke(
                "buildRequest",
                new Class[]{String.class, Map.class, String.class, String.class, HttpUtil.RequestOptions.class},
                "http://example.test/path", Map.of("content-type", "application/json"), null, "{}", null);
        assertEquals("GET", withContentType.getMethod());
        assertEquals("application/json", withContentType.getFirstHeader("Content-Type").getValue());

        assertEquals("http://example.test/path?a=b%7Cc", invoke("toSafeUri", new Class[]{String.class}, "http://example.test/path?a=b|c").toString());
        assertEquals("http://example.test/path", invoke("toSafeUri", new Class[]{String.class}, " http://example.test/path ").toString());
        assertEquals("GET", invoke("safeMethod", new Class[]{String.class}, (Object) null));
        assertEquals(9, invoke("resolveTimeoutSeconds", new Class[]{Integer.class, int.class}, 9, 2));
        assertEquals(2, invoke("resolveTimeoutSeconds", new Class[]{Integer.class, int.class}, 0, 2));

        HttpEntity entity = new StringEntity("small body");
        assertEquals("small body", invoke("readBoundedResponseBody", new Class[]{HttpEntity.class, int.class}, entity, 100));
        InvocationTargetException limitFailure = assertThrows(InvocationTargetException.class,
                () -> invoke("readBoundedResponseBody", new Class[]{HttpEntity.class, int.class}, new StringEntity("too long"), 3));
        assertTrue(limitFailure.getCause() instanceof ReloadRequestScope.LimitExceeded);

        org.apache.hc.core5.http.ClassicHttpResponse response = Mockito.mock(org.apache.hc.core5.http.ClassicHttpResponse.class);
        Mockito.when(response.getEntity()).thenReturn(null);
        assertEquals("", invoke("readResponseBody", new Class[]{org.apache.hc.core5.http.ClassicHttpResponse.class, HttpUtil.RequestOptions.class}, response, HttpUtil.RequestOptions.defaults()));
        assertEquals("", invoke("readResponseBody", new Class[]{org.apache.hc.core5.http.ClassicHttpResponse.class, HttpUtil.RequestOptions.class}, response, new HttpUtil.RequestOptions(true, false)));

        @SuppressWarnings("unchecked")
        Map<String, List<String>> headers = (Map<String, List<String>>) invoke(
                "headersToMap", new Class[]{Header[].class}, (Object) new Header[]{
                        new org.apache.hc.core5.http.message.BasicHeader("X-Test", "one"),
                        new org.apache.hc.core5.http.message.BasicHeader("X-Test", "two")
                });
        assertEquals(List.of("one", "two"), headers.get("X-Test"));
    }

    @Test
    void urlAndBodyFormatting_handlesMalformedInputsAndEmptyValues() throws Exception {
        assertEquals("<unparseable-url>", HttpUtil.sanitizeUrlForLog("http://[broken"));
        assertEquals("<empty>", invoke("abbreviateBody", new Class[]{String.class}, " "));
        assertEquals("<empty>", invoke("abbreviateBody", new Class[]{String.class}, (Object) null));
        assertEquals("\"\"", invoke("quote", new Class[]{String.class}, (Object) null));
        assertEquals("fallback", invoke("nonBlank", new Class[]{String.class, String.class}, (Object) null, "fallback"));
        assertFalse((Boolean) invoke("isSensitiveHeader", new Class[]{String.class}, (Object) null));
        assertTrue((Boolean) invoke("isSensitiveHeader", new Class[]{String.class}, "COOKIE"));
        assertFalse((Boolean) invoke("isSensitiveQueryKey", new Class[]{String.class}, (Object) null));
        assertTrue((Boolean) invoke("isSensitiveQueryKey", new Class[]{String.class}, "play-token"));
    }

    private Object invoke(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = HttpUtil.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
}
