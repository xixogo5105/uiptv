package com.uiptv.mobile.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MobileBackupArchiveTest {
    @Test
    fun defaultBackupFileNameUsesZipExtension() {
        val fileName = MobileBackupArchive.defaultFileName(1_770_000_000L)

        assertEquals("uiptv-mobile-backup-1770000000.zip", fileName)
    }

    @Test
    fun archiveConstantsDescribeFullDatabaseBackup() {
        assertEquals("application/zip", MobileBackupArchive.MIME_TYPE)
        assertEquals("uiptv-backup.json", MobileBackupArchive.METADATA_ENTRY)
        assertTrue(MobileBackupArchive.DATABASE_ENTRY.endsWith(".db"))
    }

    @Test
    fun sizeLabelsUseCompactUnits() {
        assertEquals("512 B", MobileBackupArchive.sizeLabel(512))
        assertEquals("2 KB", MobileBackupArchive.sizeLabel(2 * 1024))
        assertEquals("3 MB", MobileBackupArchive.sizeLabel(3 * 1024 * 1024))
    }

    @Test
    fun backupRestoreResultKeepsMessageAndSize() {
        val result = BackupRestoreResult("Done", bytes = 42)

        assertEquals("Done", result.message)
        assertEquals(42, result.bytes)
    }
}
