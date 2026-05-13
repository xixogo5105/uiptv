package com.uiptv.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class YoutubeDL {
    private static final String DEFAULT_YT_DLP_COMMAND = "yt-dlp";
    private static final String DEFAULT_YOUTUBE_DL_COMMAND = "youtube-dl";
    private static String customYtDlpPath = null;
    private static String customYoutubeDlPath = null;

    private YoutubeDL() {
    }

    public static void setYtDlpPath(String path) {
        customYtDlpPath = path;
    }

    public static void setYoutubeDlPath(String path) {
        customYoutubeDlPath = path;
    }

    public static String getStreamingUrl(String videoUrl) {
        String streamUrl = null;

        if (customYtDlpPath != null) {
            streamUrl = tryGetStreamUrl(customYtDlpPath, videoUrl);
        }
        if (streamUrl == null) {
            streamUrl = tryGetStreamUrl(DEFAULT_YT_DLP_COMMAND, videoUrl);
        }
        if (streamUrl != null) {
            return streamUrl;
        }

        if (customYoutubeDlPath != null) {
            streamUrl = tryGetStreamUrl(customYoutubeDlPath, videoUrl);
        }
        if (streamUrl == null) {
            streamUrl = tryGetStreamUrl(DEFAULT_YOUTUBE_DL_COMMAND, videoUrl);
        }
        if (streamUrl != null) {
            return streamUrl;
        }

        AppLog.addWarningLog(YoutubeDL.class, "Neither yt-dlp nor youtube-dl found or failed. Falling back to original URL.");
        return videoUrl;
    }

    private static String tryGetStreamUrl(String command, String videoUrl) {
        if (videoUrl == null || videoUrl.trim().isEmpty()) {
            AppLog.addErrorLog(YoutubeDL.class, "Error: Video URL is null or empty. Cannot execute " + command);
            return null;
        }
        String trimmedVideoUrl = videoUrl.trim();

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command, "-g", "-f", "b", trimmedVideoUrl);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                AppLog.addWarningLog(YoutubeDL.class, command + " process timed out for: " + trimmedVideoUrl);
                return null;
            }

            if (process.exitValue() == 0) {
                String streamUrl = output.toString().trim();
                if (!streamUrl.isEmpty() && streamUrl.startsWith("http")) {
                    return streamUrl;
                }
            } else {
                AppLog.addErrorLog(YoutubeDL.class, command + " failed for: " + trimmedVideoUrl);
                AppLog.addErrorLog(YoutubeDL.class, "Exit code: " + process.exitValue());
                AppLog.addErrorLog(YoutubeDL.class, "Output/Error: " + output);
            }
        } catch (IOException e) {
            AppLog.addErrorLog(YoutubeDL.class, "Error: The command '" + command + "' was not found. Please ensure yt-dlp or youtube-dl is installed and accessible in your system's PATH, or set the executable path using YoutubeDL.setYtDlpPath() or YoutubeDL.setYoutubeDlPath().");
            AppLog.addErrorLog(YoutubeDL.class, "Details: " + e.getMessage());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            AppLog.addWarningLog(YoutubeDL.class, "Execution interrupted while running " + command + " for " + trimmedVideoUrl);
        } catch (RuntimeException e) {
            AppLog.addErrorLog(YoutubeDL.class, "An unexpected error occurred while executing " + command + " for " + trimmedVideoUrl + ": " + e.getMessage());
        }
        return null;
    }
}
