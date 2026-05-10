package com.uiptv.service

import com.uiptv.util.ServerUrlUtil
import java.io.IOException
import java.net.URI
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

abstract class AbstractFfmpegHlsService {
    protected fun startManagedHlsStream(command: List<String>): Boolean {
        val requestedInput = extractInputUrl(command)
        val requestedInputNormalized = normalizeInputForReuse(requestedInput)
        val process: Process
        synchronized(PROCESS_LOCK) {
            val currentInput = extractInputUrl(currentCommand)
            val currentInputNormalized = normalizeInputForReuse(currentInput)
            val sameInputExact = requestedInput.isNotEmpty() && requestedInput == currentInput
            val sameInputNormalized = requestedInputNormalized.isNotEmpty() && requestedInputNormalized == currentInputNormalized
            val currentReady = InMemoryHlsService.getInstance().exists(STREAM_FILENAME)
            if ((sameInputExact || sameInputNormalized) && currentProcess != null && currentProcess!!.isAlive && currentReady) {
                if (!sameInputExact) {
                    currentCommand = ArrayList(command)
                }
                return true
            }
            stopManagedHlsStreamLocked()
            InMemoryHlsService.getInstance().clear()

            val pb = ProcessBuilder(command)
            pb.redirectErrorStream(true)
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
            currentProcess = pb.start()
            currentCommand = ArrayList(command)
            currentProcessStartedAt = System.currentTimeMillis()
            stalledConsecutiveCount = 0
            ensureWatchdogRunning()
            process = currentProcess!!
        }

        var attempts = 0
        while (!InMemoryHlsService.getInstance().exists(STREAM_FILENAME) && attempts < HLS_START_MAX_ATTEMPTS) {
            if (currentProcess !== process || !process.isAlive) {
                break
            }
            try {
                Thread.sleep(HLS_START_WAIT_MILLIS.toLong())
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
            attempts++
        }
        return InMemoryHlsService.getInstance().exists(STREAM_FILENAME)
    }

    protected fun stopManagedHlsStream() {
        synchronized(PROCESS_LOCK) {
            stopManagedHlsStreamLocked()
        }
    }

    protected fun getLocalHlsPlaybackUrl(): String = ServerUrlUtil.getLocalServerUrl() + "/hls/" + STREAM_FILENAME

    companion object {
        const val STREAM_FILENAME: String = "stream.m3u8"
        private const val FFMPEG_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
        private val HLS_START_MAX_ATTEMPTS: Int = Integer.getInteger("uiptv.hls.start.max.attempts", 400)
        private val HLS_START_WAIT_MILLIS: Int = Integer.getInteger("uiptv.hls.start.wait.millis", 10)
        private val LIVE_HLS_TIME_SECONDS: Int = Integer.getInteger("uiptv.hls.live.segment.seconds", 2)
        private val LIVE_HLS_LIST_SIZE: Int = Integer.getInteger("uiptv.hls.live.list.size", 40)
        private val INPUT_RECONNECT_DELAY_MAX_SECONDS: Int = Integer.getInteger("uiptv.hls.input.reconnect.delay.max.seconds", 3)
        private val INPUT_RW_TIMEOUT_MICROS: Long = java.lang.Long.getLong("uiptv.hls.input.rw.timeout.micros", 15_000_000L)
        private val STALL_WATCHDOG_ENABLED: Boolean = java.lang.Boolean.parseBoolean(System.getProperty("uiptv.hls.stall.watchdog.enabled", "true"))
        private val STALL_WATCHDOG_THRESHOLD_MILLIS: Long = java.lang.Long.getLong("uiptv.hls.stall.watchdog.threshold.millis", 25_000L)
        private val STALL_WATCHDOG_CHECK_INTERVAL_MILLIS: Long = java.lang.Long.getLong("uiptv.hls.stall.watchdog.check.interval.millis", 3_000L)
        private val STALL_WATCHDOG_RESTART_COOLDOWN_MILLIS: Long = java.lang.Long.getLong("uiptv.hls.stall.watchdog.restart.cooldown.millis", 10_000L)
        private val STALL_WATCHDOG_CONSECUTIVE_REQUIRED: Int = Integer.getInteger("uiptv.hls.stall.watchdog.consecutive.required", 3)
        private val STALL_WATCHDOG_STARTUP_GRACE_MILLIS: Long = java.lang.Long.getLong("uiptv.hls.stall.watchdog.startup.grace.millis", 30_000L)
        private val HLS_IDLE_STOP_MILLIS: Long = java.lang.Long.getLong("uiptv.hls.idle.stop.millis", 30_000L)
        private val PROCESS_LOCK = Any()
        private val WATCHDOG: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "uiptv-hls-stall-watchdog").apply { isDaemon = true }
        }
        private var currentProcess: Process? = null
        private var currentCommand: List<String>? = null
        @Volatile private var watchdogStarted = false
        @Volatile private var lastWatchdogRestartAt = 0L
        @Volatile private var currentProcessStartedAt = 0L
        private var stalledConsecutiveCount = 0

