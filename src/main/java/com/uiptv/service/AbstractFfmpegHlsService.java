package com.uiptv.service;

import com.uiptv.util.ServerUrlUtil;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

abstract class AbstractFfmpegHlsService {
    protected static final String STREAM_FILENAME = "stream.m3u8";
    private static final String FFMPEG_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36";
    private static final int HLS_START_MAX_ATTEMPTS = Integer.getInteger("uiptv.hls.start.max.attempts", 400);
    private static final int HLS_START_WAIT_MILLIS = Integer.getInteger("uiptv.hls.start.wait.millis", 100);
    private static final int LIVE_HLS_TIME_SECONDS = Integer.getInteger("uiptv.hls.live.segment.seconds", 2);
    private static final int LIVE_HLS_LIST_SIZE = Integer.getInteger("uiptv.hls.live.list.size", 40);
    private static final int INPUT_RECONNECT_DELAY_MAX_SECONDS = Integer.getInteger("uiptv.hls.input.reconnect.delay.max.seconds", 3);
    private static final long INPUT_RW_TIMEOUT_MICROS = Long.getLong("uiptv.hls.input.rw.timeout.micros", 15_000_000L);
    private static final boolean STALL_WATCHDOG_ENABLED = Boolean.parseBoolean(System.getProperty("uiptv.hls.stall.watchdog.enabled", "true"));
    private static final long STALL_WATCHDOG_THRESHOLD_MILLIS = Long.getLong("uiptv.hls.stall.watchdog.threshold.millis", 25_000L);
    private static final long STALL_WATCHDOG_CHECK_INTERVAL_MILLIS = Long.getLong("uiptv.hls.stall.watchdog.check.interval.millis", 3_000L);
    private static final long STALL_WATCHDOG_RESTART_COOLDOWN_MILLIS = Long.getLong("uiptv.hls.stall.watchdog.restart.cooldown.millis", 10_000L);
    private static final int STALL_WATCHDOG_CONSECUTIVE_REQUIRED = Integer.getInteger("uiptv.hls.stall.watchdog.consecutive.required", 3);
    private static final long STALL_WATCHDOG_STARTUP_GRACE_MILLIS = Long.getLong("uiptv.hls.stall.watchdog.startup.grace.millis", 30_000L);
    private static final long HLS_IDLE_STOP_MILLIS = Long.getLong("uiptv.hls.idle.stop.millis", 30_000L);
    private static final Object PROCESS_LOCK = new Object();
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "uiptv-hls-stall-watchdog");
        thread.setDaemon(true);
        return thread;
    });
    private static Process currentProcess;
    private static List<String> currentCommand;
    private static volatile boolean watchdogStarted;
    private static volatile long lastWatchdogRestartAt;
    private static volatile long currentProcessStartedAt;
    private static int stalledConsecutiveCount;

    @SuppressWarnings("java:S135")
    protected boolean startManagedHlsStream(List<String> command) throws IOException {
        Process process;
        final String requestedInput = extractInputUrl(command);
        final String requestedInputNormalized = normalizeInputForReuse(requestedInput);
        synchronized (PROCESS_LOCK) {
            String currentInput = extractInputUrl(currentCommand);
            String currentInputNormalized = normalizeInputForReuse(currentInput);
            boolean sameInputExact = !requestedInput.isEmpty() && requestedInput.equals(currentInput);
            boolean sameInputNormalized = !requestedInputNormalized.isEmpty() && requestedInputNormalized.equals(currentInputNormalized);
            boolean currentReady = InMemoryHlsService.getInstance().exists(STREAM_FILENAME);
            if ((sameInputExact || sameInputNormalized) && currentProcess != null && currentProcess.isAlive() && currentReady) {
                if (!sameInputExact) {
                    // Keep latest tokenized URL so watchdog restarts use freshest credentials.
                    currentCommand = new ArrayList<>(command);
                }
                return true;
            }
            stopManagedHlsStreamLocked();
            InMemoryHlsService.getInstance().clear();

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            currentProcess = pb.start();
            currentCommand = new ArrayList<>(command);
            currentProcessStartedAt = System.currentTimeMillis();
            stalledConsecutiveCount = 0;
            ensureWatchdogRunning();
            process = currentProcess;
        }

        int attempts = 0;
        final int maxAttempts = HLS_START_MAX_ATTEMPTS;
        while (!InMemoryHlsService.getInstance().exists(STREAM_FILENAME) && attempts < maxAttempts) {
            if (currentProcess != process || !process.isAlive()) {
                break;
            }
            try {
                Thread.sleep(HLS_START_WAIT_MILLIS);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                break;
            }
            attempts++;
        }
        return InMemoryHlsService.getInstance().exists(STREAM_FILENAME);
    }

    protected void stopManagedHlsStream() {
        synchronized (PROCESS_LOCK) {
            stopManagedHlsStreamLocked();
        }
    }

    protected String getLocalHlsPlaybackUrl() {
        return ServerUrlUtil.getLocalServerUrl() + "/hls/" + STREAM_FILENAME;
    }

    protected static List<String> buildCopyHlsCommand(String inputUrl, String outputUrl, boolean vodStylePlaylist, long startOffsetMs) {
        List<String> command = buildHlsCommandPrefix(inputUrl, vodStylePlaylist, startOffsetMs);
        command.add("-c");
        command.add("copy");
        addHlsOutputArgs(command, outputUrl, vodStylePlaylist, false);
        return command;
    }

    protected static List<String> buildCopyHlsCommand(String inputUrl, String outputUrl, boolean vodStylePlaylist) {
        return buildCopyHlsCommand(inputUrl, outputUrl, vodStylePlaylist, 0L);
    }

    private static List<String> buildHlsCommandPrefix(String inputUrl, boolean vodStylePlaylist, long startOffsetMs) {
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-nostdin");
        if (vodStylePlaylist) {
            command.add("-fflags");
            command.add("+genpts");
            if (startOffsetMs > 0) {
                command.add("-ss");
                command.add(String.format(java.util.Locale.ROOT, "%.3f", startOffsetMs / 1000.0));
            }
        }
        addInputNetworkRecoveryArgs(command, inputUrl);
        addInputHttpHeaders(command, inputUrl);
        command.add("-i");
        command.add(inputUrl);
        return command;
    }

    private static void addInputNetworkRecoveryArgs(List<String> command, String inputUrl) {
        if (inputUrl == null) {
            return;
        }
        String lower = inputUrl.trim().toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return;
        }
        // Reduce long mid-stream stalls on unstable providers by forcing reconnect behavior.
        command.add("-rw_timeout");
        command.add(String.valueOf(INPUT_RW_TIMEOUT_MICROS));
        command.add("-reconnect");
        command.add("1");
        command.add("-reconnect_streamed");
        command.add("1");
        command.add("-reconnect_at_eof");
        command.add("1");
        command.add("-reconnect_delay_max");
        command.add(String.valueOf(INPUT_RECONNECT_DELAY_MAX_SECONDS));
    }

    private static void addInputHttpHeaders(List<String> command, String inputUrl) {
        if (inputUrl == null) {
            return;
        }
        String lower = inputUrl.trim().toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return;
        }
        command.add("-user_agent");
        command.add(FFMPEG_USER_AGENT);
        String origin = originOf(inputUrl);
        if (!origin.isEmpty()) {
            command.add("-headers");
            command.add("Origin: " + origin + "\r\nReferer: " + origin + "/\r\n");
        }
    }

    private static String originOf(String url) {
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || scheme.isBlank() || host == null || host.isBlank()) {
                return "";
            }
            int port = uri.getPort();
            boolean defaultPort = port < 0
                    || ("http".equalsIgnoreCase(scheme) && port == 80)
                    || ("https".equalsIgnoreCase(scheme) && port == 443);
            return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
        } catch (Exception _) {
            return "";
        }
    }

    private static void addHlsOutputArgs(List<String> command, String outputUrl, boolean vodStylePlaylist, boolean transcoded) {
        command.add("-f");
        command.add("hls");
        // Keep sequence numbers monotonic across reconnect/restart cycles.
        command.add("-hls_start_number_source");
        command.add("epoch");
        if (vodStylePlaylist) {
            command.add("-hls_time");
            command.add("4");
            command.add("-hls_list_size");
            command.add("0");
            command.add("-hls_playlist_type");
            command.add("event");
            command.add("-hls_flags");
            command.add("append_list+independent_segments");
        } else {
            command.add("-hls_time");
            command.add(String.valueOf(LIVE_HLS_TIME_SECONDS));
            command.add("-hls_list_size");
            command.add(String.valueOf(LIVE_HLS_LIST_SIZE));
            command.add("-hls_flags");
            command.add(transcoded ? "delete_segments+independent_segments" : "delete_segments");
        }
        command.add("-method");
        command.add("PUT");
        command.add(outputUrl);
    }

    private static void ensureWatchdogRunning() {
        if (!STALL_WATCHDOG_ENABLED || watchdogStarted) {
            return;
        }
        synchronized (PROCESS_LOCK) {
            if (watchdogStarted) {
                return;
            }
            WATCHDOG.scheduleWithFixedDelay(AbstractFfmpegHlsService::checkAndRecoverStalledStream,
                    STALL_WATCHDOG_CHECK_INTERVAL_MILLIS,
                    STALL_WATCHDOG_CHECK_INTERVAL_MILLIS,
                    TimeUnit.MILLISECONDS);
            watchdogStarted = true;
        }
    }

    private static void checkAndRecoverStalledStream() {
        synchronized (PROCESS_LOCK) {
            if (currentCommand == null || currentCommand.isEmpty()) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastWatchdogRestartAt < STALL_WATCHDOG_RESTART_COOLDOWN_MILLIS) {
                return;
            }

            boolean processDead = currentProcess == null || !currentProcess.isAlive();
            long lastTsPutAt = InMemoryHlsService.getInstance().getLastTsPutAt();
            long lastClientAccessAt = InMemoryHlsService.getInstance().getLastClientAccessAt();
            long idleAge = calculateViewerIdleAgeMillis(now, currentProcessStartedAt, lastClientAccessAt);
            boolean idleExpired = shouldStopForViewerIdle(idleAge, HLS_IDLE_STOP_MILLIS);
            boolean stalled = lastTsPutAt > 0 && (now - lastTsPutAt) > STALL_WATCHDOG_THRESHOLD_MILLIS;
            boolean inStartupGrace = currentProcessStartedAt > 0 && (now - currentProcessStartedAt) < STALL_WATCHDOG_STARTUP_GRACE_MILLIS;
            if (!processDead && idleExpired) {
                stopManagedHlsStreamLocked();
                return;
            }
            if (!processDead && (inStartupGrace || !stalled)) {
                stalledConsecutiveCount = 0;
            } else if (!processDead) {
                stalledConsecutiveCount++;
            }
            if (!processDead && (inStartupGrace || !stalled || stalledConsecutiveCount < STALL_WATCHDOG_CONSECUTIVE_REQUIRED)) {
                return;
            }

            restartManagedHlsStreamLocked();
        }
    }

    private static void restartManagedHlsStreamLocked() {
        if (currentCommand == null || currentCommand.isEmpty()) {
            return;
        }
        Process processToRestart = currentProcess;
        try {
            if (processToRestart != null && processToRestart.isAlive()) {
                processToRestart.destroy();
                if (!processToRestart.waitFor(1, TimeUnit.SECONDS)) {
                    processToRestart.destroyForcibly();
                }
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            destroyProcessForcibly(processToRestart);
        } catch (Exception _) {
            destroyProcessForcibly(processToRestart);
        }
        // Do not clear in-memory HLS state during watchdog restarts. Keeping recent
        // playlist/segments avoids a hard playback break while ffmpeg reconnects.
        try {
            ProcessBuilder pb = new ProcessBuilder(currentCommand);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            currentProcess = pb.start();
            currentProcessStartedAt = System.currentTimeMillis();
            stalledConsecutiveCount = 0;
            lastWatchdogRestartAt = System.currentTimeMillis();
        } catch (IOException _) {
            // Keep current command metadata so a later watchdog tick can retry again.
        }
    }

    private static void destroyProcessForcibly(Process process) {
        if (process != null) {
            process.destroyForcibly();
        }
    }

    private static String extractInputUrl(List<String> command) {
        if (command == null || command.isEmpty()) {
            return "";
        }
        for (int i = 0; i < command.size() - 1; i++) {
            if ("-i".equals(command.get(i))) {
                return command.get(i + 1) == null ? "" : command.get(i + 1);
            }
        }
        return "";
    }

    private static String normalizeInputForReuse(String inputUrl) {
        if (inputUrl == null || inputUrl.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(inputUrl.trim());
            String filteredQuery = filterStableReuseQuery(uri.getRawQuery());
            return buildNormalizedUri(uri, filteredQuery);
        } catch (Exception _) {
            return inputUrl.trim();
        }
    }

    private static String filterStableReuseQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        StringBuilder filtered = new StringBuilder();
        for (String param : query.split("&")) {
            appendStableReuseParam(filtered, param);
        }
        return filtered.toString();
    }

    private static void appendStableReuseParam(StringBuilder filtered, String param) {
        if (param == null || param.isBlank()) {
            return;
        }
        int eq = param.indexOf('=');
        String key = eq >= 0 ? param.substring(0, eq) : param;
        if (isVolatileReuseParam(key)) {
            return;
        }
        if (!filtered.isEmpty()) {
            filtered.append('&');
        }
        filtered.append(param);
    }

    private static String buildNormalizedUri(URI uri, String filteredQuery) {
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String authority = uri.getRawAuthority() == null ? "" : uri.getRawAuthority();
        String scheme = uri.getScheme() == null ? "" : uri.getScheme();
        return scheme + "://" + authority + path + (filteredQuery.isEmpty() ? "" : "?" + filteredQuery);
    }

    private static boolean isVolatileReuseParam(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = key.toLowerCase();
        return "play_token".equals(normalized)
                || "token".equals(normalized)
                || "auth_token".equals(normalized)
                || "expires".equals(normalized)
                || "signature".equals(normalized)
                || "cacheReset".equalsIgnoreCase(key);
    }

    private static long calculateViewerIdleAgeMillis(long now, long processStartedAt, long lastClientAccessAt) {
        long reference = lastClientAccessAt > 0 ? lastClientAccessAt : processStartedAt;
        if (reference <= 0 || now <= reference) {
            return 0L;
        }
        return now - reference;
    }

    private static boolean shouldStopForViewerIdle(long idleAgeMillis, long idleStopMillis) {
        return idleStopMillis > 0 && idleAgeMillis >= idleStopMillis;
    }

    private static void stopManagedHlsStreamLocked() {
        if (currentProcess != null) {
            if (currentProcess.isAlive()) {
                currentProcess.destroy();
                try {
                    if (!currentProcess.waitFor(1, TimeUnit.SECONDS)) {
                        currentProcess.destroyForcibly();
                    }
                } catch (InterruptedException _) {
                    currentProcess.destroyForcibly();
                    Thread.currentThread().interrupt();
                }
            }
            currentProcess = null;
        }
        currentCommand = null;
        currentProcessStartedAt = 0L;
        stalledConsecutiveCount = 0;
        InMemoryHlsService.getInstance().clear();
    }
}
