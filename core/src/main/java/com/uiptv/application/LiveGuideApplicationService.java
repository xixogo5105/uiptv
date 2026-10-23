package com.uiptv.application;

import com.uiptv.model.*;
import com.uiptv.service.*;
import java.io.IOException;
import java.util.*;

/** A blank account id explicitly means the bookmark context, never all accounts. */
public class LiveGuideApplicationService {
    public record Row(String accountId, String accountName, String bookmarkId, Channel channel, List<EpgProgramme> programmes, boolean nativePending, boolean nativeProvider) {
        public Row(String accountId, String accountName, String bookmarkId, Channel channel, List<EpgProgramme> programmes) {
            this(accountId, accountName, bookmarkId, channel, programmes, false, false);
        }
        public Row(String accountId, String accountName, String bookmarkId, Channel channel, List<EpgProgramme> programmes, boolean nativePending) {
            this(accountId, accountName, bookmarkId, channel, programmes, nativePending, nativePending);
        }
    }
    /** Resolve a live bookmark using local identity, retaining embedded XMLTV metadata. */
    public Row bookmarkRow(String bookmarkId) {
        Bookmark bookmark = BookmarkApplicationService.getInstance().getBookmark(bookmarkId);
        if (bookmark == null) return null;
        var resolved = new BookmarkResolver().resolveBookmarks(List.of(bookmark)).getFirst();
        Account account = resolved.getAccount();
        if (account == null || resolved.getAccountAction() != Account.AccountAction.itv) return null;
        Channel channel = ChannelService.getInstance().getChannelByChannelIdAndAccount(bookmark.getChannelId(), account.getDbId(), false);
        if (channel == null) channel = Channel.fromJson(bookmark.getChannelJson());
        if (channel == null) channel = new Channel();
        channel.setChannelId(bookmark.getChannelId());
        if (channel.getName() == null || channel.getName().isBlank()) channel.setName(bookmark.getChannelName());
        return new Row(account.getDbId(), account.getAccountName(), bookmarkId, channel, List.of());
    }

    public Row hydrate(Row row) {
        Account account = AccountService.getInstance().getById(row.accountId());
        if (account == null) return row;
        account.setAction(Account.AccountAction.itv);
        var programmes = new EpgCacheApplicationService().schedule(account, row.channel());
        if (programmes.isEmpty()) EpgCacheRefreshService.requestVisible(account, List.of(row.channel()));
        return new Row(row.accountId(), row.accountName(), row.bookmarkId(), row.channel(),
                programmes, false, row.nativeProvider());
    }
    /** Visible-card batch: resolve each account once, then query only persisted summaries. */
    public List<Row> cachedSummaries(List<Row> rows) {
        Map<String,List<Row>> groups=new LinkedHashMap<>();
        rows.forEach(row -> groups.computeIfAbsent(row.accountId(),_ -> new ArrayList<>()).add(row));
        Map<Row,Row> loaded=new IdentityHashMap<>();
        for(var group:groups.entrySet()) {
            Account account=AccountService.getInstance().getById(group.getKey());
            if(account==null) continue;
            account.setAction(Account.AccountAction.itv);
            var summaries=new EpgCacheApplicationService().summaries(account,group.getValue().stream().map(Row::channel).toList());
            // Card rendering is a cache-only read. Missing guide data must not
            // turn scrolling or opening an "All" list into an importer trigger.
            for(Row row:group.getValue()) loaded.put(row,new Row(row.accountId(),row.accountName(),row.bookmarkId(),row.channel(),
                    summaries.getOrDefault(row.channel(),List.of()),false,row.nativeProvider()));
        }
        return rows.stream().map(row -> loaded.getOrDefault(row,row)).toList();
    }

    /** Read persisted source failure state for a bounded batch of already visible cards. */
    public Map<Row,Boolean> cachedSourceWarnings(List<Row> rows) {
        Map<Row,Boolean> result = new IdentityHashMap<>();
        Map<String,List<Row>> groups = new LinkedHashMap<>();
        rows.forEach(row -> groups.computeIfAbsent(row.accountId(), _ -> new ArrayList<>()).add(row));
        for (var group : groups.entrySet()) {
            Account account = AccountService.getInstance().getById(group.getKey());
            if (account == null) continue;
            account.setAction(Account.AccountAction.itv);
            var warnings = new EpgCacheApplicationService().sourceWarnings(account,
                    group.getValue().stream().map(Row::channel).toList());
            group.getValue().forEach(row -> result.put(row, warnings.getOrDefault(row.channel(), false)));
        }
        return result;
    }

