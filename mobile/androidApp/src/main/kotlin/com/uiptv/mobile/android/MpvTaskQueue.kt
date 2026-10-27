package com.uiptv.mobile.android

import java.io.Closeable
import java.util.concurrent.Executors

/** A blocked native player must not block Android's UI or another player session. */
internal class MpvTaskQueue : Closeable {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "uiptv-mpv").apply { isDaemon = true }
    }

    fun execute(action: () -> Unit) = executor.execute(action)

    // Drain native calls and release in order; interrupting JNI cannot cancel MPV safely.
    override fun close() = executor.shutdown()
}
