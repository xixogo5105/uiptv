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
                cooldownMinutes = 30
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
                cooldownMinutes = 30
            )
        )
    }

    @Test
    fun blocksAdWithinCooldownWindowAcrossRestarts() {
        val shownAt = 1_700_000_000_000L
        val twentyNineMinutesLater = shownAt + (29L * 60_000L)
        assertFalse(
            AdCooldownPolicy.shouldShowAd(
                isPro = false,
                lastAdShownUtcMillis = shownAt,
                nowUtcMillis = twentyNineMinutesLater,
                cooldownMinutes = 30
            )
        )
        assertEquals(
            60_000L,
            AdCooldownPolicy.remainingCooldownMillis(shownAt, twentyNineMinutesLater, 30)
        )
    }

    @Test
    fun allowsAdAtThirtyMinuteBoundary() {
        val shownAt = 1_700_000_000_000L
        val thirtyMinutesLater = shownAt + (30L * 60_000L)
        assertTrue(
            AdCooldownPolicy.shouldShowAd(
                isPro = false,
                lastAdShownUtcMillis = shownAt,
                nowUtcMillis = thirtyMinutesLater,
                cooldownMinutes = 30
            )
        )
        assertEquals(
            0L,
            AdCooldownPolicy.remainingCooldownMillis(shownAt, thirtyMinutesLater, 30)
        )
    }
}
