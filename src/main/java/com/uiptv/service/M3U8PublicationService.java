package com.uiptv.service;

import com.uiptv.db.PublishedM3uCategorySelectionDb;
import com.uiptv.db.PublishedM3uChannelSelectionDb;
import com.uiptv.db.PublishedM3uSelectionDb;
import com.uiptv.model.Account;
import com.uiptv.model.CategoryType;
import com.uiptv.model.PublishedM3uCategorySelection;
import com.uiptv.model.PublishedM3uChannelSelection;
import com.uiptv.model.PublishedM3uSelection;
import com.uiptv.util.AccountType;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.uiptv.db.SQLConnection.connect;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.widget.UIptvAlert.showError;

public class M3U8PublicationService {
    private static final String COMMENT_PREFIX = "#";
    private static final String EXTM3U = "#EXTM3U";
    private static final String EXTINF = "#EXTINF";

    private M3U8PublicationService() {
    }

    public static M3U8PublicationService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public Set<String> getSelectedAccountIds() {
        return getSelections().accountIds();
    }

    public void setSelectedAccountIds(Set<String> accountIds) {
        saveSelections(new PublicationSelections(accountIds, Map.of(), Map.of()));
    }

    public PublicationSelections getSelections() {
        LinkedHashSet<String> accountIds = PublishedM3uSelectionDb.get().getAllSelections().stream()
                .map(PublishedM3uSelection::getAccountId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        LinkedHashMap<CategorySelectionKey, Boolean> categorySelections = new LinkedHashMap<>();
        for (PublishedM3uCategorySelection selection : PublishedM3uCategorySelectionDb.get().getAllSelections()) {
            categorySelections.put(new CategorySelectionKey(selection.getAccountId(), selection.getCategoryName()), selection.isSelected());
        }

        LinkedHashMap<ChannelSelectionKey, Boolean> channelSelections = new LinkedHashMap<>();
        for (PublishedM3uChannelSelection selection : PublishedM3uChannelSelectionDb.get().getAllSelections()) {
            channelSelections.put(
                    new ChannelSelectionKey(selection.getAccountId(), selection.getCategoryName(), selection.getChannelId()),
                    selection.isSelected()
            );
        }

        return new PublicationSelections(accountIds, categorySelections, channelSelections);
    }

    public void saveSelections(PublicationSelections selections) {
        PublicationSelections normalized = selections == null
                ? new PublicationSelections(Set.of(), Map.of(), Map.of())
                : selections.normalized();
        try (Connection conn = connect()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                PublishedM3uSelectionDb.get().replaceSelections(conn, normalized.accountIds());
                PublishedM3uCategorySelectionDb.get().replaceSelections(conn, toCategorySelections(normalized.categorySelections()));
                PublishedM3uChannelSelectionDb.get().replaceSelections(conn, toChannelSelections(normalized.channelSelections()));
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Unable to save published M3U selections", e);
        }
    }

    public List<PlaylistAccountSummary> getAvailableAccounts() {
        return getPublishableAccounts().stream()
                .map(account -> new PlaylistAccountSummary(account.getDbId(), account.getAccountName()))
                .toList();
    }

    public PlaylistAccount getPlaylist(String accountId) {
        if (isBlank(accountId)) {
            return null;
        }
        Account account = AccountService.getInstance().getById(accountId);
        if (!isPublishableAccount(account)) {
            return null;
        }
        try {
            return toPlaylistAccount(account, parsePlaylistEntries(account));
        } catch (Exception e) {
            showError("Failed to load playlist for account '" + account.getAccountName() + "'", e);
            return null;
        }
    }

    public String getPublishedM3u8() {
        PublicationSelections selections = getSelections();
        if (selections.accountIds().isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        result.append(EXTM3U).append("\n");
        for (Account account : getSelectedAccounts(selections.accountIds())) {
            appendSelectedAccountPlaylist(result, account, selections);
        }
        return result.toString();
    }

    private List<Account> getPublishableAccounts() {
        return AccountService.getInstance().getAll().values().stream()
                .filter(this::isPublishableAccount)
                .toList();
    }

    private List<Account> getSelectedAccounts(Set<String> accountIds) {
        return accountIds.stream()
                .map(AccountService.getInstance()::getById)
                .filter(this::isPublishableAccount)
                .toList();
    }

    private boolean isPublishableAccount(Account account) {
        return account != null && (account.getType() == AccountType.M3U8_LOCAL || account.getType() == AccountType.M3U8_URL);
    }

    private void appendSelectedAccountPlaylist(StringBuilder result, Account account, PublicationSelections selections) {
        try {
            for (PlaylistChannelEntry entry : parsePlaylistEntries(account)) {
                if (isChannelSelected(account.getDbId(), entry.categoryName(), entry.channelId(), selections)) {
                    appendPlaylistBlock(result, entry.lines());
                }
            }
        } catch (Exception e) {
            showError("Failed to append playlist for account '" + account.getAccountName() + "'", e);
        }
    }

    private void appendPlaylistBlock(StringBuilder result, List<String> lines) {
        for (String line : lines) {
            if (!line.trim().startsWith(EXTM3U)) {
                result.append(line).append("\n");
            }
        }
    }

    private boolean isChannelSelected(String accountId,
                                      String categoryName,
                                      String channelId,
                                      PublicationSelections selections) {
        ChannelSelectionKey channelKey = new ChannelSelectionKey(accountId, categoryName, channelId);
        Boolean channelSelection = selections.channelSelections().get(channelKey);
        if (channelSelection != null) {
            return channelSelection;
        }

        CategorySelectionKey categoryKey = new CategorySelectionKey(accountId, categoryName);
        Boolean categorySelection = selections.categorySelections().get(categoryKey);
        if (categorySelection != null) {
            return categorySelection;
        }

        return selections.accountIds().contains(accountId);
    }

    private PlaylistAccount toPlaylistAccount(Account account, List<PlaylistChannelEntry> entries) {
        LinkedHashMap<String, CategoryBucket> channelsByCategory = new LinkedHashMap<>();
        for (PlaylistChannelEntry entry : entries) {
            String categoryKey = normalizeCategoryKey(entry.categoryName());
            CategoryBucket bucket = channelsByCategory.computeIfAbsent(
                    categoryKey,
                    ignored -> new CategoryBucket(entry.categoryName(), new ArrayList<>())
            );
            bucket.channels().add(new PlaylistChannel(entry.channelId(), entry.title()));
        }
        List<PlaylistCategory> categories = channelsByCategory.entrySet().stream()
                .map(entry -> new PlaylistCategory(entry.getValue().displayName(), List.copyOf(entry.getValue().channels())))
                .toList();
        return new PlaylistAccount(account.getDbId(), account.getAccountName(), categories);
    }

    private String normalizeCategoryKey(String categoryName) {
        return isBlank(categoryName) ? "" : categoryName.trim().toLowerCase(Locale.ROOT);
    }

    private List<PublishedM3uCategorySelection> toCategorySelections(Map<CategorySelectionKey, Boolean> selections) {
        List<PublishedM3uCategorySelection> models = new ArrayList<>();
        for (Map.Entry<CategorySelectionKey, Boolean> entry : selections.entrySet()) {
            CategorySelectionKey key = entry.getKey();
            if (key == null) {
                continue;
            }
            models.add(new PublishedM3uCategorySelection(key.accountId(), key.categoryName(), Boolean.TRUE.equals(entry.getValue())));
        }
        return models;
    }

    private List<PublishedM3uChannelSelection> toChannelSelections(Map<ChannelSelectionKey, Boolean> selections) {
        List<PublishedM3uChannelSelection> models = new ArrayList<>();
        for (Map.Entry<ChannelSelectionKey, Boolean> entry : selections.entrySet()) {
            ChannelSelectionKey key = entry.getKey();
            if (key == null) {
                continue;
            }
            models.add(new PublishedM3uChannelSelection(
                    key.accountId(),
                    key.categoryName(),
                    key.channelId(),
                    Boolean.TRUE.equals(entry.getValue())
            ));
        }
        return models;
    }

    private List<PlaylistChannelEntry> parsePlaylistEntries(Account account) throws IOException {
        return parsePlaylistEntries(readPlaylistContent(account));
    }

    List<PlaylistChannelEntry> parsePlaylistEntries(String content) {
        List<String> lines = Arrays.asList(content.split("\\r?\\n"));
        List<PlaylistChannelEntry> entries = new ArrayList<>();
        int index = 0;
        while (index < lines.size()) {
            String line = lines.get(index);
            if (!line.startsWith(EXTINF)) {
                index++;
                continue;
            }

            ParsedPlaylistEntry entry = parsePlaylistEntry(lines, index);
            if (entry.channelEntry() != null) {
                entries.add(entry.channelEntry());
            }
            index = entry.nextIndex();
        }
        return entries;
    }

    private ParsedPlaylistEntry parsePlaylistEntry(List<String> lines, int startIndex) {
        String extinfLine = lines.get(startIndex);
        List<String> entryLines = new ArrayList<>();
        entryLines.add(extinfLine);

        String categoryName = normalizeCategoryName(parseQuotedAttribute(extinfLine, "group-title"));
        String title = parseEntryTitle(extinfLine);
        String sourceUrl = "";
        int index = startIndex + 1;
        while (index < lines.size()) {
            String nextLine = lines.get(index);
            if (nextLine.startsWith(EXTINF)) {
                break;
            }
            entryLines.add(nextLine);
            if (isPlaylistMediaLine(nextLine)) {
                sourceUrl = nextLine.trim();
                index++;
                break;
            }
            index++;
        }

        if (isBlank(sourceUrl)) {
            return new ParsedPlaylistEntry(null, Math.max(index, startIndex + 1));
        }

        String channelId = buildChannelId(parseQuotedAttribute(extinfLine, "tvg-id"), title, sourceUrl);
        return new ParsedPlaylistEntry(
                new PlaylistChannelEntry(categoryName, channelId, title, entryLines),
                Math.max(index, startIndex + 1)
        );
    }

    private boolean isPlaylistMediaLine(String line) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith(COMMENT_PREFIX)) {
            return false;
        }
        if (trimmed.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            return true;
        }
        if (trimmed.startsWith("//")
                || trimmed.startsWith("/")
                || trimmed.startsWith("./")
                || trimmed.startsWith("../")
                || trimmed.matches("^[a-zA-Z]:\\\\.*")) {
            return true;
        }
        return trimmed.matches("(?i)^.+\\.(m3u8|mpd|ts|aac|mp3|mp4|m4s)(\\?.*)?$");
    }

