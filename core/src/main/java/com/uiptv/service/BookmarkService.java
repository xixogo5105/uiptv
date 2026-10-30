package com.uiptv.service;

import com.uiptv.db.BookmarkDb;
import com.uiptv.db.BookmarkPlayHistoryDb;
import com.uiptv.model.Bookmark;
import com.uiptv.model.BookmarkCategory;
import com.uiptv.util.AppLog;
import com.uiptv.util.ServerUtils;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

public class BookmarkService {
    private final AtomicLong changeRevision = new AtomicLong(1);
    private final Set<BookmarkChangeListener> changeListeners = new CopyOnWriteArraySet<>();
    private volatile long lastUpdatedEpochMs = System.currentTimeMillis();

    private BookmarkService() {
    }

    public static BookmarkService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public boolean isChannelBookmarked(Bookmark bookmark) {
        return (BookmarkDb.get().getBookmarkById(bookmark) != null);
    }

    public Bookmark getBookmark(String dbId) {
        return BookmarkPlayHistoryDb.isRecentId(dbId) ? BookmarkPlayHistoryDb.getRecentBookmark(dbId) : BookmarkDb.get().getById(dbId);
    }

    public Bookmark getBookmark(Bookmark bookmark) {
        return BookmarkDb.get().getBookmarkById(bookmark);
    }

    public void toggleBookmark(Bookmark bookmark) {
        Bookmark dbBookmark = BookmarkDb.get().getBookmarkById(bookmark);
        if (dbBookmark != null) {
            remove(dbBookmark.getDbId());
        } else {
            save(bookmark);
        }
    }

    public void save(Bookmark bookmark) {
        stampCatalogPolicy(bookmark);
        if (BookmarkPlayHistoryDb.isRecentId(bookmark.getDbId())) bookmark.setDbId(null);
        boolean created = BookmarkDb.get().save(bookmark);
        if (created) {
            int nextDisplayOrder = BookmarkDb.get().getNextDisplayOrder();
            BookmarkDb.get().saveBookmarkOrder(bookmark.getDbId(), nextDisplayOrder);
        }
        touchChange();
    }

    private void stampCatalogPolicy(Bookmark bookmark) {
        org.json.JSONObject snapshot;
        try {
            snapshot = new org.json.JSONObject(bookmark.getCategoryJson());
        } catch (Exception _) {
            snapshot = new org.json.JSONObject();
        }
        if (snapshot.has("catalogCacheEnabledAtSave")) return;
        AccountService accountService = AccountService.getInstance();
        com.uiptv.model.Account account = accountService.getAll().get(bookmark.getAccountName());
        if (account == null) return;
        snapshot.put("catalogCacheEnabledAtSave", account.isCacheEnabled());
        bookmark.setCategoryJson(snapshot.toString());
    }

    public List<Bookmark> read() {
        return BookmarkDb.get().getBookmarks();
    }

    public List<Bookmark> readPage(int offset, int limit) {
        return BookmarkDb.get().getBookmarksPage(offset, limit);
    }

    public List<Bookmark> getBookmarksByCategory(String categoryId) {
        return BookmarkDb.get().getBookmarksByCategory(categoryId);
    }

    public List<Bookmark> getBookmarksByAccountName(String accountName) {
        return BookmarkDb.get().getBookmarksByAccountName(accountName);
    }

    public List<Bookmark> readRecentBookmarks() {
        return BookmarkPlayHistoryDb.readRecentBookmarks();
    }

    public void recordRecentPlayback(Bookmark bookmark) {
        BookmarkPlayHistoryDb.recordPlayback(bookmark);
        touchChange();
    }

    public void removeRecentBookmark(Bookmark bookmark) {
        BookmarkPlayHistoryDb.remove(bookmark);
        touchChange();
    }

    public void clearRecentBookmarks() {
        BookmarkPlayHistoryDb.clear();
        com.uiptv.db.SQLConnection.vacuum();
    }

