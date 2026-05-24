package com.uiptv.mobile.android

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.PlayerView
import com.uiptv.mobile.shared.browse.BrowseMode
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import com.uiptv.mobile.shared.playback.PlaybackTarget
import com.uiptv.mobile.shared.playback.isDrmProtected
import com.uiptv.mobile.shared.settings.AndroidDataStorePreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class NativePlayerActivity : Activity() {
    private var player: ExoPlayer? = null
    private var playbackStarted = false
    private val playerHandler = Handler(Looper.getMainLooper())
    private val retryPlaybackRunnable = Runnable { retryCurrentStream() }
    private val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentTarget: PlaybackTarget? = null
    private var currentMimeTypeOverride = ""
    private var currentBingeSession: AndroidBingeWatchSession? = null
    private var currentBingeIndex = -1
    private var markedTargetKey = ""
    private var playbackRetryAttempt = 0

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        val streamUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        val session = intent.getStringExtra(EXTRA_BINGE_SESSION_ID)
            .orEmpty()
            .takeIf { it.isNotBlank() }
            ?.let(AndroidBingeWatchSessionStore::get)
        if (streamUrl.isBlank() && session == null) {
            finish()
            return
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(NativeLiveLoadRetryCount))
            .setDrmSessionManagerProvider(TargetDrmSessionManagerProvider { currentTarget })
        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .also { created ->
                created.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            playbackStarted = true
                            playbackRetryAttempt = 0
                            currentTarget?.let(::markOpened)
                        } else if (playbackState == Player.STATE_ENDED && currentBingeSession != null) {
                            playBingeIndex(currentBingeIndex + 1)
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        scheduleCurrentStreamRetry(error)
                    }
                })
                created.playWhenReady = true
            }
        player = exoPlayer
        setContentView(
            PlayerView(this).apply {
                this.player = exoPlayer
                keepScreenOn = true
            }
        )
        if (session != null) {
            currentBingeSession = session
            playBingeIndex(session.startIndex)
        } else {
            val target = targetFromIntent()?.copy(url = streamUrl) ?: run {
                finish()
                return
            }
            currentTarget = target
            setMediaTarget(target, intent.getStringExtra(EXTRA_MIME_TYPE).orEmpty())
        }
    }

    override fun onDestroy() {
        val currentPlayer = player
        if (playbackStarted || (currentPlayer?.currentPosition ?: 0L) > 0L) {
            (currentTarget ?: targetFromIntent())?.let(::markOpened)
        }
        if (isFinishing) {
            intent.getStringExtra(EXTRA_BINGE_SESSION_ID).orEmpty().takeIf { it.isNotBlank() }?.let(AndroidBingeWatchSessionStore::remove)
        }
        playerHandler.removeCallbacksAndMessages(null)
        playbackScope.cancel()
        currentPlayer?.release()
        player = null
        super.onDestroy()
    }

    private fun playBingeIndex(index: Int) {
        val session = currentBingeSession ?: return
        if (index >= session.targets.size) {
            finish()
            return
        }
        currentBingeIndex = index
        playbackStarted = false
        val rawTarget = session.targets[index]
        playbackScope.launch {
            val resolved = resolveForPlayback(rawTarget)
            if (!resolved.url.isPlayableNetworkUrl()) {
                setContentView(
                    TextView(this@NativePlayerActivity).apply {
                        text = "Unable to resolve ${rawTarget.title}."
                        setPadding(32, 32, 32, 32)
                    }
                )
                return@launch
            }
            currentTarget = resolved
            setMediaTarget(resolved)
        }
    }

    private suspend fun resolveForPlayback(target: PlaybackTarget): PlaybackTarget =
        withContext(Dispatchers.IO) {
            val databaseHelper = AndroidUiptvDatabaseHelper(this@NativePlayerActivity)
            try {
                AndroidPlaybackCoordinator(
                    context = this@NativePlayerActivity,
                    preferences = AndroidDataStorePreferencesRepository(this@NativePlayerActivity),
                    databaseHelper = databaseHelper
                ).resolvePlayableTarget(target)
            } finally {
                databaseHelper.close()
            }
        }

    private fun setMediaTarget(target: PlaybackTarget, mimeTypeOverride: String = "") {
        currentTarget = target
        currentMimeTypeOverride = mimeTypeOverride
        playbackRetryAttempt = 0
        playerHandler.removeCallbacks(retryPlaybackRunnable)
        prepareMediaTarget(target, mimeTypeOverride)
    }

    private fun prepareMediaTarget(target: PlaybackTarget, mimeTypeOverride: String = "") {
        val exoPlayer = player ?: return
        val mimeType = mimeTypeOverride.ifBlank { target.mimeType() }
        applySecureWindowPolicy(target)
        exoPlayer.setMediaItem(
            MediaItem.Builder()
                .setUri(Uri.parse(target.url))
                .setMediaId(target.title.ifBlank { target.url })
                .also { builder ->
                    if (mimeType.isNotBlank() && mimeType != "video/*") {
                        builder.setMimeType(mimeType)
                    }
                    buildDrmConfiguration(target)?.let(builder::setDrmConfiguration)
                }
                .build()
        )
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    private fun scheduleCurrentStreamRetry(error: PlaybackException) {
        val target = currentTarget
        if (target == null || !target.url.isPlayableNetworkUrl() || isFinishing) {
            showPlaybackError(error)
            return
        }
        if (playbackRetryAttempt >= MaxNativePlaybackRetries) {
            Log.w(LogTag, "Android Media playback failed after $playbackRetryAttempt retries", error)
            showPlaybackError(error)
            return
        }
        playbackRetryAttempt += 1
        val delayMs = minOf(
            NativePlaybackRetryMaxDelayMs,
            NativePlaybackRetryBaseDelayMs * playbackRetryAttempt
        )
        Log.w(LogTag, "Android Media source error; retrying stream in ${delayMs}ms attempt=$playbackRetryAttempt", error)
        playerHandler.removeCallbacks(retryPlaybackRunnable)
        playerHandler.postDelayed(retryPlaybackRunnable, delayMs)
    }

    private fun retryCurrentStream() {
        val target = currentTarget ?: return
        val exoPlayer = player ?: return
        runCatching {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
        prepareMediaTarget(target, currentMimeTypeOverride)
    }

    private fun showPlaybackError(error: PlaybackException) {
        setContentView(
            TextView(this).apply {
                text = error.message ?: "Playback failed."
                setPadding(32, 32, 32, 32)
            }
        )
    }

    private fun markOpened(target: PlaybackTarget) {
        val key = listOf(target.accountId, target.mode.name, target.categoryProviderId, target.channelId, target.episodeId).joinToString("|")
        if (key == markedTargetKey) {
            return
        }
        markedTargetKey = key
        val databaseHelper = AndroidUiptvDatabaseHelper(this)
        try {
            AndroidPlaybackWatchStateStore(databaseHelper).markOpened(target)
        } finally {
            databaseHelper.close()
        }
    }

    private fun targetFromIntent(): PlaybackTarget? {
        val mode = runCatching {
            BrowseMode.valueOf(intent.getStringExtra(EXTRA_MODE).orEmpty())
        }.getOrNull() ?: return null
        return PlaybackTarget(
            accountId = intent.getLongExtra(EXTRA_ACCOUNT_ID, 0L),
            accountName = intent.getStringExtra(EXTRA_ACCOUNT_NAME).orEmpty(),
            mode = mode,
            categoryProviderId = intent.getStringExtra(EXTRA_CATEGORY_PROVIDER_ID).orEmpty(),
            categoryRowId = intent.getLongExtra(EXTRA_CATEGORY_ROW_ID, 0L),
            channelId = intent.getStringExtra(EXTRA_CHANNEL_ID).orEmpty(),
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            url = intent.getStringExtra(EXTRA_URL).orEmpty(),
            logo = intent.getStringExtra(EXTRA_LOGO).orEmpty(),
            drmType = intent.getStringExtra(EXTRA_DRM_TYPE).orEmpty(),
            drmLicenseUrl = intent.getStringExtra(EXTRA_DRM_LICENSE_URL).orEmpty(),
            clearKeysJson = intent.getStringExtra(EXTRA_CLEAR_KEYS_JSON).orEmpty(),
            inputstreamAddon = intent.getStringExtra(EXTRA_INPUTSTREAM_ADDON).orEmpty(),
            manifestType = intent.getStringExtra(EXTRA_MANIFEST_TYPE).orEmpty(),
            seriesId = intent.getStringExtra(EXTRA_SERIES_ID).orEmpty(),
            seriesTitle = intent.getStringExtra(EXTRA_SERIES_TITLE).orEmpty(),
            episodeId = intent.getStringExtra(EXTRA_EPISODE_ID).orEmpty(),
            season = intent.getStringExtra(EXTRA_SEASON).orEmpty(),
            episodeNumber = intent.getStringExtra(EXTRA_EPISODE_NUMBER).orEmpty()
        )
    }

    private fun applySecureWindowPolicy(target: PlaybackTarget) {
        if (target.isDrmProtected()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun buildDrmConfiguration(target: PlaybackTarget): MediaItem.DrmConfiguration? {
        val scheme = target.drmSchemeUuid() ?: return null
        return MediaItem.DrmConfiguration.Builder(scheme)
            .also { builder ->
                if (target.drmLicenseUrl.isNotBlank()) {
                    builder.setLicenseUri(target.drmLicenseUrl)
                }
            }
            .build()
    }

    companion object {
        private const val LogTag = "UIPTV-Native"
        private const val NativeLiveLoadRetryCount = 12
        private const val MaxNativePlaybackRetries = 6
        private const val NativePlaybackRetryBaseDelayMs = 1_500L
        private const val NativePlaybackRetryMaxDelayMs = 10_000L
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MIME_TYPE = "mime_type"
        const val EXTRA_DRM_TYPE = "drm_type"
        const val EXTRA_DRM_LICENSE_URL = "drm_license_url"
        const val EXTRA_CLEAR_KEYS_JSON = "clear_keys_json"
        const val EXTRA_INPUTSTREAM_ADDON = "inputstream_addon"
        const val EXTRA_MANIFEST_TYPE = "manifest_type"
        const val EXTRA_ACCOUNT_ID = "account_id"
        const val EXTRA_ACCOUNT_NAME = "account_name"
        const val EXTRA_MODE = "mode"
        const val EXTRA_CATEGORY_PROVIDER_ID = "category_provider_id"
        const val EXTRA_CATEGORY_ROW_ID = "category_row_id"
        const val EXTRA_CHANNEL_ID = "channel_id"
        const val EXTRA_LOGO = "logo"
        const val EXTRA_SERIES_ID = "series_id"
        const val EXTRA_SERIES_TITLE = "series_title"
        const val EXTRA_EPISODE_ID = "episode_id"
        const val EXTRA_SEASON = "season"
        const val EXTRA_EPISODE_NUMBER = "episode_number"
        const val EXTRA_BINGE_SESSION_ID = "binge_session_id"
    }
}

private fun PlaybackTarget.mimeType(): String =
    when {
        manifestType.equals("mpd", ignoreCase = true) -> "application/dash+xml"
        manifestType.equals("hls", ignoreCase = true) -> "application/x-mpegURL"
        url.contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
        url.contains(".mpd", ignoreCase = true) -> "application/dash+xml"
        else -> "video/*"
    }

private fun String.isPlayableNetworkUrl(): Boolean {
    val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase()
    return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
}

private fun PlaybackTarget.drmSchemeUuid(): UUID? =
    when {
        drmType.equals("widevine", ignoreCase = true) ||
            drmType.equals("com.widevine.alpha", ignoreCase = true) -> C.WIDEVINE_UUID
        drmType.equals("clearkey", ignoreCase = true) ||
            drmType.equals("org.w3.clearkey", ignoreCase = true) ||
            drmType.equals("com.clearkey.alpha", ignoreCase = true) -> C.CLEARKEY_UUID
        clearKeysJson.isNotBlank() -> C.CLEARKEY_UUID
        else -> null
    }

private class TargetDrmSessionManagerProvider(
    private val targetProvider: () -> PlaybackTarget?
) : DrmSessionManagerProvider {
    private val defaultProvider = DefaultDrmSessionManagerProvider()

    override fun get(mediaItem: MediaItem): DrmSessionManager {
        val clearKeyResponse = targetProvider()?.clearKeyLicenseResponse()
        if (clearKeyResponse != null) {
            return DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .build(LocalMediaDrmCallback(clearKeyResponse))
        }
        return defaultProvider.get(mediaItem)
    }
}

private fun PlaybackTarget.clearKeyLicenseResponse(): ByteArray? {
    if (clearKeysJson.isBlank() || drmSchemeUuid() != C.CLEARKEY_UUID) {
        return null
    }
    runCatching { JSONObject(clearKeysJson) }.getOrNull()
        ?.takeIf { (it.optJSONArray("keys")?.length() ?: 0) > 0 }
        ?.let { return it.toString().toByteArray(Charsets.UTF_8) }

    val clearKeys = parseClearKeys(clearKeysJson)
    if (clearKeys.isEmpty()) {
        return null
    }
    val keys = JSONArray()
    clearKeys.forEach { (kid, key) ->
        keys.put(
            JSONObject()
                .put("kty", "oct")
                .put("kid", clearKeyValueToBase64Url(kid))
                .put("k", clearKeyValueToBase64Url(key))
        )
    }
    return JSONObject()
        .put("keys", keys)
        .put("type", "temporary")
        .toString()
        .toByteArray(Charsets.UTF_8)
}

private fun parseClearKeys(raw: String): Map<String, String> {
    val value = raw.trim()
    if (value.isBlank()) {
        return emptyMap()
    }
    val parsedJson = runCatching { JSONObject(value) }.getOrNull()
    if (parsedJson != null) {
        if (parsedJson.has("keys")) {
            return emptyMap()
        }
        val keys = linkedMapOf<String, String>()
        parsedJson.keys().forEach { kid ->
            val key = parsedJson.optString(kid).trim()
            if (kid.isNotBlank() && key.isNotBlank()) {
                keys[kid] = key
            }
        }
        return keys
    }
    return value.split(";")
        .mapNotNull { pair ->
            val parts = pair.split(":", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                parts[0].trim() to parts[1].trim()
            } else {
                null
            }
        }
        .toMap()
}

private fun clearKeyValueToBase64Url(value: String): String {
    val trimmed = value.trim()
    val hex = trimmed.removePrefix("0x").removePrefix("0X")
    return if (hex.length % 2 == 0 && hex.matches(HexPattern)) {
        Base64.encodeToString(hexToBytes(hex), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    } else {
        trimmed.replace('+', '-').replace('/', '_').trimEnd('=')
    }
}

private fun hexToBytes(hex: String): ByteArray =
    ByteArray(hex.length / 2) { index ->
        hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }

private val HexPattern = Regex("[0-9a-fA-F]+")
