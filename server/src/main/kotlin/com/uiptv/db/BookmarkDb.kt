package com.uiptv.db

import com.uiptv.model.Account
import com.uiptv.model.Bookmark
import com.uiptv.model.BookmarkCategory
import com.uiptv.util.StringUtils.isNotBlank
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Types
import java.util.Comparator

class BookmarkDb : BaseDb<Bookmark>(DatabaseUtils.DbTable.BOOKMARK_TABLE) {
    companion object {
        private const val DELETE_FROM = "DELETE FROM "
        private const val WHERE_BOOKMARK_DB_ID = " WHERE bookmark_db_id = ?"
        private const val AND_NULLABLE_CATEGORY_ID = " AND (category_id = ? OR (category_id IS NULL AND ? IS NULL))"
        private const val WHERE_NULLABLE_CATEGORY_ID = " WHERE (category_id = ? OR (category_id IS NULL AND ? IS NULL))"

        private var instance: BookmarkDb? = null

        @JvmStatic
        @Synchronized
        fun get(): BookmarkDb {
            if (instance == null) {
                instance = BookmarkDb()
            }
            return instance!!
        }
    }

    fun getBookmarks(): List<Bookmark> = getBookmarksOrdered(null)

    fun getBookmarksPage(offset: Int, limit: Int): List<Bookmark> =
        if (limit <= 0) getBookmarksOrdered(null) else getBookmarksOrdered(null, offset.coerceAtLeast(0), limit)

    fun getBookmarksByCategory(categoryId: String?): List<Bookmark> = getBookmarksOrdered(categoryId)

    private fun getBookmarksOrdered(categoryId: String?): List<Bookmark> = getBookmarksOrdered(categoryId, -1, -1)

