package com.uiptv.service

import com.uiptv.db.DatabaseUtils
import com.uiptv.util.SQLiteTableSync.ensureDatabaseReady
import com.uiptv.util.SQLiteTableSync.replaceTable
import com.uiptv.util.SQLiteTableSync.syncPublishedM3uCategorySelections
import com.uiptv.util.SQLiteTableSync.syncPublishedM3uChannelSelections
import com.uiptv.util.SQLiteTableSync.syncPublishedM3uSelections
import com.uiptv.util.SQLiteTableSync.syncTables
import java.sql.SQLException
import java.util.Collections

object DatabaseSyncService {
    private val CONFIGURATION_SYNCABLE = listOf(
        DatabaseUtils.DbTable.THEME_CSS_OVERRIDE_TABLE,
        DatabaseUtils.DbTable.PUBLISHED_M3U_SELECTION_TABLE,
        DatabaseUtils.DbTable.PUBLISHED_M3U_CATEGORY_SELECTION_TABLE,
        DatabaseUtils.DbTable.PUBLISHED_M3U_CHANNEL_SELECTION_TABLE
    )


    @JvmStatic
    @Throws(SQLException::class)
    fun syncDatabases(sourceDB: String, targetDB: String) {
        syncDatabases(sourceDB, targetDB, false, false)
    }

    @JvmStatic
    @Throws(SQLException::class)
    fun syncDatabases(sourceDB: String, targetDB: String, syncConfiguration: Boolean, syncExternalPlayerPaths: Boolean) {
        syncDatabasesWithReport(sourceDB, targetDB, syncConfiguration, syncExternalPlayerPaths, null)
    }

    @JvmStatic
    @Throws(SQLException::class)
    fun syncDatabasesWithReport(
        sourceDB: String,
        targetDB: String,
        syncConfiguration: Boolean,
        syncExternalPlayerPaths: Boolean,
        progressListener: SyncProgressListener?
    ): DatabaseSyncReport {
        ensureDatabaseReady(targetDB)
        val tableResults = ArrayList<TableSyncResult>()
        val totalSteps = DatabaseUtils.Syncable.size + if (syncConfiguration) 1 + CONFIGURATION_SYNCABLE.size else 0
        var completedSteps = 0
        for (tableName in DatabaseUtils.Syncable) {
            notifyProgress(progressListener, completedSteps, totalSteps, tableName.tableName)
            val syncedRows = syncTables(sourceDB, targetDB, tableName)
            tableResults.add(TableSyncResult(tableName.tableName, syncedRows))
            completedSteps++
        }
        var configurationCopied = false
        if (syncConfiguration) {
            notifyProgress(progressListener, completedSteps, totalSteps, DatabaseUtils.DbTable.CONFIGURATION_TABLE.tableName)
            configurationCopied = com.uiptv.util.SQLiteTableSync.syncConfiguration(sourceDB, targetDB, syncExternalPlayerPaths)
            completedSteps++

            notifyProgress(progressListener, completedSteps, totalSteps, DatabaseUtils.DbTable.THEME_CSS_OVERRIDE_TABLE.tableName)
            tableResults.add(
                TableSyncResult(
                    DatabaseUtils.DbTable.THEME_CSS_OVERRIDE_TABLE.tableName,
                    replaceTable(sourceDB, targetDB, DatabaseUtils.DbTable.THEME_CSS_OVERRIDE_TABLE)
                )
            )
            completedSteps++

            notifyProgress(progressListener, completedSteps, totalSteps, DatabaseUtils.DbTable.PUBLISHED_M3U_SELECTION_TABLE.tableName)
            tableResults.add(
                TableSyncResult(
                    DatabaseUtils.DbTable.PUBLISHED_M3U_SELECTION_TABLE.tableName,
                    syncPublishedM3uSelections(sourceDB, targetDB)
                )
            )
            completedSteps++

            notifyProgress(progressListener, completedSteps, totalSteps, DatabaseUtils.DbTable.PUBLISHED_M3U_CATEGORY_SELECTION_TABLE.tableName)
            tableResults.add(
                TableSyncResult(
                    DatabaseUtils.DbTable.PUBLISHED_M3U_CATEGORY_SELECTION_TABLE.tableName,
                    syncPublishedM3uCategorySelections(sourceDB, targetDB)
                )
            )
            completedSteps++

            notifyProgress(progressListener, completedSteps, totalSteps, DatabaseUtils.DbTable.PUBLISHED_M3U_CHANNEL_SELECTION_TABLE.tableName)
            tableResults.add(
                TableSyncResult(
                    DatabaseUtils.DbTable.PUBLISHED_M3U_CHANNEL_SELECTION_TABLE.tableName,
                    syncPublishedM3uChannelSelections(sourceDB, targetDB)
                )
            )
            completedSteps++
        }
        notifyProgress(progressListener, completedSteps, totalSteps, null)
        return DatabaseSyncReport(tableResults, syncConfiguration, configurationCopied, syncExternalPlayerPaths)
    }

    private fun notifyProgress(progressListener: SyncProgressListener?, completedSteps: Int, totalSteps: Int, currentStep: String?) {
        progressListener?.onProgress(completedSteps, totalSteps, currentStep)
    }

    fun interface SyncProgressListener {
        fun onProgress(completedSteps: Int, totalSteps: Int, currentStep: String?)
    }

    class DatabaseSyncReport(
        tableResults: List<TableSyncResult>,
        val configurationRequested: Boolean,
        val configurationCopied: Boolean,
        val externalPlayerPathsIncluded: Boolean
    ) {
        val tableResults: List<TableSyncResult> = Collections.unmodifiableList(ArrayList(tableResults))

        fun tableResults(): List<TableSyncResult> = tableResults

        fun isConfigurationRequested(): Boolean = configurationRequested

        fun isConfigurationCopied(): Boolean = configurationCopied

        fun isExternalPlayerPathsIncluded(): Boolean = externalPlayerPathsIncluded

        fun getTotalRowsSynced(): Int = tableResults.sumOf { it.rowCount }
    }

    data class TableSyncResult(
        val tableName: String,
        val rowCount: Int
    ) {
        fun tableName(): String = tableName

        fun rowCount(): Int = rowCount
    }
}
