package com.uiptv.mobile.shared.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the hand-maintained schema mirrors in [UiptvSyncSchema] against the one artifact both platforms
 * already share: `core/src/main/resources/db/migrations/0000_baseline.sql`, repackaged into the APK as
 * `assets/db/migrations/0000_baseline.sql`. Core loads it from the classpath and
 * `DatabaseUtils.dbStructure` mirrors it, guarded by `BaselineSchemaCompatibilityTest`.
 *
 * The mirrors drifted silently before: `configurationColumns` fell eleven columns behind the baseline and
 * `UiptvSchema.TABLE_COLUMNS` ten behind. Because the desktop app can restore a database produced on
 * Android, a column missing from a mirror is a column with no declared cross-platform contract. Deriving
 * the expectation from the baseline rather than from another literal list means these tests fail the next
 * time the schema changes, instead of surfacing at restore time.
 */
class UiptvSyncSchemaBaselineContractTest {

    @Test
    fun configurationColumnsMatchTheBaselineDeclaration() {
        assertEquals(
            baselineColumns("Configuration"),
            UiptvSyncSchema.configurationColumns,
            "UiptvSyncSchema.configurationColumns has drifted from 0000_baseline.sql. The desktop app " +
                "can restore an Android-created database, so this mirror must stay complete and in order."
        )
    }

    @Test
    fun everySyncedAndRequiredTableExistsInTheBaseline() {
        val baselineTables = baselineTableNames()

        val missingTables = (UiptvSyncSchema.syncableTables + UiptvSyncSchema.androidRequiredTables)
            .filterNot { it in baselineTables }
        assertTrue(
            missingTables.isEmpty(),
            "UiptvSyncSchema declares tables absent from the baseline: $missingTables"
        )
    }

    @Test
    fun preservedButHiddenTablesAreRealBaselineTables() {
        val baselineTables = baselineTableNames()
        val unknown = UiptvSyncSchema.desktopTablesPreservedButHiddenInV1.filterNot { it in baselineTables }
        assertTrue(unknown.isEmpty(), "desktopTablesPreservedButHiddenInV1 names unknown tables: $unknown")
    }

    @Test
    fun requiredAndSyncedTablesCoverTheWholeBaseline() {
        // Every baseline table is either synced, or explicitly recorded as preserved-but-hidden. A table that
        // is neither would be created by the baseline but never carried across a restore.
        val baselineTables = baselineTableNames().toSet()
        val declared = UiptvSyncSchema.syncableTables.toSet() +
            UiptvSyncSchema.desktopTablesPreservedButHiddenInV1.toSet()

        assertEquals(
            emptySet(),
            baselineTables - declared,
            "Baseline tables are neither synced nor declared as preserved-but-hidden: " +
                (baselineTables - declared).sorted()
        )
    }

    @Test
    fun requiredTablesAreExactlyTheSyncedTables() {
        assertEquals(UiptvSyncSchema.syncableTables.toSet(), UiptvSyncSchema.androidRequiredTables.toSet())
    }

    @Test
    fun neverSyncColumnsAreASubsetOfTheDeclaredSchema() {
        val declared = UiptvSyncSchema.configurationColumns.toSet()
        val orphans = UiptvSyncSchema.androidNeverSyncConfigurationColumns - declared
        assertTrue(
            orphans.isEmpty(),
            "androidNeverSyncConfigurationColumns references columns that no longer exist: $orphans"
        )
    }

    @Test
    fun portableColumnsAreExactlyTheDeclaredColumnsMinusIdAndNeverSync() {
        val expected = UiptvSyncSchema.configurationColumns
            .filterNot { it == "id" || it in UiptvSyncSchema.androidNeverSyncConfigurationColumns }
            .toSet()

        assertEquals(expected, UiptvSyncSchema.androidPortableConfigurationColumns)
    }

    @Test
    fun legacyDarkThemeColumnIsNotDeclared() {
        // Replaced by themeMode through 0206/0207. If darkTheme reappears in the mirror, the theme migration
        // and this contract have diverged again.
        assertTrue(
            "darkTheme" !in UiptvSyncSchema.configurationColumns,
            "darkTheme was replaced by themeMode and must not be declared"
        )
        assertEquals("light", baselineColumnDefault("Configuration", "themeMode"))
    }

