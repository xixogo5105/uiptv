package com.uiptv.service;

import java.io.File;
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

    public synchronized void startTransmuxing(String inputUrl) throws IOException {
        stopTransmuxing();
        InMemoryHlsService.getInstance().clear();

        String port = ConfigurationService.getInstance().read().getServerPort();
        if (isBlank(port)) port = "8888";
        
        String outputUrl = "http://127.0.0.1:" + port + "/hls-upload/" + STREAM_FILENAME;

        // Ensure ffmpeg is in PATH
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-i", inputUrl,
                "-c", "copy",
                "-f", "hls",
                "-hls_time", "2",
                "-hls_list_size", "5",
                "-hls_flags", "delete_segments",
                "-method", "PUT", // Use HTTP PUT method
                outputUrl
        );

        pb.redirectErrorStream(true);
        // We can redirect to a log file in temp if needed, or just discard for now to avoid disk I/O
        // For debugging, logging to a file is still useful, but let's minimize disk usage as requested.
        // We'll discard stdout/stderr to avoid buffer filling.
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);

        currentProcess = pb.start();
        
        // Wait for the playlist to appear in memory
        int attempts = 0;
        while (!InMemoryHlsService.getInstance().exists(STREAM_FILENAME) && attempts < 50) { // Wait up to 5 seconds
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            attempts++;
        }
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
