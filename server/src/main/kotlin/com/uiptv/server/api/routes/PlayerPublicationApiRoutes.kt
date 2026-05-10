package com.uiptv.server.api.routes

import com.uiptv.service.PlaylistExportService
import com.uiptv.service.WebPlayerApiService
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.route
import io.ktor.server.routing.options

fun Route.registerPlayerPublicationApiRoutes(
    webPlayerApiService: WebPlayerApiService,
    playlistExportService: PlaylistExportService
) {
    route("/player") {
        get {
            call.respondJson(webPlayerApiService.buildJsonPlaybackResponse(call.request.path(), call.request.queryParameters.toSingleMap()))
        }
    }
    route("/player/{...}") {
        get {
            call.respondJson(webPlayerApiService.buildJsonPlaybackResponse(call.request.path(), call.request.queryParameters.toSingleMap()))
        }
    }

    get("/playlist.m3u8") {
        val document = playlistExportService.buildSingleChannelPlaylist(
            call.request.queryParameters["accountId"],
            call.request.queryParameters["categoryId"],
            call.request.queryParameters["channelId"]
        )
        call.respondPlaylist(document.content, document.fileName)
    }

    options("/bookmarkEntry.ts") {
        call.response.header(HttpHeaders.Allow, "GET, HEAD")
        call.respondText("", status = HttpStatusCode.NoContent)
    }
    get("/bookmarkEntry.ts") {
        call.respondBookmarkEntry(playlistExportService.resolveBookmarkRequest("GET", call.request.queryParameters["bookmarkId"]))
    }
    head("/bookmarkEntry.ts") {
        call.respondBookmarkEntry(playlistExportService.resolveBookmarkRequest("HEAD", call.request.queryParameters["bookmarkId"]))
    }

    get("/bookmarks.m3u8") {
        val host = call.request.headers["Host"].orEmpty()
        call.respondPlaylist(
            playlistExportService.buildBookmarksPlaylist(host),
            "$host-bookmarks.m3u8"
        )
    }

    get("/iptv.m3u8") {
        val document = playlistExportService.buildPublishedPlaylist(call.request.headers["Host"], call.request.path())
        call.respondPlaylist(document.content, document.fileName)
    }
    get("/iptv.m3u") {
        val document = playlistExportService.buildPublishedPlaylist(call.request.headers["Host"], call.request.path())
        call.respondPlaylist(document.content, document.fileName)
    }
}

private suspend fun ApplicationCall.respondJson(body: String) {
    response.header("Access-Control-Allow-Origin", "*")
    response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    respondText(body, ContentType.Application.Json)
}

private suspend fun ApplicationCall.respondPlaylist(body: String, fileName: String) {
    response.header("Access-Control-Allow-Origin", "*")
    response.header("Access-Control-Allow-Methods", "GET")
    response.header("Access-Control-Allow-Headers", "*")
    response.header("Access-Control-Allow-Credentials", "true")
    response.header("Access-Control-Allow-Credentials-Header", "*")
    response.header(HttpHeaders.ContentType, "application/vnd.apple.mpegurl")
    response.header(HttpHeaders.ContentDisposition, "inline; filename=$fileName")
    respondText(body, ContentType.parse("application/vnd.apple.mpegurl"))
}

private suspend fun ApplicationCall.respondBookmarkEntry(result: PlaylistExportService.BookmarkRedirectResult) {
    result.allowHeader?.let { response.header(HttpHeaders.Allow, it) }
    result.location?.let {
        response.header(HttpHeaders.Location, it)
        respondText("", status = HttpStatusCode.fromValue(result.statusCode))
        return
    }
    if (result.responseBody != null) {
        response.header("Access-Control-Allow-Origin", "*")
        respondText(result.responseBody, ContentType.Text.Plain, HttpStatusCode.fromValue(result.statusCode))
        return
    }
    respondText("", status = HttpStatusCode.fromValue(result.statusCode))
}

private fun io.ktor.http.Parameters.toSingleMap(): Map<String, String?> = names().associateWith { get(it) }
