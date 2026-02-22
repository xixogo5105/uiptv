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

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;
import static com.uiptv.widget.UIptvAlert.showError;

public class BookmarkService {
    private static BookmarkService instance;
    private final LogoResolverService logoResolverService;

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
            BookmarkDb.get().save(bookmark);
        }
    }

    public void save(Bookmark bookmark) {
        BookmarkDb.get().save(bookmark);
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
    }

    public void removeCategory(BookmarkCategory category) {
        BookmarkDb.get().deleteCategory(category);
    }

    // Order operations
    public void saveBookmarkOrder(String categoryId, List<String> orderedBookmarkDbIds) {
        BookmarkDb.get().updateBookmarkOrders(categoryId, orderedBookmarkDbIds);
    }
}
