package com.uiptv.mobile.shared.epg

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidXmltvReaderTest {
    @Test fun reusesStrictFormatterForXmltvTimesWithAndWithoutSeconds() {
        assertEquals(
            Instant.parse("2026-10-30T18:00:00Z").toEpochMilli(),
            AndroidXmltvReader.time("202610301800 +0000")
        )
        assertEquals(
            Instant.parse("2026-10-30T19:00:00Z").toEpochMilli(),
            AndroidXmltvReader.time("20261030200000 +0100")
        )
    }

    @Test fun progressiveReadWaitsForMultipleCurrentOrUpcomingProgrammes() {
        val now = Instant.parse("2026-10-30T18:30:00Z").toEpochMilli()
        val current = MobileEpgProgramme("On now", "", now - 1_000, now + 1_000)
        val next = MobileEpgProgramme("Up next", "", now + 1_000, now + 2_000)
        val later = MobileEpgProgramme("Later", "", now + 2_000, now + 3_000)

        assertFalse(AndroidXmltvReader.hasEnoughUpcomingProgrammes(listOf(current), now, 2))
        assertTrue(AndroidXmltvReader.hasEnoughUpcomingProgrammes(listOf(current, next, later), now, 2))
    }
}
