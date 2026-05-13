package com.uiptv.mobile.shared.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AndroidUiptvDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    UiptvSchemaInfo.DATABASE_NAME,
    null,
    UiptvSchemaInfo.SCHEMA_VERSION_CODE
) {
    private val migrationSource = AndroidMigrationSource(context.applicationContext)

    override fun onCreate(db: SQLiteDatabase) {
        AndroidUiptvMigrationApplier(migrationSource).applyAll(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        AndroidUiptvMigrationApplier(migrationSource).applyAll(db)
    }
}
