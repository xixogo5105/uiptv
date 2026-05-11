package com.uiptv.service

import com.uiptv.model.Account
import com.uiptv.util.ServerUrlUtil
import com.uiptv.util.StringUtils.isBlank
import com.uiptv.util.json.KJsonArray
import com.uiptv.util.json.KJsonObject
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit

class LitePlayerFfmpegService private constructor() : AbstractFfmpegHlsService() {
    enum class PlaybackStrategy {
        DIRECT,
        COPY,
        TRANSCODE
    }

    data class PreparedPlayback @JvmOverloads constructor(
        val sourceUrl: String,
        val playbackUrl: String,
        val strategy: PlaybackStrategy,
        val estimatedDurationMs: Long = 0L,
        val startOffsetMs: Long = 0L
    ) {
        fun sourceUrl(): String = sourceUrl

        fun playbackUrl(): String = playbackUrl

        fun strategy(): PlaybackStrategy = strategy

        fun estimatedDurationMs(): Long = estimatedDurationMs

        fun startOffsetMs(): Long = startOffsetMs

        fun usesFfmpeg(): Boolean = strategy != PlaybackStrategy.DIRECT

        fun displayModeLabel(): String =
            when (strategy) {
                PlaybackStrategy.DIRECT -> "Lite direct"
                PlaybackStrategy.COPY -> "Lite using Transmux"
                PlaybackStrategy.TRANSCODE -> "Lite transcoding"
            }
    }

    class ProbeResult @JvmOverloads constructor(
        val formatName: String = "",
        val videoCodec: String = "",
        val audioCodec: String = "",
        val durationMs: Long = 0L
    ) {
        fun formatName(): String = formatName

        fun videoCodec(): String = videoCodec

        fun audioCodec(): String = audioCodec

        fun durationMs(): Long = durationMs

        fun hasVideo(): Boolean = videoCodec.isNotBlank()
        fun hasAudio(): Boolean = audioCodec.isNotBlank()
    }

    fun preparePlayback(rawUri: String?, account: Account?, forceCompatibilityFallback: Boolean, allowTranscoding: Boolean, startOffsetMs: Long): PreparedPlayback {
        val sourceUrl = normalizeSourceUrl(rawUri)
        if (isBlank(sourceUrl)) {
            return PreparedPlayback("", "", PlaybackStrategy.DIRECT, 0L, 0L)
        }

        val vodStylePlaylist = account != null && Account.NOT_LIVE_TV_CHANNELS.contains(account.action)
        val probe = probeSource(sourceUrl)
        val normalizedOffset = maxOf(0L, startOffsetMs)
        val strategy = chooseStrategy(sourceUrl, forceCompatibilityFallback, allowTranscoding)
        if (strategy == PlaybackStrategy.DIRECT) {
            return PreparedPlayback(sourceUrl, normalizeDirectPlaybackUrl(sourceUrl), PlaybackStrategy.DIRECT, estimateDurationMs(probe), 0L)
        }

        if (!ServerUrlUtil.ensureServerForWebPlayback()) {
            return PreparedPlayback(sourceUrl, normalizeDirectPlaybackUrl(sourceUrl), PlaybackStrategy.DIRECT, estimateDurationMs(probe), 0L)
        }

        try {
            val ready = when (strategy) {
                PlaybackStrategy.COPY -> startCopyPlayback(sourceUrl, vodStylePlaylist, normalizedOffset)
                PlaybackStrategy.TRANSCODE -> startTranscodePlayback(sourceUrl, vodStylePlaylist, normalizedOffset)
                PlaybackStrategy.DIRECT -> false
            }
            if (ready) {
                return PreparedPlayback(sourceUrl, getLocalHlsPlaybackUrl(), strategy, estimateDurationMs(probe), normalizedOffset)
            }
        } catch (e: Exception) {
            com.uiptv.util.AppLog.addErrorLog(LitePlayerFfmpegService::class.java, "LitePlayerFfmpegService: failed to prepare $strategy playback: ${e.message}")
        }
        return PreparedPlayback(sourceUrl, normalizeDirectPlaybackUrl(sourceUrl), PlaybackStrategy.DIRECT, estimateDurationMs(probe), 0L)
    }

    fun preparePlayback(rawUri: String?, account: Account?, forceCompatibilityFallback: Boolean, allowTranscoding: Boolean): PreparedPlayback =
        preparePlayback(rawUri, account, forceCompatibilityFallback, allowTranscoding, 0L)

