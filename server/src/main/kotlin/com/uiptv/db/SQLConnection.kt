package com.uiptv.db

import com.uiptv.util.Platform
import com.uiptv.util.StringUtils
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import java.io.IOException
import java.sql.Connection
import java.sql.SQLException

object SqlConnectionRuntime {
    private const val BUSY_TIMEOUT_MS = 10_000
    private const val INIT_RETRY_ATTEMPTS = 6
    private const val INIT_RETRY_DELAY_MS = 250L
    private const val BASELINE_MIGRATION = "0000_baseline.sql"
    private const val CURRENT_SCHEMA_VERSION = "0197"

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
        DatabasePathState.override(path)
        init()
    }

    @JvmStatic
    @Synchronized
    fun getDatabasePath(): String = DatabasePathState.currentPath()

    @JvmStatic
    fun connect(): Connection =
        try {
            dataSource().connection.also { DatabaseSqliteSupport.configurePragmas(it, BUSY_TIMEOUT_MS) }
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
            dbPath = getDatabasePath(),
            currentSchemaVersion = CURRENT_SCHEMA_VERSION,
            baselineMigration = BASELINE_MIGRATION,
            openConnection = ::openConnection
        )
    }

    @Throws(SQLException::class)
    private fun openConnection(): Connection {
        return DatabaseSqliteSupport.openConnection(getDatabasePath(), BUSY_TIMEOUT_MS)
    }

    @Synchronized
    private fun rebuildDataSource() {
        ensureDatabasePathReadyUnchecked()
        hikariDataSource?.close()
        hikariDataSource = DatabasePoolFactory.create(
            getDatabasePath(),
            DatabaseSqliteSupport.sqliteProperties(BUSY_TIMEOUT_MS)
        )
    }

    @Throws(IOException::class)
    private fun ensureDatabasePathReady() {
        DatabaseFileSupport.ensureDatabasePathReady(getDatabasePath())
    }

    private fun ensureDatabasePathReadyUnchecked() {
        try {
            ensureDatabasePathReady()
        } catch (e: IOException) {
            throw DatabaseAccessException("Unable to create database file", e)
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