    public void remove(String id) {
        if (BookmarkPlayHistoryDb.isRecentId(id)) {
            removeRecentBookmark(BookmarkPlayHistoryDb.getRecentBookmark(id));
            return;
        }
        try {
            Bookmark bookmark = BookmarkDb.get().getById(id);
            BookmarkPlayHistoryDb.preserveSnapshot(bookmark);
            BookmarkDb.get().delete(id);
            touchChange();
        } catch (Exception _) {
            AppLog.addErrorLog(BookmarkService.class, "Error while removing the bookmark");
        }
    }

    public void removeByAccountName(String accountName) {
        try {
            BookmarkDb.get().deleteByAccountName(accountName);
            BookmarkPlayHistoryDb.removeByAccountName(accountName);
            touchChange();
        } catch (Exception error) {
            // Account deletion must stop if its dependent bookmarks could not be removed.
            throw new IllegalStateException("Unable to remove bookmarks for account", error);
        }
    }

    public String readToJson() {
        List<Bookmark> bookmarks = new ArrayList<>(BookmarkDb.get().getBookmarks());
        List<BookmarkResolver.ResolvedBookmark> resolved = new BookmarkResolver().resolveBookmarks(bookmarks);
        return ServerUtils.objectToJson(resolved.stream().map(BookmarkResolver.ResolvedBookmark::getBookmark).toList());
    }

    public String readToJson(int offset, int limit) {
        if (limit <= 0) {
            return readToJson();
        }
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(0, limit);
        List<Bookmark> bookmarks = new ArrayList<>(BookmarkDb.get().getBookmarksPage(safeOffset, safeLimit));
        List<BookmarkResolver.ResolvedBookmark> resolved = new BookmarkResolver().resolveBookmarks(bookmarks);
        return ServerUtils.objectToJson(resolved.stream().map(BookmarkResolver.ResolvedBookmark::getBookmark).toList());
    }

    // Category operations
    public List<BookmarkCategory> getAllCategories() {
        return BookmarkDb.get().getAllCategories();
    }

    public void addCategory(BookmarkCategory category) {
        BookmarkDb.get().saveCategory(category);
        touchChange();
    }

    public void removeCategory(BookmarkCategory category) {
        BookmarkDb.get().deleteCategory(category);
        touchChange();
    }

    public void notifyBookmarksChanged() {
        touchChange();
    }

    // Order operations
    public void saveBookmarkOrder(List<String> orderedBookmarkDbIds) {
        Map<String, Integer> bookmarkOrders = new LinkedHashMap<>();
        for (int i = 0; i < orderedBookmarkDbIds.size(); i++) {
            bookmarkOrders.put(orderedBookmarkDbIds.get(i), i + 1);
        }
        saveBookmarkOrders(bookmarkOrders);
    }

    public void saveBookmarkOrders(Map<String, Integer> bookmarkOrders) {
        BookmarkDb.get().updateBookmarkOrders(bookmarkOrders);
        touchChange();
    }

    public long getChangeRevision() {
        return changeRevision.get();
    }

    public long getLastUpdatedEpochMs() {
        return lastUpdatedEpochMs;
    }

    public void addChangeListener(BookmarkChangeListener listener) {
        if (listener != null) {
            changeListeners.add(listener);
        }
    }

    public void removeChangeListener(BookmarkChangeListener listener) {
        if (listener != null) {
            changeListeners.remove(listener);
        }
    }

    private void touchChange() {
        lastUpdatedEpochMs = System.currentTimeMillis();
        long revision = changeRevision.incrementAndGet();
        for (BookmarkChangeListener listener : changeListeners) {
            try {
                listener.onBookmarksChanged(revision, lastUpdatedEpochMs);
            } catch (Exception _) {
                // Listener failures must never break bookmark mutation flow.
            }
        }
    }

    private static class SingletonHelper {
        private static final BookmarkService INSTANCE = new BookmarkService();
    }
}
