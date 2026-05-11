package com.uiptv.db

import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import java.io.IOException
import java.sql.Connection
import java.sql.SQLException

object SqlConnectionRuntime {
    private const val BUSY_TIMEOUT_MS = 10_000
    private const val INIT_RETRY_ATTEMPTS = 6
    private const val INIT_RETRY_DELAY_MS = 250L

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
        if (path == databasePath()) {
            return
        }
        close()
        DatabasePathState.override(path)
        init()
    }

    @JvmStatic
    fun databasePath(): String = DatabasePathState.currentPath()

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
            dbPath = databasePath(),
            openConnection = ::openConnection
        )
    }

    @Throws(SQLException::class)
    private fun openConnection(): Connection {
        return DatabaseSqliteSupport.openConnection(databasePath(), BUSY_TIMEOUT_MS)
    }

    @Synchronized
    private fun rebuildDataSource() {
        ensureDatabasePathReadyUnchecked()
        hikariDataSource?.close()
        hikariDataSource = DatabasePoolFactory.create(
            databasePath(),
            DatabaseSqliteSupport.sqliteProperties(BUSY_TIMEOUT_MS)
        )
    }

    @Throws(IOException::class)
    private fun ensureDatabasePathReady() {
        DatabaseFileSupport.ensureDatabasePathReady(databasePath())
    }

    private fun ensureDatabasePathReadyUnchecked() {
        try {
            ensureDatabasePathReady()
        } catch (e: IOException) {
            throw DatabaseAccessException("Unable to create database file", e)
        }
    }

}
