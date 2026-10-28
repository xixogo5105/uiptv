package com.uiptv.server;

import com.uiptv.util.HttpUtil;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.uiptv.util.ServerUrlUtil.getPublishedServerHost;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    void hlsUrisOnUnderscoreCdnHostsRemainProxied() throws Exception {
        HttpProxyStreamServer handler = new HttpProxyStreamServer();
        String source = "http://edge_test.example/path/mono.m3u8?token=abc";
        String rewritten = new String((byte[]) invoke(handler, "rewriteHlsPlaylist",
                new Class[]{byte[].class, String.class},
                "#EXTM3U\nsegment.ts\n".getBytes(StandardCharsets.UTF_8), source), StandardCharsets.UTF_8);
        assertTrue(rewritten.contains("/proxy-stream?src=" + URLEncoder.encode(
                "http://edge_test.example/path/segment.ts", StandardCharsets.UTF_8)));
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
        proxyServer.createContext("/proxy-stream", localUpstreamProxy());
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
    void preservesOriginalHostWhenProviderRedirectsToNumericAddress() throws Exception {
        AtomicReference<String> finalHost = new AtomicReference<>();
        upstreamServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstreamServer.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + upstreamServer.getAddress().getPort() + "/final");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        upstreamServer.createContext("/final", exchange -> {
            finalHost.set(exchange.getRequestHeaders().getFirst("Host"));
            byte[] body = "stream".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "video/mp4");
            exchange.getResponseHeaders().add("Content-Length", String.valueOf(body.length));
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        upstreamServer.start();

        proxyServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        proxyServer.createContext("/proxy-stream", new HttpProxyStreamServer(url -> {
            URI uri = URI.create(url);
            return "http".equalsIgnoreCase(uri.getScheme())
                    && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))
                    && uri.getPort() == upstreamServer.getAddress().getPort();
        }));
        proxyServer.start();

        String upstreamUrl = "http://localhost:" + upstreamServer.getAddress().getPort() + "/redirect";
        String proxyUrl = "http://127.0.0.1:" + proxyServer.getAddress().getPort() + "/proxy-stream?src="
                + URLEncoder.encode(upstreamUrl, StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) URI.create(proxyUrl).toURL().openConnection();

        assertEquals(200, connection.getResponseCode());
        assertEquals("localhost:" + upstreamServer.getAddress().getPort(), finalHost.get());
    }

    @Test
    void redirectDoesNotForwardCookiesToAnotherAuthority() throws Exception {
        AtomicReference<String> receivedCookie = new AtomicReference<>();
        upstreamServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstreamServer.createContext("/start", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "portal_session=secret; Path=/");
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + upstreamServer.getAddress().getPort() + "/final");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        upstreamServer.createContext("/final", exchange -> {
            receivedCookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
            byte[] body = "stream".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "video/mp4");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) { output.write(body); }
        });
        upstreamServer.start();
        proxyServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int upstreamPort = upstreamServer.getAddress().getPort();
        proxyServer.createContext("/proxy-stream", new HttpProxyStreamServer(url -> {
            URI uri = URI.create(url);
            return "http".equalsIgnoreCase(uri.getScheme())
                    && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))
                    && uri.getPort() == upstreamPort;
        }));
        proxyServer.start();

        String startUrl = "http://localhost:" + upstreamPort + "/start";
        String proxyUrl = "http://127.0.0.1:" + proxyServer.getAddress().getPort() + "/proxy-stream?src="
                + URLEncoder.encode(startUrl, StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) URI.create(proxyUrl).toURL().openConnection();
        assertEquals(200, connection.getResponseCode());
        connection.getInputStream().readAllBytes();
        assertNull(receivedCookie.get());
    }

    @Test
    void optionsRequestsReturnCorsContractWithoutContactingUpstream() throws Exception {
        proxyServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        proxyServer.createContext("/proxy-stream", new HttpProxyStreamServer(url -> {
            throw new AssertionError("OPTIONS must not contact the upstream");
        }));
        proxyServer.start();

        String proxyUrl = "http://127.0.0.1:" + proxyServer.getAddress().getPort() + "/proxy-stream";
        HttpURLConnection connection = (HttpURLConnection) URI.create(proxyUrl).toURL().openConnection();
        connection.setRequestMethod("OPTIONS");

        assertEquals(204, connection.getResponseCode());
        assertEquals("*", connection.getHeaderField("Access-Control-Allow-Origin"));
        assertTrue(connection.getHeaderField("Access-Control-Allow-Methods").contains("GET"));
    }

    @Test
    void liveProxyUsesAnUpstreamRequestThatAllowsIdleBodyPeriods() throws Exception {
        HttpUtil.StreamResult upstream = mock(HttpUtil.StreamResult.class);
        whenStatusOk(upstream);
        AtomicReference<HttpUtil.RequestOptions> options = new AtomicReference<>();
        HttpProxyStreamServer handler = new HttpProxyStreamServer(url -> true);

        try (MockedStatic<HttpUtil> httpUtil = mockStatic(HttpUtil.class)) {
            httpUtil.when(() -> HttpUtil.openStreamPinned(
                            anyString(), anyMap(), eq("GET"), isNull(), any(HttpUtil.RequestOptions.class), any(InetAddress[].class)))
                    .thenAnswer(invocation -> {
                        options.set(invocation.getArgument(4));
                        return upstream;
                    });

            Class<?> cookieJarClass = Class.forName("com.uiptv.server.HttpProxyStreamServer$CookieJar");
            var cookieJarConstructor = cookieJarClass.getDeclaredConstructor();
            cookieJarConstructor.setAccessible(true);
            Object cookieJar = cookieJarConstructor.newInstance();
            HttpUtil.StreamResult result = invoke(handler, "openResolvedStream",
                    new Class[]{String.class, cookieJarClass, Map.class, String.class},
                    "http://localhost/live/channel", cookieJar, Map.of(), "GET");

            assertSame(upstream, result);
            assertNotNull(options.get());
            assertTrue(options.get().responseTimeoutDisabled());
        }
    }

    @Test
    void oversizedHlsPlaylistIsRejectedBeforeItCanConsumeUnboundedMemory() throws Exception {
        upstreamServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstreamServer.createContext("/hls/large.m3u8", exchange -> {
            byte[] body = ("#EXTM3U\n" + "a".repeat(4 * 1024 * 1024)).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/vnd.apple.mpegurl");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        upstreamServer.start();

        proxyServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        proxyServer.createContext("/proxy-stream", localUpstreamProxy());
        proxyServer.start();

        String upstreamUrl = "http://127.0.0.1:" + upstreamServer.getAddress().getPort() + "/hls/large.m3u8";
        String proxyUrl = "http://127.0.0.1:" + proxyServer.getAddress().getPort() + "/proxy-stream?src="
                + URLEncoder.encode(upstreamUrl, StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) URI.create(proxyUrl).toURL().openConnection();

        assertEquals(413, connection.getResponseCode());
    }

    @Test
    void rewritesLocalBrowserOriginAndRefererForPortalPlaybackUrls() throws Exception {
        AtomicReference<String> upstreamOrigin = new AtomicReference<>();
        AtomicReference<String> upstreamReferer = new AtomicReference<>();
        AtomicReference<String> upstreamUserAgent = new AtomicReference<>();
        AtomicReference<String> upstreamXUserAgent = new AtomicReference<>();
        AtomicReference<String> upstreamCookie = new AtomicReference<>();

        upstreamServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstreamServer.createContext("/play/live.php", exchange -> {
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
        proxyServer.createContext("/proxy-stream", localUpstreamProxy());
        proxyServer.start();

        String upstreamUrl = "http://127.0.0.1:" + upstreamServer.getAddress().getPort()
                + "/play/live.php?mac=00%3A1A%3A79%3AA1%3A32%3AEB&stream=30583&extension=ts&play_token=pt30583";
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
    void rewritesHlsPlaylistUrisThroughProxy() throws Exception {
        AtomicReference<String> upstreamCacheControl = new AtomicReference<>();
        AtomicReference<String> upstreamPragma = new AtomicReference<>();
        upstreamServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstreamServer.createContext("/hls/master.m3u8", exchange -> {
            upstreamCacheControl.set(exchange.getRequestHeaders().getFirst("Cache-Control"));
            upstreamPragma.set(exchange.getRequestHeaders().getFirst("Pragma"));
            byte[] body = """
                    #EXTM3U
                    #EXT-X-STREAM-INF:BANDWIDTH=1000
                    tracks/mono.m3u8?token=abc
                    #EXT-X-KEY:METHOD=AES-128,URI="keys/key.bin"
                    #EXTINF:4,
                    segments/one.ts?token=abc
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/vnd.apple.mpegurl");
            exchange.getResponseHeaders().add("Content-Length", String.valueOf(body.length));
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        upstreamServer.start();

        proxyServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        proxyServer.createContext("/proxy-stream", localUpstreamProxy());
        proxyServer.start();

        String upstreamBase = "http://127.0.0.1:" + upstreamServer.getAddress().getPort();
        String upstreamUrl = upstreamBase + "/hls/master.m3u8";
        String proxyUrl = "http://127.0.0.1:" + proxyServer.getAddress().getPort() + "/proxy-stream?src="
                + URLEncoder.encode(upstreamUrl, StandardCharsets.UTF_8);

        HttpURLConnection connection = (HttpURLConnection) URI.create(proxyUrl).toURL().openConnection();
        assertEquals(200, connection.getResponseCode());
        String body = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertEquals("no-cache", upstreamCacheControl.get());
        assertEquals("no-cache", upstreamPragma.get());
        assertTrue(body.contains(PROXY_PREFIX + URLEncoder.encode(upstreamBase + "/hls/tracks/mono.m3u8?token=abc", StandardCharsets.UTF_8)));
        assertTrue(body.contains("URI=\"" + PROXY_PREFIX + URLEncoder.encode(upstreamBase + "/hls/keys/key.bin", StandardCharsets.UTF_8) + "\""));
        assertTrue(body.contains(PROXY_PREFIX + URLEncoder.encode(upstreamBase + "/hls/segments/one.ts?token=abc", StandardCharsets.UTF_8)));
        assertFalse(body.contains("\ntracks/mono.m3u8"));
    }

    @Test
    void privateHelpers_coverHeaderCookiesAndForwardedHeaders() throws Exception {
        HttpProxyStreamServer handler = new HttpProxyStreamServer();

        Map<String, List<String>> responseHeaders = new LinkedHashMap<>();
        responseHeaders.put("set-cookie", List.of("session=one; Path=/", "mac=00:11; HttpOnly", " "));
        Class<?> cookieJarClass = Class.forName("com.uiptv.server.HttpProxyStreamServer$CookieJar");
        var cookieJarConstructor = cookieJarClass.getDeclaredConstructor();
        cookieJarConstructor.setAccessible(true);
        Object cookies = cookieJarConstructor.newInstance();
        invoke(cookies, "scopeTo", new Class[]{String.class}, "http://portal.test");
        invoke(handler, "collectCookies", new Class[]{Map.class, cookieJarClass}, responseHeaders, cookies);
        var valuesField = cookieJarClass.getDeclaredField("values");
        valuesField.setAccessible(true);
        assertEquals(List.of("session=one", "mac=00:11"), valuesField.get(cookies));
        invoke(cookies, "scopeTo", new Class[]{String.class}, "http://other.test");
        assertTrue(((List<?>) valuesField.get(cookies)).isEmpty());
        ((List<String>) valuesField.get(cookies)).add("secure=one");
        invoke(cookies, "scopeTo", new Class[]{String.class}, "https://other.test");
        assertTrue(((List<?>) valuesField.get(cookies)).isEmpty());

        assertEquals("session=one; Path=/", invoke(handler, "firstHeader", new Class[]{Map.class, String.class}, responseHeaders, "Set-Cookie"));
        assertEquals("", invoke(handler, "firstHeader", new Class[]{Map.class, String.class}, null, "Set-Cookie"));

        Map<String, String> forwarded = new LinkedHashMap<>();
        forwarded.put("Accept", "video/*");
        forwarded.put("Range", "bytes=0-10");
        forwarded.put("Referer", "http://localhost:8080/player");
        forwarded.put("Origin", "http://localhost:8080");
        @SuppressWarnings("unchecked")
        Map<String, String> upstreamHeaders = invoke(handler, "buildUpstreamHeaders",
                new Class[]{String.class, cookieJarClass, Map.class},
                "http://portal.test/live/play/123?mac=AA%3ABB&play_token=pt", cookies, forwarded);
        assertEquals("video/*", upstreamHeaders.get("Accept"));
        assertEquals("bytes=0-10", upstreamHeaders.get("Range"));
        assertEquals("http://portal.test", upstreamHeaders.get("Origin"));
        assertEquals("http://portal.test/", upstreamHeaders.get("Referer"));
        assertTrue(upstreamHeaders.get("Cookie").contains("mac=AA:BB"));
    }

    @Test
    void privateHelpers_coverOriginsQueryAndContentLength() throws Exception {
        HttpProxyStreamServer handler = new HttpProxyStreamServer();

        assertEquals("http://example.test:8081", invoke(handler, "originOf", new Class[]{String.class}, "http://example.test:8081/a/b"));
        assertEquals("https://example.test", invoke(handler, "originOf", new Class[]{String.class}, "https://example.test/a/b"));
        assertEquals("", invoke(handler, "originOf", new Class[]{String.class}, "not a uri"));
        assertTrue((Boolean) invoke(handler, "sameOrigin", new Class[]{String.class, String.class}, "http://a.test/x", "http://a.test/y"));
        assertFalse((Boolean) invoke(handler, "sameOrigin", new Class[]{String.class, String.class}, "http://a.test/x", "http://b.test/y"));
        assertTrue((Boolean) invoke(handler, "isLocalOrigin", new Class[]{String.class}, "http://" + getPublishedServerHost() + ":8080/player"));
        assertTrue((Boolean) invoke(handler, "isLocalOrigin", new Class[]{String.class}, "http://localhost:8080/player"));
        assertFalse((Boolean) invoke(handler, "isLocalOrigin", new Class[]{String.class}, "http://0.0.0.0:8080/player"));
        assertFalse((Boolean) invoke(handler, "isLocalOrigin", new Class[]{String.class}, "http://portal.test/player"));
        assertFalse((Boolean) invoke(handler, "isAllowedTargetUrl", new Class[]{String.class}, "http://127.0.0.1:8080/private"));
        assertEquals("exa_edge_2skyd.cdn77.me", invoke(handler, "hostOf", new Class[]{URI.class},
                URI.create("http://exa_edge_2skyd.cdn77.me/live/mono.m3u8")));
        assertTrue((Boolean) invoke(handler, "isBlockedAddress", new Class[]{InetAddress.class}, InetAddress.getByName("100.64.0.1")));
        assertTrue((Boolean) invoke(handler, "isBlockedAddress", new Class[]{InetAddress.class}, InetAddress.getByName("fc00::1")));

        assertEquals("A B", invoke(handler, "queryParam", new Class[]{String.class, String.class}, "http://x.test/path?mac=A+B&empty", "mac"));
        assertEquals("", invoke(handler, "queryParam", new Class[]{String.class, String.class}, "http://x.test/path", "mac"));
        assertEquals(Long.valueOf(123L), invoke(handler, "resolveContentLength", new Class[]{String.class}, "123"));
        assertEquals(Long.valueOf(0L), invoke(handler, "resolveContentLength", new Class[]{String.class}, "bad"));
    }

    @Test
    void privateHelpers_coverFallbackAndNumericPathHelpers() throws Exception {
        HttpProxyStreamServer handler = new HttpProxyStreamServer();

        assertTrue((Boolean) invoke(handler, "isPortalPlaybackPath", new Class[]{String.class}, "http://host/play/live.php?stream=1"));
        assertTrue((Boolean) invoke(handler, "isPortalPlaybackPath", new Class[]{String.class}, "http://host/play/movie.php?stream=1"));
        assertTrue((Boolean) invoke(handler, "isPortalPlaybackPath", new Class[]{String.class}, "http://host/live/play/1"));
        assertFalse((Boolean) invoke(handler, "isPortalPlaybackPath", new Class[]{String.class}, "http://host/static/file.ts"));
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

    @Test
    void handleRejectsUnsupportedMethodsAndInvalidSources() throws Exception {
        HttpProxyStreamServer handler = new HttpProxyStreamServer(url -> true);

        TestHttpExchange unsupported = new TestHttpExchange("/proxy-stream?src=http://provider.test/live", "POST");
        handler.handle(unsupported);
        assertEquals(405, unsupported.getResponseCode());
        assertEquals("GET, HEAD, OPTIONS", unsupported.getResponseHeaders().getFirst("Allow"));

        TestHttpExchange missingSource = new TestHttpExchange("/proxy-stream", "GET");
        handler.handle(missingSource);
        assertEquals(400, missingSource.getResponseCode());

        TestHttpExchange rejected = new TestHttpExchange("/proxy-stream?src=http://provider.test/live", "GET");
        HttpProxyStreamServer rejecting = new HttpProxyStreamServer(_ -> false);
        rejecting.handle(rejected);
        assertEquals(400, rejected.getResponseCode());
    }

    @Test
    void validationAndHlsHelpersCoverSafeAndFallbackBranches() throws Exception {
        HttpProxyStreamServer handler = new HttpProxyStreamServer(_ -> true);

        assertFalse((Boolean) invoke(handler, "isAllowedTargetUrl", new Class[]{String.class}, ""));
        assertFalse((Boolean) invoke(handler, "isAllowedTargetUrl", new Class[]{String.class}, "ftp://provider.test/live"));
        assertFalse((Boolean) invoke(handler, "isAllowedTargetUrl", new Class[]{String.class}, "http://127.0.0.1/live"));
        assertFalse((Boolean) invoke(handler, "isAllowedTargetUrl", new Class[]{String.class}, "http://[broken"));
        assertEquals("example.test", invoke(handler, "hostOf", new Class[]{URI.class},
                URI.create("http://user:pass@example.test:8080/live")));
        assertEquals("[2001:db8::1]", invoke(handler, "hostOf", new Class[]{URI.class},
                URI.create("http://[2001:db8::1]:8080/live")));
        assertFalse((Boolean) invoke(handler, "isNumericHost", new Class[]{String.class}, "256.1.1.1"));
        assertTrue((Boolean) invoke(handler, "isNumericHost", new Class[]{String.class}, "2001:db8::1"));
        assertFalse((Boolean) invoke(handler, "isBlockedAddress", new Class[]{InetAddress.class},
                InetAddress.getByName("8.8.8.8")));
        assertTrue((Boolean) invoke(handler, "isBlockedAddress", new Class[]{InetAddress.class},
                InetAddress.getByName("192.0.0.1")));
        assertTrue((Boolean) invoke(handler, "isBlockedAddress", new Class[]{InetAddress.class},
                InetAddress.getByName("198.18.0.1")));

        assertEquals("plain", new String((byte[]) invoke(handler, "rewriteHlsPlaylist",
                new Class[]{byte[].class, String.class}, "plain".getBytes(StandardCharsets.UTF_8),
                "http://provider.test/live"), StandardCharsets.UTF_8));
        String playlist = """
                #EXTM3U
                #EXT-X-KEY:URI="data:text/plain,abc"
                file:///tmp/segment.ts
                blob:segment
                /proxy-stream?src=already
                invalid uri
                """;
        String rewritten = new String((byte[]) invoke(handler, "rewriteHlsPlaylist",
                new Class[]{byte[].class, String.class}, playlist.getBytes(StandardCharsets.UTF_8),
                "http://provider.test/live/master.m3u8"), StandardCharsets.UTF_8);
        assertTrue(rewritten.contains("data:text/plain,abc"));
        assertTrue(rewritten.contains("file:///tmp/segment.ts"));
        assertTrue(rewritten.contains("blob:segment"));
        assertTrue(rewritten.contains("invalid uri"));
    }

    @Test
    void redirectAndFallbackHelpersValidateTargetsAndContentTypes() throws Exception {
        HttpProxyStreamServer handler = new HttpProxyStreamServer(_ -> true);
        HttpUtil.StreamResult redirect = mock(HttpUtil.StreamResult.class);
        when(redirect.responseHeaders()).thenReturn(Map.of("Location", List.of("/final")));
        assertEquals("https://provider.test/final", invoke(handler, "resolveRedirect",
                new Class[]{String.class, HttpUtil.StreamResult.class}, "https://provider.test/start", redirect));

        HttpUtil.StreamResult insecureRedirect = mock(HttpUtil.StreamResult.class);
        when(insecureRedirect.responseHeaders()).thenReturn(Map.of("Location", List.of("http://provider.test/live/123?token=secret")));
        assertNull(invoke(handler, "resolveRedirect",
                new Class[]{String.class, HttpUtil.StreamResult.class}, "https://provider.test/start", insecureRedirect));

        HttpUtil.StreamResult missingLocation = mock(HttpUtil.StreamResult.class);
        when(missingLocation.responseHeaders()).thenReturn(Map.of());
        assertNull(invoke(handler, "resolveRedirect",
                new Class[]{String.class, HttpUtil.StreamResult.class}, "http://provider.test/start", missingLocation));

        HttpUtil.StreamResult notAcceptable = mock(HttpUtil.StreamResult.class);
        assertEquals("http://provider.test/user/pass/123.ts", invoke(handler, "resolve406Fallback",
                new Class[]{String.class, HttpUtil.StreamResult.class}, "http://provider.test/user/pass/123", notAcceptable));

        HttpUtil.StreamResult hls = mock(HttpUtil.StreamResult.class);
        when(hls.statusCode()).thenReturn(200);
        when(hls.responseHeaders()).thenReturn(Map.of("Content-Type", List.of("video/mp4")));
        when(hls.requestUri()).thenReturn("http://provider.test/live/index.m3u8");
        assertTrue((Boolean) invoke(handler, "shouldRewriteHlsPlaylist",
                new Class[]{HttpUtil.StreamResult.class}, hls));
        when(hls.statusCode()).thenReturn(404);
        assertFalse((Boolean) invoke(handler, "shouldRewriteHlsPlaylist",
                new Class[]{HttpUtil.StreamResult.class}, hls));
    }

    @SuppressWarnings("unchecked")
    private <T> T invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return (T) method.invoke(target, args);
    }

    private static final String PROXY_PREFIX = "/proxy-stream?src=";

    private HttpProxyStreamServer localUpstreamProxy() {
        int upstreamPort = upstreamServer.getAddress().getPort();
        return new HttpProxyStreamServer(url -> {
            URI uri = URI.create(url);
            return "http".equalsIgnoreCase(uri.getScheme())
                    && "127.0.0.1".equals(uri.getHost())
                    && uri.getPort() == upstreamPort;
        });
    }

    private static void whenStatusOk(HttpUtil.StreamResult upstream) {
        when(upstream.statusCode()).thenReturn(200);
        when(upstream.responseHeaders()).thenReturn(Map.of());
    }
}
