package com.uiptv.server.api.routes

import com.uiptv.service.remotesync.RemoteSyncExecutionResult
import com.uiptv.service.remotesync.RemoteSyncJson
import com.uiptv.service.remotesync.RemoteSyncRequest
import com.uiptv.service.remotesync.RemoteSyncSessionService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.utils.io.jvm.javaio.toInputStream
import org.json.JSONObject
import java.nio.file.Files
import java.sql.SQLException

fun Route.registerRemoteSyncApiRoutes(
    remoteSyncSessionService: RemoteSyncSessionService
) {
    get("/remote-sync/health") {
        call.respondText(JSONObject().put("status", "ok").toString(), ContentType.Application.Json)
    }

    post("/remote-sync/request") {
        try {
            val request: RemoteSyncRequest = RemoteSyncJson.toRequest(JSONObject(call.receiveText()))
            val requesterAddress = call.request.local.remoteHost
            val response = RemoteSyncJson.toJson(remoteSyncSessionService.createSession(request, requesterAddress)).toString()
            call.respondText(response, ContentType.Application.Json)
        } catch (ex: IllegalArgumentException) {
            call.respondText(JSONObject().put("message", ex.message).toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
        }
    }

    get("/remote-sync/status") {
        try {
            val response = RemoteSyncJson.toJson(
                remoteSyncSessionService.getSessionState(call.request.queryParameters["sessionId"])
            ).toString()
            call.respondText(response, ContentType.Application.Json)
        } catch (ex: IllegalArgumentException) {
            call.respondText(JSONObject().put("message", ex.message).toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
        } catch (ex: IllegalStateException) {
            call.respondText(JSONObject().put("message", ex.message).toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
        }
    }

    put("/remote-sync/upload") {
        try {
            val result: RemoteSyncExecutionResult = remoteSyncSessionService.acceptUpload(
                call.request.queryParameters["sessionId"],
                call.receiveChannel().toInputStream()
            )
            call.respondText(RemoteSyncJson.toJson(result).toString(), ContentType.Application.Json)
        } catch (ex: IllegalArgumentException) {
            call.respondText(JSONObject().put("message", ex.message).toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
        } catch (ex: IllegalStateException) {
            call.respondText(JSONObject().put("message", ex.message).toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
        } catch (ex: SQLException) {
            call.respondText(JSONObject().put("message", ex.message).toString(), ContentType.Application.Json, HttpStatusCode.InternalServerError)
        }
    }

    get("/remote-sync/download") {
        try {
            val snapshotPath = remoteSyncSessionService.getDownloadSnapshot(call.request.queryParameters["sessionId"])
            call.respondBytes(Files.readAllBytes(snapshotPath), ContentType.Application.OctetStream)
        } catch (ex: IllegalArgumentException) {
            call.respondText(JSONObject().put("message", ex.message).toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
        } catch (ex: IllegalStateException) {
            call.respondText(JSONObject().put("message", ex.message).toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
        }
    }

    post("/remote-sync/complete") {
        try {
            val payload = JSONObject(call.receiveText())
            remoteSyncSessionService.completeImport(
                payload.optString("sessionId", ""),
                payload.optBoolean("success", false),
                payload.optString("message", "")
            )
            call.respondText(JSONObject().put("status", "ok").toString(), ContentType.Application.Json)
        } catch (ex: IllegalArgumentException) {
            call.respondText(JSONObject().put("message", ex.message).toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
        } catch (ex: IllegalStateException) {
            call.respondText(JSONObject().put("message", ex.message).toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
        }
    }
}
