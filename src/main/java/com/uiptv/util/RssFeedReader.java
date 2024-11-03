package com.uiptv.util;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;

import javax.net.ssl.HttpsURLConnection;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static com.uiptv.util.StringUtils.isBlank;

public class RssFeedReader {

    public static class RssItem {
        private String title;
        private String link;
        private String description;

        public RssItem(String title, String link, String description) {
            this.title = title;
            this.link = link;
            this.description = description;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getLink() {
            return link;
        }

        public void setLink(String link) {
            this.link = link;
        }
    }

    public static List<RssItem> getItems(String url) throws Exception {
        List<RssItem> items = new ArrayList<>();

        SyndFeedInput input = new SyndFeedInput();
        InputStreamReader reader;
        if (url.startsWith("https")) {
            HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
            reader = (new InputStreamReader(connection.getInputStream()));
        } else {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            reader = (new InputStreamReader(connection.getInputStream()));
        }
        SyndFeed feed = input.build(reader);

        for (SyndEntry entry : feed.getEntries()) {
            String link = entry.getLink();
            if (!entry.getEnclosures().isEmpty()) {
                link = entry.getEnclosures().get(0).getUrl();
            }
            if (isBlank(link)) continue;
            if (!link.toLowerCase().startsWith("http")) {
                link = feed.getLink() + link;
            }
            items.add(new RssItem(entry.getTitle(), link, entry.getDescription().getValue()));
        }

        return items;
    }
}