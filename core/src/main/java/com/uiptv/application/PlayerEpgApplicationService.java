package com.uiptv.application;

import com.uiptv.db.*;
import com.uiptv.model.*;
import com.uiptv.service.*;
import java.util.List;

/** Resolves player UI database ids before looking up provider guide identifiers. */
public class PlayerEpgApplicationService {
    public record Query(String accountId, String channelId, String categoryId, String bookmarkId) { }
    public record Result(String channelName, EpgProgramme now, EpgProgramme next, String guideStatus) {
        public Result(String channelName, EpgProgramme now, EpgProgramme next) {
            this(channelName, now, next, "");
        }
    }
    private record Resolved(Account account, Channel channel) { }

    public record GuideResult(Result summary, List<EpgProgramme> programmes) { }
    public GuideResult lookupGuide(Query query) {
        if (!new EpgApplicationService().isEnabled() || query == null) return new GuideResult(new Result("", null, null), List.of());
        Resolved resolved = query.bookmarkId() != null && !query.bookmarkId().isBlank()
                ? resolveBookmark(query.bookmarkId()) : resolveChannel(query);
        if (resolved == null || resolved.channel() == null) return new GuideResult(new Result("", null, null), List.of());
        resolved.account().setAction(Account.AccountAction.itv);
        var programmes = new EpgApplicationService().schedule(resolved.account(), resolved.channel());
        return new GuideResult(summarize(resolved.channel().getName(), programmes), programmes);
    }
    public Result lookup(Query query) { return lookupGuide(query).summary(); }
    public List<EpgProgramme> lookupSchedule(Query query) { return lookupGuide(query).programmes(); }

    private Resolved resolveBookmark(String bookmarkId) {
        Bookmark bookmark = BookmarkApplicationService.getInstance().getBookmark(bookmarkId);
        if (bookmark == null) return null;
        var resolved = new BookmarkResolver().resolveBookmarks(List.of(bookmark)).getFirst();
        Account account = resolved.getAccount();
        if (account == null || resolved.getAccountAction() != Account.AccountAction.itv) return null;
        Channel channel = ChannelService.getInstance().getChannelByChannelIdAndAccount(bookmark.getChannelId(), account.getDbId(), false);
        if (channel == null) {
            channel = Channel.fromJson(bookmark.getChannelJson());
            if (channel == null) channel = new Channel();
            channel.setChannelId(bookmark.getChannelId());
            channel.setName(bookmark.getChannelName());
        }
        return new Resolved(account, channel);
    }

    private Resolved resolveChannel(Query query) {
        Account account = AccountService.getInstance().getById(query.accountId());
        if (account == null || query.categoryId() == null || query.categoryId().isBlank()
                || CategoryDb.get().getCategoryByDbId(query.categoryId(), account) == null) return null;
        return new Resolved(account, ChannelDb.get().getChannelById(query.channelId(), query.categoryId()));
    }

    /** Playback snapshots can predate the catalogue metadata used to key the EPG cache. */
    public List<EpgProgramme> schedule(Account account, Channel channel) {
        if (account == null || channel == null || account.getAction() != Account.AccountAction.itv) return List.of();
        return new EpgApplicationService().schedule(account, catalogueChannel(account, channel));
    }

    /** Called by the player only while a user-started stream context is active. */
    public Result currentForPlayback(Account account, Channel channel) {
        if (account == null || channel == null || account.getAction() != Account.AccountAction.itv) return new Result("", null, null);
        Channel resolved = catalogueChannel(account, channel);
        return current(account, resolved);
    }

    public Result current(Account account, Channel channel) {
        if (account == null || channel == null || account.getAction() != Account.AccountAction.itv) return new Result("", null, null);
        Channel resolved = catalogueChannel(account, channel);
        List<EpgProgramme> programmes = new EpgApplicationService().schedule(account, resolved);
        return summarize(resolved.getName(), programmes);
    }

    private Result summarize(String name, List<EpgProgramme> programmes) {
        long time = System.currentTimeMillis();
        EpgProgramme now = null;
        EpgProgramme next = null;
        for (EpgProgramme programme : programmes) {
            if (programme.start() <= time && time < programme.stop()) now = programme;
            if (programme.start() > time) { next = programme; break; }
        }
        boolean hasGuide = now != null || next != null;
        String status = hasGuide ? "AVAILABLE" : "NO_MATCH";
        return new Result(name, now, next, status);
    }

    private Channel catalogueChannel(Account account, Channel channel) {
        Channel catalogChannel = ChannelService.getInstance().getChannelByChannelIdAndAccount(
                channel.getChannelId(), account.getDbId(), false);
        return catalogChannel == null ? channel : catalogChannel;
    }
}
