package com.uiptv.player;

import com.uiptv.ui.LogDisplayUI;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class YoutubeDL {

    private static final String DEFAULT_YT_DLP_COMMAND = "yt-dlp";
    private static final String DEFAULT_YOUTUBE_DL_COMMAND = "youtube-dl";
    private static final String ENV_YT_DLP_PATH = "YT_DLP_PATH";
    private static final String ENV_YOUTUBE_DL_PATH = "YOUTUBE_DL_PATH";
    private static final AtomicBoolean missingBinariesWarningLogged = new AtomicBoolean(false);
    private static volatile String lastWorkingCommand = null;

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
        List<String> commands = buildCommandCandidates();

        boolean anyCommandFound = false;
        for (String command : commands) {
            StreamResult result = tryGetStreamUrl(command, videoUrl);
            if (result.commandFound) {
                anyCommandFound = true;
            }
            if (result.streamUrl != null) {
                lastWorkingCommand = command;
                return result.streamUrl;
            }
        }

        if (!anyCommandFound && missingBinariesWarningLogged.compareAndSet(false, true)) {
            com.uiptv.util.AppLog.addLog(
                    "Neither yt-dlp nor youtube-dl was found in PATH or common locations. " +
                    "Install yt-dlp or set a custom path with YoutubeDL.setYtDlpPath()."
            );
        }
        com.uiptv.util.AppLog.addLog("Neither yt-dlp nor youtube-dl found or failed. Falling back to original URL.");
        return videoUrl; // Fallback to original URL if both attempts fail
    }

    private static List<String> buildCommandCandidates() {
        Set<String> candidates = new LinkedHashSet<>();
        if (lastWorkingCommand != null && !lastWorkingCommand.trim().isEmpty()) {
            candidates.add(lastWorkingCommand.trim());
        }

        addIfSet(candidates, customYtDlpPath);
        addIfSet(candidates, System.getenv(ENV_YT_DLP_PATH));
        candidates.add(DEFAULT_YT_DLP_COMMAND);
        addAll(candidates, commonYtDlpPaths());

        addIfSet(candidates, customYoutubeDlPath);
        addIfSet(candidates, System.getenv(ENV_YOUTUBE_DL_PATH));
        candidates.add(DEFAULT_YOUTUBE_DL_COMMAND);
        addAll(candidates, commonYoutubeDlPaths());

        return new ArrayList<>(candidates);
    }

    private static void addIfSet(Set<String> candidates, String value) {
        if (value != null && !value.trim().isEmpty()) {
            candidates.add(value.trim());
        }
    }

    private static void addAll(Set<String> candidates, List<String> values) {
        for (String value : values) {
            addIfSet(candidates, value);
        }
    }

    private static List<String> commonYtDlpPaths() {
        List<String> paths = new ArrayList<>();
        paths.add("/opt/homebrew/bin/yt-dlp");
        paths.add("/usr/local/bin/yt-dlp");
        paths.add("/usr/bin/yt-dlp");
        paths.add("/snap/bin/yt-dlp");
        paths.add("C:\\\\yt-dlp\\\\yt-dlp.exe");
        return paths;
    }

    private static List<String> commonYoutubeDlPaths() {
        List<String> paths = new ArrayList<>();
        paths.add("/opt/homebrew/bin/youtube-dl");
        paths.add("/usr/local/bin/youtube-dl");
        paths.add("/usr/bin/youtube-dl");
        paths.add("/snap/bin/youtube-dl");
        paths.add("C:\\\\youtube-dl\\\\youtube-dl.exe");
        return paths;
    }

    private static StreamResult tryGetStreamUrl(String command, String videoUrl) {
        // Validate and trim the video URL before attempting to execute the command
        if (videoUrl == null || videoUrl.trim().isEmpty()) {
            com.uiptv.util.AppLog.addLog("Error: Video URL is null or empty. Cannot execute " + command);
            return StreamResult.failed(false);
        }
        String trimmedVideoUrl = videoUrl.trim();

        try {
            if (looksLikeAbsolutePath(command) && !new File(command).canExecute()) {
                return StreamResult.failed(false);
            }

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
                com.uiptv.util.AppLog.addLog(command + " process timed out for: " + trimmedVideoUrl);
                return StreamResult.failed(true);
            }

            if (process.exitValue() == 0) {
                String streamUrl = output.toString().trim();
                if (!streamUrl.isEmpty() && streamUrl.startsWith("http")) {
                    return StreamResult.success(streamUrl);
                }
            } else {
                com.uiptv.util.AppLog.addLog(command + " failed for: " + trimmedVideoUrl);
                com.uiptv.util.AppLog.addLog("Exit code: " + process.exitValue());
                com.uiptv.util.AppLog.addLog("Output/Error: " + output);
            }
        } catch (IOException e) {
            String details = e.getMessage() == null ? "" : e.getMessage();
            boolean commandNotFound = details.contains("No such file or directory") || details.contains("error=2");
            if (!commandNotFound) {
                com.uiptv.util.AppLog.addLog("Failed to execute '" + command + "': " + details);
            }
            return StreamResult.failed(!commandNotFound);
        } catch (Exception e) {
            // This catch block will handle other runtime exceptions.
            com.uiptv.util.AppLog.addLog("An unexpected error occurred while executing " + command + " for " + trimmedVideoUrl + ": " + e.getMessage());
            return StreamResult.failed(true);
        }
        return StreamResult.failed(true); // Return null if this command failed
    }

    private static boolean looksLikeAbsolutePath(String command) {
        if (command == null) {
            return false;
        }
        return command.startsWith("/") || command.matches("^[A-Za-z]:\\\\\\\\.*");
    }

    private static final class StreamResult {
        private final String streamUrl;
        private final boolean commandFound;

        private StreamResult(String streamUrl, boolean commandFound) {
            this.streamUrl = streamUrl;
            this.commandFound = commandFound;
        }

        private static StreamResult success(String streamUrl) {
            return new StreamResult(streamUrl, true);
        }

        private static StreamResult failed(boolean commandFound) {
            return new StreamResult(null, commandFound);
        }
    }
}
