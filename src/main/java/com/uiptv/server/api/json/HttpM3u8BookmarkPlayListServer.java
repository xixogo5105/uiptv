package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.service.BookmarkService;

import java.io.IOException;

import static com.uiptv.util.ServerUtils.generateM3u8Response;

public class HttpM3u8BookmarkPlayListServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {

        StringBuilder response = new StringBuilder();
        BookmarkService.getInstance().read().forEach(b -> {
            String requestedURL = "http://" + ex.getRequestHeaders().getFirst("Host") + "/bookmarkEntry.ts?bookmarkId=" + b.getDbId();
            response.append("#EXTM3U\n" +
                    "#EXTINF:1," + b.getDbId() + "-" + b.getAccountName() + " - " + b.getChannelName() + "\n" +
                    requestedURL + "\n");
        });
        generateM3u8Response(ex, response.toString(), "bookmarks.m3u8");
    }
}
