package com.uiptv.mobile.shared.epg

import kotlin.test.*

class MobileEpgTest {
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
