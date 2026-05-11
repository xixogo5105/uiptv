package com.uiptv.server.api.dto

import com.uiptv.service.DatabaseSyncService
import com.uiptv.service.remotesync.RemoteSyncDirection
import com.uiptv.service.remotesync.RemoteSyncExecutionResult
import com.uiptv.service.remotesync.RemoteSyncOptions
import com.uiptv.service.remotesync.RemoteSyncRequest
import com.uiptv.service.remotesync.RemoteSyncSessionState
import com.uiptv.service.remotesync.RemoteSyncStatus
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ApiDtosTest {
    private val json = Json { explicitNulls = false }

    @Test
    fun bookmarkUpsertRequest_decodesOptionalFields() {
        val request = json.decodeFromString<BookmarkUpsertRequest>("""{"name":"BBC","channelId":"1"}""")

        assertEquals("BBC", request.name)
        assertEquals("1", request.channelId)
        assertNull(request.accountId)
        assertNull(request.clearKeysJson)
    }

    @Test
    fun remoteSyncRequestDto_toDomain_mapsAllFields() {
        val dto = RemoteSyncRequestDto(
            direction = "IMPORT_FROM_REMOTE",
            verificationCode = "6789",
            requesterName = "office",
            syncConfiguration = true,
            syncExternalPlayerPaths = false
        )

        val domain = dto.toDomain()

        assertEquals(
            RemoteSyncRequest(
                RemoteSyncDirection.IMPORT_FROM_REMOTE,
                "6789",
                "office",
                RemoteSyncOptions(true, false)
            ),
            domain
        )
    }

    @Test
    fun remoteSyncDtos_fromDomain_coverSessionAndExecutionResult() {
        val sessionState = RemoteSyncSessionState(
            "session-3",
            RemoteSyncDirection.EXPORT_TO_REMOTE,
            RemoteSyncStatus.COMPLETED,
            "1111",
            "desktop",
            "127.0.0.1",
            RemoteSyncOptions(true, true),
            "done"
        )
        val result = RemoteSyncExecutionResult(
            DatabaseSyncService.DatabaseSyncReport(
                listOf(DatabaseSyncService.TableSyncResult("account", 2)),
                true,
                true,
                true
            ),
            "synced"
        )

        val sessionDto = RemoteSyncSessionStateDto.fromDomain(sessionState)
        val resultDto = RemoteSyncExecutionResultDto.fromDomain(result)
        val report = checkNotNull(resultDto.report)

        assertEquals("COMPLETED", sessionDto.status)
        assertEquals("done", sessionDto.message)
        assertEquals("synced", resultDto.message)
        assertEquals("account", report.tableResults.first().tableName)
        assertEquals(2, report.tableResults.first().rowCount)
    }
}
