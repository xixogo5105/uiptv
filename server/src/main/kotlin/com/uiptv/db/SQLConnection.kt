package com.uiptv.db

import com.uiptv.util.ConfigFileReader
import com.uiptv.util.Platform
import com.uiptv.util.StringUtils
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.apache.commons.io.FileUtils
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.jetbrains.exposed.sql.Database
import org.sqlite.SQLiteConfig
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

object SqlConnectionRuntime {
    private const val BUSY_TIMEOUT_MS = 10_000
    private const val INIT_RETRY_ATTEMPTS = 6
    private const val INIT_RETRY_DELAY_MS = 250L
    private const val MIGRATIONS_LIST_RESOURCE = "db/migrations/migrations.txt"
    private const val BASELINE_MIGRATION = "0000_baseline.sql"
    private const val LEGACY_HISTORY_TABLE = "schema_migrations"
    private const val CURRENT_SCHEMA_VERSION = "0197"

    @Volatile
    private var dbPath: String = resolveConfiguredDatabasePath()

    @Volatile
    private var exposedDatabase: Database? = null

    @Volatile
    private var hikariDataSource: HikariDataSource? = null

    init {
        init()
    }

    @JvmStatic
    @Synchronized
    fun init() {
        try {
            ensureDatabasePathReady()
            rebuildDataSource()
            for (attempt in 1..INIT_RETRY_ATTEMPTS) {
                try {
                    migrate()
                    exposedDatabase = Database.connect(dataSource())
                    return
                } catch (ex: SQLException) {
                    if (isBusy(ex) && attempt < INIT_RETRY_ATTEMPTS) {
                        sleepBeforeRetry()
                        continue
                    }
                    throw DatabaseAccessException("Unable to initialize database migrations", ex)
                }
            }
        } catch (e: IOException) {
            throw DatabaseAccessException("Unable to create database file", e)
        }
    }

    @JvmStatic
    @Synchronized
    fun setDatabasePath(path: String) {
        close()
        dbPath = path
        init()
    }

    @JvmStatic
    @Synchronized
    fun getDatabasePath(): String = dbPath

    @JvmStatic
    fun connect(): Connection =
        try {
            dataSource().connection.also(::configurePragmas)
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to open database connection", e)
        }

    @JvmStatic
    fun database(): Database = exposedDatabase ?: synchronized(this) {
        exposedDatabase ?: Database.connect(dataSource()).also { exposedDatabase = it }
    }

    @JvmStatic
    @Synchronized
    fun close() {
        exposedDatabase = null
        hikariDataSource?.close()
        hikariDataSource = null
    }

    @JvmStatic
    @Synchronized
    fun dataSource(): HikariDataSource {
        val existing = hikariDataSource
        if (existing != null && !existing.isClosed) {
            return existing
        }
        ensureDatabasePathReadyUnchecked()
        rebuildDataSource()
        return hikariDataSource ?: throw IllegalStateException("Data source not initialized")
    }

    private fun migrate() {
        openConnection().use { connection ->
            val plan = determineMigrationPlan(connection)
            if (plan.baselineVersion != null) {
                baselineExistingSchema(plan.baselineVersion, plan.baselineDescription)
            }
            if (plan.files.isNotEmpty()) {
                val migrationDirectory = materializeMigrations(plan)
                loadFlyway(migrationDirectory).migrate()
            }
        }
    }

    private fun loadFlyway(migrationDirectory: Path): Flyway =
        Flyway.configure()
            .dataSource("jdbc:sqlite:$dbPath", null, null)
            .table("flyway_schema_history")
            .locations("filesystem:${migrationDirectory.toAbsolutePath()}")
            .cleanDisabled(true)
            .baselineOnMigrate(false)
            .load()

    private fun baselineExistingSchema(version: String, description: String) {
        Flyway.configure()
            .dataSource("jdbc:sqlite:$dbPath", null, null)
            .table("flyway_schema_history")
            .baselineVersion(MigrationVersion.fromVersion(version))
            .baselineDescription(description)
            .load()
            .baseline()
    }

