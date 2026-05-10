package com.uiptv.util

import com.uiptv.db.DatabasePatchesUtils
import com.uiptv.db.DatabaseUtils
import java.io.File
import java.io.IOException
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.util.stream.Collectors

object SQLiteTableSync {
    private const val SQLITE_PREFIX = "jdbc:sqlite:"
    private const val SQL_SELECT = "SELECT "
    private const val SQL_FROM = " FROM "
    private const val SQL_INSERT_INTO = "INSERT INTO "
    private const val SQL_VALUES = ") VALUES ("
    private const val ACCOUNT_ID = "accountId"
    private val CONFIGURATION_SYNC_EXCLUDED_COLUMNS = setOf("filterLockHash")

    @JvmStatic
    @Throws(SQLException::class)
    fun ensureDatabaseReady(dbPath: String) {
        try {
            val file = File(dbPath)
            val parent = file.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
                throw IOException("Unable to create parent directory for $dbPath")
            }
            if (!file.exists() && !file.createNewFile() && !file.exists()) {
                throw IOException("Unable to create database file $dbPath")
            }
        } catch (e: IOException) {
            throw SQLException("Unable to prepare database file $dbPath", e)
        }

        DriverManager.getConnection(SQLITE_PREFIX + dbPath).use { conn ->
            DatabasePatchesUtils.applyPatches(conn)
        }
    }

    @JvmStatic
    @Throws(SQLException::class)
    fun syncTables(sourceDBPath: String, targetDBPath: String, table: DatabaseUtils.DbTable): Int {
        val tableName = DatabaseUtils.validatedTableName(table)
        DriverManager.getConnection(SQLITE_PREFIX + sourceDBPath).use { sourceConn ->
            DriverManager.getConnection(SQLITE_PREFIX + targetDBPath).use { targetConn ->
                val syncedRows = syncTable(sourceConn, targetConn, tableName)
                AppLog.addInfoLog(SQLiteTableSync::class.java, "Table '$tableName' synced from source to target.")
                return syncedRows
            }
        }
    }

    @JvmStatic
    @Throws(SQLException::class)
    fun replaceTable(sourceDBPath: String, targetDBPath: String, table: DatabaseUtils.DbTable): Int {
        val tableName = DatabaseUtils.validatedTableName(table)
        DriverManager.getConnection(SQLITE_PREFIX + sourceDBPath).use { sourceConn ->
            DriverManager.getConnection(SQLITE_PREFIX + targetDBPath).use { targetConn ->
                val syncedRows = replaceTable(sourceConn, targetConn, tableName)
                AppLog.addInfoLog(SQLiteTableSync::class.java, "Table '$tableName' replaced from source to target.")
                return syncedRows
            }
        }
    }

    @JvmStatic
    @Throws(SQLException::class)
    fun syncConfiguration(sourceDBPath: String, targetDBPath: String, includeExternalPlayerPaths: Boolean): Boolean {
        DriverManager.getConnection(SQLITE_PREFIX + sourceDBPath).use { sourceConn ->
            DriverManager.getConnection(SQLITE_PREFIX + targetDBPath).use { targetConn ->
                val synced = syncConfiguration(sourceConn, targetConn, includeExternalPlayerPaths)
                AppLog.addInfoLog(SQLiteTableSync::class.java, "Configuration synced from source to target.")
                return synced
            }
        }
    }

    @JvmStatic
    @Throws(SQLException::class)
    fun syncPublishedM3uSelections(sourceDBPath: String, targetDBPath: String): Int {
        DriverManager.getConnection(SQLITE_PREFIX + sourceDBPath).use { sourceConn ->
            DriverManager.getConnection(SQLITE_PREFIX + targetDBPath).use { targetConn ->
                val synced = syncPublishedM3uSelections(sourceConn, targetConn)
                AppLog.addInfoLog(SQLiteTableSync::class.java, "PublishedM3uSelection synced from source to target with account remapping.")
                return synced
            }
        }
    }

    @JvmStatic
    @Throws(SQLException::class)
    fun syncPublishedM3uCategorySelections(sourceDBPath: String, targetDBPath: String): Int {
        DriverManager.getConnection(SQLITE_PREFIX + sourceDBPath).use { sourceConn ->
            DriverManager.getConnection(SQLITE_PREFIX + targetDBPath).use { targetConn ->
                val synced = syncSelectionTableWithAccountRemap(
                    sourceConn,
                    targetConn,
                    DatabaseUtils.DbTable.PUBLISHED_M3U_CATEGORY_SELECTION_TABLE.tableName,
                    listOf("categoryName", "selected")
                )
                AppLog.addInfoLog(SQLiteTableSync::class.java, "PublishedM3uCategorySelection synced from source to target with account remapping.")
                return synced
            }
        }
    }

    @JvmStatic
    @Throws(SQLException::class)
    fun syncPublishedM3uChannelSelections(sourceDBPath: String, targetDBPath: String): Int {
        DriverManager.getConnection(SQLITE_PREFIX + sourceDBPath).use { sourceConn ->
            DriverManager.getConnection(SQLITE_PREFIX + targetDBPath).use { targetConn ->
                val synced = syncSelectionTableWithAccountRemap(
                    sourceConn,
                    targetConn,
                    DatabaseUtils.DbTable.PUBLISHED_M3U_CHANNEL_SELECTION_TABLE.tableName,
                    listOf("categoryName", "channelId", "selected")
                )
                AppLog.addInfoLog(SQLiteTableSync::class.java, "PublishedM3uChannelSelection synced from source to target with account remapping.")
                return synced
            }
        }
    }

    @Throws(SQLException::class)
    private fun syncTable(sourceConn: Connection, targetConn: Connection, tableName: String): Int {
        val targetColumns = getTableColumns(targetConn, tableName)
        val targetColumnSet = LinkedHashSet(targetColumns)
        val sourceColumns = getTableColumns(sourceConn, tableName)
        val commonColumns = sourceColumns.filter { targetColumnSet.contains(it) }
        if (commonColumns.isEmpty()) {
            throw SQLException("No common columns found for table $tableName")
        }
        val columnList = commonColumns.joinToString(", ")
        val placeholders = commonColumns.joinToString(", ") { "?" }
        val selectSql = SQL_SELECT + columnList + SQL_FROM + tableName
        val insertSql = "INSERT OR REPLACE INTO $tableName ($columnList$SQL_VALUES$placeholders)"

        sourceConn.createStatement().use { sourceStmt ->
            sourceStmt.executeQuery(selectSql).use { sourceResult ->
                targetConn.prepareStatement(insertSql).use { targetStatement ->
                    val columnCount = commonColumns.size
                    var syncedRows = 0
                    while (sourceResult.next()) {
                        for (i in 1..columnCount) {
                            targetStatement.setObject(i, sourceResult.getObject(i))
                        }
                        targetStatement.addBatch()
                        syncedRows++
                    }
                    targetStatement.executeBatch()
                    return syncedRows
                }
            }
        }
    }

    @Throws(SQLException::class)
    private fun replaceTable(sourceConn: Connection, targetConn: Connection, tableName: String): Int {
        val targetColumns = getTableColumns(targetConn, tableName)
        val targetColumnSet = LinkedHashSet(targetColumns)
        val sourceColumns = getTableColumns(sourceConn, tableName)
        val commonColumns = sourceColumns.filter { targetColumnSet.contains(it) }
        if (commonColumns.isEmpty()) {
            throw SQLException("No common columns found for table $tableName")
        }

        val columnList = commonColumns.joinToString(", ")
        val placeholders = commonColumns.joinToString(", ") { "?" }
        val selectSql = SQL_SELECT + columnList + SQL_FROM + tableName
        val deleteSql = "DELETE FROM $tableName"
        val insertSql = "$SQL_INSERT_INTO$tableName ($columnList$SQL_VALUES$placeholders)"

        val originalAutoCommit = targetConn.autoCommit
        try {
            sourceConn.createStatement().use { sourceStmt ->
                sourceStmt.executeQuery(selectSql).use { sourceResult ->
                    targetConn.createStatement().use { deleteStatement ->
                        targetConn.prepareStatement(insertSql).use { targetStatement ->
                            targetConn.autoCommit = false
                            deleteStatement.executeUpdate(deleteSql)
                            val columnCount = commonColumns.size
                            var syncedRows = 0
                            while (sourceResult.next()) {
                                for (i in 1..columnCount) {
                                    targetStatement.setObject(i, sourceResult.getObject(i))
                                }
                                targetStatement.addBatch()
                                syncedRows++
                            }
                            targetStatement.executeBatch()
                            targetConn.commit()
                            return syncedRows
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            targetConn.rollback()
            throw e
        } finally {
            targetConn.autoCommit = originalAutoCommit
        }
    }

    @Throws(SQLException::class)
    private fun syncConfiguration(sourceConn: Connection, targetConn: Connection, includeExternalPlayerPaths: Boolean): Boolean {
        val sourceColumns = getTableColumns(sourceConn, DatabaseUtils.DbTable.CONFIGURATION_TABLE.tableName)
        val targetColumns = getTableColumns(targetConn, DatabaseUtils.DbTable.CONFIGURATION_TABLE.tableName)
        val targetColumnSet = LinkedHashSet(targetColumns)
        val commonColumns = sourceColumns.filter { targetColumnSet.contains(it) && !CONFIGURATION_SYNC_EXCLUDED_COLUMNS.contains(it) }
        if (commonColumns.isEmpty()) {
            return false
        }

        val sourceRow = readFirstConfigurationRow(sourceConn, commonColumns) ?: return false
        val targetRow = readFirstConfigurationRow(targetConn, commonColumns)
        if (!includeExternalPlayerPaths && targetRow != null) {
            sourceRow.copyColumnIfPresentFrom(targetRow, "playerPath1")
            sourceRow.copyColumnIfPresentFrom(targetRow, "playerPath2")
            sourceRow.copyColumnIfPresentFrom(targetRow, "playerPath3")
            sourceRow.copyColumnIfPresentFrom(targetRow, "defaultPlayerPath")
        }
        upsertFirstConfigurationRow(targetConn, sourceRow, targetRow?.id)
        return true
    }

    @Throws(SQLException::class)
    private fun syncPublishedM3uSelections(sourceConn: Connection, targetConn: Connection): Int =
        syncSelectionTableWithAccountRemap(sourceConn, targetConn, DatabaseUtils.DbTable.PUBLISHED_M3U_SELECTION_TABLE.tableName, emptyList())

    @Throws(SQLException::class)
    private fun syncSelectionTableWithAccountRemap(
        sourceConn: Connection,
        targetConn: Connection,
        selectionTable: String,
        extraColumns: List<String>
    ): Int {
        val accountTable = DatabaseUtils.DbTable.ACCOUNT_TABLE.tableName
        val originalAutoCommit = targetConn.autoCommit
        var syncedRows = 0
        val selectionColumns = buildSelectionColumnList(extraColumns)
        val placeholders = buildSelectionPlaceholders(extraColumns.size + 1)

        try {
            sourceConn.createStatement().use { sourceSelections ->
                sourceSelections.executeQuery(SQL_SELECT + selectionColumns + SQL_FROM + selectionTable + " ORDER BY id").use { sourceRows ->
                    sourceConn.prepareStatement("SELECT accountName FROM $accountTable WHERE id = ?").use { sourceAccountName ->
                        targetConn.prepareStatement("SELECT id FROM $accountTable WHERE accountName = ?").use { targetAccountId ->
                            targetConn.createStatement().use { deleteTargetSelections ->
                                targetConn.prepareStatement("$SQL_INSERT_INTO$selectionTable ($selectionColumns$SQL_VALUES$placeholders)").use { insertTargetSelection ->
                                    targetConn.autoCommit = false
                                    deleteTargetSelections.executeUpdate("DELETE FROM $selectionTable")
                                    while (sourceRows.next()) {
                                        val targetSelectionAccountId = resolveTargetAccountId(sourceRows, sourceAccountName, targetAccountId)
                                        if (targetSelectionAccountId != null) {
                                            insertTargetSelection.setString(1, targetSelectionAccountId)
                                            for (i in extraColumns.indices) {
                                                insertTargetSelection.setObject(i + 2, sourceRows.getObject(extraColumns[i]))
                                            }
                                            insertTargetSelection.addBatch()
                                            syncedRows++
                                        }
                                    }
                                    if (syncedRows > 0) {
                                        insertTargetSelection.executeBatch()
                                    }
                                    targetConn.commit()
                                    return syncedRows
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            targetConn.rollback()
            throw e
        } finally {
            targetConn.autoCommit = originalAutoCommit
        }
    }

    private fun buildSelectionColumnList(extraColumns: List<String>): String {
        val columns = ArrayList<String>()
        columns.add(ACCOUNT_ID)
        columns.addAll(extraColumns)
        return columns.joinToString(", ")
    }

    private fun buildSelectionPlaceholders(count: Int): String =
        (0 until count).joinToString(", ") { "?" }

    @Throws(SQLException::class)
    private fun readFirstConfigurationRow(conn: Connection, columns: List<String>): ConfigurationRow? {
        val columnList = columns.filter { !it.equals("id", true) }.joinToString(", ")
        if (columnList.isBlank()) {
            return null
        }
        val sql = SQL_SELECT + "id, " + columnList + SQL_FROM + DatabaseUtils.DbTable.CONFIGURATION_TABLE.tableName + " ORDER BY id LIMIT 1"
        conn.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                if (!rs.next()) {
                    return null
                }
                val row = ConfigurationRow(rs.getObject("id"))
                for (column in columns) {
                    if (column.equals("id", true)) continue
                    row.put(column, rs.getObject(column))
                }
                return row
            }
        }
    }

    @Throws(SQLException::class)
    private fun upsertFirstConfigurationRow(conn: Connection, row: ConfigurationRow, targetId: Any?) {
        val columns = ArrayList(row.columns())
        if (columns.isEmpty()) {
            return
        }
        if (targetId == null) {
            val columnList = columns.joinToString(", ")
            val placeholders = columns.joinToString(", ") { "?" }
            val sql = SQL_INSERT_INTO + DatabaseUtils.DbTable.CONFIGURATION_TABLE.tableName + " (" + columnList + SQL_VALUES + placeholders + ")"
            conn.prepareStatement(sql).use { statement ->
                bindColumns(statement, columns, row)
                statement.executeUpdate()
            }
            return
        }

        val assignments = columns.joinToString(", ") { "$it=?" }
        val sql = "UPDATE " + DatabaseUtils.DbTable.CONFIGURATION_TABLE.tableName + " SET " + assignments + " WHERE id = ?"
        conn.prepareStatement(sql).use { statement ->
            bindColumns(statement, columns, row)
            statement.setObject(columns.size + 1, targetId)
            statement.executeUpdate()
        }
    }

    @Throws(SQLException::class)
    private fun bindColumns(statement: PreparedStatement, columns: List<String>, row: ConfigurationRow) {
        for (i in columns.indices) {
            statement.setObject(i + 1, row.get(columns[i]))
        }
    }

    @Throws(SQLException::class)
    private fun resolveTargetAccountId(
        sourceRows: ResultSet,
        sourceAccountName: PreparedStatement,
        targetAccountId: PreparedStatement
    ): String? {
        val sourceAccountId = sourceRows.getString(ACCOUNT_ID)
        if (sourceAccountId.isNullOrBlank()) {
            return null
        }
        sourceAccountName.setString(1, sourceAccountId)
        sourceAccountName.executeQuery().use { sourceAccount ->
            if (!sourceAccount.next()) {
                return null
            }
            val accountName = sourceAccount.getString("accountName")
            if (accountName.isNullOrBlank()) {
                return null
            }
            targetAccountId.setString(1, accountName)
            targetAccountId.executeQuery().use { targetAccount ->
                return if (targetAccount.next()) targetAccount.getString("id") else null
            }
        }
    }

    @Throws(SQLException::class)
    private fun getTableColumns(conn: Connection, tableName: String): List<String> {
        val columns = ArrayList<String>()
        conn.createStatement().use { stmt ->
            stmt.executeQuery("PRAGMA table_info($tableName)").use { rs ->
                while (rs.next()) {
                    columns.add(rs.getString("name"))
                }
            }
        }
        return columns
    }

    private class ConfigurationRow(val id: Any?) {
        private val values = LinkedHashMap<String, Any?>()
        fun put(column: String, value: Any?) {
            values[column] = value
        }
        fun get(column: String): Any? = values[column]
        fun columns(): Set<String> = values.keys
        fun copyColumnIfPresentFrom(source: ConfigurationRow?, column: String) {
            if (source != null && values.containsKey(column) && source.values.containsKey(column)) {
                values[column] = source.values[column]
            }
        }
    }
}
