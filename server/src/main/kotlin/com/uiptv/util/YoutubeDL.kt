package com.uiptv.util

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object YoutubeDL {
    private const val DEFAULT_YT_DLP_COMMAND = "yt-dlp"
    private const val DEFAULT_YOUTUBE_DL_COMMAND = "youtube-dl"
    private var customYtDlpPath: String? = null
    private var customYoutubeDlPath: String? = null

    @JvmStatic
    fun setYtDlpPath(path: String?) {
        customYtDlpPath = path
    }

    @JvmStatic
    fun setYoutubeDlPath(path: String?) {
        customYoutubeDlPath = path
    }

    @JvmStatic
    fun getStreamingUrl(videoUrl: String?): String? {
        var streamUrl: String? = null

        if (customYtDlpPath != null) {
            streamUrl = tryGetStreamUrl(customYtDlpPath!!, videoUrl)
        }
        if (streamUrl == null) {
            streamUrl = tryGetStreamUrl(DEFAULT_YT_DLP_COMMAND, videoUrl)
        }
        if (streamUrl != null) {
            return streamUrl
        }

        if (customYoutubeDlPath != null) {
            streamUrl = tryGetStreamUrl(customYoutubeDlPath!!, videoUrl)
        }
        if (streamUrl == null) {
            streamUrl = tryGetStreamUrl(DEFAULT_YOUTUBE_DL_COMMAND, videoUrl)
        }
        if (streamUrl != null) {
            return streamUrl
        }

        AppLog.addWarningLog(YoutubeDL::class.java, "Neither yt-dlp nor youtube-dl found or failed. Falling back to original URL.")
        return videoUrl
    }

    private fun tryGetStreamUrl(command: String, videoUrl: String?): String? {
        if (videoUrl.isNullOrBlank()) {
            AppLog.addErrorLog(YoutubeDL::class.java, "Error: Video URL is null or empty. Cannot execute $command")
            return null
        }
        val trimmedVideoUrl = videoUrl.trim()

        try {
            val processBuilder = ProcessBuilder(command, "-g", "-f", "b", trimmedVideoUrl)
            processBuilder.redirectErrorStream(true)
            val process = processBuilder.start()

            val output = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    output.append(line).append("\n")
                }
            }

            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                AppLog.addWarningLog(YoutubeDL::class.java, "$command process timed out for: $trimmedVideoUrl")
                return null
            }

            if (process.exitValue() == 0) {
                val streamUrl = output.toString().trim()
                if (streamUrl.isNotEmpty() && streamUrl.startsWith("http")) {
                    return streamUrl
                }
            } else {
                AppLog.addErrorLog(YoutubeDL::class.java, "$command failed for: $trimmedVideoUrl")
                AppLog.addErrorLog(YoutubeDL::class.java, "Exit code: ${process.exitValue()}")
                AppLog.addErrorLog(YoutubeDL::class.java, "Output/Error: $output")
            }
        } catch (e: IOException) {
            AppLog.addErrorLog(YoutubeDL::class.java, "Error: The command '$command' was not found. Please ensure yt-dlp or youtube-dl is installed and accessible in your system's PATH, or set the executable path using YoutubeDL.setYtDlpPath() or YoutubeDL.setYoutubeDlPath().")
            AppLog.addErrorLog(YoutubeDL::class.java, "Details: ${e.message}")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            AppLog.addWarningLog(YoutubeDL::class.java, "Execution interrupted while running $command for $trimmedVideoUrl")
        } catch (e: RuntimeException) {
            AppLog.addErrorLog(YoutubeDL::class.java, "An unexpected error occurred while executing $command for $trimmedVideoUrl: ${e.message}")
        }
        return null
    }
}