    private fun materializeMigrations(plan: MigrationPlan): Path {
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

    private fun readMigrationNames(): List<String> =
        openResource(MIGRATIONS_LIST_RESOURCE).bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
            lines.map(String::trim)
                .filter(String::isNotEmpty)
                .toList()
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
        SqlConnectionRuntime::class.java.classLoader.getResourceAsStream(path)
            ?: throw IllegalStateException("Migration resource not found: $path")

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

    private fun hasApplicationTables(connection: Connection): Boolean {
        val knownTables = listOf("Configuration", "Account", "Bookmark", "Category", "Channel")
        return knownTables.any { tableExists(connection, it) }
    }

    private fun determineMigrationPlan(connection: Connection): MigrationPlan {
        val legacyVersion = readLegacySuccessVersion(connection)
        val flywayVersion = readFlywaySuccessVersion(connection)
        return when {
            flywayVersion != null -> MigrationPlan.incrementalFrom(flywayVersion)
            legacyVersion != null -> MigrationPlan.incrementalFrom(legacyVersion).copy(
                baselineVersion = legacyVersion,
                baselineDescription = "legacy schema_migrations"
            )
            hasApplicationTables(connection) -> MigrationPlan.baselineOnly().copy(
                baselineDescription = "existing application schema"
            )
            else -> MigrationPlan.bootstrapBaseline()
        }
    }

    private fun readFlywaySuccessVersion(connection: Connection): String? {
        if (!tableExists(connection, "flyway_schema_history")) {
            return null
        }
        connection.prepareStatement(
            "SELECT version FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1"
        ).use { statement ->
            statement.executeQuery().use { rs ->
                return if (rs.next()) rs.getString(1) else null
            }
        }
    }

    private fun tableExists(connection: Connection, tableName: String): Boolean =
        connection.prepareStatement(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?"
        ).use { statement ->
            statement.setString(1, tableName)
            statement.executeQuery().use { rs -> rs.next() }
        }

    @Throws(SQLException::class)
    private fun openConnection(): Connection {
        val connection = DriverManager.getConnection(jdbcUrl(), sqliteProperties())
        configurePragmas(connection)
        return connection
    }

    @Synchronized
    private fun rebuildDataSource() {
        ensureDatabasePathReadyUnchecked()
        hikariDataSource?.close()
        hikariDataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = jdbcUrl()
            driverClassName = "org.sqlite.JDBC"
            maximumPoolSize = Integer.getInteger("uiptv.db.pool.maxSize", 8)
            minimumIdle = Integer.getInteger("uiptv.db.pool.minIdle", 0)
            connectionTimeout = Integer.getInteger("uiptv.db.pool.connectionTimeoutMs", 10_000).toLong()
            idleTimeout = Integer.getInteger("uiptv.db.pool.idleTimeoutMs", 60_000).toLong()
            maxLifetime = Integer.getInteger("uiptv.db.pool.maxLifetimeMs", 300_000).toLong()
            validationTimeout = Integer.getInteger("uiptv.db.pool.validationTimeoutMs", 5_000).toLong()
            initializationFailTimeout = -1
            isAutoCommit = true
            connectionTestQuery = "SELECT 1"
            dataSourceProperties.putAll(sqliteProperties())
        })
    }

    @Throws(IOException::class)
    private fun ensureDatabasePathReady() {
        val databaseFile = File(dbPath)
        databaseFile.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                throw IOException("Unable to create database directory: ${parent.absolutePath}")
            }
        }
        FileUtils.touch(databaseFile)
    }

    private fun ensureDatabasePathReadyUnchecked() {
        try {
            ensureDatabasePathReady()
        } catch (e: IOException) {
            throw DatabaseAccessException("Unable to create database file", e)
        }
    }

    private fun jdbcUrl(): String = "jdbc:sqlite:$dbPath"

    private fun sqliteProperties() = SQLiteConfig().apply {
        busyTimeout = BUSY_TIMEOUT_MS
        setJournalMode(SQLiteConfig.JournalMode.WAL)
        setSynchronous(SQLiteConfig.SynchronousMode.NORMAL)
    }.toProperties()

    @Throws(SQLException::class)
    private fun configurePragmas(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA busy_timeout = $BUSY_TIMEOUT_MS")
            statement.execute("PRAGMA journal_mode = WAL")
            statement.execute("PRAGMA synchronous = NORMAL")
        }
    }

    private fun isBusy(exception: SQLException): Boolean {
        var current: SQLException? = exception
        while (current != null) {
            val message = current.message
            if (current.errorCode == 5 || (message != null && message.contains("SQLITE_BUSY"))) {
                return true
            }
            current = current.nextException
        }
        return false
    }

    private fun sleepBeforeRetry() {
        try {
            Thread.sleep(INIT_RETRY_DELAY_MS)
        } catch (interruptedException: InterruptedException) {
            Thread.currentThread().interrupt()
            throw DatabaseAccessException(
                "Interrupted while waiting to retry database initialization",
                interruptedException
            )
        }
    }

    private fun resolveConfiguredDatabasePath(): String {
        val configured = ConfigFileReader.getDbPathFromConfigFile()
        return if (StringUtils.isNotBlank(configured)) {
            configured!!
        } else {
            Platform.getUserHomeDirPath() + File.separator + "uiptv.db"
        }
    }

    private data class MigrationPlan(
        val files: List<String>,
        val versionOverride: String? = null,
        val baselineVersion: String? = null,
        val baselineDescription: String = "baseline"
    ) {
        companion object {
            fun fullHistory(): MigrationPlan = MigrationPlan(readAllMigrationNames())

            fun bootstrapBaseline(): MigrationPlan = MigrationPlan(
                files = listOf(BASELINE_MIGRATION),
                versionOverride = CURRENT_SCHEMA_VERSION
            )

            fun baselineOnly(): MigrationPlan = MigrationPlan(
                files = emptyList(),
                baselineVersion = CURRENT_SCHEMA_VERSION,
                baselineDescription = "existing schema"
            )

            fun incrementalFrom(version: String): MigrationPlan =
                MigrationPlan(
                    readAllMigrationNames().filter { migration ->
                        migration != BASELINE_MIGRATION && migrationVersion(migration) > version.toInt()
                    }
                )

            private fun readAllMigrationNames(): List<String> =
                SqlConnectionRuntime.readMigrationNames()

            private fun migrationVersion(fileName: String): Int =
                Regex("""^(\d+)_""").find(fileName)?.groupValues?.get(1)?.toInt()
                    ?: throw IllegalStateException("Unsupported migration file name: $fileName")
        }
    }
}

