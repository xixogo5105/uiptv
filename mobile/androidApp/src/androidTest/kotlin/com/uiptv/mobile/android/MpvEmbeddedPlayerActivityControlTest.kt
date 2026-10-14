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
        clickControl(player, "Zoom Default")
        waitForControl(player, "Zoom Fill")
        clickControl(player, "Close player")
    }

    @Test
    fun liveControlButtonsDoNotExposeSeeking() {
        val player = launchPlayer("LIVE")

        waitForAnyControl(player, listOf("Pause", "Play", "Play/pause"))
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
    fun onAirProgrammeDrawerOnlyOpensWhenGuideDataIsAvailable() {
        val player = launchPlayer("LIVE")
        waitForAnyControl(player, listOf("Pause", "Play", "Play/pause"))
        // EPG is still loading: the drawer must not open and the Guide button must stay hidden.
        instrumentation.runOnMainSync {
            val job = player.javaClass.getDeclaredField("epgJob").apply { isAccessible = true }.get(player) as? kotlinx.coroutines.Job
            job?.cancel()
            player.javaClass.getDeclaredField("epgResult").apply { isAccessible = true }.set(player, null)
            // Ensure panel is hidden since epgResult is cleared
            player.javaClass.getDeclaredMethod("hideEpgPanel").apply { isAccessible = true }.invoke(player)
        }
        val root = player.javaClass.getDeclaredField("rootLayout").apply { isAccessible = true }.get(player) as View
        // 1) Right-edge swipe while loading: drawer must NOT open
        swipe(player, root.width - 1f, root.height * 0.2f, root.width * 0.2f, root.height * 0.2f)
        val panel = player.javaClass.getDeclaredField("epgPanel").apply { isAccessible = true }.get(player) as View
        org.junit.Assert.assertEquals(View.GONE, panel.visibility)
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
        // 2) Right-edge swipe AFTER data loaded: drawer MUST open on the right
        swipe(player, root.width - 1f, root.height * 0.2f, root.width * 0.2f, root.height * 0.2f)
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

        // Wait for lock button to appear
        clickControl(player, "Lock")

        // Wait for unlock button to appear (shows on touch)
        // We need to simulate a touch to show the unlock button
        // For now, just verify the lock button click works
        waitForAnyControl(player, listOf("Unlock"))
        clickControl(player, "Unlock")
        clickControl(player, "Unlock")
        waitForAnyControl(player, listOf("Lock"))
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
