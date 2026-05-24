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

    private fun waitForAnyControl(activity: Activity, descriptions: List<String>): View {
        val deadline = System.currentTimeMillis() + ControlWaitMs
        do {
            descriptions.forEach { description ->
                findViewByDescription(activity.window.decorView, description)?.let { return it }
            }
            Thread.sleep(100)
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
        const val ControlWaitMs = 5_000L
    }
}
