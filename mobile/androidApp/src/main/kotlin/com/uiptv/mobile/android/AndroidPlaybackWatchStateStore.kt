package com.uiptv.mobile.android

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.uiptv.mobile.shared.browse.BrowseMode
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import com.uiptv.mobile.shared.playback.PlaybackTarget

class AndroidPlaybackWatchStateStore(
    private val databaseHelper: AndroidUiptvDatabaseHelper,
    private val epochSeconds: () -> Long = { System.currentTimeMillis() / 1000L }
) {
    fun markOpened(target: PlaybackTarget) {
        when (target.mode) {
            BrowseMode.LIVE -> Unit
            BrowseMode.VOD -> markVodOpened(target)
            BrowseMode.SERIES -> markSeriesOpened(target)
        }
    }

    private fun markVodOpened(target: PlaybackTarget) {
        if (target.accountId <= 0 || target.channelId.isBlank()) {
            return
        }
        databaseHelper.writableDatabase.insertWithOnConflict(
            "VodWatchState",
            null,
            ContentValues().apply {
                put("accountId", target.accountId.toString())
                put("categoryId", target.categoryProviderId)
                put("vodId", target.channelId)
                put("vodName", target.title)
                put("vodCmd", target.url)
                put("vodLogo", target.logo)
                put("updatedAt", epochSeconds())
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun markSeriesOpened(target: PlaybackTarget) {
        if (target.accountId <= 0 || target.channelId.isBlank()) {
            return
        }
        val db = databaseHelper.writableDatabase
        val values = ContentValues().apply {
            put("categoryDbId", target.categoryRowId.toString())
            put("seriesTitle", target.title)
            put("seriesPoster", target.logo)
            put("episodesJson", "")
            put("updatedAt", epochSeconds())
        }
        val updated = db.update(
            "SeriesWatchingNowSnapshot",
            values,
            "accountId = ? AND categoryId = ? AND seriesId = ?",
            arrayOf(target.accountId.toString(), target.categoryProviderId, target.channelId)
        )
        if (updated == 0) {
            values.put("accountId", target.accountId.toString())
            values.put("categoryId", target.categoryProviderId)
            values.put("seriesId", target.channelId)
            db.insert("SeriesWatchingNowSnapshot", null, values)
        }
    }
}
