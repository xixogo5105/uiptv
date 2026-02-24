package com.uiptv.db;

import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.BookmarkCategory;
import com.uiptv.model.Channel;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BookmarkCrudFlowTest extends DbBackedTest {

    @Test
    public void testBookmarkServiceCrudAndJsonFlow() {
        BookmarkService bookmarkService = BookmarkService.getInstance();
        BookmarkDb bookmarkDb = BookmarkDb.get();

        assertEquals("[]", bookmarkService.readToJson());

        Bookmark bookmark = new Bookmark("acc-flow", "Sports", "ch-1", "Sports One", "ffmpeg http://stream/1.ts", "http://portal", "cat-1");
        bookmark.setAccountAction(Account.AccountAction.itv);
        bookmark.setCategoryJson("{\"id\":\"cat-1\"}");
        bookmark.setChannelJson("{\"id\":\"ch-1\"}");
        bookmark.setVodJson("{\"vod\":\"1\"}");
        bookmark.setSeriesJson("{\"series\":\"1\"}");
        bookmark.setFromChannel(new Channel("ch-1", "Sports One", "1", "cmd", null, null, null, "logo", 0, 1, 1, "widevine", "http://license", Map.of("kid", "key"), "addon", "hls"));

        bookmarkService.save(bookmark);
        assertTrue(bookmarkService.isChannelBookmarked(bookmark));

        Bookmark fetchedByKey = bookmarkService.getBookmark(bookmark);
        assertNotNull(fetchedByKey);
        assertNotNull(fetchedByKey.getDbId());
        assertEquals("widevine", fetchedByKey.getDrmType());
        assertEquals("http://license", fetchedByKey.getDrmLicenseUrl());
        assertEquals("addon", fetchedByKey.getInputstreamaddon());
        assertEquals("hls", fetchedByKey.getManifestType());
        assertEquals(Account.AccountAction.itv, fetchedByKey.getAccountAction());

        Bookmark fetchedById = bookmarkService.getBookmark(fetchedByKey.getDbId());
        assertNotNull(fetchedById);
        assertEquals("Sports One", fetchedById.getChannelName());

        String json = bookmarkService.readToJson();
        assertTrue(json.startsWith("["));
        assertTrue(json.contains("Sports One"));

        bookmarkService.toggleBookmark(bookmark);
        assertFalse(bookmarkService.isChannelBookmarked(bookmark));
        assertEquals("[]", bookmarkService.readToJson());

        bookmarkService.toggleBookmark(bookmark);
        assertTrue(bookmarkService.isChannelBookmarked(bookmark));

        Bookmark afterToggleAdd = bookmarkDb.getBookmarkById(bookmark);
        assertNotNull(afterToggleAdd);
        bookmarkService.remove(afterToggleAdd.getDbId());
        assertFalse(bookmarkService.isChannelBookmarked(bookmark));
    }

    @Test
    public void testBookmarkCategoryAndOrderingFlow() {
        BookmarkService bookmarkService = BookmarkService.getInstance();
        BookmarkDb bookmarkDb = BookmarkDb.get();

        BookmarkCategory newCategory = new BookmarkCategory(null, "My Favorites");
        bookmarkService.addCategory(newCategory);

        List<BookmarkCategory> allCategories = bookmarkService.getAllCategories();
        assertTrue(allCategories.stream().anyMatch(c -> "My Favorites".equals(c.getName())));
        BookmarkCategory savedCategory = allCategories.stream().filter(c -> "My Favorites".equals(c.getName())).findFirst().orElseThrow();
        assertNotNull(savedCategory.getId());

        Bookmark b1 = new Bookmark("acc-order", "Fav", "ch-1", "One", "cmd://1", "http://portal", "cat-order");
        Bookmark b2 = new Bookmark("acc-order", "Fav", "ch-2", "Two", "cmd://2", "http://portal", "cat-order");
        Bookmark b3 = new Bookmark("acc-order", "Fav", "ch-3", "Three", "cmd://3", "http://portal", "cat-order");
        bookmarkService.save(b1);
        bookmarkService.save(b2);
        bookmarkService.save(b3);

        List<Bookmark> initial = bookmarkService.getBookmarksByCategory("cat-order");
        assertEquals(3, initial.size());
        assertEquals(List.of("One", "Two", "Three"), initial.stream().map(Bookmark::getChannelName).collect(Collectors.toList()));

        List<String> reversedIds = List.of(initial.get(2).getDbId(), initial.get(1).getDbId(), initial.get(0).getDbId());
        bookmarkService.saveBookmarkOrder("cat-order", reversedIds);
        List<Bookmark> reordered = bookmarkService.getBookmarksByCategory("cat-order");
        assertEquals(List.of("Three", "Two", "One"), reordered.stream().map(Bookmark::getChannelName).collect(Collectors.toList()));

        bookmarkService.saveBookmarkOrder(null, List.of(reordered.get(0).getDbId(), reordered.get(1).getDbId(), reordered.get(2).getDbId()));
        List<Bookmark> allBookmarks = bookmarkService.read();
        assertEquals(6, allBookmarks.size());

        bookmarkDb.deleteBookmarkOrder(reordered.get(0).getDbId(), "cat-order");
        assertEquals(2, bookmarkService.getBookmarksByCategory("cat-order").size());
        bookmarkDb.saveBookmarkOrder(reordered.get(0).getDbId(), "cat-order", 0);
        assertEquals(3, bookmarkService.getBookmarksByCategory("cat-order").size());

        bookmarkDb.deleteBookmarkOrdersByCategory("cat-order");
        assertTrue(bookmarkService.getBookmarksByCategory("cat-order").isEmpty());

        bookmarkDb.saveBookmarkOrder(reordered.get(0).getDbId(), null, 0);
        bookmarkDb.deleteBookmarkOrder(reordered.get(0).getDbId(), null);
        bookmarkDb.deleteBookmarkOrdersByCategory(null);

        bookmarkService.removeCategory(savedCategory);
        assertFalse(bookmarkService.getAllCategories().stream().anyMatch(c -> "My Favorites".equals(c.getName())));
    }

    @Test
    public void testBookmarkChangeRevisionIncrementsOnMutations() {
        BookmarkService bookmarkService = BookmarkService.getInstance();

        long revisionStart = bookmarkService.getChangeRevision();

        Bookmark bookmark = new Bookmark("acc-rev", "Fav", "ch-rev-1", "Revision One", "cmd://rev-1", "http://portal", "cat-rev");
        bookmarkService.save(bookmark);
        long afterSave = bookmarkService.getChangeRevision();
        assertTrue(afterSave > revisionStart);

        Bookmark saved = bookmarkService.getBookmark(bookmark);
        assertNotNull(saved);
        bookmarkService.saveBookmarkOrder("cat-rev", List.of(saved.getDbId()));
        long afterReorder = bookmarkService.getChangeRevision();
        assertTrue(afterReorder > afterSave);

        BookmarkCategory category = new BookmarkCategory(null, "Rev Category");
        bookmarkService.addCategory(category);
        long afterAddCategory = bookmarkService.getChangeRevision();
        assertTrue(afterAddCategory > afterReorder);

        BookmarkCategory savedCategory = bookmarkService.getAllCategories().stream()
                .filter(c -> "Rev Category".equals(c.getName()))
                .findFirst()
                .orElseThrow();
        bookmarkService.removeCategory(savedCategory);
        long afterRemoveCategory = bookmarkService.getChangeRevision();
        assertTrue(afterRemoveCategory > afterAddCategory);

        bookmarkService.remove(saved.getDbId());
        long afterRemove = bookmarkService.getChangeRevision();
        assertTrue(afterRemove > afterRemoveCategory);
    }

    @Test
    public void testBookmarkDbDeleteAndUpdateFlow() {
        BookmarkDb bookmarkDb = BookmarkDb.get();

        Bookmark first = new Bookmark("acc-delete", "Movies", "m-1", "Movie One", "cmd://movie-1", "http://portal", "cat-movie");
        first.setAccountAction(Account.AccountAction.vod);
        first.setDrmType("widevine");
        first.setDrmLicenseUrl("http://license/1");
        first.setClearKeysJson("{\"kid\":\"key\"}");
        first.setInputstreamaddon("addon-v1");
        first.setManifestType("dash");
        first.setCategoryJson("{\"cat\":\"movie\"}");
        first.setChannelJson("{\"channel\":\"m-1\"}");
        first.setVodJson("{\"vod\":\"m-1\"}");
        first.setSeriesJson("{\"series\":\"m-1\"}");
        bookmarkDb.save(first);

        Bookmark fromDb = bookmarkDb.getBookmarkById(first);
        assertNotNull(fromDb);
        assertEquals(Account.AccountAction.vod, fromDb.getAccountAction());
        assertEquals("dash", fromDb.getManifestType());

        first.setCmd("cmd://movie-1-updated");
        bookmarkDb.save(first);
        Bookmark updated = bookmarkDb.getBookmarkById(first);
        assertNotNull(updated);
        assertEquals("cmd://movie-1-updated", updated.getCmd());
        assertEquals(1, bookmarkDb.getBookmarksByCategory("cat-movie").size());

        Bookmark second = new Bookmark("acc-delete", "Series", "s-1", "Series One", "cmd://series-1", "http://portal", "cat-series");
        bookmarkDb.save(second);
        assertEquals(2, bookmarkDb.getBookmarks().size());

        Bookmark deleteByComposite = new Bookmark("acc-delete", "Series", "s-1", "Series One", "cmd://series-1", "http://portal", "cat-series");
        bookmarkDb.delete(deleteByComposite);
        assertNull(bookmarkDb.getBookmarkById(deleteByComposite));

        Bookmark bookmarkWithId = bookmarkDb.getBookmarkById(first);
        assertNotNull(bookmarkWithId);
        bookmarkDb.delete(bookmarkWithId);
        assertNull(bookmarkDb.getBookmarkById(first));

        Bookmark otherAccount = new Bookmark("acc-other", "Sports", "sp-1", "Sports One", "cmd://sp-1", "http://portal", "cat-sports");
        bookmarkDb.save(otherAccount);
        assertEquals(1, bookmarkDb.getBookmarks().size());
        assertNull(bookmarkDb.getBookmarkById(new Bookmark("missing", "none", "x", "y", "cmd", "portal", "cat")));

        bookmarkDb.deleteByAccountName("acc-other");
        assertTrue(bookmarkDb.getBookmarks().isEmpty());
    }

    @Test
    public void testBookmarkIdentityLookupSupportsAllCategoryViews() {
        BookmarkService bookmarkService = BookmarkService.getInstance();

        Bookmark sportsBookmark = new Bookmark("acc-all", "Sports", "ch-42", "The Channel", "cmd://42", "http://portal", "cat-sports");
        bookmarkService.save(sportsBookmark);

        assertTrue(bookmarkService.isChannelBookmarked(sportsBookmark));
        assertNotNull(bookmarkService.getBookmark(sportsBookmark));

        Bookmark allCategoryViewBookmark = new Bookmark("acc-all", "All", "ch-42", "The Channel", "cmd://42", "http://portal", "all");
        bookmarkService.toggleBookmark(allCategoryViewBookmark);
        assertTrue(bookmarkService.isChannelBookmarked(sportsBookmark));
        assertNotNull(bookmarkService.getBookmark(allCategoryViewBookmark));
    }
}
