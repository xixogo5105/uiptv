package com.uiptv.util;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpUtilTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void sendRequest_readsBodyEvenWhenContentTypeHeaderIsMalformed() throws IOException {
        startServer(exchange -> writeResponse(
                exchange,
                200,
                "#EXTM3U\n#EXTINF:-1 group-title=\"News\",Alpha\nhttps://example.test/alpha.m3u8\n",
                "vnd.apple.mpegurl"
        ));

        HttpUtil.HttpResult response = HttpUtil.sendRequest(url("/playlist.m3u8?regions=in"), null, "GET");

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("#EXTM3U"));
        assertTrue(response.getBody().contains("Alpha"));
    }

    @Test
    void sendRequest_honorsCharsetEvenWhenContentTypeTypeIsLoose() throws IOException {
        startServer(exchange -> writeResponse(
                exchange,
                200,
                "Cafe\u00e9",
                "vnd.apple.mpegurl; charset=ISO-8859-1",
                StandardCharsets.ISO_8859_1
        ));

        HttpUtil.HttpResult response = HttpUtil.sendRequest(url("/playlist.m3u8"), null, "GET");

        assertEquals("Cafe\u00e9", response.getBody());
    }

    private void startServer(Handler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/playlist.m3u8", handler::handle);
        server.start();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static void writeResponse(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        writeResponse(exchange, status, body, contentType, StandardCharsets.UTF_8);
    }

    private static void writeResponse(HttpExchange exchange, int status, String body, String contentType, java.nio.charset.Charset charset) throws IOException {
        byte[] bytes = body.getBytes(charset);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        } finally {
            exchange.close();
        }
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
