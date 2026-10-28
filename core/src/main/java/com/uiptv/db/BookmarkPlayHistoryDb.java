package com.uiptv.db;

import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static com.uiptv.db.SQLConnection.connect;

/** Local, bounded playback history, independent of saved favourites. */
public final class BookmarkPlayHistoryDb {
    public static final int MAX_RECENT_ITEMS = 25;
    private static final String RECENT_ID_PREFIX = "recent:";
    private static final String TABLE = "BookmarkPlayHistory";
    private static final String RECORD_SQL = "INSERT INTO " + TABLE
            + " (accountName, accountAction, channelId, playCount, lastPlayedAt, snapshotJson) VALUES (?, ?, ?, 1, ?, ?) "
            + "ON CONFLICT(accountName, accountAction, channelId) DO UPDATE SET "
            + "playCount = " + TABLE + ".playCount + 1, lastPlayedAt = excluded.lastPlayedAt, snapshotJson = excluded.snapshotJson";
    private static final String DELETE_BOOKMARK_SQL =
            "DELETE FROM BookmarkPlayHistory WHERE accountName = ? AND accountAction = ? AND channelId = ?";
    private static final String DELETE_ACCOUNT_SQL =
            "DELETE FROM BookmarkPlayHistory WHERE accountName = ?";
    private static final String DELETE_ALL_SQL = "DELETE FROM BookmarkPlayHistory";

    private BookmarkPlayHistoryDb() {
    }

    public static void recordPlayback(Bookmark bookmark) {
        if (bookmark == null || blank(bookmark.getAccountName()) || blank(bookmark.getChannelId())) {
            return;
        }
        String accountAction = bookmark.getAccountAction() == null
                ? Account.AccountAction.itv.name()
                : bookmark.getAccountAction().name();
        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            recordPlaybackTransaction(conn, bookmark, accountAction);
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to record recent bookmark playback", e);
        }
    }

    public static List<Bookmark> readRecentBookmarks() {
        return readRecentBookmarks(MAX_RECENT_ITEMS);
    }

    public static int countRecentBookmarks() {
        return readRecentBookmarks().size();
    }

    /** Preserve pre-snapshot history when an older favourite is removed. */
    public static void preserveSnapshot(Bookmark bookmark) {
        if (bookmark == null) return;
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(
                "UPDATE BookmarkPlayHistory SET snapshotJson = ? WHERE accountName = ? AND accountAction = ? AND channelId = ?")) {
            statement.setString(1, bookmark.toJson());
            statement.setString(2, bookmark.getAccountName());
            statement.setString(3, bookmark.getAccountAction() == null ? "itv" : bookmark.getAccountAction().name());
            statement.setString(4, bookmark.getChannelId());
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new DatabaseAccessException("Unable to preserve recent playback", error);
        }
    }

    public static boolean isRecentId(String id) {
        return id != null && id.startsWith(RECENT_ID_PREFIX);
    }

    public static Bookmark getRecentBookmark(String id) {
        if (!isRecentId(id)) return null;
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(
                "SELECT id, accountName, accountAction, channelId, snapshotJson FROM BookmarkPlayHistory WHERE id = ?")) {
            statement.setString(1, id.substring(RECENT_ID_PREFIX.length()));
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? readSnapshot(row) : null;
            }
        } catch (SQLException error) {
            throw new DatabaseAccessException("Unable to read recent playback", error);
        }
    }

    public static List<Bookmark> readRecentBookmarks(int limit) {
        List<Bookmark> recent = new ArrayList<>();
        // Prefer the saved favourite so group changes are reflected immediately;
        // the snapshot also keeps unbookmarked entries playable after cache reloads.
        String sql = "SELECT h.id, h.accountName, h.accountAction, h.channelId, h.playCount, h.lastPlayedAt, "
                + "h.snapshotJson, b.id AS bookmarkId FROM BookmarkPlayHistory h LEFT JOIN Bookmark b "
                + "ON b.id = (SELECT MIN(b2.id) FROM Bookmark b2 WHERE b2.accountName = h.accountName "
                + "AND b2.channelId = h.channelId AND COALESCE(NULLIF(b2.accountAction, ''), 'itv') = h.accountAction) "
                + "ORDER BY h.playCount DESC, h.lastPlayedAt DESC, h.id DESC LIMIT ?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, Math.max(0, limit));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String savedId = rows.getString("bookmarkId");
                    Bookmark item = savedId == null ? readSnapshot(rows) : BookmarkDb.get().getById(savedId);
                    if (item != null) recent.add(item);
                }
            }
        } catch (SQLException error) {
            throw new DatabaseAccessException("Unable to read recent playback", error);
        }
        return recent;
    }

    private static Bookmark readSnapshot(ResultSet row) throws SQLException {
        try {
            Bookmark item = Bookmark.fromJson(row.getString("snapshotJson"));
            item.setDbId(RECENT_ID_PREFIX + row.getLong("id"));
            // Account rename updates the history identity without rewriting snapshots.
            item.setAccountName(row.getString("accountName"));
            item.setAccountAction(Account.AccountAction.valueOf(row.getString("accountAction")));
            item.setChannelId(row.getString("channelId"));
            return item;
        } catch (org.json.JSONException | IllegalArgumentException ignored) {
            return null;
        }
    }

    public static void remove(Bookmark bookmark) {
        if (bookmark == null || blank(bookmark.getAccountName()) || blank(bookmark.getChannelId())) {
            return;
        }
        String accountAction = bookmark.getAccountAction() == null
                ? Account.AccountAction.itv.name()
                : bookmark.getAccountAction().name();
        executeDelete(
                DELETE_BOOKMARK_SQL,
                bookmark.getAccountName(), accountAction, bookmark.getChannelId());
    }

    public static void removeByAccountName(String accountName) {
        if (!blank(accountName)) {
            executeDelete(DELETE_ACCOUNT_SQL, accountName);
        }
    }

    public static void clear() {
        executeDelete(DELETE_ALL_SQL);
    }

    private static void recordPlaybackTransaction(Connection conn, Bookmark bookmark, String accountAction)
            throws SQLException {
        try {
            recordPlayback(conn, bookmark, accountAction);
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        }
    }

    private static void recordPlayback(Connection conn, Bookmark bookmark, String accountAction) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(RECORD_SQL)) {
            statement.setString(1, bookmark.getAccountName());
            statement.setString(2, accountAction);
            statement.setString(3, bookmark.getChannelId());
            statement.setLong(4, System.currentTimeMillis());
            statement.setString(5, bookmark.toJson());
            statement.executeUpdate();
            trim(conn);
        }
    }

    private static final String TRIM_SQL = "DELETE FROM " + TABLE + " WHERE id NOT IN (SELECT id FROM " + TABLE
                + " ORDER BY playCount DESC, lastPlayedAt DESC, id DESC LIMIT ?)";

    private static void trim(Connection conn) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(TRIM_SQL)) {
            statement.setInt(1, MAX_RECENT_ITEMS);
            statement.executeUpdate();
        }
    }

    private static void executeDelete(String sql, String... parameters) {
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setString(i + 1, parameters[i]);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to clear recent bookmark history", e);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
