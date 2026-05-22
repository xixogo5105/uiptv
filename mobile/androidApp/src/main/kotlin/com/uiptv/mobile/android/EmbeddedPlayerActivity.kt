package com.uiptv.mobile.android

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Rational
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.uiptv.mobile.shared.browse.BrowseMode
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import com.uiptv.mobile.shared.playback.PlaybackTarget
import com.uiptv.mobile.shared.settings.AndroidDataStorePreferencesRepository
import com.uiptv.mobile.shared.settings.EmbeddedPlayerPreference
import java.io.BufferedInputStream
import java.io.StringReader
import java.lang.reflect.Field
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IVLCVout
import org.videolan.libvlc.util.HWDecoderUtil
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

class EmbeddedPlayerActivity : Activity() {
    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var audioManager: AudioManager
    private lateinit var rootLayout: FrameLayout
    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var controlsOverlay: LinearLayout
    private lateinit var playlistOverlay: LinearLayout
    private lateinit var feedbackView: TextView
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var messageView: TextView
    private lateinit var playPauseButton: ImageButton
    private lateinit var progressSeekBar: SeekBar
    private lateinit var streamInfoLabel: TextView
    private lateinit var titleLabel: TextView
    private lateinit var timeLabel: TextView
    private lateinit var zoomButton: ImageButton
    private lateinit var audioTrackButton: ImageButton
    private lateinit var subtitleTrackButton: ImageButton
    private lateinit var repeatButton: ImageButton
    private lateinit var muteButton: ImageButton
    private val overlayHandler = Handler(Looper.getMainLooper())
    private val playerCleanupExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "uiptv-vlc-cleanup").apply { isDaemon = true }
    }
    private val zoomModes = listOf(
        ZoomMode("Default", MediaPlayer.ScaleType.SURFACE_BEST_FIT, R.drawable.aspect_ratio),
        ZoomMode("Fill", MediaPlayer.ScaleType.SURFACE_FILL, R.drawable.aspect_ratio_fill),
        ZoomMode("16:9", MediaPlayer.ScaleType.SURFACE_16_9, R.drawable.aspect_ratio),
        ZoomMode("4:3", MediaPlayer.ScaleType.SURFACE_4_3, R.drawable.aspect_ratio),
        ZoomMode("Original", MediaPlayer.ScaleType.SURFACE_ORIGINAL, R.drawable.aspect_ratio_stretch)
    )
    private val hideControlsRunnable = Runnable {
        controlsOverlay.visibility = View.GONE
        controlsVisible = false
        setPlayerSurfaceTouchEnabled(true)
        enterImmersiveMode()
    }
    private val hideFeedbackRunnable = Runnable {
        feedbackView.visibility = View.GONE
    }
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            if (playerStopInProgress || stoppingForLifecycle || isFinishing) {
                return
            }
            updatePlaybackProgress()
            if (!playerStopInProgress && !stoppingForLifecycle && !isFinishing) {
                overlayHandler.postDelayed(this, ProgressUpdateMs)
            }
        }
    }
    private var playbackStarted = false
    private var streamLoadInProgress = false
    private var attached = false
    private var controlsVisible = false
    private var userSeeking = false
    private var repeatEnabled = false
    private var reconnectScheduled = false
    private var stoppingForLifecycle = false
    private var pendingPlaybackStart = false
    private var reconnectAttempt = 0
    private var audioStateRequestVersion = 0L
    private var audioFocusRequest: AudioFocusRequest? = null
    private var reconnectRunnable: Runnable? = null
    private var startupVolumeFallbackApplied = false
    private var muted = false
    private var audioTrackBeforeMute = UnknownTrackId
    private var systemVolumeBeforeMute = UnknownSystemVolume
    private var lastAudioStateApplyAt = 0L
    private var lastAudioEventSyncAt = 0L
    private var lastAudioTrackSnapshot = ""
    private var lastMutedAudioTrackSnapshot = ""
    private var adaptiveProbeGeneration = 0L
    @Volatile
    private var playerStopInProgress = false
    @Volatile
    private var lastKnownPlaybackPositionMs = 0L
    @Volatile
    private var lastKnownPlaybackLengthMs = 0L
    @Volatile
    private var lastKnownPlaying = false
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var twoFingerStartSpanX = 0f
    private var twoFingerStartSpanY = 0f
    private var initialBrightness = DefaultBrightness
    private var initialVolume = 0
    private var activeGesture = PlayerGesture.None
    private var zoomModeIndex = 0
    private var selectedVideoTrackId = UnknownTrackId
    @Volatile
    private var renderedVideoDetails: VideoTrackDetails? = null
    @Volatile
    private var adaptiveStreamInfo: AdaptiveStreamInfo? = null
    private var videoLayoutListenerWrapped = false
    private val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentTarget: PlaybackTarget? = null
    private var currentBingeSession: AndroidBingeWatchSession? = null
    private var currentBingeIndex = -1
    private var markedTargetKey = ""
    private val preferencesRepository by lazy { AndroidDataStorePreferencesRepository(this) }

    @Suppress("DEPRECATION")
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        volumeControlStream = AudioManager.STREAM_MUSIC

        currentBingeSession = intent.getStringExtra(NativePlayerActivity.EXTRA_BINGE_SESSION_ID)
            .orEmpty()
            .takeIf { it.isNotBlank() }
            ?.let(AndroidBingeWatchSessionStore::get)
        loadEmbeddedPlayerPreference()
        if (intent.getStringExtra(NativePlayerActivity.EXTRA_URL).orEmpty().isBlank() && currentBingeSession == null) {
            finish()
            return
        }

        videoLayout = VLCVideoLayout(this).apply {
            setOnTouchListener { _, event -> handlePlayerTouch(event) }
            isClickable = true
        }
        messageView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(160, 0, 0, 0))
            setPadding(24, 16, 24, 16)
            visibility = View.GONE
        }
        feedbackView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(16f)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = roundedBackground(Color.argb(190, 12, 16, 20), dp(18))
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        loadingSpinner = ProgressBar(this, null, android.R.attr.progressBarStyleLarge).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(Color.rgb(79, 216, 235))
            visibility = View.GONE
        }
        controlsOverlay = createControlsOverlay()
        playlistOverlay = createPlaylistOverlay()
        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(
                videoLayout,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            addView(
                controlsOverlay,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
            )
            addView(
                playlistOverlay,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                    leftMargin = dp(10)
                    rightMargin = dp(10)
                    bottomMargin = dp(138)
                }
            )
            addView(
                feedbackView,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            )
            addView(
                loadingSpinner,
                FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER)
            )
            addView(
                messageView,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
            )
        }
        setContentView(rootLayout)
        elevatePlayerOverlays()
        enterImmersiveMode()

        val options = arrayListOf(
            "--aout=$PreferredAudioOutput",
            "--http-reconnect",
            "--network-caching=1500",
            "--live-caching=1500",
            "--file-caching=1500",
            "--rtsp-tcp"
        )
        val createdLibVlc = LibVLC(this, options)
        val createdPlayer = MediaPlayer(createdLibVlc).apply {
            configureAudioOutput()
            setEventListener { event ->
                cachePlaybackEvent(event)
                when (event.type) {
                    MediaPlayer.Event.Opening -> {
                        runOnUiThread { keepLoadingVisible() }
                    }
                    MediaPlayer.Event.Buffering -> {
                        runOnUiThread { keepLoadingVisible() }
                    }
                    MediaPlayer.Event.Playing -> {
                        playbackStarted = true
                        reconnectAttempt = 0
                        reconnectScheduled = false
                        currentTarget?.let(::markOpened)
                        runOnUiThread {
                            syncAudioStateAtStartup()
                            keepLoadingVisible()
                            messageView.visibility = View.GONE
                            updatePlayPauseButton()
                            updateStreamInfo()
                        }
                    }
                    MediaPlayer.Event.Vout -> {
                        runOnUiThread {
                            if (event.voutCount > 0) {
                                completeStreamLoading()
                            } else {
                                keepLoadingVisible()
                            }
                            updateStreamInfo()
                        }
                    }
                    MediaPlayer.Event.TimeChanged -> {
                        runOnUiThread {
                            completeStreamLoadingIfClockStarted(event.timeChanged)
                        }
                    }
                    MediaPlayer.Event.LengthChanged -> {
                        runOnUiThread { updatePlaybackProgress() }
                    }
                    MediaPlayer.Event.ESAdded,
                    MediaPlayer.Event.ESDeleted,
                    MediaPlayer.Event.ESSelected -> {
                        runOnUiThread { handleElementaryStreamChanged(event) }
                    }
                    MediaPlayer.Event.Paused -> {
                        runOnUiThread {
                            completeStreamLoading()
                            updatePlayPauseButton()
                        }
                    }
                    MediaPlayer.Event.EndReached -> {
                        runOnUiThread {
                            completeStreamLoading()
                            updatePlayPauseButton()
                            if (!playNextBingeIfNeeded()) {
                                scheduleReconnectIfNeeded("Stream stopped")
                            }
                        }
                    }
                    MediaPlayer.Event.Stopped -> {
                        runOnUiThread {
                            completeStreamLoading()
                            updatePlayPauseButton()
                            scheduleReconnectIfNeeded("Stream stopped")
                        }
                    }
                    MediaPlayer.Event.EncounteredError -> {
                        runOnUiThread {
                            completeStreamLoading()
                            if (!scheduleReconnectIfNeeded("Stream error")) {
                                showMessage("Embedded player could not open this stream.")
                            }
                        }
                    }
                }
            }
        }
        libVlc = createdLibVlc
        mediaPlayer = createdPlayer
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveMode()
    }

    override fun onStart() {
        super.onStart()
        stoppingForLifecycle = false
        startPlayback()
        startProgressUpdates()
    }

    override fun onStop() {
        stoppingForLifecycle = true
        pendingPlaybackStart = false
        cancelReconnect()
        overlayHandler.removeCallbacks(updateProgressRunnable)
        val shouldStopPlayback = !isInPictureInPictureModeCompat()
        if (shouldStopPlayback) {
            beginPlayerStop()
        }
        if (attached && shouldStopPlayback) {
            if (!playerStopInProgress) {
                mediaPlayer?.detachViews()
            }
            attached = false
            videoLayoutListenerWrapped = false
            renderedVideoDetails = null
        }
        if (shouldStopPlayback) {
            stopPlayerInBackground(mediaPlayer)
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (playbackStarted || lastKnownPlaybackPositionMs > 0L) {
            (currentTarget ?: targetFromIntent())?.let(::markOpened)
        }
        if (isFinishing) {
            intent.getStringExtra(NativePlayerActivity.EXTRA_BINGE_SESSION_ID).orEmpty().takeIf { it.isNotBlank() }?.let(AndroidBingeWatchSessionStore::remove)
        }
        val playerToRelease = mediaPlayer
        val libVlcToRelease = libVlc
        mediaPlayer = null
        abandonAudioFocus()
        libVlc = null
        releasePlayerInBackground(playerToRelease, libVlcToRelease)
        playbackScope.cancel()
        overlayHandler.removeCallbacksAndMessages(null)
        playerCleanupExecutor.shutdown()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveMode()
        }
    }

    private fun startPlayback() {
        if (playerStopInProgress || stoppingForLifecycle || isFinishing) {
            pendingPlaybackStart = !stoppingForLifecycle && !isFinishing
            return
        }
        val createdLibVlc = libVlc ?: return
        val createdPlayer = mediaPlayer ?: return
        val session = currentBingeSession
        if (session != null && currentBingeIndex < 0 && currentTarget == null) {
            playBingeIndex(session.startIndex)
            return
        }
        val target = currentTarget ?: targetFromIntent()
        val streamUrl = target?.url.orEmpty()
        if (streamUrl.isBlank()) {
            if (session != null) {
                playBingeIndex(if (currentBingeIndex >= 0) currentBingeIndex else session.startIndex)
            } else {
                finish()
            }
            return
        }
        currentTarget = target
        if (muted) {
            applySystemMuteForApp()
        }
        if (!attached) {
            createdPlayer.attachViews(videoLayout, null, false, false)
            attached = true
            setPlayerSurfaceTouchEnabled(enabled = true)
            videoLayout.post { setPlayerSurfaceTouchEnabled(enabled = !controlsVisible) }
            installVideoLayoutObserver(createdPlayer)
        }
        createdPlayer.configureAudioOutput()
        requestAudioFocus()
        selectedVideoTrackId = UnknownTrackId
        renderedVideoDetails = null
        resetAdaptiveStreamInfo()
        adaptiveStreamInfo = initialAdaptiveStreamInfo(streamUrl)
        updateStreamInfo()
        startAdaptiveStreamProbe(streamUrl)
        lastAudioStateApplyAt = 0L
        lastAudioEventSyncAt = 0L
        lastAudioTrackSnapshot = ""
        lastMutedAudioTrackSnapshot = ""
        resetCachedPlaybackState()
        beginStreamLoading()
        ensureAudibleSystemVolume()
        val audioRequestVersion = markAudioStateSyncRequested()
        val media = Media(createdLibVlc, Uri.parse(streamUrl)).apply {
            setHWDecoderEnabled(true, false)
            addOption(":http-reconnect")
            addOption(":http-user-agent=$ChromeUserAgent")
            addOption(":network-caching=1500")
            addOption(":live-caching=1500")
            addOption(":file-caching=1500")
            addOption(":rtsp-tcp")
            addOption(":audio-time-stretch")
        }
        createdPlayer.setVideoScale(zoomModes[zoomModeIndex].scaleType)
        createdPlayer.media = media
        media.release()
        applyDesiredAudioState()
        scheduleAudioStateStartupSync(audioRequestVersion)
        createdPlayer.play()
        startProgressUpdates()
    }

    private fun playNextBingeIfNeeded(): Boolean {
        val session = currentBingeSession ?: return false
        hidePlaylistOverlay()
        val nextIndex = currentBingeIndex + 1
        if (nextIndex >= session.targets.size) {
            finish()
            return true
        }
        playBingeIndex(nextIndex)
        return true
    }

    private fun playBingeIndex(index: Int) {
        val session = currentBingeSession ?: return
        if (index >= session.targets.size) {
            finish()
            return
        }
        currentBingeIndex = index
        currentTarget = null
        playbackStarted = false
        reconnectAttempt = 0
        reconnectScheduled = false
        cancelReconnect()
        beginStreamLoading()
        stopPlayerForRestart {
            val rawTarget = session.targets[index]
            playbackScope.launch {
                val resolved = resolveForPlayback(rawTarget)
                if (!resolved.url.isPlayableNetworkUrl()) {
                    completeStreamLoading()
                    showMessage("Unable to resolve ${rawTarget.title}.")
                    return@launch
                }
                currentTarget = resolved
                startPlayback()
            }
        }
    }

    private suspend fun resolveForPlayback(target: PlaybackTarget): PlaybackTarget =
        withContext(Dispatchers.IO) {
            val databaseHelper = AndroidUiptvDatabaseHelper(this@EmbeddedPlayerActivity)
            try {
                AndroidPlaybackCoordinator(
                    context = this@EmbeddedPlayerActivity,
                    preferences = preferencesRepository,
                    databaseHelper = databaseHelper
                ).resolvePlayableTarget(target)
            } finally {
                databaseHelper.close()
            }
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

    private fun handlePlayerTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                initialBrightness = currentBrightness()
                initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                activeGesture = PlayerGesture.None
                overlayHandler.removeCallbacks(hideControlsRunnable)
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    startTwoFingerZoomGesture(event)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeGesture == PlayerGesture.Zoom) {
                    handleTwoFingerZoomGesture(event)
                    return true
                }
                if (event.pointerCount >= 2) {
                    startTwoFingerZoomGesture(event)
                    return true
                }
                val dx = event.x - touchStartX
                val dy = event.y - touchStartY
                if (activeGesture == PlayerGesture.None) {
                    if (abs(dy) <= GestureSlopPx || abs(dy) <= abs(dx)) {
                        return true
                    }
                    activeGesture = if (touchStartX < videoLayout.width / 2f) {
                        PlayerGesture.Brightness
                    } else {
                        PlayerGesture.Volume
                    }
                    controlsOverlay.visibility = View.GONE
                    controlsVisible = false
                    setPlayerSurfaceTouchEnabled(true)
                }
                val delta = (-dy / videoLayout.height.coerceAtLeast(1)).coerceIn(-1f, 1f)
                when (activeGesture) {
                    PlayerGesture.Brightness -> setBrightness(initialBrightness + delta)
                    PlayerGesture.Volume -> setVolumeLevel(initialVolume + (delta * maxMusicVolume()).roundToInt())
                    PlayerGesture.Zoom -> Unit
                    PlayerGesture.None -> Unit
                }
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                val wasTap = activeGesture == PlayerGesture.None &&
                    abs(event.x - touchStartX) < TapSlopPx &&
                    abs(event.y - touchStartY) < TapSlopPx
                if (wasTap) {
                    toggleControls()
                } else {
                    hidePlaylistOverlay()
                    enterImmersiveMode()
                }
                activeGesture = PlayerGesture.None
                return true
            }
        }
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setPlayerSurfaceTouchEnabled(enabled: Boolean) {
        if (::videoLayout.isInitialized) {
            setPlayerTouchHandlers(videoLayout, enabled)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setPlayerTouchHandlers(view: View, enabled: Boolean) {
        view.setOnTouchListener(if (enabled) View.OnTouchListener { _, event -> handlePlayerTouch(event) } else null)
        view.isClickable = enabled
        view.isFocusable = enabled
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                setPlayerTouchHandlers(view.getChildAt(index), enabled)
            }
        }
    }

    private fun elevatePlayerOverlays() {
        listOfNotNull(
            if (::controlsOverlay.isInitialized) controlsOverlay else null,
            if (::playlistOverlay.isInitialized) playlistOverlay else null,
            if (::messageView.isInitialized) messageView else null,
            if (::feedbackView.isInitialized) feedbackView else null,
            if (::loadingSpinner.isInitialized) loadingSpinner else null
        ).forEach { it.bringToFront() }
    }

    private fun startTwoFingerZoomGesture(event: MotionEvent) {
        twoFingerStartSpanX = twoFingerSpanX(event)
        twoFingerStartSpanY = twoFingerSpanY(event)
        activeGesture = PlayerGesture.Zoom
        controlsOverlay.visibility = View.GONE
        controlsVisible = false
        setPlayerSurfaceTouchEnabled(true)
        overlayHandler.removeCallbacks(hideControlsRunnable)
    }

    private fun handleTwoFingerZoomGesture(event: MotionEvent) {
        if (event.pointerCount < 2) {
            return
        }
        val spanX = twoFingerSpanX(event)
        val spanY = twoFingerSpanY(event)
        val spanDeltaX = spanX - twoFingerStartSpanX
        val spanDeltaY = spanY - twoFingerStartSpanY
        if (abs(spanDeltaX) < dp(TwoFingerZoomSlopDp) || abs(spanDeltaX) < abs(spanDeltaY)) {
            return
        }
        val targetZoomMode = if (spanDeltaX > 0f) {
            FillZoomModeIndex
        } else {
            DefaultZoomModeIndex
        }
        if (zoomModeIndex != targetZoomMode) {
            applyZoomMode(targetZoomMode)
        }
        twoFingerStartSpanX = spanX
        twoFingerStartSpanY = spanY
    }

    private fun twoFingerSpanX(event: MotionEvent): Float =
        abs(event.getX(0) - event.getX(1))

    private fun twoFingerSpanY(event: MotionEvent): Float =
        abs(event.getY(0) - event.getY(1))

    private fun createControlsOverlay(): LinearLayout {
        val isLivePlayback = targetFromIntent()?.mode == BrowseMode.LIVE
        titleLabel = TextView(this).apply {
            text = intent.getStringExtra(NativePlayerActivity.EXTRA_TITLE).orEmpty().ifBlank { "Embedded player" }
            setTextColor(Color.WHITE)
            setTextSize(14f)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            maxLines = 1
        }
        val closeButton = controlButton(CloseIcon) { finish() }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(titleLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(closeButton)
        }
        streamInfoLabel = TextView(this).apply {
            text = buildStreamInfoLabel()
            setTextColor(Color.rgb(174, 184, 194))
            setTextSize(11f)
            maxLines = 1
        }
        progressSeekBar = SeekBar(this).apply {
            max = ProgressBarMax
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(79, 216, 235))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.argb(150, 90, 102, 114))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = Unit

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    userSeeking = true
                    overlayHandler.removeCallbacks(hideControlsRunnable)
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val player = mediaPlayer
                    if (player != null && canUsePlayer(player) && lastKnownPlaybackLengthMs > 0L) {
                        player.setPosition((seekBar?.progress ?: 0).toFloat() / ProgressBarMax)
                    }
                    userSeeking = false
                    updatePlaybackProgress()
                    scheduleControlsHide()
                }
            })
        }
        timeLabel = TextView(this).apply {
            text = LiveTimeLabel
            setTextColor(Color.rgb(209, 228, 255))
            setTextSize(11f)
            gravity = Gravity.CENTER
            minWidth = dp(76)
        }
        val progressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(progressSeekBar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(timeLabel)
        }
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            playPauseButton = iconControlButton(R.drawable.video_player_pause, "Pause") { togglePlayPause() }
            addView(playPauseButton, compactControlLayoutParams())
            addView(iconControlButton(R.drawable.video_player_stop, "Stop") { finish() }, compactControlLayoutParams())
            if (!isLivePlayback) {
                addView(iconControlButton(R.drawable.video_player_reverse_rewind, "Rewind 15 seconds") { seekBySeconds(-SeekStepSeconds) }, compactControlLayoutParams())
                addView(iconControlButton(R.drawable.video_player_fast_forward, "Fast forward 15 seconds") { seekBySeconds(SeekStepSeconds) }, compactControlLayoutParams())
            }
            if (currentBingeSession != null) {
                addView(controlButton(PlaylistIcon) { togglePlaylistOverlay() }, compactControlLayoutParams())
            }
            muteButton = iconControlButton(R.drawable.mute_off, "Mute") { toggleMute() }
            updateMuteButton()
            addView(muteButton, compactControlLayoutParams())
            repeatButton = iconControlButton(R.drawable.repeat_off, "Repeat") { toggleRepeat() }
            updateRepeatButton()
            addView(repeatButton, compactControlLayoutParams())
            addView(iconControlButton(R.drawable.reload, "Reload stream") { reloadCurrentStream() }, compactControlLayoutParams())
            zoomButton = iconControlButton(zoomModes[zoomModeIndex].iconRes, "Zoom ${zoomModes[zoomModeIndex].label}") { cycleZoomMode() }
            addView(zoomButton, compactControlLayoutParams())
            if (isPictureInPictureAvailable()) {
                addView(iconControlButton(R.drawable.picture_in_picture, "Picture in picture") { enterPictureInPicture() }, compactControlLayoutParams())
            }
            audioTrackButton = iconControlButton(R.drawable.audio_track, "Audio tracks") { showAudioTrackMenu(audioTrackButton) }
            addView(audioTrackButton, compactControlLayoutParams())
            subtitleTrackButton = iconControlButton(R.drawable.subtitle_track, "Subtitle tracks") { showSubtitleTrackMenu(subtitleTrackButton) }
            addView(subtitleTrackButton, compactControlLayoutParams())
            updateTrackMenuButtons()
        }
        val controlsScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(buttonRow)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = roundedBackground(Color.argb(175, 12, 16, 20), dp(0))
            visibility = View.GONE
            addView(titleRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(streamInfoLabel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            })
            addView(progressRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            })
            addView(controlsScroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            })
        }
    }

    private fun createPlaylistOverlay(): LinearLayout {
        val session = currentBingeSession
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(10))
        }
        if (session != null) {
            content.addView(
                TextView(this).apply {
                    text = session.seriesTitle.ifBlank { "Playlist" }
                    setTextColor(Color.WHITE)
                    setTextSize(13f)
                    setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                    maxLines = 1
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6)
                }
            )
            session.targets.forEachIndexed { index, target ->
                content.addView(playlistRow(index, target))
            }
        }
        val scroller = ScrollView(this).apply {
            isFillViewport = false
            addView(content)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Color.argb(205, 12, 16, 20), dp(12), Color.argb(100, 79, 216, 235))
            visibility = View.GONE
            addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)))
        }
    }

    private fun playlistRow(index: Int, target: PlaybackTarget): TextView =
        TextView(this).apply {
            text = playlistRowLabel(index, target)
            setTextColor(Color.WHITE)
            setTextSize(13f)
            maxLines = 2
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = roundedBackground(
                color = if (index == currentBingeIndex) Color.argb(220, 34, 83, 93) else Color.argb(135, 37, 45, 54),
                radius = dp(8),
                strokeColor = if (index == currentBingeIndex) Color.rgb(79, 216, 235) else null
            )
            isClickable = true
            isFocusable = true
            setOnClickListener {
                hidePlaylistOverlay()
                if (index != currentBingeIndex) {
                    showFeedback("Opening ${target.title.ifBlank { "playlist item" }}")
                    playBingeIndex(index)
                }
                scheduleControlsHide()
            }
        }.also { row ->
            row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
        }

    private fun playlistRowLabel(index: Int, target: PlaybackTarget): String {
        val episode = listOf(target.season, target.episodeNumber)
            .filter { it.isNotBlank() }
            .joinToString("x")
            .takeIf { it.isNotBlank() }
        val prefix = episode ?: (index + 1).toString()
        return "$prefix  ${target.title.ifBlank { "Untitled" }}"
    }

    private fun togglePlaylistOverlay() {
        if (!::playlistOverlay.isInitialized || currentBingeSession == null) {
            return
        }
        elevatePlayerOverlays()
        playlistOverlay.visibility = if (playlistOverlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        overlayHandler.removeCallbacks(hideControlsRunnable)
    }

    private fun hidePlaylistOverlay() {
        if (::playlistOverlay.isInitialized) {
            playlistOverlay.visibility = View.GONE
        }
    }

    private fun isPictureInPictureAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun isInPictureInPictureModeCompat(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode

    private fun enterPictureInPicture() {
        if (!isPictureInPictureAvailable()) {
            showFeedback("PiP unavailable")
            return
        }
        hidePlaylistOverlay()
        controlsOverlay.visibility = View.GONE
        controlsVisible = false
        overlayHandler.removeCallbacks(hideControlsRunnable)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(currentVideoAspectRatio())
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                enterPictureInPictureMode()
            }
        }.onFailure {
            Log.w(LogTag, "Unable to enter picture-in-picture", it)
            showFeedback("PiP unavailable")
        }
    }

    private fun currentVideoAspectRatio(): Rational {
        val width = videoLayout.width.takeIf { it > 0 } ?: rootLayout.width.takeIf { it > 0 } ?: 16
        val height = videoLayout.height.takeIf { it > 0 } ?: rootLayout.height.takeIf { it > 0 } ?: 9
        return Rational(width.coerceAtLeast(1), height.coerceAtLeast(1))
    }

    private fun controlButton(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(14f)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            minimumWidth = dp(ControlButtonSizeDp)
            minimumHeight = dp(ControlButtonSizeDp)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = controlButtonBackground(active = false)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                onClick()
                scheduleControlsHide()
            }
        }

    private fun iconControlButton(drawableRes: Int, description: String, onClick: () -> Unit): ImageButton =
        ImageButton(this).apply {
            contentDescription = description
            background = controlButtonBackground(active = false)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(ControlIconPaddingDp), dp(ControlIconPaddingDp), dp(ControlIconPaddingDp), dp(ControlIconPaddingDp))
            minimumWidth = dp(ControlButtonSizeDp)
            minimumHeight = dp(ControlButtonSizeDp)
            isClickable = true
            isFocusable = true
            setButtonIcon(this, drawableRes)
            setOnClickListener {
                onClick()
                scheduleControlsHide()
            }
        }

    private fun setButtonIcon(button: ImageButton, drawableRes: Int) {
        val drawable = resources.getDrawable(drawableRes, theme).mutate()
        drawable.setTint(Color.WHITE)
        button.setImageDrawable(drawable)
        button.imageTintList = ColorStateList.valueOf(Color.WHITE)
        button.minimumWidth = dp(ControlButtonSizeDp)
        button.minimumHeight = dp(ControlButtonSizeDp)
    }

    private fun compactControlLayoutParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dp(ControlButtonSizeDp), dp(ControlButtonSizeDp)).apply {
            leftMargin = dp(4)
            rightMargin = dp(4)
        }

    private fun controlButtonBackground(active: Boolean): GradientDrawable =
        roundedBackground(
            color = if (active) Color.argb(240, 34, 83, 93) else Color.argb(230, 37, 45, 54),
            radius = dp(ControlButtonSizeDp / 2),
            strokeColor = if (active) Color.rgb(79, 216, 235) else Color.argb(120, 79, 216, 235)
        )

    private fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (!canUsePlayer(player)) {
            return
        }
        if (lastKnownPlaying) {
            player.pause()
            lastKnownPlaying = false
        } else {
            requestAudioFocus()
            syncAudioStateAtStartup()
            player.play()
            lastKnownPlaying = true
        }
        updatePlayPauseButton()
    }

    private fun reloadCurrentStream() {
        cancelReconnect()
        reconnectAttempt = 0
        reconnectScheduled = false
        playbackStarted = false
        selectedVideoTrackId = UnknownTrackId
        messageView.visibility = View.GONE
        hidePlaylistOverlay()
        showFeedback("Reloading")
        stopPlayerForRestart {
            val session = currentBingeSession
            if (currentTarget == null && session != null) {
                playBingeIndex(if (currentBingeIndex >= 0) currentBingeIndex else session.startIndex)
            } else {
                startPlayback()
            }
        }
    }

    private fun toggleMute() {
        muted = !muted
        if (!muted) {
            if (!restoreSystemVolumeAfterAppMute()) {
                ensureAudibleSystemVolume(force = true)
            }
        } else {
            applySystemMuteForApp()
        }
        applyDesiredAudioState(force = true)
        updateMuteButton()
        saveEmbeddedPlayerPreference()
        showFeedback(if (muted) "Muted" else "Unmuted")
    }

    private fun updateMuteButton() {
        if (!::muteButton.isInitialized) {
            return
        }
        setButtonIcon(muteButton, if (muted) R.drawable.mute_on else R.drawable.mute_off)
        muteButton.contentDescription = if (muted) "Unmute" else "Mute"
        muteButton.background = controlButtonBackground(active = muted)
    }

    private fun toggleRepeat() {
        repeatEnabled = !repeatEnabled
        reconnectAttempt = 0
        updateRepeatButton()
        if (!repeatEnabled) {
            cancelReconnect()
        }
        saveEmbeddedPlayerPreference()
        showFeedback(if (repeatEnabled) "Repeat reconnect on" else "Repeat reconnect off")
    }

    private fun loadEmbeddedPlayerPreference() {
        playbackScope.launch {
            runCatching { preferencesRepository.load().embeddedPlayerPreference }
                .onSuccess { preference ->
                    repeatEnabled = preference.repeatReconnect
                    muted = preference.muted
                    updateRepeatButton()
                    updateMuteButton()
                    applyDesiredAudioState()
                }
        }
    }

    private fun saveEmbeddedPlayerPreference() {
        val preference = EmbeddedPlayerPreference(
            repeatReconnect = repeatEnabled,
            muted = muted
        )
        playbackScope.launch {
            runCatching { preferencesRepository.saveEmbeddedPlayerPreference(preference) }
        }
    }

    private fun updateRepeatButton() {
        if (!::repeatButton.isInitialized) {
            return
        }
        setButtonIcon(repeatButton, if (repeatEnabled) R.drawable.repeat_on else R.drawable.repeat_off)
        repeatButton.contentDescription = if (repeatEnabled) "Repeat on" else "Repeat off"
        repeatButton.background = controlButtonBackground(active = repeatEnabled)
    }

    private fun seekBySeconds(seconds: Int) {
        val player = mediaPlayer ?: return
        if (!canUsePlayer(player)) {
            return
        }
        val length = lastKnownPlaybackLengthMs
        if (length <= 0L) {
            showFeedback(LiveTimeLabel)
            return
        }
        val target = (lastKnownPlaybackPositionMs + seconds * 1_000L).coerceIn(0L, length)
        player.setTime(target)
        lastKnownPlaybackPositionMs = target
        updatePlaybackProgress()
    }

    private fun startProgressUpdates() {
        if (playerStopInProgress || stoppingForLifecycle || isFinishing) {
            return
        }
        overlayHandler.removeCallbacks(updateProgressRunnable)
        overlayHandler.post(updateProgressRunnable)
    }

    private fun updatePlaybackProgress() {
        if (playerStopInProgress || mediaPlayer == null) {
            return
        }
        if (!::progressSeekBar.isInitialized || !::timeLabel.isInitialized) {
            return
        }
        val length = lastKnownPlaybackLengthMs
        val current = lastKnownPlaybackPositionMs.coerceAtLeast(0L)
        completeStreamLoadingIfClockStarted(current)
        if (controlsVisible) {
            updateStreamInfo()
        } else {
            updateTitleLabel()
        }
        if (length > 0L) {
            progressSeekBar.isEnabled = true
            if (!userSeeking) {
                progressSeekBar.progress = ((current.coerceAtMost(length) * ProgressBarMax) / length).toInt()
            }
            timeLabel.text = "${formatTime(current)} / ${formatTime(length)}"
        } else {
            progressSeekBar.isEnabled = false
            if (!userSeeking) {
                progressSeekBar.progress = 0
            }
            timeLabel.text = LiveTimeLabel
        }
        updatePlayPauseButton()
    }

    private fun updatePlayPauseButton() {
        if (!::playPauseButton.isInitialized) {
            return
        }
        val isPlaying = lastKnownPlaying && !playerStopInProgress
        setButtonIcon(playPauseButton, if (isPlaying) R.drawable.video_player_pause else R.drawable.video_player_play)
        playPauseButton.contentDescription = if (isPlaying) "Pause" else "Play"
    }

    private fun scheduleReconnectIfNeeded(reason: String): Boolean {
        if (!repeatEnabled || playerStopInProgress || stoppingForLifecycle || isFinishing || !playbackStarted) {
            return false
        }
        if (reconnectScheduled) {
            return true
        }
        if (reconnectAttempt >= MaxReconnectAttempts) {
            showMessage("Failed to reconnect after $MaxReconnectAttempts attempts.")
            return true
        }
        reconnectAttempt += 1
        reconnectScheduled = true
        beginStreamLoading()
        val delayMs = if (reconnectAttempt == 1) 0L else ReconnectDelayMs
        showMessage(
            if (delayMs == 0L) {
                "$reason. Reconnecting..."
            } else {
                "$reason. Reconnecting in ${delayMs / 1_000L}s..."
            }
        )
        val task = Runnable {
            reconnectScheduled = false
            if (!repeatEnabled || stoppingForLifecycle || isFinishing) {
                completeStreamLoading()
                return@Runnable
            }
            messageView.visibility = View.GONE
            startPlayback()
        }
        reconnectRunnable = task
        overlayHandler.postDelayed(task, delayMs)
        return true
    }

    private fun cancelReconnect() {
        reconnectRunnable?.let(overlayHandler::removeCallbacks)
        reconnectRunnable = null
        reconnectScheduled = false
    }

    private fun cachePlaybackEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Playing -> lastKnownPlaying = true
            MediaPlayer.Event.Paused,
            MediaPlayer.Event.Stopped,
            MediaPlayer.Event.EndReached,
            MediaPlayer.Event.EncounteredError -> lastKnownPlaying = false
            MediaPlayer.Event.TimeChanged -> lastKnownPlaybackPositionMs = event.timeChanged.coerceAtLeast(0L)
            MediaPlayer.Event.LengthChanged -> lastKnownPlaybackLengthMs = event.lengthChanged.coerceAtLeast(0L)
        }
    }

    private fun resetCachedPlaybackState() {
        lastKnownPlaybackPositionMs = 0L
        lastKnownPlaybackLengthMs = 0L
        lastKnownPlaying = false
    }

    private fun beginPlayerStop() {
        playerStopInProgress = true
        lastKnownPlaying = false
        overlayHandler.removeCallbacks(updateProgressRunnable)
        updatePlayPauseButton()
    }

    private fun completePlayerStop() {
        playerStopInProgress = false
        lastKnownPlaying = false
        val shouldStart = pendingPlaybackStart && !stoppingForLifecycle && !isFinishing
        pendingPlaybackStart = false
        updatePlayPauseButton()
        if (shouldStart) {
            startPlayback()
        }
    }

    private fun canUsePlayer(player: MediaPlayer): Boolean =
        mediaPlayer === player && !playerStopInProgress && !stoppingForLifecycle && !isFinishing

    private fun stopPlayerForRestart(afterStop: () -> Unit) {
        val player = mediaPlayer
        if (player == null) {
            resetCachedPlaybackState()
            afterStop()
            return
        }
        pendingPlaybackStart = false
        if (muted) {
            applySystemMuteForApp()
        }
        beginPlayerStop()
        runPlayerCleanup("stop player for restart") {
            player.runCatchingStop()
            overlayHandler.post {
                completePlayerStop()
                if (!stoppingForLifecycle && !isFinishing && mediaPlayer === player) {
                    afterStop()
                }
            }
        }
    }

    private fun stopPlayerInBackground(player: MediaPlayer?) {
        if (player == null) {
            completePlayerStop()
            return
        }
        runPlayerCleanup("stop player") {
            player.runCatchingStop()
            overlayHandler.post { completePlayerStop() }
        }
    }

    private fun releasePlayerInBackground(player: MediaPlayer?, vlc: LibVLC?) {
        if (player == null && vlc == null) {
            return
        }
        beginPlayerStop()
        runPlayerCleanup("release player") {
            player?.runCatchingStop()
            runCatching { player?.release() }
                .onFailure { Log.w(LogTag, "Unable to release VLC player", it) }
            runCatching { vlc?.release() }
                .onFailure { Log.w(LogTag, "Unable to release LibVLC", it) }
        }
    }

    private fun runPlayerCleanup(label: String, block: () -> Unit) {
        try {
            playerCleanupExecutor.execute {
                runCatching(block)
                    .onFailure { Log.w(LogTag, "Unable to $label", it) }
            }
        } catch (e: RejectedExecutionException) {
            Log.w(LogTag, "VLC cleanup executor rejected $label", e)
        }
    }

    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "$hours:${minutes.twoDigits()}:${seconds.twoDigits()}"
        } else {
            "${minutes.twoDigits()}:${seconds.twoDigits()}"
        }
    }

    private fun Long.twoDigits(): String =
        if (this < 10L) "0$this" else toString()

    private fun toggleControls() {
        if (controlsVisible) {
            controlsOverlay.visibility = View.GONE
            hidePlaylistOverlay()
            controlsVisible = false
            setPlayerSurfaceTouchEnabled(true)
            overlayHandler.removeCallbacks(hideControlsRunnable)
            enterImmersiveMode()
        } else {
            elevatePlayerOverlays()
            controlsOverlay.visibility = View.VISIBLE
            controlsVisible = true
            setPlayerSurfaceTouchEnabled(false)
            updateStreamInfo(refreshTrackButtons = true)
            scheduleControlsHide()
        }
    }

    private fun scheduleControlsHide() {
        overlayHandler.removeCallbacks(hideControlsRunnable)
        overlayHandler.postDelayed(hideControlsRunnable, ControlsAutoHideMs)
    }

    private fun adjustBrightnessBy(delta: Float) {
        setBrightness(currentBrightness() + delta)
    }

    private fun currentBrightness(): Float {
        val brightness = window.attributes.screenBrightness
        return if (brightness in MinimumBrightness..1f) brightness else DefaultBrightness
    }

    private fun setBrightness(value: Float) {
        val normalized = value.coerceIn(MinimumBrightness, 1f)
        val params = window.attributes
        params.screenBrightness = normalized
        window.attributes = params
        showFeedback("Brightness ${(normalized * 100).roundToInt()}%")
    }

    private fun adjustVolumeBy(delta: Float) {
        val maxVolume = maxMusicVolume()
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        setVolumeLevel(current + (delta * maxVolume).roundToInt())
    }

    private fun setVolumeLevel(level: Int) {
        val maxVolume = maxMusicVolume()
        val bounded = level.coerceIn(0, maxVolume)
        if (muted && bounded > 0) {
            muted = false
            systemVolumeBeforeMute = UnknownSystemVolume
            updateMuteButton()
            saveEmbeddedPlayerPreference()
        }
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, bounded, 0)
        val percent = (bounded * 100f / maxVolume).roundToInt()
        applyDesiredAudioState(force = true)
        showFeedback("Volume $percent%")
    }

    private fun maxMusicVolume(): Int =
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    private fun markAudioStateSyncRequested(): Long {
        audioStateRequestVersion += 1
        return audioStateRequestVersion
    }

    private fun syncAudioStateAtStartup() {
        val version = markAudioStateSyncRequested()
        if (!muted) {
            ensureAudioTrackSelected()
        }
        applyDesiredAudioState()
        scheduleAudioStateStartupSync(version)
    }

    private fun syncAudioStateFromStreamEvent() {
        val now = SystemClock.uptimeMillis()
        if (now - lastAudioEventSyncAt < AudioEventSyncMinIntervalMs) {
            return
        }
        lastAudioEventSyncAt = now
        syncAudioStateAtStartup()
    }

    private fun scheduleAudioStateStartupSync(requestVersion: Long) {
        scheduleAudioStateRetry(requestVersion, 150L)
        scheduleAudioStateRetry(requestVersion, 450L)
        scheduleAudioStateRetry(requestVersion, 1_000L)
    }

    private fun scheduleAudioStateRetry(requestVersion: Long, delayMs: Long) {
        overlayHandler.postDelayed({
            if (requestVersion == audioStateRequestVersion && !playerStopInProgress && !stoppingForLifecycle) {
                applyDesiredAudioState()
            }
        }, delayMs)
    }

    private fun applyDesiredAudioState(force: Boolean = false) {
        mediaPlayer?.let { player ->
            if (!canUsePlayer(player)) {
                return
            }
            val now = SystemClock.uptimeMillis()
            if (!force && now - lastAudioStateApplyAt < AudioStateApplyMinIntervalMs) {
                return
            }
            lastAudioStateApplyAt = now
            runCatching { player.setAudioDigitalOutputEnabled(false) }
            if (muted) {
                applySystemMuteForApp()
                rememberAudioTrackBeforeMute(player)
                disableAudioTrackForMute(player)
                applyVlcVolume(player, 0, "mute", force)
                return
            }
            restoreSystemVolumeAfterAppMute()
            restoreAudioTrackAfterMute(player)
            applyVlcVolume(player, VlcAudibleVolume, "volume", force)
        }
    }

    private fun applyVlcVolume(player: MediaPlayer, volume: Int, label: String, force: Boolean = false) {
        val currentVolume = runCatching { player.getVolume() }.getOrDefault(Int.MIN_VALUE)
        if (!force && currentVolume == volume) {
            return
        }
        val result = runCatching { player.setVolume(volume) }.getOrDefault(0)
        Log.d(LogTag, "Applied VLC $label=$volume result=$result")
    }

    private fun MediaPlayer.configureAudioOutput() {
        val selected = runCatching { setAudioOutput(PreferredAudioOutput) }.getOrDefault(false)
        runCatching { setAudioDigitalOutputEnabled(false) }
        Log.i(LogTag, "Audio output requested=$PreferredAudioOutput selected=$selected")
    }

    private fun ensureAudioTrackSelected() {
        val player = mediaPlayer ?: return
        val currentTrack = runCatching { player.getAudioTrack() }.getOrDefault(UnknownTrackId)
        val tracks = runCatching { player.getAudioTracks()?.toList().orEmpty() }.getOrDefault(emptyList())
        val snapshot = "$currentTrack|${tracks.joinToString { "${it.id}:${it.name}" }}"
        if (tracks.isNotEmpty() && snapshot != lastAudioTrackSnapshot) {
            lastAudioTrackSnapshot = snapshot
            Log.i(
                LogTag,
                "Audio tracks current=$currentTrack available=${tracks.joinToString { "${it.id}:${it.name}" }}"
            )
        }
        if (currentTrack >= 0) {
            return
        }
        val firstPlayableTrack = tracks.firstOrNull { it.id >= 0 } ?: return
        val selected = runCatching { player.setAudioTrack(firstPlayableTrack.id) }.getOrDefault(false)
        if (selected) {
            audioTrackBeforeMute = firstPlayableTrack.id
        }
        Log.i(LogTag, "Selected audio track id=${firstPlayableTrack.id} name=${firstPlayableTrack.name} result=$selected")
    }

    private fun rememberAudioTrackBeforeMute(player: MediaPlayer) {
        val currentTrack = runCatching { player.getAudioTrack() }.getOrDefault(UnknownTrackId)
        if (currentTrack >= 0) {
            audioTrackBeforeMute = currentTrack
        }
    }

    private fun disableAudioTrackForMute(player: MediaPlayer) {
        val currentTrack = runCatching { player.getAudioTrack() }.getOrDefault(UnknownTrackId)
        val tracks = runCatching { player.getAudioTracks()?.toList().orEmpty() }.getOrDefault(emptyList())
        val disabledTrackId = tracks.firstOrNull { it.id < 0 }?.id ?: DisabledTrackId
        val snapshot = "$currentTrack->$disabledTrackId|${tracks.joinToString { "${it.id}:${it.name}" }}"
        if (currentTrack == disabledTrackId) {
            lastMutedAudioTrackSnapshot = snapshot
            return
        }
        val selected = runCatching { player.setAudioTrack(disabledTrackId) }.getOrDefault(false)
        if (snapshot != lastMutedAudioTrackSnapshot) {
            lastMutedAudioTrackSnapshot = snapshot
            Log.i(LogTag, "Muted audio track id=$disabledTrackId result=$selected")
        }
    }

    private fun restoreAudioTrackAfterMute(player: MediaPlayer) {
        val tracks = runCatching { player.getAudioTracks()?.toList().orEmpty() }.getOrDefault(emptyList())
        val currentTrack = runCatching { player.getAudioTrack() }.getOrDefault(UnknownTrackId)
        if (currentTrack >= 0) {
            audioTrackBeforeMute = currentTrack
            return
        }
        val restoreTrack = tracks.firstOrNull { it.id == audioTrackBeforeMute && it.id >= 0 }
            ?: tracks.firstOrNull { it.id >= 0 }
            ?: return
        val selected = runCatching { player.setAudioTrack(restoreTrack.id) }.getOrDefault(false)
        if (selected) {
            audioTrackBeforeMute = restoreTrack.id
        }
        Log.i(LogTag, "Restored audio track id=${restoreTrack.id} name=${restoreTrack.name} result=$selected")
    }

    private fun applySystemMuteForApp() {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (currentVolume > 0 && systemVolumeBeforeMute == UnknownSystemVolume) {
            systemVolumeBeforeMute = currentVolume
        }
        if (currentVolume != 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        }
    }

    private fun restoreSystemVolumeAfterAppMute(): Boolean {
        val restoreVolume = systemVolumeBeforeMute
        systemVolumeBeforeMute = UnknownSystemVolume
        if (restoreVolume <= 0) {
            return false
        }
        val maxVolume = maxMusicVolume()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restoreVolume.coerceIn(1, maxVolume), 0)
        return true
    }

    private fun ensureAudibleSystemVolume(force: Boolean = false) {
        if (muted) {
            return
        }
        if (!force && startupVolumeFallbackApplied) {
            return
        }
        val maxVolume = maxMusicVolume()
        if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0) {
            startupVolumeFallbackApplied = true
            return
        }
        val fallback = (maxVolume * StartupVolumeFallbackRatio).roundToInt().coerceIn(1, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, fallback, 0)
        startupVolumeFallbackApplied = true
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                        syncAudioStateAtStartup()
                    }
                }
                .build()
                .also { audioFocusRequest = it }
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    private fun cycleZoomMode() {
        applyZoomMode((zoomModeIndex + 1) % zoomModes.size)
    }

    private fun applyZoomMode(index: Int) {
        zoomModeIndex = index.coerceIn(zoomModes.indices)
        val mode = zoomModes[zoomModeIndex]
        mediaPlayer?.setVideoScale(mode.scaleType)
        setButtonIcon(zoomButton, mode.iconRes)
        zoomButton.contentDescription = zoomControlDescription(mode)
        showFeedback("Zoom ${mode.label}")
    }

    private fun zoomControlDescription(mode: ZoomMode): String =
        "Zoom ${mode.label}"

    private fun showAudioTrackMenu(anchor: View) {
        val player = mediaPlayer
        if (player == null || !canUsePlayer(player)) {
            showFeedback("Audio unavailable")
            return
        }
        val items = audioTrackMenuItems(player)
        if (items.isEmpty()) {
            showFeedback("No audio tracks")
            return
        }
        showOptionMenu(
            title = "Audio",
            options = items.map { item ->
                PlayerMenuOption(label = item.label, selected = item.selected) {
                    val selected = runCatching { player.setAudioTrack(item.id) }.getOrDefault(false)
                    if (selected) {
                        audioTrackBeforeMute = item.id
                        updateTrackMenuButtons()
                        showFeedback("Audio ${item.label}")
                    } else {
                        showFeedback("Audio unavailable")
                    }
                }
            },
            anchor = anchor
        )
    }

    private fun showSubtitleTrackMenu(anchor: View) {
        val player = mediaPlayer
        if (player == null || !canUsePlayer(player)) {
            showFeedback("Subtitles unavailable")
            return
        }
        val items = subtitleTrackMenuItems(player)
        if (items.isEmpty()) {
            showFeedback("No subtitles")
            return
        }
        showOptionMenu(
            title = "Subtitles",
            options = items.map { item ->
                PlayerMenuOption(label = item.label, selected = item.selected) {
                    val selected = runCatching { player.setSpuTrack(item.id) }.getOrDefault(false)
                    if (selected) {
                        updateTrackMenuButtons()
                        showFeedback(if (item.id >= 0) "Subtitles ${item.label}" else "Subtitles off")
                    } else {
                        showFeedback("Subtitles unavailable")
                    }
                }
            },
            anchor = anchor
        )
    }

    private fun showOptionMenu(title: String, options: List<PlayerMenuOption>, anchor: View) {
        if (options.isEmpty() || !::rootLayout.isInitialized) {
            return
        }
        val menuWidth = min(dp(320), (rootLayout.width - dp(32)).coerceAtLeast(dp(220)))
        val maxMenuHeight = (rootLayout.height - controlsOverlay.height - dp(56)).coerceAtLeast(dp(180))
        val menuHeight = min(dp(58) + options.size * dp(46), maxMenuHeight)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundedBackground(
                color = Color.argb(245, 12, 16, 20),
                radius = dp(8),
                strokeColor = Color.argb(180, 79, 216, 235)
            )
            addView(
                TextView(this@EmbeddedPlayerActivity).apply {
                    text = title
                    setTextColor(Color.WHITE)
                    setTextSize(13f)
                    setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                    setPadding(dp(8), dp(6), dp(8), dp(8))
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }
        val popup = PopupWindow(this)
        options.forEach { option ->
            content.addView(
                TextView(this).apply {
                    text = "${if (option.selected) "[x]" else "[ ]"} ${option.label}"
                    setTextColor(if (option.enabled) Color.WHITE else Color.rgb(132, 143, 153))
                    setTextSize(14f)
                    gravity = Gravity.CENTER_VERTICAL
                    minHeight = dp(44)
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    isEnabled = option.enabled
                    if (option.enabled) {
                        isClickable = true
                        isFocusable = true
                        background = roundedBackground(
                            color = if (option.selected) Color.argb(210, 34, 83, 93) else Color.TRANSPARENT,
                            radius = dp(6)
                        )
                        setOnClickListener {
                            popup.dismiss()
                            option.onSelect()
                        }
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
            )
        }
        val scrollView = ScrollView(this).apply {
            isFillViewport = false
            addView(content)
        }
        popup.apply {
            contentView = scrollView
            width = menuWidth
            height = menuHeight
            isFocusable = true
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(8).toFloat()
            }
            setOnDismissListener { scheduleControlsHide() }
        }
        hidePlaylistOverlay()
        overlayHandler.post { overlayHandler.removeCallbacks(hideControlsRunnable) }
        val bottomOffset = controlsOverlay.height.takeIf { it > 0 } ?: dp(116)
        popup.showAtLocation(rootLayout, Gravity.BOTTOM or Gravity.END, dp(12), bottomOffset + dp(10))
        anchor.isPressed = false
    }

    private fun audioTrackMenuItems(player: MediaPlayer): List<TrackMenuItem> {
        val descriptions = runCatching { player.audioTracks?.toList().orEmpty() }.getOrDefault(emptyList())
        if (descriptions.isEmpty()) {
            return emptyList()
        }
        val currentTrack = runCatching { player.audioTrack }.getOrDefault(UnknownTrackId)
        return descriptions
            .filter { it.id >= 0 }
            .mapIndexed { index, description ->
                TrackMenuItem(
                    id = description.id,
                    label = trackMenuLabel(
                        kind = AudioTrackKind,
                        index = index,
                        description = description,
                        mediaTrack = mediaTrackById<IMedia.AudioTrack>(player, description.id)
                    ),
                    selected = description.id == currentTrack
                )
            }
    }

    private fun subtitleTrackMenuItems(player: MediaPlayer): List<TrackMenuItem> {
        val descriptions = runCatching { player.spuTracks?.toList().orEmpty() }.getOrDefault(emptyList())
        val subtitleDescriptions = descriptions.filter { it.id >= 0 }
        if (subtitleDescriptions.isEmpty()) {
            return emptyList()
        }
        val currentTrack = runCatching { player.spuTrack }.getOrDefault(UnknownTrackId)
        val offTrackId = descriptions.firstOrNull { it.id < 0 }?.id ?: DisabledTrackId
        return listOf(TrackMenuItem(id = offTrackId, label = "Off", selected = currentTrack < 0)) +
            subtitleDescriptions.mapIndexed { index, description ->
                TrackMenuItem(
                    id = description.id,
                    label = trackMenuLabel(
                        kind = SubtitleTrackKind,
                        index = index,
                        description = description,
                        mediaTrack = mediaTrackById<IMedia.SubtitleTrack>(player, description.id)
                    ),
                    selected = description.id == currentTrack
                )
            }
    }

    private inline fun <reified T : IMedia.Track> mediaTrackById(player: MediaPlayer, trackId: Int): T? =
        withPlayerMedia(player) { media ->
            for (index in 0 until media.trackCount) {
                val track = media.getTrack(index)
                if (track is T && track.id == trackId) {
                    return@withPlayerMedia track
                }
            }
            null
        }

    private fun trackMenuLabel(
        kind: String,
        index: Int,
        description: MediaPlayer.TrackDescription,
        mediaTrack: IMedia.Track?
    ): String {
        val fallback = "$kind ${index + 1}"
        val name = description.name
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "Disable" && it != "Disabled" }
        val language = languageLabel(mediaTrack?.language)
        val mediaDescription = mediaTrack?.description?.trim()?.takeIf { it.isNotBlank() }
        val primary = language ?: mediaDescription ?: name ?: fallback
        val suffix = name
            ?.takeIf { candidate -> !primary.contains(candidate, ignoreCase = true) }
            ?.takeIf { candidate -> !candidate.contains(primary, ignoreCase = true) }
        return listOfNotNull(primary, suffix).joinToString(" - ")
    }

    private fun languageLabel(language: String?): String? {
        val normalized = language
            ?.trim()
            ?.replace('_', '-')
            ?.takeIf { it.isNotBlank() && it != "und" }
            ?: return null
        KnownLanguageLabels[normalized.lowercase(Locale.ROOT)]?.let { return it }
        val locale = Locale.forLanguageTag(normalized)
        val displayLanguage = locale.getDisplayLanguage(Locale.getDefault())
        return displayLanguage
            .takeIf { it.isNotBlank() && !it.equals(normalized, ignoreCase = true) }
            ?: normalized.uppercase(Locale.ROOT)
    }

    private fun updateTrackMenuButtons() {
        val player = mediaPlayer?.takeIf { canUsePlayer(it) }
        if (::audioTrackButton.isInitialized) {
            val audioCount = player?.let { audioTrackMenuItems(it).size } ?: 0
            audioTrackButton.alpha = if (audioCount > 0) 1f else 0.55f
            audioTrackButton.background = controlButtonBackground(active = audioCount > 1)
            audioTrackButton.contentDescription = if (audioCount > 1) "Audio tracks" else "Audio"
        }
        if (::subtitleTrackButton.isInitialized) {
            val subtitleItems = player?.let { subtitleTrackMenuItems(it) } ?: emptyList()
            val subtitleCount = subtitleItems.count { it.id >= 0 }
            val subtitlesActive = subtitleItems.any { it.id >= 0 && it.selected }
            subtitleTrackButton.alpha = if (subtitleCount > 0) 1f else 0.55f
            subtitleTrackButton.background = controlButtonBackground(active = subtitlesActive)
            subtitleTrackButton.contentDescription = if (subtitleCount > 0) "Subtitle tracks" else "Subtitles"
        }
    }

    private fun updateStreamInfo(refreshTrackButtons: Boolean = false) {
        if (!::streamInfoLabel.isInitialized) {
            return
        }
        updateTitleLabel()
        streamInfoLabel.text = buildStreamInfoLabel()
        if (refreshTrackButtons) {
            updateTrackMenuButtons()
        }
    }

    private fun updateTitleLabel() {
        if (!::titleLabel.isInitialized) {
            return
        }
        titleLabel.text = currentTarget?.title
            ?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra(NativePlayerActivity.EXTRA_TITLE).orEmpty().ifBlank { "Embedded player" }
    }

    private fun handleElementaryStreamChanged(event: MediaPlayer.Event) {
        if (playerStopInProgress || stoppingForLifecycle || isFinishing) {
            return
        }
        if (event.esChangedType == IMedia.Track.Type.Audio) {
            syncAudioStateFromStreamEvent()
        }
        if (event.esChangedType == IMedia.Track.Type.Video) {
            when (event.type) {
                MediaPlayer.Event.ESSelected -> selectedVideoTrackId = event.esChangedID
                MediaPlayer.Event.ESDeleted -> {
                    if (selectedVideoTrackId == event.esChangedID) {
                        selectedVideoTrackId = UnknownTrackId
                    }
                }
            }
            scheduleStreamInfoRefreshes()
        } else {
            updateStreamInfo()
        }
        if (controlsVisible) {
            updateTrackMenuButtons()
        }
    }

    private fun scheduleStreamInfoRefreshes() {
        updateStreamInfo()
        overlayHandler.postDelayed({ updateStreamInfo() }, StreamInfoRefreshShortDelayMs)
        overlayHandler.postDelayed({ updateStreamInfo() }, StreamInfoRefreshMediumDelayMs)
        overlayHandler.postDelayed({ updateStreamInfo() }, StreamInfoRefreshLongDelayMs)
    }

    private fun resetAdaptiveStreamInfo() {
        adaptiveProbeGeneration += 1
        adaptiveStreamInfo = null
        if (::streamInfoLabel.isInitialized) {
            updateStreamInfo()
        }
    }

    private fun startAdaptiveStreamProbe(streamUrl: String) {
        val generation = adaptiveProbeGeneration
        playbackScope.launch {
            val info = withContext(Dispatchers.IO) {
                sniffAdaptiveStream(streamUrl)
            }
            if (
                info != null &&
                generation == adaptiveProbeGeneration &&
                !stoppingForLifecycle &&
                !isFinishing
            ) {
                adaptiveStreamInfo = info
                updateStreamInfo()
            }
        }
    }

    private fun sniffAdaptiveStream(streamUrl: String): AdaptiveStreamInfo? {
        val scheme = runCatching { Uri.parse(streamUrl).scheme?.lowercase(Locale.ROOT) }.getOrNull()
        if (scheme != "http" && scheme != "https") {
            return null
        }
        val body = readAdaptiveProbeBody(streamUrl) ?: return null
        val trimmed = body.trimStart()
        return when {
            trimmed.startsWith("#EXTM3U") -> parseHlsAdaptiveInfo(streamUrl, body)
            trimmed.startsWith("<") && body.contains("<MPD", ignoreCase = true) -> parseDashAdaptiveInfo(streamUrl, body)
            streamUrl.lowercase(Locale.ROOT).contains(".mpd") -> parseDashAdaptiveInfo(streamUrl, body)
            else -> null
        }
    }

    private fun initialAdaptiveStreamInfo(streamUrl: String): AdaptiveStreamInfo? {
        val normalized = streamUrl.lowercase(Locale.ROOT)
        return when {
            normalized.contains(".m3u8") -> AdaptiveStreamInfo("HLS", emptyList())
            normalized.contains(".mpd") -> AdaptiveStreamInfo("DASH", emptyList())
            else -> null
        }
    }

    private fun readAdaptiveProbeBody(streamUrl: String): String? {
        val connection = runCatching { URL(streamUrl).openConnection() as HttpURLConnection }.getOrNull() ?: return null
        return runCatching {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = AdaptiveProbeTimeoutMs
            connection.readTimeout = AdaptiveProbeTimeoutMs
            connection.setRequestProperty("User-Agent", ChromeUserAgent)
            connection.setRequestProperty("Accept", "application/vnd.apple.mpegurl, application/dash+xml, */*")
            connection.setRequestProperty("Range", "bytes=0-${AdaptiveProbeMaxBytes - 1}")
            val code = connection.responseCode
            if (code !in 200..299 && code != HttpURLConnection.HTTP_PARTIAL) {
                return@runCatching null
            }
            BufferedInputStream(connection.inputStream).use { input ->
                val output = ByteArray(AdaptiveProbeMaxBytes)
                var total = 0
                while (total < output.size) {
                    val read = input.read(output, total, output.size - total)
                    if (read <= 0) {
                        break
                    }
                    total += read
                }
                output.copyOf(total).toString(Charsets.UTF_8)
            }
        }.onFailure {
            Log.d(LogTag, "Unable to probe adaptive stream manifest", it)
        }.getOrNull().also {
            connection.disconnect()
        }
    }

    private fun parseHlsAdaptiveInfo(baseUrl: String, body: String): AdaptiveStreamInfo? {
        val variants = mutableListOf<AdaptiveVariant>()
        var pendingWidth = 0
        var pendingHeight = 0
        var pendingBandwidth = 0
        var pendingVariant = false
        body.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) -> {
                    val resolution = HlsResolutionRegex.find(line)
                    pendingWidth = resolution?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                    pendingHeight = resolution?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
                    pendingBandwidth = HlsBandwidthRegex.find(line)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                        ?: 0
                    pendingVariant = true
                }
                pendingVariant && line.isNotBlank() && !line.startsWith("#") -> {
                    variants += AdaptiveVariant(
                        width = pendingWidth,
                        height = pendingHeight,
                        bandwidth = pendingBandwidth,
                        url = resolveRelativeStreamUrl(baseUrl, line)
                    )
                    pendingVariant = false
                    pendingWidth = 0
                    pendingHeight = 0
                    pendingBandwidth = 0
                }
            }
        }
        return when {
            variants.isNotEmpty() -> AdaptiveStreamInfo("HLS", variants)
            body.contains("#EXT-X-TARGETDURATION", ignoreCase = true) -> AdaptiveStreamInfo("HLS", emptyList())
            else -> null
        }
    }

    private fun parseDashAdaptiveInfo(baseUrl: String, body: String): AdaptiveStreamInfo? =
        runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            val document = factory.newDocumentBuilder().parse(InputSource(StringReader(body)))
            val nodes = document.getElementsByTagNameNS("*", "Representation").takeIf { it.length > 0 }
                ?: document.getElementsByTagName("Representation")
            val variants = mutableListOf<AdaptiveVariant>()
            for (index in 0 until nodes.length) {
                val representation = nodes.item(index) as? Element ?: continue
                val parent = representation.parentNode as? Element
                val mimeType = representation.attribute("mimeType").ifBlank { parent?.attribute("mimeType").orEmpty() }
                val width = representation.intAttribute("width") ?: parent?.intAttribute("width") ?: 0
                val height = representation.intAttribute("height") ?: parent?.intAttribute("height") ?: 0
                if (!mimeType.contains("video", ignoreCase = true) && width <= 0 && height <= 0) {
                    continue
                }
                val bandwidth = representation.intAttribute("bandwidth") ?: 0
                variants += AdaptiveVariant(
                    width = width,
                    height = height,
                    bandwidth = bandwidth,
                    url = representation.firstChildText("BaseURL")
                        ?.let { resolveRelativeStreamUrl(baseUrl, it) }
                        .orEmpty()
                )
            }
            if (variants.isNotEmpty()) {
                AdaptiveStreamInfo("DASH", variants)
            } else if (body.contains("<MPD", ignoreCase = true)) {
                AdaptiveStreamInfo("DASH", emptyList())
            } else {
                null
            }
        }.onFailure {
            Log.d(LogTag, "Unable to parse DASH manifest", it)
        }.getOrNull()

    private fun resolveRelativeStreamUrl(baseUrl: String, value: String): String =
        runCatching { URL(URL(baseUrl), value).toString() }.getOrDefault(value)

    private fun Element.attribute(name: String): String =
        if (hasAttribute(name)) getAttribute(name).trim() else ""

    private fun Element.intAttribute(name: String): Int? =
        attribute(name).toIntOrNull()

    private fun Element.firstChildText(tagName: String): String? {
        val nodes = getElementsByTagNameNS("*", tagName).takeIf { it.length > 0 }
            ?: getElementsByTagName(tagName)
        return nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun buildStreamInfoLabel(): String {
        adaptiveStreamInfo?.summaryLabel()?.let { adaptiveLabel ->
            val codec = currentVideoTrackDetails()
                ?.codec
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.uppercase()
            return listOfNotNull(adaptiveLabel, codec, decoderModeLabel()).joinToString(" | ")
        }
        val video = currentVideoTrackDetails()
        val resolution = if (video != null) {
            video.resolutionLabel()
        } else {
            "Resolution pending"
        }
        val codec = video?.codec
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.uppercase()
            ?: "codec pending"
        return "$resolution | $codec | ${decoderModeLabel()}"
    }

    private fun currentVideoTrackDetails(): VideoTrackDetails? {
        val player = mediaPlayer ?: return null
        val rendered = renderedVideoDetails
        rendered
            ?.takeIf { it.hasUsefulInfo() }
            ?.let { return it }
        currentRenderedVideoLayoutDetails(player)
            ?.takeIf { it.hasUsefulInfo() }
            ?.let { return it }
        runCatching { player.currentVideoTrack }
            .getOrNull()
            ?.toVideoTrackDetails()
            ?.takeIf { it.hasResolution() }
            ?.also { renderedVideoDetails = it }
            ?.let { return it }
        return null
    }

    private inline fun <T> withPlayerMedia(player: MediaPlayer, block: (IMedia) -> T): T? {
        val media = runCatching { player.media }.getOrNull() ?: return null
        return try {
            block(media)
        } finally {
            media.release()
        }
    }

    private fun installVideoLayoutObserver(player: MediaPlayer) {
        if (videoLayoutListenerWrapped) {
            return
        }
        val field = VoutLayoutListenerField ?: return
        val vout = player.getVLCVout()
        val original = runCatching { field.get(vout) as? IVLCVout.OnNewVideoLayoutListener }.getOrNull() ?: return
        val observer = IVLCVout.OnNewVideoLayoutListener { vlcVout, width, height, visibleWidth, visibleHeight, sarNum, sarDen ->
            original.onNewVideoLayout(vlcVout, width, height, visibleWidth, visibleHeight, sarNum, sarDen)
            updateRenderedVideoDetails(width, height, visibleWidth, visibleHeight, sarNum, sarDen)
        }
        runCatching {
            field.set(vout, observer)
            videoLayoutListenerWrapped = true
        }
    }

    private fun updateRenderedVideoDetails(
        width: Int,
        height: Int,
        visibleWidth: Int,
        visibleHeight: Int,
        sarNum: Int,
        sarDen: Int
    ) {
        val details = videoLayoutDetails(
            width = width,
            height = height,
            visibleWidth = visibleWidth,
            visibleHeight = visibleHeight,
            sarNum = sarNum,
            sarDen = sarDen,
            codec = ""
        ) ?: return
        renderedVideoDetails = details
        overlayHandler.post { updateStreamInfo() }
    }

    private fun currentRenderedVideoLayoutDetails(player: MediaPlayer): VideoTrackDetails? {
        val helper = runCatching { VideoHelperField?.get(player) }.getOrNull() ?: return null
        val videoWidth = helper.intField("mVideoWidth")
        val videoHeight = helper.intField("mVideoHeight")
        val visibleWidth = helper.intField("mVideoVisibleWidth").takeIf { it > 0 } ?: videoWidth
        val visibleHeight = helper.intField("mVideoVisibleHeight").takeIf { it > 0 } ?: videoHeight
        return videoLayoutDetails(
            width = videoWidth,
            height = videoHeight,
            visibleWidth = visibleWidth,
            visibleHeight = visibleHeight,
            sarNum = helper.intField("mVideoSarNum"),
            sarDen = helper.intField("mVideoSarDen"),
            codec = ""
        )
    }

    private fun videoLayoutDetails(
        width: Int,
        height: Int,
        visibleWidth: Int,
        visibleHeight: Int,
        sarNum: Int,
        sarDen: Int,
        codec: String
    ): VideoTrackDetails? {
        val sourceWidth = visibleWidth.takeIf { it > 0 } ?: width
        val sourceHeight = visibleHeight.takeIf { it > 0 } ?: height
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return null
        }
        val sampleAspectRatio = if (sarNum > 0 && sarDen > 0) {
            sarNum.toFloat() / sarDen.toFloat()
        } else {
            1f
        }
        val adjustedWidth = (sourceWidth * sampleAspectRatio).roundToInt().coerceAtLeast(1)
        return VideoTrackDetails(
            displayWidth = adjustedWidth,
            displayHeight = sourceHeight,
            codec = codec
        )
    }

    private fun Any.intField(name: String): Int =
        runCatching {
            javaClass.getDeclaredField(name).apply { isAccessible = true }.getInt(this)
        }.getOrDefault(0)

    private fun IMedia.VideoTrack.toVideoTrackDetails(): VideoTrackDetails {
        val sampleAspectRatio = if (sarNum > 0 && sarDen > 0) {
            sarNum.toFloat() / sarDen.toFloat()
        } else {
            1f
        }
        val adjustedWidth = (width * sampleAspectRatio).roundToInt().coerceAtLeast(1)
        val rotated = orientation == VideoOrientationLeftBottom || orientation == VideoOrientationRightTop
        return if (rotated) {
            VideoTrackDetails(displayWidth = height, displayHeight = adjustedWidth, codec = codec)
        } else {
            VideoTrackDetails(displayWidth = adjustedWidth, displayHeight = height, codec = codec)
        }
    }

    private fun decoderModeLabel(): String {
        val decoder = runCatching { HWDecoderUtil.getDecoderFromDevice() }.getOrNull()
        return when (decoder) {
            HWDecoderUtil.Decoder.NONE -> "SW decode"
            HWDecoderUtil.Decoder.OMX -> "HW auto OMX"
            HWDecoderUtil.Decoder.MEDIACODEC -> "HW auto MediaCodec"
            HWDecoderUtil.Decoder.ALL -> "HW auto"
            HWDecoderUtil.Decoder.UNKNOWN,
            null -> "HW auto"
        }
    }

    private fun showFeedback(message: String, durationMs: Long = GestureFeedbackMs) {
        feedbackView.text = message
        elevatePlayerOverlays()
        feedbackView.visibility = View.VISIBLE
        overlayHandler.removeCallbacks(hideFeedbackRunnable)
        overlayHandler.postDelayed(hideFeedbackRunnable, durationMs)
    }

    private fun beginStreamLoading() {
        streamLoadInProgress = true
        showLoading()
    }

    private fun keepLoadingVisible() {
        if (streamLoadInProgress) {
            showLoading()
        }
    }

    private fun completeStreamLoadingIfClockStarted(timeMs: Long) {
        if (streamLoadInProgress && timeMs > 0L) {
            completeStreamLoading()
        }
    }

    private fun completeStreamLoading() {
        streamLoadInProgress = false
        hideLoading()
    }

    private fun showLoading() {
        if (::loadingSpinner.isInitialized) {
            elevatePlayerOverlays()
            loadingSpinner.visibility = View.VISIBLE
        }
    }

    private fun hideLoading() {
        if (::loadingSpinner.isInitialized) {
            loadingSpinner.visibility = View.GONE
        }
    }

    private fun roundedBackground(color: Int, radius: Int, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius.toFloat()
            strokeColor?.let { setStroke(dp(1), it) }
        }

    @Suppress("DEPRECATION")
    private fun enterImmersiveMode() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun showMessage(message: String) {
        messageView.text = message
        elevatePlayerOverlays()
        messageView.visibility = View.VISIBLE
    }

    private fun targetFromIntent(): PlaybackTarget? {
        val mode = runCatching {
            BrowseMode.valueOf(intent.getStringExtra(NativePlayerActivity.EXTRA_MODE).orEmpty())
        }.getOrNull() ?: return null
        return PlaybackTarget(
            accountId = intent.getLongExtra(NativePlayerActivity.EXTRA_ACCOUNT_ID, 0L),
            accountName = intent.getStringExtra(NativePlayerActivity.EXTRA_ACCOUNT_NAME).orEmpty(),
            mode = mode,
            categoryProviderId = intent.getStringExtra(NativePlayerActivity.EXTRA_CATEGORY_PROVIDER_ID).orEmpty(),
            categoryRowId = intent.getLongExtra(NativePlayerActivity.EXTRA_CATEGORY_ROW_ID, 0L),
            channelId = intent.getStringExtra(NativePlayerActivity.EXTRA_CHANNEL_ID).orEmpty(),
            title = intent.getStringExtra(NativePlayerActivity.EXTRA_TITLE).orEmpty(),
            url = intent.getStringExtra(NativePlayerActivity.EXTRA_URL).orEmpty(),
            logo = intent.getStringExtra(NativePlayerActivity.EXTRA_LOGO).orEmpty(),
            seriesId = intent.getStringExtra(NativePlayerActivity.EXTRA_SERIES_ID).orEmpty(),
            seriesTitle = intent.getStringExtra(NativePlayerActivity.EXTRA_SERIES_TITLE).orEmpty(),
            episodeId = intent.getStringExtra(NativePlayerActivity.EXTRA_EPISODE_ID).orEmpty(),
            season = intent.getStringExtra(NativePlayerActivity.EXTRA_SEASON).orEmpty(),
            episodeNumber = intent.getStringExtra(NativePlayerActivity.EXTRA_EPISODE_NUMBER).orEmpty()
        )
    }

    private fun MediaPlayer.runCatchingStop() {
        runCatching {
            stop()
        }.onFailure { Log.w(LogTag, "Unable to stop VLC player", it) }
    }

    private data class ZoomMode(
        val label: String,
        val scaleType: MediaPlayer.ScaleType,
        val iconRes: Int
    )

    private data class PlayerMenuOption(
        val label: String,
        val selected: Boolean,
        val enabled: Boolean = true,
        val onSelect: () -> Unit
    )

    private data class TrackMenuItem(
        val id: Int,
        val label: String,
        val selected: Boolean
    )

    private data class AdaptiveStreamInfo(
        val type: String,
        val variants: List<AdaptiveVariant>
    ) {
        fun summaryLabel(): String {
            val labels = variants
                .mapNotNull { it.qualityLabel() }
                .distinct()
                .sortedWith(compareBy({ qualitySortValue(it) }, { it }))
            if (labels.isEmpty()) {
                return type
            }
            val compact = if (labels.size <= 5) {
                labels
            } else {
                labels.take(4) + labels.last()
            }
            return "$type ${compact.joinToString("/")}"
        }

        private fun qualitySortValue(label: String): Int =
            label.substringBefore('p').toIntOrNull()
                ?: label.substringBefore('k').toIntOrNull()
                ?: Int.MAX_VALUE
    }

    private data class AdaptiveVariant(
        val width: Int,
        val height: Int,
        val bandwidth: Int,
        val url: String
    ) {
        fun qualityLabel(): String? =
            when {
                height > 0 -> "${height}p"
                width > 0 -> "${width}w"
                bandwidth > 0 -> "${(bandwidth / 1_000).coerceAtLeast(1)}k"
                url.isNotBlank() -> Uri.parse(url).lastPathSegment?.takeIf { it.isNotBlank() }
                else -> null
            }
    }

    private data class VideoTrackDetails(
        val displayWidth: Int,
        val displayHeight: Int,
        val codec: String
    ) {
        fun hasUsefulInfo(): Boolean =
            displayWidth > 0 || displayHeight > 0 || codec.isNotBlank()

        fun hasResolution(): Boolean =
            displayWidth > 0 && displayHeight > 0

        fun resolutionLabel(): String =
            if (displayWidth > 0 && displayHeight > 0) {
                val tier = when {
                    displayWidth >= 3800 || displayHeight >= 2100 -> " 4K"
                    displayWidth >= 2500 || displayHeight >= 1400 -> " QHD"
                    displayWidth >= 1900 || displayHeight >= 1000 -> " FHD"
                    displayWidth >= 1200 || displayHeight >= 700 -> " HD"
                    else -> ""
                }
                "$displayWidth x $displayHeight$tier"
            } else {
                "Resolution pending"
            }
    }

    private enum class PlayerGesture {
        None,
        Brightness,
        Volume,
        Zoom
    }

    private companion object {
        private const val DefaultZoomModeIndex = 0
        private const val FillZoomModeIndex = 1
        private const val ControlsAutoHideMs = 3_500L
        private const val GestureFeedbackMs = 900L
        private const val ProgressUpdateMs = 1_000L
        private const val ReconnectDelayMs = 10_000L
        private const val MaxReconnectAttempts = 5
        private const val PreferredAudioOutput = "opensles"
        private const val LogTag = "UIPTV-Embedded"
        private const val ChromeUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"
        private const val StreamInfoRefreshShortDelayMs = 250L
        private const val StreamInfoRefreshMediumDelayMs = 1_000L
        private const val StreamInfoRefreshLongDelayMs = 2_500L
        private const val ProgressBarMax = 1_000
        private const val VlcAudibleVolume = 100
        private const val SeekStepSeconds = 15
        private const val BrightnessStep = 0.10f
        private const val VolumeStep = 0.10f
        private const val ControlButtonSizeDp = 48
        private const val ControlIconPaddingDp = 13
        private const val AdaptiveProbeMaxBytes = 512 * 1024
        private const val AdaptiveProbeTimeoutMs = 8_000
        private const val AudioEventSyncMinIntervalMs = 1_500L
        private const val AudioStateApplyMinIntervalMs = 750L
        private const val StartupVolumeFallbackRatio = 0.35f
        private const val DefaultBrightness = 0.50f
        private const val MinimumBrightness = 0.05f
        private const val GestureSlopPx = 18f
        private const val TwoFingerZoomSlopDp = 56
        private const val TapSlopPx = 14f
        private const val UnknownTrackId = -1
        private const val UnknownSystemVolume = -1
        private const val DisabledTrackId = -1
        private const val VideoOrientationLeftBottom = 1
        private const val VideoOrientationRightTop = 3
        private const val LiveTimeLabel = "Live"
        private const val PlaylistIcon = "List"
        private const val AudioTrackKind = "Audio"
        private const val SubtitleTrackKind = "Subtitle"
        private const val CloseIcon = "\u2715"
        private val HlsResolutionRegex = Regex("""RESOLUTION=(\d+)x(\d+)""", RegexOption.IGNORE_CASE)
        private val HlsBandwidthRegex = Regex("""(?:AVERAGE-)?BANDWIDTH=(\d+)""", RegexOption.IGNORE_CASE)
        private val KnownLanguageLabels = mapOf(
            "en" to "English",
            "eng" to "English",
            "es" to "Spanish",
            "spa" to "Spanish",
            "fr" to "French",
            "fre" to "French",
            "fra" to "French",
            "de" to "German",
            "ger" to "German",
            "deu" to "German",
            "it" to "Italian",
            "ita" to "Italian",
            "pt" to "Portuguese",
            "por" to "Portuguese",
            "ar" to "Arabic",
            "ara" to "Arabic",
            "hi" to "Hindi",
            "hin" to "Hindi",
            "ur" to "Urdu",
            "urd" to "Urdu",
            "tr" to "Turkish",
            "tur" to "Turkish",
            "ru" to "Russian",
            "rus" to "Russian",
            "zh" to "Chinese",
            "zho" to "Chinese",
            "chi" to "Chinese",
            "ja" to "Japanese",
            "jpn" to "Japanese",
            "ko" to "Korean",
            "kor" to "Korean",
            "nl" to "Dutch",
            "nld" to "Dutch",
            "dut" to "Dutch",
            "sv" to "Swedish",
            "swe" to "Swedish",
            "no" to "Norwegian",
            "nor" to "Norwegian",
            "da" to "Danish",
            "dan" to "Danish",
            "fi" to "Finnish",
            "fin" to "Finnish",
            "pl" to "Polish",
            "pol" to "Polish"
        )
        private val VideoHelperField: Field? = runCatching {
            MediaPlayer::class.java.getDeclaredField("mVideoHelper").apply { isAccessible = true }
        }.getOrNull()
        private val VoutLayoutListenerField: Field? = runCatching {
            Class.forName("org.videolan.libvlc.AWindow")
                .getDeclaredField("mOnNewVideoLayoutListener")
                .apply { isAccessible = true }
        }.getOrNull()
    }
}

private fun String.isPlayableNetworkUrl(): Boolean {
    val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase()
    return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
}
