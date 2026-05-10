package com.uiptv.service

import com.uiptv.util.ServerUrlUtil
import java.io.IOException

object FfmpegService : AbstractFfmpegHlsService() {
    fun isTransmuxingNeeded(url: String?): Boolean = url != null && url.contains("extension=ts")

    @Synchronized
    @Throws(IOException::class)
    fun startTransmuxing(inputUrl: String): Boolean = startTransmuxing(inputUrl, false)

    @Synchronized
    @Throws(IOException::class)
    fun startTransmuxing(inputUrl: String, vodStylePlaylist: Boolean): Boolean {
        val outputUrl = ServerUrlUtil.getLocalServerUrl() + "/hls-upload/" + STREAM_FILENAME
        return startManagedHlsStream(buildHlsCommand(inputUrl, outputUrl, vodStylePlaylist))
    }

    @Synchronized
    @Throws(IOException::class)
    fun startTranscoding(inputUrl: String, vodStylePlaylist: Boolean): Boolean {
        val outputUrl = ServerUrlUtil.getLocalServerUrl() + "/hls-upload/" + STREAM_FILENAME
        return startManagedHlsStream(buildTranscodeHlsCommand(inputUrl, outputUrl, vodStylePlaylist, 0L))
    }

    @Synchronized
    fun stopTransmuxing() {
        stopManagedHlsStream()
    }

    @JvmStatic
    fun getInstance(): FfmpegService = this

    @JvmStatic
    fun buildHlsCommand(inputUrl: String, outputUrl: String, vodStylePlaylist: Boolean): List<String> =
        buildCopyHlsCommand(inputUrl, outputUrl, vodStylePlaylist, 0L)
}
