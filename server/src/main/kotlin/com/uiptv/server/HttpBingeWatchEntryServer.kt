package com.uiptv.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.service.BingeWatchService
import com.uiptv.util.AppLog
import com.uiptv.util.ServerUtils.generateResponseText
import com.uiptv.util.ServerUtils.getParam
import com.uiptv.util.StringUtils.isBlank
import java.io.IOException

class HttpBingeWatchEntryServer : HttpHandler {
    companion object {
        private const val LOG_PREFIX = "BingeWatch: "
        private const val EMPTY_VALUE = ""
        private const val GET = "GET"
        private const val HEAD = "HEAD"
        private const val ALLOW = "Allow"
        private const val LOCATION = "Location"
        private const val TOKEN_PARAM = "token"
        private const val EPISODE_ID_PARAM = "episodeId"
        private const val TOKEN_LOG = " token="
        private const val EPISODE_ID_LOG = " episodeId="

        private fun defaultString(value: String?): String = value ?: EMPTY_VALUE

        private fun safeLogValue(value: String?): String = AppLog.sanitizeValue(value)
    }

    @Throws(IOException::class)
    override fun handle(exchange: HttpExchange) {
        val method = exchange.requestMethod
        AppLog.addInfoLog(
            HttpBingeWatchEntryServer::class.java,
            LOG_PREFIX + "HTTP entry request method=" + safeLogValue(method) + " uri=" + safeLogValue(exchange.requestURI.toString())
        )
        if (!GET.equals(method, true) && !HEAD.equals(method, true)) {
            exchange.responseHeaders.set(ALLOW, "$GET, $HEAD")
            exchange.sendResponseHeaders(405, -1)
            return
        }

        val token = getParam(exchange, TOKEN_PARAM)
        val episodeId = getParam(exchange, EPISODE_ID_PARAM)
        if (isBlank(token) || isBlank(episodeId)) {
            AppLog.addWarningLog(
                HttpBingeWatchEntryServer::class.java,
                LOG_PREFIX + "HTTP entry missing params" + TOKEN_LOG + safeLogValue(defaultString(token)) +
                    EPISODE_ID_LOG + safeLogValue(defaultString(episodeId))
            )
            exchange.sendResponseHeaders(404, -1)
            return
        }

        try {
            val resolved = BingeWatchService.getInstance().resolveEpisode(token, episodeId)
            if (resolved == null || isBlank(resolved.url)) {
                AppLog.addWarningLog(
                    HttpBingeWatchEntryServer::class.java,
                    LOG_PREFIX + "HTTP entry resolve failed" + TOKEN_LOG + safeLogValue(token) +
                        EPISODE_ID_LOG + safeLogValue(episodeId)
                )
                generateResponseText(exchange, 404, "Binge watch item not found.")
                return
            }
            AppLog.addInfoLog(
                HttpBingeWatchEntryServer::class.java,
                LOG_PREFIX + "HTTP entry redirect" + TOKEN_LOG + safeLogValue(token) +
                        EPISODE_ID_LOG + safeLogValue(episodeId) + " location=" + safeLogValue(resolved.url)
            )
            exchange.responseHeaders.add(LOCATION, resolved.url)
            exchange.sendResponseHeaders(307, -1)
        } catch (ex: Exception) {
            AppLog.addErrorLog(
                HttpBingeWatchEntryServer::class.java,
                LOG_PREFIX + "HTTP entry exception" + TOKEN_LOG + safeLogValue(token) +
                    EPISODE_ID_LOG + safeLogValue(episodeId) + " error=" + safeLogValue(ex.message)
            )
            generateResponseText(exchange, 502, "Unable to resolve binge watch episode: " + ex.message)
        }
    }
}
