package com.uiptv.db

import org.sqlite.SQLiteConfig
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

internal object DatabaseSqliteSupport {
    fun jdbcUrl(dbPath: String): String = "jdbc:sqlite:$dbPath"

    fun sqliteProperties(busyTimeoutMs: Int) = SQLiteConfig().apply {
        busyTimeout = busyTimeoutMs
        setJournalMode(SQLiteConfig.JournalMode.WAL)
        setSynchronous(SQLiteConfig.SynchronousMode.NORMAL)
    }.toProperties()

    @Throws(SQLException::class)
    fun openConnection(dbPath: String, busyTimeoutMs: Int): Connection {
        val connection = DriverManager.getConnection(jdbcUrl(dbPath), sqliteProperties(busyTimeoutMs))
        configurePragmas(connection, busyTimeoutMs)
        return connection
    }

    @Throws(SQLException::class)
    fun configurePragmas(connection: Connection, busyTimeoutMs: Int) {
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA busy_timeout = $busyTimeoutMs")
            statement.execute("PRAGMA journal_mode = WAL")
            statement.execute("PRAGMA synchronous = NORMAL")
        }
    }
}
