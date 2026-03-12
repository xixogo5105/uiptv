package com.uiptv.util;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public class RssFeedReader {
    private RssFeedReader() {
    }

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36";

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

    public static List<RssItem> getItems(String url) throws IOException, FeedException {
        List<RssItem> items = new ArrayList<>();

        SyndFeedInput input = new SyndFeedInput();
        Map<String, String> headers = defaultRssHeaders();
        try (HttpUtil.StreamResult response = HttpUtil.openStream(url, headers, "GET", null, HttpUtil.RequestOptions.defaults())) {
            if (response.statusCode() != HttpUtil.STATUS_OK) {
                throw new IOException("RSS request failed: HTTP " + response.statusCode()
                        + " content-type=" + quoteForError(firstHeaderValue(response.responseHeaders(), "content-type")));
            }

            BufferedInputStream buffered = new BufferedInputStream(response.bodyStream());
            if (looksLikeHtml(buffered)) {
                throw new IOException("RSS response was HTML (not a feed). content-type="
                        + quoteForError(firstHeaderValue(response.responseHeaders(), "content-type")));
            }

            try (XmlReader xmlReader = new XmlReader(buffered, true, firstHeaderValue(response.responseHeaders(), "content-type"))) {
                SyndFeed feed = input.build(xmlReader);

                for (SyndEntry entry : feed.getEntries()) {
                    String link = entry.getLink();
                    if (!entry.getEnclosures().isEmpty()) {
                        link = entry.getEnclosures().get(0).getUrl();
                    }
                    if (isBlank(link)) {
                        continue;
                    }
                    if (!link.toLowerCase(Locale.ROOT).startsWith("http")) {
                        link = feed.getLink() + link;
                    }
                    items.add(new RssItem(
                            entry.getTitle(),
                            link,
                            entry.getDescription() != null && isNotBlank(entry.getDescription().getValue()) ? entry.getDescription().getValue() : ""
                    ));
                }
            }
        }

        return items;
    }

    private static Map<String, String> defaultRssHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.8");
        headers.put("Accept-Encoding", "identity");
        return headers;
    }

    private static String firstHeaderValue(Map<String, List<String>> headers, String name) {
        if (headers == null || name == null) {
            return null;
        }
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                List<String> values = e.getValue();
                if (values == null || values.isEmpty()) {
                    return null;
                }
                return values.getFirst();
            }
        }
        return null;
    }

    private static boolean looksLikeHtml(BufferedInputStream in) throws IOException {
        in.mark(4096);
        byte[] buf = in.readNBytes(2048);
        in.reset();

        if (buf.length == 0) {
            return false;
        }
        String prefix = new String(buf, java.nio.charset.StandardCharsets.UTF_8)
                .trim()
                .toLowerCase(Locale.ROOT);
        return prefix.startsWith("<!doctype html") || prefix.startsWith("<html") || prefix.contains("<html");
    }

    private static String quoteForError(String value) {
        return value == null || value.isBlank() ? "<unknown>" : "\"" + value.trim() + "\"";
    }
}
