package com.uiptv.mobile.shared.epg

import kotlin.test.*

class MobileEpgTest {
    @Test fun playlistResolutionLabelsRetainExactHdSdAndRegionalIdentity() {
        val sd = MobileEpgProgramme("SD programme", "", 1000, 2000)
        val hd = sd.copy(title = "HD programme")
        val guide = MobileXmltvGuide(mapOf("sd" to listOf(sd), "hd" to listOf(hd)),
            mapOf("b4u kadak" to "sd", "b4u kadak hd" to "hd"))
        assertEquals(listOf(sd), guide.forChannel("playlist-id", "B4U Kadak (576p)"))
        assertEquals(listOf(hd), guide.forChannel("playlist-id", "B4U Kadak HD (1080p) [Geo-blocked]"))
        assertTrue(guide.forChannel("playlist-id", "B4U Kadak International (576p)").isEmpty())
        assertTrue(guide.forChannel("playlist-id", "B4U Kadak +1 (576p)").isEmpty())
    }

    @Test fun annotatedNamesIgnoreCaseAndSpacingButKeepTimeshiftsAndRegions() {
        val aliases = MobileXmltvGuide.qualityAliases(mapOf("bbc one" to "bbc"))
        for (name in listOf("BBC One Ⓖ", "bbcONE", "BBC-One HD"))
            assertEquals("bbc", aliases[MobileXmltvGuide.normalizeQuality(name)])
        assertEquals(null, aliases[MobileXmltvGuide.normalizeQuality("BBC One +1")])
        assertEquals(null, aliases[MobileXmltvGuide.normalizeQuality("BBC One Scotland")])
        val ambiguous = MobileXmltvGuide.qualityAliases(mapOf("bbc one" to "bbc", "bbcone" to "other"))
        assertEquals(null, ambiguous[MobileXmltvGuide.normalizeQuality("BBC One Ⓖ")])
    }
    @Test fun sourcePriorityAndMeaningfulFallbackMatchDesktopPolicy() {
        val native = MobileEpgProgramme("Native film", "", 1000, 2000)
        val generic = MobileEpgProgramme("Movie", "", 2000, 4000)
        val xml = MobileEpgProgramme("XMLTV film", "", 1000, 4000)
        assertEquals(listOf(native, xml.copy(start = 2000)),
            MobileEpgSchedules.preferSources(listOf(listOf(native, generic), listOf(xml))))
        assertEquals(listOf(xml), MobileEpgSchedules.preferSources(listOf(listOf(xml), listOf(native))))
    }

    @Test fun exactIdentityAndUniqueNameFallbackRetainSuffixes() {
        val p = MobileEpgProgramme("News", "Details", 1000, 2000)
        val guide = MobileXmltvGuide(mapOf("xmltv-id" to listOf(p)), mapOf("station hd" to "xmltv-id"))
        assertEquals(listOf(p), guide.forChannel("xmltv-id", "other"))
        assertEquals(listOf(p), guide.forChannel("unknown", " STATION  HD "))
        assertEquals(listOf(p), guide.forChannel("local-row", "Station"))
        assertTrue(guide.forChannel("stream-id", "other").isEmpty())
    }
    @Test fun qualityAliasesNeverMergeAmbiguousVariantsOrRegions() {
        val p = MobileEpgProgramme("News", "", 1000, 2000)
        val guide = MobileXmltvGuide(mapOf("hd" to listOf(p), "sd" to listOf(p), "star" to listOf(p)),
            mapOf("zee tv hd" to "hd", "zee tv sd" to "sd", "star plus [4k]" to "star"))
        assertEquals(listOf(p), guide.forChannel("missing", "STAR PLUS"))
        assertEquals(listOf(p), guide.forChannel("missing", "Star Plus HD"))
        assertTrue(guide.forChannel("missing", "Star Plus Canada HD").isEmpty())
        assertTrue(guide.forChannel("missing", "Zee TV 4K").isEmpty())
        assertEquals(listOf(p), guide.forChannel("missing", "Zee TV HD"))
    }
    @Test fun namedMoviesTakePriorityOverOverlappingGenericPlaceholders() {
        val godzilla = MobileEpgProgramme("Godzilla", "", 1000, 3000)
        val movie = MobileEpgProgramme("Movie", "", 1500, 4000)
        assertEquals(listOf(godzilla, movie.copy(start = 3000)), MobileEpgSchedules.normalize(listOf(movie, godzilla, godzilla)))
        assertEquals(listOf(godzilla), MobileEpgSchedules.normalize(listOf(godzilla, movie.copy(stop = 2000))))
    }
    @Test fun programmeBoundariesAreHalfOpenAndNextIsUpcoming() {
        val first = MobileEpgProgramme("Now", "", 1000, 2000)
        val next = MobileEpgProgramme("Next", "", 2000, 3000)
        val result = MobileEpgResult(MobileEpgStatus.AVAILABLE, listOf(first, next))
        assertEquals(first, result.now(1999)); assertEquals(next, result.next(1999))
        assertEquals(next, result.now(2000)); assertNull(result.next(2000)); assertNull(result.now(3000))
        assertEquals(MobileEpgStatus.DISABLED, MobileEpgResult().status)
    }
}
