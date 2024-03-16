package com.uiptv.ui;

import com.uiptv.model.PlaylistEntry;
import com.uiptv.widget.UIptvAlert;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.uiptv.util.StringUtils.EMPTY;
import static com.uiptv.util.StringUtils.isNotBlank;

public class M3U8Parser {
    public static Set<PlaylistEntry> parseUrlCategory(URL m3u8Url) {
        try {
            if (m3u8Url.getProtocol().startsWith("https")) {
                HttpsURLConnection connection = (HttpsURLConnection) m3u8Url.openConnection();
                return parseCategory(new BufferedReader(new InputStreamReader(connection.getInputStream())));
            } else if (m3u8Url.getProtocol().startsWith("http")) {
                HttpURLConnection connection = (HttpURLConnection) m3u8Url.openConnection();
                return parseCategory(new BufferedReader(new InputStreamReader(connection.getInputStream())));
            }
            return parseCategory(new BufferedReader(new InputStreamReader(m3u8Url.openStream())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Set<PlaylistEntry> parsePathCategory(String filePath) {
        try {
            return parseCategory(new BufferedReader(new FileReader(filePath)));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<PlaylistEntry> parseChannelUrlM3U8(URL m3u8Url) {
        try {
            if (m3u8Url.getProtocol().startsWith("https")) {
                HttpsURLConnection connection = (HttpsURLConnection) m3u8Url.openConnection();
                return parseM3U8(new BufferedReader(new InputStreamReader(connection.getInputStream())));
            }
            if (m3u8Url.getProtocol().startsWith("http")) {
                HttpURLConnection connection = (HttpURLConnection) m3u8Url.openConnection();
                return parseM3U8(new BufferedReader(new InputStreamReader(connection.getInputStream())));
            }
            return parseM3U8(new BufferedReader(new InputStreamReader(m3u8Url.openStream())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<PlaylistEntry> parseChannelPathM3U8(String filePath) {
        try {
            return parseM3U8(new BufferedReader(new FileReader(filePath)));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static Set<PlaylistEntry> parseCategory(BufferedReader in) {
//        Set<PlaylistEntry> playlistEntries = new TreeSet<>((s1, s2) -> s2.getGroupTitle().compareTo(s1.getGroupTitle()));
        Set<PlaylistEntry> playlistEntries = new LinkedHashSet<>();
        playlistEntries.add(new PlaylistEntry("All", "All", null, null, null));
        try (BufferedReader reader = new BufferedReader(in)) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    if (line.startsWith("#EXTINF")) {
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
                } catch (Exception ignored) {
                    UIptvAlert.showError(ignored.getMessage());
                }
            }
        } catch (Exception e) {
            UIptvAlert.showError(e.getMessage());
        }

        return playlistEntries;
    }

    private static List<PlaylistEntry> parseM3U8(BufferedReader in) {
        List<PlaylistEntry> playlistEntries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(in)) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    if (line.startsWith("#EXTINF")) {
                        playlistEntries.add(new PlaylistEntry(
                                parseItem(line, "tvg-id=\""),
                                parseItem(line, "group-title=\""),
                                parseTitle(line),
                                reader.readLine(),
                                parseItem(line, "tvg-logo=\"")));
                    }
                } catch (Exception ignored) {
                    UIptvAlert.showError(ignored.getMessage());
                }
            }
        } catch (Exception e) {
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
        String[] firstItem = line.split(",");
        if (firstItem.length > 1) {
            return firstItem[1];
        }
        return EMPTY;
    }
}