    private String normalizeCategoryName(String categoryName) {
        return isBlank(categoryName) ? CategoryType.UNCATEGORIZED.displayName() : categoryName.trim();
    }

    private String parseQuotedAttribute(String line, String key) {
        String marker = key + "=\"";
        String[] split = line.split(marker, 2);
        if (split.length < 2) {
            return "";
        }
        String[] value = split[1].split("\"", 2);
        return value.length == 0 ? "" : value[0];
    }

    private String parseEntryTitle(String line) {
        int lastCommaIndex = line.lastIndexOf(',');
        if (lastCommaIndex < 0 || lastCommaIndex >= line.length() - 1) {
            return "";
        }
        return line.substring(lastCommaIndex + 1).trim();
    }

    private String buildChannelId(String tvgId, String title, String sourceUrl) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update((isBlank(tvgId) ? "" : tvgId).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update((isBlank(title) ? "" : title).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update((isBlank(sourceUrl) ? "" : sourceUrl).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            return (isBlank(tvgId) ? "" : tvgId) + "|" + title + "|" + sourceUrl;
        }
    }

    private String readPlaylistContent(Account account) throws IOException {
        if (account.getType() == AccountType.M3U8_LOCAL) {
            return readFile(account.getM3u8Path());
        }
        if (account.getType() == AccountType.M3U8_URL) {
            return readUrl(resolveRemotePlaylistUrl(account));
        }
        return "";
    }

    private String resolveRemotePlaylistUrl(Account account) {
        if (!isBlank(account.getM3u8Path())) {
            return account.getM3u8Path();
        }
        return account.getUrl();
    }

    private String readFile(String path) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(path, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    @SuppressWarnings("java:S1874")
    private String readUrl(String urlString) throws IOException {
        URL url = new URL(urlString);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    public record PublicationSelections(Set<String> accountIds,
                                        Map<CategorySelectionKey, Boolean> categorySelections,
                                        Map<ChannelSelectionKey, Boolean> channelSelections) {
        public PublicationSelections normalized() {
            return new PublicationSelections(
                    accountIds == null ? Set.of() : new LinkedHashSet<>(accountIds),
                    categorySelections == null ? Map.of() : new LinkedHashMap<>(categorySelections),
                    channelSelections == null ? Map.of() : new LinkedHashMap<>(channelSelections)
            );
        }
    }

    public record CategorySelectionKey(String accountId, String categoryName) {
    }

    public record ChannelSelectionKey(String accountId, String categoryName, String channelId) {
    }

    public record PlaylistAccount(String accountId, String accountName, List<PlaylistCategory> categories) {
    }

    public record PlaylistAccountSummary(String accountId, String accountName) {
    }

    public record PlaylistCategory(String categoryName, List<PlaylistChannel> channels) {
    }

    public record PlaylistChannel(String channelId, String title) {
    }

    record PlaylistChannelEntry(String categoryName, String channelId, String title, List<String> lines) {
    }

    private record ParsedPlaylistEntry(PlaylistChannelEntry channelEntry, int nextIndex) {
    }

    private record CategoryBucket(String displayName, List<PlaylistChannel> channels) {
    }

    private static class SingletonHelper {
        private static final M3U8PublicationService INSTANCE = new M3U8PublicationService();
    }
}
