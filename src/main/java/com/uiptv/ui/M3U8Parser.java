package com.uiptv.ui;

import com.uiptv.model.PlaylistEntry;
import com.uiptv.widget.UIptvAlert;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.uiptv.util.StringUtils.EMPTY;
import static com.uiptv.util.StringUtils.isNotBlank;

public class M3U8Parser {
    private static final String EXTINF = "#EXTINF";
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
        if (playlistEntries.size() > 1 && playlistEntries.stream().noneMatch(entry -> entry.getGroupTitle().equalsIgnoreCase("Uncategorized"))) {
            playlistEntries.add(new PlaylistEntry("Uncategorized", "Uncategorized", null, null, null));
        }
        return playlistEntries;
    }

    private static List<PlaylistEntry> parseM3U8(BufferedReader reader) {
        List<PlaylistEntry> playlistEntries = new ArrayList<>();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    if (line.startsWith(EXTINF)) {
                        playlistEntries.add(new PlaylistEntry(
                                parseItem(line, "tvg-id=\""),
                                parseItem(line, "group-title=\""),
                                parseTitle(line),
                                parseUrl(reader),
                                parseItem(line, "tvg-logo=\"")));
                    }
                } catch (Exception e) {
                    UIptvAlert.showError(e.getMessage());
                }
            }
        } catch (Exception e) {
            UIptvAlert.showError(e.getMessage());
        }

        return playlistEntries;
    }

    private static String parseUrl(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (isNotBlank(line) && !line.startsWith(COMMENT_PREFIX)) {
                return line;
            }
        }
        return EMPTY;
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
}