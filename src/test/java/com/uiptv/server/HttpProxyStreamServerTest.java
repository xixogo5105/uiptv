package com.uiptv.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
}
