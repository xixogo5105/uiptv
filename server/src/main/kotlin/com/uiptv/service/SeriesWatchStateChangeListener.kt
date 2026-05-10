package com.uiptv.service

fun interface SeriesWatchStateChangeListener {
    fun onSeriesWatchStateChanged(accountId: String, seriesId: String)
}
