package com.uiptv.util

import com.sun.net.httpserver.HttpExchange
import com.uiptv.api.JsonCompliant
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object ServerUtils {
    const val CONTENT_TYPE_HTML = "text/html"
    const val CONTENT_TYPE_JAVASCRIPT = "text/javascript"
    const val CONTENT_TYPE_CSS = "text/css"
    const val CONTENT_TYPE_TEXT = "text/plain"
    const val CONTENT_TYPE_TS = "video/mp2t"
    const val CONTENT_TYPE_JSON = "application/json"
    const val CONTENT_TYPE_M3U8 = "vnd.apple.mpegurl"
    private val DOWNLOADABLE = listOf(CONTENT_TYPE_TS)

    private fun queryToMap(query: String?): Map<String, String> {
        if (query == null) {
            return emptyMap()
        }
        val result = HashMap<String, String>()
        for (param in query.split("&")) {
            if (param.isEmpty()) continue
            val entry = param.split("=", limit = 2)
            val rawKey = entry[0]
            val rawValue = if (entry.size > 1) entry[1] else ""
            val key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8)
            val value = URLDecoder.decode(rawValue, StandardCharsets.UTF_8)
            result[key] = value
        }
        return result
    }

    @JvmStatic
    fun objectToJson(readCase: List<JsonCompliant?>?): String {
        if (!readCase.isNullOrEmpty()) {
            val jsonArrayString = StringBuilder("[")
            readCase.filterNotNull().forEach { channel -> jsonArrayString.append(channel.toJson()).append(",") }
            if (jsonArrayString.length == 1) {
                return "[]"
            }
            jsonArrayString.deleteCharAt(jsonArrayString.length - 1)
            return if (jsonArrayString.isNotEmpty()) jsonArrayString.append("]").toString() else "[]"
        }
        return "[]"
    }

    @JvmStatic
    fun getParam(httpExchange: HttpExchange, key: String): String? =
        queryToMap(httpExchange.requestURI.rawQuery)[key]

    @JvmStatic
    @Throws(IOException::class)
    fun generateHtmlResponse(httpExchange: HttpExchange, response: String) {
        generateResponse(httpExchange, response, CONTENT_TYPE_HTML, null)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun generateJsonResponse(httpExchange: HttpExchange, response: String) {
        generateResponse(httpExchange, response, CONTENT_TYPE_JSON, null)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeJsonResponse(httpExchange: HttpExchange, statusCode: Int, response: String?) {
        writeResponse(httpExchange, statusCode, response?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0), "$CONTENT_TYPE_JSON; charset=UTF-8")
    }

    @JvmStatic
    @Throws(IOException::class)
    fun generateJavascriptResponse(httpExchange: HttpExchange, response: String, fileName: String?) {
        generateResponse(httpExchange, response, CONTENT_TYPE_JAVASCRIPT, fileName)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun generateCssResponse(httpExchange: HttpExchange, response: String, fileName: String?) {
        generateResponse(httpExchange, response, CONTENT_TYPE_CSS, fileName)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun generateM3u8Response(httpExchange: HttpExchange, response: String, fileName: String?) {
        generateResponse(httpExchange, response, CONTENT_TYPE_M3U8, fileName)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun generateTs8Response(httpExchange: HttpExchange, response: String, fileName: String?) {
        generateResponse(httpExchange, response, CONTENT_TYPE_TS, fileName)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun generateResponseText(httpExchange: HttpExchange, statusCode: Int, response: String?) {
        writeResponse(httpExchange, statusCode, response?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0), "$CONTENT_TYPE_TEXT; charset=UTF-8")
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeBinaryResponse(httpExchange: HttpExchange, statusCode: Int, responseBytes: ByteArray?, contentType: String) {
        writeResponse(httpExchange, statusCode, responseBytes ?: ByteArray(0), contentType)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readRequestBodyText(httpExchange: HttpExchange): String =
        String(readRequestBodyBytes(httpExchange), StandardCharsets.UTF_8)

    @JvmStatic
    @Throws(IOException::class)
    fun readRequestBodyBytes(httpExchange: HttpExchange): ByteArray {
        httpExchange.requestBody.use { inputStream -> return inputStream.readAllBytes() }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun generateResponse(httpExchange: HttpExchange, response: String, contentType: String, fileName: String?) {
        if (httpExchange.requestMethod != "GET") {
            httpExchange.responseHeaders.set("Allow", "GET")
            httpExchange.sendResponseHeaders(405, -1)
            return
        }

        httpExchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        httpExchange.responseHeaders.add("Access-Control-Allow-Methods", "GET")
        httpExchange.responseHeaders.add("Access-Control-Allow-Headers", "*")
        httpExchange.responseHeaders.add("Access-Control-Allow-Credentials", "true")
        httpExchange.responseHeaders.add("Access-Control-Allow-Credentials-Header", "*")
        httpExchange.responseHeaders.add("Access-Control-Allow-Credentials-Header", "*")
        httpExchange.responseHeaders.add("Content-Type", contentType)
        if (StringUtils.isNotBlank(fileName) && DOWNLOADABLE.contains(contentType)) {
            httpExchange.responseHeaders.add("Content-Disposition", "attachment; filename=$fileName")
            httpExchange.responseHeaders.add("Content-Transfer-Encoding", "binary")
        }
        val responseBytes = response.toByteArray(StandardCharsets.UTF_8)
        httpExchange.responseHeaders.add("Content-length", responseBytes.size.toLong().toString())
        httpExchange.sendResponseHeaders(200, responseBytes.size.toLong())

        val outputStream: OutputStream = httpExchange.responseBody
        outputStream.write(responseBytes)
        outputStream.close()
    }

    @Throws(IOException::class)
    private fun writeResponse(httpExchange: HttpExchange, statusCode: Int, responseBytes: ByteArray, contentType: String) {
        httpExchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        httpExchange.responseHeaders.add("Content-Type", contentType)
        httpExchange.sendResponseHeaders(statusCode, responseBytes.size.toLong())
        httpExchange.responseBody.use { os -> os.write(responseBytes) }
    }
}
