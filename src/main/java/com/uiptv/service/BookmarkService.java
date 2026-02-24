package com.uiptv.service;

import com.uiptv.db.BookmarkDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.BookmarkCategory;
import com.uiptv.model.Channel;
import com.uiptv.util.ServerUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;
import static com.uiptv.widget.UIptvAlert.showError;

public class BookmarkService {
    private static BookmarkService instance;
    private final LogoResolverService logoResolverService;
    private final AtomicLong changeRevision = new AtomicLong(1);
    private volatile long lastUpdatedEpochMs = System.currentTimeMillis();
    private final Set<BookmarkChangeListener> changeListeners = new CopyOnWriteArraySet<>();

    private BookmarkService() {
        this.logoResolverService = LogoResolverService.getInstance();
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
        BookmarkDb.get().save(bookmark);
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
        } catch (Exception ignored) {
            showError("Error while removing the bookmark");
        }
    }

    public String readToJson() {
        List<Bookmark> bookmarks = new ArrayList<>(BookmarkDb.get().getBookmarks());
        enrichBookmarkLogos(bookmarks);
        return ServerUtils.objectToJson(bookmarks);
    }

    private void enrichBookmarkLogos(List<Bookmark> bookmarks) {
        for (Bookmark bookmark : bookmarks) {
            try {
                if (bookmark == null || isNotBlank(bookmark.getLogo())) {
                    continue;
                }
                String logo = extractLogoFromChannelJson(bookmark.getChannelJson());
                if (isBlank(logo) && isNotBlank(bookmark.getChannelId()) && isNotBlank(bookmark.getAccountName())) {
                    Account account = AccountService.getInstance().getByName(bookmark.getAccountName());
                    if (account != null && isNotBlank(account.getDbId())) {
                        Channel channel = ChannelDb.get().getChannelByChannelIdAndAccount(bookmark.getChannelId(), account.getDbId());
                        if (channel != null) {
                            logo = channel.getLogo();
                        }
                    }
                }
                if (isBlank(logo)) {
                    logo = logoResolverService.resolve(bookmark.getChannelName(), null, null);
                }
                bookmark.setLogo(logo);
            } catch (Exception ignored) {
                // Best-effort enrichment only. Never fail /bookmarks response.
            }
        }
    }

    private String extractLogoFromChannelJson(String channelJson) {
        if (isBlank(channelJson)) {
            return "";
        }
        Channel parsed = Channel.fromJson(channelJson);
        return parsed != null ? parsed.getLogo() : "";
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
    public void saveBookmarkOrder(String categoryId, List<String> orderedBookmarkDbIds) {
        BookmarkDb.get().updateBookmarkOrders(categoryId, orderedBookmarkDbIds);
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
            } catch (Exception ignored) {
                // Listener failures must never break bookmark mutation flow.
            }
        }
    }
}
