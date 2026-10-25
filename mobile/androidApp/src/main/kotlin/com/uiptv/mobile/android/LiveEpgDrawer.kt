package com.uiptv.mobile.android

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.uiptv.mobile.shared.browse.BrowseMode
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import com.uiptv.mobile.shared.epg.*
import com.uiptv.mobile.shared.i18n.MobileMessages
import com.uiptv.mobile.shared.playback.PlaybackTarget
import com.uiptv.mobile.shared.settings.AndroidDataStorePreferencesRepository
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/** A current-view guide for Android Media. Closing the player releases its result. */
internal class LiveEpgDrawer(
    private val activity: Activity,
    private val root: FrameLayout,
    private val scope: CoroutineScope
) {
    private val database = AndroidUiptvDatabaseHelper(activity)
    private val preferences = AndroidDataStorePreferencesRepository(activity)
    private val repository = AndroidEpgRepository(activity, database, preferences)
    private val body = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        setBackgroundColor(Color.argb(245, 28, 27, 31))
    }
    private val panel = ScrollView(activity).apply { isFillViewport = true; addView(body); visibility = View.GONE }
    private val scrim = View(activity).apply {
        setBackgroundColor(Color.argb(100, 0, 0, 0)); visibility = View.GONE
        setOnClickListener { hide() }
    }
    private var target: PlaybackTarget? = null
    private var result: MobileEpgResult? = null
    private var request = 0L
    private var job: Job? = null
    private var downX = 0f
    private var downY = 0f
    private var edge = 0
    var consumingGesture = false
        private set

    init {
        root.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(panel, FrameLayout.LayoutParams(dp(360), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))
    }

    fun load(channel: PlaybackTarget) {
        if (target == channel) return
        clear()
        if (channel.mode != BrowseMode.LIVE) return
        target = channel
        val generation = request
        job = scope.launch {
            val loaded = try { repository.schedule(channel.accountId, channel.channelId, channel.title) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { MobileEpgResult(MobileEpgStatus.UNAVAILABLE) }
            if (generation != request) return@launch
            result = loaded
            if (panel.visibility == View.VISIBLE) render()
        }
    }

    fun handleTouch(event: MotionEvent): Boolean {
        if (target?.mode != BrowseMode.LIVE) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                consumingGesture = false
                downX = event.x; downY = event.y
                edge = if (panel.visibility == View.VISIBLE) 0 else when {
                    event.x <= dp(32) -> Gravity.START
                    event.x >= root.width - dp(32) -> Gravity.END
                    else -> 0
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (consumingGesture) return true
                val dx = event.x - downX
                val inward = edge == Gravity.START && dx > dp(64) || edge == Gravity.END && dx < -dp(64)
                if (inward && abs(dx) > abs(event.y - downY) * 1.5f) {
                    show(edge); consumingGesture = true; return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val consumed = consumingGesture
                consumingGesture = false; edge = 0
                return consumed
            }
        }
        return false
    }

    private fun show(side: Int) {
        (panel.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = side
            width = minOf(dp(420), (root.width * .85f).toInt().coerceAtLeast(dp(180)))
        }
        render()
        scrim.visibility = View.VISIBLE; scrim.bringToFront()
        panel.visibility = View.VISIBLE; panel.bringToFront(); panel.requestLayout()
    }

    private fun render() {
        body.removeAllViews()
        body.addView(Button(activity).apply {
            text = MobileMessages.text("commonClose")
            setOnClickListener { hide() }
        })
        fun text(value: String, size: Float = 16f) {
            if (value.isBlank()) return
            body.addView(TextView(activity).apply {
                text = value; textSize = size; setTextColor(Color.WHITE); setPadding(0, dp(8), 0, dp(8))
            })
        }
        text(target?.title.orEmpty(), 20f)
        text(MobileMessages.text("epgMobileProgrammeGuide"), 14f)
        val guide = result
        val now = System.currentTimeMillis()
        val programmes = listOfNotNull(guide?.now(now), guide?.next(now)).distinct()
        if (programmes.isEmpty()) text(MobileMessages.text(if (guide == null) "epgLoading" else "epgNoGuide"))
        val format = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
        programmes.forEach { programme ->
            text(MobileMessages.text(if (programme.start <= now) "epgNow" else "epgNext"), 14f)
            text(programme.title, 20f)
            text("${format.format(Date(programme.start))} – ${format.format(Date(programme.stop))}", 14f)
            text(programme.description)
        }
    }

    private fun hide() { panel.visibility = View.GONE; scrim.visibility = View.GONE }
    fun clear() {
        request++; job?.cancel(); job = null; result = null; target = null
        body.removeAllViews(); hide(); consumingGesture = false; edge = 0
    }
    fun release() { clear(); database.close() }
    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
