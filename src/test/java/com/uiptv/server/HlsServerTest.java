package com.uiptv.server;

import com.uiptv.service.InMemoryHlsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HlsServerTest {

    @AfterEach
    void tearDown() {
        InMemoryHlsService.getInstance().clear();
    }

    @Test
    void hlsUploadServer_putDeleteAndReject() throws Exception {
        HttpHlsUploadServer handler = new HttpHlsUploadServer();
        TestHttpExchange putExchange = new TestHttpExchange("/hls-upload/segment.ts", "PUT", "data");
        handler.handle(putExchange);
        assertEquals(200, putExchange.getResponseCode());
        assertEquals("data", new String(InMemoryHlsService.getInstance().get("segment.ts"), StandardCharsets.UTF_8));

        System.setProperty("uiptv.hls.ts.delete.grace.millis", "1");
        TestHttpExchange deleteExchange = new TestHttpExchange("/hls-upload/segment.ts", "DELETE");
        handler.handle(deleteExchange);
        assertEquals(200, deleteExchange.getResponseCode());
        long deadline = System.currentTimeMillis() + 200;
        while (InMemoryHlsService.getInstance().exists("segment.ts") && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertFalse(InMemoryHlsService.getInstance().exists("segment.ts"));

        TestHttpExchange postExchange = new TestHttpExchange("/hls-upload/segment.ts", "POST");
        handler.handle(postExchange);
        assertEquals(405, postExchange.getResponseCode());
    }

    @Test
    void hlsFileServer_servesM3u8RewritesAndTs() throws Exception {
        HttpHlsFileServer handler = new HttpHlsFileServer();

        String playlist = "#EXTM3U\n#EXTINF:10,\nsegment1.ts\nsegment2.ts?x=1\n#EXT-X-ENDLIST\n";
        InMemoryHlsService.getInstance().put("playlist.m3u8", playlist.getBytes(StandardCharsets.UTF_8));

        TestHttpExchange m3u8Exchange = new TestHttpExchange("/hls/playlist.m3u8?hvec=yes", "GET");
        handler.handle(m3u8Exchange);
        assertEquals(200, m3u8Exchange.getResponseCode());
        assertTrue(m3u8Exchange.getResponseHeaders().getFirst("Content-Type").contains("vnd.apple.mpegurl"));
        String body = m3u8Exchange.getResponseBodyText();
        assertTrue(body.contains("segment1.ts?hvec=1"));
        assertTrue(body.contains("segment2.ts?x=1&hvec=1"));

        byte[] tsData = "ts-data".getBytes(StandardCharsets.UTF_8);
        InMemoryHlsService.getInstance().put("segment.ts", tsData);
        TestHttpExchange tsExchange = new TestHttpExchange("/hls/segment.ts", "GET");
        handler.handle(tsExchange);
        assertEquals(200, tsExchange.getResponseCode());
        assertTrue(tsExchange.getResponseHeaders().getFirst("Content-Type").contains("video/mp2t"));
        assertEquals("ts-data", new String(tsExchange.getResponseBodyBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void hlsFileServer_returns404ForEmptySegment() throws Exception {
        HttpHlsFileServer handler = new HttpHlsFileServer();
        InMemoryHlsService.getInstance().put("empty.ts", new byte[0]);

        TestHttpExchange exchange = new TestHttpExchange("/hls/empty.ts", "GET");
        handler.handle(exchange);
        assertEquals(404, exchange.getResponseCode());
    }

    @Test
    void hlsFileServer_waitsForDelayedTsUpload() throws Exception {
        HttpHlsFileServer handler = new HttpHlsFileServer();
        String fileName = "delayed.ts";

        Thread uploader = new Thread(() -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            InMemoryHlsService.getInstance().put(fileName, "late".getBytes(StandardCharsets.UTF_8));
        });
        uploader.start();

        TestHttpExchange exchange = new TestHttpExchange("/hls/" + fileName, "GET");
        handler.handle(exchange);
        uploader.join();

        assertEquals(200, exchange.getResponseCode());
        assertEquals("late", new String(exchange.getResponseBodyBytes(), StandardCharsets.UTF_8));
    }
}
