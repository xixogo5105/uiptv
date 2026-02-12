package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.service.AccountService;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.PlayerService;
import com.uiptv.shared.Episode;
import com.uiptv.util.StringUtils;

import java.io.IOException;
import java.net.URLDecoder;

import static com.uiptv.util.ServerUtils.generateTs8Response;
import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.StringUtils.isNotBlank;
import static java.nio.charset.StandardCharsets.UTF_8;

public class HttpM3u8BookmarkEntry implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String bookmarkId = getParam(ex, "bookmarkId");
        if (isNotBlank(bookmarkId)) {
            Bookmark bookmark = BookmarkService.getInstance().getBookmark(bookmarkId);
            if (bookmark != null) {
                String response = "#EXTM3U\n" +
                        "#EXTINF:-1 tvg-id=\"" + bookmark.getDbId() + "\" tvg-name=\"" + bookmark.getChannelName() + "\" group-title=\"" + bookmark.getAccountName() + "\"," + bookmark.getChannelName() + "\n" + StringUtils.EMPTY + bookmarkPlayerResponse(ex, bookmark) + "\n";
                generateTs8Response(ex, response, bookmark.getDbId() + "-" + bookmark.getAccountName() + " - " + bookmark.getChannelName() + ".ts");
            }
        }
    }

    private static String bookmarkPlayerResponse(HttpExchange ex, Bookmark bookmark) throws IOException {
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

        PlayerResponse playerResponse = PlayerService.getInstance().get(account, channel, bookmark.getChannelId());
        
        bookmark.setCmd(originalCmd);
        return playerResponse.getUrl();
    }

}
