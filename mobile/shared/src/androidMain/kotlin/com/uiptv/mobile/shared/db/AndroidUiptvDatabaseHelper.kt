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
) {
    private val appContext = context.applicationContext
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
        AndroidUiptvMigrationApplier(migrationSource).applyAll(db)
    }
}
