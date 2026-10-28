package com.uiptv.db;

import com.uiptv.application.RecentPlaybackApplicationService;
import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.service.BookmarkService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecentPlaybackHistoryTest extends com.uiptv.service.DbBackedTest {
    @Test void channelListPlaybackIsReplayableWithoutSavingAFavourite() {
        Account account = new Account();
        account.setAccountName("provider"); account.setAction(Account.AccountAction.itv);
        Channel channel = new Channel();
        channel.setChannelId("provider-channel"); channel.setDbId("17");
        channel.setName("News"); channel.setCmd("https://example.test/live");
        channel.setDrmType("clearkey"); channel.setClearKeysJson("{\"kid\":\"key\"}");
        Category category = new Category(); category.setCategoryId("provider-category"); category.setDbId("29");
        new RecentPlaybackApplicationService().recordRecent(account, channel, category);

        assertTrue(BookmarkService.getInstance().read().isEmpty());
        Bookmark recent = BookmarkService.getInstance().readRecentBookmarks().getFirst();
        assertTrue(BookmarkPlayHistoryDb.isRecentId(recent.getDbId()));
        Bookmark replay = BookmarkService.getInstance().getBookmark(recent.getDbId());
        assertEquals("provider-channel", replay.getChannelId());
        assertEquals(channel.getCmd(), replay.getCmd());
        assertEquals(channel.getClearKeysJson(), replay.getClearKeysJson());
        assertEquals("29", Category.fromJson(replay.getCategoryJson()).getDbId());
        assertEquals("provider-channel", Channel.fromJson(replay.getChannelJson()).getChannelId());
    }

    @Test void favouriteAndChannelPlaybackShareCountAndUnbookmarkingKeepsHistory() throws Exception {
        Bookmark item = new Bookmark("provider", "News", "one", "One", "https://example.test/1", "", null);
        BookmarkService service = BookmarkService.getInstance();
        service.recordRecentPlayback(item);
        service.save(item);
        service.recordRecentPlayback(item);
        assertEquals(1, service.readRecentBookmarks().size());
        try (var connection = SQLConnection.connect(); var statement = connection.createStatement();
             var row = statement.executeQuery("SELECT playCount FROM BookmarkPlayHistory")) {
            assertTrue(row.next()); assertEquals(2, row.getInt(1));
        }
        service.remove(item.getDbId());
        assertTrue(service.read().isEmpty());
        assertEquals("One", service.readRecentBookmarks().getFirst().getChannelName());
        service.save(service.readRecentBookmarks().getFirst());
        service.removeRecentBookmark(item);
        assertTrue(service.readRecentBookmarks().isEmpty());
        assertEquals(1, service.read().size());
    }

    @Test void identicalProviderIdsStaySeparateAcrossAccountsAndModes() {
        for (String account : java.util.List.of("a", "b")) {
            for (Account.AccountAction action : java.util.List.of(Account.AccountAction.itv, Account.AccountAction.vod)) {
                Bookmark item = new Bookmark(account, "", "same", action.name(), "cmd", "", null);
                item.setAccountAction(action);
                BookmarkPlayHistoryDb.recordPlayback(item);
            }
        }
        assertEquals(4, BookmarkPlayHistoryDb.countRecentBookmarks());
    }

    @Test void removingLegacyFavouritePreservesHistoryWithoutIncrementingCounter() throws Exception {
        Bookmark item = new Bookmark("legacy", "", "one", "Legacy", "cmd", "", null);
        BookmarkService service = BookmarkService.getInstance();
        service.save(item);
        try (var connection = SQLConnection.connect(); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO BookmarkPlayHistory(accountName, accountAction, channelId, playCount, lastPlayedAt) VALUES('legacy','itv','one',7,1)");
        }
        service.remove(item.getDbId());
        assertEquals("Legacy", service.readRecentBookmarks().getFirst().getChannelName());
        try (var connection = SQLConnection.connect(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT playCount FROM BookmarkPlayHistory")) {
            assertTrue(rows.next()); assertEquals(7, rows.getInt(1));
        }
    }
}
