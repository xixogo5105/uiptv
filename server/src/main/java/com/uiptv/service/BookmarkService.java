package com.uiptv.service;

import com.uiptv.db.BookmarkDb;
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
        return (BookmarkDb.get().getById(dbId));
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
        boolean created = BookmarkDb.get().save(bookmark);
        if (created) {
            int nextDisplayOrder = BookmarkDb.get().getNextDisplayOrder();
            BookmarkDb.get().saveBookmarkOrder(bookmark.getDbId(), nextDisplayOrder);
        }
        touchChange();
    }

    public List<Bookmark> read() {
        return BookmarkDb.get().getBookmarks();
    }

    public List<Bookmark> getBookmarksByCategory(String categoryId) {
        return BookmarkDb.get().getBookmarksByCategory(categoryId);
    }

    public void remove(String id) {
        try {
            BookmarkDb.get().delete(id);
            touchChange();
        } catch (Exception _) {
            AppLog.addErrorLog(BookmarkService.class, "Error while removing the bookmark");
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
