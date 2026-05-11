package com.uiptv.server;

import com.uiptv.service.InMemoryHlsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class HlsServerTest {

    private static void awaitCondition(BooleanSupplier condition, long timeoutNanos) {
        long deadline = System.nanoTime() + timeoutNanos;
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.onSpinWait();
        }
    }

    @AfterEach
    void tearDown() {
        InMemoryHlsService.INSTANCE.clear();
    }

    @Test
    void hlsUploadServer_putDeleteAndReject() throws Exception {
        HlsRouteSupport.INSTANCE.upload("segment.ts", "data".getBytes(StandardCharsets.UTF_8));
        assertEquals("data", new String(InMemoryHlsService.INSTANCE.get("segment.ts"), StandardCharsets.UTF_8));

        System.setProperty("uiptv.hls.ts.delete.grace.millis", "1");
        HlsRouteSupport.INSTANCE.delete("segment.ts");
        awaitCondition(() -> !InMemoryHlsService.INSTANCE.exists("segment.ts"), TimeUnit.MILLISECONDS.toNanos(200));
        assertFalse(InMemoryHlsService.INSTANCE.exists("segment.ts"));
    }

    @Test
    void hlsFileServer_servesM3u8RewritesAndTs() throws Exception {
        String playlist = "#EXTM3U\n#EXTINF:10,\nsegment1.ts\nsegment2.ts?x=1\n#EXT-X-ENDLIST\n";
        InMemoryHlsService.INSTANCE.put("playlist.m3u8", playlist.getBytes(StandardCharsets.UTF_8));

        HlsFilePayload m3u8Payload = HlsRouteSupport.INSTANCE.readFile("playlist.m3u8", true);
        assertNotNull(m3u8Payload);
        assertTrue(m3u8Payload.getContentType().contains("vnd.apple.mpegurl"));
        String body = new String(m3u8Payload.getBytes(), StandardCharsets.UTF_8);
        assertTrue(body.contains("segment1.ts?hvec=1"));
        assertTrue(body.contains("segment2.ts?x=1&hvec=1"));

        byte[] tsData = "ts-data".getBytes(StandardCharsets.UTF_8);
        InMemoryHlsService.INSTANCE.put("segment.ts", tsData);
        HlsFilePayload tsPayload = HlsRouteSupport.INSTANCE.readFile("segment.ts", false);
        assertNotNull(tsPayload);
        assertTrue(tsPayload.getContentType().contains("video/mp2t"));
        assertEquals("ts-data", new String(tsPayload.getBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void hlsFileServer_returns404ForEmptySegment() throws Exception {
        InMemoryHlsService.INSTANCE.put("empty.ts", new byte[0]);
        assertNull(HlsRouteSupport.INSTANCE.readFile("empty.ts", false));
    }

    @Test
    void hlsFileServer_waitsForDelayedTsUpload() throws Exception {
        String fileName = "delayed.ts";

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        CountDownLatch uploadLatch = new CountDownLatch(1);
        scheduler.schedule(() -> {
            InMemoryHlsService.INSTANCE.put(fileName, "late".getBytes(StandardCharsets.UTF_8));
            uploadLatch.countDown();
        }, 10, TimeUnit.MILLISECONDS);

        HlsFilePayload payload = HlsRouteSupport.INSTANCE.readFile(fileName, false);
        uploadLatch.await(1, TimeUnit.SECONDS);
        scheduler.shutdownNow();

        assertNotNull(payload);
        assertEquals("late", new String(payload.getBytes(), StandardCharsets.UTF_8));
    }
}