    public Map<Row,String> cachedIcons(List<Row> rows) {
        Map<Row,String> result=new IdentityHashMap<>();
        Map<String,List<Row>> groups=new LinkedHashMap<>();
        rows.forEach(row -> groups.computeIfAbsent(row.accountId(),_ -> new ArrayList<>()).add(row));
        for(var group:groups.entrySet()) {
            Account account=AccountService.getInstance().getById(group.getKey()); if(account==null) continue;
            account.setAction(Account.AccountAction.itv);
            var icons=new EpgCacheApplicationService().icons(account,group.getValue().stream().map(Row::channel).toList());
            group.getValue().forEach(row -> result.put(row,icons.getOrDefault(row.channel(),"")));
        }
        return result;
    }
    private boolean nativeProvider(Account account) {
        return account.getType() == com.uiptv.util.AccountType.XTREME_API || account.getType() == com.uiptv.util.AccountType.STALKER_PORTAL;
    }
    public List<Row> visible(String accountId, String visibleIds) throws IOException {
        Set<String> ids = new HashSet<>(Arrays.asList(visibleIds.split(",")));
        List<Row> selected = list(accountId).stream().filter(r -> ids.contains(r.bookmarkId().isBlank() ? r.channel().getDbId() : r.bookmarkId())).limit(32).toList();
        List<Row> rows = new ArrayList<>();
        for (Row row : selected) {
            if (Thread.currentThread().isInterrupted()) break;
            rows.add(hydrate(row));
        }
        return List.copyOf(rows);
    }

    public List<Row> list(String accountId) throws IOException {
        return list(accountId, _ -> { });
    }

    public List<Row> list(String accountId, java.util.function.Consumer<List<Row>> channelsReady) throws IOException {
        if (!new EpgApplicationService().isEnabled()) return List.of();
        if (accountId != null && !accountId.isBlank()) return listAccount(accountId, channelsReady);
        List<Row> rows = listBookmarks();
        channelsReady.accept(List.copyOf(rows));
        return hydrateBookmarks(rows);
    }

    private List<Row> listAccount(String accountId, java.util.function.Consumer<List<Row>> channelsReady) throws IOException {
        Account account = AccountService.getInstance().getById(accountId);
        if (account == null) return List.of();
        account.setAction(Account.AccountAction.itv);
        List<Channel> channels = CatalogApplicationService.getInstance().listChannels(
                new CatalogChannelsQuery(accountId, CatalogMode.ITV, CategoryType.ALL.displayName(), ""), false);
        boolean nativeProvider = nativeProvider(account);
        channelsReady.accept(channels.stream().map(channel -> new Row(accountId, account.getAccountName(), "", channel,
                List.of(), false, nativeProvider)).toList());
        Map<Channel, List<EpgProgramme>> schedules = new EpgCacheApplicationService().summaries(account, channels);
        return channels.stream().map(channel -> new Row(accountId, account.getAccountName(), "", channel,
                schedules.getOrDefault(channel, List.of()), true, nativeProvider)).toList();
    }

    private List<Row> listBookmarks() {
        Set<String> seen = new HashSet<>();
        List<Row> rows = new ArrayList<>();
        for (BookmarkResolver.ResolvedBookmark resolved : new BookmarkResolver().resolveBookmarks(BookmarkService.getInstance().read())) {
            Row row = bookmarkRow(resolved, seen);
            if (row != null) rows.add(row);
        }
        return rows;
    }

    private Row bookmarkRow(BookmarkResolver.ResolvedBookmark resolved, Set<String> seen) {
        Account account = resolved.getAccount();
        Bookmark bookmark = resolved.getBookmark();
        if (account == null || bookmark == null || resolved.getAccountAction() != Account.AccountAction.itv) return null;
        String key = account.getDbId() + "|" + bookmark.getChannelId();
        if (!seen.add(key)) return null;
        Channel channel = ChannelService.getInstance().getChannelByChannelIdAndAccount(bookmark.getChannelId(), account.getDbId(), false);
        if (channel == null) channel = bookmarkChannel(bookmark);
        return new Row(account.getDbId(), account.getAccountName(), bookmark.getDbId(), channel, List.of(), true, nativeProvider(account));
    }

    private Channel bookmarkChannel(Bookmark bookmark) {
        Channel channel = Channel.fromJson(bookmark.getChannelJson());
        if (channel == null) channel = new Channel();
        channel.setChannelId(bookmark.getChannelId());
        channel.setName(bookmark.getChannelName());
        channel.setCmd(bookmark.getCmd());
        channel.setLogo(bookmark.getLogo());
        return channel;
    }

    private List<Row> hydrateBookmarks(List<Row> rows) {
        List<Row> loaded = new ArrayList<>();
        for (Row row : rows) {
            if (!Thread.currentThread().isInterrupted()) {
                Account account = AccountService.getInstance().getById(row.accountId());
                if (account != null) {
                    account.setAction(Account.AccountAction.itv);
                    List<EpgProgramme> programmes = new EpgCacheApplicationService().summaries(account, List.of(row.channel()))
                            .getOrDefault(row.channel(), List.of());
                    loaded.add(new Row(row.accountId(), row.accountName(), row.bookmarkId(), row.channel(),
                            programmes, row.nativePending(), row.nativeProvider()));
                }
            }
        }
        return List.copyOf(loaded);
    }
}
