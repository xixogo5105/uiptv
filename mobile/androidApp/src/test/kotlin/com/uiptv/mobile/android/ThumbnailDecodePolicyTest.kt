package com.uiptv.mobile.android

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class ThumbnailDecodePolicyTest {
    @Test fun providerPosterIsSampledBeforeItAllocatesAFullBitmap() {
        val sample = ThumbnailDecodePolicy.sampleSize(7680, 4320)
        assertEquals(16, sample)
        assertTrue(7680 / sample <= 512)
        assertEquals(1, ThumbnailDecodePolicy.sampleSize(200, 100))
        assertEquals(1, ThumbnailDecodePolicy.sampleSize(-1, -1))
    }

    @Test fun responseSizeLimitRejectsOversizedImages() {
        try {
            ThumbnailDecodePolicy.readBytes(ByteArrayInputStream(ByteArray(17)), limit = 16)
            fail("Oversized image was accepted")
        } catch (_: IOException) { }
        assertArrayEquals(ByteArray(16), ThumbnailDecodePolicy.readBytes(ByteArrayInputStream(ByteArray(16)), limit = 16))
    }
}
