package com.uiptv.application;

import com.uiptv.db.CategoryDb;
import com.uiptv.db.VodCategoryDb;
import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.model.Account;
import com.uiptv.model.AccountMediaContext;
import com.uiptv.model.Bookmark;
import com.uiptv.model.BookmarkCategory;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.BookmarkResolver;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.CategoryService;
import com.uiptv.util.I18n;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.uiptv.util.M3uPlaylistUtils.escapeAttributeValue;
import static com.uiptv.util.M3uPlaylistUtils.sanitizeTitle;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

@SuppressWarnings("java:S6548")
public class BookmarkApplicationService {
    public static final String MISC_GROUP_TITLE = "Misc";

    private final BookmarkService bookmarkService = BookmarkService.getInstance();

    private BookmarkApplicationService() {
    }

    public static BookmarkApplicationService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public List<Bookmark> listBookmarks(int offset, int limit) {
        // Keep the paged web endpoint genuinely paged. Resolving the complete
        // bookmark catalogue for every 25-item request made the SPA startup
        // cost grow with the total number of bookmarks and repeated fallback
        // channel lookups could block the first visible cards.
        List<Bookmark> bookmarks;
        if (limit > 0) {
            bookmarks = new ArrayList<>(bookmarkService.readPage(Math.max(0, offset), limit));
        } else {
            bookmarks = new ArrayList<>(bookmarkService.read());
        }
        List<BookmarkResolver.ResolvedBookmark> resolved = new BookmarkResolver().resolveBookmarks(bookmarks);
        List<Bookmark> resolvedBookmarks = resolved.stream()
                .map(BookmarkResolver.ResolvedBookmark::getBookmark)
                .toList();
        if (limit <= 0) {
            return resolvedBookmarks;
        }
        return resolvedBookmarks;
    }

    public List<BookmarkCategory> listCategories() {
        return bookmarkService.getAllCategories();
    }

    public BookmarkSaveResult saveBookmark(BookmarkSaveRequest request) {
        if (!isValidBookmarkRequest(request)) {
            return null;
        }
        Account account = AccountService.getInstance().getById(request.accountId());
        if (account == null) {
            return null;
        }
        account = accountWithMode(account, request.mode());
        if (account == null) {
            return null;
        }

        String bookmarkCategoryId = safe(request.bookmarkCategoryId());
        if (!isKnownBookmarkCategory(bookmarkCategoryId)) {
            return null;
        }

        Category sourceCategory = resolveSourceCategory(account, request.categoryId());
        Bookmark bookmark = buildBookmark(account, request, bookmarkCategoryId, sourceCategory);
        Channel channel = buildChannel(request);
        bookmark.setFromChannel(channel);
        bookmark.setChannelJson(channel.toJson());
        bookmark.setAccountAction(account.getAction());

        Bookmark existing = bookmarkService.getBookmark(bookmark);
        if (existing != null) {
            return new BookmarkSaveResult("exists", existing.getDbId());
        }

        bookmarkService.save(bookmark);
        Bookmark saved = bookmarkService.getBookmark(bookmark);
        return new BookmarkSaveResult("saved", saved == null ? "" : saved.getDbId());
    }

    private boolean isValidBookmarkRequest(BookmarkSaveRequest request) {
        return request != null && isNotBlank(request.channelId()) && isNotBlank(request.channelName());
    }

    private boolean isKnownBookmarkCategory(String bookmarkCategoryId) {
        return isBlank(bookmarkCategoryId) || bookmarkService.getAllCategories().stream()
                .anyMatch(category -> bookmarkCategoryId.equals(category.getId()));
    }

    private Category resolveSourceCategory(Account account, String categoryId) {
        if (isBlank(categoryId)) {
            return null;
        }
        if (!account.isCacheEnabled()) {
            return CategoryService.getInstance().get(account, false).stream()
                    .filter(candidate -> candidate != null && (categoryId.equals(candidate.getDbId())
                            || categoryId.equals(candidate.getCategoryId())
                            || categoryId.equalsIgnoreCase(safe(candidate.getTitle()))))
                    .findFirst()
                    .orElse(null);
        }
        Category category = switch (account.getAction()) {
            case vod -> VodCategoryDb.get().getById(categoryId);
            case series -> SeriesCategoryDb.get().getById(categoryId);
            default -> CategoryDb.get().getCategoryByDbId(categoryId, account);
        };
        if (category != null && account.getAction() != Account.AccountAction.itv
                && !account.getDbId().equals(category.getAccountId())) {
            return null;
        }
        return category;
    }

