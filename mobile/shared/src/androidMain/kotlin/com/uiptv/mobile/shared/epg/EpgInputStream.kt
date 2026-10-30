package com.uiptv.mobile.shared.epg

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException

/** Bounds decompressed guide data and checks the requesting coroutine before doing more IO. */
internal class EpgInputStream(
    input: InputStream,
    private val checkCancelled: () -> Unit,
    private var remaining: Long = 200L * 1024 * 1024,
    private val deadline: Long = System.nanoTime() + 60_000_000_000L
) : FilterInputStream(input) {
    var bytesRead: Long = 0
        private set

    private fun checkRead() {
        checkCancelled()
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Guide loading cancelled")
        if (System.nanoTime() > deadline) throw IOException("Guide loading deadline exceeded")
    }

    private fun consumed(count: Int) {
        checkRead()
        if (count > 0) {
            remaining -= count
            bytesRead += count
            if (remaining < 0) throw IOException("Guide exceeds size limit")
        }
    }

    override fun read(): Int {
        checkRead()
        return `in`.read().also { consumed(if (it < 0) 0 else 1) }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        checkRead()
        return `in`.read(b, off, len).also(::consumed)
    }
}
