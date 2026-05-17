package com.uiptv.util;

import com.uiptv.model.CategoryType;
import com.uiptv.shared.PlaylistEntry;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.uiptv.util.M3uPlaylistUtils.parseAttribute;
import static com.uiptv.util.M3uPlaylistUtils.splitGroupTitles;
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
    private static final String UNCATEGORIZED = CategoryType.UNCATEGORIZED.displayName();
    private static final String DRM_TYPE_WIDEVINE = "com.widevine.alpha";
    private static final String DRM_TYPE_CLEARKEY = "org.w3.clearkey";

    private M3U8Parser() {
    }

    public static Set<PlaylistEntry> parseUrlCategory(URL m3u8Url) {
        try {
            if (isHttpUrl(m3u8Url)) {
                HttpUtil.StreamResult response = HttpUtil.openStream(m3u8Url.toString(), null, "GET", null, HttpUtil.RequestOptions.defaults());
                if (response == null) {
                    HttpUtil.HttpResult result = HttpUtil.sendRequest(m3u8Url.toString(), null, "GET");
                    return parseCategory(new BufferedReader(new StringReader(result.body())));
                }
                try (response;
                     BufferedReader reader = new BufferedReader(new InputStreamReader(response.bodyStream(), StandardCharsets.UTF_8))) {
                    return parseCategory(reader);
                }
            }
            try (InputStream inputStream = m3u8Url.openStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return parseCategory(reader);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to parse M3U categories from URL", e);
        }
    }

    public static Set<PlaylistEntry> parsePathCategory(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath, StandardCharsets.UTF_8))) {
            return parseCategory(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to parse M3U categories from file", e);
        }
    }

    public static List<PlaylistEntry> parseChannelUrlM3U8(URL m3u8Url) {
        List<PlaylistEntry> entries = new ArrayList<>();
        forEachChannelUrlM3U8(m3u8Url, entries::add);
        return entries;
    }

    public static void forEachChannelUrlM3U8(URL m3u8Url, Consumer<PlaylistEntry> consumer) {
        try {
            if (isHttpUrl(m3u8Url)) {
                HttpUtil.StreamResult response = HttpUtil.openStream(m3u8Url.toString(), null, "GET", null, HttpUtil.RequestOptions.defaults());
                if (response == null) {
                    HttpUtil.HttpResult result = HttpUtil.sendRequest(m3u8Url.toString(), null, "GET");
                    parseM3U8(new BufferedReader(new StringReader(result.body())), consumer);
                    return;
                }
                try (response;
                     BufferedReader reader = new BufferedReader(new InputStreamReader(response.bodyStream(), StandardCharsets.UTF_8))) {
                    parseM3U8(reader, consumer);
                    return;
                }
            }
            try (InputStream inputStream = m3u8Url.openStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                parseM3U8(reader, consumer);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to parse M3U channels from URL", e);
        }
    }

    public static List<PlaylistEntry> parseChannelPathM3U8(String filePath) {
        List<PlaylistEntry> entries = new ArrayList<>();
        forEachChannelPathM3U8(filePath, entries::add);
        return entries;
    }

    public static void forEachChannelPathM3U8(String filePath, Consumer<PlaylistEntry> consumer) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath, StandardCharsets.UTF_8))) {
            parseM3U8(reader, consumer);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to parse M3U channels from file", e);
        }
    }

    private static Set<PlaylistEntry> parseCategory(BufferedReader reader) {
        Set<PlaylistEntry> playlistEntries = new LinkedHashSet<>();
        playlistEntries.add(new PlaylistEntry(CategoryType.ALL.displayName(), CategoryType.ALL.displayName(), null, null, null));
        boolean hasUncategorizedEntries = false;
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith(EXTINF)) {
                    continue;
                }
                if (processCategoryLineSafely(playlistEntries, line)) {
                    hasUncategorizedEntries = true;
                }
            }
        } catch (Exception e) {
            AppLog.addErrorLog(M3U8Parser.class, e.getMessage());
        }
        if (hasUncategorizedEntries
                && playlistEntries.stream().noneMatch(entry -> entry.getGroupTitle().equalsIgnoreCase(UNCATEGORIZED))) {
            playlistEntries.add(new PlaylistEntry(UNCATEGORIZED, UNCATEGORIZED, null, null, null));
        }
        return playlistEntries;
    }

    private static boolean processCategoryLineSafely(Set<PlaylistEntry> playlistEntries, String line) {
        try {
            return processCategoryLine(playlistEntries, line);
        } catch (RuntimeException e) {
            AppLog.addErrorLog(M3U8Parser.class, e.getMessage());
            return false;
        }
    }

    private static boolean processCategoryLine(Set<PlaylistEntry> playlistEntries, String line) {
        String tvgId = parseAttribute(line, "tvg-id");
        List<String> groupTitles = splitGroupTitles(parseAttribute(line, "group-title"));
        if (groupTitles.isEmpty()) {
            return true;
        }
        for (String groupTitle : groupTitles) {
            PlaylistEntry categoryEntry = buildCategoryEntry(tvgId, groupTitle);
            if (categoryEntry != null) {
                playlistEntries.add(categoryEntry);
            }
        }
        return groupTitles.stream().anyMatch(M3U8Parser::shouldTreatAsUncategorized);
    }

    private static List<PlaylistEntry> parseM3U8(BufferedReader reader) {
        List<PlaylistEntry> playlistEntries = new ArrayList<>();
        parseM3U8(reader, playlistEntries::add);
        return playlistEntries;
    }

    private static void parseM3U8(BufferedReader reader, Consumer<PlaylistEntry> consumer) {
        if (consumer == null) {
            return;
        }
        try {
            EntryHeader header = null;
            EntryState state = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith(EXTINF)) {
                    if (header != null && state != null) {
                        String trimmed = line == null ? EMPTY : line.trim();
                        if (applyMetaLine(state, line, trimmed) && state.url != null) {
                            addParsedEntry(consumer, header, state);
                            header = null;
                            state = null;
                        }
                    }
                    continue;
                }
                header = parseEntryHeader(line);
                state = new EntryState();
            }
            if (header != null && state != null && isNotBlank(state.url)) {
                addParsedEntry(consumer, header, state);
            }
        } catch (IOException e) {
            AppLog.addErrorLog(M3U8Parser.class, e.getMessage());
        }
    }

    private static void addParsedEntry(Consumer<PlaylistEntry> consumer, EntryHeader header, EntryState state) {
        for (String groupTitle : effectiveGroupTitles(header.groupTitles)) {
            consumer.accept(new PlaylistEntry(header.tvgId, groupTitle, header.title, state.url, header.logo,
                    state.drmType, state.drmLicenseUrl, state.clearKeys, state.inputstreamaddon, state.manifestType));
        }
    }

    private static ParsedEntry parseEntryState(List<String> lines, int startIndex) {
        EntryState state = new EntryState();
        if (lines == null || lines.isEmpty()) {
            return new ParsedEntry(state, -1);
        }
        for (int index = Math.max(0, startIndex); index < lines.size(); index++) {
            String nextLine = lines.get(index);
            String trimmed = nextLine == null ? EMPTY : nextLine.trim();
            if (trimmed.startsWith(EXTINF)) {
                return new ParsedEntry(state, index - 1);
            }
            if (applyMetaLine(state, nextLine, trimmed) && state.url != null) {
                return new ParsedEntry(state, index);
            }
        }
        return new ParsedEntry(state, lines.size() - 1);
    }

    private static EntryHeader parseEntryHeader(String line) {
        return new EntryHeader(
                parseAttribute(line, "tvg-id"),
                splitGroupTitles(parseAttribute(line, "group-title")),
                parseTitle(line),
                parseAttribute(line, "tvg-logo")
        );
    }

    private static boolean shouldTreatAsUncategorized(String groupTitle) {
        return !isNotBlank(groupTitle) || groupTitle.equalsIgnoreCase(UNCATEGORIZED);
    }

    private static List<String> effectiveGroupTitles(List<String> groupTitles) {
        if (groupTitles == null || groupTitles.isEmpty()) {
            return List.of(UNCATEGORIZED);
        }
        return groupTitles;
    }

    private static PlaylistEntry buildCategoryEntry(String tvgId, String groupTitle) {
        if (!isNotBlank(groupTitle) || groupTitle.equalsIgnoreCase(CategoryType.ALL.displayName())) {
            return null;
        }
        return new PlaylistEntry(tvgId, groupTitle, null, null, null);
    }

    private static boolean applyMetaLine(EntryState state, String nextLine, String trimmed) {
        if (nextLine.startsWith(EXT_X_KEY)) {
            state.drmType = parseDrmType(nextLine);
            state.drmLicenseUrl = parseAttribute(nextLine, "URI");
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

    private record EntryHeader(String tvgId, List<String> groupTitles, String title, String logo) {
    }

    private record ParsedEntry(EntryState state, int lastIndex) {
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
        if (!isNotBlank(keyString)) {
            return keys;
        }
        String normalized = keyString.trim();
        if (normalized.startsWith("{") && normalized.endsWith("}")) {
            try {
                JSONObject json = new JSONObject(normalized);
                for (String key : json.keySet()) {
                    keys.put(key, json.optString(key));
                }
                return keys;
            } catch (Exception _) {
                // Fall through to legacy pair parsing for malformed inputs.
            }
        }
        for (String pair : normalized.split(";")) {
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

    private static boolean isHttpUrl(URL source) {
        String protocol = source == null ? "" : source.getProtocol();
        return protocol != null && protocol.toLowerCase().startsWith("http");
    }
}
