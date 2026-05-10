package com.uiptv.model

import com.uiptv.shared.BaseJson

data class SeriesWatchingNowSnapshot @JvmOverloads constructor(
    var dbId: String? = null,
    var accountId: String? = null,
    var categoryId: String? = null,
    var seriesId: String? = null,
    var categoryDbId: String? = null,
    var seriesTitle: String? = null,
    var seriesPoster: String? = null,
    var episodesJson: String? = null,
    var updatedAt: Long = 0
) : BaseJson()
