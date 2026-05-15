package com.uiptv.mobile.android

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import android.widget.SeekBar
import android.widget.TextView
import com.uiptv.mobile.shared.browse.BrowseMode
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import com.uiptv.mobile.shared.playback.PlaybackTarget
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
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
    private lateinit var feedbackView: TextView
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var messageView: TextView
    private lateinit var playPauseButton: TextView
    private lateinit var progressSeekBar: SeekBar
    private lateinit var timeLabel: TextView
    private lateinit var zoomButton: TextView
    private val overlayHandler = Handler(Looper.getMainLooper())
    private val zoomModes = listOf(
        ZoomMode("Fit", MediaPlayer.ScaleType.SURFACE_BEST_FIT),
        ZoomMode("Fill", MediaPlayer.ScaleType.SURFACE_FILL),
        ZoomMode("16:9", MediaPlayer.ScaleType.SURFACE_16_9),
        ZoomMode("4:3", MediaPlayer.ScaleType.SURFACE_4_3)
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
    private var attached = false
    private var controlsVisible = false
    private var userSeeking = false
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var initialBrightness = DefaultBrightness
    private var initialVolume = 0
    private var activeGesture = PlayerGesture.None
    private var zoomModeIndex = 0

    @Suppress("DEPRECATION")
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (intent.getStringExtra(NativePlayerActivity.EXTRA_URL).orEmpty().isBlank()) {
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

        showFeedback(
            "Swipe left side for brightness, right side for volume",
            FeedbackDurationMs
        )

        val options = arrayListOf(
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
            setEventListener { event ->
                when (event.type) {
                    MediaPlayer.Event.Opening -> {
                        runOnUiThread { showLoading() }
                    }
                    MediaPlayer.Event.Buffering -> {
                        val buffering = event.buffering
                        runOnUiThread {
                            if (buffering < 100f) {
                                showLoading()
                            } else if (isPlaying) {
                                hideLoading()
                            }
                        }
                    }
                    MediaPlayer.Event.Playing -> {
                        playbackStarted = true
                        runOnUiThread {
                            hideLoading()
                            messageView.visibility = View.GONE
                            updatePlayPauseButton()
                        }
                    }
                    MediaPlayer.Event.Paused,
                    MediaPlayer.Event.Stopped,
                    MediaPlayer.Event.EndReached -> {
                        runOnUiThread {
                            hideLoading()
                            updatePlayPauseButton()
                        }
                    }
                    MediaPlayer.Event.EncounteredError -> {
                        runOnUiThread {
                            hideLoading()
                            showMessage("Embedded player could not open this stream.")
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
        startPlayback()
        startProgressUpdates()
    }

    override fun onStop() {
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
            targetFromIntent()?.let {
                val databaseHelper = AndroidUiptvDatabaseHelper(this)
                try {
                    AndroidPlaybackWatchStateStore(databaseHelper).markOpened(it)
                } finally {
                    databaseHelper.close()
                }
            }
        }
        mediaPlayer?.release()
        mediaPlayer = null
        libVlc?.release()
        libVlc = null
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
        val streamUrl = intent.getStringExtra(NativePlayerActivity.EXTRA_URL).orEmpty()
        if (streamUrl.isBlank()) {
            finish()
            return
        }
        if (!attached) {
            createdPlayer.attachViews(videoLayout, null, false, false)
            attached = true
        }
        showLoading()
        val media = Media(createdLibVlc, Uri.parse(streamUrl)).apply {
            setHWDecoderEnabled(true, false)
            addOption(":http-reconnect")
            addOption(":network-caching=1500")
            addOption(":live-caching=1500")
            addOption(":file-caching=1500")
            addOption(":rtsp-tcp")
        }
        createdPlayer.setVideoScale(zoomModes[zoomModeIndex].scaleType)
        createdPlayer.media = media
        media.release()
        createdPlayer.play()
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

            MotionEvent.ACTION_MOVE -> {
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
                    enterImmersiveMode()
                }
                activeGesture = PlayerGesture.None
                return true
            }
        }
        return true
    }

    private fun createControlsOverlay(): LinearLayout {
        val title = TextView(this).apply {
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
            addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(closeButton)
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
            addView(controlButton("${BrightnessIcon}-") { adjustBrightnessBy(-BrightnessStep) }, compactControlLayoutParams())
            addView(controlButton("${BrightnessIcon}+") { adjustBrightnessBy(BrightnessStep) }, compactControlLayoutParams())
            addView(controlButton("Vol -") { adjustVolumeBy(-VolumeStep) }, compactControlLayoutParams())
            addView(controlButton("Vol +") { adjustVolumeBy(VolumeStep) }, compactControlLayoutParams())
            zoomButton = controlButton(zoomModes[zoomModeIndex].label) { cycleZoomMode() }
            addView(zoomButton, compactControlLayoutParams())
        }
        val controlsScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(buttonRow)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = roundedBackground(Color.argb(225, 12, 16, 20), dp(0))
            visibility = View.GONE
            addView(titleRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(progressRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            })
            addView(controlsScroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            })
        }
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

    private fun compactControlLayoutParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(4)
            rightMargin = dp(4)
        }

    private fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        updatePlayPauseButton()
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
        mediaPlayer?.setVolume(percent.coerceIn(0, 100))
        showFeedback("Volume $percent%")
    }

    private fun maxMusicVolume(): Int =
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    private fun cycleZoomMode() {
        zoomModeIndex = (zoomModeIndex + 1) % zoomModes.size
        val mode = zoomModes[zoomModeIndex]
        mediaPlayer?.setVideoScale(mode.scaleType)
        zoomButton.text = "Zoom: ${mode.label}"
        showFeedback("Zoom ${mode.label}")
    }

    private fun showFeedback(message: String, durationMs: Long = GestureFeedbackMs) {
        feedbackView.text = message
        feedbackView.visibility = View.VISIBLE
        overlayHandler.removeCallbacks(hideFeedbackRunnable)
        overlayHandler.postDelayed(hideFeedbackRunnable, durationMs)
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
            logo = intent.getStringExtra(NativePlayerActivity.EXTRA_LOGO).orEmpty()
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
        val scaleType: MediaPlayer.ScaleType
    )

    private enum class PlayerGesture {
        None,
        Brightness,
        Volume
    }

    private companion object {
        private const val ControlsAutoHideMs = 3_500L
        private const val GestureFeedbackMs = 900L
        private const val FeedbackDurationMs = 2_200L
        private const val ProgressUpdateMs = 1_000L
        private const val ProgressBarMax = 1_000
        private const val SeekStepSeconds = 15
        private const val BrightnessStep = 0.10f
        private const val VolumeStep = 0.10f
        private const val DefaultBrightness = 0.50f
        private const val MinimumBrightness = 0.05f
        private const val GestureSlopPx = 18f
        private const val TapSlopPx = 14f
        private const val LiveTimeLabel = "Live"
        private const val PlayIcon = "\u25B6"
        private const val PauseIcon = "\u23F8"
        private const val StopIcon = "\u25A0"
        private const val RewindIcon = "\u23EA"
        private const val ForwardIcon = "\u23E9"
        private const val BrightnessIcon = "\u2600"
        private const val CloseIcon = "\u2715"
    }
}
