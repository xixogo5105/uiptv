package com.uiptv.mobile.shared.ui

import com.uiptv.mobile.shared.epg.MobileEpgProgramme
import com.uiptv.mobile.shared.epg.MobileEpgResult
import com.uiptv.mobile.shared.epg.MobileEpgStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse

class MobileEpgPresentationTest {
    @Test
    fun missingTitlesAndInvalidTimesDoNotRenderProgrammeProgress() {
        for (programme in listOf(
            MobileEpgProgramme("No details available", "", 1, 100),
            MobileEpgProgramme("No programme details", "", 1, 100),
            MobileEpgProgramme("No information available", "", 1, 100),
            MobileEpgProgramme("", "", 1, 100),
            MobileEpgProgramme("Real title", "", 0, 0),
            MobileEpgProgramme("Real title", "", 100, 1)
        )) assertFalse(programme.hasProgrammeInformation())
    }

    @Test
    fun unavailableSourceWithSavedDataIsStale() {
        val current = MobileEpgProgramme("Saved programme", "", 1, 100)
        val result = MobileEpgResult(MobileEpgStatus.UNAVAILABLE, listOf(current))
        assertNull(mobileEpgNowStatus(result, current, null))
        assertEquals("Showing saved guide data", mobileEpgSourceStatus(result, current, null))
    }

    @Test
    fun stalePlaceholderDoesNotLookLikeSavedGuideData() {
        val placeholder = MobileEpgProgramme("No details available", "", 1, 100)
        val result = MobileEpgResult(MobileEpgStatus.STALE, listOf(placeholder))

        assertFalse(placeholder.hasProgrammeInformation())
        assertEquals("No programme guide available", mobileEpgNowStatus(result, null, null))
        assertNull(mobileEpgSourceStatus(result, null, null))
    }

    @Test
    fun upcomingOnlyScheduleDoesNotClaimThatTheGuideIsMissing() {
        val next = MobileEpgProgramme("Evening news", "", 2_000, 3_000)
        val result = MobileEpgResult(MobileEpgStatus.AVAILABLE, listOf(next))

        assertEquals("No programme airing now", mobileEpgNowStatus(result, null, next))
        assertNull(mobileEpgSourceStatus(result, null, next))
    }

    @Test
    fun unmatchedAndUnavailableGuidesUseTheSameNoGuidePresentation() {
        assertEquals("No programme guide available", mobileEpgNowStatus(MobileEpgResult(MobileEpgStatus.NO_MATCH), null, null))
        assertEquals("No programme guide available", mobileEpgNowStatus(MobileEpgResult(MobileEpgStatus.UNAVAILABLE), null, null))
    }

    @Test
    fun staleScheduleRemainsVisibleWithAStaleDataLabel() {
        val current = MobileEpgProgramme("Current programme", "", 1_000, 4_000)
        val result = MobileEpgResult(MobileEpgStatus.STALE, listOf(current))

        assertNull(mobileEpgNowStatus(result, current, null))
        assertEquals("Showing saved guide data", mobileEpgSourceStatus(result, current, null))
    }
}
