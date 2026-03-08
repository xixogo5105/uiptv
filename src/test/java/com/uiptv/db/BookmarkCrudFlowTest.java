package com.uiptv.db;

import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.BookmarkCategory;
import com.uiptv.model.Channel;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.DbBackedTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.uiptv.db.SQLConnection.connect;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookmarkCrudFlowTest extends DbBackedTest {

    @Test
    void testBookmarkServiceCrudAndJsonFlow() {
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
    void testBookmarkCategoryAndOrderingFlow() {
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
        bookmarkService.saveBookmarkOrder(reversedIds);
        List<Bookmark> reordered = bookmarkService.getBookmarksByCategory("cat-order");
        assertEquals(List.of("Three", "Two", "One"), reordered.stream().map(Bookmark::getChannelName).collect(Collectors.toList()));

        bookmarkService.saveBookmarkOrder(List.of(reordered.get(0).getDbId(), reordered.get(1).getDbId(), reordered.get(2).getDbId()));
        List<Bookmark> allBookmarks = bookmarkService.read();
        assertEquals(3, allBookmarks.size());
        assertEquals(List.of("Three", "Two", "One"), allBookmarks.stream().map(Bookmark::getChannelName).collect(Collectors.toList()));
        assertEquals(List.of(1, 2, 3), readDisplayOrders(allBookmarks.stream().map(Bookmark::getDbId).collect(Collectors.toList())));

        bookmarkDb.deleteBookmarkOrder(reordered.get(0).getDbId(), "cat-order");
        assertEquals(3, bookmarkService.getBookmarksByCategory("cat-order").size());
        bookmarkDb.saveBookmarkOrder(reordered.get(0).getDbId(), 1);
        assertEquals(3, bookmarkService.getBookmarksByCategory("cat-order").size());

        Bookmark b4 = new Bookmark("acc-order", "Other", "ch-4", "Four", "cmd://4", "http://portal", "cat-other");
        bookmarkService.save(b4);
        List<Bookmark> globalOrder = bookmarkService.read();
        assertEquals(List.of("Three", "Two", "One", "Four"), globalOrder.stream().map(Bookmark::getChannelName).collect(Collectors.toList()));

        bookmarkService.removeCategory(savedCategory);
        assertFalse(bookmarkService.getAllCategories().stream().anyMatch(c -> "My Favorites".equals(c.getName())));
    }

    @Test
    void testBookmarkChangeRevisionIncrementsOnMutations() {
        BookmarkService bookmarkService = BookmarkService.getInstance();

        long revisionStart = bookmarkService.getChangeRevision();

        Bookmark bookmark = new Bookmark("acc-rev", "Fav", "ch-rev-1", "Revision One", "cmd://rev-1", "http://portal", "cat-rev");
        bookmarkService.save(bookmark);
        long afterSave = bookmarkService.getChangeRevision();
        assertTrue(afterSave > revisionStart);

        Bookmark saved = bookmarkService.getBookmark(bookmark);
        assertNotNull(saved);
        bookmarkService.saveBookmarkOrder(List.of(saved.getDbId()));
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
    void testUpdatingBookmarkDoesNotChangeGlobalOrder() {
        BookmarkService bookmarkService = BookmarkService.getInstance();

        Bookmark first = new Bookmark("acc-update", "Fav", "ch-1", "One", "cmd://1", "http://portal", "cat-a");
        Bookmark second = new Bookmark("acc-update", "Fav", "ch-2", "Two", "cmd://2", "http://portal", "cat-b");
        bookmarkService.save(first);
        bookmarkService.save(second);

        List<Bookmark> initial = bookmarkService.read();
        assertEquals(List.of("One", "Two"), initial.stream().map(Bookmark::getChannelName).collect(Collectors.toList()));

        Bookmark savedFirst = bookmarkService.getBookmark(first);
        assertNotNull(savedFirst);
        savedFirst.setCmd("cmd://1-updated");
        bookmarkService.save(savedFirst);

        List<Bookmark> afterUpdate = bookmarkService.read();
        assertEquals(List.of("One", "Two"), afterUpdate.stream().map(Bookmark::getChannelName).collect(Collectors.toList()));
        assertEquals(List.of(1, 2), readDisplayOrders(afterUpdate.stream().map(Bookmark::getDbId).collect(Collectors.toList())));
    }

    @Test
    void testDeletingBookmarkRemovesItsOrderRows() {
        BookmarkService bookmarkService = BookmarkService.getInstance();

        Bookmark bookmark = new Bookmark("acc-delete-order", "Fav", "ch-1", "One", "cmd://1", "http://portal", "cat-a");
        bookmarkService.save(bookmark);

        Bookmark saved = bookmarkService.getBookmark(bookmark);
        assertNotNull(saved);
        assertEquals(1, countBookmarkOrderRows(saved.getDbId()));

        bookmarkService.remove(saved.getDbId());
        assertEquals(0, countBookmarkOrderRows(saved.getDbId()));
    }

    @Test
    void testGlobalOrderRemainsConsistentWhenCategoryViewsFilterIt() {
        BookmarkService bookmarkService = BookmarkService.getInstance();

        Bookmark a1 = new Bookmark("acc-global", "Fav", "ch-1", "A One", "cmd://1", "http://portal", "cat-a");
        Bookmark b1 = new Bookmark("acc-global", "Fav", "ch-2", "B One", "cmd://2", "http://portal", "cat-b");
        Bookmark a2 = new Bookmark("acc-global", "Fav", "ch-3", "A Two", "cmd://3", "http://portal", "cat-a");
        bookmarkService.save(a1);
        bookmarkService.save(b1);
        bookmarkService.save(a2);

        List<Bookmark> saved = bookmarkService.read();
        bookmarkService.saveBookmarkOrder(List.of(saved.get(2).getDbId(), saved.get(1).getDbId(), saved.get(0).getDbId()));

        assertEquals(
                List.of("A Two", "B One", "A One"),
                bookmarkService.read().stream().map(Bookmark::getChannelName).collect(Collectors.toList())
        );
        assertEquals(
                List.of("A Two", "A One"),
                bookmarkService.getBookmarksByCategory("cat-a").stream().map(Bookmark::getChannelName).collect(Collectors.toList())
        );
        assertEquals(
                List.of("B One"),
                bookmarkService.getBookmarksByCategory("cat-b").stream().map(Bookmark::getChannelName).collect(Collectors.toList())
        );
    }

    @Test
    void testBookmarkDbDeleteAndUpdateFlow() {
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
    void testBookmarkIdentityLookupSupportsAllCategoryViews() {
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

    private List<Integer> readDisplayOrders(List<String> bookmarkIds) {
        String placeholders = bookmarkIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = "SELECT display_order FROM BookmarkOrder WHERE bookmark_db_id IN (" + placeholders + ") ORDER BY display_order ASC";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            for (int i = 0; i < bookmarkIds.size(); i++) {
                statement.setString(i + 1, bookmarkIds.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Integer> displayOrders = new java.util.ArrayList<>();
                while (resultSet.next()) {
                    displayOrders.add(resultSet.getInt(1));
                }
                return displayOrders;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private int countBookmarkOrderRows(String bookmarkId) {
        String sql = "SELECT COUNT(*) FROM BookmarkOrder WHERE bookmark_db_id = ?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, bookmarkId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
