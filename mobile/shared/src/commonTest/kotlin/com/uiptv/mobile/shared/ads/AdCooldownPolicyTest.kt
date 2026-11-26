package com.uiptv.mobile.shared.ads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdCooldownPolicyTest {
    @Test
    fun proUserNeverSeesAds() {
        assertFalse(
            AdCooldownPolicy.shouldShowAd(
                isPro = true,
                lastAdShownUtcMillis = 0L,
                nowUtcMillis = 1_700_000_000_000L,
                cooldownMinutes = 60
            )
        )
    }

    @Test
    fun firstPlaybackShowsAdWhenNeverShownBefore() {
        assertTrue(
            AdCooldownPolicy.shouldShowAd(
                isPro = false,
                lastAdShownUtcMillis = 0L,
                nowUtcMillis = 1_700_000_000_000L,
                cooldownMinutes = 60
            )
        )
    }

    @Test
    fun blocksAdWithinCooldownWindowAcrossRestarts() {
        val shownAt = 1_700_000_000_000L
        val fiftyNineMinutesLater = shownAt + (59L * 60_000L)
        assertFalse(
            AdCooldownPolicy.shouldShowAd(
                isPro = false,
                lastAdShownUtcMillis = shownAt,
                nowUtcMillis = fiftyNineMinutesLater,
                cooldownMinutes = 60
            )
        )
        assertEquals(
            60_000L,
            AdCooldownPolicy.remainingCooldownMillis(shownAt, fiftyNineMinutesLater, 60)
        )
    }

    @Test
    fun allowsAdAtSixtyMinuteBoundary() {
        val shownAt = 1_700_000_000_000L
        val sixtyMinutesLater = shownAt + (60L * 60_000L)
        assertTrue(
            AdCooldownPolicy.shouldShowAd(
                isPro = false,
                lastAdShownUtcMillis = shownAt,
                nowUtcMillis = sixtyMinutesLater,
                cooldownMinutes = 60
            )
        )
        assertEquals(
            0L,
            AdCooldownPolicy.remainingCooldownMillis(shownAt, sixtyMinutesLater, 60)
        )
    }
}
