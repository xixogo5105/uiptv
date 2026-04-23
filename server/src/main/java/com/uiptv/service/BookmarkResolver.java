package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.Channel;
import com.uiptv.shared.Episode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public class BookmarkResolver {

    public ResolutionContext prepare(List<Bookmark> bookmarks) {
        Map<String, Account> accountByName = AccountService.getInstance().getAll();
        Map<String, BookmarkRenderData> renderDataByBookmarkId = preloadRenderData(bookmarks);
        Map<String, Channel> channelByAccountAndChannel = preloadFallbackChannels(bookmarks, accountByName, renderDataByBookmarkId);
        return new ResolutionContext(accountByName, channelByAccountAndChannel, renderDataByBookmarkId);
    }

    public List<ResolvedBookmark> resolveBookmarks(List<Bookmark> bookmarks) {
        ResolutionContext context = prepare(bookmarks);
        List<ResolvedBookmark> resolved = new ArrayList<>();
        if (bookmarks == null) {
            return resolved;
        }
        for (Bookmark bookmark : bookmarks) {
            resolved.add(resolveBookmark(bookmark, context));
        }
        return resolved;
    }

    public ResolvedBookmark resolveBookmark(Bookmark bookmark, ResolutionContext context) {
        Account account = context.accountByName.get(bookmark.getAccountName());
        BookmarkRenderData renderData = context.renderDataByBookmarkId.getOrDefault(bookmarkKey(bookmark), BookmarkRenderData.fromBookmark(bookmark));
        if (needsChannelFallback(renderData)) {
            mergeRenderData(renderData, lookupFallbackChannel(account, bookmark.getChannelId(), context.channelByAccountAndChannel));
        }

        Account.AccountAction accountAction = bookmark.getAccountAction();
        if (accountAction == null) {
            accountAction = account != null ? account.getAction() : Account.AccountAction.itv;
        }
        ResolvedBookmark resolved = new ResolvedBookmark(bookmark, account, accountAction, renderData);
        resolved.applyToBookmark();
        return resolved;
    }

    private BookmarkRenderData resolveBookmarkRenderData(Bookmark bookmark) {
        BookmarkRenderData renderData = BookmarkRenderData.fromBookmark(bookmark);
        mergeRenderData(renderData, resolveBookmarkChannelSnapshot(bookmark));
        return renderData;
    }

    private Map<String, BookmarkRenderData> preloadRenderData(List<Bookmark> bookmarks) {
        Map<String, BookmarkRenderData> renderDataByBookmarkId = new HashMap<>();
        if (bookmarks == null || bookmarks.isEmpty()) {
            return renderDataByBookmarkId;
        }
        for (Bookmark bookmark : bookmarks) {
            if (bookmark == null) {
                continue;
            }
            renderDataByBookmarkId.put(bookmarkKey(bookmark), resolveBookmarkRenderData(bookmark));
        }
        return renderDataByBookmarkId;
    }

    private Channel resolveBookmarkChannelSnapshot(Bookmark bookmark) {
        if (bookmark == null) {
            return null;
        }
        if (isNotBlank(bookmark.getChannelJson())) {
            Channel channel = Channel.fromJson(bookmark.getChannelJson());
            if (channel != null) {
                return channel;
            }
        }
        if (isNotBlank(bookmark.getVodJson())) {
            Channel channel = Channel.fromJson(bookmark.getVodJson());
            if (channel != null) {
                return channel;
            }
        }
        if (isNotBlank(bookmark.getSeriesJson())) {
            Episode episode = Episode.fromJson(bookmark.getSeriesJson());
            if (episode != null) {
                Channel channel = new Channel();
                channel.setLogo(episode.getInfo() != null ? episode.getInfo().getMovieImage() : "");
                return channel;
            }
        }
        return null;
    }

    private boolean needsChannelFallback(BookmarkRenderData renderData) {
        return renderData == null
                || isBlank(renderData.logo)
                || isBlank(renderData.drmType)
                || isBlank(renderData.drmLicenseUrl)
                || isBlank(renderData.clearKeysJson)
                || isBlank(renderData.inputstreamaddon)
                || isBlank(renderData.manifestType);
    }

    private void mergeRenderData(BookmarkRenderData target, Channel channel) {
        if (target == null || channel == null) {
            return;
        }
        if (isBlank(target.logo)) target.logo = channel.getLogo();
        if (isBlank(target.drmType)) target.drmType = channel.getDrmType();
        if (isBlank(target.drmLicenseUrl)) target.drmLicenseUrl = channel.getDrmLicenseUrl();
        if (isBlank(target.clearKeysJson)) target.clearKeysJson = channel.getClearKeysJson();
        if (isBlank(target.inputstreamaddon)) target.inputstreamaddon = channel.getInputstreamaddon();
        if (isBlank(target.manifestType)) target.manifestType = channel.getManifestType();
    }

    private Map<String, Channel> preloadFallbackChannels(List<Bookmark> bookmarks,
                                                         Map<String, Account> accountByName,
                                                         Map<String, BookmarkRenderData> renderDataByBookmarkId) {
        Map<String, Channel> channelByAccountAndChannel = new HashMap<>();
        if (bookmarks == null || bookmarks.isEmpty() || accountByName == null || accountByName.isEmpty()) {
            return channelByAccountAndChannel;
        }

        Map<String, List<String>> requestedChannelIdsByAccountId = collectFallbackChannelIds(bookmarks, accountByName, renderDataByBookmarkId);
        loadFallbackChannelsIntoCache(requestedChannelIdsByAccountId, channelByAccountAndChannel);

        return channelByAccountAndChannel;
    }

    private Map<String, List<String>> collectFallbackChannelIds(List<Bookmark> bookmarks,
                                                                Map<String, Account> accountByName,
                                                                Map<String, BookmarkRenderData> renderDataByBookmarkId) {
        Map<String, List<String>> requestedChannelIdsByAccountId = new HashMap<>();
        for (Bookmark bookmark : bookmarks) {
            if (!requiresFallbackLookup(bookmark, accountByName, renderDataByBookmarkId)) {
                continue;
            }
            Account account = accountByName.get(bookmark.getAccountName());
            requestedChannelIdsByAccountId
                    .computeIfAbsent(account.getDbId(), ignored -> new ArrayList<>())
                    .add(bookmark.getChannelId());
        }
        return requestedChannelIdsByAccountId;
    }

    private boolean requiresFallbackLookup(Bookmark bookmark,
                                           Map<String, Account> accountByName,
                                           Map<String, BookmarkRenderData> renderDataByBookmarkId) {
        if (bookmark == null) {
            return false;
        }
        BookmarkRenderData renderData = renderDataByBookmarkId.get(bookmarkKey(bookmark));
        if (!needsChannelFallback(renderData)) {
            return false;
        }
        Account account = accountByName.get(bookmark.getAccountName());
        return account != null && isNotBlank(account.getDbId()) && isNotBlank(bookmark.getChannelId());
    }

    private void loadFallbackChannelsIntoCache(Map<String, List<String>> requestedChannelIdsByAccountId,
                                               Map<String, Channel> channelByAccountAndChannel) {
        for (Map.Entry<String, List<String>> entry : requestedChannelIdsByAccountId.entrySet()) {
            List<Channel> channels = ChannelService.getInstance().getChannelsByChannelIdsAndAccount(entry.getValue(), entry.getKey(), false);
            cacheChannelsByAccountAndId(entry.getKey(), channels, channelByAccountAndChannel);
        }
    }

    private void cacheChannelsByAccountAndId(String accountId, List<Channel> channels, Map<String, Channel> channelByAccountAndChannel) {
        for (Channel channel : channels) {
            if (channel == null || isBlank(channel.getChannelId())) {
                continue;
            }
            channelByAccountAndChannel.put(accountId + "|" + channel.getChannelId(), channel);
        }
    }

    private Channel lookupFallbackChannel(Account account, String channelId, Map<String, Channel> channelByAccountAndChannel) {
        if (account == null || isBlank(account.getDbId()) || isBlank(channelId)) {
            return null;
        }
        String key = account.getDbId() + "|" + channelId;
        if (channelByAccountAndChannel.containsKey(key)) {
            return channelByAccountAndChannel.get(key);
        }
        Channel channel = null;
        try {
            channel = ChannelService.getInstance().getChannelByChannelIdAndAccount(channelId, account.getDbId(), false);
        } catch (Exception _) {
            // Best-effort fallback only. Resolver should not fail on auxiliary channel lookup.
        }
        channelByAccountAndChannel.put(key, channel);
        return channel;
    }

    private String bookmarkKey(Bookmark bookmark) {
        if (bookmark == null) {
            return "";
        }
        if (isNotBlank(bookmark.getDbId())) {
            return bookmark.getDbId();
        }
        return (bookmark.getAccountName() == null ? "" : bookmark.getAccountName()) + "|"
                + (bookmark.getChannelId() == null ? "" : bookmark.getChannelId()) + "|"
                + (bookmark.getChannelName() == null ? "" : bookmark.getChannelName());
    }

    public static final class ResolutionContext {
        private final Map<String, Account> accountByName;
        private final Map<String, Channel> channelByAccountAndChannel;
        private final Map<String, BookmarkRenderData> renderDataByBookmarkId;

        private ResolutionContext(Map<String, Account> accountByName,
                                  Map<String, Channel> channelByAccountAndChannel,
                                  Map<String, BookmarkRenderData> renderDataByBookmarkId) {
            this.accountByName = accountByName == null ? Map.of() : accountByName;
            this.channelByAccountAndChannel = channelByAccountAndChannel == null ? Map.of() : channelByAccountAndChannel;
            this.renderDataByBookmarkId = renderDataByBookmarkId == null ? Map.of() : renderDataByBookmarkId;
        }
    }

    public static final class ResolvedBookmark {
        private final Bookmark bookmark;
        private final Account account;
        private final Account.AccountAction accountAction;
        private final BookmarkRenderData renderData;

        private ResolvedBookmark(Bookmark bookmark, Account account, Account.AccountAction accountAction, BookmarkRenderData renderData) {
            this.bookmark = bookmark;
            this.account = account;
            this.accountAction = accountAction;
            this.renderData = renderData != null ? renderData : new BookmarkRenderData();
        }

        public Bookmark getBookmark() {
            return bookmark;
        }

        public Account getAccount() {
            return account;
        }

        public Account.AccountAction getAccountAction() {
            return accountAction;
        }

        public String getLogo() {
            return renderData.logo;
        }

        public String getDrmType() {
            return renderData.drmType;
        }

        public String getDrmLicenseUrl() {
            return renderData.drmLicenseUrl;
        }

        public String getClearKeysJson() {
            return renderData.clearKeysJson;
        }

        public String getInputstreamaddon() {
            return renderData.inputstreamaddon;
        }

        public String getManifestType() {
            return renderData.manifestType;
        }

        public void applyToBookmark() {
            if (bookmark == null) {
                return;
            }
            if (bookmark.getAccountAction() == null && accountAction != null) {
                bookmark.setAccountAction(accountAction);
            }
            bookmark.setLogo(renderData.logo);
            bookmark.setDrmType(renderData.drmType);
            bookmark.setDrmLicenseUrl(renderData.drmLicenseUrl);
            bookmark.setClearKeysJson(renderData.clearKeysJson);
            bookmark.setInputstreamaddon(renderData.inputstreamaddon);
            bookmark.setManifestType(renderData.manifestType);
        }
    }

    private static final class BookmarkRenderData {
        private String logo;
        private String drmType;
        private String drmLicenseUrl;
        private String clearKeysJson;
        private String inputstreamaddon;
        private String manifestType;

        private static BookmarkRenderData fromBookmark(Bookmark bookmark) {
            BookmarkRenderData data = new BookmarkRenderData();
            if (bookmark == null) {
                return data;
            }
            data.logo = bookmark.getLogo();
            data.drmType = bookmark.getDrmType();
            data.drmLicenseUrl = bookmark.getDrmLicenseUrl();
            data.clearKeysJson = bookmark.getClearKeysJson();
            data.inputstreamaddon = bookmark.getInputstreamaddon();
            data.manifestType = bookmark.getManifestType();
            return data;
        }
    }
}