    fun prepareDirectPlayback(rawUri: String?): PreparedPlayback {
        val sourceUrl = normalizeSourceUrl(rawUri)
        return PreparedPlayback(sourceUrl, normalizeDirectPlaybackUrl(sourceUrl), PlaybackStrategy.DIRECT, estimateDurationMs(probeSource(sourceUrl)), 0L)
    }

    fun isManagedPlaybackUrl(url: String?): Boolean =
        !isBlank(url) && url!!.lowercase(Locale.ROOT).startsWith(getLocalHlsPlaybackUrl().lowercase(Locale.ROOT))

    fun stopPlayback() {
        stopManagedHlsStream()
    }

    fun startCopyPlayback(inputUrl: String, vodStylePlaylist: Boolean, startOffsetMs: Long): Boolean {
        val outputUrl = ServerUrlUtil.getLocalServerUrl() + "/hls-upload/" + STREAM_FILENAME
        return startManagedHlsStream(buildCopyHlsCommand(inputUrl, outputUrl, vodStylePlaylist, startOffsetMs))
    }

    fun startTranscodePlayback(inputUrl: String, vodStylePlaylist: Boolean, startOffsetMs: Long): Boolean {
        val outputUrl = ServerUrlUtil.getLocalServerUrl() + "/hls-upload/" + STREAM_FILENAME
        return startManagedHlsStream(buildTranscodeHlsCommand(inputUrl, outputUrl, vodStylePlaylist, startOffsetMs))
    }

