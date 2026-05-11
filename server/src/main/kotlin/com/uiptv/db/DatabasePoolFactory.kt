package com.uiptv.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.util.Properties

internal object DatabasePoolFactory {
    fun create(dbPath: String, sqliteProperties: Properties): HikariDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:sqlite:$dbPath"
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
                dataSourceProperties.putAll(sqliteProperties)
            }
        )
}
