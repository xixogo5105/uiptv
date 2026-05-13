package com.uiptv.service;

import com.uiptv.db.ChannelDb;
import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.VodChannelDb;
import com.uiptv.model.*;
import com.uiptv.shared.Episode;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public class PlayerRequestResolver {

    public PlayerResponse resolveBookmarkPlayback(String bookmarkId, String mode, String seriesParentId) throws IOException {
        Bookmark bookmark = BookmarkService.getInstance().getBookmark(bookmarkId);
        Account account = AccountService.getInstance().getAll().get(bookmark.getAccountName());
        applyMode(account, mode);
        if (bookmark.getAccountAction() != null) {
            account.setAction(bookmark.getAccountAction());
        }
        Channel channel = resolveBookmarkChannel(bookmark);
        String scopedCategoryId = resolveSeriesCategoryId(account, bookmark.getCategoryId());
        return PlayerService.getInstance().get(account, channel, bookmark.getChannelId(), seriesParentId, scopedCategoryId);
    }

    public PlayerResponse resolveDirectPlayback(Account account, String categoryId, String channelId,
                                                String mode, String seriesParentId, String seriesId,
                                                Channel requestChannel) throws IOException {
        applyMode(account, mode);
        Channel channel = mergeRequestChannel(resolveRequestedChannel(account, categoryId, channelId, mode), requestChannel);
        String scopedCategoryId = resolveSeriesCategoryId(account, categoryId);
        return PlayerService.getInstance().get(account, channel, seriesId, seriesParentId, scopedCategoryId);
    }

    Channel resolveBookmarkChannel(Bookmark bookmark) {
        Channel channel = readBookmarkSnapshot(bookmark);
        if (channel != null) {
            return channel;
        }
        return createLegacyBookmarkChannel(bookmark);
    }

    Channel mergeRequestChannel(Channel channel, Channel requestChannel) {
        if (requestChannel == null) {
            return channel;
        }
        if (channel == null) {
            return requestChannel;
        }
        fillIfBlank(channel::getName, channel::setName, requestChannel.getName());
        fillIfBlank(channel::getLogo, channel::setLogo, requestChannel.getLogo());
        fillIfBlank(channel::getCmd, channel::setCmd, requestChannel.getCmd());
        fillIfBlank(channel::getCmd_1, channel::setCmd_1, requestChannel.getCmd_1());
        fillIfBlank(channel::getCmd_2, channel::setCmd_2, requestChannel.getCmd_2());
        fillIfBlank(channel::getCmd_3, channel::setCmd_3, requestChannel.getCmd_3());
        fillIfBlank(channel::getDrmType, channel::setDrmType, requestChannel.getDrmType());
        fillIfBlank(channel::getDrmLicenseUrl, channel::setDrmLicenseUrl, requestChannel.getDrmLicenseUrl());
        fillIfBlank(channel::getClearKeysJson, channel::setClearKeysJson, requestChannel.getClearKeysJson());
        fillIfBlank(channel::getInputstreamaddon, channel::setInputstreamaddon, requestChannel.getInputstreamaddon());
        fillIfBlank(channel::getManifestType, channel::setManifestType, requestChannel.getManifestType());
        fillIfBlank(channel::getSeason, channel::setSeason, requestChannel.getSeason());
        fillIfBlank(channel::getEpisodeNum, channel::setEpisodeNum, requestChannel.getEpisodeNum());
        return channel;
    }

    String resolveSeriesCategoryId(Account account, String rawCategoryId) {
        if (account == null || account.getAction() != Account.AccountAction.series) {
            return "";
        }
        if (isBlank(rawCategoryId)) {
            return "";
        }
        Category category = SeriesCategoryDb.get().getById(rawCategoryId);
        if (category != null && isNotBlank(category.getCategoryId())) {
            return category.getCategoryId();
        }
        return rawCategoryId;
    }

    private Channel readBookmarkSnapshot(Bookmark bookmark) {
        if (bookmark == null) {
            return null;
        }
        if (isNotBlank(bookmark.getSeriesJson())) {
            Episode episode = Episode.fromJson(bookmark.getSeriesJson());
            if (episode != null) {
                Channel channel = new Channel();
                channel.setCmd(episode.getCmd());
                channel.setName(episode.getTitle());
                channel.setChannelId(episode.getId());
                if (episode.getInfo() != null) {
                    channel.setLogo(episode.getInfo().getMovieImage());
                }
                return channel;
            }
        }
        if (isNotBlank(bookmark.getChannelJson())) {
            return Channel.fromJson(bookmark.getChannelJson());
        }
        if (isNotBlank(bookmark.getVodJson())) {
            return Channel.fromJson(bookmark.getVodJson());
        }
        return null;
    }

    private Channel createLegacyBookmarkChannel(Bookmark bookmark) {
        Channel channel = new Channel();
        channel.setCmd(decodeBookmarkCmd(bookmark.getCmd()));
        channel.setChannelId(bookmark.getChannelId());
        channel.setName(bookmark.getChannelName());
        channel.setDrmType(bookmark.getDrmType());
        channel.setDrmLicenseUrl(bookmark.getDrmLicenseUrl());
        channel.setClearKeysJson(bookmark.getClearKeysJson());
        channel.setInputstreamaddon(bookmark.getInputstreamaddon());
        channel.setManifestType(bookmark.getManifestType());
        return channel;
    }

    private String decodeBookmarkCmd(String value) {
        if (isBlank(value)) {
            return value;
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception _) {
            return value;
        }
    }

    private Channel resolveRequestedChannel(Account account, String categoryId, String channelId, String mode) {
        String normalizedMode = isBlank(mode) ? "" : mode.trim().toLowerCase();
        if ("vod".equals(normalizedMode) && account != null) {
            Channel vodChannel = VodChannelDb.get().getChannelByChannelId(channelId, categoryId, account.getDbId());
            if (vodChannel != null) {
                return vodChannel;
            }
            return VodChannelDb.get().getChannelByChannelIdAndAccount(channelId, account.getDbId());
        }
        return ChannelDb.get().getChannelById(channelId, categoryId);
    }

    private void applyMode(Account account, String mode) {
        if (account == null || isBlank(mode)) {
            return;
        }
        try {
            account.setAction(Account.AccountAction.valueOf(mode.toLowerCase()));
        } catch (Exception _) {
            account.setAction(Account.AccountAction.itv);
        }
    }

    private void fillIfBlank(java.util.function.Supplier<String> getter,
                             java.util.function.Consumer<String> setter,
                             String value) {
        if (isBlank(getter.get()) && isNotBlank(value)) {
            setter.accept(value);
        }
    }
}
