package com.uiptv.util

import io.ktor.client.call.body
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase
import org.apache.hc.client5.http.protocol.HttpClientContext
import java.io.IOException
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.Objects
import java.util.TreeMap

object HttpUtil {
    private val MAX_LOG_BODY_CHARS = Integer.getInteger("uiptv.http.log.max.body.chars", 4000)
    private val SENSITIVE_HEADERS = listOf("authorization", "cookie", "set-cookie", "proxy-authorization")
    const val STATUS_OK = 200
    const val STATUS_NOT_ACCEPTABLE = 406
    private val DEFAULT_TIMEOUT_SECONDS = Integer.getInteger("uiptv.http.timeout.seconds", 30)
    private val CONNECT_TIMEOUT_SECONDS = Integer.getInteger("uiptv.http.connect.timeout.seconds", DEFAULT_TIMEOUT_SECONDS)
    private val CONNECTION_REQUEST_TIMEOUT_SECONDS = Integer.getInteger("uiptv.http.connection.request.timeout.seconds", DEFAULT_TIMEOUT_SECONDS)
    private val RESPONSE_TIMEOUT_SECONDS = Integer.getInteger("uiptv.http.response.timeout.seconds", DEFAULT_TIMEOUT_SECONDS)
    private val MAX_REDIRECTS = Integer.getInteger("uiptv.http.max.redirects", 5)
    @JvmStatic
    @Throws(IOException::class)
    fun sendRequest(url: String, headers: Map<String, String>?, method: String): HttpResult =
        sendRequest(url, headers, method, null)

    @JvmStatic
    @Throws(IOException::class)
    fun sendRequest(url: String, headers: Map<String, String>?, method: String, body: String?): HttpResult =
        sendRequest(url, headers, method, body, RequestOptions.defaults())

