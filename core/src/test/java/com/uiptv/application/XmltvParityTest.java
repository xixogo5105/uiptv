package com.uiptv.application;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
class XmltvParityTest {
    @Test void normalizedNamesMatchAnnotationsSpacingAndCaseWithoutMergingDistinctChannels() {
        var programme = new EpgProgramme("News", "", 1000, 2000);
        var guide = new XmltvGuide(java.util.Map.of("bbc", java.util.List.of(programme)),
                java.util.Map.of("bbc one", "bbc"));
        for (String name : java.util.List.of("BBC One Ⓖ", "bbcONE", "BBC-One HD", "bBc oNe"))
            assertEquals(java.util.List.of(programme), guide.forChannel("stream", name), name);
        assertTrue(guide.forChannel("stream", "BBC One +1").isEmpty());
        assertTrue(guide.forChannel("stream", "BBC One Scotland").isEmpty());
        var ambiguous = new XmltvGuide(guide.programmes(),
                java.util.Map.of("bbc one", "bbc", "bbcone", "other"));
        assertTrue(ambiguous.forChannel("stream", "BBC One Ⓖ").isEmpty());
        assertEquals(java.util.List.of(programme), ambiguous.forChannel("bbc", "BBC One Ⓖ"));
    }
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
