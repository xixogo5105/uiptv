package com.uiptv.mobile.shared.db

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
        val stagedSnapshot = File.createTempFile("uiptv-remote-sync-staged-", ".db", tempDirectory)
        try {
            snapshotFile.copyTo(stagedSnapshot, overwrite = true)
            migrateAndValidate(stagedSnapshot)
            val report = buildCloneReport(stagedSnapshot)
            checkpointLiveDatabase()
            databaseHelper.close()
            replaceLiveDatabase(stagedSnapshot)
            reopenAndValidateLiveDatabase()
            return report
        } finally {
            stagedSnapshot.delete()
        }
    }

    private fun migrateAndValidate(databaseFile: File) {
        val db = SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            requireIntegrity(db)
            databaseHelper.applyMigrations(db)
            requireIntegrity(db)
            db.version = UiptvSchemaInfo.SCHEMA_VERSION_CODE
        } finally {
            db.close()
        }
    }

    private fun buildCloneReport(databaseFile: File): DatabaseSyncReport {
        val db = SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return db.use {
            DatabaseSyncReport(
                tableResults = UiptvSyncSchema.syncableTables
                    .filter { table -> tableExists(db, table) }
                    .map { table -> TableSyncResult(table, countRows(db, table)) },
                configurationRequested = true,
                configurationCopied = tableExists(db, "Configuration") && countRows(db, "Configuration") > 0,
                externalPlayerPathsIncluded = true
            )
        }
    }

    private fun checkpointLiveDatabase() {
        databaseHelper.writableDatabase.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
            if (cursor.moveToFirst()) {
                // Reading the row forces SQLite to finish the checkpoint before file replacement.
            }
        }
    }

    private fun replaceLiveDatabase(stagedSnapshot: File) {
        val databaseFile = databaseHelper.databaseFile()
        databaseFile.parentFile?.mkdirs()
        deleteDatabaseSidecars(databaseFile)
        stagedSnapshot.copyTo(databaseFile, overwrite = true)
        deleteDatabaseSidecars(databaseFile)
    }

    private fun reopenAndValidateLiveDatabase() {
        val db = databaseHelper.writableDatabase
        databaseHelper.applyMigrations(db)
        requireIntegrity(db)
    }

    private fun requireIntegrity(db: SQLiteDatabase) {
        db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
            require(cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)) {
                "Synced database failed integrity check."
            }
        }
    }

    private fun deleteDatabaseSidecars(databaseFile: File) {
        File(databaseFile.absolutePath + "-wal").delete()
        File(databaseFile.absolutePath + "-shm").delete()
    }

    private fun countRows(db: SQLiteDatabase, table: String): Int {
        db.rawQuery("SELECT COUNT(*) FROM ${quoteIdentifier(table)}", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean {
        db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private fun quoteIdentifier(identifier: String): String =
        "\"" + identifier.replace("\"", "\"\"") + "\""
}
