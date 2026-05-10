package com.uiptv.shared

import com.uiptv.util.StringUtils.safeGetString

data class EpisodeInfo @JvmOverloads constructor(
    var tmdbId: String? = null,
    var releaseDate: String? = null,
    var plot: String? = null,
    var durationSecs: String? = null,
    var duration: String? = null,
    var movieImage: String? = null,
    var bitrate: String? = null,
    var rating: String? = null,
    var season: String? = null,
    var video: VideoInfo? = null,
    var audio: AudioInfo? = null
) : BaseJson() {
    constructor(map: Map<*, *>?) : this() {
        if (map == null) {
            return
        }
        tmdbId = firstNonBlank(map, "tmdb_id", "tmdb", "imdb_id")
        releaseDate = firstNonBlank(map, "releaseDate", "release_date", "released", "air_date")
        plot = firstNonBlank(map, "plot", "overview", "description")
        durationSecs = firstNonBlank(map, "duration_secs", "durationSeconds")
        duration = firstNonBlank(map, "duration", "runtime")
        movieImage = firstNonBlank(
            map,
            "movieImage",
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
        bitrate = firstNonBlank(map, "bitrate")
        rating = firstNonBlank(map, "rating", "rating_imdb", "imdb_rating")
        season = firstNonBlank(map, "season")
        video = VideoInfo(asStringMap(map["video"]))
        audio = AudioInfo(asStringMap(map["audio"]))
    }

    private fun firstNonBlank(map: Map<*, *>?, vararg keys: String): String {
        if (map == null) {
            return ""
        }
        for (key in keys) {
            val value = safeGetString(map, key)?.trim()
            if (!value.isNullOrEmpty() && !value.equals("null", ignoreCase = true) && !value.equals("n/a", ignoreCase = true)) {
                return value
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
}
