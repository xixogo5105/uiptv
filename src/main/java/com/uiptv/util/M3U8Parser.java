package com.uiptv.util;

import com.uiptv.shared.PlaylistEntry;
import com.uiptv.widget.UIptvAlert;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.uiptv.util.StringUtils.EMPTY;
import static com.uiptv.util.StringUtils.isNotBlank;

public class M3U8Parser {
    private static final String EXTINF = "#EXTINF";
    private static final String EXT_X_KEY = "#EXT-X-KEY";
    private static final String KODIPROP_INPUTSTREAM_ADDON = "#KODIPROP:inputstreamaddon=";
    private static final String KODIPROP_MANIFEST_TYPE = "#KODIPROP:inputstream.adaptive.manifest_type=";
    private static final String KODIPROP_LICENSE_TYPE = "#KODIPROP:inputstream.adaptive.license_type=";
    private static final String KODIPROP_LICENSE_KEY = "#KODIPROP:inputstream.adaptive.license_key=";
    private static final String COMMENT_PREFIX = "#";

    public static Set<PlaylistEntry> parseUrlCategory(URL m3u8Url) {
        try {
            if (m3u8Url.getProtocol().startsWith("https")) {
                HttpsURLConnection connection = (HttpsURLConnection) m3u8Url.openConnection();
                connection.setConnectTimeout(10000);
                return parseCategory(new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)));
            } else if (m3u8Url.getProtocol().startsWith("http")) {
                HttpURLConnection connection = (HttpURLConnection) m3u8Url.openConnection();
                connection.setConnectTimeout(10000);
                return parseCategory(new BufferedReader(new InputStreamReader(connection.getInputStream())));
            }
            return parseCategory(new BufferedReader(new InputStreamReader(m3u8Url.openStream())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Set<PlaylistEntry> parsePathCategory(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath, StandardCharsets.UTF_8))) {
            return parseCategory(reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<PlaylistEntry> parseChannelUrlM3U8(URL m3u8Url) {
        try {
            if (m3u8Url.getProtocol().startsWith("https")) {
                HttpsURLConnection connection = (HttpsURLConnection) m3u8Url.openConnection();
                connection.setConnectTimeout(10000);
                return parseM3U8(new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)));
            }
            if (m3u8Url.getProtocol().startsWith("http")) {
                HttpURLConnection connection = (HttpURLConnection) m3u8Url.openConnection();
                connection.setConnectTimeout(10000);
                return parseM3U8(new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)));
            }
            return parseM3U8(new BufferedReader(new InputStreamReader(m3u8Url.openStream())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<PlaylistEntry> parseChannelPathM3U8(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath, StandardCharsets.UTF_8))) {
            return parseM3U8(reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Set<PlaylistEntry> parseCategory(BufferedReader reader) {
        Set<PlaylistEntry> playlistEntries = new LinkedHashSet<>();
        playlistEntries.add(new PlaylistEntry("All", "All", null, null, null));
        boolean hasUncategorizedEntries = false;
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    if (line.startsWith(EXTINF)) {
                        String groupTitle = parseItem(line, "group-title=\"");
                        if (isNotBlank(groupTitle) && !groupTitle.equalsIgnoreCase("All")) {
                            playlistEntries.add(new PlaylistEntry(
                                    parseItem(line, "tvg-id=\""),
                                    groupTitle,
                                    null,
                                    null,
                                    null));
                            if (groupTitle.equalsIgnoreCase("Uncategorized")) {
                                hasUncategorizedEntries = true;
                            }
                        } else {
                            // Missing group-title should be represented by Uncategorized if present in the source.
                            hasUncategorizedEntries = true;
                        }
                        reader.readLine();
                    }
                } catch (Exception e) {
                    UIptvAlert.showError(e.getMessage());
                }
            }
        } catch (Exception e) {
            UIptvAlert.showError(e.getMessage());
        }
        if (hasUncategorizedEntries
                && playlistEntries.stream().noneMatch(entry -> entry.getGroupTitle().equalsIgnoreCase("Uncategorized"))) {
            playlistEntries.add(new PlaylistEntry("Uncategorized", "Uncategorized", null, null, null));
        }
        return playlistEntries;
    }

    private static List<PlaylistEntry> parseM3U8(BufferedReader reader) {
        List<PlaylistEntry> playlistEntries = new ArrayList<>();
        try {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }

            for (int index = 0; index < lines.size(); index++) {
                line = lines.get(index);
                if (!line.startsWith(EXTINF)) {
                    continue;
                }

                String tvgId = parseItem(line, "tvg-id=\"");
                String groupTitle = parseItem(line, "group-title=\"");
                String title = parseTitle(line);
                String logo = parseItem(line, "tvg-logo=\"");

                String drmType = null;
                String drmLicenseUrl = null;
                Map<String, String> clearKeys = null;
                String inputstreamaddon = null;
                String manifestType = null;
                String url = null;
                for (index = index + 1; index < lines.size(); index++) {
                    String nextLine = lines.get(index);
                    String trimmed = nextLine == null ? EMPTY : nextLine.trim();
                    if (trimmed.startsWith(EXTINF)) {
                        index--;
                        break;
                    }
                    if (nextLine.startsWith(EXT_X_KEY)) {
                        drmType = parseDrmType(nextLine);
                        drmLicenseUrl = parseItem(nextLine, "URI=\"");
                    } else if (nextLine.startsWith(KODIPROP_INPUTSTREAM_ADDON)) {
                        inputstreamaddon = nextLine.substring(KODIPROP_INPUTSTREAM_ADDON.length()).trim();
                    } else if (nextLine.startsWith(KODIPROP_MANIFEST_TYPE)) {
                        manifestType = nextLine.substring(KODIPROP_MANIFEST_TYPE.length()).trim();
                    } else if (nextLine.startsWith(KODIPROP_LICENSE_TYPE)) {
                        String type = nextLine.substring(KODIPROP_LICENSE_TYPE.length()).trim();
                        if ("com.widevine.alpha".equalsIgnoreCase(type)) {
                            drmType = "com.widevine.alpha";
                        } else if ("clearkey".equalsIgnoreCase(type)
                                || "org.w3.clearkey".equalsIgnoreCase(type)
                                || "com.clearkey.alpha".equalsIgnoreCase(type)) {
                            drmType = "org.w3.clearkey";
                        }
                    } else if (nextLine.startsWith(KODIPROP_LICENSE_KEY)) {
                        String key = nextLine.substring(KODIPROP_LICENSE_KEY.length()).trim();
                        if ("org.w3.clearkey".equalsIgnoreCase(drmType)) {
                            if (clearKeys == null) clearKeys = new HashMap<>();
                            clearKeys.putAll(parseClearKeys(key));
                        } else {
                            drmLicenseUrl = key;
                        }
                    } else if (isNotBlank(trimmed) && !trimmed.startsWith(COMMENT_PREFIX)) {
                        String normalizedCandidate = normalizePotentialUrl(trimmed);
                        if (isLikelyStreamUrl(normalizedCandidate)) {
                            url = normalizedCandidate;
                            break;
                        }
                    }
                }

                if (isNotBlank(url)) {
                    playlistEntries.add(new PlaylistEntry(tvgId, groupTitle, title, url, logo, drmType, drmLicenseUrl, clearKeys, inputstreamaddon, manifestType));
                }
            }
        } catch (IOException e) {
            UIptvAlert.showError(e.getMessage());
        }
        return playlistEntries;
    }

    private static String parseItem(String line, String key) {
        String[] firstItem = line.split(key);
        if (firstItem.length > 1) {
            return firstItem[1].split("\"")[0];
        }
        return EMPTY;
    }

    private static String parseTitle(String line) {
        int lastCommaIndex = line.lastIndexOf(",");
        if (lastCommaIndex != -1 && lastCommaIndex < line.length() - 1) {
            return line.substring(lastCommaIndex + 1).trim();
        }
        return EMPTY;
    }

    private static String parseDrmType(String line) {
        Pattern pattern = Pattern.compile("KEYFORMAT=\"(.*?)\"");
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            String keyFormat = matcher.group(1);
            if ("com.widevine.alpha".equalsIgnoreCase(keyFormat)) {
                return "com.widevine.alpha";
            }
        }
        return null;
    }

    private static Map<String, String> parseClearKeys(String keyString) {
        Map<String, String> keys = new HashMap<>();
        for (String pair : keyString.split(";")) {
            String[] parts = pair.split(":");
            if (parts.length == 2) {
                keys.put(parts[0], parts[1]);
            }
        }
        return keys;
    }

    private static String normalizePotentialUrl(String value) {
        if (!isNotBlank(value)) {
            return value;
        }
        String normalized = value.trim();
        normalized = normalized.replace("\\/", "/");
        normalized = normalized.replaceAll("(?i)\\\\u002f", "/");
        normalized = normalized.replaceAll("(?i)\\\\u003a", ":");
        normalized = normalized.replaceAll("(?i)\\\\u003f", "?");
        normalized = normalized.replaceAll("(?i)\\\\u003d", "=");
        normalized = normalized.replaceAll("(?i)\\\\u0026", "&");
        return normalized;
    }

    private static boolean isLikelyStreamUrl(String value) {
        if (!isNotBlank(value)) {
            return false;
        }
        String candidate = value.trim();
        if (candidate.startsWith(COMMENT_PREFIX)) {
            return false;
        }
        if (candidate.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            return true;
        }
        if (candidate.startsWith("//")
                || candidate.startsWith("/")
                || candidate.startsWith("./")
                || candidate.startsWith("../")
                || candidate.matches("^[a-zA-Z]:\\\\\\\\.*")) {
            return true;
        }
        if (candidate.contains("/")) {
            return true;
        }
        return candidate.matches("(?i)^.+\\.(m3u8|mpd|ts|aac|mp3|mp4|m4s)(\\?.*)?$");
    }
}
