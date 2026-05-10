package com.uiptv.db

import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.time.Instant
import java.util.HexFormat

class DatabasePatchesUtils private constructor() {
    companion object {
        private const val MIGRATIONS_LIST_RESOURCE = "db/migrations/migrations.txt"
        private const val BASELINE_RESOURCE = "db/migrations/0000_baseline.sql"
        private const val MIGRATIONS_DIR_RESOURCE = "db/migrations/"

        @JvmStatic
        @Throws(SQLException::class)
        fun applyPatches(conn: Connection) {
            createSchemaMigrationsTable(conn)
            for (migrationName in readMigrationNames(MIGRATIONS_LIST_RESOURCE)) {
                applyMigration(conn, migrationName)
            }
        }

        @JvmStatic
        @Throws(SQLException::class)
        fun applyBaseline(conn: Connection) {
            executeMigrationContent(conn, readResource(BASELINE_RESOURCE))
        }

        @JvmStatic
        fun hasMigrationsListResource(): Boolean = resourceExists(MIGRATIONS_LIST_RESOURCE)

        @JvmStatic
        fun hasBaselineResource(): Boolean = resourceExists(BASELINE_RESOURCE)

        @Throws(SQLException::class)
        private fun applyMigration(conn: Connection, migrationName: String) {
            val resourcePath = MIGRATIONS_DIR_RESOURCE + migrationName
            val migrationSql = readResource(resourcePath)
            val checksum = checksum(migrationSql)
            val existing = findMigrationRecord(conn, migrationName)
            if (existing != null && existing.status.equals("success", true) && checksum == existing.checksum) {
                return
            }

            val originalAutoCommit = conn.autoCommit
            try {
                conn.autoCommit = false
                executeMigrationContent(conn, migrationSql)
                upsertMigrationRecord(conn, migrationName, checksum, "success", null)
                conn.commit()
            } catch (ex: Exception) {
                conn.rollback()
                upsertMigrationRecord(conn, migrationName, checksum, "failed", safeError(ex))
                conn.commit()
            } finally {
                conn.autoCommit = originalAutoCommit
            }
        }

        @Throws(SQLException::class)
        @JvmStatic
        private fun executeMigrationContent(conn: Connection, migrationSql: String) {
            val directive = findDirectiveLine(migrationSql)
            if (directive != null) {
                executeDirective(conn, directive)
            } else {
                executeSqlStatements(conn, migrationSql)
            }
        }

        @Throws(SQLException::class)
        @JvmStatic
        private fun executeDirective(conn: Connection, directiveLine: String) {
            val parts = directiveLine.trim().split(Regex("\\s+"), limit = 4)
            if (parts.size < 3) {
                throw SQLException("Invalid migration directive: $directiveLine")
            }
            val command = parts[0].lowercase()
            if (command == "--@add_column") {
                if (parts.size < 4) {
                    throw SQLException("Invalid add_column directive: $directiveLine")
                }
                val table = parts[1]
                val column = parts[2]
                val definition = parts[3]
                if (columnExists(conn, table, column)) {
                    return
                }
                conn.createStatement().use { stmt ->
                    stmt.executeUpdate("ALTER TABLE $table ADD COLUMN $column $definition")
                }
                return
            }
            if (command == "--@drop_column") {
                val table = parts[1]
                val column = parts[2]
                if (!columnExists(conn, table, column)) {
                    return
                }
                conn.createStatement().use { stmt ->
                    stmt.executeUpdate("ALTER TABLE $table DROP COLUMN $column")
                }
                return
            }
            throw SQLException("Unsupported migration directive: $directiveLine")
        }

        @Throws(SQLException::class)
        @JvmStatic
        private fun executeSqlStatements(conn: Connection, migrationSql: String) {
            val sqlBlob = buildString {
                migrationSql.split(Regex("\\R")).forEach { line ->
                    if (!line.trim().startsWith("--")) {
                        append(line).append('\n')
                    }
                }
            }
            sqlBlob.split(";").map(String::trim).filter(String::isNotEmpty).forEach { statement ->
                conn.createStatement().use { stmt -> stmt.executeUpdate(statement) }
            }
        }

        @Throws(SQLException::class)
        @JvmStatic
        private fun columnExists(conn: Connection, tableName: String, columnName: String): Boolean {
            conn.createStatement().use { stmt ->
                stmt.executeQuery("PRAGMA table_info($tableName)").use { rs ->
                    while (rs.next()) {
                        if (columnName.equals(rs.getString("name"), true)) {
                            return true
                        }
                    }
                }
            }
            return false
        }

        @JvmStatic
        private fun findDirectiveLine(migrationSql: String): String? =
            migrationSql.split(Regex("\\R"))
                .map(String::trim)
                .firstOrNull { it.startsWith("--@") }

        @JvmStatic
        private fun readMigrationNames(resourcePath: String): List<String> =
            readResource(resourcePath)
                .split(Regex("\\R"))
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("#") }