    private Bookmark buildBookmark(Account account, BookmarkSaveRequest request, String bookmarkCategoryId,
                                   Category sourceCategory) {
        String categoryTitle = sourceCategory == null ? "" : sourceCategory.getTitle();
        String portal = isBlank(account.getServerPortalUrl()) ? account.getUrl() : account.getServerPortalUrl();
        Bookmark bookmark = new Bookmark(account.getAccountName(), categoryTitle, request.channelId(),
                request.channelName(), request.cmd(), portal, isBlank(bookmarkCategoryId) ? null : bookmarkCategoryId);
        if (sourceCategory != null) {
            bookmark.setCategoryJson(sourceCategory.toJson());
        }
        return bookmark;
    }

    private Channel buildChannel(BookmarkSaveRequest request) {
        Channel channel = new Channel();
        channel.setChannelId(request.channelId());
        channel.setName(request.channelName());
        channel.setCmd(request.cmd());
        channel.setLogo(safe(request.logo()));
        channel.setDrmType(safe(request.drmType()));
        channel.setDrmLicenseUrl(safe(request.drmLicenseUrl()));
        channel.setClearKeysJson(safe(request.clearKeysJson()));
        channel.setInputstreamaddon(safe(request.inputstreamaddon()));
        channel.setManifestType(safe(request.manifestType()));
        return channel;
    }

    public void deleteBookmark(String bookmarkId) {
        bookmarkService.remove(bookmarkId);
    }

    public void saveBookmarkOrders(Map<String, Integer> bookmarkOrders) {
        bookmarkService.saveBookmarkOrders(bookmarkOrders);
    }

    public Bookmark getBookmark(String bookmarkId) {
        return bookmarkService.getBookmark(bookmarkId);
    }

    public String buildPlaylist(String host) {
        String allTabName = I18n.tr("commonAll");
        List<Bookmark> bookmarks = bookmarkService.read();
        Map<String, String> categoryNameById = loadCategoryNamesById();
        StringBuilder response = new StringBuilder("#EXTM3U\n");
        appendUncategorizedEntries(response, bookmarks, host, categoryNameById, allTabName);
        appendCategorizedEntries(response, bookmarks, host, categoryNameById, allTabName);
        return response.toString();
    }

    private Map<String, String> loadCategoryNamesById() {
        Map<String, String> names = new LinkedHashMap<>();
        for (BookmarkCategory category : bookmarkService.getAllCategories()) {
            if (category != null && isNotBlank(category.getId()) && isNotBlank(category.getName())) {
                names.put(category.getId(), category.getName());
            }
        }
        return names;
    }

    private void appendUncategorizedEntries(StringBuilder response,
                                            List<Bookmark> bookmarks,
                                            String host,
                                            Map<String, String> categoryNameById,
                                            String allTabName) {
        for (Bookmark bookmark : bookmarks) {
            if (isUncategorized(bookmark, categoryNameById, allTabName)) {
                appendPlaylistEntry(response, bookmark, host, MISC_GROUP_TITLE);
            }
        }
    }

    private void appendCategorizedEntries(StringBuilder response,
                                          List<Bookmark> bookmarks,
                                          String host,
                                          Map<String, String> categoryNameById,
                                          String allTabName) {
        for (Bookmark bookmark : bookmarks) {
            if (!isUncategorized(bookmark, categoryNameById, allTabName)) {
                String categoryName = categoryNameById.get(bookmark.getCategoryId());
                if (isNotBlank(categoryName)) {
                    appendPlaylistEntry(response, bookmark, host, categoryName);
                }
            }
        }
    }

    private boolean isUncategorized(Bookmark bookmark, Map<String, String> categoryNameById, String allTabName) {
        if (bookmark == null) {
            return true;
        }
        String categoryId = bookmark.getCategoryId();
        if (!isNotBlank(categoryId)) {
            return true;
        }
        String categoryName = categoryNameById.get(categoryId);
        return !isNotBlank(categoryName) || categoryName.equalsIgnoreCase(allTabName);
    }

    private void appendPlaylistEntry(StringBuilder response, Bookmark bookmark, String host, String groupTitle) {
        String requestedURL = "http://" + host + "/bookmarkEntry.ts?bookmarkId=" + bookmark.getDbId();
        String channelName = sanitizeTitle(bookmark.getChannelName());
        response.append("#EXTINF:-1 tvg-id=\"")
                .append(escapeAttributeValue(bookmark.getDbId()))
                .append("\" tvg-name=\"")
                .append(escapeAttributeValue(channelName))
                .append("\" group-title=\"")
                .append(escapeAttributeValue(groupTitle))
                .append("\",")
                .append(channelName)
                .append("\n")
                .append(requestedURL)
                .append("\n");
    }

    private Account accountWithMode(Account account, CatalogMode mode) {
        if (account == null) {
            return null;
        }
        AccountMediaContext context = AccountMediaContext.from(account, (mode == null ? CatalogMode.ITV : mode).toAccountAction());
        return context == null ? null : context.toAccount();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static class SingletonHelper {
        private static final BookmarkApplicationService INSTANCE = new BookmarkApplicationService();
    }
}
