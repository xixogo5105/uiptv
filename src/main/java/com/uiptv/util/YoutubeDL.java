package com.uiptv.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.io.IOException;

public class YoutubeDL {

    private static final String DEFAULT_YT_DLP_COMMAND = "yt-dlp";
    private static final String DEFAULT_YOUTUBE_DL_COMMAND = "youtube-dl";

    // New fields to store custom paths
    private static String customYtDlpPath = null;
    private static String customYoutubeDlPath = null;

    /**
     * Sets a custom path for the yt-dlp executable.
     * If set, this path will be used instead of relying on the system's PATH.
     * @param path The absolute path to the yt-dlp executable.
     */
    public static void setYtDlpPath(String path) {
        customYtDlpPath = path;
    }

    /**
     * Sets a custom path for the youtube-dl executable.
     * If set, this path will be used instead of relying on the system's PATH.
     * @param path The absolute path to the youtube-dl executable.
     */
    public static void setYoutubeDlPath(String path) {
        customYoutubeDlPath = path;
    }

    /**
     * Attempts to get the streaming URL for a given video URL using either yt-dlp or youtube-dl.
     * It tries yt-dlp first, then falls back to youtube-dl. If neither is found or
     * fails, it returns the original video URL.
     *
     * @param videoUrl The original URL of the video (e.g., YouTube watch page URL).
     * @return The direct streaming URL if successfully extracted, otherwise the original video URL.
     */
    public static String getStreamingUrl(String videoUrl) {
        String streamUrl = null;

        // Try yt-dlp first
        if (customYtDlpPath != null) {
            streamUrl = tryGetStreamUrl(customYtDlpPath, videoUrl);
        }
        if (streamUrl == null) { // If custom path failed or not set, try default command
            streamUrl = tryGetStreamUrl(DEFAULT_YT_DLP_COMMAND, videoUrl);
        }
        if (streamUrl != null) {
            return streamUrl;
        }

        // If yt-dlp failed, try youtube-dl
        if (customYoutubeDlPath != null) {
            streamUrl = tryGetStreamUrl(customYoutubeDlPath, videoUrl);
        }
        if (streamUrl == null) { // If custom path failed or not set, try default command
            streamUrl = tryGetStreamUrl(DEFAULT_YOUTUBE_DL_COMMAND, videoUrl);
        }
        if (streamUrl != null) {
            return streamUrl;
        }

        System.out.println("Neither yt-dlp nor youtube-dl found or failed. Falling back to original URL.");
        return videoUrl; // Fallback to original URL if both attempts fail
    }

    private static String tryGetStreamUrl(String command, String videoUrl) {
        // Validate and trim the video URL before attempting to execute the command
        if (videoUrl == null || videoUrl.trim().isEmpty()) {
            System.err.println("Error: Video URL is null or empty. Cannot execute " + command);
            return null;
        }
        String trimmedVideoUrl = videoUrl.trim();

        try {
            // Changed "-f", "best" to "-f", "b" to suppress the yt-dlp warning
            ProcessBuilder processBuilder = new ProcessBuilder(command, "-g", "-f", "b", trimmedVideoUrl);
            processBuilder.redirectErrorStream(true); // Redirect stderr to stdout
            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            if (!process.waitFor(30, TimeUnit.SECONDS)) { // Increased timeout to 30 seconds
                process.destroyForcibly(); // Forcefully terminate if timed out
                System.err.println(command + " process timed out for: " + trimmedVideoUrl);
                return null;
            }

            if (process.exitValue() == 0) {
                String streamUrl = output.toString().trim();
                if (!streamUrl.isEmpty() && streamUrl.startsWith("http")) {
                    return streamUrl;
                }
            } else {
                System.err.println(command + " failed for: " + trimmedVideoUrl);
                System.err.println("Exit code: " + process.exitValue());
                System.err.println("Output/Error: " + output.toString());
            }
        } catch (IOException e) {
            System.err.println("Error: The command '" + command + "' was not found. Please ensure yt-dlp or youtube-dl is installed and accessible in your system's PATH, or set the executable path using YoutubeDL.setYtDlpPath() or YoutubeDL.setYoutubeDlPath().");
            System.err.println("Details: " + e.getMessage());
        } catch (Exception e) {
            // This catch block will handle other runtime exceptions.
            System.err.println("An unexpected error occurred while executing " + command + " for " + trimmedVideoUrl + ": " + e.getMessage());
        }
        return null; // Return null if this command failed
    }
}