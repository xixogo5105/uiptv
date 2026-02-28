package com.uiptv.util;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

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
        HttpUtil.HttpResult response = HttpUtil.sendRequest(url, null, "GET");
        SyndFeed feed = input.build(new StringReader(response.body()));

        for (SyndEntry entry : feed.getEntries()) {
            String link = entry.getLink();
            if (!entry.getEnclosures().isEmpty()) {
                link = entry.getEnclosures().get(0).getUrl();
            }
            if (isBlank(link)) {
                continue;
            }
            if (!link.toLowerCase().startsWith("http")) {
                link = feed.getLink() + link;
            }
            items.add(new RssItem(entry.getTitle(), link, entry.getDescription() != null && isNotBlank(entry.getDescription().getValue()) ? entry.getDescription().getValue() : ""));
        }

        return items;
    }
}
