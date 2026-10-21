package com.uiptv.db;

import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import static com.uiptv.db.SQLConnection.connect;

/** Local, bounded playback history for items that are currently bookmarked. */
public final class BookmarkPlayHistoryDb {
    public static final int MAX_RECENT_ITEMS = 25;
    private static final String TABLE = "BookmarkPlayHistory";
    private static final String RECORD_SQL = "INSERT INTO " + TABLE
            + " (accountName, accountAction, channelId, playCount, lastPlayedAt) VALUES (?, ?, ?, 1, ?) "
            + "ON CONFLICT(accountName, accountAction, channelId) DO UPDATE SET "
            + "playCount = " + TABLE + ".playCount + 1, lastPlayedAt = excluded.lastPlayedAt";
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
        return BookmarkDb.get().getRecentlyPlayedBookmarks(MAX_RECENT_ITEMS);
    }

    public static int countRecentBookmarks() {
        return BookmarkDb.get().countRecentlyPlayedBookmarks(MAX_RECENT_ITEMS);
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
