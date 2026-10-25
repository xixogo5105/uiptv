package com.uiptv.mobile.shared.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

class AndroidUiptvDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    UiptvSchemaInfo.DATABASE_NAME,
    null,
    UiptvSchemaInfo.SCHEMA_VERSION_CODE
), java.io.Closeable {
    private val appContext = context.applicationContext
    private val maintenance by lazy { appContext.getSharedPreferences("database-maintenance", Context.MODE_PRIVATE) }
    private val migrationSource = AndroidMigrationSource(appContext)

    override fun onCreate(db: SQLiteDatabase) {
        applyMigrations(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        applyMigrations(db)
    }

    fun databaseFile(): File =
        appContext.getDatabasePath(UiptvSchemaInfo.DATABASE_NAME)

    fun applyMigrations(db: SQLiteDatabase) {
        val hadGuides = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND (name LIKE 'epg_%' OR name='source_blacklist') LIMIT 1", null).use { it.moveToFirst() }
        AndroidUiptvMigrationApplier(migrationSource).applyAll(db)
        if (hadGuides) {
            maintenance.edit().putBoolean("compact-after-0222", true).apply()
            if (!db.inTransaction()) {
                compactRetiredGuideStorage(db)
            }
        }
        File(appContext.filesDir, "epg").deleteRecursively()
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (maintenance.getBoolean("compact-after-0222", false)) {
            Thread({
                compactRetiredGuideStorage(db)
            }, "uiptv-remove-epg-storage").start()
        }
    }

    private fun compactRetiredGuideStorage(db: SQLiteDatabase) {
        runCatching {
            db.execSQL("VACUUM")
            maintenance.edit().remove("compact-after-0222").apply()
        }.onFailure { android.util.Log.w("UIPTV-Database", "Unable to compact retired EPG storage; retrying on next open", it) }
    }
}
