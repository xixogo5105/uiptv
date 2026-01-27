package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.service.AccountService;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.PlayerService;

import java.io.IOException;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.StringUtils.isNotBlank;

public class HttpPlayerJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String bookmarkId = getParam(ex, "bookmarkId");
        String accountId = getParam(ex, "accountId");
        String categoryId = getParam(ex, "categoryId");
        String channelId = getParam(ex, "channelId");

        PlayerResponse response;

        if (isNotBlank(bookmarkId)) {
            Bookmark bookmark = BookmarkService.getInstance().getBookmark(bookmarkId);
            Account account = AccountService.getInstance().getByName(bookmark.getAccountName());
            response = PlayerService.getInstance().runBookmark(account, bookmark);
        } else {
            Account account = AccountService.getInstance().getById(accountId);
            Channel channel = ChannelDb.get().getChannelById(channelId, categoryId);
            response = PlayerService.getInstance().get(account, channel);
        }

        generateJsonResponse(ex, buildJsonResponse(response));
    }

    private String buildJsonResponse(PlayerResponse response) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"url\":\"").append(response.getUrl()).append("\"");

        boolean hasDrm = isNotBlank(response.getDrmType()) || isNotBlank(response.getInputstreamaddon()) || isNotBlank(response.getManifestType());
        if (hasDrm) {
            json.append(",\"drm\":{");
            if (isNotBlank(response.getDrmType())) {
                json.append("\"type\":\"").append(response.getDrmType()).append("\",");
            }
            if (isNotBlank(response.getDrmLicenseUrl())) {
                json.append("\"licenseUrl\":\"").append(response.getDrmLicenseUrl()).append("\",");
            }
            if (isNotBlank(response.getClearKeysJson())) {
                json.append("\"clearKeys\":").append(response.getClearKeysJson()).append(",");
            }
            if (isNotBlank(response.getInputstreamaddon())) {
                json.append("\"inputstreamaddon\":\"").append(response.getInputstreamaddon()).append("\",");
            }
            if (isNotBlank(response.getManifestType())) {
                json.append("\"manifestType\":\"").append(response.getManifestType()).append("\"");
            }
            if (json.charAt(json.length() - 1) == ',') {
                json.deleteCharAt(json.length() - 1);
            }
            json.append("}");
        }

        json.append("}");
        return json.toString();
    }
}
