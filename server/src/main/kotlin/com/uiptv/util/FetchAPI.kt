package com.uiptv.util

import com.uiptv.model.Account
import com.uiptv.util.json.optBoolean
import com.uiptv.util.json.optInt
import com.uiptv.util.json.optString
import kotlinx.serialization.json.JsonObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object FetchAPI {
    private const val PORTAL_PHP = "portal.php"

    @JvmStatic
    fun fetch(params: Map<String, String>, account: Account): String =
        fetch(params, account, HttpUtil.RequestOptions.defaults())

    @JvmStatic
    fun fetch(params: Map<String, String>, account: Account, options: HttpUtil.RequestOptions): String {
        try {
            val baseUrl = resolveBaseUrl(account)
            if (StringUtils.isBlank(baseUrl)) {
                throw IllegalArgumentException("Target host is not specified")
            }
            val payload = if (params.isEmpty()) "" else mapToString(params)
            val httpMethod = account.httpMethod
            val isPost = httpMethod.equals("POST", ignoreCase = true)

            var requestUrl = baseUrl
            if (!isPost && payload.isNotEmpty()) {
                requestUrl += "?$payload"
            }

            val headers = headers(account, isPost)
            val response = HttpUtil.sendRequest(requestUrl, headers, httpMethod, if (isPost) payload else null, options)
            LogUtil.httpLog(requestUrl, response, params)
            if (response.statusCode == HttpUtil.STATUS_OK) {
                return response.body
            }
        } catch (ex: Exception) {
            AppLog.addWarningLog(FetchAPI::class.java, "Network Error: ${ex.message}")
        }
        return StringUtils.EMPTY
    }

    private fun resolveBaseUrl(account: Account?): String {
        if (account == null) {
            return ""
        }
        val serverPortal = normalizeUrlCandidate(account.serverPortalUrl, true)
        return if (StringUtils.isNotBlank(serverPortal)) serverPortal else normalizeUrlCandidate(account.url, true)
    }

    private fun normalizeUrlCandidate(value: String?, appendPortalPhpWhenMissing: Boolean): String {
        if (StringUtils.isBlank(value)) {
            return ""
        }
        var candidate = value.orEmpty().trim()
        if (!candidate.contains("://")) {
            candidate = "http://$candidate"
        }
        return try {
            val uri = java.net.URI.create(candidate)
            if (StringUtils.isBlank(uri.host)) {
                return ""
            }
            val path = uri.path?.lowercase().orEmpty()
            if (appendPortalPhpWhenMissing && !path.endsWith(PORTAL_PHP) && !path.endsWith("load.php")) {
                if (candidate.endsWith("/")) "$candidate$PORTAL_PHP" else "$candidate/$PORTAL_PHP"
            } else {
                candidate
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun headers(account: Account, isPost: Boolean): Map<String, String> {
        val headers = linkedMapOf<String, String>()
        headers["User-Agent"] = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
        headers["X-User-Agent"] = "Model: MAG250; Link: WiFi"
        headers["Referer"] = if (StringUtils.isBlank(account.serverPortalUrl)) account.url.orEmpty() else account.serverPortalUrl.orEmpty()
        headers["Accept"] = "*/*"
        headers["Pragma"] = "no-cache"
        if (account.isConnected()) headers["Authorization"] = "Bearer ${account.token}"
        val timezone = account.timezone
        headers["Cookie"] = "mac=${account.macAddress}; stb_lang=en; timezone=$timezone;"
        if (isPost) {
            headers["Content-Type"] = "application/x-www-form-urlencoded"
        }
        return headers
    }

    private fun mapToString(parameters: Map<String, String>): String =
        parameters.entries.joinToString("&") { entry ->
            entry.key + "=" + URLEncoder.encode(entry.value, StandardCharsets.UTF_8)
        }

    @JvmStatic
    fun nullSafeBoolean(jsonCategory: JsonObject, key: String): Boolean =
        jsonCategory.optBoolean(key)

    @JvmStatic
    fun nullSafeInteger(jsonCategory: JsonObject, key: String): Int =
        jsonCategory.optInt(key, -1)

    @JvmStatic
    fun nullSafeString(jsonCategory: JsonObject, key: String): String =
        jsonCategory.optString(key)

    enum class ServerType(private val loader: String) {
        PORTAL(PORTAL_PHP),
        LOAD("load.php?");

        fun getLoader(): String = loader
    }
}
