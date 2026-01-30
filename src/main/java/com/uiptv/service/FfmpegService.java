package com.uiptv.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class FfmpegService {
    private static FfmpegService instance;
    private Process currentProcess;
    private final String outputDir;
    private static final String STREAM_FILENAME = "stream.m3u8";

    private FfmpegService() {
        String tempPath = System.getProperty("java.io.tmpdir");
        outputDir = tempPath + File.separator + "uiptv_hls";
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public static synchronized FfmpegService getInstance() {
        if (instance == null) {
            instance = new FfmpegService();
        }
        return instance;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public boolean isTransmuxingNeeded(String url) {
        // Simple check: if it contains .ts and not .m3u8, it likely needs transmuxing for web playback
        return url != null && url.contains("extension=ts");
    }

    public synchronized void startTransmuxing(String inputUrl) throws IOException {
        stopTransmuxing();
        cleanOutputDir();

        String outputPath = outputDir + File.separator + STREAM_FILENAME;

        // Ensure ffmpeg is in PATH
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-i", inputUrl,
                "-c", "copy",
                "-f", "hls",
                "-hls_time", "2",
                "-hls_list_size", "5",
                "-hls_flags", "delete_segments",
                outputPath
        );

        pb.redirectErrorStream(true);
        File logFile = new File(outputDir, "ffmpeg.log");
        pb.redirectOutput(logFile);

        currentProcess = pb.start();
        
        // Wait for the file to appear
        File m3u8File = new File(outputPath);
        int attempts = 0;
        while (!m3u8File.exists() && attempts < 50) { // Wait up to 5 seconds
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
    }

    private void cleanOutputDir() {
        try {
            if (Files.exists(Paths.get(outputDir))) {
                Files.walk(Paths.get(outputDir))
                        .filter(Files::isRegularFile)
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
