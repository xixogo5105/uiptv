package com.uiptv.db

import com.uiptv.model.PublishedM3uSelection
import com.uiptv.util.StringUtils.isBlank
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException

class PublishedM3uSelectionDb : BaseDb<PublishedM3uSelection>(DatabaseUtils.DbTable.PUBLISHED_M3U_SELECTION_TABLE) {
    companion object {
        private var instance: PublishedM3uSelectionDb? = null

        @JvmStatic
        @Synchronized
        fun get(): PublishedM3uSelectionDb {
            if (instance == null) {
                instance = PublishedM3uSelectionDb()
            }
            return instance!!
        }
    }

    override fun populate(resultSet: ResultSet): PublishedM3uSelection {
        val selection = PublishedM3uSelection()
        selection.dbId = nullSafeString(resultSet, "id")
        selection.accountId = nullSafeString(resultSet, "accountId")
        return selection
    }

    fun getAllSelections(): List<PublishedM3uSelection> = getAll(" ORDER BY id", emptyArray())

    fun replaceSelections(accountIds: Set<String>?) {
        try {
            SQLConnection.connect().use { conn ->
                replaceSelectionsInTransaction(conn, accountIds)
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to replace published M3U selections", e)
        }
    }

    @Throws(SQLException::class)
    fun replaceSelections(conn: Connection, accountIds: Set<String>?) {
        replaceSelectionsOnConnection(conn, accountIds)
    }

    @Throws(SQLException::class)
    private fun replaceSelectionsOnConnection(conn: Connection, accountIds: Set<String>?) {
        conn.prepareStatement("DELETE FROM ${DatabaseUtils.DbTable.PUBLISHED_M3U_SELECTION_TABLE.tableName}").use {
            it.executeUpdate()
        }
        if (!accountIds.isNullOrEmpty()) {
            conn.prepareStatement(DatabaseUtils.insertTableSql(DatabaseUtils.DbTable.PUBLISHED_M3U_SELECTION_TABLE)).use { insert ->
                accountIds.forEach { accountId ->
                    if (!isBlank(accountId)) {
                        insert.setString(1, accountId)
                        insert.addBatch()
                    }
                }
                insert.executeBatch()
            }
        }
    }

    @Throws(SQLException::class)
    private fun replaceSelectionsInTransaction(conn: Connection, accountIds: Set<String>?) {
        val originalAutoCommit = conn.autoCommit
        try {
            conn.autoCommit = false
            replaceSelectionsOnConnection(conn, accountIds)
            conn.commit()
        } catch (e: SQLException) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = originalAutoCommit
        }
    }

    fun deleteByAccountId(accountId: String?) {
        if (isBlank(accountId)) {
            return
        }
        val sql = "DELETE FROM ${DatabaseUtils.DbTable.PUBLISHED_M3U_SELECTION_TABLE.tableName} WHERE accountId=?"
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(sql).use { statement ->
                    statement.setString(1, accountId)
                    statement.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to delete published M3U selection", e)
        }
    }
}
