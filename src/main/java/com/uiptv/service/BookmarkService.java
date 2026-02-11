package com.uiptv.service;

import com.uiptv.db.BookmarkDb;
import com.uiptv.model.Bookmark;
import com.uiptv.model.BookmarkCategory;
import com.uiptv.util.ServerUtils;

import java.util.ArrayList;
import java.util.List;

import static com.uiptv.widget.UIptvAlert.showError;

public class BookmarkService {
    private static BookmarkService instance;

    private BookmarkService() {
    }

    public static synchronized BookmarkService getInstance() {
        if (instance == null) {
            instance = new BookmarkService();
        }
        return instance;
    }

    public boolean isChannelBookmarked(Bookmark bookmark) {
        return (BookmarkDb.get().getBookmarkById(bookmark) != null);
    }

    public Bookmark getBookmark(String dbId) {
        return (BookmarkDb.get().getById(dbId));
    }

    // New overloaded method to find a bookmark by its properties
    public Bookmark getBookmark(Bookmark bookmark) {
        return BookmarkDb.get().getBookmarkById(bookmark);
    }

    public void toggleBookmark(Bookmark bookmark) {
        Bookmark dbBookmark = BookmarkDb.get().getBookmarkById(bookmark);
        if (dbBookmark != null) {
            remove(dbBookmark.getDbId());
        } else {
            BookmarkDb.get().save(bookmark);
        }
    }

    public void save(Bookmark bookmark) {
        BookmarkDb.get().save(bookmark);
    }

    public List<Bookmark> read() {
        return BookmarkDb.get().getBookmarks();
    }

    public void remove(String id) {
        try {
            BookmarkDb.get().delete(id);
        } catch (Exception ignored) {
            showError("Error while removing the bookmark");
        }
    }

    public String readToJson() {
        return ServerUtils.objectToJson(new ArrayList<>(read()));
    }

    // New methods for category operations
    public List<BookmarkCategory> getAllCategories() {
        return BookmarkDb.get().getAllCategories();
    }

    public void addCategory(BookmarkCategory category) {
        BookmarkDb.get().saveCategory(category);
    }

    public void removeCategory(BookmarkCategory category) {
        BookmarkDb.get().deleteCategory(category);
    }
}
