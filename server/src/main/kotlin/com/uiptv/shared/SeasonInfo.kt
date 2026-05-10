package com.uiptv.shared

import org.json.JSONObject

import com.uiptv.util.StringUtils.safeGetString

data class SeasonInfo @JvmOverloads constructor(
    var name: String? = null,
    var cover: String? = null,
    var plot: String? = null,
    var cast: String? = null,
    var director: String? = null,
    var genre: String? = null,
    var releaseDate: String? = null,
    var lastModified: String? = null,
    var rating: String? = null,
    var rating5Based: String? = null,
    var backdropPath: String? = null,
    var tmdb: String? = null,
    var youtubeTrailer: String? = null,
    var episodeRunTime: String? = null,
    var categoryId: String? = null
) : BaseJson() {
    constructor(info: JSONObject) : this(
        name = safeGetString(info, "name"),
        cover = safeGetString(info, "cover"),
        plot = safeGetString(info, "plot"),
        cast = safeGetString(info, "cast"),
        director = safeGetString(info, "director"),
        genre = safeGetString(info, "genre"),
        releaseDate = safeGetString(info, "releaseDate"),
        lastModified = safeGetString(info, "last_modified"),
        rating = safeGetString(info, "rating"),
        rating5Based = safeGetString(info, "rating_5based"),
        backdropPath = safeGetString(info, "backdrop_path"),
        tmdb = safeGetString(info, "tmdb"),
        youtubeTrailer = safeGetString(info, "youtube_trailer"),
        episodeRunTime = safeGetString(info, "episode_run_time"),
        categoryId = safeGetString(info, "category_id")
    )
}
