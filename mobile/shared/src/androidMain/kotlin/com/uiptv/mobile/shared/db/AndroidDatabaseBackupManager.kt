package com.uiptv.mobile.shared.db

import android.content.Context
import android.net.Uri
import com.uiptv.mobile.shared.settings.BackupRestoreResult
import com.uiptv.mobile.shared.settings.MobileBackupArchive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class AndroidDatabaseBackupManager(
    context: Context,
    private val databaseHelper: AndroidUiptvDatabaseHelper
) {
    private val appContext = context.applicationContext

    suspend fun backupToUri(uriString: String): BackupRestoreResult = withContext(Dispatchers.IO) {
        val databaseFile = prepareClosedDatabaseCloneSource()
        val uri = Uri.parse(uriString)
        openOutput(uri).use { output ->
            ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                writeMetadata(zip, databaseFile.length())
                zip.putNextEntry(ZipEntry(MobileBackupArchive.DATABASE_ENTRY))
                FileInputStream(databaseFile).use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
        BackupRestoreResult(
            message = "Backup created: ${MobileBackupArchive.sizeLabel(databaseFile.length())}",
            bytes = databaseFile.length()
        )
    }

    suspend fun restoreFromUri(uriString: String): BackupRestoreResult = withContext(Dispatchers.IO) {
        val restoreDir = File.createTempFile("uiptv-db-restore-", ".tmp", appContext.cacheDir).apply {
            check(delete() && mkdir()) { "Unable to stage database restore." }
        }
        try {
            val stagedDatabase = File(restoreDir, MobileBackupArchive.DATABASE_ENTRY)
            openInput(Uri.parse(uriString)).use { extractDatabase(it, stagedDatabase) }
            val bytes = stagedDatabase.length()
            // Use the same validated, rollback-protected whole-file replacement
            // as desktop sync. Never overwrite the only live copy in place.
            AndroidSQLiteSnapshotSyncApplier(
                databaseHelper,
                requireNotNull(databaseHelper.databaseFile().parentFile).apply { mkdirs() }
            ).applyFile(stagedDatabase)
            BackupRestoreResult(message = "Backup restored. Local data refreshed.", bytes = bytes)
        } finally {
            restoreDir.deleteRecursively()
        }
    }

    private fun prepareClosedDatabaseCloneSource(): File {
        databaseHelper.writableDatabase.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
            if (cursor.moveToFirst()) {
                // Reading the first row forces SQLite to finish the checkpoint before the copy.
            }
        }
        databaseHelper.close()
        val databaseFile = appContext.getDatabasePath(UiptvSchemaInfo.DATABASE_NAME)
        require(databaseFile.exists()) { "Database file does not exist." }
        return databaseFile
    }

    private fun writeMetadata(zip: ZipOutputStream, databaseBytes: Long) {
        val metadata = JSONObject()
            .put("formatVersion", MobileBackupArchive.FORMAT_VERSION)
            .put("databaseName", UiptvSchemaInfo.DATABASE_NAME)
            .put("schemaVersionCode", UiptvSchemaInfo.SCHEMA_VERSION_CODE)
            .put("schemaVersion", UiptvSchemaInfo.CURRENT_SCHEMA_VERSION)
            .put("databaseBytes", databaseBytes)
            .put("createdAtEpochSeconds", System.currentTimeMillis() / 1000L)
        zip.putNextEntry(ZipEntry(MobileBackupArchive.METADATA_ENTRY))
        zip.write(metadata.toString(2).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun extractDatabase(input: InputStream, stagedDatabase: File) {
        var databaseFound = false
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name == MobileBackupArchive.DATABASE_ENTRY) {
                    FileOutputStream(stagedDatabase).use { output -> zip.copyTo(output) }
                    databaseFound = true
                }
                zip.closeEntry()
            }
        }
        require(databaseFound) { "Backup does not contain ${MobileBackupArchive.DATABASE_ENTRY}." }
        require(stagedDatabase.length() > 0L) { "Backup database is empty." }
    }

    private fun openOutput(uri: Uri): OutputStream =
        if (uri.scheme == "file") {
            FileOutputStream(requireNotNull(uri.path) { "Invalid backup file path." })
        } else {
            appContext.contentResolver.openOutputStream(uri, "wt")
                ?: error("Unable to open backup destination.")
        }

    private fun openInput(uri: Uri): InputStream =
        if (uri.scheme == "file") {
            FileInputStream(requireNotNull(uri.path) { "Invalid restore file path." })
        } else {
            appContext.contentResolver.openInputStream(uri)
                ?: error("Unable to open backup file.")
        }
}
