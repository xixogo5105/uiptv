package com.uiptv.service.remotesync

import com.uiptv.service.DatabaseSyncService
import com.uiptv.util.json.KJsonArray
import com.uiptv.util.json.KJsonObject

object RemoteSyncJson {
    private const val DIRECTION = "direction"
    private const val VERIFICATION_CODE = "verificationCode"
    private const val REQUESTER_NAME = "requesterName"
    private const val SYNC_CONFIGURATION = "syncConfiguration"
    private const val SYNC_EXTERNAL_PLAYER_PATHS = "syncExternalPlayerPaths"
    private const val MESSAGE = "message"

    @JvmStatic
    fun toJson(request: RemoteSyncRequest): KJsonObject =
        KJsonObject()
            .put(DIRECTION, request.direction.name)
            .put(VERIFICATION_CODE, request.verificationCode)
            .put(REQUESTER_NAME, request.requesterName)
            .put(SYNC_CONFIGURATION, request.options.syncConfiguration)
            .put(SYNC_EXTERNAL_PLAYER_PATHS, request.options.syncExternalPlayerPaths)

    @JvmStatic
    fun toRequest(json: KJsonObject): RemoteSyncRequest =
        RemoteSyncRequest(
            RemoteSyncDirection.valueOf(json.optString(DIRECTION, RemoteSyncDirection.EXPORT_TO_REMOTE.name)),
            json.optString(VERIFICATION_CODE, ""),
            json.optString(REQUESTER_NAME, ""),
            RemoteSyncOptions(
                json.optBoolean(SYNC_CONFIGURATION, false),
                json.optBoolean(SYNC_EXTERNAL_PLAYER_PATHS, false)
            )
        )

    @JvmStatic
    fun toJson(state: RemoteSyncSessionState): KJsonObject =
        KJsonObject()
            .put("sessionId", state.sessionId)
            .put(DIRECTION, state.direction.name)
            .put("status", state.status.name)
            .put(VERIFICATION_CODE, state.verificationCode)
            .put(REQUESTER_NAME, state.requesterName)
            .put("requesterAddress", state.requesterAddress)
            .put(SYNC_CONFIGURATION, state.options.syncConfiguration)
            .put(SYNC_EXTERNAL_PLAYER_PATHS, state.options.syncExternalPlayerPaths)
            .put(MESSAGE, state.message)

    @JvmStatic
    fun toSessionState(json: KJsonObject): RemoteSyncSessionState =
        RemoteSyncSessionState(
            json.optString("sessionId", ""),
            RemoteSyncDirection.valueOf(json.optString(DIRECTION, RemoteSyncDirection.EXPORT_TO_REMOTE.name)),
            RemoteSyncStatus.valueOf(json.optString("status", RemoteSyncStatus.FAILED.name)),
            json.optString(VERIFICATION_CODE, ""),
            json.optString(REQUESTER_NAME, ""),
            json.optString("requesterAddress", ""),
            RemoteSyncOptions(
                json.optBoolean(SYNC_CONFIGURATION, false),
                json.optBoolean(SYNC_EXTERNAL_PLAYER_PATHS, false)
            ),
            json.optString(MESSAGE, "")
        )

    @JvmStatic
    fun toJson(result: RemoteSyncExecutionResult): KJsonObject {
        val json = KJsonObject().put(MESSAGE, result.message)
        val report = result.report ?: return json
        val tables = KJsonArray()
        for (table in report.tableResults) {
            tables.put(
                KJsonObject()
                    .put("tableName", table.tableName)
                    .put("rowCount", table.rowCount)
            )
        }
        json.put(
            "report",
            KJsonObject()
                .put("configurationRequested", report.configurationRequested)
                .put("configurationCopied", report.configurationCopied)
                .put("externalPlayerPathsIncluded", report.externalPlayerPathsIncluded)
                .put("tableResults", tables)
        )
        return json
    }

    @JvmStatic
    fun toExecutionResult(json: KJsonObject): RemoteSyncExecutionResult {
        val reportJson = json.optJSONObject("report")
        if (reportJson == null) {
            return RemoteSyncExecutionResult(null, json.optString(MESSAGE, ""))
        }
        val tableResults = ArrayList<DatabaseSyncService.TableSyncResult>()
        val tables = reportJson.optJSONArray("tableResults")
        if (tables != null) {
            for (i in 0 until tables.length()) {
                val tableJson = tables.optJSONObject(i) ?: continue
                tableResults += DatabaseSyncService.TableSyncResult(
                    tableJson.optString("tableName", ""),
                    tableJson.optInt("rowCount", 0)
                )
            }
        }
        val report = DatabaseSyncService.DatabaseSyncReport(
            tableResults,
            reportJson.optBoolean("configurationRequested", false),
            reportJson.optBoolean("configurationCopied", false),
            reportJson.optBoolean("externalPlayerPathsIncluded", false)
        )
        return RemoteSyncExecutionResult(report, json.optString(MESSAGE, ""))
    }
}
