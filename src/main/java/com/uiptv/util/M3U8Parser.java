package com.uiptv.util;

import com.uiptv.shared.PlaylistEntry;
import com.uiptv.widget.UIptvAlert;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
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
    private static final String UNCATEGORIZED = "Uncategorized";
    private static final String DRM_TYPE_WIDEVINE = "com.widevine.alpha";
    private static final String DRM_TYPE_CLEARKEY = "org.w3.clearkey";

    private M3U8Parser() {
    }

    public static Set<PlaylistEntry> parseUrlCategory(URL m3u8Url) {
        try {
            String protocol = m3u8Url.getProtocol();
            if (protocol != null && protocol.toLowerCase().startsWith("http")) {
                HttpUtil.HttpResult response = HttpUtil.sendRequest(m3u8Url.toString(), null, "GET");
                return parseCategory(new BufferedReader(new StringReader(response.body())));
            }
            return parseCategory(new BufferedReader(new StringReader(readNonHttpUrl(m3u8Url))));
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
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
            String protocol = m3u8Url.getProtocol();
            if (protocol != null && protocol.toLowerCase().startsWith("http")) {
                HttpUtil.HttpResult response = HttpUtil.sendRequest(m3u8Url.toString(), null, "GET");
                return parseM3U8(new BufferedReader(new StringReader(response.body())));
            }
            return parseM3U8(new BufferedReader(new StringReader(readNonHttpUrl(m3u8Url))));
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
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
                    if (!line.startsWith(EXTINF)) {
                        continue;
                    }
                    String groupTitle = parseItem(line, "group-title=\"");
                    hasUncategorizedEntries |= shouldTreatAsUncategorized(groupTitle);
                    PlaylistEntry categoryEntry = buildCategoryEntry(line, groupTitle);
                    if (categoryEntry != null) {
                        playlistEntries.add(categoryEntry);
                    }
                    if (reader.readLine() == null) {
                        break;
                    }
                } catch (Exception e) {
                    UIptvAlert.showError(e.getMessage());
                }
            }
        } catch (Exception e) {
            UIptvAlert.showError(e.getMessage());
        }
        if (hasUncategorizedEntries
                && playlistEntries.stream().noneMatch(entry -> entry.getGroupTitle().equalsIgnoreCase(UNCATEGORIZED))) {
            playlistEntries.add(new PlaylistEntry(UNCATEGORIZED, UNCATEGORIZED, null, null, null));
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

                EntryState state = new EntryState();
                for (index = index + 1; index < lines.size(); index++) {
                    String nextLine = lines.get(index);
                    String trimmed = nextLine == null ? EMPTY : nextLine.trim();
                    if (trimmed.startsWith(EXTINF)) {
                        index--;
                        break;
                    }
                    if (applyMetaLine(state, nextLine, trimmed)) {
                        if (state.url != null) {
                            break;
                        }
                    }
                }

                if (isNotBlank(state.url)) {
                    playlistEntries.add(new PlaylistEntry(tvgId, groupTitle, title, state.url, logo, state.drmType, state.drmLicenseUrl, state.clearKeys, state.inputstreamaddon, state.manifestType));
                }
            }
        } catch (IOException e) {
            UIptvAlert.showError(e.getMessage());
        }
        return playlistEntries;
    }

    private static boolean shouldTreatAsUncategorized(String groupTitle) {
        return !isNotBlank(groupTitle) || groupTitle.equalsIgnoreCase(UNCATEGORIZED);
    }

    private static PlaylistEntry buildCategoryEntry(String line, String groupTitle) {
        if (!isNotBlank(groupTitle) || groupTitle.equalsIgnoreCase("All")) {
            return null;
        }
        return new PlaylistEntry(parseItem(line, "tvg-id=\""), groupTitle, null, null, null);
    }

    private static boolean applyMetaLine(EntryState state, String nextLine, String trimmed) {
        if (nextLine.startsWith(EXT_X_KEY)) {
            state.drmType = parseDrmType(nextLine);
            state.drmLicenseUrl = parseItem(nextLine, "URI=\"");
            return false;
        }
        if (nextLine.startsWith(KODIPROP_INPUTSTREAM_ADDON)) {
            state.inputstreamaddon = nextLine.substring(KODIPROP_INPUTSTREAM_ADDON.length()).trim();
            return false;
        }
        if (nextLine.startsWith(KODIPROP_MANIFEST_TYPE)) {
            state.manifestType = nextLine.substring(KODIPROP_MANIFEST_TYPE.length()).trim();
            return false;
        }
        if (nextLine.startsWith(KODIPROP_LICENSE_TYPE)) {
            state.drmType = normalizeLicenseType(nextLine.substring(KODIPROP_LICENSE_TYPE.length()).trim(), state.drmType);
            return false;
        }
        if (nextLine.startsWith(KODIPROP_LICENSE_KEY)) {
            applyLicenseKey(state, nextLine.substring(KODIPROP_LICENSE_KEY.length()).trim());
            return false;
        }
        if (isNotBlank(trimmed) && !trimmed.startsWith(COMMENT_PREFIX)) {
            String normalizedCandidate = normalizePotentialUrl(trimmed);
            if (isLikelyStreamUrl(normalizedCandidate)) {
                state.url = normalizedCandidate;
                return true;
            }
        }
        return false;
    }

    private static String normalizeLicenseType(String type, String currentType) {
        if (DRM_TYPE_WIDEVINE.equalsIgnoreCase(type)) {
            return DRM_TYPE_WIDEVINE;
        }
        if ("clearkey".equalsIgnoreCase(type)
                || DRM_TYPE_CLEARKEY.equalsIgnoreCase(type)
                || "com.clearkey.alpha".equalsIgnoreCase(type)) {
            return DRM_TYPE_CLEARKEY;
        }
        return currentType;
    }

    private static void applyLicenseKey(EntryState state, String key) {
        if (DRM_TYPE_CLEARKEY.equalsIgnoreCase(state.drmType)) {
            if (state.clearKeys == null) {
                state.clearKeys = new HashMap<>();
            }
            state.clearKeys.putAll(parseClearKeys(key));
            return;
        }
        state.drmLicenseUrl = key;
    }

    private static final class EntryState {
        private String drmType;
        private String drmLicenseUrl;
        private Map<String, String> clearKeys;
        private String inputstreamaddon;
        private String manifestType;
        private String url;
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
            if (DRM_TYPE_WIDEVINE.equalsIgnoreCase(keyFormat)) {
                return DRM_TYPE_WIDEVINE;
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

    private static String readNonHttpUrl(URL source) throws IOException {
        try (InputStream inputStream = source.openStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
