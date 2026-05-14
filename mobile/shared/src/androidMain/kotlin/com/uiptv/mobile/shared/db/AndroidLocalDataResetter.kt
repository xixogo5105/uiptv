package com.uiptv.mobile.shared.db

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidLocalDataResetter(
    private val databaseHelper: AndroidUiptvDatabaseHelper
) {
    suspend fun resetAndVacuum(): Unit = withContext(Dispatchers.IO) {
        val db = databaseHelper.writableDatabase
        val tables = userTables(db)
        db.execSQL("PRAGMA foreign_keys=OFF")
        db.beginTransaction()
        try {
            tables.forEach { table ->
                db.delete(table, null, null)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.execSQL("PRAGMA foreign_keys=ON")
        }
        db.execSQL("VACUUM")
    }

    private fun userTables(db: SQLiteDatabase): List<String> =
        db.rawQuery(
            """
            SELECT name
            FROM sqlite_master
            WHERE type = 'table'
                AND name NOT LIKE 'sqlite_%'
                AND name <> 'android_metadata'
            """.trimIndent(),
            null
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getString(0))
                }
            }
        }
}
