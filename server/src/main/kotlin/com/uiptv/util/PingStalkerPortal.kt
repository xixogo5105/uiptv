package com.uiptv.util

import com.uiptv.model.Account
import com.uiptv.util.json.KJsonObject
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.regex.Pattern
import javax.net.ssl.SSLException

object PingStalkerPortal {
    private const val DOCUMENT_URL_REPLACE_PREFIX = "document.URL.replace(pattern,"
    const val SPLIT_FUNCTION_SERVER_PARAMS: String = "\\Qthis.get_server_params=function()\\E"
    private val PROBE_PATHS = arrayOf(
        "/c/portal.php",
        "/stalker_portal/c/portal.php",
        "/portal.php",
        "/server/load.php",
        "/stalker_portal/server/load.php",
        "/server/portal.php",
        "/mag/c/portal.php"
    )

    private enum class ProbeStatus {
        SUCCESS,
        NO_MATCH,
        NETWORK_ERROR
    }

    @JvmStatic
    fun ping(account: Account): String {
        val url = account.url.orEmpty()
        val timezone = account.timezone
        val httpMethod = account.httpMethod
        AppLog.addInfoLog(PingStalkerPortal::class.java, "Attempting to download xpcom.common.js from portal base URL: $url")
        try {
            val pingUrl = if (!url.endsWith("/")) "$url/xpcom.common.js" else url + "xpcom.common.js"
            val headers = hashMapOf("User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3")
            val response = HttpUtil.sendRequest(pingUrl, headers, httpMethod)
            if (response.statusCode == HttpUtil.STATUS_OK) {
                val discoveredApi = parsePortalApiServer((response.body + " ").replace(" ", ""), url)
                val defaultApi = getDefaultApiEndpoint(url)
                if (StringUtils.isNotBlank(discoveredApi) && !discoveredApi.equals(defaultApi, true)) {
                    AppLog.addInfoLog(PingStalkerPortal::class.java, "Successfully parsed xpcom.common.js and resolved a specific API endpoint: $discoveredApi")
                    return discoveredApi
                }
                AppLog.addWarningLog(PingStalkerPortal::class.java, "xpcom.common.js was accessible, but did not yield a specific API endpoint. Trying known API paths next.")
            } else {
                AppLog.addWarningLog(PingStalkerPortal::class.java, "Unable to access xpcom.common.js (HTTP ${response.statusCode}). Trying known API paths next.")
            }
        } catch (ex: Exception) {
            if (isNetworkFailure(ex)) {
                AppLog.addWarningLog(PingStalkerPortal::class.java, "Network/connection issue while requesting xpcom.common.js: ${rootCauseMessage(ex)}. Skipping endpoint probing.")
                return getDefaultApiEndpoint(url)
            }
            AppLog.addErrorLog(PingStalkerPortal::class.java, "Error while requesting xpcom.common.js: ${ex.message}. Trying known API paths next.")
        }

        AppLog.addInfoLog(PingStalkerPortal::class.java, "Probing known Stalker API endpoints.")
        val verifiedApi = probeKnownPaths(url, account.macAddress, timezone, httpMethod)
        if (verifiedApi != null) {
            AppLog.addInfoLog(PingStalkerPortal::class.java, "Successfully discovered working API endpoint via direct handshake probing: $verifiedApi")
            return verifiedApi
        }

        AppLog.addWarningLog(PingStalkerPortal::class.java, "No working endpoint discovered from xpcom.common.js or probing. Falling back to default API endpoint.")
        return getDefaultApiEndpoint(url)
    }

    private fun probeKnownPaths(baseUrl: String, macAddress: String?, timezone: String, httpMethod: String): String? {
        var cleanBase = ensureAbsoluteUrl(baseUrl)
        if (cleanBase.endsWith("/")) {
            cleanBase = cleanBase.substring(0, cleanBase.length - 1)
        }
        for (path in PROBE_PATHS) {
            val targetUrl = cleanBase + path
            when (checkHandshake(targetUrl, macAddress, timezone, httpMethod)) {
                ProbeStatus.SUCCESS -> return targetUrl
                ProbeStatus.NETWORK_ERROR -> {
                    AppLog.addWarningLog(PingStalkerPortal::class.java, "Stopping endpoint probing after connection-level failure at: $targetUrl")
                    return null
                }
                ProbeStatus.NO_MATCH -> {}
            }
        }
        return null
    }

