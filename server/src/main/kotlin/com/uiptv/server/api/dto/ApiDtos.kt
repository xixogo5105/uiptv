package com.uiptv.server.api.dto

import com.uiptv.service.remotesync.RemoteSyncDirection
import com.uiptv.service.remotesync.RemoteSyncExecutionResult
import com.uiptv.service.remotesync.RemoteSyncOptions
import com.uiptv.service.remotesync.RemoteSyncRequest
import com.uiptv.service.remotesync.RemoteSyncSessionState
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String
)

@Serializable
data class ConfigResponse(
    val enableThumbnails: Boolean
)

@Serializable
data class StatusResponse(
    val status: String,
    val action: String? = null,
    val message: String? = null,
    val bookmarkId: String? = null
)

@Serializable
data class BookmarkUpsertRequest(
    val accountId: String? = null,
    val categoryId: String? = null,
    val mode: String? = null,
    val channelId: String? = null,
    val id: String? = null,
    val name: String? = null,
    val cmd: String? = null,
    val logo: String? = null,
    val drmType: String? = null,
    val drmLicenseUrl: String? = null,
    val clearKeysJson: String? = null,
    val inputstreamaddon: String? = null,
    val manifestType: String? = null
)

@Serializable
data class BookmarkOrderRequest(
    val bookmarkOrders: Map<String, Int>? = null,
    val orderedBookmarkDbIds: List<String>? = null,
    val bookmarkIds: List<String>? = null
)

@Serializable
data class BookmarkDeleteRequest(
    val bookmarkId: String? = null
)

@Serializable
data class RemoteSyncHealthResponse(
    val status: String
)

@Serializable
data class RemoteSyncRequestDto(
    val direction: String,
    val verificationCode: String,
    val requesterName: String,
    val syncConfiguration: Boolean,
    val syncExternalPlayerPaths: Boolean
) {
    fun toDomain(): RemoteSyncRequest =
        RemoteSyncRequest(
            direction = RemoteSyncDirection.valueOf(direction),
            verificationCode = verificationCode,
            requesterName = requesterName,
            options = RemoteSyncOptions(syncConfiguration, syncExternalPlayerPaths)
        )
}

@Serializable
data class RemoteSyncSessionStateDto(
    val sessionId: String,
    val direction: String,
    val status: String,
    val verificationCode: String,
    val requesterName: String,
    val requesterAddress: String,
    val syncConfiguration: Boolean,
    val syncExternalPlayerPaths: Boolean,
    val message: String
) {
    companion object {
        fun fromDomain(state: RemoteSyncSessionState): RemoteSyncSessionStateDto =
            RemoteSyncSessionStateDto(
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
    }
}

@Serializable
data class RemoteSyncTableResultDto(
    val tableName: String,
    val rowCount: Int
)

@Serializable
data class RemoteSyncReportDto(
    val configurationRequested: Boolean,
    val configurationCopied: Boolean,
    val externalPlayerPathsIncluded: Boolean,
    val tableResults: List<RemoteSyncTableResultDto>
)

@Serializable
data class RemoteSyncExecutionResultDto(
    val message: String,
    val report: RemoteSyncReportDto? = null
) {
    companion object {
        fun fromDomain(result: RemoteSyncExecutionResult): RemoteSyncExecutionResultDto =
            RemoteSyncExecutionResultDto(
                message = result.message.orEmpty(),
                report = result.report?.let { report ->
                    RemoteSyncReportDto(
                        configurationRequested = report.configurationRequested,
                        configurationCopied = report.configurationCopied,
                        externalPlayerPathsIncluded = report.externalPlayerPathsIncluded,
                        tableResults = report.tableResults.map { RemoteSyncTableResultDto(it.tableName, it.rowCount) }
                    )
                }
            )
    }
}

@Serializable
data class RemoteSyncCompleteRequest(
    val sessionId: String = "",
    val success: Boolean = false,
    val message: String = ""
)
