package com.uiptv.db;

import com.uiptv.model.*;
import com.uiptv.service.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BookmarkIdentityLifecycleTest extends DbBackedTest {
    @Test void accountUpdateAndCacheClearPreserveBookmarkProviderIdentitySnapshotsAndHistory() throws Exception {
        Account account = new Account(); account.setAccountName("before"); account.setUrl("http://example.test/");
        AccountService.getInstance().save(account);
        String accountId = account.getDbId();
        Bookmark bookmark = new Bookmark("before", "News", "provider-channel", "Station", "cmd", "portal", "old-cache-category");
        bookmark.setAccountAction(Account.AccountAction.itv);
        bookmark.setChannelJson("{\"channelId\":\"provider-channel\",\"cmd\":\"cmd\",\"logo\":\"saved-logo\"}");
        BookmarkService.getInstance().save(bookmark);
        BookmarkService.getInstance().recordRecentPlayback(bookmark);
        account.setUsername("updated");
        AccountService.getInstance().save(account);
        assertEquals(accountId, account.getDbId());
        assertEquals(1, AccountService.getInstance().getAll().size());
        Bookmark saved = BookmarkService.getInstance().read().getFirst();
        assertEquals("before", saved.getAccountName());
        assertEquals("provider-channel", saved.getChannelId());
        assertEquals(bookmark.getChannelJson(), saved.getChannelJson());
        assertEquals("before", BookmarkService.getInstance().readRecentBookmarks().getFirst().getAccountName());
        // Missing cache rows are expected after a clear: saved bookmarks remain valid.
        ConfigurationDb.get().clearCache(account);
        assertEquals(1, BookmarkService.getInstance().read().size());
        assertNotNull(new BookmarkResolver().resolveBookmarks(BookmarkService.getInstance().read()).getFirst().getAccount());
        AccountService.getInstance().delete(accountId);
        assertTrue(BookmarkService.getInstance().read().isEmpty());
        try (var connection = SQLConnection.connect(); var statement = connection.createStatement()) {
            for (String table : java.util.List.of("BookmarkOrder", "BookmarkPlayHistory")) {
                try (var rows = statement.executeQuery("SELECT count(*) FROM " + table)) {
                    assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
                }
            }
        }
    }

    @Test void deletingByBookmarkIdentityAlsoDeletesOrderRows() throws Exception {
        Bookmark bookmark = new Bookmark("account", "News", "provider-channel", "Station", "cmd", "portal", "category");
        BookmarkService.getInstance().save(bookmark);
        Bookmark identityOnly = new Bookmark("account", "News", "provider-channel", "Station", "cmd", "portal", "category");
        BookmarkDb.get().delete(identityOnly);
        try (var connection = SQLConnection.connect(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM BookmarkOrder")) {
            assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
        }
        assertTrue(BookmarkService.getInstance().read().isEmpty());
    }

    @Test void failedBookmarkDeleteRollsBackOrderDeleteAndStopsAccountDeletion() throws Exception {
        Account account = new Account(); account.setAccountName("protected"); account.setUrl("http://example.test/");
        AccountService.getInstance().save(account);
        Bookmark bookmark = new Bookmark("protected", "News", "provider-channel", "Station", "cmd", "portal", "category");
        BookmarkService.getInstance().save(bookmark);
        String bookmarkId = bookmark.getDbId();
        try (var connection = SQLConnection.connect(); var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_bookmark_delete BEFORE DELETE ON Bookmark BEGIN SELECT RAISE(ABORT,'test failure'); END");
        }
        BookmarkDb bookmarkDb = BookmarkDb.get();
        AccountService accountService = AccountService.getInstance();
        assertThrows(DatabaseAccessException.class, () -> bookmarkDb.delete(bookmarkId));
        String accountIdForDelete = account.getDbId();
        assertThrows(IllegalStateException.class, () -> accountService.delete(accountIdForDelete));
        assertNotNull(AccountService.getInstance().getById(account.getDbId()));
        assertEquals(1, BookmarkService.getInstance().read().size());
        try (var connection = SQLConnection.connect(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM BookmarkOrder")) {
            assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
        }
    }

    @Test void deletingAUserGroupMovesBookmarksToAllAndKeepsSourceSnapshot() throws Exception {
        BookmarkService service = BookmarkService.getInstance();
        service.addCategory(new BookmarkCategory(null, "Favorites"));
        BookmarkCategory group = service.getAllCategories().getFirst();
        Bookmark bookmark = new Bookmark("account", "Provider news", "provider-channel", "Station", "cmd", "portal", group.getId());
        bookmark.setCategoryJson("{\"categoryId\":\"provider-news\",\"title\":\"Provider news\"}");
        service.save(bookmark);
        service.removeCategory(group);
        Bookmark saved = service.read().getFirst();
        assertTrue(saved.getCategoryId() == null || saved.getCategoryId().isBlank());
        assertEquals("provider-channel", saved.getChannelId());
        assertEquals(bookmark.getCategoryJson(), saved.getCategoryJson());
        assertTrue(service.getAllCategories().isEmpty());
        try (var connection = SQLConnection.connect(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM BookmarkOrder WHERE category_id='" + group.getId() + "'")) {
            assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
        }
    }
}
