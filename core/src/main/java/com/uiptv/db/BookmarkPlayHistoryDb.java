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

    private BookmarkPlayHistoryDb() {
    }

    public static void record(Bookmark bookmark) {
        if (bookmark == null || blank(bookmark.getAccountName()) || blank(bookmark.getChannelId())) {
            return;
        }
        String accountAction = bookmark.getAccountAction() == null
                ? Account.AccountAction.itv.name()
                : bookmark.getAccountAction().name();
        String sql = "INSERT INTO " + TABLE + " (accountName, accountAction, channelId, playCount, lastPlayedAt) "
                + "VALUES (?, ?, ?, 1, ?) "
                + "ON CONFLICT(accountName, accountAction, channelId) DO UPDATE SET "
                + "playCount = " + TABLE + ".playCount + 1, lastPlayedAt = excluded.lastPlayedAt";
        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                statement.setString(1, bookmark.getAccountName());
                statement.setString(2, accountAction);
                statement.setString(3, bookmark.getChannelId());
                statement.setLong(4, System.currentTimeMillis());
                statement.executeUpdate();
                trim(conn);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
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
        delete("accountName = ? AND accountAction = ? AND channelId = ?",
                bookmark.getAccountName(), accountAction, bookmark.getChannelId());
    }

    public static void removeByAccountName(String accountName) {
        if (!blank(accountName)) {
            delete("accountName = ?", accountName);
        }
    }

    public static void clear() {
        delete(null);
    }

    private static void trim(Connection conn) throws SQLException {
        String sql = "DELETE FROM " + TABLE + " WHERE id NOT IN (SELECT id FROM " + TABLE
                + " ORDER BY playCount DESC, lastPlayedAt DESC, id DESC LIMIT ?)";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, MAX_RECENT_ITEMS);
            statement.executeUpdate();
        }
    }

    private static void delete(String where, String... parameters) {
        String sql = "DELETE FROM " + TABLE + (where == null ? "" : " WHERE " + where);
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
