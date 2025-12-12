package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.service.BookmarkService;
import com.uiptv.util.StringUtils;

import java.io.IOException;

import static com.uiptv.util.ServerUtils.generateJsonResponse;

public class HttpBookmarksJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        generateJsonResponse(ex, StringUtils.EMPTY + BookmarkService.getInstance().readToJson());
    }
}
