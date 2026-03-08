package com.uiptv.db;

import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.BookmarkCategory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.uiptv.db.DatabaseUtils.DbTable.*;
import static com.uiptv.db.DatabaseUtils.insertTableSql;
import static com.uiptv.db.SQLConnection.connect;
import static com.uiptv.util.StringUtils.isNotBlank;

public class BookmarkDb extends BaseDb {
    private static BookmarkDb instance;
    private static final String DELETE_FROM = "DELETE FROM ";
    private static final String AND_NULLABLE_CATEGORY_ID = " AND (category_id = ? OR (category_id IS NULL AND ? IS NULL))";
    private static final String WHERE_NULLABLE_CATEGORY_ID = " WHERE (category_id = ? OR (category_id IS NULL AND ? IS NULL))";

    public static synchronized BookmarkDb get() {
        if (instance == null) {
            instance = new BookmarkDb();
        }
        return instance;
    }

    public BookmarkDb() {
        super(BOOKMARK_TABLE);
    }

    public List<Bookmark> getBookmarks() {
        return getBookmarksOrdered(null);
    }

    public List<Bookmark> getBookmarksByCategory(String categoryId) {
        return getBookmarksOrdered(categoryId);
    }

    private List<Bookmark> getBookmarksOrdered(String categoryId) {
        List<Bookmark> bookmarks = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT b.*, bo.display_order FROM ")
                .append(BOOKMARK_TABLE.getTableName()).append(" b ")
                .append("LEFT JOIN (SELECT bookmark_db_id, MIN(display_order) AS display_order FROM ")
                .append(BOOKMARK_ORDER_TABLE.getTableName())
                .append(" GROUP BY bookmark_db_id) bo ON b.id = bo.bookmark_db_id ");

        if (categoryId != null) {
            sql.append("WHERE b.categoryId = ? ");
        }

        sql.append("ORDER BY CASE WHEN bo.display_order IS NULL THEN 1 ELSE 0 END, bo.display_order ASC, b.id ASC");

        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql.toString())) {
            if (categoryId != null) {
                statement.setString(1, categoryId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    bookmarks.add(populate(resultSet));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute query for ordered bookmarks", e);
        }
        return bookmarks;
    }

    public Bookmark getBookmarkById(Bookmark b) {
        List<Bookmark> bookmarks = getAll(" where accountName=? AND categoryTitle=? AND channelId=? AND channelName=?", new String[]{b.getAccountName(), b.getCategoryTitle(), b.getChannelId(), b.getChannelName()});
        return (bookmarks != null && !bookmarks.isEmpty()) ? bookmarks.get(0) : null;
    }

    public boolean save(Bookmark bookmark) {
        Bookmark existing = getBookmarkById(bookmark);
        if (existing != null) {
            bookmark.setDbId(existing.getDbId());
            update(bookmark);
            return false;
        } else {
            insert(bookmark);
            return true;
        }
    }

    private void insert(Bookmark bookmark) {
        String sql = insertTableSql(BOOKMARK_TABLE);
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            int i = 1;
            statement.setString(i++, bookmark.getAccountName());
            statement.setString(i++, bookmark.getCategoryTitle());
            statement.setString(i++, bookmark.getChannelId());
            statement.setString(i++, bookmark.getChannelName());
            statement.setString(i++, bookmark.getCmd());
            statement.setString(i++, bookmark.getServerPortalUrl());
            statement.setString(i++, bookmark.getCategoryId());
            statement.setString(i++, bookmark.getAccountAction() != null ? bookmark.getAccountAction().name() : null); // New field
            statement.setString(i++, bookmark.getDrmType());
            statement.setString(i++, bookmark.getDrmLicenseUrl());
            statement.setString(i++, bookmark.getClearKeysJson());
            statement.setString(i++, bookmark.getInputstreamaddon());
            statement.setString(i++, bookmark.getManifestType());
            statement.setString(i++, bookmark.getCategoryJson());
            statement.setString(i++, bookmark.getChannelJson());
            statement.setString(i++, bookmark.getVodJson());
            statement.setString(i++, bookmark.getSeriesJson());
            statement.execute();
            // Retrieve the generated ID for the new bookmark
            try (ResultSet rs = statement.getGeneratedKeys()) {
                if (rs.next()) {
                    bookmark.setDbId(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute insert query for bookmark", e);
        }
    }

    private void update(Bookmark bookmark) {
        String sql = "UPDATE " + BOOKMARK_TABLE.getTableName() + " SET accountName=?, categoryTitle=?, channelId=?, channelName=?, cmd=?, serverPortalUrl=?, categoryId=?, accountAction=?, drmType=?, drmLicenseUrl=?, clearKeysJson=?, inputstreamaddon=?, manifestType=?, categoryJson=?, channelJson=?, vodJson=?, seriesJson=? WHERE id=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            int i = 1;
            statement.setString(i++, bookmark.getAccountName());
            statement.setString(i++, bookmark.getCategoryTitle());
            statement.setString(i++, bookmark.getChannelId());
            statement.setString(i++, bookmark.getChannelName());
            statement.setString(i++, bookmark.getCmd());
            statement.setString(i++, bookmark.getServerPortalUrl());
            statement.setString(i++, bookmark.getCategoryId());
            statement.setString(i++, bookmark.getAccountAction() != null ? bookmark.getAccountAction().name() : null); // New field
            statement.setString(i++, bookmark.getDrmType());
            statement.setString(i++, bookmark.getDrmLicenseUrl());
            statement.setString(i++, bookmark.getClearKeysJson());
            statement.setString(i++, bookmark.getInputstreamaddon());
            statement.setString(i++, bookmark.getManifestType());
            statement.setString(i++, bookmark.getCategoryJson());
            statement.setString(i++, bookmark.getChannelJson());
            statement.setString(i++, bookmark.getVodJson());
            statement.setString(i++, bookmark.getSeriesJson());
            statement.setString(i++, bookmark.getDbId());
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute update query for bookmark", e);
        }
    }

    public void delete(Bookmark bookmark) {
        if (isNotBlank(bookmark.getDbId())) {
            delete(bookmark.getDbId());
        }
        deleteBookmark(bookmark);
    }

    @Override
    public void delete(String id) {
        super.delete(id);
        deleteBookmarkOrders(id);
    }

    private void deleteBookmark(Bookmark b) {
        String sql = DELETE_FROM + BOOKMARK_TABLE.getTableName() + " where accountName=? AND categoryTitle=? AND channelId=? AND channelName=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, b.getAccountName());
            statement.setString(2, b.getCategoryTitle());
            statement.setString(3, b.getChannelId());
            statement.setString(4, b.getChannelName());
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute delete query", e);
        }
    }

    public void deleteByAccountName(String accountName) {
        String deleteOrderSql = DELETE_FROM + BOOKMARK_ORDER_TABLE.getTableName() +
                " WHERE bookmark_db_id IN (SELECT id FROM " + BOOKMARK_TABLE.getTableName() + " WHERE accountName=?)";
        String deleteBookmarkSql = DELETE_FROM + BOOKMARK_TABLE.getTableName() + " WHERE accountName=?";

        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt1 = conn.prepareStatement(deleteOrderSql)) {
                stmt1.setString(1, accountName);
                stmt1.executeUpdate();
            }
            try (PreparedStatement stmt2 = conn.prepareStatement(deleteBookmarkSql)) {
                stmt2.setString(1, accountName);
                stmt2.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            try {
                connect().rollback();
            } catch (SQLException ex) {
                throw new RuntimeException("Failed to rollback transaction", ex);
            }
            throw new RuntimeException("Unable to delete bookmarks by account name", e);
        }
    }

    @Override
    Bookmark populate(ResultSet resultSet) {
        Bookmark bookmark = new Bookmark(
                nullSafeString(resultSet, "accountName"),
                nullSafeString(resultSet, "categoryTitle"),
                nullSafeString(resultSet, "channelId"),
                nullSafeString(resultSet, "channelName"),
                nullSafeString(resultSet, "cmd"),
                nullSafeString(resultSet, "serverPortalUrl"),
                nullSafeString(resultSet, "categoryId"));
        bookmark.setDbId(nullSafeString(resultSet, "id"));
        String accountActionStr = nullSafeString(resultSet, "accountAction");
        if (isNotBlank(accountActionStr)) {
            bookmark.setAccountAction(Account.AccountAction.valueOf(accountActionStr));
        }
        bookmark.setDrmType(nullSafeString(resultSet, "drmType"));
        bookmark.setDrmLicenseUrl(nullSafeString(resultSet, "drmLicenseUrl"));
        bookmark.setClearKeysJson(nullSafeString(resultSet, "clearKeysJson"));
        bookmark.setInputstreamaddon(nullSafeString(resultSet, "inputstreamaddon"));
        bookmark.setManifestType(nullSafeString(resultSet, "manifestType"));
        bookmark.setCategoryJson(nullSafeString(resultSet, "categoryJson"));
        bookmark.setChannelJson(nullSafeString(resultSet, "channelJson"));
        bookmark.setVodJson(nullSafeString(resultSet, "vodJson"));
        bookmark.setSeriesJson(nullSafeString(resultSet, "seriesJson"));
        return bookmark;
    }

    public void saveBookmarkOrder(String bookmarkDbId, int displayOrder) {
        String deleteSql = DELETE_FROM + BOOKMARK_ORDER_TABLE.getTableName() + " WHERE bookmark_db_id = ?";
        String insertSql = "INSERT INTO " + BOOKMARK_ORDER_TABLE.getTableName() + " (bookmark_db_id, category_id, display_order) VALUES (?, NULL, ?)";

        Connection conn = null;
        try {
            conn = connect();
            conn.setAutoCommit(false);

            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                deleteStmt.setString(1, bookmarkDbId);
                deleteStmt.executeUpdate();
            }

            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, bookmarkDbId);
                insertStmt.setInt(2, displayOrder);
                insertStmt.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (SQLException ex) {
                throw new RuntimeException("Failed to rollback transaction", ex);
            }
            throw new RuntimeException("Unable to save bookmark order", e);
        } finally {
            closeConnection(conn);
        }
    }

    public void updateBookmarkOrders(Map<String, Integer> bookmarkOrders) {
        String deleteSql = DELETE_FROM + BOOKMARK_ORDER_TABLE.getTableName();
        String insertSql = "INSERT INTO " + BOOKMARK_ORDER_TABLE.getTableName() + " (bookmark_db_id, category_id, display_order) VALUES (?, NULL, ?)";

        Connection conn = null;
        try {
            conn = connect();
            conn.setAutoCommit(false);

            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                deleteStmt.executeUpdate();
            }

            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                List<Map.Entry<String, Integer>> orderedEntries = bookmarkOrders.entrySet().stream()
                        .filter(entry -> isNotBlank(entry.getKey()) && entry.getValue() != null)
                        .sorted(Comparator.comparingInt(Map.Entry::getValue))
                        .toList();
                for (Map.Entry<String, Integer> entry : orderedEntries) {
                    insertStmt.setString(1, entry.getKey());
                    insertStmt.setInt(2, entry.getValue());
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (SQLException ex) {
                throw new RuntimeException("Failed to rollback transaction", ex);
            }
            throw new RuntimeException("Unable to update bookmark orders", e);
        } finally {
            closeConnection(conn);
        }
    }

    private void closeConnection(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.close();
        } catch (SQLException _) {
        }
    }

    public void deleteBookmarkOrder(String bookmarkDbId, String categoryId) {
        String sql = DELETE_FROM + BOOKMARK_ORDER_TABLE.getTableName() + " WHERE bookmark_db_id = ?" + AND_NULLABLE_CATEGORY_ID;
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, bookmarkDbId);
            if (categoryId == null) {
                statement.setNull(2, java.sql.Types.VARCHAR);
                statement.setNull(3, java.sql.Types.VARCHAR);
            } else {
                statement.setString(2, categoryId);
                statement.setString(3, categoryId);
            }
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to delete bookmark order", e);
        }
    }

    public void deleteBookmarkOrders(String bookmarkDbId) {
        String sql = DELETE_FROM + BOOKMARK_ORDER_TABLE.getTableName() + " WHERE bookmark_db_id = ?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, bookmarkDbId);
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to delete bookmark orders", e);
        }
    }

    public int getNextDisplayOrder() {
        String sql = "SELECT COALESCE(MAX(display_order), 0) + 1 FROM " + BOOKMARK_ORDER_TABLE.getTableName();
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Unable to read next bookmark display order", e);
        }
        return 1;
    }

    public void deleteBookmarkOrdersByCategory(String categoryId) {
        String sql = DELETE_FROM + BOOKMARK_ORDER_TABLE.getTableName() + WHERE_NULLABLE_CATEGORY_ID;
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            if (categoryId == null) {
                statement.setNull(1, java.sql.Types.VARCHAR);
                statement.setNull(2, java.sql.Types.VARCHAR);
            } else {
                statement.setString(1, categoryId);
                statement.setString(2, categoryId);
            }
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to delete bookmark orders by category", e);
        }
    }

    public void saveCategory(BookmarkCategory category) {
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(insertTableSql(BOOKMARK_CATEGORY_TABLE))) {
            statement.setString(1, category.getName());
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute query", e);
        }
    }

    public void deleteCategory(BookmarkCategory category) {
        String sql = DELETE_FROM + BOOKMARK_CATEGORY_TABLE.getTableName() + " where id=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, category.getId());
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute delete query", e);
        }
    }

    public List<BookmarkCategory> getAllCategories() {
        List<BookmarkCategory> categories = new ArrayList<>();
        String sql = "SELECT * FROM " + BOOKMARK_CATEGORY_TABLE.getTableName();
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql); ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                BookmarkCategory category = new BookmarkCategory(
                        resultSet.getString("id"),
                        resultSet.getString("name")
                );
                categories.add(category);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute query", e);
        }
        return categories;
    }
}
