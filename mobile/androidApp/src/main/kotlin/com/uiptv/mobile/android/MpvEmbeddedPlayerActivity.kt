package com.uiptv.mobile.android

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.uiptv.mobile.shared.browse.BrowseMode
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import com.uiptv.mobile.shared.playback.PlaybackTarget
import com.uiptv.mobile.shared.settings.AndroidDataStorePreferencesRepository
import com.uiptv.mobile.shared.settings.EmbeddedPlayerPreference
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

class MpvEmbeddedPlayerActivity : Activity() {
    private var mpv: MPVLib? = null
    @Volatile
    private var activityDestroyed = false
    private lateinit var audioManager: AudioManager
    private lateinit var rootLayout: FrameLayout
    private lateinit var videoSurface: TextureView
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
    private lateinit var repeatButton: ImageButton
    private lateinit var muteButton: ImageButton
    private lateinit var audioTrackButton: ImageButton
    private lateinit var subtitleTrackButton: ImageButton
    private val overlayHandler = Handler(Looper.getMainLooper())
    private val zoomModes = listOf(
        ZoomMode("Default", keepAspect = true, aspectOverride = "no", unscaled = false, R.drawable.aspect_ratio),
        ZoomMode("Fill", keepAspect = false, aspectOverride = "no", unscaled = false, R.drawable.aspect_ratio_fill),
        ZoomMode("16:9", keepAspect = true, aspectOverride = "16:9", unscaled = false, R.drawable.aspect_ratio),
        ZoomMode("4:3", keepAspect = true, aspectOverride = "4:3", unscaled = false, R.drawable.aspect_ratio),
        ZoomMode("Original", keepAspect = true, aspectOverride = "no", unscaled = true, R.drawable.aspect_ratio_stretch)
    )
    private val hideControlsRunnable = Runnable {
        controlsOverlay.visibility = View.GONE
        controlsVisible = false
        enterImmersiveMode()
    }
    private val hideFeedbackRunnable = Runnable {
        feedbackView.visibility = View.GONE
    }
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updatePlaybackProgress()
            overlayHandler.postDelayed(this, ProgressUpdateMs)
        }
    }
    private var playbackStarted = false
    private var streamLoadInProgress = false
    private var attached = false
    private var surfaceReady = false
    private var pendingPlayback = false
    private var mpvRenderSurface: Surface? = null
    private var controlsVisible = false
    private var userSeeking = false
    private var repeatEnabled = false
    private var reconnectScheduled = false
    private var stoppingForLifecycle = false
    private var reconnectAttempt = 0
    private var audioStateRequestVersion = 0L
    private var audioFocusRequest: AudioFocusRequest? = null
    private var reconnectRunnable: Runnable? = null
    private var startupVolumeFallbackApplied = false
    private var muted = false
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var twoFingerStartSpanX = 0f
    private var twoFingerStartSpanY = 0f
    private var initialBrightness = DefaultBrightness
    private var initialVolume = 0
    private var activeGesture = PlayerGesture.None
    private var zoomModeIndex = 0
    @Volatile
    private var mpvVideoDetails: VideoTrackDetails? = null
    private var playbackTimeMs = 0L
    private var playbackLengthMs = 0L
    private var mpvPaused = false
    private var mpvHwDecoder = ""
    private var mpvVideoOutput = ""
    private var lastLoggedVideoDetailsKey = ""
    private val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentTarget: PlaybackTarget? = null
    private var currentBingeSession: AndroidBingeWatchSession? = null
    private var currentBingeIndex = -1
    private var markedTargetKey = ""
    private val preferencesRepository by lazy { AndroidDataStorePreferencesRepository(this) }
    private val mpvObserver = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) {
            postMpvEvent { handleMpvPropertyChanged(property, null) }
        }

        override fun eventProperty(property: String, value: Long) {
            postMpvEvent { handleMpvPropertyChanged(property, value) }
        }

        override fun eventProperty(property: String, value: Double) {
            postMpvEvent { handleMpvPropertyChanged(property, value) }
        }

        override fun eventProperty(property: String, value: Boolean) {
            postMpvEvent { handleMpvPropertyChanged(property, value) }
        }

        override fun eventProperty(property: String, value: String) {
            postMpvEvent { handleMpvPropertyChanged(property, value) }
        }

        override fun event(eventId: Int) {
            postMpvEvent { handleMpvEvent(eventId) }
        }
    }
    private val mpvLogObserver = object : MPVLib.LogObserver {
        override fun logMessage(prefix: String, level: Int, text: String) {
            if (level <= MPVLib.MpvLogLevel.MPV_LOG_LEVEL_INFO) {
                Log.d(LogTag, "mpv[$prefix] ${text.trim()}")
            }
        }
    }

    private fun postMpvEvent(action: () -> Unit) {
        if (activityDestroyed) {
            return
        }
        runOnUiThread {
            if (!activityDestroyed) {
                action()
            }
        }
    }

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
        currentTarget = targetFromIntent()
        loadEmbeddedPlayerPreference()
        if (intent.getStringExtra(NativePlayerActivity.EXTRA_URL).orEmpty().isBlank() && currentBingeSession == null) {
            finish()
            return
        }

        videoSurface = TextureView(this).apply {
            isClickable = true
            isFocusable = true
            setOnTouchListener { _, event -> handlePlayerTouch(event) }
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                    surfaceReady = true
                    updateVideoSurfaceTransform()
                    attachMpvSurface(Surface(surfaceTexture))
                    if (pendingPlayback) {
                        startPlayback()
                    }
                }

                override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                    updateVideoSurfaceTransform()
                }

                override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                    surfaceReady = false
                    attached = false
                    if (!stoppingForLifecycle && !activityDestroyed) {
                        stopMpvPlayback(async = true)
                    }
                    return true
                }

                override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
            }
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
            setOnTouchListener { _, event -> handlePlayerTouch(event) }
            addView(
                videoSurface,
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
        controlsOverlay.bringToFront()
        playlistOverlay.bringToFront()
        feedbackView.bringToFront()
        loadingSpinner.bringToFront()
        messageView.bringToFront()
        enterImmersiveMode()

        val createdMpv = MPVLib.create(this)
        if (createdMpv == null) {
            showMessage("mpv could not start on this device.")
            return
        }
        mpv = createdMpv
        createdMpv.addObserver(mpvObserver)
        createdMpv.addLogObserver(mpvLogObserver)
        configureMpvOptions(createdMpv)
        runCatching { createdMpv.init() }
            .onFailure {
                Log.e(LogTag, "Unable to initialize mpv", it)
                showMessage("mpv could not initialize.")
                return
            }
        observeMpvProperties(createdMpv)
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
        showControlsTemporarily()
    }

    override fun onStop() {
        stoppingForLifecycle = true
        cancelReconnect()
        overlayHandler.removeCallbacks(updateProgressRunnable)
        stopMpvPlayback(async = true)
        attached = false
        super.onStop()
    }

    override fun onDestroy() {
        activityDestroyed = true
        if (playbackStarted || playbackTimeMs > 0L) {
            (currentTarget ?: targetFromIntent())?.let(::markOpened)
        }
        if (isFinishing) {
            intent.getStringExtra(NativePlayerActivity.EXTRA_BINGE_SESSION_ID).orEmpty().takeIf { it.isNotBlank() }?.let(AndroidBingeWatchSessionStore::remove)
        }
        val player = mpv
        val renderSurface = mpvRenderSurface
        mpv = null
        mpvRenderSurface = null
        player?.let {
            runCatching { it.removeObserver(mpvObserver) }
            runCatching { it.removeLogObserver(mpvLogObserver) }
            destroyMpvAsync(it, renderSurface)
        } ?: renderSurface?.release()
        abandonAudioFocus()
        playbackScope.cancel()
        overlayHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveMode()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.isInsideVisibleOverlay(controlsOverlay) || event.isInsideVisibleOverlay(playlistOverlay)) {
            return super.dispatchTouchEvent(event)
        }
        return handlePlayerTouch(event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_SPACE -> {
                toggleControls()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                togglePlayPause()
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }

    private fun startPlayback() {
        val player = mpv ?: return
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
        val playbackTarget = target ?: return
        currentTarget = playbackTarget
        if (!surfaceReady) {
            pendingPlayback = true
            beginStreamLoading()
            return
        }
        if (!attached) {
            val surfaceTexture = videoSurface.surfaceTexture
            if (surfaceTexture == null) {
                pendingPlayback = true
                beginStreamLoading()
                return
            }
            attachMpvSurface(Surface(surfaceTexture))
        }
        requestAudioFocus()
        mpvVideoDetails = null
        lastLoggedVideoDetailsKey = ""
        playbackTimeMs = 0L
        playbackLengthMs = 0L
        mpvPaused = false
        pendingPlayback = false
        beginStreamLoading()
        ensureAudibleSystemVolume()
        val audioRequestVersion = markAudioStateSyncRequested()
        applyZoomMode(zoomModeIndex, showFeedback = false)
        applyDesiredAudioState()
        scheduleAudioStateStartupSync(audioRequestVersion)
        Log.d(LogTag, "mpv loading stream ${streamUrl.safeStreamDescriptor()}")
        player.runCatchingCommand("loadfile", streamUrl, "replace")
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
        stopMpvPlayback()
        beginStreamLoading()
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

    private suspend fun resolveForPlayback(target: PlaybackTarget): PlaybackTarget =
        withContext(Dispatchers.IO) {
            val databaseHelper = AndroidUiptvDatabaseHelper(this@MpvEmbeddedPlayerActivity)
            try {
                AndroidPlaybackCoordinator(
                    context = this@MpvEmbeddedPlayerActivity,
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
                    activeGesture = if (touchStartX < videoSurface.width / 2f) {
                        PlayerGesture.Brightness
                    } else {
                        PlayerGesture.Volume
                    }
                    controlsOverlay.visibility = View.GONE
                    controlsVisible = false
                }
                val delta = (-dy / videoSurface.height.coerceAtLeast(1)).coerceIn(-1f, 1f)
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

    private fun startTwoFingerZoomGesture(event: MotionEvent) {
        twoFingerStartSpanX = twoFingerSpanX(event)
        twoFingerStartSpanY = twoFingerSpanY(event)
        activeGesture = PlayerGesture.Zoom
        controlsOverlay.visibility = View.GONE
        controlsVisible = false
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
                    if (!isLiveStream() && playbackLengthMs > 0L) {
                        val targetSeconds = ((seekBar?.progress ?: 0).toDouble() / ProgressBarMax) * (playbackLengthMs / 1_000.0)
                        mpv?.runCatchingCommand("seek", targetSeconds.toString(), "absolute")
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
            playPauseButton = iconControlButton(R.drawable.video_player_pause, "Play/pause") { togglePlayPause() }
            addView(playPauseButton, compactControlLayoutParams())
            if (!isLiveStream()) {
                addView(iconControlButton(R.drawable.video_player_reverse_rewind, "Rewind 15 seconds") { seekBySeconds(-SeekStepSeconds) }, compactControlLayoutParams())
                addView(iconControlButton(R.drawable.video_player_fast_forward, "Fast forward 15 seconds") { seekBySeconds(SeekStepSeconds) }, compactControlLayoutParams())
            }
            addView(iconControlButton(R.drawable.reload, "Reload stream") { reloadCurrentStream() }, compactControlLayoutParams())
            addView(iconControlButton(R.drawable.video_player_stop, "Close player") { finish() }, compactControlLayoutParams())
            if (currentBingeSession != null) {
                addView(controlButton(PlaylistIcon) { togglePlaylistOverlay() }, compactControlLayoutParams())
            }
            muteButton = iconControlButton(R.drawable.mute_off, "Mute") { toggleMute() }
            updateMuteButton()
            addView(muteButton, compactControlLayoutParams())
            audioTrackButton = iconControlButton(R.drawable.audio_track, "Audio track") { cycleAudioTrack() }
            addView(audioTrackButton, compactControlLayoutParams())
            subtitleTrackButton = iconControlButton(R.drawable.subtitle_track, "Subtitles") { cycleSubtitleTrack() }
            updateTrackButtons()
            addView(subtitleTrackButton, compactControlLayoutParams())
            repeatButton = iconControlButton(R.drawable.repeat_off, "Repeat") { toggleRepeat() }
            updateRepeatButton()
            addView(repeatButton, compactControlLayoutParams())
            zoomButton = iconControlButton(zoomModes[zoomModeIndex].iconRes, "Zoom ${zoomModes[zoomModeIndex].label}") { cycleZoomMode() }
            addView(zoomButton, compactControlLayoutParams())
            if (isPictureInPictureAvailable()) {
                addView(iconControlButton(R.drawable.picture_in_picture, "Picture in picture") { enterPictureInPicture() }, compactControlLayoutParams())
            }
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
        val width = videoSurface.width.takeIf { it > 0 } ?: rootLayout.width.takeIf { it > 0 } ?: 16
        val height = videoSurface.height.takeIf { it > 0 } ?: rootLayout.height.takeIf { it > 0 } ?: 9
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
        val player = mpv ?: return
        val nextPaused = !mpvPaused
        if (!nextPaused) {
            requestAudioFocus()
            syncAudioStateAtStartup()
        }
        runCatching { player.setPropertyBoolean("pause", nextPaused) }
        mpvPaused = nextPaused
        updatePlayPauseButton()
    }

    private fun reloadCurrentStream() {
        cancelReconnect()
        reconnectAttempt = 0
        reconnectScheduled = false
        playbackStarted = false
        messageView.visibility = View.GONE
        hidePlaylistOverlay()
        showFeedback("Reloading")
        stopMpvPlayback()
        val session = currentBingeSession
        if (currentTarget == null && session != null) {
            playBingeIndex(if (currentBingeIndex >= 0) currentBingeIndex else session.startIndex)
        } else {
            startPlayback()
        }
    }

    private fun toggleMute() {
        muted = !muted
        if (!muted) {
            ensureAudibleSystemVolume()
        }
        applyDesiredAudioState()
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

    private fun cycleAudioTrack() {
        val player = mpv ?: return
        player.runCatchingCommand("cycle", "aid")
        overlayHandler.postDelayed({
            updateTrackButtons()
            showFeedback(currentTrackFeedback("audio", "aid", "Audio"))
        }, TrackFeedbackDelayMs)
    }

    private fun cycleSubtitleTrack() {
        val player = mpv ?: return
        player.runCatchingCommand("cycle", "sid")
        overlayHandler.postDelayed({
            updateTrackButtons()
            showFeedback(currentTrackFeedback("sub", "sid", "Subtitles"))
        }, TrackFeedbackDelayMs)
    }

    private fun updateTrackButtons() {
        if (::audioTrackButton.isInitialized) {
            audioTrackButton.background = controlButtonBackground(active = false)
        }
        if (::subtitleTrackButton.isInitialized) {
            subtitleTrackButton.background = controlButtonBackground(active = currentTrackIsEnabled("sid"))
        }
    }

    private fun currentTrackFeedback(trackType: String, idProperty: String, label: String): String {
        val player = mpv ?: return label
        if (!currentTrackIsEnabled(idProperty)) {
            return "$label off"
        }
        val trackLabel = player.firstNonBlankString(
            "current-tracks/$trackType/title",
            "current-tracks/$trackType/lang",
            "current-tracks/$trackType/codec"
        )
        val id = player.firstString(idProperty)
            .takeIf { it.isNotBlank() && !it.equals("no", ignoreCase = true) }
            ?.let { "#$it" }
        return listOf(trackLabel, id)
            .filterNot { it.isNullOrBlank() }
            .joinToString(" ")
            .ifBlank { "selected" }
            .let { "$label $it" }
    }

    private fun currentTrackIsEnabled(idProperty: String): Boolean {
        val value = mpv?.firstString(idProperty).orEmpty()
        return value.isNotBlank() && !value.equals("no", ignoreCase = true)
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
        if (isLiveStream()) {
            showFeedback(LiveTimeLabel)
            return
        }
        if (playbackLengthMs <= 0L) {
            showFeedback(LiveTimeLabel)
            return
        }
        mpv?.runCatchingCommand("seek", seconds.toString(), "relative")
        updatePlaybackProgress()
    }

    private fun startProgressUpdates() {
        overlayHandler.removeCallbacks(updateProgressRunnable)
        overlayHandler.post(updateProgressRunnable)
    }

    private fun updatePlaybackProgress() {
        if (!::progressSeekBar.isInitialized || !::timeLabel.isInitialized) {
            return
        }
        refreshMpvPlaybackState()
        val length = playbackLengthMs
        val current = playbackTimeMs.coerceAtLeast(0L)
        completeStreamLoadingIfClockStarted(current)
        updateStreamInfo()
        if (!isLiveStream() && length > 0L) {
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
        setButtonIcon(playPauseButton, if (!mpvPaused && playbackStarted) R.drawable.video_player_pause else R.drawable.video_player_play)
        playPauseButton.contentDescription = if (!mpvPaused && playbackStarted) "Pause" else "Play"
    }

    private fun scheduleReconnectIfNeeded(reason: String): Boolean {
        if (!repeatEnabled || stoppingForLifecycle || isFinishing || !playbackStarted) {
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

    private fun MotionEvent.isInsideVisibleOverlay(view: View): Boolean =
        view.visibility == View.VISIBLE &&
            x >= view.left &&
            x <= view.right &&
            y >= view.top &&
            y <= view.bottom

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
            overlayHandler.removeCallbacks(hideControlsRunnable)
            enterImmersiveMode()
        } else {
            controlsOverlay.visibility = View.VISIBLE
            controlsVisible = true
            scheduleControlsHide()
        }
    }

    private fun showControlsTemporarily() {
        if (!::controlsOverlay.isInitialized) {
            return
        }
        controlsOverlay.visibility = View.VISIBLE
        controlsOverlay.bringToFront()
        controlsVisible = true
        scheduleControlsHide()
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
            updateMuteButton()
            saveEmbeddedPlayerPreference()
        }
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, bounded, 0)
        val percent = (bounded * 100f / maxVolume).roundToInt()
        applyDesiredAudioState()
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
        applyDesiredAudioState()
        scheduleAudioStateStartupSync(version)
    }

    private fun scheduleAudioStateStartupSync(requestVersion: Long) {
        scheduleAudioStateRetry(requestVersion, 150L)
        scheduleAudioStateRetry(requestVersion, 450L)
        scheduleAudioStateRetry(requestVersion, 1_000L)
    }

    private fun scheduleAudioStateRetry(requestVersion: Long, delayMs: Long) {
        overlayHandler.postDelayed({
            if (requestVersion == audioStateRequestVersion) {
                applyDesiredAudioState()
            }
        }, delayMs)
    }

    private fun applyDesiredAudioState() {
        val player = mpv ?: return
        runCatching { player.setPropertyBoolean("mute", muted) }
        runCatching { player.setPropertyDouble("volume", if (muted) 0.0 else MpvAudibleVolume) }
    }

    private fun ensureAudibleSystemVolume() {
        if (muted) {
            return
        }
        if (startupVolumeFallbackApplied) {
            return
        }
        startupVolumeFallbackApplied = true
        val maxVolume = maxMusicVolume()
        if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0) {
            return
        }
        val fallback = (maxVolume * StartupVolumeFallbackRatio).roundToInt().coerceIn(1, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, fallback, 0)
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

    private fun applyZoomMode(index: Int, showFeedback: Boolean = true) {
        zoomModeIndex = index.coerceIn(zoomModes.indices)
        val mode = zoomModes[zoomModeIndex]
        mpv?.let { player ->
            runCatching { player.setPropertyBoolean("keepaspect", mode.keepAspect) }
            runCatching { player.setPropertyString("video-aspect-override", mode.aspectOverride) }
            runCatching { player.setPropertyString("video-unscaled", if (mode.unscaled) "yes" else "no") }
        }
        updateVideoSurfaceTransform()
        setButtonIcon(zoomButton, mode.iconRes)
        zoomButton.contentDescription = "Zoom ${mode.label}"
        if (showFeedback) {
            showFeedback("Zoom ${mode.label}")
        }
    }

    private fun updateVideoSurfaceTransform() {
        if (!::videoSurface.isInitialized) {
            return
        }
        if (!mpvVideoOutput.equals("mediacodec_embed", ignoreCase = true)) {
            videoSurface.setTransform(Matrix())
            return
        }
        val viewWidth = videoSurface.width.toFloat()
        val viewHeight = videoSurface.height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f) {
            return
        }
        val mode = zoomModes[zoomModeIndex]
        if (!mode.keepAspect) {
            videoSurface.setTransform(Matrix())
            return
        }
        val sourceSize = mode.aspectOverride
            .takeIf { it != "no" }
            ?.split(":")
            ?.takeIf { it.size == 2 }
            ?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == 2 && it[0] > 0f && it[1] > 0f }
            ?: mpvVideoDetails
                ?.takeIf { it.hasResolution() }
                ?.let { listOf(it.displayWidth.toFloat(), it.displayHeight.toFloat()) }
            ?: return
        val sourceWidth = sourceSize[0]
        val sourceHeight = sourceSize[1]
        val scale = if (mode.unscaled) {
            1f
        } else {
            minOf(viewWidth / sourceWidth, viewHeight / sourceHeight)
        }
        val scaledWidth = sourceWidth * scale
        val scaledHeight = sourceHeight * scale
        val matrix = Matrix().apply {
            setScale(
                scaledWidth / viewWidth,
                scaledHeight / viewHeight,
                viewWidth / 2f,
                viewHeight / 2f
            )
        }
        videoSurface.setTransform(matrix)
    }

    private fun updateStreamInfo() {
        if (!::streamInfoLabel.isInitialized) {
            return
        }
        if (::titleLabel.isInitialized) {
            titleLabel.text = currentTarget?.title
                ?.takeIf { it.isNotBlank() }
                ?: intent.getStringExtra(NativePlayerActivity.EXTRA_TITLE).orEmpty().ifBlank { "Embedded player" }
        }
        streamInfoLabel.text = buildStreamInfoLabel()
    }

    private fun handleMpvEvent(eventId: Int) {
        when (eventId) {
            MPVLib.MpvEvent.MPV_EVENT_START_FILE -> {
                beginStreamLoading()
                updatePlayPauseButton()
            }
            MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                playbackStarted = true
                reconnectAttempt = 0
                reconnectScheduled = false
                mpvPaused = false
                currentTarget?.let(::markOpened)
                messageView.visibility = View.GONE
                syncAudioStateAtStartup()
                scheduleStreamInfoRefreshes()
                updatePlayPauseButton()
            }
            MPVLib.MpvEvent.MPV_EVENT_VIDEO_RECONFIG,
            MPVLib.MpvEvent.MPV_EVENT_AUDIO_RECONFIG,
            MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
                completeStreamLoading()
                refreshMpvTrackDetails()
                scheduleStreamInfoRefreshes()
                if (eventId == MPVLib.MpvEvent.MPV_EVENT_VIDEO_RECONFIG) {
                    showControlsTemporarily()
                }
                updatePlayPauseButton()
            }
            MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                completeStreamLoading()
                mpvPaused = false
                updatePlayPauseButton()
                if (!playNextBingeIfNeeded()) {
                    scheduleReconnectIfNeeded("Stream stopped")
                }
            }
            MPVLib.MpvEvent.MPV_EVENT_SHUTDOWN -> {
                completeStreamLoading()
                updatePlayPauseButton()
            }
        }
    }

    private fun handleMpvPropertyChanged(property: String, value: Any?) {
        when (property) {
            "time-pos" -> playbackTimeMs = secondsToMillis(value)
            "duration" -> playbackLengthMs = secondsToMillis(value)
            "pause" -> mpvPaused = value as? Boolean ?: mpvPaused
            "hwdec-current" -> mpvHwDecoder = (value as? String).orEmpty()
            "current-vo" -> {
                mpvVideoOutput = (value as? String).orEmpty()
                updateVideoSurfaceTransform()
                scheduleStreamInfoRefreshes()
            }
            "width",
            "height",
            "dwidth",
            "dheight",
            "video-codec" -> {
                refreshMpvTrackDetails()
                scheduleStreamInfoRefreshes()
            }
            "aid",
            "sid" -> updateTrackButtons()
        }
        updatePlaybackProgressLabelOnly()
        updatePlayPauseButton()
    }

    private fun secondsToMillis(value: Any?): Long =
        when (value) {
            is Double -> (value * 1_000.0).roundToInt().toLong().coerceAtLeast(0L)
            is Long -> (value * 1_000L).coerceAtLeast(0L)
            is Int -> (value * 1_000L).coerceAtLeast(0L)
            else -> 0L
        }

    private fun scheduleStreamInfoRefreshes() {
        updateStreamInfo()
        overlayHandler.postDelayed({ updateStreamInfo() }, StreamInfoRefreshShortDelayMs)
        overlayHandler.postDelayed({ updateStreamInfo() }, StreamInfoRefreshMediumDelayMs)
        overlayHandler.postDelayed({ updateStreamInfo() }, StreamInfoRefreshLongDelayMs)
    }

    private fun buildStreamInfoLabel(): String {
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
        val videoOutput = mpvVideoOutput
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { " | VO $it" }
            .orEmpty()
        return "$resolution | $codec | ${decoderModeLabel()}$videoOutput"
    }

    private fun currentVideoTrackDetails(): VideoTrackDetails? =
        if (!playbackStarted && mpvVideoDetails == null) {
            null
        } else {
            refreshMpvTrackDetails() ?: mpvVideoDetails
        }

    private fun refreshMpvTrackDetails(): VideoTrackDetails? {
        val player = mpv ?: return mpvVideoDetails
        val displayWidth = player.firstPositiveInt("dwidth", "width", "video-out-params/w", "video-params/w")
        val displayHeight = player.firstPositiveInt("dheight", "height", "video-out-params/h", "video-params/h")
        val codec = player.firstNonBlankString("video-codec", "current-tracks/video/codec", "track-list/0/codec")
        val hwDecoder = player.firstString("hwdec-current")
        val videoOutput = player.firstString("current-vo")
        if (hwDecoder.isNotBlank()) {
            mpvHwDecoder = hwDecoder
        }
        if (videoOutput.isNotBlank()) {
            mpvVideoOutput = videoOutput
        }
        val details = VideoTrackDetails(
            displayWidth = displayWidth,
            displayHeight = displayHeight,
            codec = codec
        ).takeIf { it.hasUsefulInfo() }
        if (details != null) {
            mpvVideoDetails = details
            updateVideoSurfaceTransform()
            val key = "${details.displayWidth}x${details.displayHeight}|${details.codec}|$mpvHwDecoder|$mpvVideoOutput"
            if (key != lastLoggedVideoDetailsKey) {
                lastLoggedVideoDetailsKey = key
                Log.d(LogTag, "mpv video details ${details.displayWidth}x${details.displayHeight} codec=${details.codec} hw=$mpvHwDecoder vo=$mpvVideoOutput")
            }
        }
        return details
    }

    private fun decoderModeLabel(): String {
        val decoder = mpvHwDecoder.trim()
        return when {
            decoder.isBlank() -> "HW auto"
            decoder.equals("no", ignoreCase = true) -> "SW decode"
            else -> "HW $decoder"
        }
    }

    private fun MPVLib.firstPositiveInt(vararg properties: String): Int {
        for (property in properties) {
            val value = runCatching { getPropertyInt(property) }.getOrNull()
            if (value != null && value > 0) {
                return value
            }
        }
        return 0
    }

    private fun MPVLib.firstNonBlankString(vararg properties: String): String {
        for (property in properties) {
            val value = runCatching { getPropertyString(property) }.getOrNull()
                ?.trim()
                .orEmpty()
            if (value.isNotBlank() && value != "no") {
                return value
            }
        }
        return ""
    }

    private fun MPVLib.firstString(vararg properties: String): String {
        for (property in properties) {
            val value = runCatching { getPropertyString(property) }.getOrNull()
                ?.trim()
                .orEmpty()
            if (value.isNotBlank()) {
                return value
            }
        }
        return ""
    }

    private fun configureMpvOptions(player: MPVLib) {
        val stableMobileAudio = shouldUseStableMobileAudioProfile()
        Log.d(LogTag, "mpv stable mobile audio profile=$stableMobileAudio outputDevices=${audioOutputDeviceTypes()}")
        player.setMpvOption("profile", "fast")
        player.setMpvOption("vo", "mediacodec_embed,gpu")
        player.setMpvOption("gpu-context", "android")
        player.setMpvOption("opengl-es", "yes")
        player.setMpvOption("ao", if (stableMobileAudio) "opensles,audiotrack" else "audiotrack,opensles")
        player.setMpvOption("audio-set-media-role", "yes")
        player.setMpvOption("audio-buffer", if (stableMobileAudio) StableMobileAudioBufferSeconds else DefaultAudioBufferSeconds)
        if (stableMobileAudio) {
            player.setMpvOption("audio-channels", "stereo")
            player.setMpvOption("audio-format", "s16")
        }
        player.setMpvOption("hwdec", "mediacodec,mediacodec-copy")
        player.setMpvOption("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        player.setMpvOption("hwdec-software-fallback", "yes")
        player.setMpvOption("input-default-bindings", "yes")
        player.setMpvOption("scale", "bilinear")
        player.setMpvOption("cscale", "bilinear")
        player.setMpvOption("dscale", "bilinear")
        player.setMpvOption("correct-downscaling", "no")
        player.setMpvOption("linear-downscaling", "no")
        player.setMpvOption("sigmoid-upscaling", "no")
        player.setMpvOption("interpolation", "no")
        player.setMpvOption("deband", "no")
        player.setMpvOption("video-sync", "audio")
        player.setMpvOption("ytdl", "no")
        player.setMpvOption("user-agent", ChromeUserAgent)
        player.setMpvOption("tls-verify", "no")
        player.setMpvOption("stream-lavf-o", "timeout=10000000,reconnect=1,reconnect_on_network_error=1,reconnect_streamed=1,reconnect_delay_max=2,reconnect_max_retries=1,multiple_requests=0")
        player.setMpvOption("hls-bitrate", "max")
        player.setMpvOption("cache", "yes")
        player.setMpvOption("cache-secs", "20")
        player.setMpvOption("cache-pause", "no")
        player.setMpvOption("cache-pause-initial", "no")
        player.setMpvOption("cache-pause-wait", "0")
        player.setMpvOption("demuxer-cache-wait", "no")
        player.setMpvOption("demuxer-readahead-secs", "20")
        player.setMpvOption("demuxer-max-bytes", "64MiB")
        player.setMpvOption("demuxer-max-back-bytes", "0")
        player.setMpvOption("prefetch-playlist", "no")
        player.setMpvOption("network-timeout", "15")
        player.setMpvOption("keep-open", "no")
    }

    private fun shouldUseStableMobileAudioProfile(): Boolean {
        val outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        if (outputDevices.any { it.isExternalMultichannelSink() }) {
            return false
        }
        if (outputDevices.any { it.isPhoneClassOutput() }) {
            return true
        }
        return Build.HARDWARE.equals("qcom", ignoreCase = true) &&
            Build.BOARD.contains("sdm660", ignoreCase = true)
    }

    private fun audioOutputDeviceTypes(): String =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .joinToString(",") { it.type.toString() }
            .ifBlank { "none" }

    private fun AudioDeviceInfo.isExternalMultichannelSink(): Boolean =
        when (type) {
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> true
            else -> false
        }

    private fun AudioDeviceInfo.isPhoneClassOutput(): Boolean =
        when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_USB_HEADSET -> true
            else -> false
        }

    private fun observeMpvProperties(player: MPVLib) {
        player.observe("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        player.observe("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        player.observe("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        player.observe("width", MPVLib.MpvFormat.MPV_FORMAT_INT64)
        player.observe("height", MPVLib.MpvFormat.MPV_FORMAT_INT64)
        player.observe("dwidth", MPVLib.MpvFormat.MPV_FORMAT_INT64)
        player.observe("dheight", MPVLib.MpvFormat.MPV_FORMAT_INT64)
        player.observe("video-codec", MPVLib.MpvFormat.MPV_FORMAT_STRING)
        player.observe("hwdec-current", MPVLib.MpvFormat.MPV_FORMAT_STRING)
        player.observe("current-vo", MPVLib.MpvFormat.MPV_FORMAT_STRING)
        player.observe("aid", MPVLib.MpvFormat.MPV_FORMAT_STRING)
        player.observe("sid", MPVLib.MpvFormat.MPV_FORMAT_STRING)
    }

    private fun attachMpvSurface(surface: Surface) {
        if (!surface.isValid) {
            return
        }
        runCatching {
            if (mpvRenderSurface !== surface) {
                mpvRenderSurface?.release()
                mpvRenderSurface = surface
            }
            mpv?.attachSurface(surface)
            mpv?.runCatchingCommand("set", "vid", "auto")
            attached = true
            Log.d(LogTag, "mpv surface attached")
        }.onFailure {
            Log.w(LogTag, "Unable to attach mpv surface", it)
        }
    }

    private fun stopMpvPlayback(async: Boolean = false) {
        val player = mpv
        if (async && player != null) {
            runMpvCommandAsync(player, "stop")
        } else {
            runCatching { player?.runCatchingCommand("stop") }
        }
        playbackTimeMs = 0L
        playbackLengthMs = 0L
        mpvPaused = false
        updatePlayPauseButton()
    }

    private fun runMpvCommandAsync(player: MPVLib, vararg args: String) {
        MpvReleaseExecutor.execute {
            runCatching { player.command(args.toList().toTypedArray()) }
                .onFailure { Log.w(LogTag, "mpv async command failed: ${args.joinToString(" ")}", it) }
        }
    }

    private fun destroyMpvAsync(player: MPVLib, surface: Surface?) {
        MpvReleaseExecutor.execute {
            runCatching { player.command(arrayOf("stop")) }
                .onFailure { Log.w(LogTag, "Unable to stop mpv before destroy", it) }
            runCatching { player.destroy() }
                .onFailure { Log.w(LogTag, "Unable to destroy mpv", it) }
            surface?.release()
        }
    }

    private fun refreshMpvPlaybackState() {
        val player = mpv ?: return
        runCatching { player.getPropertyDouble("time-pos") }
            .getOrNull()
            ?.let { playbackTimeMs = secondsToMillis(it) }
        runCatching { player.getPropertyDouble("duration") }
            .getOrNull()
            ?.let { playbackLengthMs = secondsToMillis(it) }
        runCatching { player.getPropertyBoolean("pause") }
            .getOrNull()
            ?.let { mpvPaused = it }
    }

    private fun updatePlaybackProgressLabelOnly() {
        if (!::timeLabel.isInitialized || !::progressSeekBar.isInitialized) {
            return
        }
        if (!isLiveStream() && playbackLengthMs > 0L) {
            if (!userSeeking) {
                progressSeekBar.progress = ((playbackTimeMs.coerceAtMost(playbackLengthMs) * ProgressBarMax) / playbackLengthMs).toInt()
            }
            timeLabel.text = "${formatTime(playbackTimeMs)} / ${formatTime(playbackLengthMs)}"
        } else {
            if (!userSeeking) {
                progressSeekBar.progress = 0
            }
            timeLabel.text = LiveTimeLabel
        }
    }

    private fun MPVLib.setMpvOption(name: String, value: String) {
        val result = runCatching { setOptionString(name, value) }.getOrDefault(-1)
        Log.d(LogTag, "mpv option $name=$value result=$result")
    }

    private fun MPVLib.observe(property: String, format: Int) {
        runCatching { observeProperty(property, format) }
            .onFailure { Log.w(LogTag, "Unable to observe mpv property $property", it) }
    }

    private fun MPVLib.runCatchingCommand(vararg args: String) {
        runCatching { command(args.toList().toTypedArray()) }
            .onFailure { Log.w(LogTag, "mpv command failed: ${args.joinToString(" ")}", it) }
    }

    private fun showFeedback(message: String, durationMs: Long = GestureFeedbackMs) {
        feedbackView.text = message
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
        messageView.visibility = View.VISIBLE
    }

    private fun isLiveStream(): Boolean =
        currentTarget?.mode == BrowseMode.LIVE

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

    private data class ZoomMode(
        val label: String,
        val keepAspect: Boolean,
        val aspectOverride: String,
        val unscaled: Boolean,
        val iconRes: Int
    )

    private data class VideoTrackDetails(
        val displayWidth: Int,
        val displayHeight: Int,
        val codec: String
    ) {
        val area: Int = displayWidth.coerceAtLeast(0) * displayHeight.coerceAtLeast(0)

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
        private const val LogTag = "UIPTV-MpvEmbedded"
        private const val ChromeUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"
        private const val StreamInfoRefreshShortDelayMs = 250L
        private const val StreamInfoRefreshMediumDelayMs = 1_000L
        private const val StreamInfoRefreshLongDelayMs = 2_500L
        private const val TrackFeedbackDelayMs = 120L
        private const val ProgressBarMax = 1_000
        private const val MpvAudibleVolume = 100.0
        private const val SeekStepSeconds = 15
        private const val BrightnessStep = 0.10f
        private const val VolumeStep = 0.10f
        private const val ControlButtonSizeDp = 48
        private const val ControlIconPaddingDp = 13
        private const val StartupVolumeFallbackRatio = 0.35f
        private const val DefaultBrightness = 0.50f
        private const val MinimumBrightness = 0.05f
        private const val GestureSlopPx = 18f
        private const val TwoFingerZoomSlopDp = 56
        private const val TapSlopPx = 14f
        private const val LiveTimeLabel = "Live"
        private const val DefaultAudioBufferSeconds = "0.35"
        private const val StableMobileAudioBufferSeconds = "0.85"
        private const val PlayIcon = "\u25B6"
        private const val PauseIcon = "\u23F8"
        private const val StopIcon = "\u25A0"
        private const val RewindIcon = "\u23EA"
        private const val ForwardIcon = "\u23E9"
        private const val PlaylistIcon = "List"
        private const val CloseIcon = "\u2715"
        private val MpvReleaseExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "uiptv-mpv-release").apply { isDaemon = true }
        }
    }
}

private fun String.isPlayableNetworkUrl(): Boolean {
    val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase()
    return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
}

private fun String.safeStreamDescriptor(): String {
    val uri = runCatching { Uri.parse(trim()) }.getOrNull() ?: return "non-url"
    val scheme = uri.scheme?.lowercase()
    if ((scheme != "http" && scheme != "https") || uri.host.isNullOrBlank()) {
        return "non-url"
    }
    val lastSegment = uri.lastPathSegment.orEmpty()
    val pathHint = when {
        uri.path.isNullOrBlank() -> ""
        lastSegment.isNotBlank() -> "/$lastSegment"
        else -> uri.path.orEmpty().take(24)
    }
    return "$scheme://${uri.host}$pathHint"
}
