package com.uiptv.mobile.shared.settings

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidSQLiteFilterSettingsRepository(
    private val databaseHelper: AndroidUiptvDatabaseHelper
) : AndroidFilterSettingsRepository {
    override suspend fun load(): AndroidFilterSettings = withContext(Dispatchers.IO) {
        val db = databaseHelper.writableDatabase
        db.rawQuery(
            """
            SELECT filterCategoriesList, filterChannelsList, pauseFiltering, enableThumbnails
            FROM Configuration
            ORDER BY id
            LIMIT 1
            """.trimIndent(),
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                AndroidFilterSettings()
            } else {
                AndroidFilterSettings(
                    categoryFilters = cursor.string("filterCategoriesList"),
                    channelFilters = cursor.string("filterChannelsList"),
                    paused = cursor.string("pauseFiltering").isTruthy(),
                    enableThumbnails = cursor.string("enableThumbnails").isTruthy()
                )
            }
        }
    }

    override suspend fun save(settings: AndroidFilterSettings): Unit = withContext(Dispatchers.IO) {
        val db = databaseHelper.writableDatabase
        val id = ensureConfigurationRow(db)
        db.update(
            "Configuration",
            ContentValues().apply {
                put("filterCategoriesList", settings.categoryFilters.trim())
                put("filterChannelsList", settings.channelFilters.trim())
                put("pauseFiltering", if (settings.paused) "1" else "0")
                put("enableThumbnails", if (settings.enableThumbnails) "1" else "0")
            },
            "id = ?",
            arrayOf(id.toString())
        )
    }

    override suspend fun setPaused(paused: Boolean): Unit = withContext(Dispatchers.IO) {
        val db = databaseHelper.writableDatabase
        val id = ensureConfigurationRow(db)
        db.update(
            "Configuration",
            ContentValues().apply { put("pauseFiltering", if (paused) "1" else "0") },
            "id = ?",
            arrayOf(id.toString())
        )
    }

    override suspend fun setEnableThumbnails(enabled: Boolean): Unit = withContext(Dispatchers.IO) {
        val db = databaseHelper.writableDatabase
        val id = ensureConfigurationRow(db)
        db.update(
            "Configuration",
            ContentValues().apply { put("enableThumbnails", if (enabled) "1" else "0") },
            "id = ?",
            arrayOf(id.toString())
        )
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
        )
    }

    private fun android.database.Cursor.string(column: String): String {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) "" else getString(index).orEmpty()
    }

    private fun String.isTruthy(): Boolean =
        equals("1", ignoreCase = true) ||
            equals("true", ignoreCase = true) ||
            equals("yes", ignoreCase = true)
}
