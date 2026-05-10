package com.uiptv.server.api.routes

import com.uiptv.server.StaticWebFileResolver
import com.uiptv.server.UIptvServer
import com.uiptv.server.html.HttpSpaHtmlServer
import com.uiptv.service.BingeWatchService
import com.uiptv.service.InMemoryHlsService
import com.uiptv.util.AppLog
import com.uiptv.util.HttpUtil
import com.uiptv.util.ServerUtils
import com.uiptv.util.StringUtils.isBlank
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
import org.apache.commons.io.IOUtils
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Locale
import java.util.regex.Pattern

fun Route.registerLegacyWebRoutes() {
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

    get("/bingewatch.m3u8") { call.respondBingeWatchPlaylist() }
    options("/bingwatch/{...}") {
        call.response.header(HttpHeaders.Allow, "GET, HEAD")
        call.respond(HttpStatusCode.NoContent)
    }
    get("/bingwatch/{...}") { call.respondBingeWatchEntry() }
    head("/bingwatch/{...}") { call.respondBingeWatchEntry() }

    get("/{...}") { call.respondSpaHtml("index.html") }
}

private suspend fun ApplicationCall.respondStaticUtf8(requestPath: String, contentType: ContentType) {
    try {
        val filePath = StaticWebFileResolver.resolveRequestPath(requestPath)
        respondText(StaticWebFileResolver.readUtf8(filePath), contentType)
    } catch (_: IOException) {
        respond(HttpStatusCode.NotFound)
    }
}

private suspend fun ApplicationCall.respondSpaHtml(htmlFileName: String) {
    val filePath = java.nio.file.Path.of(
        com.uiptv.util.Platform.getWebServerRootPath(),
        htmlFileName
    )
    val html = Files.readString(filePath, StandardCharsets.UTF_8)
    respondText(html, ContentType.Text.Html)
}

private suspend fun ApplicationCall.respondIcon() {
    var bytes: ByteArray? = null
    try {
        val filePath = StaticWebFileResolver.resolveRequestPath("/icon.ico")
        bytes = Files.readAllBytes(filePath)
    } catch (_: IOException) {
    }
    if (bytes == null) {
        HttpSpaHtmlServer::class.java.getResourceAsStream("/icon.ico")?.use { input ->
            bytes = IOUtils.toByteArray(input)
        }
    }
    val body = bytes ?: run {
        respond(HttpStatusCode.NotFound)
        return
    }
    response.header(HttpHeaders.ContentType, "image/x-icon")
    respondBytes(body)
}

private suspend fun ApplicationCall.respondHlsFile() {
    val fileName = request.path().substringAfterLast('/')
    InMemoryHlsService.markClientAccess()
    var data = waitForUploadIfNeeded(fileName)
    if (data.isEmpty()) {
        respond(HttpStatusCode.NotFound)
        return
    }
    if (fileName.endsWith(".m3u8") && isTruthy(request.queryParameters["hvec"])) {
        data = rewritePlaylistWithHvecQuery(data)
    }
    response.header(HttpHeaders.AccessControlAllowOrigin, "*")
    response.header(HttpHeaders.CacheControl, "no-store")
    response.header(HttpHeaders.ContentType, if (fileName.endsWith(".m3u8")) ServerUtils.CONTENT_TYPE_M3U8 else ServerUtils.CONTENT_TYPE_TS)
    respondBytes(data)
}

private suspend fun ApplicationCall.handleHlsUploadPut() {
    val fileName = request.path().substringAfterLast('/')
    receiveChannel().toInputStream().use { input ->
        InMemoryHlsService.put(fileName, IOUtils.toByteArray(input))
    }
    respond(HttpStatusCode.OK)
}

private suspend fun ApplicationCall.handleHlsUploadDelete() {
    val fileName = request.path().substringAfterLast('/')
    InMemoryHlsService.remove(fileName)
    respond(HttpStatusCode.OK)
}

private suspend fun ApplicationCall.handleProxyStream() {
    val source = request.queryParameters["src"]?.trim()
    if (source.isNullOrBlank()) {
        respond(HttpStatusCode.BadRequest)
        return
    }
    try {
        openResolvedStream(source, linkedMapOf(
            HEADER_ACCEPT to request.headers[HEADER_ACCEPT],
            HEADER_RANGE to request.headers[HEADER_RANGE],
            HEADER_REFERER to request.headers[HEADER_REFERER],
            HEADER_ORIGIN to request.headers[HEADER_ORIGIN]
        ), request.httpMethod.value).use { upstream ->
            if (upstream == null) {
                respond(HttpStatusCode.BadGateway)
                return
            }
            applyProxyResponseHeaders(this, upstream.responseHeaders)
            if (request.httpMethod.value.equals("HEAD", true)) {
                respond(HttpStatusCode.fromValue(upstream.statusCode))
                return
            }
            respondOutputStream(status = HttpStatusCode.fromValue(upstream.statusCode)) {
                upstream.bodyStream.use { input -> copyStream(input, this) }
            }
        }
    } catch (_: Exception) {
        respond(HttpStatusCode.BadGateway)
    }
}

