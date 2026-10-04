package com.uiptv.mobile.shared.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards [UiptvSyncSchema.configurationColumns] against the one artifact both platforms already share:
 * `core/src/main/resources/db/migrations/0000_baseline.sql`, packaged into the APK as
 * `assets/db/migrations/0000_baseline.sql`.
 *
 * The declared list is a hand-maintained mirror, and it silently fell behind the baseline by eleven columns
 * (httpsServerEnabled, httpsServerPort, vlcNoVideoTitleShow, vlcQuiet, vlcHttpReconnect,
 * vlcAdaptiveUseAccess, vlcVout, vlcAvcodecHw, lightweightModeEnabled, showBookmarkAndAccountCounts and
 * themeMode). Because the desktop app can restore a database produced on Android, a column missing from the
 * mirror is a column with no declared cross-platform contract. Deriving the expectation from the baseline
 * rather than another literal list means this test fails the next time a column is added.
 */
class UiptvSyncSchemaBaselineContractTest {

    @Test
    fun configurationColumnsMatchTheBaselineDeclaration() {
        val baseline = baselineConfigurationColumns()

        assertEquals(
            baseline,
            UiptvSyncSchema.configurationColumns,
            "UiptvSyncSchema.configurationColumns has drifted from 0000_baseline.sql. " +
                "The desktop app can restore an Android-created database, so this mirror must stay complete " +
                "and in baseline order."
        )
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
        // Migrated to themeMode by 0206/0207. If darkTheme reappears in the mirror, the theme migration and
        // this contract have diverged again.
        assertTrue(
            "darkTheme" !in UiptvSyncSchema.configurationColumns,
            "darkTheme was replaced by themeMode and must not be declared"
        )
    }

    private fun baselineConfigurationColumns(): List<String> {
        val sql = readBaselineSql()
        val body = Regex(
            """CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?["`\[]?Configuration["`\]]?\s*\((.*?)\n\)\s*;""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(sql)?.groupValues?.get(1)
            ?: error("Could not find the Configuration table declaration in 0000_baseline.sql")

        // The baseline writes the type on its own line and lets DEFAULT run onto the next one, so entries are
        // split on commas that sit at the top level of the body rather than per line. Parenthesised and
        // quoted text is skipped so a comma inside a DEFAULT expression cannot split an entry.
        return splitTopLevel(body).mapNotNull { entry ->
            val tokens = entry.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val first = tokens.firstOrNull() ?: return@mapNotNull null
            if (first.equals("PRIMARY", ignoreCase = true) || first.equals("FOREIGN", ignoreCase = true) ||
                first.equals("UNIQUE", ignoreCase = true) || first.equals("CHECK", ignoreCase = true) ||
                first.equals("CONSTRAINT", ignoreCase = true)
            ) {
                return@mapNotNull null
            }
            first.trim('"', '`', '[', ']')
        }
    }

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
}