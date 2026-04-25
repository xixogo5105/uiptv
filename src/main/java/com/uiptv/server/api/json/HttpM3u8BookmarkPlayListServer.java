package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.model.Bookmark;
import com.uiptv.model.BookmarkCategory;
import com.uiptv.service.BookmarkService;
import com.uiptv.util.I18n;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.uiptv.util.StringUtils.isNotBlank;
import static com.uiptv.util.ServerUtils.generateM3u8Response;

public class HttpM3u8BookmarkPlayListServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String host = ex.getRequestHeaders().getFirst("Host");
        String allTabName = I18n.tr("commonAll");
        List<Bookmark> bookmarks = BookmarkService.getInstance().read();
        Map<String, String> categoryNameById = loadCategoryNamesById();
        StringBuilder response = new StringBuilder("#EXTM3U\n");
        appendGroupedEntries(response, bookmarks, host, allTabName);
        appendCategorizedEntries(response, bookmarks, host, categoryNameById, allTabName);
        generateM3u8Response(ex, response.toString(), host + "-bookmarks.m3u8");
    }

    private Map<String, String> loadCategoryNamesById() {
        Map<String, String> names = new LinkedHashMap<>();
        for (BookmarkCategory category : BookmarkService.getInstance().getAllCategories()) {
            if (category != null && isNotBlank(category.getId()) && isNotBlank(category.getName())) {
                names.put(category.getId(), category.getName());
            }
        }
        return names;
    }

    private void appendGroupedEntries(StringBuilder response, List<Bookmark> bookmarks, String host, String groupTitle) {
        for (Bookmark bookmark : bookmarks) {
            appendPlaylistEntry(response, bookmark, host, groupTitle);
        }
    }

    private void appendCategorizedEntries(StringBuilder response,
                                          List<Bookmark> bookmarks,
                                          String host,
                                          Map<String, String> categoryNameById,
                                          String allTabName) {
        for (Bookmark bookmark : bookmarks) {
            String categoryId = bookmark.getCategoryId();
            if (!isNotBlank(categoryId)) {
                continue;
            }
            String categoryName = categoryNameById.get(categoryId);
            if (!isNotBlank(categoryName) || categoryName.equalsIgnoreCase(allTabName)) {
                continue;
            }
            appendPlaylistEntry(response, bookmark, host, categoryName);
        }
    }

    private void appendPlaylistEntry(StringBuilder response, Bookmark bookmark, String host, String groupTitle) {
        String requestedURL = "http://" + host + "/bookmarkEntry.ts?bookmarkId=" + bookmark.getDbId();
        response.append("#EXTINF:-1 tvg-id=\"")
                .append(bookmark.getDbId())
                .append("\" tvg-name=\"")
                .append(bookmark.getChannelName())
                .append("\" group-title=\"")
                .append(groupTitle)
                .append("\",")
                .append(bookmark.getChannelName())
                .append("\n")
                .append(requestedURL)
                .append("\n");
    }
}
