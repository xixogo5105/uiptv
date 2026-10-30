package com.uiptv.mobile.android

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import android.os.SystemClock
import android.os.Looper
import android.util.Rational
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewConfiguration
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
import androidx.activity.ComponentActivity
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.ComposeView
import com.uiptv.mobile.shared.browse.BrowseMode
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import com.uiptv.mobile.shared.i18n.MobileMessages
import com.uiptv.mobile.shared.playback.MobileResolutionDisplay
import com.uiptv.mobile.shared.playback.PlaybackTarget
import com.uiptv.mobile.shared.playback.MobilePlaybackIndicator
import com.uiptv.mobile.shared.logging.AndroidAppLog
import com.uiptv.mobile.shared.settings.AndroidDataStorePreferencesRepository
import com.uiptv.mobile.shared.settings.EmbeddedPlayerRenderProfile
import com.uiptv.mobile.shared.settings.EmbeddedPlayerPreference
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.roundToInt

class MpvEmbeddedPlayerActivity : ComponentActivity() {
    private data class PlayerPalette(
        val controlSurface: Int,
        val buttonSurface: Int,
        val accentContainer: Int,
        val onAccentContainer: Int,
        val onSurface: Int,
        val onSurfaceVariant: Int,
        val outline: Int,
        val primary: Int
    )

    private lateinit var playerPalette: PlayerPalette
    private val lockOverlayIconTint: Int get() = playerPalette.onSurface
    private val M3ControlSurface: Int get() = playerPalette.controlSurface
    private val M3OverlaySurface: Int get() = withAlpha(M3ControlSurface, 184)
    private val M3ButtonSurface: Int get() = playerPalette.buttonSurface
    private val M3AccentContainer: Int get() = playerPalette.accentContainer
    private val M3OnAccent: Int get() = playerPalette.onAccentContainer
    private val M3OnSurface: Int get() = playerPalette.onSurface
    private val M3OnSurfaceVariant: Int get() = playerPalette.onSurfaceVariant
    private val M3SurfaceOutline: Int get() = playerPalette.outline