private fun applyProxyResponseHeaders(call: ApplicationCall, upstreamHeaders: Map<String, List<String>>) {
    val contentType = firstHeader(upstreamHeaders, HEADER_CONTENT_TYPE)
    call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
    call.response.header(HttpHeaders.CacheControl, "no-store")
    call.response.header(HttpHeaders.ContentType, if (contentType.isBlank()) "application/octet-stream" else contentType)
    copyProxyHeaderIfPresent(call, upstreamHeaders, HEADER_ACCEPT_RANGES)
    copyProxyHeaderIfPresent(call, upstreamHeaders, HEADER_CONTENT_RANGE)
    copyProxyHeaderIfPresent(call, upstreamHeaders, HEADER_CONTENT_DISPOSITION)
    firstHeader(upstreamHeaders, HEADER_CONTENT_LENGTH).takeIf { it.isNotBlank() }?.let {
        call.response.header(HttpHeaders.ContentLength, it)
    }
}

private fun copyProxyHeaderIfPresent(call: ApplicationCall, upstreamHeaders: Map<String, List<String>>, headerName: String) {
    firstHeader(upstreamHeaders, headerName).takeIf { it.isNotBlank() }?.let {
        call.response.header(headerName, it)
    }
}

private suspend fun ApplicationCall.respondBingeWatchPlaylist() {
    val token = request.queryParameters["token"]
    AppLog.addInfoLog(UIptvServer::class.java, "BingeWatch: HTTP playlist request token=${token ?: ""}")
    val playlist = BingeWatchService.getInstance().renderPlaylist(token)
    if (token.isNullOrBlank() || playlist.isBlank()) {
        AppLog.addWarningLog(UIptvServer::class.java, "BingeWatch: HTTP playlist request failed token=${token ?: ""}")
        respond(HttpStatusCode.NotFound)
        return
    }
    AppLog.addInfoLog(UIptvServer::class.java, "BingeWatch: HTTP playlist response token=$token length=${playlist.length}")
    response.header(HttpHeaders.AccessControlAllowOrigin, "*")
    response.header(HttpHeaders.ContentType, "application/vnd.apple.mpegurl")
    response.header(HttpHeaders.ContentDisposition, ContentDisposition.Inline.withParameter(ContentDisposition.Parameters.FileName, "binge-watch-$token.m3u8").toString())
    respondText(playlist, ContentType.parse("application/vnd.apple.mpegurl"))
}

private suspend fun ApplicationCall.respondBingeWatchEntry() {
    val method = request.httpMethod.value
    AppLog.addInfoLog(UIptvServer::class.java, "BingeWatch: HTTP entry request method=${AppLog.sanitizeValue(method)} uri=${AppLog.sanitizeValue(request.uri)}")
    val token = request.queryParameters["token"]
    val episodeId = request.queryParameters["episodeId"]
    if (token.isNullOrBlank() || episodeId.isNullOrBlank()) {
        AppLog.addWarningLog(UIptvServer::class.java, "BingeWatch: HTTP entry missing params token=${AppLog.sanitizeValue(token ?: "")} episodeId=${AppLog.sanitizeValue(episodeId ?: "")}")
        respond(HttpStatusCode.NotFound)
        return
    }
    try {
        val resolved = BingeWatchService.getInstance().resolveEpisode(token, episodeId)
        if (resolved == null || resolved.url.isNullOrBlank()) {
            AppLog.addWarningLog(UIptvServer::class.java, "BingeWatch: HTTP entry resolve failed token=${AppLog.sanitizeValue(token)} episodeId=${AppLog.sanitizeValue(episodeId)}")
            respondText("Binge watch item not found.", ContentType.Text.Plain, HttpStatusCode.NotFound)
            return
        }
        AppLog.addInfoLog(UIptvServer::class.java, "BingeWatch: HTTP entry redirect token=${AppLog.sanitizeValue(token)} episodeId=${AppLog.sanitizeValue(episodeId)} location=${AppLog.sanitizeValue(resolved.url)}")
        response.header(HttpHeaders.Location, resolved.url)
        respond(HttpStatusCode.TemporaryRedirect)
    } catch (ex: Exception) {
        AppLog.addErrorLog(UIptvServer::class.java, "BingeWatch: HTTP entry exception token=${AppLog.sanitizeValue(token)} episodeId=${AppLog.sanitizeValue(episodeId)} error=${AppLog.sanitizeValue(ex.message)}")
        respondText("Unable to resolve binge watch episode: ${ex.message}", ContentType.Text.Plain, HttpStatusCode.BadGateway)
    }
}

