package com.uiptv.db

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

internal object DatabaseMigrationResources {
    private const val MIGRATIONS_LIST_RESOURCE = "db/migrations/migrations.txt"

    fun readMigrationNames(): List<String> =
        openResource(MIGRATIONS_LIST_RESOURCE).bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
            lines.map(String::trim)
                .filter(String::isNotEmpty)
                .toList()
        }

    fun materialize(plan: MigrationPlan): Path {
        val directory = Files.createTempDirectory("uiptv-flyway-")
        directory.toFile().deleteOnExit()
        plan.files.forEach { migrationName ->
            val content = readMigrationResource(migrationName)
            val transformed = transformMigration(content)
            val flywayName = toFlywayFileName(migrationName, plan.versionOverride)
            Files.writeString(directory.resolve(flywayName), transformed, StandardCharsets.UTF_8)
        }
        return directory
    }

    private fun readMigrationResource(name: String): String =
        openResource("db/migrations/$name").bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

    private fun toFlywayFileName(name: String, versionOverride: String? = null): String {
        val match = Regex("""^(\d+)_([^.]+)\.sql$""").matchEntire(name)
            ?: throw IllegalStateException("Unsupported migration file name: $name")
        val version = versionOverride ?: match.groupValues[1]
        return "V${version}__${match.groupValues[2]}.sql"
    }

    private fun transformMigration(content: String): String {
        val trimmed = content.trimStart()
        return when {
            trimmed.startsWith("--@add_column ") -> {
                val directive = trimmed.lineSequence().first().removePrefix("--@add_column ").trim()
                val pieces = directive.split(Regex("\\s+"), limit = 3)
                require(pieces.size == 3) { "Unsupported add-column directive: $directive" }
                "ALTER TABLE ${pieces[0]} ADD COLUMN ${pieces[1]} ${pieces[2].trimEnd(';')};\n"
            }
            trimmed.startsWith("--@drop_column ") -> {
                val directive = trimmed.lineSequence().first().removePrefix("--@drop_column ").trim()
                val pieces = directive.split(Regex("\\s+"), limit = 2)
                require(pieces.size == 2) { "Unsupported drop-column directive: $directive" }
                "ALTER TABLE ${pieces[0]} DROP COLUMN ${pieces[1].trimEnd(';')};\n"
            }
            else -> content
        }
    }

    private fun openResource(path: String) =
        DatabaseMigrationResources::class.java.classLoader.getResourceAsStream(path)
            ?: throw IllegalStateException("Migration resource not found: $path")
}
