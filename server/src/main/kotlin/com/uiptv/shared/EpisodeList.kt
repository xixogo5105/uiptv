package com.uiptv.shared

data class EpisodeList @JvmOverloads constructor(
    var seasonInfo: SeasonInfo? = null
) : BaseJson() {
    @Transient
    var episodes: MutableList<Episode> = mutableListOf()
}
