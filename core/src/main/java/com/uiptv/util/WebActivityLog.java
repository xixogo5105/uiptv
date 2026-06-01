package com.uiptv.util;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public final class WebActivityLog {
    public static final String ACTIVITY_DESCRIPTION_ATTRIBUTE = "uiptv.webActivity.description";
    private static final int MAX_LINES = Integer.getInteger("uiptv.webActivity.maxLines", 2000);
    private static final int MAX_VALUE_LENGTH = 160;
    private static final String WEB_PLAYER_SUFFIX = " in the web player";
    private static final String SOURCE_PREFIX = " from ";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Object LOCK = new Object();
    private static final Deque<String> entries = new ArrayDeque<>();
    private static final CopyOnWriteArrayList<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private static final Path LOG_FILE = buildLogFilePath();

    private WebActivityLog() {
    }

    public static void recordRequest(String method,
                                     String path,
                                     String rawQuery,
                                     String requestIp,
                                     int statusCode,
                                     long durationMillis) {
        recordRequest(method, path, rawQuery, requestIp, statusCode, durationMillis, "");
    }

    public static void recordRequest(String method,
                                     String path,
                                     String rawQuery,
                                     String requestIp,
                                     int statusCode,
                                     long durationMillis,
                                     String activityDescription) {
        String action = isNotBlank(activityDescription) ? AppLog.sanitizeValue(activityDescription) : describeRequest(method, path, rawQuery);
        if (isBlank(action)) {
            return;
        }

        String entry = TIMESTAMP_FORMATTER.format(LocalDateTime.now())
                + " | IP " + safeValue(isBlank(requestIp) ? "unknown" : requestIp)
                + " | " + action
                + " | Result: " + describeResult(statusCode)
                + " | " + Math.max(0L, durationMillis) + " ms";
        appendEntry(entry);
        notifyListeners(entry);
        if (AppLog.isTerminalLoggingEnabled()) {
            AppLog.addInfoLog(WebActivityLog.class, "Web activity: " + entry);
        }
    }

    public static String describeRequest(String method, String path, String rawQuery) {
        String normalizedPath = normalizePath(path);
        if (shouldIgnore(normalizedPath)) {
            return "";
        }
        Map<String, String> params = queryToMap(rawQuery);
        String requestMethod = isBlank(method) ? "GET" : method.trim().toUpperCase(Locale.ROOT);
        String action = describePath(normalizedPath, params);
        if (!"GET".equals(requestMethod) && !action.startsWith(requestMethod + " request")) {
            return requestMethod + " request - " + action;
        }
        return action;
    }

    public static String describeBingeWatchPlaylist(String episodeName, String season, String episodeNumber, int episodeCount) {
        String target = episodeTarget(episodeName, season, episodeNumber);
        String count = episodeCount > 0 ? " containing " + episodeCount + " " + (episodeCount == 1 ? "episode" : "episodes") : "";
        return "Downloaded a binge-watch playlist" + (isNotBlank(target) ? " starting with " + target : "") + count;
    }

    public static String describeBingeWatchEpisode(String episodeName, String season, String episodeNumber) {
        String target = episodeTarget(episodeName, season, episodeNumber);
        return "Played binge-watch episode" + (isNotBlank(target) ? " " + target : "");
    }

    public static String describePublishedM3uEntry(String title, String accountName, String categoryName) {
        StringBuilder description = new StringBuilder("Played published M3U entry");
        if (isNotBlank(title)) {
            description.append(" \"").append(safeValue(title)).append("\"");
        }
        if (isNotBlank(accountName)) {
            description.append(" from account \"").append(safeValue(accountName)).append("\"");
        }
        if (isNotBlank(categoryName)) {
            description.append(" in category \"").append(safeValue(categoryName)).append("\"");
        }
        return description.toString();
    }

    public static String readAllText() {
        synchronized (LOCK) {
            if (!entries.isEmpty()) {
                return String.join(System.lineSeparator(), entries) + System.lineSeparator();
            }
        }
        try {
            return Files.exists(LOG_FILE) ? Files.readString(LOG_FILE) : "";
        } catch (IOException e) {
            AppLog.addWarningLog(WebActivityLog.class, "Unable to read temporary web activity log: " + e.getMessage());
            return "";
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            entries.clear();
            try {
                Files.deleteIfExists(LOG_FILE);
            } catch (IOException e) {
                AppLog.addWarningLog(WebActivityLog.class, "Unable to clear temporary web activity log: " + e.getMessage());
            }
        }
    }

    public static Path getLogFilePath() {
        return LOG_FILE;
    }

    public static void registerListener(Consumer<String> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public static void unregisterListener(Consumer<String> listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private static String describePath(String path, Map<String, String> params) {
        String staticPage = describeStaticPage(path);
        if (isNotBlank(staticPage)) {
            return staticPage;
        }
        if (path.startsWith("/player")) {
            return describePlayerRequest(path, params);
        }
        String playlist = describePlaylistRequest(path, params);
        if (isNotBlank(playlist)) {
            return playlist;
        }
        String streaming = describeStreamingRequest(path, params);
        if (isNotBlank(streaming)) {
            return streaming;
        }
        String remoteSync = describeRemoteSyncRequest(path);
        if (isNotBlank(remoteSync)) {
            return remoteSync;
        }
        String appData = describeAppDataRequest(path);
        if (isNotBlank(appData)) {
            return appData;
        }
        return "Opened " + path;
    }

    private static String describeStaticPage(String path) {
        return switch (path) {
            case "/", "/index.html" -> "Opened the UIPTV web app";
            case "/drm.html" -> "Opened the UIPTV web app";
            default -> "";
        };
    }

    private static String describePlaylistRequest(String path, Map<String, String> params) {
        return switch (path) {
            case "/playlist.m3u8" -> "Downloaded a playlist file: playlist.m3u8" + describePlaylistScope(params);
            case "/bookmarks.m3u8" -> "Downloaded the bookmarks M3U playlist";
            case "/iptv.m3u8", "/iptv.m3u" -> "Accessed the published M3U playlist: " + fileName(path);
            case "/bookmarkEntry.ts" -> "Played a local M3U playlist entry";
            case "/bingewatch.m3u8" -> "Downloaded a binge-watch playlist";
            default -> path.startsWith("/bingwatch") ? "Opened a binge-watch episode stream" : "";
        };
    }

    private static String describeStreamingRequest(String path, Map<String, String> params) {
        if (path.startsWith("/proxy-stream")) {
            return "Streamed media through the web player" + sourceSummary(params.get("src"));
        }
        return "";
    }

    private static String describeRemoteSyncRequest(String path) {
        if (path.startsWith("/remote-sync/download")) {
            return "Downloaded a remote sync database snapshot";
        }
        if (path.startsWith("/remote-sync/upload")) {
            return "Uploaded a remote sync database snapshot";
        }
        if (path.startsWith("/remote-sync/request")) {
            return "Requested remote sync access";
        }
        if (path.startsWith("/remote-sync/status")) {
            return "Checked remote sync status";
        }
        if (path.startsWith("/remote-sync/complete")) {
            return "Completed a remote sync session";
        }
        return "";
    }

    private static String describeAppDataRequest(String path) {
        if (path.startsWith("/accounts")) {
            return "Loaded accounts in the web app";
        }
        if (path.startsWith("/categories")) {
            return "Loaded categories in the web app";
        }
        if (path.startsWith("/channels")) {
            return "Loaded channels in the web app";
        }
        if (path.startsWith("/seriesEpisodes")) {
            return "Loaded series episodes in the web app";
        }
        if (path.startsWith("/seriesDetails")) {
            return "Loaded series details in the web app";
        }
        if (path.startsWith("/vodDetails")) {
            return "Loaded movie details in the web app";
        }
        if (path.startsWith("/watchingNow")) {
            return "Updated the watching-now list in the web app";
        }
        if (path.startsWith("/bookmarks")) {
            return "Used bookmarks in the web app";
        }
        if (path.startsWith("/config")) {
            return "Loaded web app configuration";
        }
        return "";
    }

    private static String describePlayerRequest(String path, Map<String, String> params) {
        String name = safeValue(params.get("name"));
        String target = isNotBlank(name) ? " \"" + name + "\"" : "";
        String mode = firstNonBlank(params.get("mode"), modeFromPlayerPath(path));
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "itv", "live" -> "Played live channel" + target + WEB_PLAYER_SUFFIX;
            case "vod" -> "Played movie or video" + target + WEB_PLAYER_SUFFIX;
            case "series" -> "Played series episode" + target + WEB_PLAYER_SUFFIX;
            default -> isNotBlank(params.get("bookmarkId"))
                    ? "Played a bookmarked item" + WEB_PLAYER_SUFFIX
                    : "Started web playback" + target;
        };
    }

    private static String modeFromPlayerPath(String path) {
        if (path.endsWith("/live")) {
            return "live";
        }
        if (path.endsWith("/vod")) {
            return "vod";
        }
        if (path.endsWith("/series") || path.endsWith("/bingewatch")) {
            return "series";
        }
        return "";
    }

    private static String describePlaylistScope(Map<String, String> params) {
        if (isNotBlank(params.get("channelId"))) {
            return " for one channel";
        }
        if (isNotBlank(params.get("categoryId"))) {
            return " for one category";
        }
        if (isNotBlank(params.get("accountId"))) {
            return " for one account";
        }
        return "";
    }

    private static String sourceSummary(String source) {
        if (isBlank(source)) {
            return "";
        }
        try {
            URI uri = URI.create(source);
            String host = uri.getHost();
            String sourceFile = fileName(uri.getPath());
            if (isNotBlank(host) && isNotBlank(sourceFile)) {
                return SOURCE_PREFIX + safeValue(host + "/" + sourceFile);
            }
            if (isNotBlank(host)) {
                return SOURCE_PREFIX + safeValue(host);
            }
        } catch (Exception _) {
            // Fall back to a safe, short source label below.
        }
        return SOURCE_PREFIX + safeValue(fileName(source));
    }

    private static String episodeTarget(String episodeName, String season, String episodeNumber) {
        StringBuilder details = new StringBuilder();
        if (isNotBlank(season)) {
            details.append("Season ").append(safeValue(season));
        }
        if (isNotBlank(episodeNumber)) {
            if (!details.isEmpty()) {
                details.append(", ");
            }
            details.append("Episode ").append(safeValue(episodeNumber));
        }
        if (isNotBlank(episodeName)) {
            String target = "\"" + safeValue(episodeName) + "\"";
            return details.isEmpty() ? target : target + " (" + details + ")";
        }
        return details.toString();
    }

    private static String describeResult(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return "completed";
        }
        if (statusCode >= 300 && statusCode < 400) {
            return "redirected";
        }
        if (statusCode == 404) {
            return "not found";
        }
        if (statusCode >= 400 && statusCode < 500) {
            return "rejected (" + statusCode + ")";
        }
        if (statusCode >= 500) {
            return "failed (" + statusCode + ")";
        }
        return "unknown";
    }

    private static void appendEntry(String entry) {
        synchronized (LOCK) {
            entries.addLast(entry);
            boolean trimmed = false;
            while (entries.size() > MAX_LINES) {
                entries.removeFirst();
                trimmed = true;
            }
            try {
                Files.createDirectories(LOG_FILE.getParent());
                if (trimmed) {
                    persistAllEntries();
                } else {
                    Files.writeString(LOG_FILE, entry + System.lineSeparator(), StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
            } catch (IOException e) {
                AppLog.addWarningLog(WebActivityLog.class, "Unable to write temporary web activity log: " + e.getMessage());
            }
        }
    }

    private static void persistAllEntries() throws IOException {
        String text = entries.isEmpty() ? "" : String.join(System.lineSeparator(), entries) + System.lineSeparator();
        Files.writeString(LOG_FILE, text, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void notifyListeners(String entry) {
        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(entry);
            } catch (Exception e) {
                AppLog.addWarningLog(WebActivityLog.class, "Web activity listener failed: " + e.getMessage());
            }
        }
    }

    private static Path buildLogFilePath() {
        long pid = -1L;
        try {
            pid = ProcessHandle.current().pid();
        } catch (Exception _) {
            // Process id is only used to avoid cross-session collisions.
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "uiptv", "web-activity-" + pid + ".log");
    }

    private static boolean shouldIgnore(String path) {
        return path.startsWith("/css")
                || path.startsWith("/javascript")
                || path.startsWith("/js")
                || "/icon.ico".equals(path)
                || "/manifest.json".equals(path)
                || "/sw.js".equals(path);
    }

    private static String normalizePath(String path) {
        if (isBlank(path)) {
            return "/";
        }
        String normalized = path.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private static String fileName(String path) {
        if (isBlank(path)) {
            return "";
        }
        String clean = path;
        int queryIndex = clean.indexOf('?');
        if (queryIndex >= 0) {
            clean = clean.substring(0, queryIndex);
        }
        int slashIndex = clean.lastIndexOf('/');
        String value = slashIndex >= 0 ? clean.substring(slashIndex + 1) : clean;
        return safeValue(decode(value));
    }

    private static Map<String, String> queryToMap(String rawQuery) {
        if (isBlank(rawQuery)) {
            return Collections.emptyMap();
        }
        Map<String, String> params = new HashMap<>();
        for (String param : rawQuery.split("&")) {
            if (isBlank(param)) {
                continue;
            }
            String[] pair = param.split("=", 2);
            String key = decode(pair[0]);
            String value = pair.length > 1 ? decode(pair[1]) : "";
            params.put(key, value);
        }
        return params;
    }

    private static String firstNonBlank(String first, String second) {
        if (isNotBlank(first)) {
            return first;
        }
        return isBlank(second) ? "" : second;
    }

    private static String safeValue(String value) {
        String sanitized = AppLog.sanitizeValue(value);
        if (sanitized.length() <= MAX_VALUE_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_VALUE_LENGTH) + "...";
    }

    private static String decode(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException _) {
            return value;
        }
    }
}