        @JvmStatic
        protected fun buildCopyHlsCommand(inputUrl: String, outputUrl: String, vodStylePlaylist: Boolean, startOffsetMs: Long): List<String> {
            val command = buildHlsCommandPrefix(inputUrl, vodStylePlaylist, startOffsetMs)
            command.add("-c")
            command.add("copy")
            addHlsOutputArgs(command, outputUrl, vodStylePlaylist, false)
            return command
        }

        @JvmStatic
        protected fun buildCopyHlsCommand(inputUrl: String, outputUrl: String, vodStylePlaylist: Boolean): List<String> =
            buildCopyHlsCommand(inputUrl, outputUrl, vodStylePlaylist, 0L)

        @JvmStatic
        protected fun buildHlsCommandPrefix(inputUrl: String, vodStylePlaylist: Boolean, startOffsetMs: Long): MutableList<String> {
            val command = ArrayList<String>()
            command.add("ffmpeg")
            command.add("-nostdin")
            if (vodStylePlaylist) {
                command.add("-fflags")
                command.add("+genpts")
                if (startOffsetMs > 0) {
                    command.add("-ss")
                    command.add(String.format(Locale.ROOT, "%.3f", startOffsetMs / 1000.0))
                }
            }
            addInputNetworkRecoveryArgs(command, inputUrl)
            addInputHttpHeaders(command, inputUrl)
            command.add("-i")
            command.add(inputUrl)
            return command
        }

        @JvmStatic
        protected fun buildTranscodeHlsCommand(inputUrl: String, outputUrl: String, vodStylePlaylist: Boolean, startOffsetMs: Long): List<String> {
            val command = buildHlsCommandPrefix(inputUrl, vodStylePlaylist, startOffsetMs)
            command.add("-c:v")
            command.add("libx264")
            command.add("-preset")
            command.add("ultrafast")
            command.add("-tune")
            command.add("zerolatency")
            command.add("-crf")
            command.add("28")
            command.add("-profile:v")
            command.add("baseline")
            command.add("-pix_fmt")
            command.add("yuv420p")
            command.add("-c:a")
            command.add("aac")
            command.add("-b:a")
            command.add("96k")
            command.add("-ac")
            command.add("2")
            addHlsOutputArgs(command, outputUrl, vodStylePlaylist, true)
            return command
        }

