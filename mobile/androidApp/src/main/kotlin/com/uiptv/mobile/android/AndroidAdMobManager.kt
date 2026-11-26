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
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.uiptv.mobile.shared.ads.AdCooldownPolicy
import com.uiptv.mobile.shared.logging.AndroidAppLog
import com.uiptv.mobile.shared.playback.PlaybackLaunchResult
import com.uiptv.mobile.shared.settings.AndroidPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private var consentUpdateStarted = false
    @Volatile private var adsAllowedByConsent = false
    @Volatile private var isProAccount = false
    private var consentInformation: ConsentInformation? = null
    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    fun requestConsentAndInitialize(activity: Activity, isPro: Boolean) {
        isProAccount = isPro
        if (isPro) {
            adsAllowedByConsent = false
            clearLoadedAds()
            return
        }
        val information = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation = information
        if (consentUpdateStarted) {
            applyConsentDecision(information)
            return
        }
        consentUpdateStarted = true
        information.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        AndroidAppLog.w("AdMob", "Consent form error: ${formError.message}")
                    }
                    applyConsentDecision(information)
                }
            },
            { requestError ->
                AndroidAppLog.w("AdMob", "Consent info update error: ${requestError.message}")
                applyConsentDecision(information)
            }
        )
        // Previously saved consent can allow requests while this session's update runs.
        applyConsentDecision(information)
    }

    suspend fun showPrivacyOptions(activity: Activity): Boolean =
        suspendCancellableCoroutine { continuation ->
            adsAllowedByConsent = false
            clearLoadedAds()
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
                consentInformation?.let(::applyConsentDecision)
                if (continuation.isActive) continuation.resume(formError == null)
            }
        }

    private fun applyConsentDecision(information: ConsentInformation) {
        updatePrivacyOptionsRequirement(information)
        adsAllowedByConsent = information.canRequestAds() && !isProAccount
        if (adsAllowedByConsent) initializeAndPreload(false) else clearLoadedAds()
    }

    private fun updatePrivacyOptionsRequirement(information: ConsentInformation) {
        _privacyOptionsRequired.value = information.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    fun initializeAndPreload(isPro: Boolean) {
        if (isPro) {
            isProAccount = true
            adsAllowedByConsent = false
            clearLoadedAds()
            return
        }
        if (!adsAllowedByConsent) return
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
        if (!adsAllowedByConsent || isProAccount || interstitialAd != null || isLoadingAd) return
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
                    if (!adsAllowedByConsent || isProAccount) return
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
        // Keep the ad frequency cap identical in debug, test-ads, and production builds.
        val cooldownMinutes = BuildConfig.AD_COOLDOWN_MINUTES
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
