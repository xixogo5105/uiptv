package com.uiptv.service.remotesync

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.SQLException

class DatabaseSnapshotService {
    @Throws(IOException::class, SQLException::class)
    fun createSnapshot(databasePath: String): Path {
        require(databasePath.isNotBlank()) { "databasePath" }
        val snapshotPath = SecureTempFileSupport.createTempFile("uiptv-remote-sync-", ".db")
        try {
            runVacuumInto(databasePath, snapshotPath)
            return snapshotPath
        } catch (ex: SQLException) {
            Files.deleteIfExists(snapshotPath)
            throw ex
        }
    }

    @Throws(SQLException::class)
    private fun runVacuumInto(databasePath: String, snapshotPath: Path) {
        DriverManager.getConnection("jdbc:sqlite:$databasePath").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("VACUUM INTO '" + escapeSqlLiteral(snapshotPath.toAbsolutePath().toString()) + "'")
            }
        }
    }

    private fun escapeSqlLiteral(value: String): String = value.replace("'", "''")
}