        private fun addInputNetworkRecoveryArgs(command: MutableList<String>, inputUrl: String?) {
            if (inputUrl == null) return
            val lower = inputUrl.trim().lowercase()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) return
            command.add("-rw_timeout")
            command.add(INPUT_RW_TIMEOUT_MICROS.toString())
            command.add("-reconnect")
            command.add("1")
            command.add("-reconnect_streamed")
            command.add("1")
            command.add("-reconnect_at_eof")
            command.add("1")
            command.add("-reconnect_delay_max")
            command.add(INPUT_RECONNECT_DELAY_MAX_SECONDS.toString())
        }

        private fun addInputHttpHeaders(command: MutableList<String>, inputUrl: String?) {
            if (inputUrl == null) return
            val lower = inputUrl.trim().lowercase()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) return
            command.add("-user_agent")
            command.add(FFMPEG_USER_AGENT)
            val origin = originOf(inputUrl)
            if (origin.isNotEmpty()) {
                command.add("-headers")
                command.add("Origin: $origin\r\nReferer: $origin/\r\n")
            }
        }

        private fun originOf(url: String): String {
            return try {
                val uri = URI.create(url.trim())
                val scheme = uri.scheme
                val host = uri.host
                if (scheme.isNullOrBlank() || host.isNullOrBlank()) {
                    ""
                } else {
                    val port = uri.port
                    val defaultPort = port < 0 ||
                        (scheme.equals("http", true) && port == 80) ||
                        (scheme.equals("https", true) && port == 443)
                    if (defaultPort) "$scheme://$host" else "$scheme://$host:$port"
                }
            } catch (_: Exception) {
                ""
            }
        }

        @JvmStatic
        private fun addHlsOutputArgs(command: MutableList<String>, outputUrl: String, vodStylePlaylist: Boolean, transcoded: Boolean) {
            command.add("-f")
            command.add("hls")
            command.add("-hls_start_number_source")
            command.add("epoch")
            if (vodStylePlaylist) {
                command.add("-hls_time")
                command.add("4")
                command.add("-hls_list_size")
                command.add("0")
                command.add("-hls_playlist_type")
                command.add("event")
                command.add("-hls_flags")
                command.add("append_list+independent_segments")
            } else {
                command.add("-hls_time")
                command.add(LIVE_HLS_TIME_SECONDS.toString())
                command.add("-hls_list_size")
                command.add(LIVE_HLS_LIST_SIZE.toString())
                command.add("-hls_flags")
                command.add(if (transcoded) "delete_segments+independent_segments" else "delete_segments")
            }
            command.add("-method")
            command.add("PUT")
            command.add(outputUrl)
        }

        private fun ensureWatchdogRunning() {
            if (!STALL_WATCHDOG_ENABLED || watchdogStarted) return
            synchronized(PROCESS_LOCK) {
                if (watchdogStarted) return
                WATCHDOG.scheduleWithFixedDelay(
                    { checkAndRecoverStalledStream() },
                    STALL_WATCHDOG_CHECK_INTERVAL_MILLIS,
                    STALL_WATCHDOG_CHECK_INTERVAL_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                watchdogStarted = true
            }
        }

        private fun checkAndRecoverStalledStream() {
            synchronized(PROCESS_LOCK) {
                if (currentCommand.isNullOrEmpty()) return
                val now = System.currentTimeMillis()
                if (now - lastWatchdogRestartAt < STALL_WATCHDOG_RESTART_COOLDOWN_MILLIS) return

                val processDead = currentProcess == null || !currentProcess!!.isAlive
                val lastTsPutAt = InMemoryHlsService.getInstance().getLastTsPutAt()
                val lastClientAccessAt = InMemoryHlsService.getInstance().getLastClientAccessAt()
                val idleAge = calculateViewerIdleAgeMillis(now, currentProcessStartedAt, lastClientAccessAt)
                val idleExpired = shouldStopForViewerIdle(idleAge, HLS_IDLE_STOP_MILLIS)
                val stalled = lastTsPutAt > 0 && now - lastTsPutAt > STALL_WATCHDOG_THRESHOLD_MILLIS
                val inStartupGrace = currentProcessStartedAt > 0 && now - currentProcessStartedAt < STALL_WATCHDOG_STARTUP_GRACE_MILLIS
                if (!processDead && idleExpired) {
                    stopManagedHlsStreamLocked()
                    return
                }
                if (!processDead && (inStartupGrace || !stalled)) {
                    stalledConsecutiveCount = 0
                } else if (!processDead) {
                    stalledConsecutiveCount++
                }
                if (!processDead && (inStartupGrace || !stalled || stalledConsecutiveCount < STALL_WATCHDOG_CONSECUTIVE_REQUIRED)) {
                    return
                }
                restartManagedHlsStreamLocked()
            }
        }

        private fun restartManagedHlsStreamLocked() {
            val command = currentCommand ?: return
            val processToRestart = currentProcess
            try {
                if (processToRestart != null && processToRestart.isAlive) {
                    processToRestart.destroy()
                    if (!processToRestart.waitFor(1, TimeUnit.SECONDS)) {
                        processToRestart.destroyForcibly()
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                destroyProcessForcibly(processToRestart)
            } catch (_: Exception) {
                destroyProcessForcibly(processToRestart)
            }
            try {
                val pb = ProcessBuilder(command)
                pb.redirectErrorStream(true)
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
                currentProcess = pb.start()
                currentProcessStartedAt = System.currentTimeMillis()
                stalledConsecutiveCount = 0
                lastWatchdogRestartAt = System.currentTimeMillis()
            } catch (_: IOException) {
            }
        }

        private fun destroyProcessForcibly(process: Process?) {
            process?.destroyForcibly()
        }

        private fun extractInputUrl(command: List<String>?): String {
            if (command.isNullOrEmpty()) return ""
            for (i in 0 until command.size - 1) {
                if (command[i] == "-i") {
                    return command[i + 1]
                }
            }
            return ""
        }

        private fun normalizeInputForReuse(inputUrl: String?): String {
            if (inputUrl.isNullOrBlank()) return ""
            return try {
                val uri = URI.create(inputUrl.trim())
                val filteredQuery = filterStableReuseQuery(uri.rawQuery)
                buildNormalizedUri(uri, filteredQuery)
            } catch (_: Exception) {
                inputUrl.trim()
            }
        }

        private fun filterStableReuseQuery(query: String?): String {
            if (query.isNullOrBlank()) return ""
            val filtered = StringBuilder()
            query.split("&").forEach { param -> appendStableReuseParam(filtered, param) }
            return filtered.toString()
        }

        private fun appendStableReuseParam(filtered: StringBuilder, param: String?) {
            if (param.isNullOrBlank()) return
            val eq = param.indexOf('=')
            val key = if (eq >= 0) param.substring(0, eq) else param
            if (isVolatileReuseParam(key)) return
            if (filtered.isNotEmpty()) filtered.append('&')
            filtered.append(param)
        }

        private fun buildNormalizedUri(uri: URI, filteredQuery: String): String {
            val path = uri.rawPath ?: ""
            val authority = uri.rawAuthority ?: ""
            val scheme = uri.scheme ?: ""
            return scheme + "://" + authority + path + if (filteredQuery.isEmpty()) "" else "?$filteredQuery"
        }

        private fun isVolatileReuseParam(key: String?): Boolean {
            if (key.isNullOrBlank()) return false
            val normalized = key.lowercase()
            return normalized == "play_token" ||
                normalized == "token" ||
                normalized == "auth_token" ||
                normalized == "expires" ||
                normalized == "signature" ||
                key.equals("cacheReset", true)
        }

        @JvmStatic
        private fun calculateViewerIdleAgeMillis(now: Long, processStartedAt: Long, lastClientAccessAt: Long): Long {
            val reference = if (lastClientAccessAt > 0) lastClientAccessAt else processStartedAt
            if (reference <= 0 || now <= reference) return 0L
            return now - reference
        }

        @JvmStatic
        private fun shouldStopForViewerIdle(idleAgeMillis: Long, idleStopMillis: Long): Boolean =
            idleStopMillis > 0 && idleAgeMillis >= idleStopMillis

        private fun stopManagedHlsStreamLocked() {
            currentProcess?.let { process ->
                if (process.isAlive) {
                    process.destroy()
                    try {
                        if (!process.waitFor(1, TimeUnit.SECONDS)) {
                            process.destroyForcibly()
                        }
                    } catch (_: InterruptedException) {
                        process.destroyForcibly()
                        Thread.currentThread().interrupt()
                    }
                }
            }
            currentProcess = null
            currentCommand = null
            currentProcessStartedAt = 0L
            stalledConsecutiveCount = 0
            InMemoryHlsService.getInstance().clear()
        }
    }
}
