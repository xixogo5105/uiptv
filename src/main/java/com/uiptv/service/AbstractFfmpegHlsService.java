package com.uiptv.service;

import com.uiptv.util.ServerUrlUtil;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

abstract class AbstractFfmpegHlsService {
    protected static final String STREAM_FILENAME = "stream.m3u8";
    private static final String FFMPEG_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36";
    private static final Object PROCESS_LOCK = new Object();
    private static Process currentProcess;

    @SuppressWarnings("java:S135")
    protected boolean startManagedHlsStream(List<String> command) throws IOException {
        Process process;
        synchronized (PROCESS_LOCK) {
            stopManagedHlsStreamLocked();
            InMemoryHlsService.getInstance().clear();

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            currentProcess = pb.start();
            process = currentProcess;
        }

        int attempts = 0;
        final int maxAttempts = 150;
        while (!InMemoryHlsService.getInstance().exists(STREAM_FILENAME) && attempts < maxAttempts) {
            if (currentProcess != process || !process.isAlive()) {
                break;
            }
            try {
                Thread.sleep(100);
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

    protected static List<String> buildTranscodeHlsCommand(String inputUrl, String outputUrl, boolean vodStylePlaylist, long startOffsetMs) {
        List<String> command = buildHlsCommandPrefix(inputUrl, vodStylePlaylist, startOffsetMs);
        command.add("-map");
        command.add("0:v:0?");
        command.add("-map");
        command.add("0:a:0?");
        command.add("-sn");
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("ultrafast");
        command.add("-tune");
        command.add("zerolatency");
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("128k");
        command.add("-ac");
        command.add("2");
        addHlsOutputArgs(command, outputUrl, vodStylePlaylist, true);
        return command;
    }

    protected static List<String> buildTranscodeHlsCommand(String inputUrl, String outputUrl, boolean vodStylePlaylist) {
        return buildTranscodeHlsCommand(inputUrl, outputUrl, vodStylePlaylist, 0L);
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
        addInputHttpHeaders(command, inputUrl);
        command.add("-i");
        command.add(inputUrl);
        return command;
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
        if (vodStylePlaylist) {
            command.add("-hls_time");
            command.add("4");
            command.add("-hls_list_size");
            command.add("0");
            command.add("-hls_playlist_type");
            command.add("vod");
            command.add("-hls_flags");
            command.add("independent_segments");
        } else {
            command.add("-hls_time");
            command.add("2");
            command.add("-hls_list_size");
            command.add("20");
            command.add("-hls_flags");
            command.add(transcoded ? "delete_segments+independent_segments" : "delete_segments");
        }
        command.add("-method");
        command.add("PUT");
        command.add(outputUrl);
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
        InMemoryHlsService.getInstance().clear();
    }
}
