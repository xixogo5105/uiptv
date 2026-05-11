package com.uiptv.server.api.routes

import com.uiptv.server.api.dto.RemoteSyncCompleteRequest
import com.uiptv.server.api.dto.RemoteSyncExecutionResultDto
import com.uiptv.server.api.dto.RemoteSyncHealthResponse
import com.uiptv.server.api.dto.RemoteSyncRequestDto
import com.uiptv.server.api.dto.RemoteSyncSessionStateDto
import com.uiptv.server.api.dto.StatusResponse
import com.uiptv.service.remotesync.RemoteSyncSessionService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.nio.file.Files

fun Route.registerRemoteSyncApiRoutes(
    remoteSyncSessionService: RemoteSyncSessionService
) {
    get("/remote-sync/health") {
        call.respond(RemoteSyncHealthResponse(status = "ok"))
    }

    post("/remote-sync/request") {
        val request = call.receivePayload<RemoteSyncRequestDto>().toDomain()
        val requesterAddress = call.request.local.remoteHost
        call.respond(
            RemoteSyncSessionStateDto.fromDomain(
                remoteSyncSessionService.createSession(request, requesterAddress)
            )
        )
    }

    get("/remote-sync/status") {
        call.respond(
            RemoteSyncSessionStateDto.fromDomain(
                remoteSyncSessionService.getSessionState(call.request.queryParameters["sessionId"])
            )
        )
    }

    put("/remote-sync/upload") {
        val result = remoteSyncSessionService.acceptUpload(
            call.request.queryParameters["sessionId"],
            call.receiveChannel().toInputStream()
        )
        call.respond(RemoteSyncExecutionResultDto.fromDomain(result))
    }

    get("/remote-sync/download") {
        val snapshotPath = remoteSyncSessionService.getDownloadSnapshot(call.request.queryParameters["sessionId"])
        call.respondBytes(Files.readAllBytes(snapshotPath), ContentType.Application.OctetStream)
    }

    post("/remote-sync/complete") {
        val payload = call.receivePayload<RemoteSyncCompleteRequest>()
        remoteSyncSessionService.completeImport(
            payload.sessionId,
            payload.success,
            payload.message
        )
        call.respond(StatusResponse(status = "ok"))
    }
}

private suspend inline fun <reified T> ApplicationCall.receivePayload(): T {
    val text = receiveText()
    if (text.isBlank()) {
        throw IllegalArgumentException("Request body is required")
    }
    return remoteSyncRouteJson.decodeFromString(text)
}

private val remoteSyncRouteJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
