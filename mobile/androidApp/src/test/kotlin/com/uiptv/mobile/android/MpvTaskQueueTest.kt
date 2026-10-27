package com.uiptv.mobile.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MpvTaskQueueTest {
    @Test
    fun stalledNativeCallDoesNotBlockCallerOrNewPlayerSession() {
        val stalled = MpvTaskQueue()
        val nextPlayer = MpvTaskQueue()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val responsive = CountDownLatch(1)
        try {
            stalled.execute { entered.countDown(); release.await() }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            // Closing a stuck player must return without waiting for its native call.
            stalled.close()
            nextPlayer.execute { responsive.countDown() }
            assertTrue("A new player must have its own native queue", responsive.await(2, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            stalled.close()
            nextPlayer.close()
        }
    }

    @Test
    fun releaseWaitsForInFlightCallWithoutInterruptingIt() {
        val queue = MpvTaskQueue()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val interrupted = AtomicBoolean()
        val order = mutableListOf<String>()
        try {
            queue.execute {
                entered.countDown()
                try { release.await() } catch (_: InterruptedException) { interrupted.set(true) }
                order.add("command")
            }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            queue.execute { order.add("destroy"); closed.countDown() }
            queue.close()
            assertFalse(closed.await(100, TimeUnit.MILLISECONDS))
            release.countDown()
            assertTrue(closed.await(2, TimeUnit.SECONDS))
            assertEquals(listOf("command", "destroy"), order)
            assertFalse("JNI calls must not be interrupted during release", interrupted.get())
        } finally {
            release.countDown()
            queue.close()
        }
    }
}
