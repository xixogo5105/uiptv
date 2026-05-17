package com.uiptv.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebActivityLogTest {

    @AfterEach
    void tearDown() {
        WebActivityLog.clear();
    }

    @Test
    void recordRequest_writesFriendlyPlaybackEntryWithIp() {
        WebActivityLog.recordRequest(
                "GET",
                "/player/live",
                "mode=itv&name=BBC+One",
                "192.168.1.25",
                200,
                12
        );

        String text = WebActivityLog.readAllText();
        assertTrue(text.contains("IP 192.168.1.25"));
        assertTrue(text.contains("Played live channel \"BBC One\" in the web player"));
        assertTrue(text.contains("Result: completed"));
    }

    @Test
    void describeRequest_identifiesPublishedM3uAccess() {
        String description = WebActivityLog.describeRequest("GET", "/iptv.m3u", "");

        assertTrue(description.contains("published M3U playlist"));
        assertTrue(description.contains("iptv.m3u"));
    }

    @Test
    void clear_removesTemporaryEntries() {
        WebActivityLog.recordRequest("GET", "/playlist.m3u8", "accountId=1", "127.0.0.1", 200, 1);
        WebActivityLog.clear();

        assertTrue(WebActivityLog.readAllText().isBlank());
    }

    @Test
    void listener_receivesNewEntries() {
        AtomicReference<String> received = new AtomicReference<>();
        Consumer<String> listener = received::set;
        WebActivityLog.registerListener(listener);
        try {
            WebActivityLog.recordRequest("GET", "/bookmarks.m3u8", "", "127.0.0.1", 200, 1);
        } finally {
            WebActivityLog.unregisterListener(listener);
        }

        assertTrue(received.get().contains("bookmarks M3U playlist"));
    }
}
