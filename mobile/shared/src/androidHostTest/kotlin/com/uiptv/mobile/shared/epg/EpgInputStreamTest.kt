package com.uiptv.mobile.shared.epg

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EpgInputStreamTest {
    @Test fun cancelledGuideDoesNotReadAnyMoreUpstreamBytes() {
        var reads = 0
        val upstream = object : InputStream() {
            override fun read(): Int { reads++; return 1 }
        }
        val request = Job()
        val stream = EpgInputStream(upstream, { request.ensureActive() })
        assertEquals(1, stream.read())
        request.cancel()
        assertFailsWith<CancellationException> { stream.read() }
        assertFailsWith<CancellationException> { stream.read(ByteArray(8), 0, 8) }
        assertEquals(1, reads)
    }

    @Test fun byteLimitCoversMixedSingleAndBulkReads() {
        val stream = EpgInputStream(ByteArrayInputStream(ByteArray(10)), {}, remaining = 3)
        stream.read()
        assertEquals(2, stream.read(ByteArray(2)))
        assertFailsWith<IOException> { stream.read() }
    }

    @Test fun expiredGuideDoesNotTouchTheUpstreamSource() {
        var reads = 0
        val upstream = object : InputStream() {
            override fun read(): Int { reads++; return 1 }
        }
        val stream = EpgInputStream(upstream, {}, deadline = 0)
        assertFailsWith<IOException> { stream.read() }
        assertEquals(0, reads)
    }
}
