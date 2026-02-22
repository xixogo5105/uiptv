package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.service.*;
import com.uiptv.shared.Episode;

import java.io.IOException;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public class HttpPlayerJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String bookmarkId = getParam(ex, "bookmarkId");
        String accountId = getParam(ex, "accountId");
        String categoryId = getParam(ex, "categoryId");
        String channelId = getParam(ex, "channelId");
        String mode = getParam(ex, "mode");

        PlayerResponse response;

        if (isNotBlank(bookmarkId)) {
            Bookmark bookmark = BookmarkService.getInstance().getBookmark(bookmarkId);
            Account account = AccountService.getInstance().getAll().get(bookmark.getAccountName());
            applyMode(account, mode);
            if (bookmark.getAccountAction() != null) {
                account.setAction(bookmark.getAccountAction());
            }
            
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
            response = PlayerService.getInstance().get(account, channel, bookmark.getChannelId());
        } else {
            Account account = AccountService.getInstance().getById(accountId);
            applyMode(account, mode);
            Channel channel = ChannelDb.get().getChannelById(channelId, categoryId);

            if (channel == null) {
                channel = new Channel();
                channel.setChannelId(channelId);
                channel.setName(getParam(ex, "name"));
                channel.setLogo(getParam(ex, "logo"));
                channel.setCmd(getParam(ex, "cmd"));
                channel.setDrmType(getParam(ex, "drmType"));
                channel.setDrmLicenseUrl(getParam(ex, "drmLicenseUrl"));
                channel.setClearKeysJson(getParam(ex, "clearKeysJson"));
                channel.setInputstreamaddon(getParam(ex, "inputstreamaddon"));
                channel.setManifestType(getParam(ex, "manifestType"));
            }
            String seriesId = getParam(ex, "seriesId");
            response = PlayerService.getInstance().get(account, channel, seriesId);
        }

        // Check if transmuxing is needed and enabled
        boolean isFfmpegEnabled = ConfigurationService.getInstance().read().isEnableFfmpegTranscoding();
        if (isFfmpegEnabled && FfmpegService.getInstance().isTransmuxingNeeded(response.getUrl())) {
            FfmpegService.getInstance().startTransmuxing(response.getUrl());
            response.setUrl("/hls/stream.m3u8");
            response.setManifestType("hls");
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

    private void applyMode(Account account, String mode) {
        if (account == null || isBlank(mode)) {
            return;
        }
        try {
            account.setAction(Account.AccountAction.valueOf(mode.toLowerCase()));
        } catch (Exception ignored) {
            account.setAction(Account.AccountAction.itv);
        }
    }
}
