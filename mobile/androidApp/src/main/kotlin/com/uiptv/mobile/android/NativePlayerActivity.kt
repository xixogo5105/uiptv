package com.uiptv.mobile.android

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.uiptv.mobile.shared.browse.BrowseMode
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import com.uiptv.mobile.shared.playback.PlaybackTarget
import com.uiptv.mobile.shared.settings.AndroidDataStorePreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NativePlayerActivity : Activity() {
    private var player: ExoPlayer? = null
    private var playbackStarted = false
    private val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentTarget: PlaybackTarget? = null
    private var currentBingeSession: AndroidBingeWatchSession? = null
    private var currentBingeIndex = -1
    private var markedTargetKey = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val streamUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        val session = intent.getStringExtra(EXTRA_BINGE_SESSION_ID)
            .orEmpty()
            .takeIf { it.isNotBlank() }
            ?.let(AndroidBingeWatchSessionStore::get)
        if (streamUrl.isBlank() && session == null) {
            finish()
            return
        }

        val exoPlayer = ExoPlayer.Builder(this).build().also { created ->
            created.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        playbackStarted = true
                        currentTarget?.let(::markOpened)
                    } else if (playbackState == Player.STATE_ENDED && currentBingeSession != null) {
                        playBingeIndex(currentBingeIndex + 1)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    setContentView(
                        TextView(this@NativePlayerActivity).apply {
                            text = error.message ?: "Playback failed."
                            setPadding(32, 32, 32, 32)
                        }
                    )
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
        val exoPlayer = player ?: return
        val mimeType = mimeTypeOverride.ifBlank { target.mimeType() }
        exoPlayer.setMediaItem(
            MediaItem.Builder()
                .setUri(Uri.parse(target.url))
                .setMediaId(target.title.ifBlank { target.url })
                .also { builder ->
                    if (mimeType.isNotBlank() && mimeType != "video/*") {
                        builder.setMimeType(mimeType)
                    }
                    buildDrmConfiguration(target.drmType, target.drmLicenseUrl)?.let(builder::setDrmConfiguration)
                }
                .build()
        )
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
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
            seriesId = intent.getStringExtra(EXTRA_SERIES_ID).orEmpty(),
            seriesTitle = intent.getStringExtra(EXTRA_SERIES_TITLE).orEmpty(),
            episodeId = intent.getStringExtra(EXTRA_EPISODE_ID).orEmpty(),
            season = intent.getStringExtra(EXTRA_SEASON).orEmpty(),
            episodeNumber = intent.getStringExtra(EXTRA_EPISODE_NUMBER).orEmpty()
        )
    }

    private fun buildDrmConfiguration(drmType: String, drmLicenseUrl: String): MediaItem.DrmConfiguration? {
        if (drmLicenseUrl.isBlank()) {
            return null
        }
        val scheme = when {
            drmType.equals("widevine", ignoreCase = true) ||
                drmType.equals("com.widevine.alpha", ignoreCase = true) -> C.WIDEVINE_UUID
            drmType.equals("clearkey", ignoreCase = true) ||
                drmType.equals("org.w3.clearkey", ignoreCase = true) -> C.CLEARKEY_UUID
            else -> null
        } ?: return null
        return MediaItem.DrmConfiguration.Builder(scheme)
            .setLicenseUri(drmLicenseUrl)
            .build()
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MIME_TYPE = "mime_type"
        const val EXTRA_DRM_TYPE = "drm_type"
        const val EXTRA_DRM_LICENSE_URL = "drm_license_url"
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
