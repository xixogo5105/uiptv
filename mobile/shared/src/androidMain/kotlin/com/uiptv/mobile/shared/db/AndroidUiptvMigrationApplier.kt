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

        // SQLiteDatabase nests transactions by reference counting, so an inner endTransaction() only decrements
        // a counter: it cannot roll back on its own. SQLiteOpenHelper already wraps onCreate/onUpgrade in a
        // transaction, so starting another one here would leave a failed migration's partial writes (a
        // half-created Configuration_new, for example) inside the outer transaction instead of undoing them.
        // Only take ownership of a transaction when the applier is running standalone.
        val ownsTransaction = !db.inTransaction()
        if (ownsTransaction) {
            db.beginTransaction()
        }

        var failure: Exception? = null
        try {
            executeMigration(db, sql)
            recordMigration(db, name, checksum, "success", null)
            if (ownsTransaction) {
                db.setTransactionSuccessful()
            }
        } catch (ex: Exception) {
            failure = ex
        } finally {
            if (ownsTransaction) {
                db.endTransaction()
            }
        }

        if (failure != null) {
            // Matches DatabasePatchesUtils.applyMigration on the desktop: a migration that cannot be applied is
            // recorded as failed and the chain continues to the next one. The baseline already carries the
            // current schema, so migrations written against older shapes (0167 and 0199 rebuild Configuration
            // around the long-dropped darkTheme column, for example) are redundant here rather than fatal.
            // Rethrowing aborted the whole chain and left the database unusable.
            recordMigration(db, name, checksum, "failed", failure.message ?: failure::class.simpleName.orEmpty())
        }
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
