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
    public Row hydrate(Row row) {
        Account account = AccountService.getInstance().getById(row.accountId());
        if (account == null) return row;
        account.setAction(Account.AccountAction.itv);
        return new Row(row.accountId(), row.accountName(), row.bookmarkId(), row.channel(),
                new EpgApplicationService().schedule(account, row.channel()), false, row.nativeProvider());
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
        if (!new EpgApplicationService().isEnabled()) return List.of();
        EpgApplicationService epg = new EpgApplicationService();
        List<Row> rows = new ArrayList<>();
        if (accountId != null && !accountId.isBlank()) {
            Account account = AccountService.getInstance().getById(accountId);
            if (account == null) return List.of();
            account.setAction(Account.AccountAction.itv);
            List<Channel> channels = CatalogApplicationService.getInstance().listChannels(
                    new CatalogChannelsQuery(accountId, CatalogMode.ITV, CategoryType.ALL.displayName(), ""));
            Map<Channel, List<EpgProgramme>> schedules = epg.catalogSchedules(account, channels);
            for (Channel channel : channels) rows.add(new Row(accountId, account.getAccountName(), "", channel, schedules.getOrDefault(channel, List.of()), nativeProvider(account) && !new NativeEpgApplicationService().fresh(account, channel), nativeProvider(account)));
        } else {
            Set<String> seen = new HashSet<>();
            for (BookmarkResolver.ResolvedBookmark resolved : new BookmarkResolver().resolveBookmarks(BookmarkService.getInstance().read())) {
                Account account = resolved.getAccount();
                Bookmark bookmark = resolved.getBookmark();
                if (account == null || bookmark == null || resolved.getAccountAction() != Account.AccountAction.itv) continue;
                String key = account.getDbId() + "|" + bookmark.getChannelId();
                if (!seen.add(key)) continue;
                Channel channel = ChannelService.getInstance().getChannelByChannelIdAndAccount(bookmark.getChannelId(), account.getDbId(), false);
                if (channel == null) {
                    channel = Channel.fromJson(bookmark.getChannelJson());
                    if (channel == null) channel = new Channel();
                    channel.setChannelId(bookmark.getChannelId());
                    channel.setName(bookmark.getChannelName());
                    channel.setCmd(bookmark.getCmd());
                    channel.setLogo(bookmark.getLogo());
                }
                rows.add(new Row(account.getDbId(), account.getAccountName(), bookmark.getDbId(), channel, epg.cachedSchedule(account, channel), nativeProvider(account) && !new NativeEpgApplicationService().fresh(account, channel), nativeProvider(account)));
            }
        }
        return List.copyOf(rows);
    }
}
