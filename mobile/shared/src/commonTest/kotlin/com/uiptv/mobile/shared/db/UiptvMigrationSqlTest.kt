package com.uiptv.mobile.shared.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class UiptvMigrationSqlTest {
    @Test
    fun schemaVersionConstantsMatchBundledMigrationSet() {
        assertEquals("uiptv.db", UiptvSchemaInfo.DATABASE_NAME)
        assertEquals(199, UiptvSchemaInfo.SCHEMA_VERSION_CODE)
        assertEquals("0199", UiptvSchemaInfo.CURRENT_SCHEMA_VERSION)
        assertEquals("db/migrations", UiptvSchemaInfo.MIGRATIONS_DIR)
        assertEquals("db/migrations/migrations.txt", UiptvSchemaInfo.MIGRATIONS_LIST)
    }

    @Test
    fun parseMigrationNamesIgnoresBlankLinesAndComments() {
        val names = UiptvMigrationSql.parseMigrationNames(
            """
            # comment
            0000_baseline.sql

            0197_add_configuration_filter_lock_unlock_duration_minutes.sql
            """.trimIndent()
        )

        assertEquals(
            listOf("0000_baseline.sql", "0197_add_configuration_filter_lock_unlock_duration_minutes.sql"),
            names
        )
    }

    @Test
    fun findDirectiveReturnsFirstDirectiveLine() {
        val directive = UiptvMigrationSql.findDirective(
            """
            -- regular comment
            --@add_column Account pinned INTEGER NOT NULL DEFAULT 0
            ALTER TABLE Account ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0;
            """.trimIndent()
        )

        val addColumn = assertIs<MigrationDirective.AddColumn>(directive)
        assertEquals("Account", addColumn.table)
        assertEquals("pinned", addColumn.column)
        assertEquals("INTEGER NOT NULL DEFAULT 0", addColumn.definition)
    }

    @Test
    fun findDirectiveReturnsNullWhenAbsent() {
        assertNull(UiptvMigrationSql.findDirective("ALTER TABLE Account ADD COLUMN pinned INTEGER;"))
    }

    @Test
    fun parseDirectiveSupportsDropColumn() {
        val directive = UiptvMigrationSql.parseDirective("--@drop_column Account legacyColumn")

        val dropColumn = assertIs<MigrationDirective.DropColumn>(directive)
        assertEquals("Account", dropColumn.table)
        assertEquals("legacyColumn", dropColumn.column)
    }

    @Test
    fun parseDirectiveRejectsMalformedLines() {
        assertFailsWith<IllegalArgumentException> {
            UiptvMigrationSql.parseDirective("--@add_column Account onlyColumn")
        }
        assertFailsWith<IllegalStateException> {
            UiptvMigrationSql.parseDirective("--@unknown Account column")
        }
    }

    @Test
    fun parseAddColumnStatementHandlesQuotedIdentifiers() {
        val directive = UiptvMigrationSql.parseAddColumnStatement(
            """ALTER TABLE "Account Info" ADD COLUMN `new``column` TEXT NOT NULL DEFAULT 'x'"""
        )

        val addColumn = assertIs<MigrationDirective.AddColumn>(directive)
        assertEquals("Account Info", addColumn.table)
        assertEquals("new`column", addColumn.column)
        assertEquals("TEXT NOT NULL DEFAULT 'x'", addColumn.definition)
    }

    @Test
    fun parseAddColumnStatementHandlesBracketIdentifiersAndRejectsNonAddColumn() {
        val directive = UiptvMigrationSql.parseAddColumnStatement(
            "ALTER TABLE [Configuration] ADD COLUMN [enableThumbnails] INTEGER DEFAULT 1"
        )

        val addColumn = assertIs<MigrationDirective.AddColumn>(directive)
        assertEquals("Configuration", addColumn.table)
        assertEquals("enableThumbnails", addColumn.column)
        assertEquals("INTEGER DEFAULT 1", addColumn.definition)
        assertNull(UiptvMigrationSql.parseAddColumnStatement("DROP TABLE Account"))
    }

    @Test
    fun executableStatementsRemoveCommentsAndSplitSql() {
        val statements = UiptvMigrationSql.executableStatements(
            """
            -- ignored
            CREATE TABLE Example(id INTEGER PRIMARY KEY);
            CREATE INDEX idx_example ON Example(id);
            """.trimIndent()
        )

        assertEquals(
            listOf(
                "CREATE TABLE Example(id INTEGER PRIMARY KEY)",
                "CREATE INDEX idx_example ON Example(id)"
            ),
            statements
        )
    }

    @Test
    fun parsesCurrentMigrationAddColumnStatementsForAndroidIdempotence() {
        val plain = assertIs<MigrationDirective.AddColumn>(
            UiptvMigrationSql.parseAddColumnStatement(
                "ALTER TABLE Configuration ADD COLUMN filterLockUnlockDurationMinutes TEXT DEFAULT '15'"
            )
        )
        val quoted = assertIs<MigrationDirective.AddColumn>(
            UiptvMigrationSql.parseAddColumnStatement(
                "ALTER TABLE \"Configuration\" ADD COLUMN \"enableThumbnails\" TEXT DEFAULT '0'"
            )
        )

        assertEquals("Configuration", plain.table)
        assertEquals("filterLockUnlockDurationMinutes", plain.column)
        assertEquals("TEXT DEFAULT '15'", plain.definition)
        assertEquals("Configuration", quoted.table)
        assertEquals("enableThumbnails", quoted.column)
        assertEquals("TEXT DEFAULT '0'", quoted.definition)
    }
}
