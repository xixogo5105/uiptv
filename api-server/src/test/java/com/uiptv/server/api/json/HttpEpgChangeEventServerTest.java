package com.uiptv.server.api.json;

import com.uiptv.application.EpgChangeNotifications;
import com.uiptv.application.EpgProgramme;
import com.uiptv.db.EpgCacheDb;
import com.uiptv.server.TestHttpExchange;
import com.uiptv.testsupport.DbBackedTest;
import org.junit.jupiter.api.Test;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpEpgChangeEventServerTest extends DbBackedTest {
    @Test
    void rejectsNonGetRequests() throws Exception {
        TestHttpExchange exchange = new TestHttpExchange("/epg/changes", "POST");
        new HttpEpgChangeEventServer().handle(exchange);
        assertEquals(405, exchange.getResponseCode());
        assertEquals("GET", exchange.getResponseHeaders().getFirst("Allow"));
    }

    @Test
    void sendsReplayedEventAndClosesWhenTheClientDisconnects() throws Exception {
        long now = System.currentTimeMillis();
        new EpgCacheDb().replaceSource("sse-source", Map.of("guide", List.of(
                new EpgProgramme("News", "", now - 1_000, now + 60_000)
        )), Map.of("News", "guide"), Map.of(), now, now + 60_000);
        long revision = EpgChangeNotifications.snapshot().revision();
        InterruptingExchange exchange = new InterruptingExchange(
                "/epg/changes?since=" + (revision - 1), "GET");
        exchange.getRequestHeaders().set("Last-Event-ID", "bad");
        try {
            new HttpEpgChangeEventServer().handle(exchange);
        } finally {
            Thread.interrupted();
        }
        assertEquals(200, exchange.getResponseCode());
        assertEquals("text/event-stream; charset=UTF-8", exchange.getResponseHeaders().getFirst("Content-Type"));
        assertTrue(exchange.getResponseBodyText().contains("kind"));
    }

    private static final class InterruptingExchange extends TestHttpExchange {
        private InterruptingExchange(String uri, String method) {
            super(uri, method);
        }

        @Override
        public OutputStream getResponseBody() {
            OutputStream delegate = super.getResponseBody();
            return new FilterOutputStream(delegate) {
                @Override
                public void write(byte[] bytes, int offset, int length) throws IOException {
                    super.write(bytes, offset, length);
                    Thread.currentThread().interrupt();
                }
            };
        }
    }
}
