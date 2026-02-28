package com.uiptv.service;

import com.uiptv.util.ServerUrlUtil;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.uiptv.util.StringUtils.isBlank;

public class FfmpegService {
    private static FfmpegService instance;
    private Process currentProcess;
    private static final String STREAM_FILENAME = "stream.m3u8";

    private FfmpegService() {
    }

    public static synchronized FfmpegService getInstance() {
        if (instance == null) {
            instance = new FfmpegService();
        }
        return instance;
    }

    public boolean isTransmuxingNeeded(String url) {
        return url != null && url.contains("extension=ts");
    }

    public synchronized boolean startTransmuxing(String inputUrl) throws IOException {
        return startTransmuxing(inputUrl, false);
    }

    public synchronized boolean startTransmuxing(String inputUrl, boolean vodStylePlaylist) throws IOException {
        stopTransmuxing();
        InMemoryHlsService.getInstance().clear();

        String outputUrl = ServerUrlUtil.getLocalServerUrl() + "/hls-upload/" + STREAM_FILENAME;

        // Ensure ffmpeg is in PATH
        ProcessBuilder pb;
        if (vodStylePlaylist) {
            pb = new ProcessBuilder(
                    "ffmpeg",
                    "-fflags", "+genpts",
                    "-i", inputUrl,
                    "-c", "copy",
                    "-f", "hls",
                    "-hls_time", "4",
                    "-hls_list_size", "0",
                    "-hls_playlist_type", "vod",
                    "-hls_flags", "independent_segments",
                    "-method", "PUT",
                    outputUrl
            );
        } else {
            pb = new ProcessBuilder(
                    "ffmpeg",
                    "-i", inputUrl,
                    "-c", "copy",
                    "-f", "hls",
                    "-hls_time", "2",
                    "-hls_list_size", "20",
                    "-method", "PUT",
                    outputUrl
            );
        }

        pb.redirectErrorStream(true);
        // We can redirect to a log file in temp if needed, or just discard for now to avoid disk I/O
        // For debugging, logging to a file is still useful, but let's minimize disk usage as requested.
        // We'll discard stdout/stderr to avoid buffer filling.
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);

        currentProcess = pb.start();
        
        // Wait for the playlist to appear in memory.
        // VOD/series startup can be slow on some portals, so allow a longer warm-up.
        int attempts = 0;
        final int maxAttempts = 150; // up to ~15 seconds
        while (!InMemoryHlsService.getInstance().exists(STREAM_FILENAME) && attempts < maxAttempts) {
            if (currentProcess == null || !currentProcess.isAlive()) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            attempts++;
        }

        return InMemoryHlsService.getInstance().exists(STREAM_FILENAME);
    }

    public synchronized void stopTransmuxing() {
        if (currentProcess != null) {
            if (currentProcess.isAlive()) {
                currentProcess.destroy();
                try {
                    if (!currentProcess.waitFor(1, TimeUnit.SECONDS)) {
                        currentProcess.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    currentProcess.destroyForcibly();
                    Thread.currentThread().interrupt();
                }
            }
            currentProcess = null;
        }
        InMemoryHlsService.getInstance().clear();
    }
}
