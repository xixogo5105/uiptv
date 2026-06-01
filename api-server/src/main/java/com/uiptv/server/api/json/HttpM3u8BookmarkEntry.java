package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.BookmarkApplicationService;
import com.uiptv.application.PlaybackApplicationService;
import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.util.WebActivityLog;

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

        Bookmark bookmark = BookmarkApplicationService.getInstance().getBookmark(bookmarkId);
        if (bookmark == null) {
            ex.sendResponseHeaders(404, -1);
            return;
        }
        setActivityDescription(ex, bookmark, null);

        PlaybackApplicationService.BookmarkRedirectResult redirect = PlaybackApplicationService.getInstance().resolveBookmarkRedirect(bookmarkId);
        if (redirect == null || isBlank(redirect.url())) {
            generateResponseText(ex, 502, "Unable to resolve bookmark playback.");
            return;
        }
        setActivityDescription(ex, bookmark, redirect.response());
        ex.getResponseHeaders().add(LOCATION, redirect.url());
        ex.sendResponseHeaders(307, -1);
    }

    private void setActivityDescription(HttpExchange ex, Bookmark bookmark, PlayerResponse response) {
        ex.setAttribute(
                WebActivityLog.ACTIVITY_DESCRIPTION_ATTRIBUTE,
                WebActivityLog.describePublishedM3uEntry(
                        title(bookmark, response),
                        accountName(bookmark, response),
                        bookmark.getCategoryTitle()
                )
        );
    }

    private String title(Bookmark bookmark, PlayerResponse response) {
        Channel channel = response == null ? null : response.getChannel();
        if (channel != null && !isBlank(channel.getName())) {
            return channel.getName();
        }
        return bookmark.getChannelName();
    }

    private String accountName(Bookmark bookmark, PlayerResponse response) {
        Account account = response == null ? null : response.getAccount();
        if (account != null && !isBlank(account.getAccountName())) {
            return account.getAccountName();
        }
        return bookmark.getAccountName();
    }
}
