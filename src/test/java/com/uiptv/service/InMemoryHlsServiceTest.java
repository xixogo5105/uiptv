package com.uiptv.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class InMemoryHlsServiceTest {

    private InMemoryHlsService service;

    @BeforeEach
    public void setUp() {
        service = InMemoryHlsService.getInstance();
        service.clear();
    }

    @Test
    public void testPutAndGet() {
        String filename = "test.ts";
        byte[] data = new byte[]{1, 2, 3};

        service.put(filename, data);

        assertTrue(service.exists(filename));
        assertArrayEquals(data, service.get(filename));
    }

    @Test
    public void testRemove() {
        String filename = "test.ts";
        byte[] data = new byte[]{1, 2, 3};

        service.put(filename, data);
        service.remove(filename);

        assertFalse(service.exists(filename));
        assertNull(service.get(filename));
    }

    @Test
    public void testCleanupOldSegments() {
        // Add more than MAX_SEGMENTS (20)
        for (int i = 0; i < 25; i++) {
            service.put("segment" + i + ".ts", new byte[]{1});
            // Small delay to ensure timestamps differ
            try {
                Thread.sleep(1);
            } catch (InterruptedException ignored) {
            }
        }

        // The oldest segments (0 to 3) should be removed when we reach 25 segments
        // Cleanup runs before storing, so:
        // - At segment 20: count is 20 (NOT > 20), no cleanup, store it (count=21)
        // - At segment 21: count is 21 (> 20), remove oldest (0), store (count=21)
        // - At segment 22: count is 21 (> 20), remove oldest (1), store (count=21)
        // - At segment 23: count is 21 (> 20), remove oldest (2), store (count=21)
        // - At segment 24: count is 21 (> 20), remove oldest (3), store (count=21)
        assertFalse(service.exists("segment0.ts"));
        assertFalse(service.exists("segment3.ts"));

        // Segments 4-24 should exist
        assertTrue(service.exists("segment4.ts"));
        assertTrue(service.exists("segment24.ts"));
        assertTrue(service.exists("segment20.ts"));
    }
}
