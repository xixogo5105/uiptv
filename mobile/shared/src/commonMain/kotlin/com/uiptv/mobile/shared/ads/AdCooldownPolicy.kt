package com.uiptv.mobile.shared.ads

/**
 * Pure UTC-timestamp-based interstitial ad frequency cap policy.
 * Uses persisted UTC epoch milliseconds so app restarts do not reset the cooldown timer.
 */
object AdCooldownPolicy {
    const val DEFAULT_TEST_COOLDOWN_MINUTES = 60
    const val DEFAULT_PRODUCTION_COOLDOWN_MINUTES = 60

    fun shouldShowAd(
        isPro: Boolean,
        lastAdShownUtcMillis: Long,
        nowUtcMillis: Long,
        cooldownMinutes: Int
    ): Boolean {
        if (isPro) return false
        if (cooldownMinutes <= 0) return true
        if (lastAdShownUtcMillis <= 0L) return true
        val elapsedMillis = nowUtcMillis - lastAdShownUtcMillis
        if (elapsedMillis < 0L) return false
        return elapsedMillis >= cooldownMinutes.toLong() * 60_000L
    }

    fun remainingCooldownMillis(
        lastAdShownUtcMillis: Long,
        nowUtcMillis: Long,
        cooldownMinutes: Int
    ): Long {
        if (cooldownMinutes <= 0 || lastAdShownUtcMillis <= 0L) return 0L
        val cooldownMillis = cooldownMinutes.toLong() * 60_000L
        val elapsedMillis = (nowUtcMillis - lastAdShownUtcMillis).coerceAtLeast(0L)
        return (cooldownMillis - elapsedMillis).coerceAtLeast(0L)
    }
}
