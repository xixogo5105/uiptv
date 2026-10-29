package com.uiptv.application;

import com.uiptv.db.CategoryDb;
import com.uiptv.db.VodCategoryDb;
import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.BookmarkCategory;
import com.uiptv.model.Category;
import com.uiptv.service.AccountService;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.util.AccountType;
import com.uiptv.util.XtremeApiParser;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

class BookmarkApplicationServiceTest extends DbBackedTest {

    private final BookmarkApplicationService service = BookmarkApplicationService.getInstance();

    @Test
    void cachedVodAndSeriesBookmarksKeepTheirOwnProviderCategorySnapshot() {
        for (CatalogMode mode : List.of(CatalogMode.VOD, CatalogMode.SERIES)) {
            Account account = createAccount("bookmark-cached-" + mode);
            Category category = new Category("provider-" + mode, "Titles", "Titles", false, 0);
            if (mode == CatalogMode.VOD) {
                account.setAction(Account.AccountAction.vod);
                VodCategoryDb.get().saveAll(List.of(category), account);
                category = VodCategoryDb.get().getCategories(account).getFirst();
            } else {
                account.setAction(Account.AccountAction.series);
                com.uiptv.db.SeriesCategoryDb.get().saveAll(List.of(category), account);
                category = com.uiptv.db.SeriesCategoryDb.get().getCategories(account).getFirst();
            }
            BookmarkSaveResult saved = service.saveBookmark(new BookmarkSaveRequest(
                    account.getDbId(), category.getDbId(), mode, "item", "Title", "http://stream/item",
                    "", "", "", "", "", ""));
            assertEquals(category.getCategoryId(), Category.fromJson(
                    service.getBookmark(saved.bookmarkId()).getCategoryJson()).getCategoryId());
        }
    }

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
        assertEquals("logo", service.listBookmarks(0, 10).getFirst().getLogo());
        Bookmark bookmark = service.getBookmark(saved.bookmarkId());
        assertTrue(bookmark.getCategoryId() == null || bookmark.getCategoryId().isBlank());
        assertEquals(category.getCategoryId(), Category.fromJson(bookmark.getCategoryJson()).getCategoryId());
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

    @Test
    void saveBookmarkUsesTheSelectedFavoritesCategory() {
        Account account = createAccount("bookmark-category-save");
        BookmarkService bookmarkService = BookmarkService.getInstance();
        bookmarkService.addCategory(new BookmarkCategory(null, "Sports"));
        BookmarkCategory destination = bookmarkService.getAllCategories().getFirst();

        BookmarkSaveRequest request = new BookmarkSaveRequest(
                account.getDbId(), "", CatalogMode.ITV, "ch-category", "Sports Channel", "http://stream/category",
                "", "", "", "", "", "", destination.getId()
        );

        BookmarkSaveResult saved = service.saveBookmark(request);

        assertEquals("saved", saved.action());
        assertEquals(destination.getId(), service.getBookmark(saved.bookmarkId()).getCategoryId());
        assertNull(service.saveBookmark(new BookmarkSaveRequest(
                account.getDbId(), "", CatalogMode.ITV, "ch-invalid-category", "Invalid Destination", "cmd",
                "", "", "", "", "", "", "missing-category"
        )));
    }

    @Test
    void saveBookmarkResolvesLiveCategoryAndChannelSnapshotWhenCatalogTablesAreEmpty() {
        Account account = createAccount("bookmark-live-catalog");
        account.setCacheEnabled(false);
        AccountService.getInstance().save(account);
        Category providerCategory = new Category("vod-cat", "Movies", "movies", false, 0);

        BookmarkSaveResult saved;
        try (MockedStatic<XtremeApiParser> parser = mockStatic(XtremeApiParser.class)) {
            parser.when(() -> XtremeApiParser.parseCategories(any(Account.class))).thenReturn(List.of(providerCategory));
            saved = service.saveBookmark(new BookmarkSaveRequest(
                    account.getDbId(), "vod-cat", CatalogMode.VOD, "movie-42", "Current Movie", "cmd://movie-42",
                    "https://img.example/movie.jpg", "", "", "", "", ""
            ));
        }

        assertEquals("saved", saved.action());
        assertTrue(VodCategoryDb.get().getCategories(accountWithVodMode(account)).isEmpty());
        assertTrue(CategoryDb.get().getAllAccountCategories(account.getDbId()).isEmpty());
        Bookmark bookmark = service.getBookmark(saved.bookmarkId());
        assertEquals("Movies", bookmark.getCategoryTitle());
        assertEquals("vod-cat", Category.fromJson(bookmark.getCategoryJson()).getCategoryId());
        assertEquals("movie-42", com.uiptv.model.Channel.fromJson(bookmark.getChannelJson()).getChannelId());
        assertEquals("https://img.example/movie.jpg", service.listBookmarks(0, 10).getFirst().getLogo());
    }

    private Account createAccount(String name) {
        Account account = new Account(name, "user", "pass", "http://test/xtreme", null, null, null, null, null, null,
                AccountType.XTREME_API, null, "http://test/xtreme", false);
        AccountService.getInstance().save(account);
        return AccountService.getInstance().getByName(name);
    }

    private Account accountWithVodMode(Account account) {
        account.setAction(Account.AccountAction.vod);
        return account;
    }
}
