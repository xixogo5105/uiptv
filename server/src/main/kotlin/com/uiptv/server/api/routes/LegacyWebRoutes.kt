package com.uiptv.server.api.routes

import com.uiptv.server.BingeWatchRouteSupport
import com.uiptv.server.HlsRouteSupport
import com.uiptv.server.ProxyStreamSupport
import com.uiptv.server.UIptvServer
import com.uiptv.server.WebStaticContentSupport
import com.uiptv.server.api.BackendHttpException
import com.uiptv.service.BingeWatchService
import com.uiptv.util.AppLog
import com.uiptv.util.HttpUtil
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.options
import io.ktor.server.routing.put
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.util.Locale

fun Route.registerLegacyWebRoutes(bingeWatchService: BingeWatchService) {
    get("/") { call.respondSpaHtml("index.html") }
    get("/index.html") { call.respondSpaHtml("index.html") }
    get("/myflix.html") { call.respondSpaHtml("myflix.html") }
    get("/player.html") { call.respondSpaHtml("player.html") }
    get("/drm.html") { call.respondSpaHtml("player.html") }

    get("/manifest.json") { call.respondStaticUtf8("/manifest.json", ContentType.Application.Json) }
    get("/sw.js") { call.respondStaticUtf8("/sw.js", ContentType.parse("text/javascript")) }
    get("/icon.ico") { call.respondIcon() }
    get("/javascript/{...}") { call.respondStaticUtf8(call.request.path(), ContentType.parse("text/javascript")) }
    get("/js/{...}") { call.respondStaticUtf8(call.request.path(), ContentType.parse("text/javascript")) }
    get("/css/{...}") { call.respondStaticUtf8(call.request.path(), ContentType.Text.CSS) }

    get("/hls/{...}") { call.respondHlsFile() }
    put("/hls-upload/{...}") { call.handleHlsUploadPut() }
    delete("/hls-upload/{...}") { call.handleHlsUploadDelete() }

    options("/proxy-stream") {
        call.response.header(HttpHeaders.Allow, "GET, HEAD")
        call.respond(HttpStatusCode.NoContent)
    }
    get("/proxy-stream") { call.handleProxyStream() }
    head("/proxy-stream") { call.handleProxyStream() }

    get("/bingewatch.m3u8") { call.respondBingeWatchPlaylist(bingeWatchService) }
    options("/bingwatch/{...}") {
        call.response.header(HttpHeaders.Allow, "GET, HEAD")
        call.respond(HttpStatusCode.NoContent)
    }
    get("/bingwatch/{...}") { call.respondBingeWatchEntry(bingeWatchService) }
    head("/bingwatch/{...}") { call.respondBingeWatchEntry(bingeWatchService) }

    get("/{...}") { call.respondSpaHtml("index.html") }
}

private suspend fun ApplicationCall.respondStaticUtf8(requestPath: String, contentType: ContentType) {
    val payload = runCatching { WebStaticContentSupport.readStaticUtf8(requestPath) }
        .getOrElse { throw BackendHttpException(HttpStatusCode.NotFound) }
    respondText(payload, contentType)
}

private suspend fun ApplicationCall.respondSpaHtml(htmlFileName: String) {
    respondText(WebStaticContentSupport.readSpaHtml(htmlFileName), ContentType.Text.Html)
}

private suspend fun ApplicationCall.respondIcon() {
    val iconBytes = runCatching { WebStaticContentSupport.readIconBytes() }
        .getOrElse { throw BackendHttpException(HttpStatusCode.NotFound) }
    response.header(HttpHeaders.ContentType, "image/x-icon")
    respondBytes(iconBytes)
}

private suspend fun ApplicationCall.respondHlsFile() {
    val fileName = request.path().substringAfterLast('/')
    val payload = HlsRouteSupport.readFile(fileName, HlsRouteSupport.isTruthy(request.queryParameters["hvec"])) ?: run {
        respond(HttpStatusCode.NotFound)
        return
    }
    response.header(HttpHeaders.AccessControlAllowOrigin, "*")
    response.header(HttpHeaders.CacheControl, "no-store")
    response.header(HttpHeaders.ContentType, payload.contentType)
    respondBytes(payload.bytes)
}

private suspend fun ApplicationCall.handleHlsUploadPut() {
    val fileName = request.path().substringAfterLast('/')
    receiveChannel().toInputStream().use { input -> HlsRouteSupport.upload(fileName, input.readAllBytes()) }
    respond(HttpStatusCode.OK)
}

private suspend fun ApplicationCall.handleHlsUploadDelete() {
    HlsRouteSupport.delete(request.path().substringAfterLast('/'))
    respond(HttpStatusCode.OK)
}

