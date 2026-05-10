package com.uiptv.model

import com.uiptv.shared.BaseJson

data class SeriesWatchState @JvmOverloads constructor(
    var dbId: String? = null,
    var accountId: String? = null,
    var mode: String? = null,
    var categoryId: String? = null,
    var seriesId: String? = null,
    var episodeId: String? = null,
    var episodeName: String? = null,
    var season: String? = null,
    var episodeNum: Int = 0,
    var updatedAt: Long = 0,
    var source: String? = null,
    var seriesCategorySnapshot: String? = null,
    var seriesChannelSnapshot: String? = null,
    var seriesEpisodeSnapshot: String? = null
) : BaseJson()
