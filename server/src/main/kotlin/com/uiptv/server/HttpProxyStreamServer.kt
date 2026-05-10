package com.uiptv.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.util.HttpUtil
import com.uiptv.util.ServerUtils.getParam
import com.uiptv.util.StringUtils.isBlank
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Suppress("java:S1075", "java:S135")
class HttpProxyStreamServer : HttpHandler {
    companion object {
        private const val PATH_LIVE_PLAY = "/live/play/"
        private const val PATH_PLAY_MOVIE = "/play/movie.php"
        private const val HEADER_ACCEPT = "Accept"
        private const val HEADER_ACCEPT_RANGES = "Accept-Ranges"
        private const val HEADER_ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin"
        private const val HEADER_CACHE_CONTROL = "Cache-Control"
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
        private const val UNKNOWN_CONTENT_LENGTH = 0L
    }

    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        val source = getParam(ex, "src")
        if (isBlank(source)) {
            ex.sendResponseHeaders(400, -1)
            return
        }
        val current = source!!.trim()
        val cookies = ArrayList<String>()
        val requestMethod = ex.requestMethod
        try {
            openResolvedStream(current, cookies, readForwardHeaders(ex), requestMethod).use { upstream ->
                if (upstream == null) {
                    sendBadGateway(ex)
                    return
                }
                writeResponseHeaders(ex, upstream.responseHeaders)
                ex.sendResponseHeaders(upstream.statusCode, resolveContentLength(firstHeader(upstream.responseHeaders, HEADER_CONTENT_LENGTH)))
                if (!"HEAD".equals(requestMethod, true)) {
                    resolvedBodyStream(upstream).use { input ->
                        ex.responseBody.use { output -> copyStream(input, output) }
                    }
                }
            }
        } catch (_: Exception) {
            sendBadGateway(ex)
        }
    }

    private fun openResolvedStream(
        initialUrl: String,
        cookies: MutableList<String>,
        forwardHeaders: Map<String, String>,
        requestMethod: String
    ): HttpUtil.StreamResult? {
        var current = initialUrl
        repeat(6) {
            val upstreamHeaders = buildUpstreamHeaders(current, cookies, forwardHeaders)
            val upstreamMethod = if ("HEAD".equals(requestMethod, true)) "HEAD" else "GET"
            val upstream = HttpUtil.openStream(current, upstreamHeaders, upstreamMethod, null, HttpUtil.RequestOptions(false, true))
            collectCookies(upstream.responseHeaders, cookies)
            val status = upstream.statusCode
            if (status in 300..399) {
                upstream.use {
                    val location = firstHeader(it.responseHeaders, HEADER_LOCATION)
                    if (isBlank(location)) {
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
                    if (!isBlank(fallback) && fallback != current) {
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
            if (isBlank(row)) return@forEach
            val pair = row.split(";", limit = 2)[0].trim()
            if (isBlank(pair)) return@forEach
            val key = pair.split("=", limit = 2)[0].trim()
            if (isBlank(key)) return@forEach
            cookies.removeIf { existing -> existing.startsWith("$key=") }
            cookies += pair
        }
    }

    private fun firstHeader(headers: Map<String, List<String>>?, name: String): String {
        if (headers == null || isBlank(name)) return ""
        headers.entries.firstOrNull { it.key.equals(name, true) }?.value?.firstOrNull()?.let {
            if (!isBlank(it)) return it
        }
        return ""
    }

    private fun readForwardHeaders(ex: HttpExchange): Map<String, String> = linkedMapOf(
        HEADER_ACCEPT to ex.requestHeaders.getFirst(HEADER_ACCEPT),
        HEADER_RANGE to ex.requestHeaders.getFirst(HEADER_RANGE),
        HEADER_REFERER to ex.requestHeaders.getFirst(HEADER_REFERER),
        HEADER_ORIGIN to ex.requestHeaders.getFirst(HEADER_ORIGIN)
    )

    private fun buildUpstreamHeaders(currentUrl: String, cookies: MutableList<String>, forwardedHeaders: Map<String, String>): MutableMap<String, String> {
        val upstreamHeaders = linkedMapOf<String, String>()
        val stalkerStyle = isStalkerPortalStream(currentUrl)
        upstreamHeaders["User-Agent"] = if (stalkerStyle) MAG_USER_AGENT else USER_AGENT
        upstreamHeaders[HEADER_ACCEPT] = if (isBlank(forwardedHeaders[HEADER_ACCEPT])) "*/*" else forwardedHeaders[HEADER_ACCEPT]!!
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
        if (isBlank(mac)) return
        upstreamHeaders[HEADER_COOKIE] = "mac=$mac; stb_lang=en; timezone=Europe/London;"
        if (cookies.none { it.startsWith("mac=") }) cookies += "mac=$mac"
    }

    private fun resolveUpstreamOriginHeader(currentUrl: String, forwardedOrigin: String?): String {
        val sourceOrigin = originOf(currentUrl)
        if (isBlank(sourceOrigin)) return ""
        val origin = forwardedOrigin ?: return if (shouldForcePortalHeaders(currentUrl)) sourceOrigin else ""
        if (isLocalOrigin(origin) || !sameOrigin(sourceOrigin, origin)) {
            return if (shouldForcePortalHeaders(currentUrl)) sourceOrigin else ""
        }
        return origin
    }

    private fun resolveUpstreamRefererHeader(currentUrl: String, forwardedReferer: String?): String {
        val sourceOrigin = originOf(currentUrl)
        if (isBlank(sourceOrigin)) return ""
        val referer = forwardedReferer ?: return if (shouldForcePortalHeaders(currentUrl)) "$sourceOrigin/" else ""
        if (isLocalOrigin(referer) || !sameOrigin(sourceOrigin, referer)) {
            return if (shouldForcePortalHeaders(currentUrl)) "$sourceOrigin/" else ""
        }
        return referer
    }

    private fun shouldForcePortalHeaders(currentUrl: String): Boolean {
        if (isBlank(currentUrl)) return false
        val lower = currentUrl.lowercase()
        return lower.contains(PATH_LIVE_PLAY) || lower.contains(PATH_PLAY_MOVIE) || hasNumericLastPathSegment(lower)
    }

    private fun isStalkerPortalStream(currentUrl: String): Boolean {
        if (isBlank(currentUrl)) return false
        val lower = currentUrl.lowercase()
        return (lower.contains(PATH_LIVE_PLAY) || lower.contains(PATH_PLAY_MOVIE)) &&
            (lower.contains("play_token=") || lower.contains("mac="))
    }

    private fun sameOrigin(left: String, right: String): Boolean {
        val leftOrigin = originOf(left)
        val rightOrigin = originOf(right)
        return !isBlank(leftOrigin) && leftOrigin.equals(rightOrigin, true)
    }

    private fun isLocalOrigin(url: String): Boolean {
        val origin = originOf(url)
        if (isBlank(origin)) return false
        return try {
            val host = URI.create(origin).host
            if (isBlank(host)) false else when (host.trim().lowercase()) {
                "127.0.0.1", "localhost", "::1" -> true
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun originOf(url: String): String {
        if (isBlank(url)) return ""
        return try {
            val uri = URI.create(url.trim())
            val scheme = uri.scheme
            val host = uri.host
            if (isBlank(scheme) || isBlank(host)) return ""
            val port = uri.port
            val defaultPort = port < 0 || ("http".equals(scheme, true) && port == 80) || ("https".equals(scheme, true) && port == 443)
            if (defaultPort) "$scheme://$host" else "$scheme://$host:$port"
        } catch (_: Exception) {
            ""
        }
    }

    private fun queryParam(url: String, key: String): String {
        if (isBlank(url) || isBlank(key)) return ""
        return try {
            val query = URI.create(url.trim()).rawQuery
            if (isBlank(query)) return ""
            query.split("&").forEach { pair ->
                if (!isBlank(pair)) {
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
        if (!isBlank(value)) headers[name] = value!!
    }

    private fun resolvedBodyStream(upstream: HttpUtil.StreamResult): InputStream =
        upstream.bodyStream

    private fun writeResponseHeaders(ex: HttpExchange, upstreamHeaders: Map<String, List<String>>) {
        val contentType = firstHeader(upstreamHeaders, HEADER_CONTENT_TYPE)
        ex.responseHeaders.add(HEADER_ACCESS_CONTROL_ALLOW_ORIGIN, "*")
        ex.responseHeaders.add(HEADER_CACHE_CONTROL, "no-store")
        ex.responseHeaders.add(HEADER_CONTENT_TYPE, if (isBlank(contentType)) "application/octet-stream" else contentType)
        copyHeaderIfPresent(ex, upstreamHeaders, HEADER_ACCEPT_RANGES)
        copyHeaderIfPresent(ex, upstreamHeaders, HEADER_CONTENT_RANGE)
        copyHeaderIfPresent(ex, upstreamHeaders, HEADER_CONTENT_DISPOSITION)
    }

    private fun copyHeaderIfPresent(ex: HttpExchange, upstreamHeaders: Map<String, List<String>>, headerName: String) {
        val value = firstHeader(upstreamHeaders, headerName)
        if (!isBlank(value)) ex.responseHeaders.add(headerName, value)
    }

    private fun resolveContentLength(contentLengthHeader: String): Long =
        try {
            val contentLength = if (isBlank(contentLengthHeader)) UNKNOWN_CONTENT_LENGTH else contentLengthHeader.toLong()
            if (contentLength > 0) contentLength else UNKNOWN_CONTENT_LENGTH
        } catch (_: Exception) {
            UNKNOWN_CONTENT_LENGTH
        }

    @Throws(IOException::class)
    private fun copyStream(inputStream: InputStream, outputStream: OutputStream) {
        val buffer = ByteArray(8192)
        while (true) {
            val read = inputStream.read(buffer)
            if (read == -1) break
            outputStream.write(buffer, 0, read)
        }
    }

    @Throws(IOException::class)
    private fun sendBadGateway(ex: HttpExchange) {
        ex.sendResponseHeaders(502, -1)
    }

    private fun downgradeHttpsToHttp(url: String): String {
        if (isBlank(url)) return url
        val lower = url.lowercase()
        return if (lower.startsWith("https://") && (lower.contains(PATH_LIVE_PLAY) || lower.contains(PATH_PLAY_MOVIE) || hasNumericLastPathSegment(lower))) {
            "http://" + url.substring("https://".length)
        } else {
            url
        }
    }

    private fun build406Fallback(originalUrl: String): String {
        if (isBlank(originalUrl)) return originalUrl
        return try {
            val uri = URI.create(originalUrl)
            val path = uri.path
            if (isBlank(path)) return originalUrl
            val segments = path.split("/").filter { !isBlank(it) }.toMutableList()
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
        if (isBlank(value)) return ""
        val dot = value.lastIndexOf('.')
        if (dot < 0 || dot == value.length - 1) return ""
        return value.substring(dot + 1).lowercase()
    }

    private fun hasNumericLastPathSegment(url: String): Boolean {
        if (isBlank(url)) return false
        val queryStart = url.indexOf('?')
        val pathOnly = if (queryStart >= 0) url.substring(0, queryStart) else url
        val lastSlash = pathOnly.lastIndexOf('/')
        if (lastSlash < 0 || lastSlash == pathOnly.length - 1) return false
        return isAsciiDigits(pathOnly.substring(lastSlash + 1))
    }

    private fun isAsciiDigits(value: String?): Boolean = !isBlank(value) && value!!.all(Char::isDigit)
}