    @JvmStatic
    @Throws(IOException::class)
    fun sendRequest(url: String, headers: Map<String, String>?, method: String, body: String?, options: RequestOptions): HttpResult {
        val requestUrl = url
        val requestMethod = method
        try {
            return runBlocking {
                val client = HttpClientFactory.shared(options.followRedirects)
                val response = client.request(toSafeUri(requestUrl).toString()) {
                    this.method = toHttpMethod(requestMethod)
                    headers?.forEach { (key, value) -> this.headers.append(key, value) }
                    if (body != null) {
                        if (headers == null || headers.keys.none { it.equals("Content-Type", true) }) {
                            this.headers.append("Content-Type", "application/x-www-form-urlencoded")
                        }
                        setBody(body)
                    }
                }
                val responseBody = if (options.readBody) readResponseBodyText(response) else ""
                HttpResult(
                    safeMethod(requestMethod),
                    response.call.request.url.toString(),
                    response.status.value,
                    responseBody,
                    headersToMap(headers),
                    headersToMap(response.headers)
                )
            }
        } catch (ex: Exception) {
            if (ex is IOException) {
                throw ex
            }
            throw IOException("Unable to execute HTTP request", ex)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun openStream(url: String, headers: Map<String, String>?, method: String, body: String?, options: RequestOptions): StreamResult {
        val requestUrl = url
        val requestMethod = method
        try {
            return runBlocking {
                val client = HttpClientFactory.shared(options.followRedirects)
                val response = client.request(toSafeUri(requestUrl).toString()) {
                    this.method = toHttpMethod(requestMethod)
                    headers?.forEach { (key, value) -> this.headers.append(key, value) }
                    if (body != null) {
                        if (headers == null || headers.keys.none { it.equals("Content-Type", true) }) {
                            this.headers.append("Content-Type", "application/x-www-form-urlencoded")
                        }
                        setBody(body)
                    }
                }
                val bodyBytes = response.body<ByteArray>()
                StreamResult(
                    safeMethod(requestMethod),
                    response.call.request.url.toString(),
                    response.status.value,
                    headersToMap(headers),
                    headersToMap(response.headers),
                    ByteArrayInputStream(bodyBytes),
                    AutoCloseable { }
                )
            }
        } catch (ex: Exception) {
            if (ex is IOException) {
                throw ex
            }
            throw IOException("Unable to open HTTP stream", ex)
        }
    }

    private suspend fun readResponseBodyText(response: HttpResponse): String {
        val bytes = response.bodyAsBytes()
        val charset = response.headers["Content-Type"]
            ?.let(::extractCharsetFromContentType)
            ?: StandardCharsets.UTF_8
        return bytes.toString(charset)
    }

    private fun extractCharsetFromContentType(contentType: String): java.nio.charset.Charset? {
        return contentType
            .split(';')
            .asSequence()
            .drop(1)
            .map { it.trim() }
            .firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                runCatching { java.nio.charset.Charset.forName(it) }.getOrNull()
            }
    }

    @JvmStatic
    fun formatHttpLog(requestUrl: String?, response: HttpResult?, requestParams: Map<String, String>?): String {
        if (response == null) {
            return "HTTP request log unavailable: response was null"
        }
        val out = StringBuilder(1024)
        out.append("HTTP ")
            .append(nonBlank(response.requestMethod, "GET"))
            .append(' ')
            .append(nonBlank(response.requestUri, nonBlank(requestUrl, "<unknown>")))
            .append(System.lineSeparator())
        out.append("Status: ").append(response.statusCode).append(System.lineSeparator())
        if (!requestParams.isNullOrEmpty()) {
            appendSection(out, "Request Params", formatParams(requestParams))
        }
        appendSection(out, "Request Headers", formatHeaders(response.requestHeaders))
        appendSection(out, "Response Headers", formatHeaders(response.responseHeaders))
        appendSection(out, "Response Body", abbreviateBody(response.body))
        return out.toString().trim()
    }

    private fun appendSection(out: StringBuilder, title: String, content: String) {
        out.append(System.lineSeparator())
            .append(title)
            .append(':')
            .append(System.lineSeparator())
            .append(indent(nonBlank(content, "<none>")))
            .append(System.lineSeparator())
    }

    private fun formatParams(params: Map<String, String>): String =
        params.entries.sortedBy { it.key.lowercase() }.joinToString(System.lineSeparator()) { entry -> entry.key + "=" + quote(entry.value) }

    private fun formatHeaders(headers: Map<String, List<String>>?): String {
        if (headers.isNullOrEmpty()) {
            return "<none>"
        }
        val sorted = TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER)
        sorted.putAll(headers)
        return sorted.entries.joinToString(System.lineSeparator()) { entry ->
            entry.key + ": " + formatHeaderValues(entry.key, entry.value)
        }
    }

    private fun formatHeaderValues(headerName: String?, values: List<String>?): String {
        if (values.isNullOrEmpty()) {
            return ""
        }
        if (isSensitiveHeader(headerName)) {
            return "<redacted>"
        }
        return values.filter(Objects::nonNull).joinToString(", ") { quote(it) }
    }

    private fun isSensitiveHeader(headerName: String?): Boolean =
        headerName != null && SENSITIVE_HEADERS.contains(headerName.lowercase(Locale.ROOT))

    private fun abbreviateBody(body: String?): String {
        if (body.isNullOrBlank()) {
            return "<empty>"
        }
        val normalized = body.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (looksBinary(normalized)) {
            return "<binary ${normalized.toByteArray(StandardCharsets.UTF_8).size} bytes>"
        }
        if (normalized.length <= MAX_LOG_BODY_CHARS) {
            return normalized
        }
        return normalized.substring(0, MAX_LOG_BODY_CHARS) +
            System.lineSeparator() +
            "... [truncated ${normalized.length - MAX_LOG_BODY_CHARS} chars]"
    }

    private fun looksBinary(value: String): Boolean =
        value.any { Character.isISOControl(it) && !Character.isWhitespace(it) }

    private fun indent(value: String): String =
        value.lines().joinToString(System.lineSeparator()) { line -> "  $line" }

    private fun quote(value: String?): String = if (value == null) "\"\"" else "\"$value\""
    private fun nonBlank(value: String?, fallback: String): String = if (value.isNullOrBlank()) fallback else value

