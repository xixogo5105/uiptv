package com.uiptv.util;

import com.rometools.rome.io.FeedException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RssFeedReaderTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void parsesValidRssFeed() throws IOException, FeedException {
        startServer(exchange -> {
            String body = """
                    <?xml version="1.0" encoding="UTF-8" ?>
                    <rss version="2.0">
                      <channel>
                        <title>Mock RSS</title>
                        <link>http://example.test/</link>
                        <description>Mock feed</description>
                        <item><title>RSS One</title><link>https://example.test/one.ts</link></item>
                        <item><title>RSS Two</title><link>https://example.test/two.ts</link></item>
                      </channel>
                    </rss>
                    """;
            writeResponse(exchange, 200, body, "application/rss+xml; charset=utf-8");
        });

        List<RssFeedReader.RssItem> items = RssFeedReader.getItems(url("/rss"));
        assertEquals(2, items.size());
        assertEquals("RSS One", items.get(0).getTitle());
        assertEquals("https://example.test/one.ts", items.get(0).getLink());
    }

    @Test
    void throwsHelpfulErrorOnNon200() throws IOException {
        startServer(exchange -> writeResponse(
                exchange,
                403,
                "<html><body>Forbidden</body></html>",
                "text/html; charset=utf-8"
        ));

        IOException ex = assertThrows(IOException.class, () -> RssFeedReader.getItems(url("/rss")));
        assertTrue(ex.getMessage().contains("HTTP 403"), ex.getMessage());
    }

    @Test
    void throwsHelpfulErrorOnHtml200() throws IOException {
        startServer(exchange -> writeResponse(
                exchange,
                200,
                "<!doctype html><html><body>Not an RSS feed</body></html>",
                "text/html; charset=utf-8"
        ));

        IOException ex = assertThrows(IOException.class, () -> RssFeedReader.getItems(url("/rss")));
        assertTrue(ex.getMessage().toLowerCase().contains("html"), ex.getMessage());
    }

    private void startServer(Handler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rss", handler::handle);
        server.start();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static void writeResponse(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
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

