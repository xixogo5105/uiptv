package com.uiptv.server.api.json;

import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.HandshakeService;
import com.uiptv.service.PlayerService;
import com.uiptv.util.StringUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.URLDecoder;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.StringUtils.isNotBlank;
import static java.nio.charset.StandardCharsets.UTF_8;

public class HttpPlayerJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (isNotBlank(getParam(ex, "bookmarkId"))) {
            bookmarkPlayerResponse(ex);
        } else {
            channelPlayerResponse(ex);
        }
    }

    private static void bookmarkPlayerResponse(HttpExchange ex) throws IOException {
        Bookmark bookmark = BookmarkService.getInstance().getBookmark(getParam(ex, "bookmarkId"));
        Account account = AccountService.getInstance().getByName(bookmark.getAccountName());
        String cmd = URLDecoder.decode(bookmark.getCmd(), UTF_8);
        String response = "{ \"url\":\"" + StringUtils.EMPTY + PlayerService.getInstance().get(account, cmd) + "\",\"channelName\":\" Test " + StringUtils.EMPTY + bookmark.getChannelName() + "\"}";
        generateJsonResponse(ex, response);
    }

    private static void channelPlayerResponse(HttpExchange ex) throws IOException {
        Account account = AccountService.getInstance().getById(getParam(ex, "accountId"));
        Channel channel = ChannelDb.get().getChannelById(getParam(ex, "channelId"), getParam(ex, "categoryId"));
        HandshakeService.getInstance().hardTokenRefresh(account);
        String cmd = URLDecoder.decode(channel.getCmd(), UTF_8);
        String response = "{ \"url\":\"" + StringUtils.EMPTY + PlayerService.getInstance().get(account, cmd) + "\",\"channelName\":\" Test " + StringUtils.EMPTY + channel.getName() + "\"}";
        generateJsonResponse(ex, response);
    }
}
