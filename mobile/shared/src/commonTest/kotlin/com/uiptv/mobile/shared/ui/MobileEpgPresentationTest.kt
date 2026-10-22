package com.uiptv.mobile.shared.ui

import com.uiptv.mobile.shared.epg.MobileEpgProgramme
import com.uiptv.mobile.shared.epg.MobileEpgResult
import com.uiptv.mobile.shared.epg.MobileEpgStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MobileEpgPresentationTest {
    @Test
    fun upcomingOnlyScheduleDoesNotClaimThatTheGuideIsMissing() {
        val next = MobileEpgProgramme("Evening news", "", 2_000, 3_000)
        val result = MobileEpgResult(MobileEpgStatus.AVAILABLE, listOf(next))

        assertEquals("No programme airing now", mobileEpgNowStatus(result, null, next))
        assertNull(mobileEpgSourceStatus(result, null, next))
    }

    @Test
    fun unmatchedAndUnavailableGuidesHaveDistinctStates() {
        assertEquals("No guide available", mobileEpgNowStatus(MobileEpgResult(MobileEpgStatus.NO_MATCH), null, null))
        assertEquals("Guide source unavailable", mobileEpgNowStatus(MobileEpgResult(MobileEpgStatus.UNAVAILABLE), null, null))
    }

    @Test
    fun staleScheduleRemainsVisibleWithAStaleDataLabel() {
        val current = MobileEpgProgramme("Current programme", "", 1_000, 4_000)
        val result = MobileEpgResult(MobileEpgStatus.STALE, listOf(current))

        assertNull(mobileEpgNowStatus(result, current, null))
        assertEquals("Showing saved guide data", mobileEpgSourceStatus(result, current, null))
    }
}
