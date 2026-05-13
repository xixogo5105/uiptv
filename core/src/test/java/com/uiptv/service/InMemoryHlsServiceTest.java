package com.uiptv.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryHlsServiceTest {

    private InMemoryHlsService service;

    @BeforeEach
    void setUp() {
        service = InMemoryHlsService.getInstance();
        service.clear();
    }

    @Test
    void testPutAndGet() {
        String filename = "test.ts";
        byte[] data = new byte[]{1, 2, 3};

        service.put(filename, data);

        assertTrue(service.exists(filename));
        assertArrayEquals(data, service.get(filename));
    }

    @Test
    void testRemove() {
        String filename = "test.m3u8";
        byte[] data = new byte[]{1, 2, 3};

        service.put(filename, data);
        service.remove(filename);

        assertFalse(service.exists(filename));
        assertNull(service.get(filename));
    }

    @Test
    void testCleanupOldSegments() {
        int maxSegments = Integer.getInteger("uiptv.hls.max.segments", 180);
        int extraSegments = 5;
        int totalSegments = maxSegments + extraSegments;

        for (int i = 0; i < totalSegments; i++) {
            service.put("segment" + i + ".ts", new byte[]{1});
            waitForNextMillisecond();
        }

        assertFalse(service.exists("segment0.ts"));
        assertFalse(service.exists("segment" + (extraSegments - 1) + ".ts"));
        assertTrue(service.exists("segment" + extraSegments + ".ts"));
        assertTrue(service.exists("segment" + (totalSegments - 1) + ".ts"));
        assertTrue(service.exists("segment" + maxSegments + ".ts"));
    }

    @Test
    void testClientAccessTracking() {
        assertEquals(0L, service.getLastClientAccessAt());

        service.markClientAccess();

        assertTrue(service.getLastClientAccessAt() > 0L);

        service.clear();

        assertEquals(0L, service.getLastClientAccessAt());
    }

    private void waitForNextMillisecond() {
        long now = System.currentTimeMillis();
        while (System.currentTimeMillis() == now) {
            Thread.onSpinWait();
        }
    }
}