    @Test
    fun baselineIsParseableAndCoversConfiguration() {
        // Guards the parser itself: a baseline this test silently mis-reads would make every other assertion
        // here vacuous.
        val columns = baselineColumns("Configuration")
        assertTrue(columns.size >= 30, "Expected the full Configuration column list, parsed $columns")
        assertEquals("id", columns.first())
    }

    private fun baselineTableNames(): List<String> =
        createTableBodies().keys.sorted()

    private fun baselineColumns(table: String): List<String> {
        val body = createTableBodies()[table]
            ?: error("Table $table is not declared in 0000_baseline.sql")
        return splitTopLevel(body).mapNotNull { entry ->
            val first = entry.trim().split(Regex("\\s+")).firstOrNull { it.isNotEmpty() }
                ?: return@mapNotNull null
            if (first.uppercase() in TABLE_LEVEL_KEYWORDS) {
                null
            } else {
                first.trim('"', '`', '[', ']')
            }
        }
    }

    private fun baselineColumnDefault(table: String, column: String): String? =
        splitTopLevel(createTableBodies().getValue(table))
            .map { it.trim() }
            .firstOrNull { it.trim().split(Regex("\\s+")).first().trim('"', '`', '[', ']') == column }
            ?.let { entry ->
                // Quoted literals keep their inner text; bare tokens are returned as written.
                Regex("""DEFAULT\s+'([^']*)'""", RegexOption.IGNORE_CASE).find(entry)?.groupValues?.get(1)
                    ?: Regex("""DEFAULT\s+([^\s,]+)""", RegexOption.IGNORE_CASE).find(entry)?.groupValues?.get(1)
            }

    /** Extracts the parenthesised body of every `CREATE TABLE` in the baseline, keyed by table name. */
    private fun createTableBodies(): Map<String, String> {
        val sql = readBaselineSql()
        val bodies = LinkedHashMap<String, String>()
        val header = Regex(
            """CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?["`\[]?(\w+)["`\]]?\s*\(""",
            RegexOption.IGNORE_CASE
        )
        var index = 0
        while (index < sql.length) {
            val match = header.find(sql, index) ?: break
            // The match ends just after the opening parenthesis, so the paren itself is the last matched char.
            val openParen = match.range.last
            val closeParen = matchingParen(sql, openParen)
            bodies[match.groupValues[1]] = sql.substring(openParen + 1, closeParen)
            index = closeParen + 1
        }
        return bodies
    }

    /** Returns the index of the parenthesis closing the one at [openIndex]. */
    private fun matchingParen(sql: String, openIndex: Int): Int {
        var depth = 0
        var index = openIndex
        var quote: Char? = null
        while (index < sql.length) {
            val ch = sql[index]
            when {
                quote != null -> if (ch == quote) quote = null
                ch == '\'' || ch == '"' || ch == '`' -> quote = ch
                ch == '(' -> depth++
                ch == ')' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
            index++
        }
        error("Unbalanced parentheses in the baseline starting at $openIndex")
    }

    /**
     * Splits a table body on commas at nesting depth zero. The baseline writes each column's type on its own
     * line and lets DEFAULT run onto the next, so entries cannot be split per line. Quoted text and nested
     * parentheses are skipped so a comma inside a DEFAULT expression cannot split an entry.
     */
    private fun splitTopLevel(body: String): List<String> {
        val entries = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var quote: Char? = null
        for (ch in body) {
            when {
                quote != null -> {
                    current.append(ch)
                    if (ch == quote) quote = null
                }
                ch == '\'' || ch == '"' || ch == '`' -> {
                    quote = ch
                    current.append(ch)
                }
                ch == '(' -> { depth++; current.append(ch) }
                ch == ')' -> { depth--; current.append(ch) }
                ch == ',' && depth == 0 -> { entries.add(current.toString()); current.setLength(0) }
                else -> current.append(ch)
            }
        }
        if (current.isNotBlank()) entries.add(current.toString())
        return entries
    }

    private fun readBaselineSql(): String =
        UiptvSyncSchemaBaselineContractTest::class.java
            .getResourceAsStream("/db/migrations/0000_baseline.sql")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("0000_baseline.sql is not on the classpath; cannot verify the schema contract")

    private companion object {
        val TABLE_LEVEL_KEYWORDS = setOf(
            "PRIMARY", "FOREIGN", "UNIQUE", "CHECK", "CONSTRAINT", "KEY"
        )
    }
}