private suspend fun ApplicationCall.handleProxyStream() {
    val source = request.queryParameters["src"]?.trim()
    if (source.isNullOrBlank()) {
        respond(HttpStatusCode.BadRequest)
        return
    }
    try {
        ProxyStreamSupport.openResolvedStream(
            source,
            linkedMapOf(
                ProxyStreamSupport.HEADER_ACCEPT to request.headers[ProxyStreamSupport.HEADER_ACCEPT].orEmpty(),
                ProxyStreamSupport.HEADER_RANGE to request.headers[ProxyStreamSupport.HEADER_RANGE].orEmpty(),
                ProxyStreamSupport.HEADER_REFERER to request.headers[ProxyStreamSupport.HEADER_REFERER].orEmpty(),
                ProxyStreamSupport.HEADER_ORIGIN to request.headers[ProxyStreamSupport.HEADER_ORIGIN].orEmpty()
            ),
            request.httpMethod.value
        ).use { upstream ->
            if (upstream == null) {
                throw BackendHttpException(HttpStatusCode.BadGateway)
            }
            applyProxyResponseHeaders(this, upstream.responseHeaders)
            if (request.httpMethod.value.equals("HEAD", true)) {
                respond(HttpStatusCode.fromValue(upstream.statusCode))
                return
            }
            respondOutputStream(status = HttpStatusCode.fromValue(upstream.statusCode)) {
                upstream.bodyStream.use { input -> ProxyStreamSupport.copyStream(input, this) }
            }
        }
    } catch (cause: BackendHttpException) {
        throw cause
    } catch (_: Exception) {
        throw BackendHttpException(HttpStatusCode.BadGateway)
    }
}

private fun applyProxyResponseHeaders(call: ApplicationCall, upstreamHeaders: Map<String, List<String>>) {
    val contentType = ProxyStreamSupport.firstHeader(upstreamHeaders, ProxyStreamSupport.HEADER_CONTENT_TYPE)
    call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
    call.response.header(HttpHeaders.CacheControl, "no-store")
    call.response.header(HttpHeaders.ContentType, if (contentType.isBlank()) "application/octet-stream" else contentType)
    copyProxyHeaderIfPresent(call, upstreamHeaders, ProxyStreamSupport.HEADER_ACCEPT_RANGES)
    copyProxyHeaderIfPresent(call, upstreamHeaders, ProxyStreamSupport.HEADER_CONTENT_RANGE)
    copyProxyHeaderIfPresent(call, upstreamHeaders, ProxyStreamSupport.HEADER_CONTENT_DISPOSITION)
    ProxyStreamSupport.firstHeader(upstreamHeaders, ProxyStreamSupport.HEADER_CONTENT_LENGTH).takeIf { it.isNotBlank() }?.let {
        call.response.header(HttpHeaders.ContentLength, it)
    }
}

private fun copyProxyHeaderIfPresent(call: ApplicationCall, upstreamHeaders: Map<String, List<String>>, headerName: String) {
    ProxyStreamSupport.firstHeader(upstreamHeaders, headerName).takeIf { it.isNotBlank() }?.let {
        call.response.header(headerName, it)
    }
}

private suspend fun ApplicationCall.respondBingeWatchPlaylist(bingeWatchService: BingeWatchService) {
    val payload = BingeWatchRouteSupport.renderPlaylist(request.queryParameters["token"], bingeWatchService) ?: run {
        respond(HttpStatusCode.NotFound)
        return
    }
    response.header(HttpHeaders.AccessControlAllowOrigin, "*")
    response.header(HttpHeaders.ContentType, "application/vnd.apple.mpegurl")
    response.header(HttpHeaders.ContentDisposition, ContentDisposition.Inline.withParameter(ContentDisposition.Parameters.FileName, payload.fileName).toString())
    respondText(payload.body, ContentType.parse("application/vnd.apple.mpegurl"))
}

private suspend fun ApplicationCall.respondBingeWatchEntry(bingeWatchService: BingeWatchService) {
    when (val result = BingeWatchRouteSupport.resolveEntry(request.httpMethod.value, request.queryParameters["token"], request.queryParameters["episodeId"], bingeWatchService)) {
        else -> when (result.statusCode) {
            307 -> {
                response.header(HttpHeaders.Location, result.location.orEmpty())
                respond(HttpStatusCode.TemporaryRedirect)
            }
            404 -> respondText(result.message ?: "Binge watch item not found.", ContentType.Text.Plain, HttpStatusCode.NotFound)
            405 -> {
                response.header(HttpHeaders.Allow, "GET, HEAD")
                respond(HttpStatusCode.MethodNotAllowed)
            }
            502 -> respondText(result.message ?: "Bad gateway", ContentType.Text.Plain, HttpStatusCode.BadGateway)
            else -> respond(HttpStatusCode.fromValue(result.statusCode))
        }
    }
}
