package com.uiptv.shared

import com.uiptv.model.Account
import com.uiptv.util.StringUtils.getXtremeStreamUrl
import com.uiptv.util.StringUtils.safeGetString
import com.uiptv.util.json.KJsonObject

data class Episode @JvmOverloads constructor(
    var id: String? = null,
    var episodeNum: String? = null,
    var title: String? = null,
    var containerExtension: String? = null,
    @Suppress("PropertyName")
    var custom_sid: String? = null,
    var added: String? = null,
    var season: String? = null,
    @Suppress("PropertyName")
    var direct_source: String? = null,
    var cmd: String? = null,
    var info: EpisodeInfo? = null
) : BaseJson() {
    constructor(account: Account, map: Map<*, *>?) : this() {
        if (map == null) {
            return
        }
        id = safeGetString(map, "id")
        episodeNum = safeGetString(map, "episode_num")
        title = safeGetString(map, "title")
        containerExtension = safeGetString(map, "container_extension")
        custom_sid = safeGetString(map, "custom_sid")
        added = safeGetString(map, "added")
        season = safeGetString(map, "season")
        direct_source = safeGetString(map, "direct_source")
        info = EpisodeInfo(asStringMap(map["info"]))
        mergeEpisodeLevelArtwork(map)
        cmd = getXtremeStreamUrl(account, id, containerExtension)
    }

    companion object {
        @JvmStatic
        fun fromJson(json: String?): Episode? {
            if (json.isNullOrBlank()) {
                return null
            }
            return try {
                val jsonObj = KJsonObject(json)
                val episode = Episode(
                    id = safeGetString(jsonObj, "id"),
                    episodeNum = safeGetString(jsonObj, "episodeNum"),
                    title = safeGetString(jsonObj, "title"),
                    containerExtension = safeGetString(jsonObj, "containerExtension"),
                    custom_sid = safeGetString(jsonObj, "custom_sid"),
                    added = safeGetString(jsonObj, "added"),
                    season = safeGetString(jsonObj, "season"),
                    direct_source = safeGetString(jsonObj, "direct_source"),
                    cmd = safeGetString(jsonObj, "cmd")
                )
                val infoObject = jsonObj.optJSONObject("info")
                if (infoObject != null) {
                    episode.info = EpisodeInfo(infoObject.toMap())
                }
                episode.mergeEpisodeLevelArtwork(jsonObj.toMap())
                episode
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun mergeEpisodeLevelArtwork(map: Map<*, *>?) {
        if (map == null) {
            return
        }
        if (info == null) {
            info = EpisodeInfo()
        }
        val current = info!!.movieImage
        if (!isBlankLike(current)) {
            return
        }
        val rootEpisodeImage = firstNonBlank(
            map,
            "movie_image",
            "thumbnail",
            "still_path",
            "cover_big",
            "cover",
            "screenshot_uri",
            "stream_icon",
            "image",
            "poster"
        )
        if (!isBlankLike(rootEpisodeImage)) {
            info!!.movieImage = rootEpisodeImage
        }
    }

    private fun firstNonBlank(map: Map<*, *>?, vararg keys: String): String {
        if (map == null) {
            return ""
        }
        for (key in keys) {
            val value = safeGetString(map, key)
            if (!isBlankLike(value)) {
                return value!!.trim()
            }
        }
        return ""
    }

    @Suppress("UNCHECKED_CAST")
    private fun asStringMap(value: Any?): Map<String, Any> {
        return if (value is Map<*, *>) {
            value as Map<String, Any>
        } else {
            emptyMap()
        }
    }

    private fun isBlankLike(value: String?): Boolean {
        if (value == null) {
            return true
        }
        val candidate = value.trim()
        return candidate.isEmpty() || candidate.equals("null", ignoreCase = true) || candidate.equals("n/a", ignoreCase = true)
    }
}