private val missingTsRetryAttempts = Integer.getInteger("uiptv.hls.missing.ts.retry.attempts", 100)
private val missingTsRetrySleepMs = java.lang.Long.getLong("uiptv.hls.missing.ts.retry.sleep.millis", 50L)
private val emptyContent = ByteArray(0)

private fun waitForUploadIfNeeded(fileName: String): ByteArray {
    var data = InMemoryHlsService.get(fileName)
    if (data != null || !fileName.endsWith(".ts")) {
        return data ?: emptyContent
    }
    repeat(missingTsRetryAttempts) {
        try {
            Thread.sleep(missingTsRetrySleepMs)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return emptyContent
        }
        data = InMemoryHlsService.get(fileName)
        if (data != null) {
            return data
        }
    }
    return emptyContent
}

private fun isTruthy(value: String?): Boolean =
    when (value?.trim()?.lowercase()) {
        "1", "true", "yes", "on" -> true
        else -> false
    }

private fun rewritePlaylistWithHvecQuery(data: ByteArray): ByteArray {
    if (data.isEmpty()) {
        return data
    }
    val body = String(data, StandardCharsets.UTF_8)
    val lines = Pattern.compile("\\r?\\n").split(body, -1)
    val rewritten = StringBuilder(body.length + 32)
    lines.forEachIndexed { index, sourceLine ->
        var line = sourceLine
        val trimmed = line.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.contains("hvec=")) {
            line += if (line.contains("?")) "&" else "?"
            line += "hvec=1"
        }
        rewritten.append(line)
        if (index < lines.size - 1) {
            rewritten.append('\n')
        }
    }
    return rewritten.toString().toByteArray(StandardCharsets.UTF_8)
}

private const val PATH_LIVE_PLAY = "/live/play/"
private const val PATH_PLAY_MOVIE = "/play/movie.php"
private const val HEADER_ACCEPT = "Accept"
private const val HEADER_ACCEPT_RANGES = "Accept-Ranges"
private const val HEADER_CONTENT_DISPOSITION = "Content-Disposition"
private const val HEADER_CONTENT_LENGTH = "Content-Length"
private const val HEADER_CONTENT_RANGE = "Content-Range"
private const val HEADER_CONTENT_TYPE = "Content-Type"
private const val HEADER_COOKIE = "Cookie"
private const val HEADER_LOCATION = "Location"
private const val HEADER_ORIGIN = "Origin"
private const val HEADER_PRAGMA = "Pragma"
private const val HEADER_RANGE = "Range"
private const val HEADER_REFERER = "Referer"
private const val HEADER_SET_COOKIE = "Set-Cookie"
private const val HEADER_X_USER_AGENT = "X-User-Agent"
private const val MAG_USER_AGENT = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

private fun openResolvedStream(
    initialUrl: String,
    forwardHeaders: Map<String, String?>,
    requestMethod: String
): HttpUtil.StreamResult? {
    var current = initialUrl
    val cookies = ArrayList<String>()
    repeat(6) {
        val upstreamHeaders = buildUpstreamHeaders(current, cookies, forwardHeaders)
        val upstreamMethod = if (requestMethod.equals("HEAD", true)) "HEAD" else "GET"
        val upstream = HttpUtil.openStream(current, upstreamHeaders, upstreamMethod, null, HttpUtil.RequestOptions(false, true))
        collectCookies(upstream.responseHeaders, cookies)
        val status = upstream.statusCode
        if (status in 300..399) {
            upstream.use {
                val location = firstHeader(it.responseHeaders, HEADER_LOCATION)
                if (location.isBlank()) {
                    return null
                }
                val resolved = URI.create(current).resolve(location)
                current = downgradeHttpsToHttp(resolved.toString())
                return@repeat
            }
        }
        if (status == HttpUtil.STATUS_NOT_ACCEPTABLE) {
            upstream.use {
                val fallback = build406Fallback(current)
                if (fallback.isNotBlank() && fallback != current) {
                    current = fallback
                    return@repeat
                }
            }
        }
        return upstream
    }
    return null
}

