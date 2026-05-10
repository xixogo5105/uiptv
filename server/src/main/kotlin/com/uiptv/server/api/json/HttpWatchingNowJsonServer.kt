package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.model.Account
import com.uiptv.model.SeriesWatchState
import com.uiptv.service.WatchingNowSeriesResolver
import com.uiptv.util.ServerUtils.generateJsonResponse
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class HttpWatchingNowJsonServer : HttpHandler {
    private val resolver = WatchingNowSeriesResolver()

    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        generateJsonResponse(ex, toJson(buildRows()))
    }

    private fun toJson(rows: List<PanelRow>): String {
        val payload = JSONArray()
        for (row in rows) {
            payload.put(row.toJson())
        }
        return payload.toString()
    }

    private fun buildRows(): List<PanelRow> {
        val rows = mutableListOf<PanelRow>()
        for (row in resolver.resolveAll()) {
            val state: SeriesWatchState = row.state
            val account: Account = row.account
            rows += PanelRow(
                safe(account.dbId),
                safe(account.accountName),
                safe(account.type.name),
                safe(state.categoryId),
                safe(row.categoryDbId),
                safe(state.seriesId),
                safe(state.episodeId),
                safe(state.episodeName),
                safe(state.season),
                state.episodeNum,
                safe(row.seriesTitle),
                safe(row.seriesPoster),
                state.updatedAt
            )
        }
        return rows.sortedWith(compareByDescending<PanelRow> { it.updatedAt }.thenBy(String.CASE_INSENSITIVE_ORDER) { safe(it.seriesTitle) })
    }

    private fun safe(value: String?): String = value?.trim() ?: ""

    private data class PanelRow(
        private val accountId: String,
        private val accountName: String,
        private val accountType: String,
        private val categoryId: String,
        private val categoryDbId: String,
        private val seriesId: String,
        private val episodeId: String,
        private val episodeName: String,
        private val season: String,
        private val episodeNum: Int,
        val seriesTitle: String,
        private val seriesPoster: String,
        val updatedAt: Long
    ) {
        fun toJson(): JSONObject =
            JSONObject()
                .put("key", "$accountId|$seriesId")
                .put("accountId", accountId)
                .put("accountName", accountName)
                .put("accountType", accountType)
                .put("categoryId", categoryId)
                .put("categoryDbId", categoryDbId)
                .put("seriesId", seriesId)
                .put("episodeId", episodeId)
                .put("episodeName", episodeName)
                .put("season", season)
                .put("episodeNum", episodeNum)
                .put("seriesTitle", seriesTitle)
                .put("seriesPoster", seriesPoster)
                .put("updatedAt", updatedAt)
    }
}
