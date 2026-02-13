package com.uiptv.db;

import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.BookmarkCategory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static com.uiptv.db.DatabaseUtils.DbTable.*;
import static com.uiptv.db.DatabaseUtils.insertTableSql;
import static com.uiptv.db.SQLConnection.connect;
import static com.uiptv.util.StringUtils.isNotBlank;

public class BookmarkDb extends BaseDb {
    private static BookmarkDb instance;

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
                .append("LEFT JOIN ").append(BOOKMARK_ORDER_TABLE.getTableName()).append(" bo ON b.id = bo.bookmark_db_id ");

        if (categoryId == null) { // "All" category
            sql.append("WHERE bo.category_id IS NULL OR bo.category_id = b.categoryId ORDER BY bo.display_order ASC");
        } else {
            sql.append("WHERE b.categoryId = ? AND bo.category_id = ? ORDER BY bo.display_order ASC");
        }

        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql.toString())) {
            if (categoryId != null) {
                statement.setString(1, categoryId);
                statement.setString(2, categoryId);
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

    public void save(Bookmark bookmark) {
        // Check if bookmark exists to decide between insert and update
        Bookmark existing = getBookmarkById(bookmark);
        if (existing != null) {
            bookmark.setDbId(existing.getDbId()); // Ensure we update the correct record
            update(bookmark);
        } else {
            insert(bookmark);
        }
        // Update or insert order
        saveBookmarkOrder(bookmark.getDbId(), bookmark.getCategoryId(), -1); // -1 means append to end
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
        deleteBookmarkOrder(id, null); // Delete order for this bookmark across all categories
    }

    private void deleteBookmark(Bookmark b) {
        String sql = "DELETE FROM " + BOOKMARK_TABLE.getTableName() + " where accountName=? AND categoryTitle=? AND channelId=? AND channelName=?";
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
        String deleteOrderSql = "DELETE FROM " + BOOKMARK_ORDER_TABLE.getTableName() +
                " WHERE bookmark_db_id IN (SELECT id FROM " + BOOKMARK_TABLE.getTableName() + " WHERE accountName=?)";
        String deleteBookmarkSql = "DELETE FROM " + BOOKMARK_TABLE.getTableName() + " WHERE accountName=?";

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

    public void saveBookmarkOrder(String bookmarkDbId, String categoryId, int displayOrder) {
        String deleteSql = "DELETE FROM " + BOOKMARK_ORDER_TABLE.getTableName() + " WHERE bookmark_db_id = ? AND (category_id = ? OR (category_id IS NULL AND ? IS NULL))";
        String insertSql = "INSERT INTO " + BOOKMARK_ORDER_TABLE.getTableName() + " (bookmark_db_id, category_id, display_order) VALUES (?, ?, ?)";
        String updateSql = "UPDATE " + BOOKMARK_ORDER_TABLE.getTableName() + " SET display_order = ? WHERE bookmark_db_id = ? AND (category_id = ? OR (category_id IS NULL AND ? IS NULL))";

        try (Connection conn = connect()) {
            conn.setAutoCommit(false); // Start transaction

            // First, delete any existing order for this bookmark in this category
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                deleteStmt.setString(1, bookmarkDbId);
                if (categoryId == null) {
                    deleteStmt.setNull(2, java.sql.Types.VARCHAR);
                    deleteStmt.setNull(3, java.sql.Types.VARCHAR);
                } else {
                    deleteStmt.setString(2, categoryId);
                    deleteStmt.setString(3, categoryId);
                }
                deleteStmt.executeUpdate();
            }

            // If displayOrder is -1, find the next available order
            if (displayOrder == -1) {
                String maxOrderSql = "SELECT MAX(display_order) FROM " + BOOKMARK_ORDER_TABLE.getTableName() + " WHERE (category_id = ? OR (category_id IS NULL AND ? IS NULL))";
                try (PreparedStatement maxOrderStmt = conn.prepareStatement(maxOrderSql)) {
                    if (categoryId == null) {
                        maxOrderStmt.setNull(1, java.sql.Types.VARCHAR);
                        maxOrderStmt.setNull(2, java.sql.Types.VARCHAR);
                    } else {
                        maxOrderStmt.setString(1, categoryId);
                        maxOrderStmt.setString(2, categoryId);
                    }
                    try (ResultSet rs = maxOrderStmt.executeQuery()) {
                        if (rs.next()) {
                            displayOrder = rs.getInt(1) + 1;
                        } else {
                            displayOrder = 0; // First item
                        }
                    }
                }
            }

            // Insert the new order
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, bookmarkDbId);
                if (categoryId == null) {
                    insertStmt.setNull(2, java.sql.Types.VARCHAR);
                } else {
                    insertStmt.setString(2, categoryId);
                }
                insertStmt.setInt(3, displayOrder);
                insertStmt.executeUpdate();
            }
            conn.commit(); // Commit transaction
        } catch (SQLException e) {
            try {
                connect().rollback(); // Rollback on error
            } catch (SQLException ex) {
                throw new RuntimeException("Failed to rollback transaction", ex);
            }
            throw new RuntimeException("Unable to save bookmark order", e);
        }
    }

    public void updateBookmarkOrders(String categoryId, List<String> orderedBookmarkDbIds) {
        String deleteSql = "DELETE FROM " + BOOKMARK_ORDER_TABLE.getTableName() + " WHERE (category_id = ? OR (category_id IS NULL AND ? IS NULL))";
        String insertSql = "INSERT INTO " + BOOKMARK_ORDER_TABLE.getTableName() + " (bookmark_db_id, category_id, display_order) VALUES (?, ?, ?)";

        try (Connection conn = connect()) {
            conn.setAutoCommit(false); // Start transaction

            // Delete all existing orders for this category
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                if (categoryId == null) {
                    deleteStmt.setNull(1, java.sql.Types.VARCHAR);
                    deleteStmt.setNull(2, java.sql.Types.VARCHAR);
                } else {
                    deleteStmt.setString(1, categoryId);
                    deleteStmt.setString(2, categoryId);
                }
                deleteStmt.executeUpdate();
            }

            // Insert new orders
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                for (int i = 0; i < orderedBookmarkDbIds.size(); i++) {
                    insertStmt.setString(1, orderedBookmarkDbIds.get(i));
                    if (categoryId == null) {
                        insertStmt.setNull(2, java.sql.Types.VARCHAR);
                    } else {
                        insertStmt.setString(2, categoryId);
                    }
                    insertStmt.setInt(3, i);
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
            }
            conn.commit(); // Commit transaction
        } catch (SQLException e) {
            try {
                connect().rollback(); // Rollback on error
            } catch (SQLException ex) {
                throw new RuntimeException("Failed to rollback transaction", ex);
            }
            throw new RuntimeException("Unable to update bookmark orders", e);
        }
    }

    public void deleteBookmarkOrder(String bookmarkDbId, String categoryId) {
        String sql = "DELETE FROM " + BOOKMARK_ORDER_TABLE.getTableName() + " WHERE bookmark_db_id = ? AND (category_id = ? OR (category_id IS NULL AND ? IS NULL))";
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

    public void deleteBookmarkOrdersByCategory(String categoryId) {
        String sql = "DELETE FROM " + BOOKMARK_ORDER_TABLE.getTableName() + " WHERE (category_id = ? OR (category_id IS NULL AND ? IS NULL))";
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
        String sql = "DELETE FROM " + BOOKMARK_CATEGORY_TABLE.getTableName() + " where id=?";
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
