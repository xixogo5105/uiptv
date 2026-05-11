package com.uiptv.util;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HlsPlaylistResolverTest {
    @Test
    void resolveHlsPlaylistChain_returnsOriginalForNonManifestUrl() {
        String resolved = HlsPlaylistResolver.resolveHlsPlaylistChain(
                "http://example.com/video.mp4",
                Map.of(),
                3
        );

        assertEquals("http://example.com/video.mp4", resolved);
    }

    @Test
    void resolveHlsPlaylistChain_selectsHighestBandwidthVariant() throws Exception {
        withServer(server -> {
            server.createContext("/master.m3u8", exchange -> respond(exchange, """
                    #EXTM3U
                    #EXT-X-STREAM-INF:BANDWIDTH=500000
                    low.m3u8
                    #EXT-X-STREAM-INF:BANDWIDTH=2500000
                    hi.m3u8
                    """));
            server.createContext("/low.m3u8", exchange -> respond(exchange, "#EXTM3U\n#EXTINF:10,\nlow.ts"));
            server.createContext("/hi.m3u8", exchange -> respond(exchange, "#EXTM3U\n#EXTINF:10,\nhi.ts"));

            String resolved = HlsPlaylistResolver.resolveHlsPlaylistChain(
                    baseUrl(server) + "/master.m3u8",
                    Map.of(),
                    4
            );

            assertEquals(baseUrl(server) + "/hi.m3u8", resolved);
        });
    }

    @Test
    void resolveHlsPlaylistChain_recursesThroughNestedMasterPlaylists() throws Exception {
        withServer(server -> {
            server.createContext("/outer.m3u8", exchange -> respond(exchange, """
                    #EXTM3U
                    #EXT-X-STREAM-INF:BANDWIDTH=1000000
                    /inner/master.m3u8
                    """));
            server.createContext("/inner/master.m3u8", exchange -> respond(exchange, """
                    #EXTM3U
                    #EXT-X-STREAM-INF:BANDWIDTH=1000000
                    stream-final.m3u8
                    """));
            server.createContext("/inner/stream-final.m3u8", exchange -> respond(exchange, "#EXTM3U\n#EXTINF:10,\nseg.ts"));

            String resolved = HlsPlaylistResolver.resolveHlsPlaylistChain(
                    baseUrl(server) + "/outer.m3u8",
                    Map.of(),
                    4
            );

            assertEquals(baseUrl(server) + "/inner/stream-final.m3u8", resolved);
        });
    }

    @Test
    void resolveHlsPlaylistChain_preservesBaseQueryWhenVariantHasNoQuery() throws Exception {
        withServer(server -> {
            server.createContext("/master-query.m3u8", exchange -> respond(exchange, """
                    #EXTM3U
                    #EXT-X-STREAM-INF:BANDWIDTH=2000000
                    variant/index.m3u8
                    """));
            server.createContext("/variant/index.m3u8", exchange -> respond(exchange, "#EXTM3U\n#EXTINF:10,\nseg.ts"));

            String resolved = HlsPlaylistResolver.resolveHlsPlaylistChain(
                    baseUrl(server) + "/master-query.m3u8?token=abc",
                    Map.of(),
                    3
            );

            assertEquals(baseUrl(server) + "/variant/index.m3u8?token=abc", resolved);
        });
    }

    @Test
    void resolveHlsPlaylistChain_stopsOnVisitedLoop() throws Exception {
        withServer(server -> {
            server.createContext("/loop.m3u8", exchange -> respond(exchange, """
                    #EXTM3U
                    #EXT-X-STREAM-INF:BANDWIDTH=1000000
                    /loop.m3u8
                    """));

            String resolved = HlsPlaylistResolver.resolveHlsPlaylistChain(
                    baseUrl(server) + "/loop.m3u8",
                    Map.of(),
                    5
            );

            assertEquals(baseUrl(server) + "/loop.m3u8", resolved);
        });
    }

    private interface ThrowingServerConsumer {
        void accept(HttpServer server) throws Exception;
    }

    private void withServer(ThrowingServerConsumer consumer) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        try {
            server.start();
            consumer.accept(server);
        } finally {
            server.stop(0);
        }
    }

    private String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/vnd.apple.mpegurl");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