private fun collectCookies(responseHeaders: Map<String, List<String>>?, cookies: MutableList<String>) {
    if (responseHeaders == null) return
    val setCookie = responseHeaders.entries.firstOrNull { HEADER_SET_COOKIE.equals(it.key, true) }?.value ?: return
    setCookie.forEach { row ->
        if (row.isBlank()) return@forEach
        val pair = row.split(";", limit = 2)[0].trim()
        if (pair.isBlank()) return@forEach
        val key = pair.split("=", limit = 2)[0].trim()
        if (key.isBlank()) return@forEach
        cookies.removeIf { existing -> existing.startsWith("$key=") }
        cookies += pair
    }
}

private fun firstHeader(headers: Map<String, List<String>>?, name: String): String {
    if (headers == null || name.isBlank()) return ""
    return headers.entries.firstOrNull { it.key.equals(name, true) }?.value?.firstOrNull()?.takeIf { it.isNotBlank() } ?: ""
}

private fun buildUpstreamHeaders(currentUrl: String, cookies: MutableList<String>, forwardedHeaders: Map<String, String?>): MutableMap<String, String> {
    val upstreamHeaders = linkedMapOf<String, String>()
    val stalkerStyle = isStalkerPortalStream(currentUrl)
    upstreamHeaders["User-Agent"] = if (stalkerStyle) MAG_USER_AGENT else USER_AGENT
    upstreamHeaders[HEADER_ACCEPT] = forwardedHeaders[HEADER_ACCEPT].takeUnless { it.isNullOrBlank() } ?: "*/*"
    upstreamHeaders["Accept-Encoding"] = "identity"
    if (stalkerStyle) {
        upstreamHeaders[HEADER_X_USER_AGENT] = "Model: MAG250; Link: WiFi"
        upstreamHeaders[HEADER_PRAGMA] = "no-cache"
    }
    addHeaderIfPresent(upstreamHeaders, HEADER_RANGE, forwardedHeaders[HEADER_RANGE])
    val origin = resolveUpstreamOriginHeader(currentUrl, forwardedHeaders[HEADER_ORIGIN])
    val referer = resolveUpstreamRefererHeader(currentUrl, forwardedHeaders[HEADER_REFERER])
    addHeaderIfPresent(upstreamHeaders, HEADER_ORIGIN, origin)
    addHeaderIfPresent(upstreamHeaders, HEADER_REFERER, referer)
    addStalkerCookieFromUrl(upstreamHeaders, currentUrl, cookies)
    if (cookies.isNotEmpty()) {
        upstreamHeaders.putIfAbsent(HEADER_COOKIE, cookies.joinToString("; "))
    }
    return upstreamHeaders
}

private fun addStalkerCookieFromUrl(upstreamHeaders: MutableMap<String, String>, currentUrl: String, cookies: MutableList<String>) {
    if (!isStalkerPortalStream(currentUrl)) return
    val mac = queryParam(currentUrl, "mac")
    if (mac.isBlank()) return
    upstreamHeaders[HEADER_COOKIE] = "mac=$mac; stb_lang=en; timezone=Europe/London;"
    if (cookies.none { it.startsWith("mac=") }) cookies += "mac=$mac"
}

private fun resolveUpstreamOriginHeader(currentUrl: String, forwardedOrigin: String?): String {
    val sourceOrigin = originOf(currentUrl)
    if (sourceOrigin.isBlank()) return ""
    val origin = forwardedOrigin ?: return if (shouldForcePortalHeaders(currentUrl)) sourceOrigin else ""
    if (isLocalOrigin(origin) || !sameOrigin(sourceOrigin, origin)) {
        return if (shouldForcePortalHeaders(currentUrl)) sourceOrigin else ""
    }
    return origin
}

private fun resolveUpstreamRefererHeader(currentUrl: String, forwardedReferer: String?): String {
    val sourceOrigin = originOf(currentUrl)
    if (sourceOrigin.isBlank()) return ""
    val referer = forwardedReferer ?: return if (shouldForcePortalHeaders(currentUrl)) "$sourceOrigin/" else ""
    if (isLocalOrigin(referer) || !sameOrigin(sourceOrigin, referer)) {
        return if (shouldForcePortalHeaders(currentUrl)) "$sourceOrigin/" else ""
    }
    return referer
}

private fun shouldForcePortalHeaders(currentUrl: String): Boolean {
    if (currentUrl.isBlank()) return false
    val lower = currentUrl.lowercase(Locale.ROOT)
    return lower.contains(PATH_LIVE_PLAY) || lower.contains(PATH_PLAY_MOVIE) || hasNumericLastPathSegment(lower)
}

