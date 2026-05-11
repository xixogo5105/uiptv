package com.uiptv.db

import com.uiptv.util.Platform
import com.uiptv.util.StringUtils
import com.zaxxer.hikari.HikariDataSource
import org.apache.commons.io.FileUtils
import org.jetbrains.exposed.sql.Database
import org.sqlite.SQLiteConfig
import java.io.File
import java.io.IOException
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

object SqlConnectionRuntime {
    private const val BUSY_TIMEOUT_MS = 10_000
    private const val INIT_RETRY_ATTEMPTS = 6
    private const val INIT_RETRY_DELAY_MS = 250L
    private const val BASELINE_MIGRATION = "0000_baseline.sql"
    private const val CURRENT_SCHEMA_VERSION = "0197"

    @Volatile
    private var dbPath: String = DatabasePathResolver.resolve()

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
                    if (DatabaseRetrySupport.isBusy(ex) && attempt < INIT_RETRY_ATTEMPTS) {
                        DatabaseRetrySupport.sleepBeforeRetry(INIT_RETRY_DELAY_MS)
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
        DatabaseMigrationRunner.migrate(
            dbPath = dbPath,
            currentSchemaVersion = CURRENT_SCHEMA_VERSION,
            baselineMigration = BASELINE_MIGRATION,
            openConnection = ::openConnection
        )
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
        hikariDataSource = DatabasePoolFactory.create(dbPath, sqliteProperties())
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
}

object SQLConnection {
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
        return DatabaseRetrySupport.isBusy(exception)
    }

    @Suppress("unused")
    @JvmStatic
    private fun sleepBeforeRetry() {
        DatabaseRetrySupport.sleepBeforeRetry(INIT_RETRY_DELAY_MS)
    }
}
