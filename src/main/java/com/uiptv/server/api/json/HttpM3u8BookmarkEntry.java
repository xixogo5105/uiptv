package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.service.AccountService;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.PlayerService;
import com.uiptv.util.StringUtils;

import java.io.IOException;
import java.net.URLDecoder;

import static com.uiptv.util.ServerUtils.generateTs8Response;
import static com.uiptv.util.ServerUtils.getParam;
import static java.nio.charset.StandardCharsets.UTF_8;

public class HttpM3u8BookmarkEntry implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        Bookmark bookmark = BookmarkService.getInstance().getBookmark(getParam(ex, "bookmarkId"));

        String response = "#EXTM3U\n" +
                "#EXTINF:-1 tvg-id=\"" + bookmark.getDbId() + "\" tvg-name=\"" + bookmark.getChannelName() + "\" group-title=\"" + bookmark.getAccountName() + "\"," + bookmark.getChannelName() + "\n" + StringUtils.EMPTY + bookmarkPlayerResponse(ex) + "\n";
        generateTs8Response(ex, response, bookmark.getDbId() + "-" + bookmark.getAccountName() + " - " + bookmark.getChannelName() + ".ts");
    }

    private static String bookmarkPlayerResponse(HttpExchange ex) throws IOException {
        Bookmark bookmark = BookmarkService.getInstance().getBookmark(getParam(ex, "bookmarkId"));
        Account account = AccountService.getInstance().getByName(bookmark.getAccountName());
        String cmd = URLDecoder.decode(bookmark.getCmd(), UTF_8);
        return PlayerService.getInstance().get(account, cmd);
    }

}