    private val mpvTasks = MpvTaskQueue()
    @Volatile private var mpv: MPVLib? = null
    // Accessed only by mpvTasks, including cleanup if initialization is still in flight.
    private var nativePlayer: MPVLib? = null
    private var mpvInitializing = false
    private var softwareVideoFallback = false
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
    private var progressSeekBarPresentationIsLive: Boolean? = null
    private lateinit var streamInfoLabel: TextView
    private lateinit var epgLabel: TextView
    private lateinit var epgPanel: ScrollView
    private lateinit var epgScrim: View
    private lateinit var epgDetails: LinearLayout
    private lateinit var epgPanelContent: LinearLayout
    private lateinit var epgGuideButton: TextView
    private lateinit var surfCarouselView: ComposeView
    private lateinit var recordingDot: View
    private var epgResult: com.uiptv.mobile.shared.epg.MobileEpgResult? = null
    private var epgPanelSide = Gravity.END
    private var epgLoadedKey: String? = null
    private var edgeSwipeSide = 0
    private var panelTouchX = 0f
    private var panelTouchY = 0f
    private var epgJob: kotlinx.coroutines.Job? = null
    private var epgPreferenceJob: kotlinx.coroutines.Job? = null
    @Volatile private var epgEnabled = false
    private val epgDatabaseDelegate = lazy { AndroidUiptvDatabaseHelper(applicationContext) }
    private val epgDatabase by epgDatabaseDelegate
    private val epgRepository by lazy { com.uiptv.mobile.shared.epg.AndroidEpgRepository(this, epgDatabase, preferencesRepository) }
    private lateinit var titleLabel: TextView
    private lateinit var timeLabel: TextView
    private lateinit var zoomButton: ImageButton
    private lateinit var repeatButton: ImageButton
    private lateinit var muteButton: ImageButton
    private lateinit var audioTrackButton: ImageButton
    private lateinit var subtitleTrackButton: ImageButton
    private val overlayHandler = Handler(Looper.getMainLooper())
    private val zoomModes = listOf(
        ZoomMode("fit", "Fit", keepAspect = true, cropToFill = false, R.drawable.video_scale_fit),
        ZoomMode("crop", "Crop", keepAspect = true, cropToFill = true, R.drawable.video_scale_crop),
        ZoomMode("stretch", "Stretch", keepAspect = false, cropToFill = false, R.drawable.video_scale_stretch)
    )
    private val hideControlsRunnable = Runnable {
        controlsOverlay.visibility = View.GONE
        controlsVisible = false
        enterImmersiveMode()
    }
    private val hideSurfCarouselRunnable = Runnable { closeSurfCarousel() }
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
    private var suppressReconnectForNextEndFile = false
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
    private var persistedBrightness: Float? = null
    private var persistedVolumePercent: Int? = null
    private var persistedZoomMode: String? = null
    private var embeddedPreferenceLoaded = false
    @Volatile
    private var mpvVideoDetails: VideoTrackDetails? = null
    @Volatile private var playbackTimeMs = 0L
    @Volatile private var playbackLengthMs = 0L
    private var mpvPaused = false
    private var mpvHwDecoder = ""
    private var mpvVideoOutput = ""
    private var mpvAudioCodec = ""
    private var subtitleTrackEnabled = false
    private var streamInfoRefreshPending = false
    private var embeddedRenderProfile = EmbeddedPlayerRenderProfile.DIRECT_MEDIACODEC
    private var renderedPlayPauseState: Boolean? = null
    private var lastDiagnosticMs = 0L
    private var lastProgressTickMs = 0L
    private var maxProgressTickDelayMs = 0L
    private val streamInfoRefreshRunnable = Runnable {
        if (!activityDestroyed && !stoppingForLifecycle) {
            refreshMpvTrackDetails()
        }
    }
    private val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val hlsProxyLock = Any()
    private var hlsProxy: EmbeddedHlsProxy? = null
    @Volatile private var streamLoadGeneration = 0L
    private var currentTarget: PlaybackTarget? = null
    private var currentBingeSession: AndroidBingeWatchSession? = null
    private var currentBingeIndex = -1
    private var currentSurfSession: AndroidPlaybackSurfSession? = null
    private var currentSurfIndex = -1
    private var surfCarouselOpen = false
    private var surfCarouselContentCreated = false
    private var surfCarouselTouchStartX = Float.NaN
    private var surfCarouselTouchStartY = Float.NaN
    private val surfCurrentIndexState = mutableIntStateOf(-1)
    private val surfCarouselOpenGenerationState = mutableIntStateOf(0)
    private var surfSwitchGeneration = 0L
    private var surfSwitchJob: kotlinx.coroutines.Job? = null
    private var markedTargetKey = ""
    private val preferencesRepository by lazy { AndroidDataStorePreferencesRepository(this) }
    private var locked = false
    private lateinit var lockButton: ImageButton
    private lateinit var lockOverlay: LinearLayout
    private lateinit var unlockButton: ImageButton
    private lateinit var lockedMuteButton: ImageButton
    private lateinit var lockHelpText: TextView
    private var unlockTapTime = 0L
    private var unlockHideRunnable: Runnable? = null
    private val mpvObserver = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) {
            dispatchMpvProperty(property, null)
        }

        override fun eventProperty(property: String, value: Long) {
            dispatchMpvProperty(property, value)
        }

        override fun eventProperty(property: String, value: Double) {
            dispatchMpvProperty(property, value)
        }

        override fun eventProperty(property: String, value: Boolean) {
            dispatchMpvProperty(property, value)
        }

        override fun eventProperty(property: String, value: String) {
            dispatchMpvProperty(property, value)
        }

        override fun event(eventId: Int) {
            postMpvEvent { handleMpvEvent(eventId) }
        }
    }
    private val mpvLogObserver = object : MPVLib.LogObserver {
        override fun logMessage(prefix: String, level: Int, text: String) {
            if (AndroidAppLog.isEnabled) AndroidAppLog.d("UIPTV-Mpv", "[$prefix] ${text.trimEnd()}")
            // The bundled MPV reports hardware decoder failure before trying its
            // next decoder. Never call native code back from its event thread.
            if (prefix == "vd" &&
                (text.contains("Could not open codec") || text.contains("Error while decoding frame (hardware decoding)"))
            ) {
                val generation = streamLoadGeneration
                postMpvEvent {
                    if (generation == streamLoadGeneration) recoverVideoWithSoftwareDecoder()
                }
            }
        }
    }

    private fun recoverVideoWithSoftwareDecoder() {
        val player = mpv ?: return
        if (softwareVideoFallback || embeddedRenderProfile != EmbeddedPlayerRenderProfile.DIRECT_MEDIACODEC) return
        softwareVideoFallback = true
        AndroidAppLog.w(LogTag, "Hardware video decoding failed; switching this player session to software decoding with GPU output")
        runMpvOperation(player, streamScoped = false) {
            it.setPropertyString("vid", "no")
            it.setPropertyString("vo", "gpu")
            it.setPropertyString("hwdec", "no")
            it.setPropertyString("vid", "auto")
        }
    }
    private fun dispatchMpvProperty(property: String, value: Any?) {
        if (activityDestroyed) return
        // Clock notifications can arrive every video frame. Keep them off the UI
        // queue; the one-second ticker presents the latest clock when visible.
        when (property) {
            "time-pos" -> playbackTimeMs = secondsToMillis(value)
            "duration" -> playbackLengthMs = secondsToMillis(value)
            else -> postMpvEvent { handleMpvPropertyChanged(property, value) }
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
        playerPalette = resolvePlayerPalette()
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        volumeControlStream = AudioManager.STREAM_MUSIC

        currentBingeSession = intent.getStringExtra(NativePlayerActivity.EXTRA_BINGE_SESSION_ID)
            .orEmpty()
            .takeIf { it.isNotBlank() }
            ?.let(AndroidBingeWatchSessionStore::get)
        currentSurfSession = intent.getStringExtra(NativePlayerActivity.EXTRA_SURF_SESSION_ID)
            .orEmpty()
            .takeIf { it.isNotBlank() }
            ?.let(AndroidPlaybackSurfSessionStore::get)
        currentSurfIndex = currentSurfSession?.let { session ->
            intent.getIntExtra(NativePlayerActivity.EXTRA_SURF_START_INDEX, session.startIndex)
                .coerceIn(session.targets.indices)
        } ?: -1
        currentTarget = targetFromIntent()
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
                }

                override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                    updateVideoSurfaceTransform()
                }

                override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                    surfaceReady = false
                    streamLoadGeneration += 1
                    attached = false
                    if (!stoppingForLifecycle && !activityDestroyed) {
                        pendingPlayback = true
                        stopMpvPlayback()
                    }
                    val player = mpv
                    val surface = mpvRenderSurface
                    mpvRenderSurface = null
                    // TextureView transfers ownership to us. Keep its producer alive until MPV
                    // has detached on the same queue used for native release.
                    mpvTasks.execute {
                        try {
                            runCatching { player?.detachSurface() }
                                .onFailure { AndroidAppLog.w(LogTag, "Unable to detach mpv surface") }
                        } finally {
                            surface?.release()
                            surfaceTexture.release()
                        }
                    }
                    if (activityDestroyed) mpvTasks.close()
                    return false
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
            elevation = dp(12).toFloat()
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
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                    leftMargin = dp(12)
                    rightMargin = dp(12)
                    bottomMargin = dp(12)
                }
            )
            addView(
                surfCarouselView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(158),
                    Gravity.BOTTOM
                ).apply {
                    bottomMargin = dp(12)
                }
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
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM).apply {
                    bottomMargin = dp(120)
                }
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
        renderSurfCarousel()
        controlsOverlay.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (feedbackView.visibility == View.VISIBLE) positionFeedbackAboveControls()
        }
        controlsOverlay.bringToFront()
        surfCarouselView.bringToFront()
        playlistOverlay.bringToFront()
        feedbackView.bringToFront()
        loadingSpinner.bringToFront()
        messageView.bringToFront()
        epgPanel = createEpgPanel()
        rootLayout.addView(epgPanel, FrameLayout.LayoutParams(minOf(dp(360), resources.displayMetrics.widthPixels * 4 / 5), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))
        epgScrim = View(this).apply {
            setBackgroundColor(Color.argb(150, 0, 0, 0))
            visibility = View.GONE
        }
        rootLayout.addView(epgScrim, 1, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        epgPanel.bringToFront()
        unlockButton = ImageButton(this).apply {
            contentDescription = "Unlock"
            minimumWidth = dp(72)
            minimumHeight = dp(72)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = roundedBackground(M3ControlSurface, dp(24), M3SurfaceOutline)
            elevation = dp(4).toFloat()
            scaleType = ImageView.ScaleType.FIT_CENTER
            imageTintList = ColorStateList.valueOf(lockOverlayIconTint)
            isClickable = true
            isFocusable = true
            setOnClickListener { handleUnlockTap() }
        }
        lockHelpText = TextView(this).apply {
            contentDescription = "Lock help"
            setTextColor(M3OnSurface)
            setTextSize(14f)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = roundedBackground(M3ControlSurface, dp(18), M3SurfaceOutline)
            elevation = dp(2).toFloat()
        }
        lockOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            addView(unlockButton, LinearLayout.LayoutParams(dp(72), dp(72)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            })
            addView(lockHelpText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(8)
            })
        }
        rootLayout.addView(lockOverlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        })
        lockedMuteButton = ImageButton(this).apply {
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = lockedMuteButtonBackground()
            elevation = dp(3).toFloat()
            scaleType = ImageView.ScaleType.FIT_CENTER
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            visibility = View.GONE
            setTag(R.id.player_theme_button_role, "lockedMute")
            setOnClickListener {
                toggleMute()
                if (locked) showUnlockButton() else showUnlockedConfirmation()
            }
        }
        updateLockedMuteButton()
        rootLayout.addView(
            lockedMuteButton,
            FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(12)
                marginEnd = dp(12)
            }
        )
        applyPlayerTheme()
        enterImmersiveMode()
        loadEmbeddedPlayerPreference()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        playerPalette = resolvePlayerPalette()
        renderedPlayPauseState = null
        updateLiveIndicatorTheme()
        if (::rootLayout.isInitialized) applyPlayerTheme()
    }

    private fun updateLiveIndicatorTheme() {
        if (!::recordingDot.isInitialized) return
        val indicator = recordingDot as? ViewGroup ?: return
        if (indicator.childCount == 0) return
        val color = if (
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        ) Color.WHITE else Color.BLACK
        val ring = indicator.getChildAt(0) as? ViewGroup ?: return
        (ring.background as? GradientDrawable)?.setStroke(dp(1), color)
        ring.getChildAt(0)?.background?.let { background ->
            (background as? GradientDrawable)?.setColor(color)
        }
    }

    private fun resolvePlayerPalette(): PlayerPalette {
        val dynamicScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                dynamicDarkColorScheme(this)
            }.getOrNull()
        } else null
        if (dynamicScheme != null) {
            return PlayerPalette(
                controlSurface = withAlpha(dynamicScheme.surfaceContainerHigh.toArgb(), 244),
                buttonSurface = dynamicScheme.surfaceContainerHighest.toArgb(),
                accentContainer = dynamicScheme.primaryContainer.toArgb(),
                onAccentContainer = dynamicScheme.onPrimaryContainer.toArgb(),
                onSurface = dynamicScheme.onSurface.toArgb(),
                onSurfaceVariant = dynamicScheme.onSurfaceVariant.toArgb(),
                outline = withAlpha(dynamicScheme.outlineVariant.toArgb(), 58),
                primary = dynamicScheme.primary.toArgb(),
            )
        }

        return PlayerPalette(
            controlSurface = Color.argb(244, 28, 27, 31),
            buttonSurface = Color.rgb(55, 58, 63),
            accentContainer = Color.rgb(79, 216, 235),
            onAccentContainer = Color.rgb(0, 35, 40),
            onSurface = Color.rgb(241, 240, 245),
            onSurfaceVariant = Color.rgb(202, 197, 208),
            outline = Color.argb(58, 255, 255, 255),
            primary = Color.rgb(79, 216, 235)
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun applyPlayerTheme() {
        if (::controlsOverlay.isInitialized) {
            controlsOverlay.background = roundedBackground(M3OverlaySurface, dp(24), M3SurfaceOutline)
        }
        if (::feedbackView.isInitialized) {
            feedbackView.setTextColor(M3OnSurface)
            feedbackView.background = roundedBackground(M3ControlSurface, dp(18), M3SurfaceOutline)
        }
        if (::messageView.isInitialized) {
            messageView.setTextColor(M3OnSurface)
            messageView.background = roundedBackground(M3ControlSurface, dp(18), M3SurfaceOutline)
        }
        if (::loadingSpinner.isInitialized) {
            loadingSpinner.indeterminateTintList = ColorStateList.valueOf(playerPalette.primary)
        }
        if (::progressSeekBar.isInitialized) {
            updateProgressSeekBarAppearance()
        }
        if (::playlistOverlay.isInitialized) {
            playlistOverlay.background = roundedBackground(M3ControlSurface, dp(12), M3SurfaceOutline)
        }
        if (::epgPanelContent.isInitialized) epgPanelContent.setBackgroundColor(M3ControlSurface)
        if (::unlockButton.isInitialized) {
            unlockButton.background = roundedBackground(M3ControlSurface, dp(24), M3SurfaceOutline)
            unlockButton.imageTintList = ColorStateList.valueOf(lockOverlayIconTint)
            (unlockButton.drawable)?.mutate()?.setTint(lockOverlayIconTint)
        }
        if (::lockHelpText.isInitialized) {
            lockHelpText.setTextColor(M3OnSurface)
            lockHelpText.background = roundedBackground(M3ControlSurface, dp(18), M3SurfaceOutline)
        }
        if (::lockedMuteButton.isInitialized) {
            lockedMuteButton.background = lockedMuteButtonBackground()
            setButtonIcon(lockedMuteButton, if (muted) R.drawable.mute_on else R.drawable.mute_off)
        }
        if (::rootLayout.isInitialized) applyPaletteToViewTree(rootLayout)
    }

    private fun applyPaletteToViewTree(view: View) {
        if (view is TextView) {
            val textRole = view.getTag(R.id.player_theme_text_role) as? String
            view.setTextColor(if (textRole == "variant") M3OnSurfaceVariant else M3OnSurface)
            if (view.getTag(R.id.player_theme_button_role) == "button") {
                view.background = controlButtonBackground(active = false)
            } else if (view.getTag(R.id.player_theme_button_role) == "playlistRow") {
                val index = view.getTag(R.id.player_theme_row_index) as? Int ?: -1
                val selected = index == currentBingeIndex
                view.background = roundedBackground(
                    color = if (selected) M3AccentContainer else M3ButtonSurface,
                    radius = dp(8),
                    strokeColor = if (selected) M3SurfaceOutline else null
                )
            }
        } else if (view is ImageButton && view.getTag(R.id.player_theme_button_role) == "button") {
            (view.tag as? Int)?.let { setButtonIcon(view, it) }
            view.background = controlButtonBackground(isControlButtonActive(view))
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) applyPaletteToViewTree(view.getChildAt(index))
        }
    }

    private fun isControlButtonActive(button: ImageButton): Boolean =
        (::muteButton.isInitialized && button === muteButton && muted) ||
            (::repeatButton.isInitialized && button === repeatButton && repeatEnabled) ||
            (::subtitleTrackButton.isInitialized && button === subtitleTrackButton && subtitleTrackEnabled) ||
            (::lockButton.isInitialized && button === lockButton && locked)

    override fun onResume() {
        super.onResume()
        enterImmersiveMode()
    }

    override fun onStart() {
        super.onStart()
        stoppingForLifecycle = false
        epgEnabled = false
        if (surfSwitchJob?.isActive != true) startPlayback()
        startProgressUpdates()
        epgLoadedKey = null
        epgPreferenceJob?.cancel()
        epgPreferenceJob = playbackScope.launch {
            val enabled = preferencesRepository.load().epgEnabled
            if (!activityDestroyed && !stoppingForLifecycle) {
                epgEnabled = enabled
                refreshEpg()
            }
        }
        showControlsTemporarily()
    }

    override fun onStop() {
        stoppingForLifecycle = true
        epgPreferenceJob?.cancel()
        MobilePlaybackIndicator.clear(currentTarget)
        epgJob?.cancel()
        epgResult = null
        epgLoadedKey = null
        if (::epgDetails.isInitialized) epgDetails.removeAllViews()
        if (::titleLabel.isInitialized) titleLabel.text = currentTarget?.title.orEmpty()
        hideEpgPanel()
        streamLoadGeneration += 1
        cancelReconnect()
        closeHlsProxyAsync()
        overlayHandler.removeCallbacks(updateProgressRunnable)
        stopMpvPlayback()
        super.onStop()
    }

    private fun closeHlsProxyAsync() {
        val proxy = synchronized(hlsProxyLock) { hlsProxy.also { hlsProxy = null } }
        proxy?.let { Thread({ it.close() }, "uiptv-hls-close").start() }
    }

    override fun onDestroy() {
        activityDestroyed = true
        surfSwitchGeneration += 1
        surfSwitchJob?.cancel()
        streamLoadGeneration += 1
        closeHlsProxyAsync()
        MobilePlaybackIndicator.clear(currentTarget)
        if (playbackStarted || playbackTimeMs > 0L) {
            (currentTarget ?: targetFromIntent())?.let(::markOpened)
        }
        if (isFinishing) {
            intent.getStringExtra(NativePlayerActivity.EXTRA_BINGE_SESSION_ID).orEmpty().takeIf { it.isNotBlank() }?.let(AndroidBingeWatchSessionStore::remove)
            intent.getStringExtra(NativePlayerActivity.EXTRA_SURF_SESSION_ID).orEmpty().takeIf { it.isNotBlank() }?.let(AndroidPlaybackSurfSessionStore::remove)
        }
        val renderSurface = mpvRenderSurface
        mpv = null
        mpvRenderSurface = null
        destroyMpvAsync(renderSurface)
        // The TextureView callback queues producer release after native teardown.
        if (!surfaceReady) mpvTasks.close()
        abandonAudioFocus()
        playbackScope.cancel()
        playbackScope.coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion {
            if (epgDatabaseDelegate.isInitialized()) epgDatabase.close()
        }
        overlayHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveMode()
        }
    }

    private var playerOwnsTouchGesture = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (locked && ::unlockButton.isInitialized) {
            if (::lockedMuteButton.isInitialized && lockedMuteButton.visibility == View.VISIBLE &&
                event.isInsideVisibleOverlay(lockedMuteButton)) {
                return super.dispatchTouchEvent(event)
            }
            if (unlockButton.visibility == View.VISIBLE &&
                event.isInsideVisibleOverlay(unlockButton)) {
                return super.dispatchTouchEvent(event)
            }
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                showUnlockButton()
            }
            return true
        }
        if (surfCarouselOpen) {
            val insideSurfCarousel = event.isInsideVisibleOverlay(surfCarouselView)
            if (event.actionMasked == MotionEvent.ACTION_DOWN && !insideSurfCarousel) {
                closeSurfCarousel()
                return true
            }
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                surfCarouselTouchStartX = event.rawX
                surfCarouselTouchStartY = event.rawY
            } else if (event.actionMasked == MotionEvent.ACTION_UP && !surfCarouselTouchStartY.isNaN()) {
                val deltaX = event.rawX - surfCarouselTouchStartX
                val deltaY = event.rawY - surfCarouselTouchStartY
                surfCarouselTouchStartX = Float.NaN
                surfCarouselTouchStartY = Float.NaN
                if (deltaY > dp(48) && deltaY > abs(deltaX)) {
                    closeSurfCarousel()
                    return true
                }
            } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                surfCarouselTouchStartX = Float.NaN
                surfCarouselTouchStartY = Float.NaN
            }
            val isTouchInteraction = event.actionMasked == MotionEvent.ACTION_DOWN ||
                event.actionMasked == MotionEvent.ACTION_MOVE ||
                event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            if (insideSurfCarousel && isTouchInteraction) {
                scheduleSurfCarouselAutoHide()
            }
            return super.dispatchTouchEvent(event)
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            playerOwnsTouchGesture = !event.isInsideVisibleOverlay(epgPanel) &&
                !event.isInsideVisibleOverlay(controlsOverlay) && !event.isInsideVisibleOverlay(playlistOverlay)
        }
        // Keep the original target for the whole gesture, even when it crosses an overlay edge.
        // Otherwise a video swipe loses its UP event to the drawer it is trying to dismiss.
        val ownedByPlayer = playerOwnsTouchGesture
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            playerOwnsTouchGesture = false
        }
        return if (ownedByPlayer) handlePlayerTouch(event) else super.dispatchTouchEvent(event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (surfCarouselOpen) { closeSurfCarousel(); true }
                else if (epgPanel.visibility == View.VISIBLE) { hideEpgPanel(); true }
                else super.onKeyUp(keyCode, event)
            }
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
        val player = mpv
        if (player == null) {
            pendingPlayback = true
            beginStreamLoading()
            return
        }
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
            pendingPlayback = true
            beginStreamLoading()
            if (mpvRenderSurface == null) {
                val surfaceTexture = videoSurface.surfaceTexture ?: return
                attachMpvSurface(Surface(surfaceTexture))
            }
            return
        }
        requestAudioFocus()
        mpvVideoDetails = null
        mpvHwDecoder = ""
        mpvAudioCodec = ""
        playbackTimeMs = 0L
        playbackLengthMs = 0L
        mpvPaused = false
        pendingPlayback = false
        beginStreamLoading()
        ensureAudibleSystemVolume()
        val audioRequestVersion = markAudioStateSyncRequested()
        val generation = ++streamLoadGeneration
        subtitleTrackEnabled = false
        applyZoomMode(zoomModeIndex, showFeedback = false)
        applyDesiredAudioState()
        scheduleAudioStateStartupSync(audioRequestVersion)
        playbackScope.launch {
            val playbackUrl = withContext(Dispatchers.IO) {
                synchronized(hlsProxyLock) {
                    if (activityDestroyed || stoppingForLifecycle || generation != streamLoadGeneration) {
                        return@withContext streamUrl
                    }
                    // A new load owns a fresh proxy; retire downloads from the old stream.
                    hlsProxy?.close()
                    hlsProxy = null
                    if (playbackTarget.manifestType.equals("hls", true) || Uri.parse(streamUrl).path.orEmpty().endsWith(".m3u8", true)) {
                        EmbeddedHlsProxy().also { hlsProxy = it }.playbackUrl(streamUrl)
                    } else streamUrl
                }
            }
            if (!activityDestroyed && generation == streamLoadGeneration) {
                runMpvCommandAsync(player, "loadfile", playbackUrl, "replace")
            }
        }
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

    private suspend fun resolveForPlayback(
        target: PlaybackTarget,
        forceStalkerRefresh: Boolean = false
    ): PlaybackTarget =
        withContext(Dispatchers.IO) {
            val databaseHelper = AndroidUiptvDatabaseHelper(this@MpvEmbeddedPlayerActivity)
            try {
                val resolved = AndroidPlaybackCoordinator(
                    context = this@MpvEmbeddedPlayerActivity,
                    preferences = preferencesRepository,
                    databaseHelper = databaseHelper
                ).resolvePlayableTarget(target, forceStalkerRefresh)
                resolveAndroidStreamTarget(resolved)
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
        AndroidPlaybackWatchStateStore.recordOpened(this, target)
    }

    private fun handlePlayerTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                edgeSwipeSide = edgeSideAt(event.x)
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
                if (activeGesture == PlayerGesture.Guide) return true
                if (isLiveStream() && abs(dx) > dp(EdgeSwipeTravelDp) && abs(dx) > abs(dy)) {
                    if (epgPanel.visibility == View.VISIBLE) {
                        if ((epgPanelSide == Gravity.START && dx < 0) || (epgPanelSide == Gravity.END && dx > 0)) hideEpgPanel()
                    } else if (edgeSwipeSide == Gravity.START && dx > 0) showEpgPanel(Gravity.START)
                    else if (edgeSwipeSide == Gravity.END && dx < 0) showEpgPanel(Gravity.END)
                    activeGesture = PlayerGesture.Guide
                    return true
                }
                if (activeGesture == PlayerGesture.None) {
                    if (abs(dy) <= GestureSlopPx || abs(dy) <= abs(dx)) {
                        return true
                    }
                    if (edgeSwipeSide != 0 && !(abs(dy) > dp(EdgeVerticalClaimDp) && abs(dy) > abs(dx) * 2f)) {
                        return true
                    }
                    edgeSwipeSide = 0
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
                    PlayerGesture.Guide -> Unit
                    PlayerGesture.None -> Unit
                }
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                val dx = event.x - touchStartX
                val dy = event.y - touchStartY
                val shouldPersistDisplaySettings = activeGesture == PlayerGesture.Brightness ||
                    activeGesture == PlayerGesture.Volume
                if (event.actionMasked == MotionEvent.ACTION_UP && activeGesture == PlayerGesture.None &&
                    abs(dx) > dp(EdgeSwipeTravelDp) && abs(dx) > abs(dy)) {
                    if (epgPanel.visibility == View.VISIBLE) {
                        if ((epgPanelSide == Gravity.START && dx < 0) || (epgPanelSide == Gravity.END && dx > 0)) hideEpgPanel()
                    } else if (edgeSwipeSide == Gravity.START && dx > 0) showEpgPanel(Gravity.START)
                    else if (edgeSwipeSide == Gravity.END && dx < 0) showEpgPanel(Gravity.END)
                    edgeSwipeSide = 0
                    return true
                }
                val wasTap = activeGesture == PlayerGesture.None &&
                    abs(dx) < TapSlopPx &&
                    abs(dy) < TapSlopPx
                if (wasTap && epgPanel.visibility == View.VISIBLE && !event.isInsideVisibleOverlay(epgPanel)) {
                    hideEpgPanel()
                } else if (wasTap) {
                    toggleControls()
                } else {
                    hidePlaylistOverlay()
                    enterImmersiveMode()
                }
                if (shouldPersistDisplaySettings) {
                    saveEmbeddedPlayerPreference()
                }
                activeGesture = PlayerGesture.None
                edgeSwipeSide = 0
                return true
            }
        }
        return true
    }

    private fun edgeSideAt(x: Float): Int {
        if (!isLiveStream() || !::rootLayout.isInitialized || rootLayout.width <= 0) {
            return 0
        }
        val edge = dp(EdgeSwipeZoneDp)
        return when {
            x <= edge -> Gravity.START
            x >= rootLayout.width - edge -> Gravity.END
            else -> 0
        }
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
            CropZoomModeIndex
        } else {
            FitZoomModeIndex
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
            setHorizontallyScrolling(true)
            gravity = Gravity.CENTER_VERTICAL
        }
        epgLabel = TextView(this).apply {
            setTextColor(Color.WHITE); setTextSize(11f); maxLines = 2; visibility = View.GONE
        }
        surfCarouselView = ComposeView(this).apply { visibility = View.GONE }
        epgGuideButton = controlButton("Guide") { showEpgPanel() }.apply {
            contentDescription = "Show programme guide"; visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(12), 0, dp(12), 0)
            }
        }
        val closeButton = controlButton(CloseIcon) { finish() }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(8), 0, dp(8), 0)
            }
        }
        streamInfoLabel = TextView(this).apply {
            text = buildStreamInfoLabel()
            setTextColor(M3OnSurfaceVariant)
            setTag(R.id.player_theme_text_role, "variant")
            setTextSize(9f)
            maxLines = 1
            setHorizontallyScrolling(true)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 0)
            background = null
        }
        val titleScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            clipToPadding = false
            addView(titleLabel, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val codecScroller = object : HorizontalScrollView(this) {
        }.apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            clipToPadding = false
            addView(streamInfoLabel, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val titleAndCodecRow = object : LinearLayout(this) {
            init {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(titleScroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(ControlHeaderHeightDp)))
                addView(codecScroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(ControlHeaderHeightDp)))
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
                if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED || availableWidth <= 0) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                    return
                }

                val headerHeight = MeasureSpec.makeMeasureSpec(dp(ControlHeaderHeightDp), MeasureSpec.EXACTLY)
                val codecMaximumWidth = (availableWidth * CodecAreaMaximumFraction).toInt().coerceAtLeast(1)
                codecScroller.measure(MeasureSpec.makeMeasureSpec(codecMaximumWidth, MeasureSpec.AT_MOST), headerHeight)

                val titleMaximumWidth = (availableWidth - codecScroller.measuredWidth - dp(ControlHeaderGapDp)).coerceAtLeast(0)
                titleScroller.measure(MeasureSpec.makeMeasureSpec(titleMaximumWidth, MeasureSpec.AT_MOST), headerHeight)

                val contentWidth = titleScroller.measuredWidth + dp(ControlHeaderGapDp) + codecScroller.measuredWidth
                val contentHeight = maxOf(titleScroller.measuredHeight, codecScroller.measuredHeight, dp(ControlHeaderHeightDp))
                setMeasuredDimension(resolveSize(contentWidth, widthMeasureSpec), resolveSize(contentHeight, heightMeasureSpec))
            }

            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                val contentHeight = bottom - top
                val titleTop = (contentHeight - titleScroller.measuredHeight) / 2
                titleScroller.layout(0, titleTop, titleScroller.measuredWidth, titleTop + titleScroller.measuredHeight)
                val codecLeft = titleScroller.measuredWidth + dp(ControlHeaderGapDp)
                val codecTop = (contentHeight - codecScroller.measuredHeight) / 2
                codecScroller.layout(codecLeft, codecTop, codecLeft + codecScroller.measuredWidth, codecTop + codecScroller.measuredHeight)
            }
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val liveIndicatorColor = if (
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            ) Color.WHITE else Color.BLACK
            recordingDot = FrameLayout(this@MpvEmbeddedPlayerActivity).apply {
                contentDescription = "Live broadcast"
                addView(FrameLayout(this@MpvEmbeddedPlayerActivity).apply {
                    contentDescription = "Live broadcast indicator"
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.TRANSPARENT)
                        setStroke(dp(1), liveIndicatorColor)
                    }
                    addView(View(this@MpvEmbeddedPlayerActivity).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(liveIndicatorColor)
                        }
                    }, FrameLayout.LayoutParams(dp(3), dp(3), Gravity.CENTER))
                }, FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER))
                visibility = if (isLiveStream()) View.VISIBLE else View.GONE
            }
            addView(recordingDot, LinearLayout.LayoutParams(dp(RecordingDotSlotWidthDp), dp(ControlHeaderHeightDp)).apply {
                rightMargin = dp(RecordingDotTitleGapDp)
            })
            addView(titleAndCodecRow, LinearLayout.LayoutParams(0, dp(ControlHeaderHeightDp), 1f))
            addView(LinearLayout(this@MpvEmbeddedPlayerActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(epgGuideButton)
                addView(closeButton)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(ControlHeaderHeightDp)))
        }
        progressSeekBar = SeekBar(this).apply {
            max = ProgressBarMax
            progress = if (isLiveStream()) ProgressBarMax else 0
            scaleY = 0.75f
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
        updateProgressSeekBarAppearance()
        timeLabel = TextView(this).apply {
            text = LiveTimeLabel
            setTextColor(M3OnSurfaceVariant)
            setTag(R.id.player_theme_text_role, "variant")
            setTextSize(10f)
            gravity = Gravity.CENTER
            minWidth = dp(48)
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
            zoomButton = iconControlButton(zoomModes[zoomModeIndex].iconRes, "Video scale: ${zoomModes[zoomModeIndex].label}") { cycleZoomMode() }
            addView(zoomButton, compactControlLayoutParams())
            if (isPictureInPictureAvailable()) {
                addView(iconControlButton(R.drawable.picture_in_picture, "Picture in picture") { enterPictureInPicture() }, compactControlLayoutParams())
            }
            lockButton = iconControlButton(R.drawable.unlock_icon, "Lock") { toggleLock() }
            addView(lockButton, compactControlLayoutParams())
            if ((currentSurfSession?.targets?.size ?: 0) >= 2) {
                addView(
                    iconControlButton(
                        R.drawable.channel_surf,
                        MobileMessages.text("epgCloseGuide")
                    ) { toggleSurfCarousel() },
                    compactControlLayoutParams()
                )
            }
        }
        val controlsScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            addView(buttonRow)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground(M3OverlaySurface, dp(24), M3SurfaceOutline)
            elevation = dp(6).toFloat()
            visibility = View.GONE
            addView(epgLabel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(titleRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(progressRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            })
            addView(controlsScroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
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
            background = roundedBackground(M3ControlSurface, dp(12), M3SurfaceOutline)
            visibility = View.GONE
            addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)))
        }
    }

    private fun playlistRow(index: Int, target: PlaybackTarget): TextView =
        TextView(this).apply {
            text = playlistRowLabel(index, target)
            setTextColor(Color.WHITE)
            setTag(R.id.player_theme_button_role, "playlistRow")
            setTag(R.id.player_theme_row_index, index)
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
            AndroidAppLog.w(LogTag, "Unable to enter picture-in-picture", it)
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
            setTag(R.id.player_theme_button_role, "button")
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
            setTag(R.id.player_theme_button_role, "button")
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
                if (!surfCarouselOpen) scheduleControlsHide()
            }
        }

    private fun setButtonIcon(button: ImageButton, drawableRes: Int) {
        val drawable = resources.getDrawable(drawableRes, theme).mutate()
        val active = (::muteButton.isInitialized && button === muteButton && muted) ||
            (::repeatButton.isInitialized && button === repeatButton && repeatEnabled) ||
            (::subtitleTrackButton.isInitialized && button === subtitleTrackButton && subtitleTrackEnabled) ||
            (::lockButton.isInitialized && button === lockButton && locked)
        drawable.setTint(if (active) M3OnAccent else M3OnSurface)
        button.setImageDrawable(drawable)
        button.imageTintList = ColorStateList.valueOf(if (active) M3OnAccent else M3OnSurface)
        button.tag = drawableRes
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
            color = if (active) M3AccentContainer else M3ButtonSurface,
            radius = dp(16),
            strokeColor = null
        )

    private fun lockedMuteButtonBackground(): GradientDrawable =
        roundedBackground(M3ControlSurface, dp(16), M3SurfaceOutline)

    private fun togglePlayPause() {
        val player = mpv ?: return
        val nextPaused = !mpvPaused
        if (!nextPaused) {
            requestAudioFocus()
            syncAudioStateAtStartup()
        }
        runMpvOperation(player) { it.setPropertyBoolean("pause", nextPaused) }
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
        updateLockedMuteButton()
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

    private fun updateLockedMuteButton() {
        if (!::lockedMuteButton.isInitialized) {
            return
        }
        setButtonIcon(lockedMuteButton, if (muted) R.drawable.mute_on else R.drawable.mute_off)
        lockedMuteButton.contentDescription = if (muted) "Unmute" else "Mute"
        lockedMuteButton.background = lockedMuteButtonBackground()
    }

    private fun cycleAudioTrack() {
        val player = mpv ?: return
        val generation = streamLoadGeneration
        runMpvOperation(player) {
            val nextId = MpvTrackSelection.nextAudioTrack(it.trackIds("audio"), it.intPropertyOrNull("aid"))
            if (nextId != null) it.setPropertyInt("aid", nextId)
            val feedback = if (nextId == null) "Audio off" else currentTrackFeedback(it, "audio", "aid", "Audio")
            postMpvResult(player, generation) {
                updateTrackButtons()
                showFeedback(feedback)
            }
        }
    }

    private fun cycleSubtitleTrack() {
        val player = mpv ?: return
        val generation = streamLoadGeneration
        runMpvOperation(player) {
            val tracks = it.trackIds("sub")
            val nextId = MpvTrackSelection.nextSubtitleTrack(tracks, it.getPropertyString("sid")?.toIntOrNull())
            if (nextId == null) it.setPropertyString("sid", "no") else it.setPropertyInt("sid", nextId)
            val feedback = if (tracks.isEmpty()) "Subtitles off" else currentTrackFeedback(it, "sub", "sid", "Subtitles")
            postMpvResult(player, generation) {
                subtitleTrackEnabled = nextId != null
                updateTrackButtons()
                showFeedback(feedback)
            }
        }
    }

    private fun updateTrackButtons() {
        if (::audioTrackButton.isInitialized) {
            setButtonIcon(audioTrackButton, R.drawable.audio_track)
            audioTrackButton.background = controlButtonBackground(active = false)
        }
        if (::subtitleTrackButton.isInitialized) {
            setButtonIcon(subtitleTrackButton, R.drawable.subtitle_track)
            subtitleTrackButton.background = controlButtonBackground(active = subtitleTrackEnabled)
        }
    }

    private fun currentTrackFeedback(player: MPVLib, trackType: String, idProperty: String, label: String): String {
        val subtitleSelection = if (idProperty == "sid") {
            runCatching { player.getPropertyString(idProperty) }.getOrNull()?.trim()
        } else null
        val id = if (idProperty == "sid") subtitleSelection?.toIntOrNull() else player.intPropertyOrNull(idProperty)
        if ((idProperty == "sid" && subtitleSelection.equals("no", ignoreCase = true)) ||
            (idProperty != "sid" && id == null)
        ) {
            return "$label off"
        }
        val trackLabel = player.firstNonBlankString(
            "current-tracks/$trackType/title",
            "current-tracks/$trackType/lang",
            "current-tracks/$trackType/codec"
        )
        return listOf(trackLabel, id?.let { "#$it" })
            .filterNot { it.isNullOrBlank() }
            .joinToString(" ")
            .ifBlank { "selected" }
            .let { "$label $it" }
    }

    private fun MPVLib.trackIds(type: String): List<Int> = buildList {
        for (index in 0 until MaxMpvTrackCount) {
            val prefix = "track-list/$index"
            val trackType = runCatching { getPropertyString("$prefix/type") }.getOrNull() ?: break
            if (!trackType.equals(type, ignoreCase = true)) continue
            runCatching { getPropertyInt("$prefix/id") }.getOrNull()?.let(::add)
        }
    }

    private fun MPVLib.intPropertyOrNull(property: String): Int? =
        runCatching { getPropertyInt(property) }.getOrNull()?.takeIf { it > 0 }

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
            val preference = try {
                preferencesRepository.load().embeddedPlayerPreference
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AndroidAppLog.w(LogTag, "Unable to load embedded player preferences; using defaults", error)
                EmbeddedPlayerPreference()
            }
            repeatEnabled = preference.repeatReconnect
            muted = preference.muted
            locked = false
            embeddedRenderProfile = preference.renderProfile
            persistedVolumePercent = preference.volumePercent
            val restoredZoomMode = preference.zoomMode
                ?.let { key -> zoomModes.firstOrNull { it.key.equals(key, ignoreCase = true) } }
            zoomModeIndex = restoredZoomMode?.let(zoomModes::indexOf) ?: FitZoomModeIndex
            persistedZoomMode = restoredZoomMode?.key
            persistedBrightness = preference.brightness?.let { applyBrightness(it, showFeedback = false) }
            preference.volumePercent?.let(::applyVolumePercent)
            updateRepeatButton()
            updateMuteButton()
            updateLockedMuteButton()
            updateLockButton()
            applyZoomMode(zoomModeIndex, showFeedback = false, persist = false)
            applyDesiredAudioState()
            embeddedPreferenceLoaded = true
            initializeMpv()
            showControlsTemporarily()
        }
    }

    private fun initializeMpv() {
        if (activityDestroyed || mpvInitializing || mpv != null) return
        mpvInitializing = true
        val renderProfile = embeddedRenderProfile
        mpvTasks.execute {
            if (activityDestroyed) return@execute
            val createdMpv = runCatching { MPVLib.create(applicationContext) }.getOrNull()
            nativePlayer = createdMpv
            val initialized = createdMpv != null && runCatching {
                createdMpv.addObserver(mpvObserver)
                createdMpv.addLogObserver(mpvLogObserver)
                configureMpvOptions(createdMpv, renderProfile)
                createdMpv.init()
                observeMpvProperties(createdMpv)
            }.onFailure { AndroidAppLog.e(LogTag, "Unable to initialize mpv", it) }.isSuccess
            if (!initialized && createdMpv != null) {
                createdMpv.removeObserver(mpvObserver)
                createdMpv.removeLogObserver(mpvLogObserver)
                runCatching { createdMpv.destroy() }
                nativePlayer = null
            }
            overlayHandler.post {
                mpvInitializing = false
                if (activityDestroyed) return@post
                if (!initialized) {
                    completeStreamLoading()
                    showMessage("mpv could not initialize.")
                    return@post
                }
                mpv = createdMpv
                val surfaceTexture = if (surfaceReady) videoSurface.surfaceTexture else null
                if (surfaceTexture != null) {
                    attachMpvSurface(Surface(surfaceTexture))
                } else {
                    pendingPlayback = true
                }
            }
        }
    }

    private fun saveEmbeddedPlayerPreference() {
        if (!embeddedPreferenceLoaded) {
            return
        }
        val preference = EmbeddedPlayerPreference(
            repeatReconnect = repeatEnabled,
            muted = muted,
            renderProfile = embeddedRenderProfile,
            brightness = persistedBrightness,
            volumePercent = persistedVolumePercent,
            zoomMode = persistedZoomMode
        )
        playbackScope.launch {
            runCatching { preferencesRepository.saveEmbeddedPlayerPreference(preference) }
        }
    }

    private fun toggleLock() {
        hideEpgPanel()
        locked = !locked
        if (surfCarouselOpen) {
            closeSurfCarousel()
        }
        saveEmbeddedPlayerPreference()
        if (locked) {
            overlayHandler.removeCallbacks(hideControlsRunnable)
            controlsOverlay.visibility = View.GONE
            controlsVisible = false
            hidePlaylistOverlay()
            showUnlockButton()
            enterImmersiveMode()
        } else {
            lockedMuteButton.visibility = View.GONE
            showUnlockedConfirmation()
            showControlsTemporarily()
        }
        renderSurfCarousel()
        updateLockButton()
    }

    private fun showUnlockButton() {
        if (!::lockOverlay.isInitialized) return
        setCenterLockState(R.drawable.lock_icon, "Unlock", "Screen locked · Double-tap to unlock")
        showLockControls()
        unlockTapTime = 0L
        unlockHideRunnable?.let(overlayHandler::removeCallbacks)
        unlockHideRunnable = Runnable { hideUnlockButton() }
        overlayHandler.postDelayed(unlockHideRunnable!!, 3000)
    }

    private fun showUnlockedConfirmation() {
        if (!::lockOverlay.isInitialized) return
        setCenterLockState(R.drawable.unlock_icon, "Unlocked", "Screen unlocked")
        showLockControls()
        unlockHideRunnable?.let(overlayHandler::removeCallbacks)
        unlockHideRunnable = Runnable { hideUnlockButton() }
        overlayHandler.postDelayed(unlockHideRunnable!!, 1200)
    }

    private fun setCenterLockState(iconRes: Int, description: String, helpText: String) {
        val drawable = resources.getDrawable(iconRes, theme).mutate()
        drawable.setTint(lockOverlayIconTint)
        unlockButton.setImageDrawable(drawable)
        unlockButton.imageTintList = ColorStateList.valueOf(lockOverlayIconTint)
        unlockButton.tag = iconRes
        unlockButton.contentDescription = description
        lockHelpText.text = helpText
    }

    private fun hideUnlockButton() {
        if (!::lockOverlay.isInitialized) return
        lockOverlay.visibility = View.GONE
        lockedMuteButton.visibility = View.GONE
        lockOverlay.alpha = 1f
        lockedMuteButton.alpha = 1f
        unlockTapTime = 0L
        unlockHideRunnable?.let(overlayHandler::removeCallbacks)
        unlockHideRunnable = null
    }

    private fun showLockControls() {
        lockOverlay.alpha = 1f
        lockedMuteButton.alpha = 1f
        lockOverlay.visibility = View.VISIBLE
        lockedMuteButton.visibility = View.VISIBLE
        lockedMuteButton.bringToFront()
        lockOverlay.bringToFront()
    }

    private fun handleUnlockTap() {
        val now = System.currentTimeMillis()
        val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
        if (now - unlockTapTime < doubleTapTimeout) {
            // Double tap within 500ms - unlock
            toggleLock()
        } else {
            // First tap - show feedback and wait for second tap
            unlockTapTime = now
            lockHelpText.text = "Double-tap to unlock"
            overlayHandler.postDelayed({
                if (System.currentTimeMillis() - unlockTapTime >= doubleTapTimeout) {
                    unlockTapTime = 0L
                }
            }, doubleTapTimeout)
        }
    }

    private fun updateLockButton() {
        if (!::lockButton.isInitialized) return
        setButtonIcon(lockButton, if (locked) R.drawable.lock_icon else R.drawable.unlock_icon)
        lockButton.background = controlButtonBackground(active = locked)
        lockButton.contentDescription = if (locked) "Unlock screen" else "Lock screen"
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
        val now = SystemClock.elapsedRealtime()
        if (lastProgressTickMs != 0L) {
            maxProgressTickDelayMs = maxOf(maxProgressTickDelayMs, (now - lastProgressTickMs - ProgressUpdateMs).coerceAtLeast(0L))
        }
        lastProgressTickMs = now
        samplePlaybackDiagnostics(now)
        completeStreamLoadingIfClockStarted(playbackTimeMs)
        if (controlsOverlay.visibility != View.VISIBLE) return
        updateProgressSeekBarAppearance()
        val length = playbackLengthMs
        val current = playbackTimeMs.coerceAtLeast(0L)
        updateStreamInfo()
        if (!isLiveStream() && length > 0L) {
            progressSeekBar.isEnabled = true
            if (!userSeeking) {
                progressSeekBar.progress = ((current.coerceAtMost(length) * ProgressBarMax) / length).toInt()
            }
            timeLabel.text = "${formatTime(current)} / ${formatTime(length)}"
        } else {
            progressSeekBar.isEnabled = true
            if (!userSeeking) {
                // Live streams have no finite duration, so show the timeline as a
                // fully occupied live bar instead of an empty seek track.
                progressSeekBar.progress = if (isLiveStream()) ProgressBarMax else 0
            }
            timeLabel.text = LiveTimeLabel
        }
        updatePlayPauseButton()
    }

    private fun updatePlayPauseButton() {
        if (!::playPauseButton.isInitialized) {
            return
        }
        val playing = !mpvPaused && playbackStarted
        if (renderedPlayPauseState == playing) return
        renderedPlayPauseState = playing
        setButtonIcon(playPauseButton, if (playing) R.drawable.video_player_pause else R.drawable.video_player_play)
        playPauseButton.contentDescription = if (playing) "Pause" else "Play"
    }

    private fun scheduleReconnectIfNeeded(reason: String): Boolean {
        val retryingStartup = !playbackStarted
        if ((!retryingStartup && !repeatEnabled) || stoppingForLifecycle || isFinishing) {
            return false
        }
        if (reconnectScheduled) {
            return true
        }
        if (reconnectAttempt >= MaxReconnectAttempts) {
            showMessage(com.uiptv.mobile.shared.i18n.MobileMessages.text("autoFailedToReconnectAfter5Attempts"))
            return true
        }
        reconnectAttempt += 1
        val delayMs = if (retryingStartup) {
            StartupRetryDelaysMs[reconnectAttempt - 1]
        } else if (reconnectAttempt == 1) {
            0L
        } else {
            ReconnectDelayMs
        }
        val retryGeneration = streamLoadGeneration
        val retryTarget = currentTarget
        reconnectScheduled = true
        beginStreamLoading()
        if (retryingStartup) {
            showMessage(com.uiptv.mobile.shared.i18n.MobileMessages.text("autoCouldNotPlayVideoUnsupportedFormatOrNetworkError"))
        } else {
            showMessage(
                if (delayMs == 0L) {
                    "$reason. Reconnecting..."
                } else {
                    "$reason. Reconnecting in ${delayMs / 1_000L}s..."
                }
            )
        }
        val task = Runnable {
            reconnectScheduled = false
            reconnectRunnable = null
            if ((!retryingStartup && !repeatEnabled) || stoppingForLifecycle || isFinishing) {
                completeStreamLoading()
                return@Runnable
            }
            if (retryGeneration != streamLoadGeneration) {
                completeStreamLoading()
                return@Runnable
            }
            messageView.visibility = View.GONE
            if (!retryingStartup) {
                startPlayback()
                return@Runnable
            }
            val target = retryTarget ?: currentTarget
            if (target == null) {
                completeStreamLoading()
                showMessage(com.uiptv.mobile.shared.i18n.MobileMessages.text("autoCouldNotPlayVideoUnsupportedFormatOrNetworkError"))
                return@Runnable
            }
            playbackScope.launch {
                val refreshed = runCatching { resolveForPlayback(target, forceStalkerRefresh = true) }
                    .getOrDefault(target)
                if (
                    activityDestroyed || stoppingForLifecycle || isFinishing ||
                    retryGeneration != streamLoadGeneration || currentTarget != target
                ) {
                    return@launch
                }
                currentTarget = refreshed
                startPlayback()
            }
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
        view.isShown && run {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            rawX >= location[0] &&
                rawX <= location[0] + view.width &&
                rawY >= location[1] &&
                rawY <= location[1] + view.height
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
        if (surfCarouselOpen) {
            closeSurfCarousel()
            return
        }
        if (controlsVisible) {
            controlsOverlay.visibility = View.GONE
            hidePlaylistOverlay()
            controlsVisible = false
            overlayHandler.removeCallbacks(hideControlsRunnable)
            enterImmersiveMode()
        } else {
            controlsOverlay.visibility = View.VISIBLE
            controlsVisible = true
            updatePlaybackProgress()
            scheduleControlsHide()
        }
    }

    private fun showControlsTemporarily() {
        if (locked || surfCarouselOpen || !::controlsOverlay.isInitialized) {
            return
        }
        controlsOverlay.visibility = View.VISIBLE
        controlsOverlay.bringToFront()
        controlsVisible = true
        updatePlaybackProgress()
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
        persistedBrightness = applyBrightness(value, showFeedback = true)
    }

    private fun applyBrightness(value: Float, showFeedback: Boolean): Float {
        val normalized = value.coerceIn(MinimumBrightness, 1f)
        val params = window.attributes
        params.screenBrightness = normalized
        window.attributes = params
        if (showFeedback) {
            showFeedback("Brightness ${(normalized * 100).roundToInt()}%")
        }
        return normalized
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
        persistedVolumePercent = percent
        applyDesiredAudioState()
        showFeedback("Volume $percent%")
    }

    private fun applyVolumePercent(percent: Int) {
        val maxVolume = maxMusicVolume()
        val boundedPercent = percent.coerceIn(0, 100)
        val level = (boundedPercent * maxVolume / 100f).roundToInt().coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
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
        val desiredMute = muted
        runMpvOperation(player) {
            it.setPropertyBoolean("mute", desiredMute)
            it.setPropertyDouble("volume", if (desiredMute) 0.0 else MpvAudibleVolume)
        }
    }

    private fun ensureAudibleSystemVolume() {
        if (muted) {
            return
        }
        if (persistedVolumePercent != null) {
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

    private fun applyZoomMode(index: Int, showFeedback: Boolean = true, persist: Boolean = showFeedback) {
        zoomModeIndex = index.coerceIn(zoomModes.indices)
        val mode = zoomModes[zoomModeIndex]
        val usesTextureViewCrop = mode.cropToFill && mpvVideoOutput.equals("mediacodec_embed", ignoreCase = true)
        mpv?.let { player ->
            runMpvOperation(player) {
                it.setPropertyBoolean("keepaspect", mode.keepAspect)
                it.setPropertyString("video-aspect-override", "no")
                it.setPropertyString("video-unscaled", "no")
                it.setPropertyDouble("panscan", if (mode.cropToFill && !usesTextureViewCrop) 1.0 else 0.0)
                it.setPropertyDouble("video-zoom", 0.0)
                it.setPropertyDouble("video-pan-x", 0.0)
                it.setPropertyDouble("video-pan-y", 0.0)
            }
        }
        updateVideoSurfaceTransform()
        setButtonIcon(zoomButton, mode.iconRes)
        zoomButton.contentDescription = "Video scale: ${mode.label}. Tap to change"
        if (showFeedback) {
            showFeedback("Video scale: ${mode.label}")
        }
        if (persist) {
            persistedZoomMode = mode.key
            saveEmbeddedPlayerPreference()
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
        val source = mpvVideoDetails
            ?.takeIf { it.hasResolution() }
            ?: run {
                videoSurface.setTransform(Matrix())
                return
            }
        val sourceWidth = source.displayWidth.toFloat()
        val sourceHeight = source.displayHeight.toFloat()
        val fitScale = minOf(viewWidth / sourceWidth, viewHeight / sourceHeight)
        val scale = if (mode.cropToFill) {
            maxOf(viewWidth / sourceWidth, viewHeight / sourceHeight)
        } else {
            fitScale
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

    private fun refreshEpg() {
        if (!::epgLabel.isInitialized) return
        epgLoadedKey = epgTargetKey()
        epgJob?.cancel()
        epgLabel.visibility = View.GONE
        epgResult = null
        if (::epgGuideButton.isInitialized) epgGuideButton.visibility = View.GONE
        if (::epgPanel.isInitialized) hideEpgPanel()
        val target = currentTarget ?: return
        if (target.mode != BrowseMode.LIVE) {
            return
        }
        if (!epgEnabled) {
            epgLabel.visibility = View.GONE
            return
        }
        epgLabel.text = com.uiptv.mobile.shared.i18n.MobileMessages.text("epgLoading")
        epgLabel.visibility = View.VISIBLE
        epgJob = playbackScope.launch {
            val result = try {
                epgRepository.scheduleProgressively(target.accountId, target.channelId, target.title) { update ->
                    withContext(Dispatchers.Main) {
                        if (target == currentTarget && !stoppingForLifecycle && !activityDestroyed) displayEpg(update)
                    }
                }
            }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { com.uiptv.mobile.shared.epg.MobileEpgResult(com.uiptv.mobile.shared.epg.MobileEpgStatus.UNAVAILABLE) }
            if (target == currentTarget && !stoppingForLifecycle && !activityDestroyed) displayEpg(result)
        }
    }

    private fun epgTargetKey(): String? {
        val target = currentTarget ?: return null
        if (target.mode != BrowseMode.LIVE) return null
        return listOf(target.accountId, target.mode.name, target.categoryProviderId, target.channelId).joinToString("|")
    }

    private fun createEpgPanel(): ScrollView {
        epgPanelContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
            setBackgroundColor(Color.argb(242, 18, 22, 30))
        }
        epgPanelContent.addView(controlButton("Close guide") { hideEpgPanel() }.apply { contentDescription = "Close programme guide" })
        epgDetails = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        epgPanelContent.addView(epgDetails)
        return ScrollView(this).apply {
            contentDescription = "Programme details panel"
            visibility = View.GONE; isFillViewport = true; addView(epgPanelContent)
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { panelTouchX = event.x; panelTouchY = event.y; false }
                    MotionEvent.ACTION_UP -> {
                        val dx = event.x - panelTouchX
                        if (abs(dx) > dp(32) && abs(dx) > abs(event.y - panelTouchY) &&
                            ((epgPanelSide == Gravity.START && dx < 0) || (epgPanelSide == Gravity.END && dx > 0))) {
                            hideEpgPanel(); true
                        } else false
                    }
                    else -> false
                }
            }
        }
    }

    private fun showEpgPanel(side: Int = Gravity.END) {
        if (locked || !isLiveStream()) return
        controlsOverlay.visibility = View.GONE; controlsVisible = false
        hidePlaylistOverlay(); epgPanelSide = side
        (epgPanel.layoutParams as FrameLayout.LayoutParams).apply { gravity = side }
        epgPanel.requestLayout(); renderEpgDetails()
        epgPanel.visibility = View.VISIBLE; epgPanel.bringToFront()
        epgPanel.translationX = epgPanel.layoutParams.width.toFloat() * if (side == Gravity.START) -1 else 1
        epgPanel.animate().translationX(0f).setDuration(180).start()
        if (::epgScrim.isInitialized) {
            epgScrim.visibility = View.VISIBLE
            epgScrim.bringToFront()
            epgPanel.bringToFront()
        }
    }

    private fun hideEpgPanel() {
        if (::epgPanel.isInitialized) {
            epgPanel.animate().cancel()
            epgPanel.visibility = View.GONE
            epgPanel.translationX = 0f
        }
        if (::epgScrim.isInitialized) {
            epgScrim.visibility = View.GONE
        }
    }

    private fun displayEpg(result: com.uiptv.mobile.shared.epg.MobileEpgResult) {
        epgResult = result
        val now = System.currentTimeMillis()
        val current = result.now(now)
        val next = result.next(now)
        val channelTitle = currentTarget?.title.orEmpty()
        val parts = mutableListOf<String>()
        if (current != null) parts.add("Now: ${current.title}")
        if (next != null) parts.add("Next: ${next.title}")
        if (::titleLabel.isInitialized) {
            titleLabel.text = channelTitle
            titleLabel.maxLines = 1
            titleLabel.visibility = if (channelTitle.isBlank()) View.GONE else View.VISIBLE
        }
        val epgInfo = parts.joinToString(" · ")
        epgLabel.text = epgInfo.ifBlank {
            result.message.takeIf { it.isNotBlank() }
                ?: com.uiptv.mobile.shared.i18n.MobileMessages.text("epgNoGuide")
        }
        epgLabel.visibility = View.VISIBLE
        epgGuideButton.visibility = View.GONE
        if (::epgPanel.isInitialized && epgPanel.visibility == View.VISIBLE) renderEpgDetails()
    }

    private fun renderSurfCarousel() {
        if (!::surfCarouselView.isInitialized) return
        val session = currentSurfSession
        if (session == null || session.targets.size < 2 || currentSurfIndex !in session.targets.indices) {
            surfCarouselView.visibility = View.GONE
            return
        }
        surfCurrentIndexState.intValue = currentSurfIndex
        if (surfCarouselContentCreated) return
        val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(this) else darkColorScheme()
        surfCarouselView.setContent {
            MaterialTheme(colorScheme = colors) {
                key(surfCarouselOpenGenerationState.intValue) {
                    PlaybackSurfCarousel(
                        targets = session.targets,
                        currentIndex = surfCurrentIndexState.intValue,
                        enabled = !locked,
                        onSelect = ::surfTo,
                        onClose = ::closeSurfCarousel
                    )
                }
            }
        }
        surfCarouselContentCreated = true
    }

    private fun toggleSurfCarousel() {
        val session = currentSurfSession ?: return
        if (locked || session.targets.size < 2 || currentSurfIndex !in session.targets.indices) return
        if (surfCarouselOpen) {
            closeSurfCarousel()
            return
        }
        hideEpgPanel()
        hidePlaylistOverlay()
        overlayHandler.removeCallbacks(hideControlsRunnable)
        controlsOverlay.visibility = View.GONE
        controlsVisible = false
        surfCarouselOpenGenerationState.intValue += 1
        surfCarouselOpen = true
        surfCarouselView.visibility = View.VISIBLE
        surfCarouselView.bringToFront()
        renderSurfCarousel()
        scheduleSurfCarouselAutoHide()
        enterImmersiveMode()
    }

    private fun closeSurfCarousel() {
        if (!surfCarouselOpen) return
        surfCarouselOpen = false
        overlayHandler.removeCallbacks(hideSurfCarouselRunnable)
        surfCarouselView.visibility = View.GONE
        overlayHandler.removeCallbacks(hideControlsRunnable)
        controlsOverlay.visibility = View.GONE
        controlsVisible = false
        hidePlaylistOverlay()
        enterImmersiveMode()
    }

    private fun scheduleSurfCarouselAutoHide() {
        overlayHandler.removeCallbacks(hideSurfCarouselRunnable)
        overlayHandler.postDelayed(hideSurfCarouselRunnable, SurfCarouselAutoHideMs)
    }

    private fun surfTo(index: Int) {
        val session = currentSurfSession ?: return
        if (locked || index !in session.targets.indices || index == currentSurfIndex) return

        currentSurfIndex = index
        renderSurfCarousel()
        val target = session.targets[index]
        MobilePlaybackIndicator.clear(currentTarget)
        currentTarget = target
        playbackStarted = false
        reconnectAttempt = 0
        reconnectScheduled = false
        cancelReconnect()
        streamLoadGeneration += 1
        epgJob?.cancel()
        epgResult = null
        epgLoadedKey = null
        stopMpvPlayback()
        beginStreamLoading()
        updateStreamInfo()
        if (!surfCarouselOpen) {
            showFeedback("Opening ${target.title.ifBlank { "channel" }}")
        }

        val generation = ++surfSwitchGeneration
        surfSwitchJob?.cancel()
        surfSwitchJob = playbackScope.launch {
            val resolved = try {
                resolveForPlayback(target)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (!activityDestroyed && generation == surfSwitchGeneration) {
                    completeStreamLoading()
                    showMessage("Unable to resolve ${target.title}.")
                }
                return@launch
            }
            if (activityDestroyed || generation != surfSwitchGeneration) return@launch
            if (!resolved.url.isPlayableNetworkUrl()) {
                completeStreamLoading()
                showMessage("Unable to resolve ${target.title}.")
                return@launch
            }
            currentTarget = resolved
            if (!stoppingForLifecycle) {
                AndroidPlaybackWatchStateStore.recordRecent(this@MpvEmbeddedPlayerActivity, target)
                startPlayback()
            }
        }
    }

    private fun renderEpgDetails() {
        epgDetails.removeAllViews()
        fun text(value: String, size: Float = 16f, description: String? = null) {
            epgDetails.addView(TextView(this).apply {
                this.text = value; setTextColor(Color.WHITE); textSize = size
                setPadding(0, dp(8), 0, dp(8)); contentDescription = description
            })
        }
        text(currentTarget?.title.orEmpty(), 18f)
        val now = System.currentTimeMillis()
        val result = epgResult
        val current = result?.now(now)
        val format = java.text.SimpleDateFormat("EEE HH:mm", java.util.Locale.getDefault())
        if (current != null) {
            text("On air", 13f); text(current.title, 22f, "On-air programme title")
            text("${format.format(java.util.Date(current.start))} – ${format.format(java.util.Date(current.stop))}", 14f)
            epgDetails.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000; progress = (((now - current.start).toDouble() / (current.stop - current.start)) * 1000).toInt().coerceIn(0, 1000)
                contentDescription = "Programme progress"
            })
            text(current.description.ifBlank { "No programme description available" }, 16f, "On-air programme description")
        } else text(result?.message?.takeIf { it.isNotBlank() } ?: com.uiptv.mobile.shared.i18n.MobileMessages.text(if (result == null && epgEnabled) "epgLoading" else "epgNoGuide"), 16f)
        result?.next(now)?.let { next ->
            text("Up next", 13f); text(next.title, 18f)
            text("${format.format(java.util.Date(next.start))} – ${format.format(java.util.Date(next.stop))}", 14f)
        }
    }

    private fun updateStreamInfo() {
        if (!::streamInfoLabel.isInitialized) {
            return
        }
        if (::titleLabel.isInitialized) {
            if (::recordingDot.isInitialized) {
                recordingDot.visibility = if (isLiveStream()) View.VISIBLE else View.GONE
            }
            titleLabel.text = currentTarget?.title
                ?.takeIf { it.isNotBlank() }
                ?: intent.getStringExtra(NativePlayerActivity.EXTRA_TITLE).orEmpty().ifBlank { "Embedded player" }
        }
        streamInfoLabel.text = buildStreamInfoLabel()
        if (epgTargetKey() != epgLoadedKey) {
            refreshEpg()
        }
    }

    private fun handleMpvEvent(eventId: Int) {
        when (eventId) {
            MPVLib.MpvEvent.MPV_EVENT_START_FILE -> {
                suppressReconnectForNextEndFile = false
                MobilePlaybackIndicator.clear()
                beginStreamLoading()
                updatePlayPauseButton()
            }
            MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                playbackStarted = true
                MobilePlaybackIndicator.set(currentTarget)
                cancelReconnect()
                reconnectAttempt = 0
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
                if (suppressReconnectForNextEndFile) {
                    suppressReconnectForNextEndFile = false
                } else if (!playNextBingeIfNeeded()) {
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
            "pause" -> mpvPaused = value as? Boolean ?: mpvPaused
            "hwdec-current" -> {
                mpvHwDecoder = (value as? String).orEmpty()
                scheduleStreamInfoRefreshes()
            }
            "current-vo" -> {
                mpvVideoOutput = (value as? String).orEmpty()
                applyZoomMode(zoomModeIndex, showFeedback = false)
                scheduleStreamInfoRefreshes()
            }
            "width",
            "height",
            "dwidth",
            "dheight",
            "video-codec",
            "audio-codec-name" -> {
                scheduleStreamInfoRefreshes()
            }
            "aid" -> updateTrackButtons()
            "sid" -> {
                subtitleTrackEnabled = value?.toString()?.let { it.isNotBlank() && !it.equals("no", true) } == true
                updateTrackButtons()
            }
        }
        if (controlsOverlay.visibility == View.VISIBLE) updatePlaybackProgressLabelOnly()
        updatePlayPauseButton()
    }

    private fun secondsToMillis(value: Any?): Long =
        when (value) {
            is Double -> (value * 1_000.0).toLong().coerceAtLeast(0L)
            is Long -> (value * 1_000L).coerceAtLeast(0L)
            is Int -> (value * 1_000L).coerceAtLeast(0L)
            else -> 0L
        }

    private fun scheduleStreamInfoRefreshes() {
        // Reconfiguration produces several property events together. Collapse
        // them into one refresh instead of multiplying delayed native queries.
        overlayHandler.removeCallbacks(streamInfoRefreshRunnable)
        overlayHandler.postDelayed(streamInfoRefreshRunnable, StreamInfoRefreshShortDelayMs)
    }

    private fun buildStreamInfoLabel(): String {
        val resolution = currentVideoTrackDetails()
            ?.takeIf { it.hasResolution() }
            ?.resolutionLabel()
        val videoCodec = currentVideoTrackDetails()?.codec?.mobileCodecLabel()
        val audioCodec = mpvAudioCodec.mobileCodecLabel()
        val decoderMode = decoderModeLabel()
        return listOf(resolution, videoCodec, audioCodec, decoderMode).filterNot { it.isNullOrBlank() }.distinct().joinToString(" · ")
            .ifBlank { "Codec info pending" }
    }

    private fun String.mobileCodecLabel(): String {
        val codec = trim().lowercase()
        return when {
            codec.contains("h.264") || codec.contains("h264") || codec.contains("avc") -> "H.264/AVC"
            codec.contains("h.265") || codec.contains("h265") || codec.contains("hevc") -> "H.265/HEVC"
            codec.contains("mpeg2") || codec.contains("mpeg-2") -> "MPEG-2"
            codec.contains("mpeg4") || codec.contains("mpeg-4") || codec == "mp4v" -> "MPEG-4"
            codec.contains("av1") || codec.contains("av01") -> "AV1"
            codec.contains("aac") -> "AAC"
            codec.contains("eac3") || codec.contains("e-ac-3") -> "E-AC-3"
            codec.contains("ac3") || codec.contains("ac-3") -> "AC-3"
            codec.contains("opus") -> "Opus"
            codec.contains("flac") -> "FLAC"
            codec.contains("mp3") -> "MP3"
            codec.contains("truehd") -> "TrueHD"
            else -> trim().takeIf { it.isNotBlank() }?.uppercase().orEmpty()
        }
    }

    private fun currentVideoTrackDetails(): VideoTrackDetails? = mpvVideoDetails

    private fun refreshMpvTrackDetails() {
        val player = mpv ?: return
        if (streamInfoRefreshPending) return
        streamInfoRefreshPending = true
        val generation = streamLoadGeneration
        mpvTasks.execute {
            val snapshot = if (!activityDestroyed && mpv === player && generation == streamLoadGeneration) {
                runCatching {
                    val details = VideoTrackDetails(
                        displayWidth = player.firstPositiveInt("dwidth", "width", "video-out-params/w", "video-params/w"),
                        displayHeight = player.firstPositiveInt("dheight", "height", "video-out-params/h", "video-params/h"),
                        codec = player.firstNonBlankString("video-codec", "current-tracks/video/codec", "track-list/0/codec")
                    ).takeIf { it.hasUsefulInfo() }
                    details to player.firstNonBlankString("audio-codec-name", "current-tracks/audio/codec")
                }.getOrNull()
            } else null
            overlayHandler.post {
                streamInfoRefreshPending = false
                if (!activityDestroyed && mpv === player && generation == streamLoadGeneration && snapshot != null) {
                    mpvVideoDetails = snapshot.first
                    mpvAudioCodec = snapshot.second
                    updateVideoSurfaceTransform()
                    updateStreamInfo()
                } else if (!activityDestroyed && !stoppingForLifecycle && mpv === player && generation != streamLoadGeneration) {
                    scheduleStreamInfoRefreshes()
                }
            }
        }
    }

    private fun decoderModeLabel(): String? {
        val decoder = mpvHwDecoder.trim()
        return when {
            decoder.isBlank() -> null
            decoder.equals("no", ignoreCase = true) -> "SW"
            else -> "HW"
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

    private fun hardwareDecodeOptionFor(profile: EmbeddedPlayerRenderProfile): String =
        when (profile) {
            EmbeddedPlayerRenderProfile.DIRECT_MEDIACODEC -> HardwareDecodeModeHardware
            EmbeddedPlayerRenderProfile.GPU -> HardwareDecodeModeAuto
        }

    private fun configureMpvOptions(player: MPVLib, renderProfile: EmbeddedPlayerRenderProfile) {
        val stableMobileAudio = shouldUseStableMobileAudioProfile()
        player.setMpvOption("config", "no")
        player.setMpvOption("terminal", "no")
        player.setMpvOption("profile", "fast")
        // MPV warnings also drive the internal hardware-decoder recovery hook.
        // Release keeps only those warnings; opted-in debug builds get detail.
        player.setMpvOption("msg-level", if (AndroidAppLog.isEnabled) "all=debug" else "all=warn")
        when (renderProfile) {
            EmbeddedPlayerRenderProfile.DIRECT_MEDIACODEC -> {
                // Pair direct Surface output with its matching MediaCodec decoder mode.
                player.setMpvOption("vo", "mediacodec_embed,gpu")
                player.setMpvOption("hwdec", hardwareDecodeOptionFor(renderProfile))
            }
            EmbeddedPlayerRenderProfile.GPU -> {
                player.setMpvOption("vo", "gpu")
                player.setMpvOption("hwdec", hardwareDecodeOptionFor(renderProfile))
            }
        }
        player.setMpvOption("gpu-context", "android")
        player.setMpvOption("opengl-es", "yes")
        player.setMpvOption("ao", if (stableMobileAudio) "opensles,audiotrack" else "audiotrack,opensles")
        player.setMpvOption("audio-set-media-role", "yes")
        player.setMpvOption("audio-buffer", if (stableMobileAudio) StableMobileAudioBufferSeconds else DefaultAudioBufferSeconds)
        if (stableMobileAudio) {
            player.setMpvOption("audio-channels", "stereo")
            player.setMpvOption("audio-format", "s16")
        }
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
        player.setMpvOption("cache-on-disk", "no")
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
        player.observe("audio-codec-name", MPVLib.MpvFormat.MPV_FORMAT_STRING)
        player.observe("hwdec-current", MPVLib.MpvFormat.MPV_FORMAT_STRING)
        player.observe("current-vo", MPVLib.MpvFormat.MPV_FORMAT_STRING)
        player.observe("aid", MPVLib.MpvFormat.MPV_FORMAT_INT64)
        player.observe("sid", MPVLib.MpvFormat.MPV_FORMAT_STRING)
    }

    private fun attachMpvSurface(surface: Surface) {
        val player = mpv
        if (!surface.isValid || player == null || activityDestroyed) {
            surface.release()
            return
        }
        val previous = mpvRenderSurface
        mpvRenderSurface = surface
        attached = false
        mpvTasks.execute {
            val success = runCatching {
                if (previous != null) {
                    player.detachSurface()
                    previous.release()
                }
                player.attachSurface(surface)
                player.command(arrayOf("set", "vid", "auto"))
            }.isSuccess
            overlayHandler.post {
                if (activityDestroyed || mpv !== player || mpvRenderSurface !== surface || !surfaceReady) return@post
                attached = success
                if (success && pendingPlayback && !stoppingForLifecycle) startPlayback()
                if (!success) AndroidAppLog.w(LogTag, "Unable to attach mpv surface")
            }
        }
    }

    private fun stopMpvPlayback() {
        suppressReconnectForNextEndFile = true
        mpv?.let { runMpvCommandAsync(it, "stop") }
        playbackTimeMs = 0L
        playbackLengthMs = 0L
        mpvPaused = false
        updatePlayPauseButton()
    }

    private fun runMpvOperation(player: MPVLib, streamScoped: Boolean = true, action: (MPVLib) -> Unit) {
        val generation = streamLoadGeneration
        mpvTasks.execute {
            if (activityDestroyed || mpv !== player || (streamScoped && generation != streamLoadGeneration)) return@execute
            runCatching { action(player) }
                .onFailure { AndroidAppLog.w(LogTag, "mpv operation failed", it) }
        }
    }

    private fun postMpvResult(player: MPVLib, generation: Long, action: () -> Unit) {
        overlayHandler.post {
            if (!activityDestroyed && mpv === player && generation == streamLoadGeneration) action()
        }
    }

    private fun runMpvCommandAsync(player: MPVLib, vararg args: String) {
        runMpvOperation(player) { it.command(args.toList().toTypedArray()) }
    }

    private fun destroyMpvAsync(surface: Surface?) {
        mpvTasks.execute {
            nativePlayer?.let { player ->
                player.removeObserver(mpvObserver)
                player.removeLogObserver(mpvLogObserver)
                runCatching { player.command(arrayOf("stop")) }
                    .onFailure { AndroidAppLog.w(LogTag, "Unable to stop mpv before destroy", it) }
                runCatching { player.destroy() }
                    .onFailure { AndroidAppLog.w(LogTag, "Unable to destroy mpv", it) }
                nativePlayer = null
            }
            surface?.release()
        }
    }

    private fun samplePlaybackDiagnostics(now: Long) {
        // App diagnostics are emitted only for an explicitly opted-in debug launch.
        // Native reads run on the lifecycle queue, never on the video/UI thread.
        if (!AndroidAppLog.isEnabled || now - lastDiagnosticMs < 5_000L) return
        val player = mpv ?: return
        lastDiagnosticMs = now
        val tickDelay = maxProgressTickDelayMs
        maxProgressTickDelayMs = 0L
        mpvTasks.execute {
            if (activityDestroyed || mpv !== player) return@execute
            val properties = listOf(
                "time-pos", "demuxer-cache-duration", "cache-speed", "paused-for-cache",
                "frame-drop-count", "decoder-frame-drop-count", "avsync", "container-fps",
                "estimated-vf-fps", "hwdec-current", "current-vo"
            )
            val values = properties.joinToString(" ") { name ->
                "$name=${runCatching { player.getPropertyString(name) }.getOrNull() ?: "unknown"}"
            }
            AndroidAppLog.d(DiagnosticLogTag, "elapsedMs=$now uiDelayMs=$tickDelay $values")
        }
    }

    private fun updatePlaybackProgressLabelOnly() {
        if (!::timeLabel.isInitialized || !::progressSeekBar.isInitialized) {
            return
        }
        updateProgressSeekBarAppearance()
        if (!isLiveStream() && playbackLengthMs > 0L) {
            if (!userSeeking) {
                progressSeekBar.progress = ((playbackTimeMs.coerceAtMost(playbackLengthMs) * ProgressBarMax) / playbackLengthMs).toInt()
            }
            timeLabel.text = "${formatTime(playbackTimeMs)} / ${formatTime(playbackLengthMs)}"
        } else {
            if (!userSeeking) {
                progressSeekBar.progress = if (isLiveStream()) ProgressBarMax else 0
            }
            timeLabel.text = LiveTimeLabel
        }
    }

    private fun updateProgressSeekBarAppearance() {
        if (!::progressSeekBar.isInitialized) return
        val live = isLiveStream()
        progressSeekBar.isEnabled = true
        if (progressSeekBarPresentationIsLive != live) {
            progressSeekBarPresentationIsLive = live
            progressSeekBar.thumb = if (live) {
                null
            } else {
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(playerPalette.primary)
                    setSize(dp(10), dp(10))
                }
            }
            progressSeekBar.isClickable = !live
            progressSeekBar.isFocusable = !live
            progressSeekBar.setOnTouchListener { _, _ -> live }
        }
        progressSeekBar.progressTintList = ColorStateList.valueOf(playerPalette.primary)
        progressSeekBar.thumbTintList = ColorStateList.valueOf(playerPalette.primary)
        progressSeekBar.progressBackgroundTintList = ColorStateList.valueOf(
            if (live) playerPalette.primary else withAlpha(playerPalette.primary, 80)
        )
    }

    private fun MPVLib.setMpvOption(name: String, value: String) {
        val result = runCatching { setOptionString(name, value) }.getOrDefault(-1)
        if (result < 0) AndroidAppLog.w(LogTag, "mpv rejected option $name (error $result)")
    }

    private fun MPVLib.observe(property: String, format: Int) {
        runCatching { observeProperty(property, format) }
            .onFailure { AndroidAppLog.w(LogTag, "Unable to observe mpv property $property", it) }
    }

    private fun MPVLib.runCatchingCommand(vararg args: String) {
        runMpvCommandAsync(this, *args)
    }

    private fun showFeedback(message: String, durationMs: Long = GestureFeedbackMs) {
        feedbackView.text = message
        feedbackView.visibility = View.VISIBLE
        feedbackView.bringToFront()
        positionFeedbackAboveControls()
        feedbackView.post { positionFeedbackAboveControls() }
        overlayHandler.removeCallbacks(hideFeedbackRunnable)
        overlayHandler.postDelayed(hideFeedbackRunnable, durationMs)
    }

    private fun positionFeedbackAboveControls() {
        if (!::feedbackView.isInitialized || !::controlsOverlay.isInitialized || !::rootLayout.isInitialized) return
        val params = feedbackView.layoutParams as? FrameLayout.LayoutParams ?: return
        val controlsTop = if (controlsOverlay.visibility == View.VISIBLE) controlsOverlay.top else 0
        val targetBottomMargin = if (rootLayout.height > 0 && controlsTop > 0) {
            rootLayout.height - controlsTop + dp(16)
        } else {
            dp(120)
        }
        if (params.bottomMargin != targetBottomMargin) {
            params.bottomMargin = targetBottomMargin
            feedbackView.layoutParams = params
        }
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
            manifestType = intent.getStringExtra(NativePlayerActivity.EXTRA_MANIFEST_TYPE).orEmpty(),
            seriesId = intent.getStringExtra(NativePlayerActivity.EXTRA_SERIES_ID).orEmpty(),
            seriesTitle = intent.getStringExtra(NativePlayerActivity.EXTRA_SERIES_TITLE).orEmpty(),
            episodeId = intent.getStringExtra(NativePlayerActivity.EXTRA_EPISODE_ID).orEmpty(),
            season = intent.getStringExtra(NativePlayerActivity.EXTRA_SEASON).orEmpty(),
            episodeNumber = intent.getStringExtra(NativePlayerActivity.EXTRA_EPISODE_NUMBER).orEmpty()
        )
    }

    private data class ZoomMode(
        val key: String,
        val label: String,
        val keepAspect: Boolean,
        val cropToFill: Boolean,
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

        fun resolutionLabel(): String = MobileResolutionDisplay.shortText(displayWidth, displayHeight)
    }

    private enum class PlayerGesture {
        None,
        Brightness,
        Volume,
        Guide,
        Zoom
    }

    private companion object {
        private const val FitZoomModeIndex = 0
        private const val CropZoomModeIndex = 1
        private const val ControlsAutoHideMs = 3_500L
        private const val SurfCarouselAutoHideMs = 15_000L
        private const val GestureFeedbackMs = 1_800L
        private const val ProgressUpdateMs = 1_000L
        private const val ReconnectDelayMs = 10_000L
        private const val MaxReconnectAttempts = 5
        private val StartupRetryDelaysMs = longArrayOf(1_000L, 2_000L, 5_000L, 10_000L, 20_000L)
        private const val LogTag = "UIPTV-MpvEmbedded"
        private const val DiagnosticLogTag = "UIPTV-Playback"
        private const val ChromeUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"
        private const val StreamInfoRefreshShortDelayMs = 250L
        private const val MaxMpvTrackCount = 256
        private const val ProgressBarMax = 1_000
        private const val CodecAreaMaximumFraction = 0.35f
        private const val ControlHeaderHeightDp = 48
        private const val ControlHeaderGapDp = 8
        private const val RecordingDotSlotWidthDp = 32
        private const val RecordingDotTitleGapDp = 4
        private const val MpvAudibleVolume = 100.0
        private const val SeekStepSeconds = 15
        private const val BrightnessStep = 0.10f
        private const val VolumeStep = 0.10f
        private const val ControlButtonSizeDp = 44
        private const val ControlIconPaddingDp = 12
        private const val StartupVolumeFallbackRatio = 0.35f
        private const val DefaultBrightness = 0.50f
        private const val MinimumBrightness = 0.05f
        private const val GestureSlopPx = 18f
        private const val EdgeSwipeZoneDp = 24
        private const val EdgeSwipeTravelDp = 16
        private const val EdgeVerticalClaimDp = 20
        private const val TwoFingerZoomSlopDp = 56
        private const val TapSlopPx = 14f
        private const val LiveTimeLabel = "Live"
        private const val DefaultAudioBufferSeconds = "0.35"
        private const val StableMobileAudioBufferSeconds = "0.85"
        // Retry failed direct decoding in software/GPU, not through the same
        // failing MediaCodec driver again in copy mode (which can spin forever).
        private const val HardwareDecodeModeHardware = "mediacodec"
        private const val HardwareDecodeModeAuto = "auto"
        private const val PlayIcon = "\u25B6"
        private const val PauseIcon = "\u23F8"
        private const val StopIcon = "\u25A0"
        private const val RewindIcon = "\u23EA"
        private const val ForwardIcon = "\u23E9"
        private const val PlaylistIcon = "List"
        private const val CloseIcon = "\u2715"
    }
}

private fun String.isPlayableNetworkUrl(): Boolean {
    val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase()
    return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
}

