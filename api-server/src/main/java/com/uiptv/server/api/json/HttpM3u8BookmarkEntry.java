package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.BookmarkApplicationService;
import com.uiptv.application.PlaybackApplicationService;

import java.io.IOException;

import static com.uiptv.util.ServerUtils.generateResponseText;
import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.StringUtils.isBlank;

public class HttpM3u8BookmarkEntry implements HttpHandler {
    private static final String GET = "GET";
    private static final String HEAD = "HEAD";
    private static final String ALLOW = "Allow";
    private static final String LOCATION = "Location";

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if (!GET.equalsIgnoreCase(method) && !HEAD.equalsIgnoreCase(method)) {
            ex.getResponseHeaders().set(ALLOW, GET + ", " + HEAD);
            ex.sendResponseHeaders(405, -1);
            return;
        }

        String bookmarkId = getParam(ex, "bookmarkId");
        if (isBlank(bookmarkId)) {
            ex.sendResponseHeaders(404, -1);
            return;
        }

        if (BookmarkApplicationService.getInstance().getBookmark(bookmarkId) == null) {
            ex.sendResponseHeaders(404, -1);
            return;
        }

        String url = PlaybackApplicationService.getInstance().resolveBookmarkRedirectUrl(bookmarkId);
        if (isBlank(url)) {
            generateResponseText(ex, 502, "Unable to resolve bookmark playback.");
            return;
        }
        ex.getResponseHeaders().add(LOCATION, url);
        ex.sendResponseHeaders(307, -1);
    }
}