    @JvmStatic
    private fun toSafeUri(url: String?): URI {
        val normalized = url?.trim().orEmpty()
        if (normalized.isEmpty()) {
            return URI.create("http://localhost/empty-url-fallback")
        }
        try {
            return URI.create(normalized)
        } catch (original: IllegalArgumentException) {
            try {
                return UiptUtils.parseUrlLikeUri(normalized)
            } catch (e: Exception) {
                AppLog.addErrorLog(HttpUtil::class.java, "Failed to create URI from URL: $normalized. Error: ${e.message}")
                throw original
            }
        }
    }

    @JvmStatic
    private fun safeMethod(method: String?): String =
        if (method.isNullOrBlank()) "GET" else method.trim().uppercase()

    private fun toHttpMethod(method: String?): HttpMethod =
        HttpMethod.parse(safeMethod(method))

    @JvmStatic
    private fun getFinalUri(request: HttpUriRequestBase, context: HttpClientContext): String {
        return try {
            val redirects = context.redirectLocations
            if (redirects != null) {
                val redirectCount = redirects.size()
                if (redirectCount > 0 && redirectCount <= MAX_REDIRECTS + 1) {
                    val finalRedirect = redirects[redirectCount - 1]
                    return finalRedirect?.toString() ?: request.requestUri
                }
                if (redirectCount > MAX_REDIRECTS + 1) {
                    return request.requestUri
                }
            }
            request.uri?.toString().orEmpty()
        } catch (e: Exception) {
            AppLog.addWarningLog(HttpUtil::class.java, "Failed to get final URI: ${e.message}")
            request.requestUri
        }
    }

    private fun headersToMap(headers: Headers): Map<String, List<String>> {
        val headerMap = LinkedHashMap<String, MutableList<String>>()
        headers.forEach { key, values -> headerMap.computeIfAbsent(key) { ArrayList() }.addAll(values) }
        return headerMap
    }

    private fun headersToMap(headers: Map<String, String>?): Map<String, List<String>> {
        val headerMap = LinkedHashMap<String, MutableList<String>>()
        headers?.forEach { (key, value) -> headerMap.computeIfAbsent(key) { ArrayList() }.add(value) }
        return headerMap
    }

    data class HttpResult(
        val requestMethod: String,
        val requestUri: String,
        val statusCode: Int,
        val body: String,
        val requestHeaders: Map<String, List<String>>,
        val responseHeaders: Map<String, List<String>>
    ) {
        fun requestMethod(): String = requestMethod

        fun requestUri(): String = requestUri

        fun statusCode(): Int = statusCode

        fun body(): String = body

        fun requestHeaders(): Map<String, List<String>> = requestHeaders

        fun responseHeaders(): Map<String, List<String>> = responseHeaders

        constructor(
            statusCode: Int,
            body: String,
            requestHeaders: Map<String, List<String>>,
            responseHeaders: Map<String, List<String>>
        ) : this("", "", statusCode, body, requestHeaders, responseHeaders)
    }

    class RequestOptions(
        val followRedirects: Boolean,
        val readBody: Boolean,
        val connectTimeoutSeconds: Int? = null,
        val connectionRequestTimeoutSeconds: Int? = null,
        val responseTimeoutSeconds: Int? = null
    ) {
        fun followRedirects(): Boolean = followRedirects

        fun readBody(): Boolean = readBody

        fun connectTimeoutSeconds(): Int? = connectTimeoutSeconds

        fun connectionRequestTimeoutSeconds(): Int? = connectionRequestTimeoutSeconds

        fun responseTimeoutSeconds(): Int? = responseTimeoutSeconds

        companion object {
            @JvmStatic
            fun defaults(): RequestOptions = RequestOptions(true, true)
        }
    }

    class StreamResult(
        val requestMethod: String,
        val requestUri: String,
        val statusCode: Int,
        val requestHeaders: Map<String, List<String>>,
        val responseHeaders: Map<String, List<String>>,
        val bodyStream: InputStream,
        private val closeable: AutoCloseable
    ) : AutoCloseable {
        @Throws(IOException::class)
        override fun close() {
            closeable.close()
        }
    }
}
