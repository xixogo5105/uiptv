package com.uiptv.mobile.android

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.uiptv.mobile.shared.ads.AdCooldownPolicy
import com.uiptv.mobile.shared.logging.AndroidAppLog
import com.uiptv.mobile.shared.playback.PlaybackLaunchResult
import com.uiptv.mobile.shared.settings.AndroidPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class AndroidAdMobManager(
    context: Context,
    private val preferences: AndroidPreferencesRepository,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sdkInitialized = AtomicBoolean(false)
    private var interstitialAd: InterstitialAd? = null
    private var isLoadingAd = false
    private var retryJob: Job? = null
    private var retryAttempt = 0

    fun initializeAndPreload(isPro: Boolean) {
        if (isPro) {
            clearLoadedAds()
            return
        }
        if (sdkInitialized.compareAndSet(false, true)) {
            val testDeviceIds = BuildConfig.ADMOB_TEST_DEVICE_IDS
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (testDeviceIds.isNotEmpty()) {
                MobileAds.setRequestConfiguration(
                    RequestConfiguration.Builder()
                        .setTestDeviceIds(testDeviceIds)
                        .build()
                )
            }
            MobileAds.initialize(appContext) {
                scope.launch { preloadInterstitial() }
            }
        } else {
            scope.launch { preloadInterstitial() }
        }
    }

    fun clearLoadedAds() {
        scope.launch {
            retryJob?.cancel()
            retryJob = null
            retryAttempt = 0
            interstitialAd = null
            isLoadingAd = false
        }
    }

    private fun preloadInterstitial() {
        if (interstitialAd != null || isLoadingAd) return
        val adUnitId = BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID
        if (adUnitId.isBlank()) return
        isLoadingAd = true
        InterstitialAd.load(
            appContext,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoadingAd = false
                    retryAttempt = 0
                    retryJob?.cancel()
                    retryJob = null
                    interstitialAd = ad
                    AndroidAppLog.d("AdMob", "Interstitial ad loaded (debug=${BuildConfig.DEBUG})")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingAd = false
                    interstitialAd = null
                    AndroidAppLog.w("AdMob", "Interstitial load failed (code=${error.code}): ${error.message}")
                    if (retryAttempt < 3) {
                        val delayMillis = when (retryAttempt) {
                            0 -> 5_000L
                            1 -> 15_000L
                            else -> 30_000L
                        }
                        retryAttempt += 1
                        retryJob?.cancel()
                        retryJob = scope.launch {
                            delay(delayMillis)
                            preloadInterstitial()
                        }
                    }
                }
            }
        )
    }

    suspend fun runWithPreRollAd(
        activity: Activity?,
        onProceed: suspend () -> PlaybackLaunchResult
    ): PlaybackLaunchResult {
        val snapshot = preferences.load()
        val cooldownMinutes = snapshot.adCooldownMinutes ?: BuildConfig.AD_COOLDOWN_MINUTES
        val nowUtcMillis = clockMillis()
        if (!AdCooldownPolicy.shouldShowAd(
                isPro = snapshot.proUnlocked,
                lastAdShownUtcMillis = snapshot.lastAdShownUtcMillis,
                nowUtcMillis = nowUtcMillis,
                cooldownMinutes = cooldownMinutes
            )
        ) {
            return onProceed()
        }

        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            return onProceed()
        }

        val shown = showInterstitialIfReady(activity)
        if (!shown) {
            scope.launch { preloadInterstitial() }
        }
        return onProceed()
    }

    private suspend fun showInterstitialIfReady(activity: Activity): Boolean =
        withContext(Dispatchers.Main.immediate) {
            val ad = interstitialAd ?: return@withContext false
            interstitialAd = null
            suspendCancellableCoroutine { continuation ->
                var resumed = false
                fun finishOnce() {
                    if (!resumed && continuation.isActive) {
                        resumed = true
                        preloadInterstitial()
                        continuation.resume(true)
                    }
                }

                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdShowedFullScreenContent() {
                        val shownAtUtc = clockMillis()
                        CoroutineScope(Dispatchers.IO).launch {
                            preferences.saveLastAdShownUtcMillis(shownAtUtc)
                        }
                    }

                    override fun onAdDismissedFullScreenContent() {
                        finishOnce()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        AndroidAppLog.w("AdMob", "Interstitial failed to show: ${adError.message}")
                        finishOnce()
                    }
                }
                ad.show(activity)
            }
        }
}
