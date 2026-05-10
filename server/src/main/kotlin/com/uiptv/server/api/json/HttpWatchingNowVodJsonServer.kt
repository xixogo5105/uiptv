package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.model.Account
import com.uiptv.model.Channel
import com.uiptv.model.VodWatchState
import com.uiptv.service.WatchingNowVodResolver
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets

class HttpWatchingNowVodJsonServer : HttpHandler {
    private val resolver = WatchingNowVodResolver()

    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        if (!ex.requestMethod.equals("GET", ignoreCase = true)) {
            ex.responseHeaders.set("Allow", "GET")
            ex.sendResponseHeaders(405, -1)
            return
        }
        val responseBytes = buildPayload().toByteArray(StandardCharsets.UTF_8)
        ex.responseHeaders.add("Access-Control-Allow-Origin", "*")
        ex.responseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(200, responseBytes.size.toLong())
        ex.responseBody.use { it.write(responseBytes) }
    }

    private fun buildPayload(): String {
        val rows = resolver.resolveAll().map(::toVodRow)
            .sortedWith(compareByDescending<VodRow> { it.updatedAt }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.vodName })
        val payload = JSONArray()
        for (row in rows) {
            payload.put(row.toJson())
        }
        return payload.toString()
    }

    private fun toVodRow(row: WatchingNowVodResolver.VodRow): VodRow =
        VodRow(
            row.account,
            row.state,
            row.playbackChannel,
            VodMetadata(
                row.displayTitle,
                row.metadata.logo,
                row.metadata.plot,
                row.metadata.releaseDate,
                row.metadata.rating,
                row.metadata.duration
            )
        )

    private data class VodRow(
        private val accountId: String,
        private val accountName: String,
        private val accountType: String,
        private val categoryId: String,
        private val vodId: String,
        val vodName: String,
        private val vodLogo: String,
        private val plot: String,
        private val releaseDate: String,
        private val rating: String,
        private val duration: String,
        val updatedAt: Long,
        private val playItem: Channel?
    ) {
        constructor(account: Account, state: VodWatchState, playItem: Channel?, metadata: VodMetadata) : this(
            safeStatic(account.dbId),
            safeStatic(account.accountName),
            safeStatic(account.type?.name ?: ""),
            safeStatic(state.categoryId),
            safeStatic(state.vodId),
            safeStatic(metadata.title),
            safeStatic(metadata.logo),
            safeStatic(metadata.plot),
            safeStatic(metadata.releaseDate),
            safeStatic(metadata.rating),
            safeStatic(metadata.duration),
            state.updatedAt,
            playItem
        )

        fun toJson(): JSONObject {
            val item = JSONObject()
            item.put("accountId", accountId)
            item.put("accountName", accountName)
            item.put("accountType", accountType)
            item.put("categoryId", categoryId)
            item.put("vodId", vodId)
            item.put("vodName", vodName)
            item.put("vodLogo", vodLogo)
            item.put("plot", plot)
            item.put("releaseDate", releaseDate)
            item.put("rating", rating)
            item.put("duration", duration)
            item.put("updatedAt", updatedAt)
            if (playItem != null) {
                item.put("playItem", JSONObject(playItem.toJson()))
            }
            return item
        }

        companion object {
            private fun safeStatic(value: String?): String = value ?: ""
        }
    }

    private data class VodMetadata(
        val title: String?,
        val logo: String?,
        val plot: String?,
        val releaseDate: String?,
        val rating: String?,
        val duration: String?
    )
}
