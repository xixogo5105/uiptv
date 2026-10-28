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
    private static final Map<String, String> PLAYLIST_REQUEST_HEADERS = Map.of("User-Agent", "UIPTV");
    private static final String EXTINF = "#EXTINF";
    private static final String EXT_X_KEY = "#EXT-X-KEY";
    private static final String KODIPROP_INPUTSTREAM_ADDON = "#KODIPROP:inputstreamaddon=";
    private static final String KODIPROP_MANIFEST_TYPE = "#KODIPROP:inputstream.adaptive.manifest_type=";
    private static final String KODIPROP_LICENSE_TYPE = "#KODIPROP:inputstream.adaptive.license_type=";
    private static final String KODIPROP_LICENSE_KEY = "#KODIPROP:inputstream.adaptive.license_key=";
    private static final String EXTGRP = "#EXTGRP:";
    private static final String ATTRIBUTE_GROUP_TITLE = "group-title";
    private static final String EXTVLCOPT = "#EXTVLCOPT:";
    private static final String WEBPROP = "#WEBPROP:";
    private static final String PLAYLIST_TYPE = "#EXT-X-PLAYLIST-TYPE:";
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
                HttpUtil.StreamResult response = openPlaylistStream(source);
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
                HttpUtil.StreamResult response = openPlaylistStream(m3u8Uri.toString());
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
                HttpUtil.StreamResult response = openPlaylistStream(source);
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
                HttpUtil.StreamResult response = openPlaylistStream(m3u8Uri.toString());
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
        ParseCategoryState state = new ParseCategoryState();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                processCategoryInputLine(line.strip(), state);
            }
            addPendingCategoryEntry(state);
        } catch (Exception e) {
            AppLog.addErrorLog(M3U8Parser.class, e.getMessage());
        }
        if (state.hasUncategorizedEntries
                && state.entries.stream().noneMatch(entry -> entry.getGroupTitle().equalsIgnoreCase(UNCATEGORIZED))) {
            state.entries.add(new PlaylistEntry(UNCATEGORIZED, UNCATEGORIZED, null, null, null));
        }
        return state.entries;
    }

    private static void processCategoryInputLine(String trimmed, ParseCategoryState state) {
        if (trimmed.startsWith(EXTGRP)) {
            state.activeDirectiveGroups = splitGroupTitles(trimmed.substring(EXTGRP.length()));
            if (state.pendingEntry != null && parseAttribute(state.pendingEntry, ATTRIBUTE_GROUP_TITLE).isBlank()) {
                state.pendingGroups = state.activeDirectiveGroups;
            }
            return;
        }
        if (!trimmed.startsWith(EXTINF)) return;
        addPendingCategoryEntry(state);
        state.pendingEntry = trimmed;
        boolean hasGroupTitle = !parseAttribute(trimmed, ATTRIBUTE_GROUP_TITLE).isBlank();
        if (hasGroupTitle) state.activeDirectiveGroups = List.of();
        state.pendingGroups = hasGroupTitle ? List.of() : state.activeDirectiveGroups;
    }

    private static void addPendingCategoryEntry(ParseCategoryState state) {
        if (state.pendingEntry == null) return;
        if (processCategoryLineSafely(state.entries, state.pendingEntry, state.pendingGroups)) {
            state.hasUncategorizedEntries = true;
        }
        state.pendingEntry = null;
        state.pendingGroups = List.of();
    }

    private static final class ParseCategoryState {
        private final Set<PlaylistEntry> entries = new LinkedHashSet<>();
        private List<String> activeDirectiveGroups = List.of();
        private String pendingEntry;
        private List<String> pendingGroups = List.of();
        private boolean hasUncategorizedEntries;

        private ParseCategoryState() {
            entries.add(new PlaylistEntry(CategoryType.ALL.displayName(), CategoryType.ALL.displayName(), null, null, null));
        }
    }

    private static boolean processCategoryLineSafely(Set<PlaylistEntry> playlistEntries, String line, List<String> directiveGroups) {
        try {
            return processCategoryLine(playlistEntries, line, directiveGroups);
        } catch (RuntimeException e) {
            AppLog.addErrorLog(M3U8Parser.class, e.getMessage());
            return false;
        }
    }

    private static boolean processCategoryLine(Set<PlaylistEntry> playlistEntries, String line, List<String> directiveGroups) {
        String tvgId = parseAttribute(line, "tvg-id");
        List<String> groupTitles = splitGroupTitles(parseAttribute(line, ATTRIBUTE_GROUP_TITLE));
        if (groupTitles.isEmpty()) groupTitles = directiveGroups;
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
                parser.accept(line.replace("\uFEFF", "").strip());
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
        private List<String> directiveGroups = List.of();
        private final Map<String, Object> metadata = new LinkedHashMap<>();
    }

    private static final class EntryParser {
        private final Consumer<PlaylistEntry> consumer;
        private EntryHeader header;
        private EntryState state;
        private List<String> activeDirectiveGroups = List.of();

        private EntryParser(Consumer<PlaylistEntry> consumer) {
            this.consumer = consumer;
        }

        private void accept(String line) {
            if (line.strip().startsWith(EXTGRP) && header == null) {
                activeDirectiveGroups = splitGroupTitles(line.strip().substring(EXTGRP.length()));
                return;
            }
            if (line.startsWith(EXTINF)) {
                startEntry(line);
                return;
            }
            applyLineToActiveEntry(line);
        }

        private void startEntry(String line) {
            header = parseEntryHeader(line);
            state = new EntryState();
            if (!header.groupTitles.isEmpty()) activeDirectiveGroups = List.of();
            state.directiveGroups = activeDirectiveGroups;
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
            List<String> groups = header.groupTitles.isEmpty() ? state.directiveGroups : header.groupTitles;
            for (String groupTitle : effectiveGroupTitles(groups)) {
                PlaylistEntry entry = new PlaylistEntry(header.tvgId, groupTitle, header.title, state.url, header.logo,
                        state.drmType, state.drmLicenseUrl, state.clearKeys, state.inputstreamaddon, state.manifestType);
                Map<String, Object> metadata = new LinkedHashMap<>(header.attributes);
                metadata.putAll(state.metadata);
                entry.setMetadata(metadata);
                consumer.accept(entry);
            }
        }

        private static EntryHeader parseEntryHeader(String line) {
            Map<String, Object> attributes = parseAttributes(line);
            String title = parseTitle(line);
            if (title.isBlank()) title = parseAttribute(line, "tvg-name");
            return new EntryHeader(parseAttribute(line, "tvg-id"),
                    splitGroupTitles(parseAttribute(line, ATTRIBUTE_GROUP_TITLE)), title,
                    parseAttribute(line, "tvg-logo"), attributes);
        }

        private static Map<String, Object> parseAttributes(String line) {
            Map<String, Object> values = new LinkedHashMap<>();
            Pattern attributes = Pattern.compile("(?:^|\\s)([\\w-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s,]+))");
            Matcher matcher = attributes.matcher(line);
            while (matcher.find()) {
                String value = parsedAttributeValue(matcher);
                values.put(matcher.group(1).toLowerCase(Locale.ROOT), value);
            }
            return values;
        }

        private static String parsedAttributeValue(Matcher matcher) {
            if (matcher.group(2) != null) return matcher.group(2);
            if (matcher.group(3) != null) return matcher.group(3);
            return matcher.group(4);
        }

        private static List<String> effectiveGroupTitles(List<String> groupTitles) {
            if (groupTitles == null || groupTitles.isEmpty()) {
                return List.of(UNCATEGORIZED);
            }
            return groupTitles;
        }

        private boolean applyMetaLine(EntryState state, String nextLine, String trimmed) {
            if (trimmed.startsWith(EXTGRP)) {
                state.directiveGroups = splitGroupTitles(trimmed.substring(EXTGRP.length()));
                activeDirectiveGroups = state.directiveGroups;
                state.metadata.put("extgrp", trimmed.substring(EXTGRP.length()).trim());
                return false;
            }
            if (trimmed.startsWith(EXTVLCOPT)) {
                addProperty(state.metadata, "extvlcopt", trimmed.substring(EXTVLCOPT.length()));
                return false;
            }
            if (trimmed.startsWith(WEBPROP)) {
                addProperty(state.metadata, "webprop", trimmed.substring(WEBPROP.length()));
                return false;
            }
            if (trimmed.startsWith(PLAYLIST_TYPE)) {
                state.metadata.put("playlistType", trimmed.substring(PLAYLIST_TYPE.length()).trim());
                return false;
            }
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
            if (trimmed.startsWith("#KODIPROP:")) {
                addProperty(state.metadata, "kodiprop", trimmed.substring("#KODIPROP:".length()));
                return false;
            }
            if (isNotBlank(trimmed) && !trimmed.startsWith(COMMENT_PREFIX)) {
                String normalizedCandidate = normalizePotentialUrl(trimmed);
                if (isLikelyStreamUrl(normalizedCandidate)) {
                    state.url = separateUrlHeaders(normalizedCandidate, state);
                    return true;
                }
            }
            return false;
        }

        @SuppressWarnings("unchecked")
        private static void addProperty(Map<String, Object> metadata, String key, String raw) {
            int equals = raw.indexOf('=');
            String name = equals < 0 ? raw.trim() : raw.substring(0, equals).trim();
            String value = equals < 0 ? "" : raw.substring(equals + 1).trim();
            if (name.isBlank()) return;
            Map<String, String> properties = (Map<String, String>) metadata.computeIfAbsent(key, _ -> new LinkedHashMap<String, String>());
            properties.put(name, value);
        }

        private static String separateUrlHeaders(String candidate, EntryState state) {
            int delimiter = candidate.indexOf('|');
            if (delimiter < 0 || delimiter == candidate.length() - 1) return candidate;
            Map<String, String> headers = new LinkedHashMap<>();
            Map<String, String> vlcOptions = new LinkedHashMap<>();
            for (String pair : candidate.substring(delimiter + 1).split("&")) {
                addUrlHeader(pair, headers, vlcOptions);
            }
            if (!headers.isEmpty()) state.metadata.put("urlHeaders", headers);
            if (!vlcOptions.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, String> existing = (Map<String, String>) state.metadata.computeIfAbsent("extvlcopt", _ -> new LinkedHashMap<String, String>());
                existing.putAll(vlcOptions);
            }
            return candidate.substring(0, delimiter);
        }

        private static void addUrlHeader(String pair, Map<String, String> headers, Map<String, String> vlcOptions) {
            int equals = pair.indexOf('=');
            if (equals <= 0) return;
            String name = pair.substring(0, equals).trim();
            String value = pair.substring(equals + 1).trim();
            if (name.isBlank() || value.isBlank()) return;
            headers.put(name, value);
            switch (name.toLowerCase(Locale.ROOT)) {
                case "user-agent" -> vlcOptions.put("http-user-agent", value);
                case "referer", "referrer" -> vlcOptions.put("http-referrer", value);
                case "origin" -> vlcOptions.put("http-origin", value);
                case "cookie" -> vlcOptions.put("http-cookie", value);
                default -> { /* Preserve unrecognized headers for export and web playback. */ }
            }
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
            return candidate.matches("(?i)^[^.]+\\.(m3u8|mpd|ts|aac|mp3|mp4|m4s)(\\?.*)?$");
        }
    }

    private record EntryHeader(String tvgId, List<String> groupTitles, String title, String logo, Map<String, Object> attributes) {
    }

    private static boolean isHttpUri(URI source) {
        String protocol = source == null ? "" : source.getScheme();
        return protocol != null && protocol.toLowerCase().startsWith("http");
    }

    private static HttpUtil.StreamResult openPlaylistStream(String source) throws IOException {
        HttpUtil.StreamResult response = HttpUtil.openStream(
                source, PLAYLIST_REQUEST_HEADERS, "GET", null, HttpUtil.RequestOptions.defaults());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            int status = response.statusCode();
            response.close();
            throw new IOException("M3U playlist request failed with HTTP " + status);
        }
        return response;
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
        if (source == null) {
            throw new IllegalArgumentException("Source URI cannot be null");
        }
        if ("file".equalsIgnoreCase(source.getScheme())) {
            return Files.newBufferedReader(Path.of(source), StandardCharsets.UTF_8);
        }
        if (source.getScheme() == null) {
            return Files.newBufferedReader(Path.of(source.toString()), StandardCharsets.UTF_8);
        }
        InputStream inputStream = source.toURL().openStream();
        return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }
}