private fun isStalkerPortalStream(currentUrl: String): Boolean {
    if (currentUrl.isBlank()) return false
    val lower = currentUrl.lowercase(Locale.ROOT)
    return (lower.contains(PATH_LIVE_PLAY) || lower.contains(PATH_PLAY_MOVIE)) &&
        (lower.contains("play_token=") || lower.contains("mac="))
}

private fun sameOrigin(left: String, right: String): Boolean {
    val leftOrigin = originOf(left)
    val rightOrigin = originOf(right)
    return leftOrigin.isNotBlank() && leftOrigin.equals(rightOrigin, true)
}

private fun isLocalOrigin(url: String): Boolean =
    try {
        when (URI.create(originOf(url)).host?.trim()?.lowercase(Locale.ROOT)) {
            "127.0.0.1", "localhost", "::1" -> true
            else -> false
        }
    } catch (_: Exception) {
        false
    }

private fun originOf(url: String): String {
    if (url.isBlank()) return ""
    return try {
        val uri = URI.create(url.trim())
        val scheme = uri.scheme
        val host = uri.host
        if (scheme.isNullOrBlank() || host.isNullOrBlank()) return ""
        val port = uri.port
        val defaultPort = port < 0 || ("http".equals(scheme, true) && port == 80) || ("https".equals(scheme, true) && port == 443)
        if (defaultPort) "$scheme://$host" else "$scheme://$host:$port"
    } catch (_: Exception) {
        ""
    }
}

private fun queryParam(url: String, key: String): String {
    if (url.isBlank() || key.isBlank()) return ""
    return try {
        val query = URI.create(url.trim()).rawQuery ?: return ""
        query.split("&").forEach { pair ->
            if (pair.isNotBlank()) {
                val parts = pair.split("=", limit = 2)
                if (parts.isNotEmpty() && key.equals(parts[0], true)) {
                    return if (parts.size > 1) URLDecoder.decode(parts[1], StandardCharsets.UTF_8) else ""
                }
            }
        }
        ""
    } catch (_: Exception) {
        ""
    }
}

private fun addHeaderIfPresent(headers: MutableMap<String, String>, name: String, value: String?) {
    if (!value.isNullOrBlank()) headers[name] = value
}

private fun copyStream(inputStream: InputStream, outputStream: java.io.OutputStream) {
    val buffer = ByteArray(8192)
    while (true) {
        val read = inputStream.read(buffer)
        if (read == -1) break
        outputStream.write(buffer, 0, read)
    }
}

private fun downgradeHttpsToHttp(url: String): String {
    if (url.isBlank()) return url
    val lower = url.lowercase(Locale.ROOT)
    return if (lower.startsWith("https://") && (lower.contains(PATH_LIVE_PLAY) || lower.contains(PATH_PLAY_MOVIE) || hasNumericLastPathSegment(lower))) {
        "http://" + url.substring("https://".length)
    } else {
        url
    }
}

private fun build406Fallback(originalUrl: String): String {
    if (originalUrl.isBlank()) return originalUrl
    return try {
        val uri = URI.create(originalUrl)
        val path = uri.path
        if (path.isNullOrBlank()) return originalUrl
        val segments = path.split("/").filter { it.isNotBlank() }.toMutableList()
        if (segments.size < 3) return originalUrl
        val last = segments.last()
        if (!isAsciiDigits(last)) return originalUrl
        val ext = extensionOf(last)
        if (ext == "ts" || ext == "m3u8") return originalUrl
        segments[segments.lastIndex] = "$last.ts"
        URI(uri.scheme, uri.userInfo, uri.host, uri.port, "/" + segments.joinToString("/"), uri.query, uri.fragment).toString()
    } catch (_: Exception) {
        originalUrl
    }
}

private fun extensionOf(value: String): String {
    if (value.isBlank()) return ""
    val dot = value.lastIndexOf('.')
    if (dot < 0 || dot == value.length - 1) return ""
    return value.substring(dot + 1).lowercase(Locale.ROOT)
}

private fun hasNumericLastPathSegment(url: String): Boolean {
    if (url.isBlank()) return false
    val queryStart = url.indexOf('?')
    val pathOnly = if (queryStart >= 0) url.substring(0, queryStart) else url
    val lastSlash = pathOnly.lastIndexOf('/')
    if (lastSlash < 0 || lastSlash == pathOnly.length - 1) return false
    return isAsciiDigits(pathOnly.substring(lastSlash + 1))
}

private fun isAsciiDigits(value: String?): Boolean = !value.isNullOrBlank() && value.all(Char::isDigit)