    private fun checkHandshake(apiUrl: String, macAddress: String?, timezone: String, httpMethod: String): ProbeStatus {
        try {
            AppLog.addInfoLog(PingStalkerPortal::class.java, "Checking handshake for : $apiUrl")
            val headers = HashMap<String, String>()
            headers["User-Agent"] = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
            headers["X-User-Agent"] = "Model: MAG250; Link: WiFi"
            headers["Referer"] = apiUrl
            headers["Cookie"] = "mac=$macAddress; stb_lang=en; timezone=$timezone"
            val handshakeQuery = "?type=stb&action=handshake&JsHttpRequest=${System.currentTimeMillis()}-xml"
            val response = HttpUtil.sendRequest(apiUrl + handshakeQuery, headers, httpMethod)
            if (response.statusCode == 200 && hasHandshakeToken(response.body)) {
                return ProbeStatus.SUCCESS
            }
        } catch (e: Exception) {
            if (isNetworkFailure(e)) {
                AppLog.addWarningLog(PingStalkerPortal::class.java, "Network/connection issue while checking $apiUrl: ${rootCauseMessage(e)}")
                return ProbeStatus.NETWORK_ERROR
            }
        }
        return ProbeStatus.NO_MATCH
    }

    @JvmStatic
    private fun isNetworkFailure(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            if (current is UnknownHostException || current is ConnectException || current is NoRouteToHostException ||
                current is SocketTimeoutException || current is SSLException
            ) {
                return true
            }
            val className = current.javaClass.name
            if (className.contains("ConnectTimeoutException") || className.contains("ConnectionRequestTimeoutException")) {
                return true
            }
            current = current.cause
        }
        return false
    }

    @JvmStatic
    private fun rootCauseMessage(throwable: Throwable): String {
        var current = throwable
        while (current.cause != null) {
            current = current.cause!!
        }
        return current.javaClass.simpleName + if (StringUtils.isNotBlank(current.message)) ": ${current.message}" else ""
    }

    @JvmStatic
    fun parsePortalApiServer(jsFileContents: String, url: String): String {
        var portalApiServer: String? = null
        try {
            val serverUri = prepareServerUrl(jsFileContents, url)
            if (StringUtils.isNotBlank(serverUri) && isValidUrl(serverUri)) {
                return serverUri
            }
            val portal = "portal.php"
            val load = "load.php"
            if (jsFileContents.lowercase().contains(portal)) {
                val i = jsFileContents.lowercase().indexOf(portal)
                val delimiter = jsFileContents.lowercase().split(portal)[1].substring(0, 1)
                val startingIndex = jsFileContents.lowercase().split(portal)[0].lastIndexOf(delimiter + "/")
                val extractedPath = jsFileContents.substring(startingIndex + 1, i + portal.length)
                portalApiServer = combineUrlWithPath(url, extractedPath)
                if (!isValidUrl(portalApiServer)) {
                    portalApiServer = null
                }
            } else if (jsFileContents.lowercase().contains(load)) {
                val i = jsFileContents.lowercase().indexOf(load)
                val delimiter = jsFileContents.lowercase().split(load)[1].substring(0, 1)
                val startingIndex = jsFileContents.lowercase().split(load)[0].lastIndexOf(delimiter + "/")
                val extractedPath = jsFileContents.substring(startingIndex + 1, i + load.length)
                portalApiServer = combineUrlWithPath(url, extractedPath)
                if (!isValidUrl(portalApiServer)) {
                    portalApiServer = null
                }
            }
        } catch (e: Exception) {
            AppLog.addErrorLog(PingStalkerPortal::class.java, "pingParseError: ${e.message}")
        }
        if (StringUtils.isBlank(portalApiServer)) {
            portalApiServer = getDefaultApiEndpoint(url)
        }
        return portalApiServer.orEmpty()
    }

    @JvmStatic
    private fun getDefaultApiEndpoint(url: String): String {
        val baseUrl = ensureAbsoluteUrl(url)
        val lowered = baseUrl.lowercase()
        if (lowered.contains("/c/") && !lowered.endsWith(".php/") && !lowered.endsWith(".php")) {
            val cIndex = lowered.indexOf("/c/")
            val root = baseUrl.substring(0, cIndex + 1)
            return root + "server/load.php"
        }
        return baseUrl + FetchAPI.ServerType.PORTAL.getLoader()
    }

    @JvmStatic
    private fun hasHandshakeToken(body: String?): Boolean {
        if (StringUtils.isBlank(body)) return false
        return try {
            val root = KJsonObject(body.orEmpty())
            val js = root.optJSONObject("js")
            if (js != null && StringUtils.isNotBlank(js.optString("token"))) {
                true
            } else {
                StringUtils.isNotBlank(root.optString("token"))
            }
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    private fun prepareServerUrl(jsFileContents: String, uri: String): String {
        return try {
            val ajaxLoader = extractActiveAssignment(jsFileContents, "this.ajax_loader=")
            if (StringUtils.isBlank(ajaxLoader)) {
                return ""
            }
            val regex = getPattern(jsFileContents).replace("^/+".toRegex(), "").replace("/+$".toRegex(), "")
            val result = ajaxLoader
                .replace("'", "")
                .replace("+", "")
                .replace(" ", "")
                .replace(";", "")
                .replace("this.portal_protocol", resolvePortalValue(jsFileContents, uri, regex, ::getPortalProtocolParamNumber, "http"))
                .replace("this.portal_ip", resolvePortalValue(jsFileContents, uri, regex, ::getPortalIP, ""))
                .replace("this.portal_port", resolvePortalPortValue(jsFileContents, uri, regex))
                .replace("this.portal_path", resolvePortalValue(jsFileContents, uri, regex, ::getPortalPath, ""))
            ensureAbsoluteServerUrl(result, uri)
        } catch (_: Exception) {
            ""
        }
    }

    private fun getPattern(jsFileContents: String): String {
        val pattern = "varpattern="
        val endsWithString = ";"
        return try {
            val functionSplitArray = jsFileContents.split(SPLIT_FUNCTION_SERVER_PARAMS.toRegex())
            if (functionSplitArray.size > 1) {
                val patternArray = functionSplitArray[1].split(pattern)
                if (patternArray.size > 1 && patternArray[1].contains(endsWithString)) {
                    patternArray[1].substring(0, patternArray[1].indexOf(endsWithString))
                } else ""
            } else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun getPortalProtocolParamNumber(jsFileContents: String, url: String): String? {
        return try {
            val functionSplitArray = jsFileContents.split(SPLIT_FUNCTION_SERVER_PARAMS.toRegex())
            if (functionSplitArray.size > 1) {
                val patternArray = functionSplitArray[1].split("this.portal_protocol=")
                if (patternArray.size > 1 && patternArray[1].contains(";")) {
                    patternArray[1].substring(0, patternArray[1].indexOf(";"))
                        .replace(DOCUMENT_URL_REPLACE_PREFIX, "").replace("\"", "").replace(")", "")
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getPortalIP(jsFileContents: String, url: String): String? {
        return try {
            val functionSplitArray = jsFileContents.split(SPLIT_FUNCTION_SERVER_PARAMS.toRegex())
            if (functionSplitArray.size > 1) {
                val patternArray = functionSplitArray[1].split("this.portal_ip=")
                if (patternArray.size > 1 && patternArray[1].contains(";")) {
                    patternArray[1].substring(0, patternArray[1].indexOf(";"))
                        .replace(DOCUMENT_URL_REPLACE_PREFIX, "").replace("\"", "").replace(")", "")
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getPortalPath(jsFileContents: String, url: String): String? {
        return try {
            val functionSplitArray = jsFileContents.split(SPLIT_FUNCTION_SERVER_PARAMS.toRegex())
            if (functionSplitArray.size > 1) {
                val patternArray = functionSplitArray[1].split("this.portal_path=")
                if (patternArray.size > 1 && patternArray[1].contains(";")) {
                    patternArray[1].substring(0, patternArray[1].indexOf(";"))
                        .replace(DOCUMENT_URL_REPLACE_PREFIX, "").replace("\"", "").replace(")", "")
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getPortalPort(jsFileContents: String): String? =
        extractPortalParam(jsFileContents, "this.portal_port=", DOCUMENT_URL_REPLACE_PREFIX)

    @JvmStatic
    private fun extractActiveAssignment(jsFileContents: String, pattern: String): String {
        val functionSplitArray = jsFileContents.split(SPLIT_FUNCTION_SERVER_PARAMS.toRegex())
        if (functionSplitArray.size <= 1) {
            return ""
        }
        var patternArray = functionSplitArray[1].split(pattern).toTypedArray()
        while (patternArray.size > 1 && isCommentPrefix(patternArray[0])) {
            patternArray = patternArray[1].split(pattern).toTypedArray()
        }
        return if (patternArray.size > 1 && patternArray[1].contains(";")) {
            patternArray[1].substring(0, patternArray[1].indexOf(";"))
        } else {
            ""
        }
    }

    private fun isCommentPrefix(value: String): Boolean =
        value.endsWith("//") || value.endsWith("/*") || value.endsWith("*")

    @JvmStatic
    private fun ensureAbsoluteServerUrl(result: String, uri: String): String {
        if (StringUtils.isBlank(result) || result.contains("://")) {
            return result
        }
        val baseUrl = ensureAbsoluteUrl(uri)
        return if (result.startsWith("/")) baseUrl + result.substring(1) else baseUrl + result
    }

    private fun resolvePortalValue(
        jsFileContents: String,
        uri: String,
        regex: String,
        extractor: (String, String) -> String?,
        fallback: String
    ): String {
        val value = extractor(jsFileContents, uri)
        return value?.let { uri.replaceFirst(regex.toRegex(), it) } ?: fallback
    }

    private fun resolvePortalPortValue(jsFileContents: String, uri: String, regex: String): String {
        val port = getPortalPort(jsFileContents)
        return if (port != null && !StringUtils.isBlank(port)) uri.replaceFirst(regex.toRegex(), port) else ""
    }

    @JvmStatic
    private fun extractPortalParam(jsFileContents: String, pattern: String, replacePrefix: String): String? {
        return try {
            val assignment = extractActiveAssignment(jsFileContents, pattern)
            if (StringUtils.isBlank(assignment)) {
                null
            } else {
                assignment.replace(replacePrefix, "").replace("\"", "").replace(")", "")
            }
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    private fun ensureAbsoluteUrl(url: String?): String {
        if (StringUtils.isBlank(url)) return "http://"
        val normalized = url.orEmpty()
        return if (normalized.contains("://")) {
            if (normalized.endsWith("/")) normalized else "$normalized/"
        } else {
            "http://$normalized"
        }
    }

    @JvmStatic
    private fun isValidUrl(url: String?): Boolean {
        if (StringUtils.isBlank(url)) return false
        val normalized = url.orEmpty()
        return try {
            URI.create(normalized)
            normalized.contains("://")
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    private fun combineUrlWithPath(baseUrl: String, path: String?): String {
        if (StringUtils.isBlank(path)) return baseUrl
        var base = baseUrl
        if (!base.endsWith("/")) {
            base += "/"
        }
        var pathToAppend = path!!
        if (pathToAppend.startsWith("/")) {
            pathToAppend = pathToAppend.substring(1)
        }
        return base + pathToAppend
    }
}
