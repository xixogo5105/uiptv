package com.uiptv.mobile.android

import android.content.ContentValues
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.uiptv.mobile.shared.db.AndroidDatabaseBackupManager
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import com.uiptv.mobile.shared.db.UiptvSchemaInfo
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidDatabaseBackupManagerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper: AndroidUiptvDatabaseHelper

    @Before
    fun setUp() {
        context.deleteDatabase(UiptvSchemaInfo.DATABASE_NAME)
        helper = AndroidUiptvDatabaseHelper(context)
    }

    @After
    fun tearDown() {
        helper.close()
        context.deleteDatabase(UiptvSchemaInfo.DATABASE_NAME)
    }

    @Test
    fun backupAndRestoreReplacesWholeDatabaseClone() = runBlocking {
        helper.writableDatabase.insert(
            "Account",
            null,
            ContentValues().apply {
                put("accountName", "Original")
                put("type", "m3u")
            }
        )
        val backupFile = File(context.cacheDir, "uiptv-test-backup.zip").apply { delete() }
        val manager = AndroidDatabaseBackupManager(context, helper)

        val backup = manager.backupToUri(Uri.fromFile(backupFile).toString())
        assertTrue(backupFile.length() > 0L)
        assertTrue(backup.bytes > 0L)

        helper.writableDatabase.delete("Account", null, null)
        helper.writableDatabase.insert(
            "Account",
            null,
            ContentValues().apply {
                put("accountName", "Temporary")
                put("type", "m3u")
            }
        )

        val restore = manager.restoreFromUri(Uri.fromFile(backupFile).toString())

        assertTrue(restore.bytes > 0L)
        helper.readableDatabase.rawQuery("SELECT accountName FROM Account ORDER BY accountName", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Original", cursor.getString(0))
            assertEquals(false, cursor.moveToNext())
        }
    }
}
