package com.uiptv.application;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
class XmltvParityTest {
    @Test void sharedFixturePinsDstMissingStopsAndAmbiguousMatching() throws Exception {
        try (var input = getClass().getResourceAsStream("/epg/parity.xml")) {
            var guide = XmltvGuide.read(input, Instant.parse("2026-10-25T00:45:00Z"));
            var list = guide.forChannel("bbc", "other"); assertEquals(2, list.size());
            assertEquals(Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(), list.getFirst().start());
            assertEquals(list.get(1).start(), list.getFirst().stop());
            assertEquals(list, guide.forChannel("stream", " BBC  ONE HD "));
            assertTrue(guide.forChannel("local-row", "News").isEmpty());
            assertEquals(list, guide.forChannel("stream", "BBC One"));
        }
    }
}
