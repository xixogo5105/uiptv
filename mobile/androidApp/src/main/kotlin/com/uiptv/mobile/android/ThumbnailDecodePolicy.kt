package com.uiptv.mobile.android

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Provider logos are untrusted images, not full-size photo surfaces. */
internal object ThumbnailDecodePolicy {
    fun sampleSize(width: Int, height: Int, maxDimension: Int = 512): Int {
        require(maxDimension > 0)
        var sample = 1
        while (maxOf(width, height) / sample > maxDimension) sample *= 2
        return sample
    }

    fun readBytes(input: InputStream, limit: Int = 8 * 1024 * 1024): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return output.toByteArray()
            if (read > limit - output.size()) throw IOException("Thumbnail exceeds size limit")
            output.write(buffer, 0, read)
        }
    }
}
