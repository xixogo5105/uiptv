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
                cooldownMinutes = 15
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
                cooldownMinutes = 15
            )
        )
    }

    @Test
    fun blocksAdWithinCooldownWindowAcrossRestarts() {
        val shownAt = 1_700_000_000_000L
        val fourteenMinutesLater = shownAt + (14L * 60_000L)
        assertFalse(
            AdCooldownPolicy.shouldShowAd(
                isPro = false,
                lastAdShownUtcMillis = shownAt,
                nowUtcMillis = fourteenMinutesLater,
                cooldownMinutes = 15
            )
        )
        assertEquals(
            60_000L,
            AdCooldownPolicy.remainingCooldownMillis(shownAt, fourteenMinutesLater, 15)
        )
    }

    @Test
    fun allowsAdOnceCooldownWindowElapses() {
        val shownAt = 1_700_000_000_000L
        val fifteenMinutesLater = shownAt + (15L * 60_000L)
        assertTrue(
            AdCooldownPolicy.shouldShowAd(
                isPro = false,
                lastAdShownUtcMillis = shownAt,
                nowUtcMillis = fifteenMinutesLater,
                cooldownMinutes = 15
            )
        )
        assertEquals(
            0L,
            AdCooldownPolicy.remainingCooldownMillis(shownAt, fifteenMinutesLater, 15)
        )
    }

    @Test
    fun respectsConfigurableProductionCooldown() {
        val shownAt = 1_700_000_000_000L
        val thirtyMinutesLater = shownAt + (30L * 60_000L)
        val sixtyMinutesLater = shownAt + (60L * 60_000L)
        assertFalse(
            AdCooldownPolicy.shouldShowAd(
                isPro = false,
                lastAdShownUtcMillis = shownAt,
                nowUtcMillis = thirtyMinutesLater,
                cooldownMinutes = 60
            )
        )
        assertTrue(
            AdCooldownPolicy.shouldShowAd(
                isPro = false,
                lastAdShownUtcMillis = shownAt,
                nowUtcMillis = sixtyMinutesLater,
                cooldownMinutes = 60
            )
        )
    }
}
