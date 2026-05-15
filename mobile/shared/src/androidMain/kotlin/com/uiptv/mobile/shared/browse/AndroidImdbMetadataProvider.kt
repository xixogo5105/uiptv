package com.uiptv.mobile.shared.browse

interface AndroidImdbMetadataProvider {
    fun findSeriesDetails(title: String, preferredImdbId: String, hints: List<String>): AndroidImdbMetadata
    fun findMovieDetails(title: String, preferredImdbId: String, hints: List<String>): AndroidImdbMetadata
}

data class AndroidImdbMetadata(
    val name: String = "",
    val cover: String = "",
    val plot: String = "",
    val cast: String = "",
    val director: String = "",
    val genre: String = "",
    val releaseDate: String = "",
    val rating: String = "",
    val tmdb: String = "",
    val imdbUrl: String = "",
    val duration: String = "",
    val episodesMeta: List<AndroidEpisodeMetadata> = emptyList()
)

data class AndroidEpisodeMetadata(
    val title: String = "",
    val season: String = "",
    val episodeNumber: String = "",
    val plot: String = "",
    val logo: String = "",
    val releaseDate: String = "",
    val rating: String = ""
)
