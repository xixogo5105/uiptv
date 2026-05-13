package com.uiptv.application;

import com.uiptv.db.CategoryDb;
import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.BookmarkCategory;
import com.uiptv.model.Category;
import com.uiptv.service.AccountService;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookmarkApplicationServiceTest extends DbBackedTest {

    private final BookmarkApplicationService service = BookmarkApplicationService.getInstance();

    @Test
    void saveBookmarkDetectsDuplicatesAndListPagination() {
        Account account = createAccount("bookmark-save");
        CategoryDb.get().saveAll(List.of(new Category("cat-api", "News", "news", false, 0)), account);
        Category category = CategoryDb.get().getCategories(account).getFirst();

        BookmarkSaveRequest request = new BookmarkSaveRequest(
                account.getDbId(), category.getDbId(), CatalogMode.ITV, "ch-1", "Channel One", "http://stream/1",
                "logo", "widevine", "http://license", "{}", "inputstream", "hls"
        );

        BookmarkSaveResult saved = service.saveBookmark(request);
        BookmarkSaveResult duplicate = service.saveBookmark(request);

        assertEquals("saved", saved.action());
        assertEquals("exists", duplicate.action());
        assertEquals(1, service.listBookmarks(0, 10).size());
        assertTrue(service.listBookmarks(99, 10).isEmpty());
        assertEquals(1, service.listBookmarks(-5, 0).size());
        assertNull(service.saveBookmark(null));
        assertNull(service.saveBookmark(new BookmarkSaveRequest(account.getDbId(), category.getDbId(), CatalogMode.ITV, "", "", "", "", "", "", "", "", "")));
    }

    @Test
    void buildPlaylistGroupsUncategorizedAndNamedCategories() {
        Account account = createAccount("bookmark-playlist");
        BookmarkService bookmarkService = BookmarkService.getInstance();
        bookmarkService.addCategory(new BookmarkCategory("favorites", "Favorites"));
        BookmarkCategory savedCategory = bookmarkService.getAllCategories().getFirst();

        Bookmark uncategorized = new Bookmark(account.getAccountName(), "", "ch-1", "Uncategorized", "cmd", "http://portal", "");
        Bookmark categorized = new Bookmark(account.getAccountName(), "Favorites", "ch-2", "Categorized", "cmd", "http://portal", savedCategory.getId());
        bookmarkService.save(uncategorized);
        bookmarkService.save(categorized);

        String playlist = service.buildPlaylist("127.0.0.1:8888");

        assertTrue(playlist.startsWith("#EXTM3U"));
        assertTrue(playlist.contains("group-title=\"Misc\""));
        assertTrue(playlist.contains("group-title=\"Favorites\""));
        assertTrue(playlist.contains("/bookmarkEntry.ts?bookmarkId="));
        assertEquals(1, service.listCategories().size());
        assertNotNull(service.getBookmark(BookmarkService.getInstance().read().getFirst().getDbId()));

        Map<String, Integer> orders = Map.of(BookmarkService.getInstance().read().getFirst().getDbId(), 3);
        service.saveBookmarkOrders(orders);
        service.deleteBookmark(BookmarkService.getInstance().read().getFirst().getDbId());
    }

    private Account createAccount(String name) {
        Account account = new Account(name, "user", "pass", "http://test/xtreme", null, null, null, null, null, null,
                AccountType.XTREME_API, null, "http://test/xtreme", false);
        AccountService.getInstance().save(account);
        return AccountService.getInstance().getByName(name);
    }
}
