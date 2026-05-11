package com.uiptv.db

import java.sql.Connection

internal data class MigrationPlan(
    val files: List<String>,
    val versionOverride: String? = null,
    val baselineVersion: String? = null,
    val baselineDescription: String = "baseline"
)

internal object DatabaseMigrationCompatibility {
    private const val LEGACY_HISTORY_TABLE = "schema_migrations"
    private const val FLYWAY_HISTORY_TABLE = "flyway_schema_history"
    private val KNOWN_APPLICATION_TABLES = listOf("Configuration", "Account", "Bookmark", "Category", "Channel")

    fun determineMigrationPlan(
        connection: Connection,
        currentSchemaVersion: String,
        baselineMigration: String,
        readAllMigrationNames: () -> List<String>
    ): MigrationPlan {
        val legacyVersion = readLegacySuccessVersion(connection)
        val flywayVersion = readFlywaySuccessVersion(connection)
        return when {
            flywayVersion != null -> incrementalFrom(flywayVersion, baselineMigration, readAllMigrationNames)
            legacyVersion != null -> incrementalFrom(legacyVersion, baselineMigration, readAllMigrationNames).copy(
                baselineVersion = legacyVersion,
                baselineDescription = "legacy schema_migrations"
            )
            hasApplicationTables(connection) -> MigrationPlan(
                files = emptyList(),
                baselineVersion = currentSchemaVersion,
                baselineDescription = "existing schema"
            )
            else -> MigrationPlan(
                files = listOf(baselineMigration),
                versionOverride = currentSchemaVersion
            )
        }
    }

    private fun incrementalFrom(
        version: String,
        baselineMigration: String,
        readAllMigrationNames: () -> List<String>
    ): MigrationPlan =
        MigrationPlan(
            readAllMigrationNames().filter { migration ->
                migration != baselineMigration && migrationVersion(migration) > version.toInt()
            }
        )

    private fun readLegacySuccessVersion(connection: Connection): String? {
        if (!tableExists(connection, LEGACY_HISTORY_TABLE)) {
            return null
        }
        connection.prepareStatement(
            "SELECT name FROM $LEGACY_HISTORY_TABLE WHERE status='success' ORDER BY name DESC LIMIT 1"
        ).use { statement ->
            statement.executeQuery().use { rs ->
                if (!rs.next()) {
                    return null
                }
                val fileName = rs.getString(1) ?: return null
                return Regex("""^(\d+)_""").find(fileName)?.groupValues?.get(1)
            }
        }
    }

    private fun readFlywaySuccessVersion(connection: Connection): String? {
        if (!tableExists(connection, FLYWAY_HISTORY_TABLE)) {
            return null
        }
        connection.prepareStatement(
            "SELECT version FROM $FLYWAY_HISTORY_TABLE WHERE success = 1 AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1"
        ).use { statement ->
            statement.executeQuery().use { rs ->
                return if (rs.next()) rs.getString(1) else null
            }
        }
    }

    private fun hasApplicationTables(connection: Connection): Boolean =
        KNOWN_APPLICATION_TABLES.any { tableExists(connection, it) }

    private fun tableExists(connection: Connection, tableName: String): Boolean =
        connection.prepareStatement(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?"
        ).use { statement ->
            statement.setString(1, tableName)
            statement.executeQuery().use { rs -> rs.next() }
        }

    private fun migrationVersion(fileName: String): Int =
        Regex("""^(\d+)_""").find(fileName)?.groupValues?.get(1)?.toInt()
            ?: throw IllegalStateException("Unsupported migration file name: $fileName")
}