        @JvmStatic
        private fun readResource(path: String): String {
            try {
                openResource(path)?.use { input ->
                    BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
                        return buildString {
                            while (true) {
                                val line = reader.readLine() ?: break
                                append(line).append('\n')
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                throw IllegalStateException("Unable to read migration resource: $path", e)
            }
            throw IllegalStateException("Migration resource not found: $path")
        }

        @JvmStatic
        private fun resourceExists(path: String): Boolean =
            try {
                openResource(path)?.use { true } ?: false
            } catch (_: Exception) {
                false
            }

        @JvmStatic
        private fun openResource(path: String): InputStream? {
            val normalized = if (path.startsWith("/")) path.substring(1) else path
            val absolute = "/$normalized"

            DatabasePatchesUtils::class.java.getResourceAsStream(absolute)?.let { return it }
            Thread.currentThread().contextClassLoader?.getResourceAsStream(normalized)?.let { return it }
            DatabasePatchesUtils::class.java.classLoader?.getResourceAsStream(normalized)?.let { return it }
            try {
                DatabasePatchesUtils::class.java.module.getResourceAsStream(normalized)?.let { return it }
            } catch (_: Exception) {
            }
            val localPath: Path = Paths.get("src", "main", "resources", normalized)
            return if (Files.isRegularFile(localPath)) FileInputStream(localPath.toFile()) else null
        }

        @JvmStatic
        private fun checksum(text: String): String =
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                HexFormat.of().formatHex(digest.digest(text.toByteArray(StandardCharsets.UTF_8)))
            } catch (ex: Exception) {
                throw IllegalStateException("Unable to compute migration checksum", ex)
            }

        @Throws(SQLException::class)
        @JvmStatic
        private fun createSchemaMigrationsTable(conn: Connection) {
            conn.createStatement().use { stmt ->
                stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS schema_migrations (" +
                        "name TEXT PRIMARY KEY," +
                        "checksum TEXT NOT NULL," +
                        "status TEXT NOT NULL," +
                        "applied_at INTEGER NOT NULL," +
                        "error_message TEXT" +
                        ")"
                )
            }
        }

        @Throws(SQLException::class)
        @JvmStatic
        private fun findMigrationRecord(conn: Connection, name: String): MigrationRecord? {
            conn.prepareStatement("SELECT checksum, status FROM schema_migrations WHERE name = ?").use { ps ->
                ps.setString(1, name)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) {
                        return null
                    }
                    return MigrationRecord(rs.getString("checksum"), rs.getString("status"))
                }
            }
        }

        @Throws(SQLException::class)
        @JvmStatic
        private fun upsertMigrationRecord(conn: Connection, name: String, checksum: String, status: String, errorMessage: String?) {
            val sql = "INSERT INTO schema_migrations(name, checksum, status, applied_at, error_message) VALUES(?,?,?,?,?) " +
                "ON CONFLICT(name) DO UPDATE SET " +
                "checksum=excluded.checksum," +
                "status=excluded.status," +
                "applied_at=excluded.applied_at," +
                "error_message=excluded.error_message"
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, name)
                ps.setString(2, checksum)
                ps.setString(3, status)
                ps.setLong(4, Instant.now().epochSecond)
                ps.setString(5, errorMessage)
                ps.executeUpdate()
            }
        }

        @JvmStatic
        private fun safeError(ex: Exception?): String {
            val message = ex?.message.orEmpty()
            if (message.isBlank()) {
                return ex?.javaClass?.simpleName.orEmpty()
            }
            return if (message.length > 1000) message.substring(0, 1000) else message
        }
    }

    private data class MigrationRecord(val checksum: String, val status: String)
}
