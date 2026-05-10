package com.uiptv.db

import com.uiptv.model.PublishedM3uCategorySelection
import com.uiptv.util.StringUtils.isBlank
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException

class PublishedM3uCategorySelectionDb : BaseDb<PublishedM3uCategorySelection>(DatabaseUtils.DbTable.PUBLISHED_M3U_CATEGORY_SELECTION_TABLE) {
    companion object {
        private var instance: PublishedM3uCategorySelectionDb? = null

        @JvmStatic
        @Synchronized
        fun get(): PublishedM3uCategorySelectionDb {
            if (instance == null) {
                instance = PublishedM3uCategorySelectionDb()
            }
            return instance!!
        }
    }

    override fun populate(resultSet: ResultSet): PublishedM3uCategorySelection {
        val selection = PublishedM3uCategorySelection()
        selection.dbId = nullSafeString(resultSet, "id")
        selection.accountId = nullSafeString(resultSet, "accountId")
        selection.categoryName = nullSafeString(resultSet, "categoryName")
        selection.selected = safeBoolean(resultSet, "selected")
        return selection
    }

    fun getAllSelections(): List<PublishedM3uCategorySelection> = getAll(" ORDER BY id", emptyArray())

    @Throws(SQLException::class)
    fun replaceSelections(conn: Connection, selections: List<PublishedM3uCategorySelection>?) {
        conn.prepareStatement("DELETE FROM ${DatabaseUtils.DbTable.PUBLISHED_M3U_CATEGORY_SELECTION_TABLE.tableName}").use {
            it.executeUpdate()
        }
        if (selections.isNullOrEmpty()) {
            return
        }
        conn.prepareStatement(DatabaseUtils.insertTableSql(DatabaseUtils.DbTable.PUBLISHED_M3U_CATEGORY_SELECTION_TABLE)).use { insert ->
            selections.forEach { selection ->
                if (selection != null && !isBlank(selection.accountId) && !isBlank(selection.categoryName)) {
                    insert.setString(1, selection.accountId)
                    insert.setString(2, selection.categoryName)
                    insert.setString(3, if (selection.selected) "1" else "0")
                    insert.addBatch()
                }
            }
            insert.executeBatch()
        }
    }

    fun deleteByAccountId(accountId: String?) {
        if (isBlank(accountId)) {
            return
        }
        val sql = "DELETE FROM ${DatabaseUtils.DbTable.PUBLISHED_M3U_CATEGORY_SELECTION_TABLE.tableName} WHERE accountId=?"
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(sql).use { statement ->
                    statement.setString(1, accountId)
                    statement.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to delete published M3U category selection", e)
        }
    }
}
