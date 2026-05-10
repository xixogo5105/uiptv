package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.model.Channel
import com.uiptv.service.AccountService
import com.uiptv.service.SeriesWatchStateService
import com.uiptv.service.SeriesWatchingNowSnapshotService
import com.uiptv.util.StringUtils.isBlank
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets

class HttpWatchingNowSeriesActionServer : HttpHandler {
    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        when {
            ex.requestMethod.equals("POST", ignoreCase = true) -> upsert(ex)
            ex.requestMethod.equals("DELETE", ignoreCase = true) -> remove(ex)
            else -> {
                ex.responseHeaders.set("Allow", "POST,DELETE")
                ex.sendResponseHeaders(405, -1)
            }
        }
    }

    private fun upsert(ex: HttpExchange) {
        val body = readBodyJson(ex)
        val accountId = opt(body, "accountId")
        val categoryId = opt(body, "categoryId")
        val seriesId = opt(body, "seriesId")
        val episodeId = opt(body, "episodeId")
        val episodeName = opt(body, "episodeName")
        val season = opt(body, "season")
        val episodeNum = opt(body, "episodeNum")
        val categoryDbId = opt(body, "categoryDbId")
        val seriesTitle = opt(body, "seriesTitle")
        val seriesPoster = opt(body, "seriesPoster")
        if (isBlank(accountId) || isBlank(seriesId) || isBlank(episodeId)) {
            writeJson(ex, 400, """{"status":"error","message":"accountId, seriesId, episodeId are required"}""")
            return
        }
        val account = AccountService.getInstance().getById(accountId)
        if (account == null) {
            writeJson(ex, 404, """{"status":"error","message":"account not found"}""")
            return
        }
        SeriesWatchStateService.getInstance().markSeriesEpisodeManual(account, categoryId, seriesId, episodeId, episodeName, season, episodeNum)
        SeriesWatchingNowSnapshotService.getInstance().saveChannels(
            account,
            categoryId,
            seriesId,
            categoryDbId,
            seriesTitle,
            seriesPoster,
            parseChannels(body.optJSONArray("episodes"))
        )
        writeJson(ex, 200, """{"status":"ok"}""")
    }

    private fun remove(ex: HttpExchange) {
        val body = readBodyJson(ex)
        val accountId = opt(body, "accountId")
        val categoryId = opt(body, "categoryId")
        val seriesId = opt(body, "seriesId")
        if (isBlank(accountId) || isBlank(seriesId)) {
            writeJson(ex, 400, """{"status":"error","message":"accountId and seriesId are required"}""")
            return
        }
        SeriesWatchStateService.getInstance().clearSeriesLastWatched(accountId, categoryId, seriesId)
        writeJson(ex, 200, """{"status":"ok"}""")
    }

    private fun readBodyJson(ex: HttpExchange): JSONObject =
        ex.requestBody.use { input ->
            val body = String(input.readAllBytes(), StandardCharsets.UTF_8)
            if (body.isBlank()) JSONObject() else JSONObject(body)
        }

    private fun opt(body: JSONObject?, key: String): String =
        if (body == null || !body.has(key) || body.isNull(key)) "" else body.opt(key).toString().trim()

    private fun parseChannels(payload: JSONArray?): List<Channel> {
        if (payload == null || payload.isEmpty) {
            return emptyList()
        }
        val channels = mutableListOf<Channel>()
        for (i in 0 until payload.length()) {
            val value = payload.opt(i) ?: continue
            val channel = Channel.fromJson(value.toString())
            if (channel != null) {
                channels += channel
            }
        }
        return channels
    }

    private fun writeJson(ex: HttpExchange, status: Int, body: String) {
        val responseBytes = body.toByteArray(StandardCharsets.UTF_8)
        ex.responseHeaders.add("Access-Control-Allow-Origin", "*")
        ex.responseHeaders.add("Access-Control-Allow-Methods", "POST,DELETE,OPTIONS")
        ex.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type,*")
        ex.responseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(status, responseBytes.size.toLong())
        ex.responseBody.use { it.write(responseBytes) }
    }
}
