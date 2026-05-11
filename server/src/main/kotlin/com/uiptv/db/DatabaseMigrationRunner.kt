package com.uiptv.db

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import java.nio.file.Path
import java.sql.Connection

internal object DatabaseMigrationRunner {
    fun migrate(
        dbPath: String,
        currentSchemaVersion: String,
        baselineMigration: String,
        openConnection: () -> Connection
    ) {
        openConnection().use { connection ->
            val plan = DatabaseMigrationCompatibility.determineMigrationPlan(
                connection = connection,
                currentSchemaVersion = currentSchemaVersion,
                baselineMigration = baselineMigration,
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
            .table("flyway_schema_history")
            .locations("filesystem:${migrationDirectory.toAbsolutePath()}")
            .cleanDisabled(true)
            .baselineOnMigrate(false)
            .load()

    private fun baselineExistingSchema(dbPath: String, version: String, description: String) {
        Flyway.configure()
            .dataSource("jdbc:sqlite:$dbPath", null, null)
            .table("flyway_schema_history")
            .baselineVersion(MigrationVersion.fromVersion(version))
            .baselineDescription(description)
            .load()
            .baseline()
    }
}
