package com.uiptv.mobile.shared.db

object UiptvSyncSchema {
    val syncableTables: List<String> = listOf(
        "Account",
        "AccountInfo",
        "Bookmark",
        "BookmarkCategory",
        "BookmarkOrder",
        "Category",
        "Channel",
        "VodCategory",
        "VodChannel",
        "VodWatchState",
        "SeriesCategory",
        "SeriesChannel",
        "SeriesEpisode",
        "SeriesWatchState",
        "SeriesWatchingNowSnapshot",
        "PublishedM3uSelection",
        "PublishedM3uCategorySelection",
        "PublishedM3uChannelSelection",
        "Configuration"
    )

    val androidRequiredTables: List<String> = listOf(
        "Account",
        "AccountInfo",
        "Bookmark",
        "BookmarkCategory",
        "BookmarkOrder",
        "Category",
        "Channel",
        "VodCategory",
        "VodChannel",
        "VodWatchState",
        "SeriesCategory",
        "SeriesChannel",
        "SeriesEpisode",
        "SeriesWatchState",
        "SeriesWatchingNowSnapshot",
        "PublishedM3uSelection",
        "PublishedM3uCategorySelection",
        "PublishedM3uChannelSelection",
        "Configuration"
    )

    val desktopTablesPreservedButHiddenInV1: List<String> = emptyList()

    /**
     * Configuration columns that never travel between a desktop and an Android device.
     *
     * Deliberately small. The desktop can restore a database produced on Android and vice versa, and the
     * snapshot is applied as a whole-file clone, so the contract is an exact clone rather than a filtered
     * subset: only values that are unsafe or meaningless to copy are excluded. Player paths name executables
     * that exist on one machine only, `filterLockHash` is a secret-derived value that must not leave the
     * device that produced it, and `id` is the row key. Everything else, including the server bindings and the
     * VLC and theme options, is expected to match across devices.
     */
    val androidNeverSyncConfigurationColumns: Set<String> = setOf(
        "playerPath1",
        "playerPath2",
        "playerPath3",
        "defaultPlayerPath",
        "filterLockHash"
    )

    /**
     * Every column of the `Configuration` table, in the exact order declared by
     * `core/src/main/resources/db/migrations/0000_baseline.sql` (mirrored by `DatabaseUtils.dbStructure`).
     *
     * The list is generated from that baseline by the `generateUiptvSchemaMirror` Gradle task, not written by
     * hand. It has to stay a complete, ordered mirror rather than the subset the Android UI happens to read:
     * the desktop app can restore a database produced on Android, so any column missing here is a column the
     * desktop side has no contract for. `UiptvSyncSchemaBaselineContractTest` re-derives the list from the
     * baseline and fails the build if the generated output is ever stale.
     */
    val configurationColumns: List<String> = UiptvGeneratedSchema.configurationColumns

    val androidPortableConfigurationColumns: Set<String> =
        configurationColumns
            .filterNot { it == "id" || it in androidNeverSyncConfigurationColumns }
            .toSet()

    fun commonSyncColumns(sourceColumns: List<String>, targetColumns: List<String>): List<String> {
        val targetColumnSet = targetColumns.toSet()
        return sourceColumns.filter { it in targetColumnSet }
    }
}

data class TableSyncResult(
    val tableName: String,
    val rowCount: Int
)

data class DatabaseSyncReport(
    val tableResults: List<TableSyncResult>,
    val configurationRequested: Boolean = false,
    val configurationCopied: Boolean = false,
    val externalPlayerPathsIncluded: Boolean = false
) {
    val totalRowsSynced: Int = tableResults.sumOf { it.rowCount }
}
