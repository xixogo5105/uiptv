package com.uiptv.mobile.shared.settings

import com.uiptv.mobile.shared.db.UiptvSchemaInfo

data class BackupRestoreResult(
    val message: String,
    val bytes: Long = 0
)

object MobileBackupArchive {
    const val MIME_TYPE = "application/zip"
    const val METADATA_ENTRY = "uiptv-backup.json"
    const val DATABASE_ENTRY = UiptvSchemaInfo.DATABASE_NAME
    const val FORMAT_VERSION = 1

    fun defaultFileName(epochSeconds: Long): String =
        "uiptv-backup-$epochSeconds.zip"

    fun sizeLabel(bytes: Long): String =
        when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
            else -> "${bytes / (1024L * 1024L)} MB"
        }
}