    companion object {
        private val INSTANCE = LitePlayerFfmpegService()

        @JvmStatic
        fun getInstance(): LitePlayerFfmpegService = INSTANCE

        @JvmStatic
        fun chooseStrategy(sourceUrl: String?, forceCompatibilityFallback: Boolean, allowTranscoding: Boolean): PlaybackStrategy {
            if (isBlank(sourceUrl)) return PlaybackStrategy.DIRECT
            val lower = sourceUrl!!.lowercase(Locale.ROOT)
            val probe = probeSource(sourceUrl)
            if (lower.startsWith(resolveLocalServerUrl().lowercase(Locale.ROOT) + "/hls/")) return PlaybackStrategy.DIRECT
            if (!forceCompatibilityFallback && isLikelyDirectPlayableContainer(lower)) return PlaybackStrategy.DIRECT
            if (probe != null) return chooseStrategy(sourceUrl, probe, forceCompatibilityFallback, allowTranscoding)
            if (looksLikeTransportStream(lower)) return PlaybackStrategy.COPY
            return PlaybackStrategy.DIRECT
        }

        @JvmStatic
        fun chooseStrategy(sourceUrl: String?, probe: ProbeResult?, forceCompatibilityFallback: Boolean, allowTranscoding: Boolean): PlaybackStrategy {
            val lower = sourceUrl?.lowercase(Locale.ROOT).orEmpty()
            if (probe == null) return PlaybackStrategy.DIRECT
            val codecsCompatible = isLiteCompatibleVideoCodec(probe.videoCodec) && isLiteCompatibleAudioCodec(probe.audioCodec)
            if (!codecsCompatible) return if (allowTranscoding) PlaybackStrategy.TRANSCODE else PlaybackStrategy.DIRECT
            if (looksLikeTransportStream(lower) || probe.formatName.lowercase(Locale.ROOT).contains("mpegts")) return PlaybackStrategy.COPY
            if (!forceCompatibilityFallback && (isLikelyDirectPlayableContainer(lower) || isDirectPlayableFormat(probe.formatName))) return PlaybackStrategy.DIRECT
            return PlaybackStrategy.COPY
        }

        @JvmStatic
        fun isLikelyDirectPlayableContainer(sourceUrl: String?): Boolean {
            if (isBlank(sourceUrl)) return false
            val lower = sourceUrl!!.lowercase(Locale.ROOT)
            return lower.contains(".m3u8") ||
                lower.contains("extension=m3u8") ||
                lower.contains(".mp4") ||
                lower.contains("extension=mp4") ||
                lower.contains(".m4v") ||
                lower.contains("extension=m4v") ||
                lower.contains(".m4a") ||
                lower.contains(".aac") ||
                lower.contains(".mp3")
        }

        private fun looksLikeTransportStream(sourceUrl: String): Boolean =
            sourceUrl.contains("extension=ts") || sourceUrl.matches(Regex(".*\\.ts(\\?.*)?$"))

        @JvmStatic
        private fun isDirectPlayableFormat(formatName: String?): Boolean {
            val lower = formatName?.lowercase(Locale.ROOT).orEmpty()
            return lower.contains("hls") || lower.contains("applehttp") || lower.contains("mp4") || lower.contains("mov")
        }

        @JvmStatic
        private fun isLiteCompatibleVideoCodec(codec: String?): Boolean {
            if (isBlank(codec)) return true
            val lower = codec!!.lowercase(Locale.ROOT)
            return lower.contains("h264") || lower.contains("avc")
        }

        @JvmStatic
        private fun isLiteCompatibleAudioCodec(codec: String?): Boolean {
            if (isBlank(codec)) return true
            val lower = codec!!.lowercase(Locale.ROOT)
            return lower.contains("aac") || lower.contains("mp3") || lower.contains("mp4a")
        }

        private fun normalizeSourceUrl(rawUri: String?): String {
            var sourceUrl = rawUri?.trim().orEmpty()
            if (!sourceUrl.startsWith("http") && !sourceUrl.startsWith("file:")) {
                val file = File(sourceUrl)
                if (file.exists()) {
                    sourceUrl = file.toURI().toString()
                }
            }
            return sourceUrl
        }

        private fun normalizeDirectPlaybackUrl(sourceUrl: String): String =
            sourceUrl.replace("extension=ts", "extension=m3u8")

        private fun resolveLocalServerUrl(): String =
            try {
                ServerUrlUtil.getLocalServerUrl()
            } catch (_: Exception) {
                "http://127.0.0.1:8080"
            }

        @JvmStatic
        fun probeSource(sourceUrl: String?): ProbeResult? {
            return try {
                val process = buildProbeProcess(sourceUrl.orEmpty()).start()
                if (!awaitSuccessfulProbe(process)) {
                    null
                } else {
                    val json = String(process.inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    if (json.isBlank()) null else toProbeResult(KJsonObject(json))
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                null
            } catch (_: IOException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        private fun buildProbeProcess(sourceUrl: String): ProcessBuilder =
            ProcessBuilder("ffprobe", "-v", "error", "-print_format", "json", "-show_format", "-show_streams", sourceUrl)

        private fun awaitSuccessfulProbe(process: Process): Boolean {
            if (!process.waitFor(probeTimeoutMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                return false
            }
            return process.exitValue() == 0
        }

        private fun probeTimeoutMillis(): Long =
            java.lang.Long.getLong("uiptv.ffprobe.timeout.ms", 4_000L)

        @JvmStatic
        private fun toProbeResult(root: KJsonObject): ProbeResult {
            val format = root.optJSONObject("format")
            val formatName = format?.optString("format_name").orEmpty()
            val durationMs = parseDurationMs(format?.opt("duration"))
            val codecs = extractCodecs(root.optJSONArray("streams"))
            return ProbeResult(formatName, codecs.videoCodec, codecs.audioCodec, durationMs)
        }

        private fun parseDurationMs(rawDuration: Any?): Long {
            if (rawDuration == null) return 0L
            return try {
                val seconds = rawDuration.toString().toDouble()
                if (!seconds.isFinite() || seconds <= 0) 0L else kotlin.math.round(seconds * 1000.0).toLong()
            } catch (_: NumberFormatException) {
                0L
            }
        }

        private fun estimateDurationMs(probe: ProbeResult?): Long = probe?.durationMs ?: 0L

        private fun extractCodecs(streams: KJsonArray?): CodecPair {
            var videoCodec = ""
            var audioCodec = ""
            if (streams == null) return CodecPair(videoCodec, audioCodec)
            for (i in 0 until streams.length()) {
                val stream = streams.optJSONObject(i) ?: continue
                val codecType = stream.optString("codec_type", "")
                if (codecType.equals("video", true) && videoCodec.isBlank()) {
                    videoCodec = stream.optString("codec_name", "")
                } else if (codecType.equals("audio", true) && audioCodec.isBlank()) {
                    audioCodec = stream.optString("codec_name", "")
                }
            }
            return CodecPair(videoCodec, audioCodec)
        }

        private data class CodecPair(val videoCodec: String, val audioCodec: String)
    }
}
