package com.uiptv.mobile.shared.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.uiptv.mobile.shared.sync.RemoteSnapshotApplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidSQLiteSnapshotSyncApplier(
    private val databaseHelper: AndroidUiptvDatabaseHelper,
    private val tempDirectory: File
) : RemoteSnapshotApplier {
    override suspend fun apply(snapshot: ByteArray): DatabaseSyncReport = withContext(Dispatchers.IO) {
        val snapshotFile = File.createTempFile("uiptv-remote-sync-", ".db", tempDirectory)
        try {
            snapshotFile.writeBytes(snapshot)
            applyFile(snapshotFile)
        } finally {
            snapshotFile.delete()
        }
    }

    suspend fun applyFile(snapshotFile: File): DatabaseSyncReport = withContext(Dispatchers.IO) {
        applySnapshotFile(snapshotFile)
    }

    fun createTempSnapshotFile(): File =
        File.createTempFile("uiptv-remote-sync-", ".db", tempDirectory)

    fun createTempTransferFile(suffix: String = ".bin"): File =
        File.createTempFile("uiptv-remote-transfer-", suffix, tempDirectory)

    fun apply(snapshotFile: File): DatabaseSyncReport =
        applySnapshotFile(snapshotFile)

    private fun applySnapshotFile(snapshotFile: File): DatabaseSyncReport {
        val target = databaseHelper.writableDatabase
        val source = SQLiteDatabase.openDatabase(
            snapshotFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        return source.use {
            syncDatabases(source, target)
        }
    }

    private fun syncDatabases(source: SQLiteDatabase, target: SQLiteDatabase): DatabaseSyncReport {
        val results = mutableListOf<TableSyncResult>()
        target.beginTransaction()
        try {
            UiptvSyncSchema.syncableTables.forEach { table ->
                results += if (table == "Configuration") {
                    TableSyncResult(table, syncFilterConfiguration(source, target))
                } else {
                    TableSyncResult(table, syncTable(source, target, table))
                }
            }
            removeUnsupportedSyncedAccounts(target)
            target.setTransactionSuccessful()
        } finally {
            target.endTransaction()
        }
        return DatabaseSyncReport(results)
    }

    private fun syncTable(source: SQLiteDatabase, target: SQLiteDatabase, table: String): Int {
        val sourceColumns = tableColumns(source, table)
        if (sourceColumns.isEmpty()) {
            return 0
        }
        val targetColumns = tableColumns(target, table)
        val commonColumns = UiptvSyncSchema.commonSyncColumns(sourceColumns, targetColumns)
        require(commonColumns.isNotEmpty()) { "No common columns found for table $table" }

        val quotedColumns = commonColumns.joinToString(", ") { quoteIdentifier(it) }

        var syncedRows = 0
        val whereClause = if (table == "Account" && "type" in sourceColumns) {
            " WHERE UPPER(COALESCE(type, '')) <> 'RSS_FEED'"
        } else {
            ""
        }
        source.rawQuery("SELECT $quotedColumns FROM ${quoteIdentifier(table)}$whereClause", null).use { cursor ->
            if (table in replaceOnSyncTables) {
                target.delete(table, null, null)
            }
            while (cursor.moveToNext()) {
                val values = ContentValues(commonColumns.size)
                commonColumns.forEachIndexed { index, column ->
                    values.putCursorValue(column, cursor, index)
                }
                target.insertWithOnConflict(
                    table,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                syncedRows++
            }
        }
        return syncedRows
    }

    private fun syncFilterConfiguration(source: SQLiteDatabase, target: SQLiteDatabase): Int {
        val sourceColumns = tableColumns(source, "Configuration")
        val targetColumns = tableColumns(target, "Configuration")
        val commonColumns = UiptvSyncSchema.commonSyncColumns(sourceColumns, targetColumns)
            .filterNot { it == "id" || it in UiptvSyncSchema.androidNeverSyncConfigurationColumns }
        if (commonColumns.isEmpty()) {
            return 0
        }
        val selectColumns = commonColumns.joinToString(", ") { quoteIdentifier(it) }
        source.rawQuery("SELECT $selectColumns FROM Configuration ORDER BY id LIMIT 1", null).use { cursor ->
            if (!cursor.moveToFirst()) {
                return 0
            }
            val values = ContentValues(commonColumns.size)
            commonColumns.forEachIndexed { index, column ->
                values.putCursorValue(column, cursor, index)
            }
            val targetId = ensureConfigurationRow(target)
            val updatedRows = target.update(
                "Configuration",
                values,
                "id = ?",
                arrayOf(targetId.toString())
            )
            if (updatedRows == 0) {
                values.put("id", targetId)
                target.insertWithOnConflict("Configuration", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            return 1
        }
    }

    private fun ensureConfigurationRow(db: SQLiteDatabase): Long {
        db.rawQuery("SELECT id FROM Configuration ORDER BY id LIMIT 1", null).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        return db.insertWithOnConflict(
            "Configuration",
            null,
            ContentValues().apply {
                put("id", 1L)
                put("filterCategoriesList", "")
                put("filterChannelsList", "")
                put("pauseFiltering", "0")
                put("enableThumbnails", "0")
            },
            SQLiteDatabase.CONFLICT_REPLACE
        ).takeIf { it > 0 } ?: 1L
    }

    private fun removeUnsupportedSyncedAccounts(db: SQLiteDatabase) {
        val rssAccountIds = mutableListOf<String>()
        db.rawQuery(
            "SELECT id FROM Account WHERE UPPER(COALESCE(type, '')) = 'RSS_FEED'",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rssAccountIds += cursor.getLong(0).toString()
            }
        }
        rssAccountIds.forEach { accountId ->
            db.execSQL(
                "DELETE FROM Channel WHERE categoryId IN (SELECT id FROM Category WHERE accountId = ?)",
                arrayOf(accountId)
            )
            db.execSQL(
                "DELETE FROM VodChannel WHERE categoryId IN (SELECT categoryId FROM VodCategory WHERE accountId = ?)",
                arrayOf(accountId)
            )
            db.execSQL(
                "DELETE FROM SeriesEpisode WHERE accountId = ? OR seriesId IN (SELECT channelId FROM SeriesChannel WHERE accountId = ?)",
                arrayOf(accountId, accountId)
            )
            db.delete("SeriesChannel", "accountId = ?", arrayOf(accountId))
            db.delete("AccountInfo", "accountId = ?", arrayOf(accountId))
            db.delete("VodWatchState", "accountId = ?", arrayOf(accountId))
            db.delete("SeriesWatchState", "accountId = ?", arrayOf(accountId))
            db.delete("SeriesWatchingNowSnapshot", "accountId = ?", arrayOf(accountId))
            db.delete("Category", "accountId = ?", arrayOf(accountId))
            db.delete("VodCategory", "accountId = ?", arrayOf(accountId))
            db.delete("SeriesCategory", "accountId = ?", arrayOf(accountId))
            db.delete("Account", "id = ?", arrayOf(accountId))
        }
    }

    private fun tableColumns(db: SQLiteDatabase, table: String): List<String> {
        db.rawQuery("PRAGMA table_info(${quoteIdentifier(table)})", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
            return columns
        }
    }

    private fun ContentValues.putCursorValue(column: String, cursor: Cursor, index: Int) {
        when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> putNull(column)
            Cursor.FIELD_TYPE_INTEGER -> put(column, cursor.getLong(index))
            Cursor.FIELD_TYPE_FLOAT -> put(column, cursor.getDouble(index))
            Cursor.FIELD_TYPE_BLOB -> put(column, cursor.getBlob(index))
            else -> put(column, cursor.getString(index))
        }
    }

    private fun quoteIdentifier(identifier: String): String =
        "\"" + identifier.replace("\"", "\"\"") + "\""

    private companion object {
        private val replaceOnSyncTables = setOf(
            "PublishedM3uSelection",
            "PublishedM3uCategorySelection",
            "PublishedM3uChannelSelection"
        )
    }
}
