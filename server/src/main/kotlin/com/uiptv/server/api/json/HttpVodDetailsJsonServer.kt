package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.db.ChannelDb
import com.uiptv.db.VodChannelDb
import com.uiptv.model.Account
import com.uiptv.model.Channel
import com.uiptv.service.AccountService
import com.uiptv.service.HandshakeService
import com.uiptv.service.ImdbMetadataService
import com.uiptv.util.ServerUtils.generateJsonResponse
import com.uiptv.util.ServerUtils.getParam
import com.uiptv.util.StringUtils.isBlank
import org.json.JSONObject
import java.io.IOException

class HttpVodDetailsJsonServer : HttpHandler {
    companion object {
        private const val KEY_COVER = "cover"
        private const val KEY_DIRECTOR = "director"
        private const val KEY_GENRE = "genre"
        private const val KEY_IMDB_URL = "imdbUrl"
        private const val KEY_RATING = "rating"
        private const val KEY_RELEASE_DATE = "releaseDate"
    }

    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        val account = AccountService.getInstance().getById(getParam(ex, "accountId"))
        val categoryId = getParam(ex, "categoryId")
        val channelId = getParam(ex, "channelId")
        val vodName = getParam(ex, "vodName")

        val vodInfo = JSONObject()
        vodInfo.put("name", if (isBlank(vodName)) "VOD" else vodName)
        vodInfo.put(KEY_COVER, "")
        vodInfo.put("plot", "")
        vodInfo.put("cast", "")
        vodInfo.put(KEY_DIRECTOR, "")
        vodInfo.put(KEY_GENRE, "")
        vodInfo.put(KEY_RELEASE_DATE, "")
        vodInfo.put(KEY_RATING, "")
        vodInfo.put("tmdb", "")
        vodInfo.put(KEY_IMDB_URL, "")
        vodInfo.put("duration", "")

        if (account != null && account.isNotConnected()) {
            HandshakeService.getInstance().connect(account)
        }

        var providerChannel: Channel? = null
        if (account != null && !isBlank(channelId)) {
            providerChannel = VodChannelDb.get().getChannelByChannelId(channelId!!, categoryId ?: "", account.dbId ?: "")
            if (providerChannel == null) {
                providerChannel = ChannelDb.get().getChannelById(channelId, categoryId ?: "")
            }
        }

        if (providerChannel != null) {
            mergeMissing(vodInfo, "name", providerChannel.name)
            mergeMissing(vodInfo, KEY_COVER, providerChannel.logo)
            mergeMissing(vodInfo, "plot", providerChannel.description)
            mergeMissing(vodInfo, KEY_RELEASE_DATE, providerChannel.releaseDate)
            mergeMissing(vodInfo, KEY_RATING, providerChannel.rating)
            mergeMissing(vodInfo, "duration", providerChannel.duration)
        }

        val queryTitle = if (isBlank(vodName)) vodInfo.optString("name", "") else vodName!!
        val fuzzyHints = buildFuzzyHints(queryTitle, providerChannel, vodInfo)
        val imdbFirst = ImdbMetadataService.getInstance().findBestEffortMovieDetails(
            queryTitle,
            vodInfo.optString("tmdb", ""),
            fuzzyHints
        )
        mergeMissing(vodInfo, "name", imdbFirst.optString("name", ""))
        mergeMissing(vodInfo, KEY_COVER, imdbFirst.optString(KEY_COVER, ""))
        mergeMissing(vodInfo, "plot", imdbFirst.optString("plot", ""))
        mergeMissing(vodInfo, "cast", imdbFirst.optString("cast", ""))
        mergeMissing(vodInfo, KEY_DIRECTOR, imdbFirst.optString(KEY_DIRECTOR, ""))
        mergeMissing(vodInfo, KEY_GENRE, imdbFirst.optString(KEY_GENRE, ""))
        mergeMissing(vodInfo, KEY_RELEASE_DATE, imdbFirst.optString(KEY_RELEASE_DATE, ""))
        mergeMissing(vodInfo, KEY_RATING, imdbFirst.optString(KEY_RATING, ""))
        mergeMissing(vodInfo, "tmdb", imdbFirst.optString("tmdb", ""))
        mergeMissing(vodInfo, KEY_IMDB_URL, imdbFirst.optString(KEY_IMDB_URL, ""))

        val response = JSONObject()
        response.put("vodInfo", vodInfo)
        generateJsonResponse(ex, response.toString())
    }

    private fun mergeMissing(target: JSONObject, key: String, incoming: String?) {
        if (isBlank(target.optString(key, "")) && !isBlank(incoming)) {
            target.put(key, incoming)
        }
    }

    private fun buildFuzzyHints(queryTitle: String, providerChannel: Channel?, vodInfo: JSONObject?): List<String> {
        val hints = ArrayList<String>()
        addHint(hints, queryTitle)
        if (providerChannel != null) {
            addHint(hints, providerChannel.name)
            addHint(hints, providerChannel.description)
            addHint(hints, providerChannel.releaseDate)
        }
        if (vodInfo != null) {
            addHint(hints, vodInfo.optString("name", ""))
            addHint(hints, vodInfo.optString("plot", ""))
            addHint(hints, vodInfo.optString(KEY_RELEASE_DATE, ""))
        }
        return hints
    }

    private fun addHint(hints: MutableList<String>, value: String?) {
        if (isBlank(value)) {
            return
        }
        val cleaned = value!!
            .replace(Regex("(?i)\\b(4k|8k|uhd|fhd|hd|sd|series|movie|complete)\\b"), " ")
            .replace(Regex("[\\[\\]{}()]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (isBlank(cleaned) || cleaned.length < 2 || hints.contains(cleaned)) {
            return
        }
        hints += cleaned
    }
}
