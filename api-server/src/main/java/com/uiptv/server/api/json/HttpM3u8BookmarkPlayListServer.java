package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.BookmarkApplicationService;

import java.io.IOException;
import static com.uiptv.util.ServerUtils.generateM3u8Response;

public class HttpM3u8BookmarkPlayListServer implements HttpHandler {
    static final String MISC_GROUP_TITLE = BookmarkApplicationService.MISC_GROUP_TITLE;

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String host = ex.getRequestHeaders().getFirst("Host");
        generateM3u8Response(ex, buildPlaylist(host), host + "-bookmarks.m3u8");
    }

    public static String buildPlaylist(String host) {
        return BookmarkApplicationService.getInstance().buildPlaylist(host);
    }
}
