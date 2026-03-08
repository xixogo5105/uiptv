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
        // MAX_SEGMENTS is 40; add 45 so oldest 5 are evicted.
        for (int i = 0; i < 45; i++) {
            service.put("segment" + i + ".ts", new byte[]{1});
            waitForNextMillisecond();
        }

        assertFalse(service.exists("segment0.ts"));
        assertFalse(service.exists("segment4.ts"));

        assertTrue(service.exists("segment5.ts"));
        assertTrue(service.exists("segment44.ts"));
        assertTrue(service.exists("segment40.ts"));
    }

    private void waitForNextMillisecond() {
        long now = System.currentTimeMillis();
        while (System.currentTimeMillis() == now) {
            Thread.onSpinWait();
        }
    }
}