    private fun getBookmarksOrdered(categoryId: String?, offset: Int, limit: Int): List<Bookmark> {
        val bookmarks = ArrayList<Bookmark>()
        val sql = StringBuilder("SELECT b.*, bo.display_order FROM ")
            .append(DatabaseUtils.DbTable.BOOKMARK_TABLE.tableName).append(" b ")
            .append("LEFT JOIN (SELECT bookmark_db_id, MIN(display_order) AS display_order FROM ")
            .append(DatabaseUtils.DbTable.BOOKMARK_ORDER_TABLE.tableName)
            .append(" GROUP BY bookmark_db_id) bo ON b.id = bo.bookmark_db_id ")
            .apply {
                if (categoryId != null) append("WHERE b.categoryId = ? ")
            }
            .append("ORDER BY CASE WHEN bo.display_order IS NULL THEN 1 ELSE 0 END, bo.display_order ASC, b.id ASC")
            .apply {
                if (limit > 0) append(" LIMIT ? OFFSET ? ")
            }
            .toString()
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(sql).use { statement ->
                    var index = 1
                    if (categoryId != null) {
                        statement.setString(index++, categoryId)
                    }
                    if (limit > 0) {
                        statement.setInt(index++, limit.coerceAtLeast(0))
                        statement.setInt(index, offset.coerceAtLeast(0))
                    }
                    statement.executeQuery().use { resultSet ->
                        while (resultSet.next()) {
                            bookmarks += populate(resultSet)
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to execute query for ordered bookmarks", e)
        }
        return bookmarks
    }

    fun getBookmarkById(b: Bookmark): Bookmark? =
        getAll(
            " where accountName=? AND categoryTitle=? AND channelId=? AND channelName=?",
            arrayOf(b.accountName ?: "", b.categoryTitle ?: "", b.channelId ?: "", b.channelName ?: "")
        ).firstOrNull()

    fun save(bookmark: Bookmark): Boolean {
        val existing = getBookmarkById(bookmark)
        return if (existing != null) {
            bookmark.dbId = existing.dbId
            update(bookmark)
            false
        } else {
            insert(bookmark)
            true
        }
    }

    private fun insert(bookmark: Bookmark) {
        val sql = DatabaseUtils.insertTableSql(DatabaseUtils.DbTable.BOOKMARK_TABLE)
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { statement ->
                    var i = 1
                    statement.setString(i++, bookmark.accountName)
                    statement.setString(i++, bookmark.categoryTitle)
                    statement.setString(i++, bookmark.channelId)
                    statement.setString(i++, bookmark.channelName)
                    statement.setString(i++, bookmark.cmd)
                    statement.setString(i++, bookmark.serverPortalUrl)
                    statement.setString(i++, bookmark.categoryId)
                    statement.setString(i++, bookmark.accountAction?.name)
                    statement.setString(i++, bookmark.drmType)
                    statement.setString(i++, bookmark.drmLicenseUrl)
                    statement.setString(i++, bookmark.clearKeysJson)
                    statement.setString(i++, bookmark.inputstreamaddon)
                    statement.setString(i++, bookmark.manifestType)
                    statement.setString(i++, bookmark.categoryJson)
                    statement.setString(i++, bookmark.channelJson)
                    statement.setString(i++, bookmark.vodJson)
                    statement.setString(i, bookmark.seriesJson)
                    statement.execute()
                    statement.generatedKeys.use { rs ->
                        if (rs.next()) {
                            bookmark.dbId = rs.getString(1)
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to execute insert query for bookmark", e)
        }
    }

    private fun update(bookmark: Bookmark) {
        val sql = "UPDATE ${DatabaseUtils.DbTable.BOOKMARK_TABLE.tableName} " +
            "SET accountName=?, categoryTitle=?, channelId=?, channelName=?, cmd=?, serverPortalUrl=?, categoryId=?, accountAction=?," +
            " drmType=?, drmLicenseUrl=?, clearKeysJson=?, inputstreamaddon=?, manifestType=?, categoryJson=?, channelJson=?, vodJson=?, seriesJson=? WHERE id=?"
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(sql).use { statement ->
                    var i = 1
                    statement.setString(i++, bookmark.accountName)
                    statement.setString(i++, bookmark.categoryTitle)
                    statement.setString(i++, bookmark.channelId)
                    statement.setString(i++, bookmark.channelName)
                    statement.setString(i++, bookmark.cmd)
                    statement.setString(i++, bookmark.serverPortalUrl)
                    statement.setString(i++, bookmark.categoryId)
                    statement.setString(i++, bookmark.accountAction?.name)
                    statement.setString(i++, bookmark.drmType)
                    statement.setString(i++, bookmark.drmLicenseUrl)
                    statement.setString(i++, bookmark.clearKeysJson)
                    statement.setString(i++, bookmark.inputstreamaddon)
                    statement.setString(i++, bookmark.manifestType)
                    statement.setString(i++, bookmark.categoryJson)
                    statement.setString(i++, bookmark.channelJson)
                    statement.setString(i++, bookmark.vodJson)
                    statement.setString(i++, bookmark.seriesJson)
                    statement.setString(i, bookmark.dbId)
                    statement.execute()
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to execute update query for bookmark", e)
        }
    }

    fun delete(bookmark: Bookmark) {
        if (isNotBlank(bookmark.dbId)) {
            delete(bookmark.dbId!!)
        }
        deleteBookmark(bookmark)
    }

    override fun delete(id: String) {
        super.delete(id)
        deleteBookmarkOrders(id)
    }

    private fun deleteBookmark(b: Bookmark) {
        val sql = DELETE_FROM + DatabaseUtils.DbTable.BOOKMARK_TABLE.tableName + " where accountName=? AND categoryTitle=? AND channelId=? AND channelName=?"
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(sql).use { statement ->
                    statement.setString(1, b.accountName)
                    statement.setString(2, b.categoryTitle)
                    statement.setString(3, b.channelId)
                    statement.setString(4, b.channelName)
                    statement.execute()
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to execute delete query", e)
        }
    }

    fun deleteByAccountName(accountName: String) {
        val deleteOrderSql = DELETE_FROM + DatabaseUtils.DbTable.BOOKMARK_ORDER_TABLE.tableName +
            " WHERE bookmark_db_id IN (SELECT id FROM ${DatabaseUtils.DbTable.BOOKMARK_TABLE.tableName} WHERE accountName=?)"
        val deleteBookmarkSql = DELETE_FROM + DatabaseUtils.DbTable.BOOKMARK_TABLE.tableName + " WHERE accountName=?"
        try {
            SQLConnection.connect().use { conn ->
                conn.autoCommit = false
                try {
                    conn.prepareStatement(deleteOrderSql).use { stmt ->
                        stmt.setString(1, accountName)
                        stmt.executeUpdate()
                    }
                    conn.prepareStatement(deleteBookmarkSql).use { stmt ->
                        stmt.setString(1, accountName)
                        stmt.executeUpdate()
                    }
                    conn.commit()
                } catch (e: SQLException) {
                    conn.rollback()
                    throw DatabaseAccessException("Unable to delete bookmarks by account name", e)
                } finally {
                    conn.autoCommit = true
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to delete bookmarks by account name", e)
        }
    }

    override fun populate(resultSet: ResultSet): Bookmark {
        val bookmark = Bookmark(
            nullSafeString(resultSet, "accountName"),
            nullSafeString(resultSet, "categoryTitle"),
            nullSafeString(resultSet, "channelId"),
            nullSafeString(resultSet, "channelName"),
            nullSafeString(resultSet, "cmd"),
            nullSafeString(resultSet, "serverPortalUrl"),
            nullSafeString(resultSet, "categoryId")
        )
        bookmark.dbId = nullSafeString(resultSet, "id")
        val accountActionStr = nullSafeString(resultSet, "accountAction")
        if (isNotBlank(accountActionStr)) {
            bookmark.accountAction = Account.AccountAction.valueOf(accountActionStr!!)
        }
        bookmark.drmType = nullSafeString(resultSet, "drmType")
        bookmark.drmLicenseUrl = nullSafeString(resultSet, "drmLicenseUrl")
        bookmark.clearKeysJson = nullSafeString(resultSet, "clearKeysJson")
        bookmark.inputstreamaddon = nullSafeString(resultSet, "inputstreamaddon")
        bookmark.manifestType = nullSafeString(resultSet, "manifestType")
        bookmark.categoryJson = nullSafeString(resultSet, "categoryJson")
        bookmark.channelJson = nullSafeString(resultSet, "channelJson")
        bookmark.vodJson = nullSafeString(resultSet, "vodJson")
        bookmark.seriesJson = nullSafeString(resultSet, "seriesJson")
        return bookmark
    }

    fun saveBookmarkOrder(bookmarkDbId: String, displayOrder: Int) {
        val deleteSql = DELETE_FROM + DatabaseUtils.DbTable.BOOKMARK_ORDER_TABLE.tableName + WHERE_BOOKMARK_DB_ID
        val insertSql = "INSERT INTO ${DatabaseUtils.DbTable.BOOKMARK_ORDER_TABLE.tableName} (bookmark_db_id, category_id, display_order) VALUES (?, NULL, ?)"
        transaction { conn ->
            conn.prepareStatement(deleteSql).use { deleteStmt ->
                deleteStmt.setString(1, bookmarkDbId)
                deleteStmt.executeUpdate()
            }
            conn.prepareStatement(insertSql).use { insertStmt ->
                insertStmt.setString(1, bookmarkDbId)
                insertStmt.setInt(2, displayOrder)
                insertStmt.executeUpdate()
            }
        }
    }

    fun updateBookmarkOrders(bookmarkOrders: Map<String, Int>) {
        val deleteSql = DELETE_FROM + DatabaseUtils.DbTable.BOOKMARK_ORDER_TABLE.tableName
        val insertSql = "INSERT INTO ${DatabaseUtils.DbTable.BOOKMARK_ORDER_TABLE.tableName} (bookmark_db_id, category_id, display_order) VALUES (?, NULL, ?)"
        transaction { conn ->
            conn.prepareStatement(deleteSql).use { it.executeUpdate() }
            conn.prepareStatement(insertSql).use { insertStmt ->
                bookmarkOrders.entries
                    .filter { isNotBlank(it.key) && it.value != null }
                    .sortedWith(Comparator.comparingInt(Map.Entry<String, Int>::value))
                    .forEach { entry ->
                        insertStmt.setString(1, entry.key)
                        insertStmt.setInt(2, entry.value)
                        insertStmt.addBatch()
                    }
                insertStmt.executeBatch()
            }
        }
    }

    fun deleteBookmarkOrder(bookmarkDbId: String, categoryId: String?) {
        val sql = DELETE_FROM + DatabaseUtils.DbTable.BOOKMARK_ORDER_TABLE.tableName + WHERE_BOOKMARK_DB_ID + AND_NULLABLE_CATEGORY_ID
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(sql).use { statement ->
                    statement.setString(1, bookmarkDbId)
                    bindNullableCategoryId(statement, 2, categoryId)
                    statement.execute()
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to delete bookmark order", e)
        }
    }

    fun deleteBookmarkOrders(bookmarkDbId: String) {
        val sql = DELETE_FROM + DatabaseUtils.DbTable.BOOKMARK_ORDER_TABLE.tableName + WHERE_BOOKMARK_DB_ID
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(sql).use { statement ->
                    statement.setString(1, bookmarkDbId)
                    statement.execute()
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to delete bookmark orders", e)
        }
    }

    fun getNextDisplayOrder(): Int {
        val sql = "SELECT COALESCE(MAX(display_order), 0) + 1 FROM ${DatabaseUtils.DbTable.BOOKMARK_ORDER_TABLE.tableName}"
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(sql).use { statement ->
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            return resultSet.getInt(1)
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to read next bookmark display order", e)
        }
        return 1
    }

    fun deleteBookmarkOrdersByCategory(categoryId: String?) {
        val sql = DELETE_FROM + DatabaseUtils.DbTable.BOOKMARK_ORDER_TABLE.tableName + WHERE_NULLABLE_CATEGORY_ID
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(sql).use { statement ->
                    bindNullableCategoryId(statement, 1, categoryId)
                    statement.execute()
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to delete bookmark orders by category", e)
        }
    }

    fun saveCategory(category: BookmarkCategory) {
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(DatabaseUtils.insertTableSql(DatabaseUtils.DbTable.BOOKMARK_CATEGORY_TABLE)).use { statement ->
                    statement.setString(1, category.name)
                    statement.execute()
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to execute query", e)
        }
    }

    fun deleteCategory(category: BookmarkCategory) {
        val sql = DELETE_FROM + DatabaseUtils.DbTable.BOOKMARK_CATEGORY_TABLE.tableName + " where id=?"
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(sql).use { statement ->
                    statement.setString(1, category.id)
                    statement.execute()
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to execute delete query", e)
        }
    }

    fun getAllCategories(): List<BookmarkCategory> {
        val categories = ArrayList<BookmarkCategory>()
        val sql = "SELECT * FROM ${DatabaseUtils.DbTable.BOOKMARK_CATEGORY_TABLE.tableName}"
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(sql).use { statement ->
                    statement.executeQuery().use { resultSet ->
                        while (resultSet.next()) {
                            categories += BookmarkCategory(resultSet.getString("id"), resultSet.getString("name"))
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to execute query", e)
        }
        return categories
    }

    private fun bindNullableCategoryId(statement: java.sql.PreparedStatement, startIndex: Int, categoryId: String?) {
        if (categoryId == null) {
            statement.setNull(startIndex, Types.VARCHAR)
            statement.setNull(startIndex + 1, Types.VARCHAR)
        } else {
            statement.setString(startIndex, categoryId)
            statement.setString(startIndex + 1, categoryId)
        }
    }

    private inline fun transaction(block: (Connection) -> Unit) {
        try {
            SQLConnection.connect().use { conn ->
                conn.autoCommit = false
                try {
                    block(conn)
                    conn.commit()
                } catch (e: SQLException) {
                    conn.rollback()
                    throw DatabaseAccessException("Failed to rollback transaction", e)
                } finally {
                    conn.autoCommit = true
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to execute bookmark transaction", e)
        }
    }
}