class SQLConnection private constructor() {
    companion object {
        private const val INIT_RETRY_DELAY_MS = 250L

        @JvmStatic
        @Synchronized
        fun init() {
            SqlConnectionRuntime.init()
        }

        @JvmStatic
        @Synchronized
        fun setDatabasePath(path: String) {
            SqlConnectionRuntime.setDatabasePath(path)
        }

        @JvmStatic
        @Synchronized
        fun getDatabasePath(): String = SqlConnectionRuntime.getDatabasePath()

        @JvmStatic
        fun connect(): Connection = SqlConnectionRuntime.connect()

        @JvmStatic
        fun database(): Database = SqlConnectionRuntime.database()

        @JvmStatic
        @Synchronized
        fun close() {
            SqlConnectionRuntime.close()
        }

        @Suppress("unused")
        @JvmStatic
        private fun isBusy(exception: SQLException): Boolean {
            var current: SQLException? = exception
            while (current != null) {
                val message = current.message
                if (current.errorCode == 5 || (message != null && message.contains("SQLITE_BUSY"))) {
                    return true
                }
                current = current.nextException
            }
            return false
        }

        @Suppress("unused")
        @JvmStatic
        private fun sleepBeforeRetry() {
            try {
                Thread.sleep(INIT_RETRY_DELAY_MS)
            } catch (interruptedException: InterruptedException) {
                Thread.currentThread().interrupt()
                throw DatabaseAccessException(
                    "Interrupted while waiting to retry database initialization",
                    interruptedException
                )
            }
        }
    }
}
