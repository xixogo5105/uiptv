package com.uiptv.application;

import com.uiptv.model.*;
import com.uiptv.service.*;
import java.io.IOException;
import java.util.*;

/** A blank account id explicitly means the bookmark context, never all accounts. */
public class LiveGuideApplicationService {
    public record Row(String accountId, String accountName, String bookmarkId, Channel channel, List<EpgProgramme> programmes) { }

    public List<Row> list(String accountId) throws IOException {
        EpgApplicationService epg = new EpgApplicationService();
        List<Row> rows = new ArrayList<>();
        if (accountId != null && !accountId.isBlank()) {
            Account account = AccountService.getInstance().getById(accountId);
            if (account == null) return List.of();
            account.setAction(Account.AccountAction.itv);
            List<Channel> channels = CatalogApplicationService.getInstance().listChannels(
                    new CatalogChannelsQuery(accountId, CatalogMode.ITV, CategoryType.ALL.displayName(), ""));
            for (Channel channel : channels) rows.add(new Row(accountId, account.getAccountName(), "", channel, epg.schedule(account, channel)));
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
                rows.add(new Row(account.getDbId(), account.getAccountName(), bookmark.getDbId(), channel, epg.schedule(account, channel)));
            }
        }
        return List.copyOf(rows);
    }
}
