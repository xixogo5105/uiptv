package com.uiptv.application;

import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.service.BookmarkService;

/** Records replayable channel metadata without creating a saved favourite. */
public class RecentPlaybackApplicationService {
    public void record(Account account, Channel channel, Category sourceCategory) {
        if (account == null || channel == null) return;
        Bookmark item = new Bookmark(account.getAccountName(), sourceCategory == null ? "" : sourceCategory.getTitle(),
                channel.getChannelId(), channel.getName(), channel.getCmd(), account.getServerPortalUrl(), null);
        item.setAccountAction(account.getAction());
        item.setFromChannel(channel);
        item.setChannelJson(channel.toJson());
        if (sourceCategory != null) item.setCategoryJson(sourceCategory.toJson());
        BookmarkService.getInstance().recordRecentPlayback(item);
    }
}
