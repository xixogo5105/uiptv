package com.uiptv.ui;

import com.uiptv.model.PlaylistEntry;
import com.uiptv.util.RssFeedReader;
import com.uiptv.util.StringUtils;

import java.math.BigInteger;
import java.util.*;

public class RssParser {
    public static Set<PlaylistEntry> getCategories() {
        Set<PlaylistEntry> playlistEntries = new LinkedHashSet<>();
        playlistEntries.add(new PlaylistEntry("All", "All", null, null, null));
        return playlistEntries;
    }

    public static List<PlaylistEntry> parse(String rssUrl) {
        List<PlaylistEntry> playlistEntries = new ArrayList<>();
        try {
            for (RssFeedReader.RssItem item : RssFeedReader.getItems(rssUrl)) {
                String lUUID = String.format("%040d", new BigInteger(UUID.randomUUID().toString().replace("-", ""), 16));

                String title = StringUtils.safeUtf(item.getTitle());
                playlistEntries.add(new PlaylistEntry(
                        lUUID,
                        title,
                        title,
                        item.getLink(),
                        ""));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return playlistEntries;
    }

}