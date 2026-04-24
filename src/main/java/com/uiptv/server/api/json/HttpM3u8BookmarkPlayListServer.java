package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.service.BookmarkService;

import java.io.IOException;

import static com.uiptv.util.ServerUtils.generateM3u8Response;

public class HttpM3u8BookmarkPlayListServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {

        StringBuilder response = new StringBuilder("#EXTM3U\n");
        BookmarkService.getInstance().read().forEach(b -> {
            String requestedURL = "http://" + ex.getRequestHeaders().getFirst("Host") + "/bookmarkEntry.ts?bookmarkId=" + b.getDbId();
            response.append("#EXTINF:-1 tvg-id=\"").append(b.getDbId()).append("\" tvg-name=\"").append(b.getChannelName()).append("\" group-title=\"").append(b.getAccountName()).append("\",").append(b.getChannelName()).append("\n").append(requestedURL).append("\n");
        });
        generateM3u8Response(ex, response.toString(), ex.getRequestHeaders().getFirst("Host") + "-bookmarks.m3u8");
    }
}
