package com.uiptv.mobile.shared.db

object UiptvSchemaInfo {
    const val DATABASE_NAME = "uiptv.db"
    const val SCHEMA_VERSION_CODE = 197
    const val CURRENT_SCHEMA_VERSION = "0197"
    const val MIGRATIONS_DIR = "db/migrations"
    const val MIGRATIONS_LIST = "$MIGRATIONS_DIR/migrations.txt"
}

sealed interface MigrationDirective {
    val table: String

    data class AddColumn(
        override val table: String,
        val column: String,
        val definition: String
    ) : MigrationDirective

    data class DropColumn(
        override val table: String,
        val column: String
    ) : MigrationDirective
}

object UiptvMigrationSql {
    fun parseMigrationNames(content: String): List<String> =
        content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()

    fun findDirective(sql: String): MigrationDirective? {
        val directiveLine = sql.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("--@") }
            ?: return null
        return parseDirective(directiveLine)
    }

    fun parseDirective(line: String): MigrationDirective {
        val parts = line.trim().split(Regex("\\s+"), limit = 4)
        require(parts.size >= 3) { "Invalid migration directive: $line" }
        return when (parts[0].lowercase()) {
            "--@add_column" -> {
                require(parts.size == 4) { "Invalid add_column directive: $line" }
                MigrationDirective.AddColumn(parts[1], parts[2], parts[3])
            }
            "--@drop_column" -> MigrationDirective.DropColumn(parts[1], parts[2])
            else -> error("Unsupported migration directive: $line")
        }
    }

    fun executableStatements(sql: String): List<String> {
        val withoutComments = sql.lineSequence()
            .filterNot { it.trim().startsWith("--") }
            .joinToString(separator = "\n")
        return withoutComments
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
