package com.uiptv.server.api.dto

import com.uiptv.service.remotesync.RemoteSyncDirection
import com.uiptv.service.remotesync.RemoteSyncExecutionResult
import com.uiptv.service.remotesync.RemoteSyncOptions
import com.uiptv.service.remotesync.RemoteSyncRequest
import com.uiptv.service.remotesync.RemoteSyncSessionState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

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

@Serializable
data class WatchingNowSeriesActionRequest(
    val accountId: String? = null,
    val categoryId: String? = null,
    val seriesId: String? = null,
    val episodeId: String? = null,
    val episodeName: String? = null,
    val season: String? = null,
    val episodeNum: String? = null,
    val categoryDbId: String? = null,
    val seriesTitle: String? = null,
    val seriesPoster: String? = null,
    val episodes: JsonArray? = null
)

@Serializable
data class WatchingNowVodActionRequest(
    val accountId: String? = null,
    val categoryId: String? = null,
    val vodId: String? = null,
    val vodName: String? = null,
    val vodCmd: String? = null,
    val vodLogo: String? = null
)

@Serializable
data class PlayerPlaybackResponseDto(
    val url: String = "",
    val strategyHint: String? = null,
    val channel: PlayerPlaybackChannelDto? = null,
    val title: String? = null,
    val drm: PlayerPlaybackDrmDto? = null,
    val ffmpegMode: String? = null,
    val bingeWatch: PlayerPlaybackBingeWatchDto? = null
)

@Serializable
data class PlayerPlaybackChannelDto(
    val channelId: String = "",
    val name: String = "",
    val logo: String = "",
    val season: String = "",
    val episodeNum: String = ""
)

@Serializable
data class PlayerPlaybackDrmDto(
    val type: String? = null,
    val licenseUrl: String? = null,
    val clearKeys: JsonObject? = null,
    val inputstreamaddon: String? = null,
    val manifestType: String? = null
)

@Serializable
data class PlayerPlaybackBingeWatchDto(
    val token: String,
    val currentEpisodeId: String = "",
    val items: List<PlayerPlaybackBingeWatchItemDto>
)

@Serializable
data class PlayerPlaybackBingeWatchItemDto(
    val episodeId: String = "",
    val episodeName: String = "",
    val season: String = "",
    val episodeNumber: String = ""
)

@Serializable
data class ChannelRouteDto(
    val dbId: String? = null,
    val channelId: String? = null,
    val categoryId: String? = null,
    val name: String? = null,
    val number: String? = null,
    val cmd: String? = null,
    val cmd_1: String? = null,
    val cmd_2: String? = null,
    val cmd_3: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val season: String? = null,
    val episodeNum: String? = null,
    val releaseDate: String? = null,
    val rating: String? = null,
    val duration: String? = null,
    val extraJson: String? = null,
    val censored: Int = 0,
    val status: Int = 0,
    val hd: Int = 0,
    val watched: Boolean = false,
    val drmType: String? = null,
    val drmLicenseUrl: String? = null,
    val clearKeysJson: String? = null,
    val inputstreamaddon: String? = null,
    val manifestType: String? = null
)

@Serializable
data class VodInfoDto(
    val name: String = "",
    val cover: String = "",
    val plot: String = "",
    val cast: String = "",
    val director: String = "",
    val genre: String = "",
    val releaseDate: String = "",
    val rating: String = "",
    val tmdb: String = "",
    val imdbUrl: String = "",
    val duration: String = ""
)

@Serializable
data class VodDetailsResponseDto(
    val vodInfo: VodInfoDto
)

@Serializable
data class WatchingNowSeriesRowDto(
    val key: String = "",
    val accountId: String = "",
    val accountName: String = "",
    val accountType: String = "",
    val categoryId: String = "",
    val categoryDbId: String = "",
    val seriesId: String = "",
    val episodeId: String = "",
    val episodeName: String = "",
    val season: String = "",
    val episodeNum: String = "",
    val seriesTitle: String = "",
    val seriesPoster: String = "",
    val updatedAt: Long = 0L
)

@Serializable
data class WatchingNowVodRowDto(
    val accountId: String = "",
    val accountName: String = "",
    val accountType: String = "",
    val categoryId: String = "",
    val vodId: String = "",
    val vodName: String = "",
    val vodLogo: String = "",
    val plot: String = "",
    val releaseDate: String = "",
    val rating: String = "",
    val duration: String = "",
    val updatedAt: Long = 0L,
    val playItem: ChannelRouteDto? = null
)

@Serializable
data class SeriesDetailsResponseDto(
    val seasonInfo: JsonObject,
    val episodes: List<ChannelRouteDto>,
    val episodesMeta: List<JsonObject> = emptyList()
)
