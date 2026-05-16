package com.uiptv.mobile.shared.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.security.MessageDigest

class AndroidUiptvMigrationApplier(private val source: AndroidMigrationSource) {
    fun applyAll(db: SQLiteDatabase) {
        createSchemaMigrationsTable(db)
        source.migrationNames().forEach { migrationName ->
            applyMigration(db, migrationName, source.migrationSql(migrationName))
        }
    }

    private fun applyMigration(db: SQLiteDatabase, name: String, sql: String) {
        val checksum = sha256(sql)
        if (isSuccessful(db, name, checksum)) {
            return
        }

        db.beginTransaction()
        try {
            executeMigration(db, sql)
            recordMigration(db, name, checksum, "success", null)
            db.setTransactionSuccessful()
        } catch (ex: Exception) {
            db.endTransaction()
            recordMigration(db, name, checksum, "failed", ex.message ?: ex::class.simpleName.orEmpty())
            throw ex
        }
        db.endTransaction()
    }

    private fun executeMigration(db: SQLiteDatabase, sql: String) {
        when (val directive = UiptvMigrationSql.findDirective(sql)) {
            is MigrationDirective.AddColumn -> applyAddColumn(db, directive)
            is MigrationDirective.DropColumn -> {
                if (columnExists(db, directive.table, directive.column)) {
                    db.execSQL("ALTER TABLE ${quoteIdentifier(directive.table)} DROP COLUMN ${quoteIdentifier(directive.column)}")
                }
            }
            null -> UiptvMigrationSql.executableStatements(sql).forEach { statement ->
                when (val addColumn = UiptvMigrationSql.parseAddColumnStatement(statement)) {
                    null -> db.execSQL(statement)
                    else -> applyAddColumn(db, addColumn)
                }
            }
        }
    }

    private fun applyAddColumn(db: SQLiteDatabase, directive: MigrationDirective.AddColumn) {
        if (!columnExists(db, directive.table, directive.column)) {
            db.execSQL(
                "ALTER TABLE ${quoteIdentifier(directive.table)} " +
                    "ADD COLUMN ${quoteIdentifier(directive.column)} ${directive.definition}"
            )
        }
    }

    private fun createSchemaMigrationsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS schema_migrations (
                name TEXT PRIMARY KEY,
                checksum TEXT NOT NULL,
                status TEXT NOT NULL,
                applied_at INTEGER NOT NULL,
                error_message TEXT
            )
            """.trimIndent()
        )
    }

    private fun isSuccessful(db: SQLiteDatabase, name: String, checksum: String): Boolean {
        db.rawQuery(
            "SELECT status FROM schema_migrations WHERE name = ? AND checksum = ?",
            arrayOf(name, checksum)
        ).use { cursor ->
            return cursor.moveToFirst() && cursor.getString(0).equals("success", ignoreCase = true)
        }
    }

    private fun recordMigration(
        db: SQLiteDatabase,
        name: String,
        checksum: String,
        status: String,
        errorMessage: String?
    ) {
        val values = ContentValues().apply {
            put("name", name)
            put("checksum", checksum)
            put("status", status)
            put("applied_at", epochSeconds())
            put("error_message", errorMessage)
        }
        val updated = db.update("schema_migrations", values, "name = ?", arrayOf(name))
        if (updated == 0) {
            db.insertWithOnConflict("schema_migrations", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private fun columnExists(db: SQLiteDatabase, table: String, column: String): Boolean {
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex).equals(column, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun epochSeconds(): Long = System.currentTimeMillis() / 1000L

    private fun quoteIdentifier(identifier: String): String =
        "\"" + identifier.replace("\"", "\"\"") + "\""
}
