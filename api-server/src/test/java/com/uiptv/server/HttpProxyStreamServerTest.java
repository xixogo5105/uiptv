package com.uiptv.server;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpProxyStreamServerTest {
    private HttpServer upstreamServer;
    private HttpServer proxyServer;

    @AfterEach
    void tearDown() {
        if (proxyServer != null) {
            proxyServer.stop(0);
        }
        if (upstreamServer != null) {
            upstreamServer.stop(0);
        }
    }

    @Test
    void headRequestsRemainHeadUpstream() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<String> upstreamMethod = new AtomicReference<>();

        upstreamServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstreamServer.createContext("/stream.ts", exchange -> {
            requestCount.incrementAndGet();
            upstreamMethod.set(exchange.getRequestMethod());
            exchange.getResponseHeaders().add("Content-Type", "video/mp2t");
            exchange.getResponseHeaders().add("Content-Length", "4");
            exchange.sendResponseHeaders(200, 4);
            if (!"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write("test".getBytes(StandardCharsets.UTF_8));
                }
            } else {
                exchange.close();
            }
        });
        upstreamServer.start();

        proxyServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        proxyServer.createContext("/proxy-stream", new HttpProxyStreamServer());
        proxyServer.start();

        String upstreamUrl = "http://127.0.0.1:" + upstreamServer.getAddress().getPort() + "/stream.ts";
        String proxyUrl = "http://127.0.0.1:" + proxyServer.getAddress().getPort() + "/proxy-stream?src="
                + URLEncoder.encode(upstreamUrl, StandardCharsets.UTF_8);

        HttpURLConnection connection = (HttpURLConnection) URI.create(proxyUrl).toURL().openConnection();
        connection.setRequestMethod("HEAD");
        assertEquals(200, connection.getResponseCode());
        assertEquals("HEAD", upstreamMethod.get());
        assertEquals(1, requestCount.get());
    }

    @Test
    void rewritesLocalBrowserOriginAndRefererForPortalPlaybackUrls() throws Exception {
        AtomicReference<String> upstreamOrigin = new AtomicReference<>();
        AtomicReference<String> upstreamReferer = new AtomicReference<>();
        AtomicReference<String> upstreamUserAgent = new AtomicReference<>();
        AtomicReference<String> upstreamXUserAgent = new AtomicReference<>();
        AtomicReference<String> upstreamCookie = new AtomicReference<>();

        upstreamServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstreamServer.createContext("/play/movie.php", exchange -> {
            upstreamOrigin.set(exchange.getRequestHeaders().getFirst("Origin"));
            upstreamReferer.set(exchange.getRequestHeaders().getFirst("Referer"));
            upstreamUserAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            upstreamXUserAgent.set(exchange.getRequestHeaders().getFirst("X-User-Agent"));
            upstreamCookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
            if (String.valueOf(upstreamOrigin.get()).contains(":8080")
                    || String.valueOf(upstreamReferer.get()).contains(":8080")) {
                exchange.sendResponseHeaders(462, -1);
                exchange.close();
                return;
            }
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "video/mp4");
            exchange.getResponseHeaders().add("Content-Length", String.valueOf(body.length));
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        upstreamServer.start();

        proxyServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        proxyServer.createContext("/proxy-stream", new HttpProxyStreamServer());
        proxyServer.start();

        String upstreamUrl = "http://127.0.0.1:" + upstreamServer.getAddress().getPort()
                + "/play/movie.php?mac=00%3A1A%3A79%3AA1%3A32%3AEB&stream=246761.mkv&play_token=pt246761&type=movie";
        String proxyUrl = "http://127.0.0.1:" + proxyServer.getAddress().getPort() + "/proxy-stream?src="
                + URLEncoder.encode(upstreamUrl, StandardCharsets.UTF_8);

        HttpURLConnection connection = (HttpURLConnection) URI.create(proxyUrl).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Origin", "http://127.0.0.1:8080");
        connection.setRequestProperty("Referer", "http://127.0.0.1:8080/player");

        assertEquals(200, connection.getResponseCode());
        assertEquals("http://127.0.0.1:" + upstreamServer.getAddress().getPort(), upstreamOrigin.get());
        assertTrue(upstreamReferer.get().startsWith("http://127.0.0.1:" + upstreamServer.getAddress().getPort() + "/"));
        assertFalse(upstreamReferer.get().contains(":8080"));
        assertTrue(String.valueOf(upstreamUserAgent.get()).contains("MAG200"));
        assertEquals("Model: MAG250; Link: WiFi", upstreamXUserAgent.get());
        assertTrue(String.valueOf(upstreamCookie.get()).contains("mac=00:1A:79:A1:32:EB"));
    }

    @Test
    void privateHelpers_coverHeaderCookiesOriginsFallbacksAndUrlParsing() throws Exception {
        HttpProxyStreamServer handler = new HttpProxyStreamServer();

        Map<String, List<String>> responseHeaders = new LinkedHashMap<>();
        responseHeaders.put("set-cookie", List.of("session=one; Path=/", "mac=00:11; HttpOnly", " "));
        List<String> cookies = new ArrayList<>(List.of("session=old"));
        invoke(handler, "collectCookies", new Class[]{Map.class, List.class}, responseHeaders, cookies);
        assertEquals(List.of("session=one", "mac=00:11"), cookies);

        assertEquals("session=one; Path=/", invoke(handler, "firstHeader", new Class[]{Map.class, String.class}, responseHeaders, "Set-Cookie"));
        assertEquals("", invoke(handler, "firstHeader", new Class[]{Map.class, String.class}, null, "Set-Cookie"));

        Map<String, String> forwarded = new LinkedHashMap<>();
        forwarded.put("Accept", "video/*");
        forwarded.put("Range", "bytes=0-10");
        forwarded.put("Referer", "http://localhost:8080/player");
        forwarded.put("Origin", "http://localhost:8080");
        @SuppressWarnings("unchecked")
        Map<String, String> upstreamHeaders = invoke(handler, "buildUpstreamHeaders",
                new Class[]{String.class, List.class, Map.class},
                "http://portal.test/live/play/123?mac=AA%3ABB&play_token=pt", cookies, forwarded);
        assertEquals("video/*", upstreamHeaders.get("Accept"));
        assertEquals("bytes=0-10", upstreamHeaders.get("Range"));
        assertEquals("http://portal.test", upstreamHeaders.get("Origin"));
        assertEquals("http://portal.test/", upstreamHeaders.get("Referer"));
        assertTrue(upstreamHeaders.get("Cookie").contains("mac=AA:BB"));

        assertEquals("http://example.test:8081", invoke(handler, "originOf", new Class[]{String.class}, "http://example.test:8081/a/b"));
        assertEquals("https://example.test", invoke(handler, "originOf", new Class[]{String.class}, "https://example.test/a/b"));
        assertEquals("", invoke(handler, "originOf", new Class[]{String.class}, "not a uri"));
        assertTrue((Boolean) invoke(handler, "sameOrigin", new Class[]{String.class, String.class}, "http://a.test/x", "http://a.test/y"));
        assertFalse((Boolean) invoke(handler, "sameOrigin", new Class[]{String.class, String.class}, "http://a.test/x", "http://b.test/y"));
        assertTrue((Boolean) invoke(handler, "isLocalOrigin", new Class[]{String.class}, "http://localhost:8080/player"));
        assertFalse((Boolean) invoke(handler, "isLocalOrigin", new Class[]{String.class}, "http://portal.test/player"));

        assertEquals("A B", invoke(handler, "queryParam", new Class[]{String.class, String.class}, "http://x.test/path?mac=A+B&empty", "mac"));
        assertEquals("", invoke(handler, "queryParam", new Class[]{String.class, String.class}, "http://x.test/path", "mac"));
        assertEquals(Long.valueOf(123L), invoke(handler, "resolveContentLength", new Class[]{String.class}, "123"));
        assertEquals(Long.valueOf(0L), invoke(handler, "resolveContentLength", new Class[]{String.class}, "bad"));

        assertEquals("http://host/live/play/123",
                invoke(handler, "downgradeHttpsToHttp", new Class[]{String.class}, "https://host/live/play/123"));
        assertEquals("https://host/static/file.mp4",
                invoke(handler, "downgradeHttpsToHttp", new Class[]{String.class}, "https://host/static/file.mp4"));
        assertEquals("http://host/user/pass/123.ts?token=a",
                invoke(handler, "build406Fallback", new Class[]{String.class}, "http://host/user/pass/123?token=a"));
        assertEquals("http://host/user/pass/123.ts",
                invoke(handler, "build406Fallback", new Class[]{String.class}, "http://host/user/pass/123.ts"));
        assertEquals("mkv", invoke(handler, "extensionOf", new Class[]{String.class}, "movie.mkv"));
        assertEquals("", invoke(handler, "extensionOf", new Class[]{String.class}, "movie"));
        assertTrue((Boolean) invoke(handler, "hasNumericLastPathSegment", new Class[]{String.class}, "http://host/a/123?x=1"));
        assertFalse((Boolean) invoke(handler, "hasNumericLastPathSegment", new Class[]{String.class}, "http://host/a/abc?x=1"));
        assertTrue((Boolean) invoke(handler, "isAsciiDigits", new Class[]{String.class}, "12345"));
        assertFalse((Boolean) invoke(handler, "isAsciiDigits", new Class[]{String.class}, "12a45"));
    }

    @SuppressWarnings("unchecked")
    private <T> T invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return (T) method.invoke(target, args);
    }
}
