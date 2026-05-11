package com.uiptv.db

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import java.nio.file.Path
import java.sql.Connection

internal object DatabaseMigrationRunner {
    private const val BASELINE_MIGRATION = "0000_baseline.sql"
    private const val CURRENT_SCHEMA_VERSION = "0197"
    private const val FLYWAY_HISTORY_TABLE = "flyway_schema_history"

    fun migrate(
        dbPath: String,
        openConnection: () -> Connection
    ) {
        openConnection().use { connection ->
            val plan = DatabaseMigrationCompatibility.determineMigrationPlan(
                connection = connection,
                currentSchemaVersion = CURRENT_SCHEMA_VERSION,
                baselineMigration = BASELINE_MIGRATION,
                readAllMigrationNames = DatabaseMigrationResources::readMigrationNames
            )
            if (plan.baselineVersion != null) {
                baselineExistingSchema(dbPath, plan.baselineVersion, plan.baselineDescription)
            }
            if (plan.files.isNotEmpty()) {
                val migrationDirectory = DatabaseMigrationResources.materialize(plan)
                loadFlyway(dbPath, migrationDirectory).migrate()
            }
        }
    }

    private fun loadFlyway(dbPath: String, migrationDirectory: Path): Flyway =
        Flyway.configure()
            .dataSource("jdbc:sqlite:$dbPath", null, null)
            .table(FLYWAY_HISTORY_TABLE)
            .locations("filesystem:${migrationDirectory.toAbsolutePath()}")
            .cleanDisabled(true)
            .baselineOnMigrate(false)
            .load()

    private fun baselineExistingSchema(dbPath: String, version: String, description: String) {
        Flyway.configure()
            .dataSource("jdbc:sqlite:$dbPath", null, null)
            .table(FLYWAY_HISTORY_TABLE)
            .baselineVersion(MigrationVersion.fromVersion(version))
            .baselineDescription(description)
            .load()
            .baseline()
    }
}
