package com.uiptv.application;

import com.uiptv.db.*;
import com.uiptv.model.*;
import com.uiptv.service.*;
import java.util.List;

/** Resolves player UI database ids before looking up provider guide identifiers. */
public class PlayerEpgApplicationService {
    public record Query(String accountId, String channelId, String categoryId, String bookmarkId) { }
    public record Result(String channelName, EpgProgramme now, EpgProgramme next) { }

    public Result lookup(Query query) {
        if (!new EpgApplicationService().isEnabled() || query == null) return new Result("", null, null);
        Account account;
        Channel channel;
        if (query.bookmarkId() != null && !query.bookmarkId().isBlank()) {
            Bookmark bookmark = BookmarkApplicationService.getInstance().getBookmark(query.bookmarkId());
            if (bookmark == null) return new Result("", null, null);
            var resolved = new BookmarkResolver().resolveBookmarks(List.of(bookmark)).getFirst();
            account = resolved.getAccount();
            if (account == null || resolved.getAccountAction() != Account.AccountAction.itv) return new Result("", null, null);
            channel = ChannelService.getInstance().getChannelByChannelIdAndAccount(bookmark.getChannelId(), account.getDbId(), false);
            if (channel == null) {
                channel=Channel.fromJson(bookmark.getChannelJson());
                if(channel==null) channel=new Channel();
                channel.setChannelId(bookmark.getChannelId()); channel.setName(bookmark.getChannelName());
            }
        } else {
            account = AccountService.getInstance().getById(query.accountId());
            if (account == null || query.categoryId() == null || query.categoryId().isBlank()
                    || CategoryDb.get().getCategoryByDbId(query.categoryId(), account) == null) return new Result("", null, null);
            channel = ChannelDb.get().getChannelById(query.channelId(), query.categoryId());
        }
        return current(account, channel);
    }

    /** Playback snapshots can predate the catalogue metadata used to key the EPG cache. */
    public List<EpgProgramme> schedule(Account account, Channel channel) {
        if (account == null || channel == null || account.getAction() != Account.AccountAction.itv) return List.of();
        Channel catalogChannel = ChannelService.getInstance().getChannelByChannelIdAndAccount(
                channel.getChannelId(), account.getDbId(), false);
        Channel resolved = catalogChannel == null ? channel : catalogChannel;
        if (new EpgApplicationService().isPlaybackOnly()) {
            // Lightweight mode bypasses the scheduler and visible-card prefetch.
            // This is the single on-demand refresh made for the playing channel.
            new EpgCacheRefreshService().refreshAccount(account, List.of(resolved));
        }
        return new EpgCacheApplicationService().schedule(account, resolved);
    }

    public Result current(Account account, Channel channel) {
        if (account == null || channel == null || account.getAction() != Account.AccountAction.itv) return new Result("", null, null);
        List<EpgProgramme> programmes = schedule(account, channel);
        long time = System.currentTimeMillis();
        EpgProgramme now = null;
        EpgProgramme next = null;
        for (EpgProgramme programme : programmes) {
            if (programme.start() <= time && time < programme.stop()) now = programme;
            if (programme.start() > time) { next = programme; break; }
        }
        return new Result(channel.getName(), now, next);
    }
}
