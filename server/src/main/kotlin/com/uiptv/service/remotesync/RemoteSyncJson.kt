package com.uiptv.service.remotesync

import com.uiptv.service.DatabaseSyncService
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object RemoteSyncJson {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @JvmStatic
    fun toJson(request: RemoteSyncRequest): String =
        json.encodeToString(
            RequestPayload(
                direction = request.direction.name,
                verificationCode = request.verificationCode,
                requesterName = request.requesterName,
                syncConfiguration = request.options.syncConfiguration,
                syncExternalPlayerPaths = request.options.syncExternalPlayerPaths
            )
        )

    @JvmStatic
    fun toRequest(payload: String): RemoteSyncRequest {
        val parsed = json.decodeFromString<RequestPayload>(payload)
        return RemoteSyncRequest(
            RemoteSyncDirection.valueOf(parsed.direction),
            parsed.verificationCode,
            parsed.requesterName,
            RemoteSyncOptions(
                parsed.syncConfiguration,
                parsed.syncExternalPlayerPaths
            )
        )
    }

    @JvmStatic
    fun toJson(state: RemoteSyncSessionState): String =
        json.encodeToString(
            SessionStatePayload(
                sessionId = state.sessionId,
                direction = state.direction.name,
                status = state.status.name,
                verificationCode = state.verificationCode,
                requesterName = state.requesterName,
                requesterAddress = state.requesterAddress,
                syncConfiguration = state.options.syncConfiguration,
                syncExternalPlayerPaths = state.options.syncExternalPlayerPaths,
                message = state.message.orEmpty()
            )
        )

    @JvmStatic
    fun toSessionState(payload: String): RemoteSyncSessionState {
        val parsed = json.decodeFromString<SessionStatePayload>(payload)
        return RemoteSyncSessionState(
            parsed.sessionId,
            RemoteSyncDirection.valueOf(parsed.direction),
            RemoteSyncStatus.valueOf(parsed.status),
            parsed.verificationCode,
            parsed.requesterName,
            parsed.requesterAddress,
            RemoteSyncOptions(
                parsed.syncConfiguration,
                parsed.syncExternalPlayerPaths
            ),
            parsed.message
        )
    }

    @JvmStatic
    fun toJson(result: RemoteSyncExecutionResult): String =
        json.encodeToString(
            ExecutionResultPayload(
                message = result.message.orEmpty(),
                report = result.report?.let { report ->
                    ReportPayload(
                        configurationRequested = report.configurationRequested,
                        configurationCopied = report.configurationCopied,
                        externalPlayerPathsIncluded = report.externalPlayerPathsIncluded,
                        tableResults = report.tableResults.map { TableResultPayload(it.tableName, it.rowCount) }
                    )
                }
            )
        )

    @JvmStatic
    fun toExecutionResult(payload: String): RemoteSyncExecutionResult {
        val parsed = json.decodeFromString<ExecutionResultPayload>(payload)
        val report = parsed.report?.let { report ->
            DatabaseSyncService.DatabaseSyncReport(
                report.tableResults.map { DatabaseSyncService.TableSyncResult(it.tableName, it.rowCount) },
                report.configurationRequested,
                report.configurationCopied,
                report.externalPlayerPathsIncluded
            )
        }
        return RemoteSyncExecutionResult(report, parsed.message)
    }

    @JvmStatic
    fun toJson(sessionId: String, success: Boolean, message: String?): String =
        json.encodeToString(
            CompleteSessionPayload(
                sessionId = sessionId,
                success = success,
                message = message.orEmpty()
            )
        )

    @Serializable
    private data class RequestPayload(
        val direction: String = RemoteSyncDirection.EXPORT_TO_REMOTE.name,
        val verificationCode: String = "",
        val requesterName: String = "",
        val syncConfiguration: Boolean = false,
        val syncExternalPlayerPaths: Boolean = false
    )

    @Serializable
    private data class SessionStatePayload(
        val sessionId: String = "",
        val direction: String = RemoteSyncDirection.EXPORT_TO_REMOTE.name,
        val status: String = RemoteSyncStatus.FAILED.name,
        val verificationCode: String = "",
        val requesterName: String = "",
        val requesterAddress: String = "",
        val syncConfiguration: Boolean = false,
        val syncExternalPlayerPaths: Boolean = false,
        val message: String = ""
    )

    @Serializable
    private data class ExecutionResultPayload(
        val message: String = "",
        val report: ReportPayload? = null
    )

    @Serializable
    private data class ReportPayload(
        val configurationRequested: Boolean = false,
        val configurationCopied: Boolean = false,
        val externalPlayerPathsIncluded: Boolean = false,
        val tableResults: List<TableResultPayload> = emptyList()
    )

    @Serializable
    private data class TableResultPayload(
        val tableName: String = "",
        val rowCount: Int = 0
    )

    @Serializable
    private data class CompleteSessionPayload(
        val sessionId: String = "",
        val success: Boolean = false,
        val message: String = ""
    )
}
