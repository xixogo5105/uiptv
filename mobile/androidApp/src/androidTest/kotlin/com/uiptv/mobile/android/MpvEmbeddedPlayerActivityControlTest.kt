package com.uiptv.mobile.android

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MpvEmbeddedPlayerActivityControlTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var activity: Activity? = null

    @Test
    fun audioTrackSelectionNeverTurnsAudioOffAndWrapsAvailableTracks() {
        org.junit.Assert.assertEquals(4, MpvTrackSelection.nextAudioTrack(listOf(4, 9), null))
        org.junit.Assert.assertEquals(9, MpvTrackSelection.nextAudioTrack(listOf(4, 9), 4))
        org.junit.Assert.assertEquals(4, MpvTrackSelection.nextAudioTrack(listOf(4, 9), 9))
        org.junit.Assert.assertEquals(4, MpvTrackSelection.nextAudioTrack(listOf(4, 9), 99))
        org.junit.Assert.assertNull(MpvTrackSelection.nextAudioTrack(emptyList(), null))
    }

    @Test
    fun subtitleTrackSelectionCyclesEveryTrackThenOffAndBackOn() {
        val tracks = listOf(2, 7, 12)
        org.junit.Assert.assertEquals(2, MpvTrackSelection.nextSubtitleTrack(tracks, null))
        org.junit.Assert.assertEquals(7, MpvTrackSelection.nextSubtitleTrack(tracks, 2))
        org.junit.Assert.assertEquals(12, MpvTrackSelection.nextSubtitleTrack(tracks, 7))
        org.junit.Assert.assertNull(MpvTrackSelection.nextSubtitleTrack(tracks, 12))
        org.junit.Assert.assertEquals(2, MpvTrackSelection.nextSubtitleTrack(tracks, null))
        org.junit.Assert.assertNull(MpvTrackSelection.nextSubtitleTrack(emptyList(), null))
    }

    @After
    fun tearDown() {
        activity?.finish()
        activity = null
    }

    @Test
    fun vodControlButtonsRespondToClicks() {
        val player = launchPlayer("VOD")

        clickAnyControl(player, "Pause", "Play", "Play/pause")
        clickAnyControl(player, "Pause", "Play", "Play/pause")
        clickControl(player, "Rewind 15 seconds")
        clickControl(player, "Fast forward 15 seconds")
        clickControl(player, "Reload stream")
        clickAnyControl(player, "Mute", "Unmute")
        clickAnyControl(player, "Mute", "Unmute")
        clickControl(player, "Audio track")
        clickControl(player, "Subtitles")
        clickAnyControl(player, "Repeat off", "Repeat on")
        clickAnyControl(player, "Repeat off", "Repeat on")
        clickControl(player, "Video scale: Fit. Tap to change")
        waitForControl(player, "Video scale: Crop. Tap to change")
        clickControl(player, "Video scale: Crop. Tap to change")
        waitForControl(player, "Video scale: Stretch. Tap to change")
        clickControl(player, "Video scale: Stretch. Tap to change")
        waitForControl(player, "Video scale: Fit. Tap to change")
        clickControl(player, "Close player")
    }

    @Test
    fun liveControlButtonsDoNotExposeSeeking() {
        val player = launchPlayer("LIVE")

        waitForAnyControl(player, listOf("Pause", "Play", "Play/pause"))
        val broadcastIndicator = waitForControl(player, "Live broadcast") as android.widget.FrameLayout
        val density = player.resources.displayMetrics.density
        org.junit.Assert.assertEquals((32 * density).toInt(), broadcastIndicator.layoutParams.width)
        org.junit.Assert.assertEquals((48 * density).toInt(), broadcastIndicator.layoutParams.height)
        val broadcastIcon = broadcastIndicator.getChildAt(0) as android.widget.FrameLayout
        org.junit.Assert.assertEquals((28 * density).toInt(), broadcastIcon.layoutParams.width)
        org.junit.Assert.assertEquals((28 * density).toInt(), broadcastIcon.layoutParams.height)
        assertNull(findViewByDescription(player.window.decorView, "Rewind 15 seconds"))
        assertNull(findViewByDescription(player.window.decorView, "Fast forward 15 seconds"))
        clickControl(player, "Audio track")
        clickControl(player, "Subtitles")
        clickControl(player, "Close player")
    }

    @Test
    fun onAirProgrammeDrawerShowsDetailsAndOpensBySwipe() {
        val player = launchPlayer("LIVE")
        waitForAnyControl(player, listOf("Pause", "Play", "Play/pause"))
        val now = System.currentTimeMillis()
        val programme = com.uiptv.mobile.shared.epg.MobileEpgProgramme("Godzilla", "On-air movie description", now - 60_000, now + 3_600_000)
        instrumentation.runOnMainSync {
            val job = player.javaClass.getDeclaredField("epgJob").apply { isAccessible = true }.get(player) as? kotlinx.coroutines.Job
            job?.cancel()
            player.javaClass.getDeclaredMethod("displayEpg", com.uiptv.mobile.shared.epg.MobileEpgResult::class.java).apply { isAccessible = true }
                .invoke(player, com.uiptv.mobile.shared.epg.MobileEpgResult(com.uiptv.mobile.shared.epg.MobileEpgStatus.AVAILABLE, listOf(programme)))
        }
        clickControl(player, "Show programme guide")
        val panel = waitForControl(player, "Programme details panel")
        org.junit.Assert.assertEquals(View.VISIBLE, panel.visibility)
        org.junit.Assert.assertEquals("Godzilla", (waitForControl(player, "On-air programme title") as android.widget.TextView).text.toString())
        org.junit.Assert.assertEquals("On-air movie description", (waitForControl(player, "On-air programme description") as android.widget.TextView).text.toString())
        clickControl(player, "Close programme guide")
        org.junit.Assert.assertEquals(View.GONE, panel.visibility)
        val root = player.javaClass.getDeclaredField("rootLayout").apply { isAccessible = true }.get(player) as View
        swipe(player, root.width - 1f, root.height * 0.2f, root.width * 0.2f, root.height * 0.2f, jitterX = -10f, jitterY = 40f)
        org.junit.Assert.assertEquals(View.VISIBLE, panel.visibility)
        org.junit.Assert.assertEquals(android.view.Gravity.END, (panel.layoutParams as android.widget.FrameLayout.LayoutParams).gravity)
        Thread.sleep(1_500)
        org.junit.Assert.assertEquals(View.VISIBLE, panel.visibility)
        swipe(player, root.width * 0.5f, root.height * 0.5f, root.width - 1f, root.height * 0.5f)
        org.junit.Assert.assertEquals(View.GONE, panel.visibility)
        swipe(player, 1f, root.height * 0.2f, root.width * 0.3f, root.height * 0.2f)
        org.junit.Assert.assertEquals(View.VISIBLE, panel.visibility)
        org.junit.Assert.assertEquals(android.view.Gravity.START, (panel.layoutParams as android.widget.FrameLayout.LayoutParams).gravity)
        tap(player, root.width * 0.95f, root.height * 0.5f)
        org.junit.Assert.assertEquals(View.GONE, panel.visibility)
    }

    @Test
    fun onAirProgrammeDrawerOpensWhileEmptyAndUpdatesWhenDataArrives() {
        val player = launchPlayer("LIVE")
        waitForAnyControl(player, listOf("Pause", "Play", "Play/pause"))
        // The swipe drawer remains available while the guide is empty or loading.
        instrumentation.runOnMainSync {
            val job = player.javaClass.getDeclaredField("epgJob").apply { isAccessible = true }.get(player) as? kotlinx.coroutines.Job
            job?.cancel()
            player.javaClass.getDeclaredField("epgResult").apply { isAccessible = true }.set(player, null)
            // Ensure panel is hidden since epgResult is cleared
            player.javaClass.getDeclaredMethod("hideEpgPanel").apply { isAccessible = true }.invoke(player)
        }
        val root = player.javaClass.getDeclaredField("rootLayout").apply { isAccessible = true }.get(player) as View
        // 1) Right-edge swipe while loading: drawer opens with an empty/loading state
        swipe(player, root.width - 1f, root.height * 0.2f, root.width * 0.2f, root.height * 0.2f)
        val panel = player.javaClass.getDeclaredField("epgPanel").apply { isAccessible = true }.get(player) as View
        org.junit.Assert.assertEquals(View.VISIBLE, panel.visibility)
        val guideButton = player.javaClass.getDeclaredField("epgGuideButton").apply { isAccessible = true }.get(player) as View
        org.junit.Assert.assertEquals(View.GONE, guideButton.visibility)
        // Load EPG data
        val now = System.currentTimeMillis()
        val programme = com.uiptv.mobile.shared.epg.MobileEpgProgramme("Godzilla", "On-air movie description", now - 60_000, now + 3_600_000)
        instrumentation.runOnMainSync {
            player.javaClass.getDeclaredMethod("displayEpg", com.uiptv.mobile.shared.epg.MobileEpgResult::class.java).apply { isAccessible = true }
                .invoke(player, com.uiptv.mobile.shared.epg.MobileEpgResult(com.uiptv.mobile.shared.epg.MobileEpgStatus.AVAILABLE, listOf(programme)))
        }
        org.junit.Assert.assertEquals(View.GONE, guideButton.visibility)
        // Data populates the already-open panel without closing it.
        org.junit.Assert.assertEquals(View.VISIBLE, panel.visibility)
        org.junit.Assert.assertEquals(android.view.Gravity.END, (panel.layoutParams as android.widget.FrameLayout.LayoutParams).gravity)
        org.junit.Assert.assertEquals("Godzilla", (waitForControl(player, "On-air programme title") as android.widget.TextView).text.toString())
        clickControl(player, "Close programme guide")
        org.junit.Assert.assertEquals(View.GONE, panel.visibility)
        // 3) Left-edge swipe: drawer opens on the left
        swipe(player, 1f, root.height * 0.2f, root.width * 0.3f, root.height * 0.2f)
        org.junit.Assert.assertEquals(View.VISIBLE, panel.visibility)
        org.junit.Assert.assertEquals(android.view.Gravity.START, (panel.layoutParams as android.widget.FrameLayout.LayoutParams).gravity)
        // 4) Tap outside closes drawer
        tap(player, root.width * 0.95f, root.height * 0.5f)
        org.junit.Assert.assertEquals(View.GONE, panel.visibility)
    }

    @Test
    fun lockButtonTogglesLockState() {
        val player = launchPlayer("VOD")

        val controlBarLock = waitForControl(player, "Lock screen") as android.widget.ImageButton
        org.junit.Assert.assertEquals(R.drawable.unlock_icon, controlBarLock.tag)
        val palette = player.javaClass.getDeclaredField("playerPalette").apply { isAccessible = true }.get(player)
        val onSurfaceColor = palette.javaClass.getDeclaredField("onSurface").apply { isAccessible = true }.getInt(palette)
        val controlSurfaceColor = palette.javaClass.getDeclaredField("controlSurface").apply { isAccessible = true }.getInt(palette)
        org.junit.Assert.assertEquals(onSurfaceColor, controlBarLock.imageTintList?.defaultColor)
        clickControl(player, "Lock screen")

        val centreLock = waitForControl(player, "Unlock") as android.widget.ImageButton
        val cornerMute = player.javaClass.getDeclaredField("lockedMuteButton").apply { isAccessible = true }
            .get(player) as android.widget.ImageButton
        val lockOverlay = player.javaClass.getDeclaredField("lockOverlay").apply { isAccessible = true }
            .get(player) as View
        val help = waitForControl(player, "Lock help") as android.widget.TextView
        org.junit.Assert.assertEquals(R.drawable.lock_icon, centreLock.tag)
        org.junit.Assert.assertEquals(View.VISIBLE, cornerMute.visibility)
        org.junit.Assert.assertEquals(onSurfaceColor, centreLock.imageTintList?.defaultColor)
        org.junit.Assert.assertEquals(controlSurfaceColor,
            (cornerMute.background as android.graphics.drawable.GradientDrawable).color?.defaultColor)
        clickControl(player, cornerMute.contentDescription.toString())
        org.junit.Assert.assertEquals(controlSurfaceColor,
            (cornerMute.background as android.graphics.drawable.GradientDrawable).color?.defaultColor)
        org.junit.Assert.assertTrue(help.text.toString().contains("Double-tap to unlock"))
        org.junit.Assert.assertTrue("Help text must render below the measured icon", help.top >= centreLock.bottom)
        Thread.sleep(3_400)
        waitForVisibility(cornerMute, View.GONE)
        waitForVisibility(lockOverlay, View.GONE)
        org.junit.Assert.assertEquals("Corner mute control should hide with the lock overlay", View.GONE, cornerMute.visibility)
        org.junit.Assert.assertEquals("Center lock overlay should hide with the corner mute control", View.GONE, lockOverlay.visibility)
        val root = player.window.decorView
        tap(player, root.width * 0.8f, root.height * 0.15f)
        val shownLock = waitForControl(player, "Unlock") as android.widget.ImageButton
        val shownMute = cornerMute
        org.junit.Assert.assertEquals(View.VISIBLE, shownLock.visibility)
        org.junit.Assert.assertEquals(View.VISIBLE, shownMute.visibility)
        tapTwice(player, shownLock)

        val unlocked = waitForControl(player, "Unlocked") as android.widget.ImageButton
        org.junit.Assert.assertEquals(R.drawable.unlock_icon, unlocked.tag)
        org.junit.Assert.assertEquals("Screen unlocked", (waitForControl(player, "Lock help") as android.widget.TextView).text.toString())
        waitForAnyControl(player, listOf("Lock screen"))
    }

    private fun launchPlayer(mode: String): Activity {
        val context = instrumentation.targetContext
        val intent = Intent(context, MpvEmbeddedPlayerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(NativePlayerActivity.EXTRA_MODE, mode)
            .putExtra(NativePlayerActivity.EXTRA_ACCOUNT_ID, 1L)
            .putExtra(NativePlayerActivity.EXTRA_ACCOUNT_NAME, "Control Test")
            .putExtra(NativePlayerActivity.EXTRA_CATEGORY_PROVIDER_ID, "control-test")
            .putExtra(NativePlayerActivity.EXTRA_CATEGORY_ROW_ID, 1L)
            .putExtra(NativePlayerActivity.EXTRA_CHANNEL_ID, "control-test")
            .putExtra(NativePlayerActivity.EXTRA_TITLE, "Control Test $mode")
            .putExtra(NativePlayerActivity.EXTRA_URL, TestStreamUrl)

        return instrumentation.startActivitySync(intent).also {
            activity = it
        }
    }

    private fun clickControl(activity: Activity, description: String) {
        val view = waitForControl(activity, description)
        instrumentation.runOnMainSync { view.performClick() }
        instrumentation.waitForIdleSync()
    }

    private fun clickAnyControl(activity: Activity, vararg descriptions: String) {
        val view = waitForAnyControl(activity, descriptions.toList())
        instrumentation.runOnMainSync { view.performClick() }
        instrumentation.waitForIdleSync()
    }

    private fun waitForControl(activity: Activity, description: String): View {
        return waitForAnyControl(activity, listOf(description))
    }

    private fun waitForText(activity: Activity, text: String): View {
        val deadline = System.currentTimeMillis() + ControlWaitMs
        do {
            findViewByText(activity.window.decorView, text)?.let { return it }
            Thread.sleep(ControlPollIntervalMs)
        } while (System.currentTimeMillis() < deadline)
        val found = collectTexts(activity.window.decorView).joinToString()
        assertNotNull("Text '$text' not found. Visible texts: $found", null)
        error("unreachable")
    }

    private fun waitForVisibility(view: View, visibility: Int) {
        val deadline = System.currentTimeMillis() + ControlWaitMs
        do {
            instrumentation.waitForIdleSync()
            if (view.visibility == visibility) return
            Thread.sleep(ControlPollIntervalMs)
        } while (System.currentTimeMillis() < deadline)
    }

    private fun findViewByText(view: View, text: String): View? {
        if (view is android.widget.TextView && view.text?.toString() == text) {
            return view
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findViewByText(view.getChildAt(index), text)?.let { return it }
            }
        }
        return null
    }

    private fun collectTexts(view: View): List<String> {
        val texts = mutableListOf<String>()
        if (view is android.widget.TextView) {
            view.text?.toString()?.takeIf { it.isNotBlank() }?.let(texts::add)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                texts += collectTexts(view.getChildAt(index))
            }
        }
        return texts
    }

    private fun swipe(
        activity: Activity,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        jitterX: Float = 0f,
        jitterY: Float = 0f
    ) {
        instrumentation.runOnMainSync {
            val time = android.os.SystemClock.uptimeMillis()
            val events = mutableListOf<android.view.MotionEvent>()
            events += android.view.MotionEvent.obtain(time, time, android.view.MotionEvent.ACTION_DOWN, startX, startY, 0)
            if (jitterX != 0f || jitterY != 0f) {
                events += android.view.MotionEvent.obtain(time, time + 20, android.view.MotionEvent.ACTION_MOVE, startX + jitterX, startY + jitterY, 0)
            }
            events += android.view.MotionEvent.obtain(time, time + 70, android.view.MotionEvent.ACTION_MOVE, endX, endY, 0)
            events += android.view.MotionEvent.obtain(time, time + 100, android.view.MotionEvent.ACTION_UP, endX, endY, 0)
            events.forEach(activity::dispatchTouchEvent)
            events.forEach(android.view.MotionEvent::recycle)
        }
        instrumentation.waitForIdleSync()
    }

    private fun tap(activity: Activity, x: Float, y: Float) {
        swipe(activity, x, y, x, y)
    }

    private fun tapTwice(activity: Activity, view: View) {
        val position = IntArray(2)
        val decorPosition = IntArray(2)
        instrumentation.runOnMainSync {
            view.getLocationOnScreen(position)
            activity.window.decorView.getLocationOnScreen(decorPosition)
        }
        val x = position[0] - decorPosition[0] + view.width / 2f
        val y = position[1] - decorPosition[1] + view.height / 2f
        tap(activity, x, y)
        Thread.sleep(80)
        tap(activity, x, y)
    }

    private fun waitForAnyControl(activity: Activity, descriptions: List<String>): View {
        val deadline = System.currentTimeMillis() + ControlWaitMs
        do {
            descriptions.forEach { description ->
                findViewByDescription(activity.window.decorView, description)?.let { return it }
            }
            Thread.sleep(ControlPollIntervalMs)
        } while (System.currentTimeMillis() < deadline)
        val found = collectDescriptions(activity.window.decorView).joinToString()
        assertNotNull("Controls '${descriptions.joinToString()}' not found. Visible controls: $found", null)
        error("unreachable")
    }

    private fun findViewByDescription(view: View, description: String): View? {
        if (view.contentDescription?.toString() == description) {
            return view
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findViewByDescription(view.getChildAt(index), description)?.let { return it }
            }
        }
        return null
    }

    private fun collectDescriptions(view: View): List<String> {
        val descriptions = mutableListOf<String>()
        view.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(descriptions::add)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                descriptions += collectDescriptions(view.getChildAt(index))
            }
        }
        return descriptions
    }

    private companion object {
        const val TestStreamUrl = "http://10.0.2.2:8765/test.mp4"
        const val ControlWaitMs = 2_000L
        const val ControlPollIntervalMs = 25L
    }
}
