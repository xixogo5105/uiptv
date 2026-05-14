package com.uiptv.mobile.shared.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UiptvMigrationSqlTest {
    @Test
    fun migrationNamesIgnoreBlankLinesAndComments() {
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
    fun parsesAddColumnDirective() {
        val directive = UiptvMigrationSql.findDirective("--@add_column Account timezone TEXT default 'Europe/London'")

        val addColumn = assertIs<MigrationDirective.AddColumn>(directive)
        assertEquals("Account", addColumn.table)
        assertEquals("timezone", addColumn.column)
        assertEquals("TEXT default 'Europe/London'", addColumn.definition)
    }

    @Test
    fun stripsSqlCommentsBeforeSplittingStatements() {
        val statements = UiptvMigrationSql.executableStatements(
            """
            -- ignored
            CREATE TABLE Example(id INTEGER PRIMARY KEY);
            CREATE INDEX idx_example ON Example(id);
            """.trimIndent()
        )

        assertEquals(
            listOf("CREATE TABLE Example(id INTEGER PRIMARY KEY)", "CREATE INDEX idx_example ON Example(id)"),
            statements
        )
    }

    @Test
    fun parsesPlainAddColumnStatementsForAndroidIdempotence() {
        val directive = UiptvMigrationSql.parseAddColumnStatement(
            "ALTER TABLE Configuration ADD COLUMN filterLockUnlockDurationMinutes TEXT DEFAULT '15'"
        )

        val addColumn = assertIs<MigrationDirective.AddColumn>(directive)
        assertEquals("Configuration", addColumn.table)
        assertEquals("filterLockUnlockDurationMinutes", addColumn.column)
        assertEquals("TEXT DEFAULT '15'", addColumn.definition)
    }

    @Test
    fun parsesQuotedAddColumnStatementsForAndroidIdempotence() {
        val directive = UiptvMigrationSql.parseAddColumnStatement(
            "ALTER TABLE \"Configuration\" ADD COLUMN \"enableThumbnails\" TEXT DEFAULT '0'"
        )

        val addColumn = assertIs<MigrationDirective.AddColumn>(directive)
        assertEquals("Configuration", addColumn.table)
        assertEquals("enableThumbnails", addColumn.column)
        assertEquals("TEXT DEFAULT '0'", addColumn.definition)
    }
}
