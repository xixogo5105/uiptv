package com.uiptv.mobile.android

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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
import java.lang.reflect.Field
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.HWDecoderUtil
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.abs
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
    private lateinit var playPauseButton: TextView
    private lateinit var progressSeekBar: SeekBar
    private lateinit var streamInfoLabel: TextView
    private lateinit var titleLabel: TextView
    private lateinit var timeLabel: TextView
    private lateinit var zoomButton: TextView
    private lateinit var repeatButton: TextView
    private lateinit var muteButton: TextView
    private val overlayHandler = Handler(Looper.getMainLooper())
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
    private var selectedVideoTrackId = UnknownTrackId
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
        enterImmersiveMode()

        val options = arrayListOf(
            "--aout=$PreferredAudioOutput",
            "--http-reconnect",
            "--network-caching=1500",
            "--live-caching=1500",
            "--file-caching=1500",
            "--rtsp-tcp",
            "--no-drop-late-frames",
            "--no-skip-frames"
        )
        val createdLibVlc = LibVLC(this, options)
        val createdPlayer = MediaPlayer(createdLibVlc).apply {
            configureAudioOutput()
            setEventListener { event ->
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
                            updateStreamInfo()
                        }
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
        cancelReconnect()
        setMusicStreamMuted(false)
        overlayHandler.removeCallbacks(updateProgressRunnable)
        mediaPlayer?.runCatchingStop()
        if (attached) {
            mediaPlayer?.detachViews()
            attached = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (playbackStarted || (mediaPlayer?.time ?: 0L) > 0L) {
            (currentTarget ?: targetFromIntent())?.let(::markOpened)
        }
        if (isFinishing) {
            intent.getStringExtra(NativePlayerActivity.EXTRA_BINGE_SESSION_ID).orEmpty().takeIf { it.isNotBlank() }?.let(AndroidBingeWatchSessionStore::remove)
        }
        mediaPlayer?.release()
        mediaPlayer = null
        abandonAudioFocus()
        libVlc?.release()
        libVlc = null
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

    private fun startPlayback() {
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
        if (!attached) {
            createdPlayer.attachViews(videoLayout, null, false, false)
            attached = true
        }
        createdPlayer.configureAudioOutput()
        requestAudioFocus()
        selectedVideoTrackId = UnknownTrackId
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
        mediaPlayer?.runCatchingStop()
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
                    val player = mediaPlayer
                    if (player != null && (player.length > 0L)) {
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
            playPauseButton = controlButton(PauseIcon) { togglePlayPause() }
            addView(playPauseButton, compactControlLayoutParams())
            addView(controlButton(StopIcon) { finish() }, compactControlLayoutParams())
            addView(controlButton(RewindIcon) { seekBySeconds(-SeekStepSeconds) }, compactControlLayoutParams())
            addView(controlButton(ForwardIcon) { seekBySeconds(SeekStepSeconds) }, compactControlLayoutParams())
            if (currentBingeSession != null) {
                addView(controlButton(PlaylistIcon) { togglePlaylistOverlay() }, compactControlLayoutParams())
            }
            muteButton = iconControlButton(R.drawable.mute_off, "Mute") { toggleMute() }
            updateMuteButton()
            addView(muteButton, compactControlLayoutParams())
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
            minWidth = dp(44)
            minHeight = dp(36)
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = roundedBackground(Color.argb(230, 37, 45, 54), dp(18), Color.argb(120, 79, 216, 235))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                onClick()
                scheduleControlsHide()
            }
        }

    private fun iconControlButton(drawableRes: Int, description: String, onClick: () -> Unit): TextView =
        controlButton("", onClick).apply {
            contentDescription = description
            setButtonIcon(this, drawableRes)
        }

    private fun setButtonIcon(button: TextView, drawableRes: Int) {
        val drawable = resources.getDrawable(drawableRes, theme).mutate()
        val size = dp(ControlIconSizeDp)
        drawable.setBounds(0, 0, size, size)
        drawable.setTint(Color.WHITE)
        button.text = ""
        button.setCompoundDrawables(drawable, null, null, null)
        button.compoundDrawablePadding = 0
        button.minWidth = dp(44)
        button.minHeight = dp(36)
    }

    private fun compactControlLayoutParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dp(44), dp(36)).apply {
            leftMargin = dp(4)
            rightMargin = dp(4)
        }

    private fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            requestAudioFocus()
            syncAudioStateAtStartup()
            player.play()
        }
        updatePlayPauseButton()
    }

    private fun toggleMute() {
        muted = !muted
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
        muteButton.background = roundedBackground(
            color = if (muted) Color.argb(240, 34, 83, 93) else Color.argb(230, 37, 45, 54),
            radius = dp(18),
            strokeColor = if (muted) Color.rgb(79, 216, 235) else Color.argb(120, 79, 216, 235)
        )
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
        repeatButton.background = roundedBackground(
            color = if (repeatEnabled) Color.argb(240, 34, 83, 93) else Color.argb(230, 37, 45, 54),
            radius = dp(18),
            strokeColor = if (repeatEnabled) Color.rgb(79, 216, 235) else Color.argb(120, 79, 216, 235)
        )
    }

    private fun seekBySeconds(seconds: Int) {
        val player = mediaPlayer ?: return
        val length = player.length
        if (length <= 0L) {
            showFeedback(LiveTimeLabel)
            return
        }
        val target = (player.time + seconds * 1_000L).coerceIn(0L, length)
        player.setTime(target)
        updatePlaybackProgress()
    }

    private fun startProgressUpdates() {
        overlayHandler.removeCallbacks(updateProgressRunnable)
        overlayHandler.post(updateProgressRunnable)
    }

    private fun updatePlaybackProgress() {
        val player = mediaPlayer ?: return
        if (!::progressSeekBar.isInitialized || !::timeLabel.isInitialized) {
            return
        }
        val length = player.length
        val current = player.time.coerceAtLeast(0L)
        completeStreamLoadingIfClockStarted(current)
        updateStreamInfo()
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
        playPauseButton.text = if (mediaPlayer?.isPlaying == true) PauseIcon else PlayIcon
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
        if (!muted) {
            ensureAudioTrackSelected()
        }
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
        mediaPlayer?.let { player ->
            runCatching { player.setAudioDigitalOutputEnabled(false) }
            setMusicStreamMuted(muted)
            if (muted) {
                val volumeResult = runCatching { player.setVolume(0) }.getOrDefault(0)
                Log.d(LogTag, "Applied VLC mute volumeResult=$volumeResult")
                return
            }
            ensureAudioTrackSelected()
            val result = runCatching { player.setVolume(VlcAudibleVolume) }.getOrDefault(0)
            Log.d(LogTag, "Applied VLC volume=$VlcAudibleVolume result=$result")
        }
    }

    private fun setMusicStreamMuted(value: Boolean) {
        val direction = if (value) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
        runCatching { audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0) }
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
        if (tracks.isNotEmpty()) {
            Log.i(
                LogTag,
                "Audio tracks current=$currentTrack available=${tracks.joinToString { "${it.id}:${it.name}" }}"
            )
        }
        if (currentTrack != UnknownTrackId) {
            return
        }
        val firstPlayableTrack = tracks.firstOrNull { it.id >= 0 } ?: return
        val selected = runCatching { player.setAudioTrack(firstPlayableTrack.id) }.getOrDefault(false)
        Log.i(LogTag, "Selected audio track id=${firstPlayableTrack.id} name=${firstPlayableTrack.name} result=$selected")
    }

    private fun ensureAudibleSystemVolume() {
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

    private fun applyZoomMode(index: Int) {
        zoomModeIndex = index.coerceIn(zoomModes.indices)
        val mode = zoomModes[zoomModeIndex]
        mediaPlayer?.setVideoScale(mode.scaleType)
        setButtonIcon(zoomButton, mode.iconRes)
        zoomButton.contentDescription = "Zoom ${mode.label}"
        showFeedback("Zoom ${mode.label}")
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

    private fun handleElementaryStreamChanged(event: MediaPlayer.Event) {
        if (event.esChangedType == IMedia.Track.Type.Audio) {
            syncAudioStateAtStartup()
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
        return "$resolution | $codec | ${decoderModeLabel()}"
    }

    private fun currentVideoTrackDetails(): VideoTrackDetails? {
        val player = mediaPlayer ?: return null
        val media = runCatching { player.media }.getOrNull()
        currentRenderedVideoLayoutDetails(player)
            ?.takeIf { it.hasUsefulInfo() }
            ?.let { return it }
        runCatching { player.currentVideoTrack }
            .getOrNull()
            ?.toVideoTrackDetails()
            ?.takeIf { it.hasUsefulInfo() }
            ?.let { return it }

        val selectedTrackId = selectedVideoTrackId.takeIf { it != UnknownTrackId }
            ?: runCatching { player.videoTrack }.getOrNull()?.takeIf { it != UnknownTrackId }
        if (media != null && selectedTrackId != null) {
            findVideoTrackById(media, selectedTrackId)?.let { return it }
        }
        return media?.let { bestUsefulVideoTrack(it) }
    }

    private fun currentRenderedVideoLayoutDetails(player: MediaPlayer): VideoTrackDetails? {
        val helper = runCatching { VideoHelperField?.get(player) }.getOrNull() ?: return null
        val videoWidth = helper.intField("mVideoWidth")
        val videoHeight = helper.intField("mVideoHeight")
        val visibleWidth = helper.intField("mVideoVisibleWidth").takeIf { it > 0 } ?: videoWidth
        val visibleHeight = helper.intField("mVideoVisibleHeight").takeIf { it > 0 } ?: videoHeight
        if (visibleWidth <= 0 || visibleHeight <= 0) {
            return null
        }
        val sarNum = helper.intField("mVideoSarNum")
        val sarDen = helper.intField("mVideoSarDen")
        val sampleAspectRatio = if (sarNum > 0 && sarDen > 0) {
            sarNum.toFloat() / sarDen.toFloat()
        } else {
            1f
        }
        val adjustedWidth = (visibleWidth * sampleAspectRatio).roundToInt().coerceAtLeast(1)
        return VideoTrackDetails(
            displayWidth = adjustedWidth,
            displayHeight = visibleHeight,
            codec = currentVideoCodec(player)
        )
    }

    private fun currentVideoCodec(player: MediaPlayer): String =
        runCatching { player.currentVideoTrack }
            .getOrNull()
            ?.codec
            ?.takeIf { it.isNotBlank() }
            ?: runCatching { player.media }.getOrNull()?.let(::bestUsefulVideoTrack)?.codec.orEmpty()

    private fun Any.intField(name: String): Int =
        runCatching {
            javaClass.getDeclaredField(name).apply { isAccessible = true }.getInt(this)
        }.getOrDefault(0)

    private fun findVideoTrackById(media: IMedia, trackId: Int): VideoTrackDetails? =
        runCatching {
            for (index in 0 until media.trackCount) {
                val track = media.getTrack(index)
                if (track is IMedia.VideoTrack && track.id == trackId) {
                    return@runCatching track.toVideoTrackDetails().takeIf { it.hasUsefulInfo() }
                }
            }
            null
        }.getOrNull()

    private fun bestUsefulVideoTrack(media: IMedia): VideoTrackDetails? =
        runCatching {
            var bestTrack: VideoTrackDetails? = null
            for (index in 0 until media.trackCount) {
                val track = media.getTrack(index)
                if (track is IMedia.VideoTrack) {
                    val details = track.toVideoTrackDetails()
                    if (details.hasUsefulInfo() && (bestTrack == null || details.area > bestTrack.area)) {
                        bestTrack = details
                    }
                }
            }
            bestTrack
        }.getOrNull()

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
            if (isPlaying) {
                stop()
            }
        }
    }

    private data class ZoomMode(
        val label: String,
        val scaleType: MediaPlayer.ScaleType,
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
        private const val ControlIconSizeDp = 18
        private const val StartupVolumeFallbackRatio = 0.35f
        private const val DefaultBrightness = 0.50f
        private const val MinimumBrightness = 0.05f
        private const val GestureSlopPx = 18f
        private const val TwoFingerZoomSlopDp = 56
        private const val TapSlopPx = 14f
        private const val UnknownTrackId = -1
        private const val VideoOrientationLeftBottom = 1
        private const val VideoOrientationRightTop = 3
        private const val LiveTimeLabel = "Live"
        private const val PlayIcon = "\u25B6"
        private const val PauseIcon = "\u23F8"
        private const val StopIcon = "\u25A0"
        private const val RewindIcon = "\u23EA"
        private const val ForwardIcon = "\u23E9"
        private const val PlaylistIcon = "List"
        private const val CloseIcon = "\u2715"
        private val VideoHelperField: Field? = runCatching {
            MediaPlayer::class.java.getDeclaredField("mVideoHelper").apply { isAccessible = true }
        }.getOrNull()
    }
}

private fun String.isPlayableNetworkUrl(): Boolean {
    val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase()
    return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
}
