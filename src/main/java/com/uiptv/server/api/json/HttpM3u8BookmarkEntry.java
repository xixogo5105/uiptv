package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.PlayerService;
import com.uiptv.shared.Episode;

import java.io.IOException;
import java.net.URLDecoder;

import static com.uiptv.util.ServerUtils.generateResponseText;
import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;
import static java.nio.charset.StandardCharsets.UTF_8;

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

        Bookmark bookmark = BookmarkService.getInstance().getBookmark(bookmarkId);
        if (bookmark == null) {
            ex.sendResponseHeaders(404, -1);
            return;
        }

        String url = bookmarkPlayerResponse(bookmark);
        if (isBlank(url)) {
            generateResponseText(ex, 502, "Unable to resolve bookmark playback.");
            return;
        }
        ex.getResponseHeaders().add(LOCATION, url);
        ex.sendResponseHeaders(307, -1);
    }

    private static String bookmarkPlayerResponse(Bookmark bookmark) throws IOException {
        Account account = AccountService.getInstance().getAll().get(bookmark.getAccountName());

        String originalCmd = bookmark.getCmd();
        bookmark.setCmd(URLDecoder.decode(originalCmd, UTF_8));

        Channel channel = null;
        if (isNotBlank(bookmark.getSeriesJson())) {
            Episode episode = Episode.fromJson(bookmark.getSeriesJson());
            if (episode != null) {
                channel = new Channel();
                channel.setCmd(episode.getCmd());
                channel.setName(episode.getTitle());
                channel.setChannelId(episode.getId());
                if (episode.getInfo() != null) {
                    channel.setLogo(episode.getInfo().getMovieImage());
                }
            }
        } else if (isNotBlank(bookmark.getChannelJson())) {
            channel = Channel.fromJson(bookmark.getChannelJson());
        } else if (isNotBlank(bookmark.getVodJson())) {
            channel = Channel.fromJson(bookmark.getVodJson());
        }

        if (channel == null) { // Fallback for legacy bookmarks
            channel = new Channel();
            channel.setCmd(bookmark.getCmd());
            channel.setChannelId(bookmark.getChannelId());
            channel.setName(bookmark.getChannelName());
            channel.setDrmType(bookmark.getDrmType());
            channel.setDrmLicenseUrl(bookmark.getDrmLicenseUrl());
            channel.setClearKeysJson(bookmark.getClearKeysJson());
            channel.setInputstreamaddon(bookmark.getInputstreamaddon());
            channel.setManifestType(bookmark.getManifestType());
        }

        String url = PlayerService.getInstance().get(account, channel, bookmark.getChannelId()).getUrl();

        bookmark.setCmd(originalCmd);
        return url;
    }

}
