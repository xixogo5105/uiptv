package com.uiptv.util;

import com.uiptv.model.CategoryType;
import com.uiptv.shared.PlaylistEntry;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        return parseSourceCategory(m3u8Url.toString());
    }

    public static Set<PlaylistEntry> parseSourceCategory(String m3u8Source) {
        try {
            String source = normalizeSource(m3u8Source);
            if (isHttpSource(source)) {
                HttpUtil.StreamResult response = HttpUtil.openStream(source, null, "GET", null, HttpUtil.RequestOptions.defaults());
                try (response;
                     BufferedReader reader = new BufferedReader(new InputStreamReader(response.bodyStream(), StandardCharsets.UTF_8))) {
                    return parseCategory(reader);
                }
            }
            try (BufferedReader reader = openUriReader(URI.create(source))) {
                return parseCategory(reader);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to parse M3U categories from URL", e);
        }
    }

    public static Set<PlaylistEntry> parseUriCategory(URI m3u8Uri) {
        try {
            if (isHttpUri(m3u8Uri)) {
                HttpUtil.StreamResult response = HttpUtil.openStream(m3u8Uri.toString(), null, "GET", null, HttpUtil.RequestOptions.defaults());
                try (response;
                     BufferedReader reader = new BufferedReader(new InputStreamReader(response.bodyStream(), StandardCharsets.UTF_8))) {
                    return parseCategory(reader);
                }
            }
            try (BufferedReader reader = openUriReader(m3u8Uri)) {
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
        return parseChannelSourceM3U8(m3u8Url.toString());
    }

    public static List<PlaylistEntry> parseChannelSourceM3U8(String m3u8Source) {
        List<PlaylistEntry> entries = new ArrayList<>();
        forEachChannelSourceM3U8(m3u8Source, entries::add);
        return entries;
    }

    public static List<PlaylistEntry> parseChannelUriM3U8(URI m3u8Uri) {
        List<PlaylistEntry> entries = new ArrayList<>();
        forEachChannelUriM3U8(m3u8Uri, entries::add);
        return entries;
    }

    public static void forEachChannelUrlM3U8(URL m3u8Url, Consumer<PlaylistEntry> consumer) {
        forEachChannelSourceM3U8(m3u8Url.toString(), consumer);
    }

    public static void forEachChannelSourceM3U8(String m3u8Source, Consumer<PlaylistEntry> consumer) {
        try {
            String source = normalizeSource(m3u8Source);
            if (isHttpSource(source)) {
                HttpUtil.StreamResult response = HttpUtil.openStream(source, null, "GET", null, HttpUtil.RequestOptions.defaults());
                try (response;
                     BufferedReader reader = new BufferedReader(new InputStreamReader(response.bodyStream(), StandardCharsets.UTF_8))) {
                    parseM3U8(reader, consumer);
                    return;
                }
            }
            try (BufferedReader reader = openUriReader(URI.create(source))) {
                parseM3U8(reader, consumer);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to parse M3U channels from URL", e);
        }
    }

    public static void forEachChannelUriM3U8(URI m3u8Uri, Consumer<PlaylistEntry> consumer) {
        try {
            if (isHttpUri(m3u8Uri)) {
                HttpUtil.StreamResult response = HttpUtil.openStream(m3u8Uri.toString(), null, "GET", null, HttpUtil.RequestOptions.defaults());
                try (response;
                     BufferedReader reader = new BufferedReader(new InputStreamReader(response.bodyStream(), StandardCharsets.UTF_8))) {
                    parseM3U8(reader, consumer);
                    return;
                }
            }
            try (BufferedReader reader = openUriReader(m3u8Uri)) {
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

    private static void parseM3U8(BufferedReader reader, Consumer<PlaylistEntry> consumer) {
        if (consumer == null) {
            return;
        }
        try {
            EntryParser parser = new EntryParser(consumer);
            String line;
            while ((line = reader.readLine()) != null) {
                parser.accept(line);
            }
            parser.flush();
        } catch (IOException e) {
            AppLog.addErrorLog(M3U8Parser.class, e.getMessage());
        }
    }

    private static boolean shouldTreatAsUncategorized(String groupTitle) {
        return !isNotBlank(groupTitle) || groupTitle.equalsIgnoreCase(UNCATEGORIZED);
    }

    private static PlaylistEntry buildCategoryEntry(String tvgId, String groupTitle) {
        if (!isNotBlank(groupTitle) || groupTitle.equalsIgnoreCase(CategoryType.ALL.displayName())) {
            return null;
        }
        return new PlaylistEntry(tvgId, groupTitle, null, null, null);
    }

    private static final class EntryState {
        private String drmType;
        private String drmLicenseUrl;
        private Map<String, String> clearKeys;
        private String inputstreamaddon;
        private String manifestType;
        private String url;
    }

    private static final class EntryParser {
        private final Consumer<PlaylistEntry> consumer;
        private EntryHeader header;
        private EntryState state;

        private EntryParser(Consumer<PlaylistEntry> consumer) {
            this.consumer = consumer;
        }

        private void accept(String line) {
            if (line.startsWith(EXTINF)) {
                startEntry(line);
                return;
            }
            applyLineToActiveEntry(line);
        }

        private void startEntry(String line) {
            header = parseEntryHeader(line);
            state = new EntryState();
        }

        private void applyLineToActiveEntry(String line) {
            if (header == null) {
                return;
            }
            String trimmed = line.trim();
            if (applyMetaLine(state, line, trimmed)) {
                addParsedEntry(consumer, header, state);
                clearEntry();
            }
        }

        private void flush() {
            if (header != null && isNotBlank(state.url)) {
                addParsedEntry(consumer, header, state);
            }
        }

        private void clearEntry() {
            header = null;
            state = null;
        }

        private static void addParsedEntry(Consumer<PlaylistEntry> consumer, EntryHeader header, EntryState state) {
            for (String groupTitle : effectiveGroupTitles(header.groupTitles)) {
                consumer.accept(new PlaylistEntry(header.tvgId, groupTitle, header.title, state.url, header.logo,
                        state.drmType, state.drmLicenseUrl, state.clearKeys, state.inputstreamaddon, state.manifestType));
            }
        }

        private static EntryHeader parseEntryHeader(String line) {
            return new EntryHeader(
                    parseAttribute(line, "tvg-id"),
                    splitGroupTitles(parseAttribute(line, "group-title")),
                    parseTitle(line),
                    parseAttribute(line, "tvg-logo")
            );
        }

        private static List<String> effectiveGroupTitles(List<String> groupTitles) {
            if (groupTitles == null || groupTitles.isEmpty()) {
                return List.of(UNCATEGORIZED);
            }
            return groupTitles;
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
    }

    private record EntryHeader(String tvgId, List<String> groupTitles, String title, String logo) {
    }

    private static boolean isHttpUri(URI source) {
        String protocol = source == null ? "" : source.getScheme();
        return protocol != null && protocol.toLowerCase().startsWith("http");
    }

    private static boolean isHttpSource(String source) {
        if (!isNotBlank(source)) {
            return false;
        }
        return source.regionMatches(true, 0, "http://", 0, "http://".length())
                || source.regionMatches(true, 0, "https://", 0, "https://".length());
    }

    private static String normalizeSource(String source) {
        return source == null ? "" : source.trim();
    }

    private static BufferedReader openUriReader(URI source) throws IOException {
        if (source != null && "file".equalsIgnoreCase(source.getScheme())) {
            return Files.newBufferedReader(Path.of(source), StandardCharsets.UTF_8);
        }
        if (source != null && source.getScheme() == null) {
            return Files.newBufferedReader(Path.of(source.toString()), StandardCharsets.UTF_8);
        }
        InputStream inputStream = source.toURL().openStream();
        return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }
}
