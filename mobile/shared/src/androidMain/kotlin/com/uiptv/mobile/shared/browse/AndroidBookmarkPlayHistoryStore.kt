package com.uiptv.mobile.shared.browse

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject

class AndroidBookmarkPlayHistoryStore(
    private val db: SQLiteDatabase,
    private val epochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L }
) {
    fun record(bookmark: MobileBookmark) {
        val key = bookmark.historyKey() ?: return
        ensureTable()
        db.beginTransaction()
        try {
            val previousCount = currentPlayCount(key)
            val now = epochSeconds() * 1_000L
            if (previousCount == null) {
                db.insert(
                    TABLE,
                    null,
                    ContentValues().apply {
                        put("accountName", key.accountName)
                        put("accountAction", key.accountAction)
                        put("channelId", key.channelId)
                        put("playCount", 1)
                        put("lastPlayedAt", now)
                        put("snapshotJson", bookmark.snapshotJson())
                    }
                )
            } else {
                db.update(
                    TABLE,
                    ContentValues().apply {
                        put("playCount", previousCount + 1)
                        put("lastPlayedAt", now)
                        put("snapshotJson", bookmark.snapshotJson())
                    },
                    KEY_WHERE,
                    key.args()
                )
            }
            trimToRecentLimit()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun topBookmarks(bookmarks: List<MobileBookmark>, query: String): List<MobileBookmark> {
        ensureTable()
        val saved = buildMap {
            bookmarks.forEach { item -> item.historyKey()?.let { key -> if (!containsKey(key)) put(key, item) } }
        }
        return loadHistory(saved.keys).values.mapNotNull { entry ->
            val item = saved[entry.key] ?: entry.bookmark
            item?.copy(recentPlayCount = entry.playCount)
        }.filter { it.matchesBookmarkQuery(query) }
            .take(RECENT_BOOKMARK_LIMIT)
    }

    fun currentBookmarkCount(bookmarks: List<MobileBookmark>): Int =
        topBookmarks(bookmarks, query = "").size

    fun clear() {
        ensureTable()
        db.delete(TABLE, null, null)
    }

    fun preserveSnapshot(bookmark: MobileBookmark) {
        val key = bookmark.historyKey() ?: return
        ensureTable()
        db.update(TABLE, ContentValues().apply { put("snapshotJson", bookmark.snapshotJson()) }, KEY_WHERE, key.args())
    }

    fun remove(bookmark: MobileBookmark) {
        val key = bookmark.historyKey() ?: return
        ensureTable()
        db.delete(TABLE, KEY_WHERE, key.args())
    }

    private fun currentPlayCount(key: BookmarkHistoryKey): Int? =
        db.rawQuery(
            "SELECT playCount FROM $TABLE WHERE $KEY_WHERE LIMIT 1",
            key.args()
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else null
        }

    private fun loadHistory(savedKeys: Set<BookmarkHistoryKey>): Map<BookmarkHistoryKey, HistoryEntry> =
        db.rawQuery(
            """
            SELECT accountName, accountAction, channelId, playCount, lastPlayedAt, id, snapshotJson
            FROM $TABLE
                ORDER BY playCount DESC, lastPlayedAt DESC, id DESC
            LIMIT $RECENT_BOOKMARK_LIMIT
            """.trimIndent(),
            null
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val key = BookmarkHistoryKey(
                        accountName = cursor.getString(0).orEmpty(),
                        accountAction = cursor.getString(1).orEmpty(),
                        channelId = cursor.getString(2).orEmpty()
                    )
                    put(
                        key,
                        HistoryEntry(
                            key = key,
                            playCount = cursor.getInt(3),
                            lastPlayedAt = cursor.getLong(4),
                            bookmark = if (key in savedKeys) null else readSnapshot(cursor.getLong(5), key, cursor.getString(6).orEmpty())
                        )
                    )
                }
            }
        }

    private fun trimToRecentLimit() {
        db.execSQL(
            """
            DELETE FROM $TABLE
            WHERE rowid NOT IN (
                SELECT rowid
                FROM $TABLE
                ORDER BY playCount DESC, lastPlayedAt DESC, rowid DESC
                LIMIT $RECENT_BOOKMARK_LIMIT
            )
            """.trimIndent()
        )
    }

    private fun ensureTable() {
        migrateLegacyTableIfNeeded()
    }

    private fun migrateLegacyTableIfNeeded() {
        if (tableExists(LEGACY_TABLE)) {
            db.execSQL(
                """
                INSERT OR REPLACE INTO $TABLE(accountName, accountAction, channelId, playCount, lastPlayedAt)
                SELECT accountName, accountAction, channelId, playCount, lastPlayedAt, id, snapshotJson FROM $LEGACY_TABLE
                """.trimIndent()
            )
            db.execSQL("DROP TABLE $LEGACY_TABLE")
        }
    }

    private fun tableExists(tableName: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(tableName)).use { cursor ->
            cursor.moveToFirst()
        }

    private fun MobileBookmark.historyKey(): BookmarkHistoryKey? {
        val accountName = accountName.trim()
        val channelId = channelId.trim()
        if (accountName.isBlank() || channelId.isBlank()) {
            return null
        }
        return BookmarkHistoryKey(
            accountName = accountName,
            accountAction = mode.accountAction(),
            channelId = channelId
        )
    }

    private fun MobileBookmark.matchesBookmarkQuery(query: String): Boolean {
        val normalizedQuery = query.trim()
        return normalizedQuery.isBlank() ||
            channelName.contains(normalizedQuery, ignoreCase = true) ||
            accountName.contains(normalizedQuery, ignoreCase = true) ||
            categoryTitle.contains(normalizedQuery, ignoreCase = true)
    }

    private fun BrowseMode.accountAction(): String =
        when (this) {
            BrowseMode.LIVE -> "itv"
            BrowseMode.VOD -> "vod"
            BrowseMode.SERIES -> "series"
        }

    private data class BookmarkHistoryKey(
        val accountName: String,
        val accountAction: String,
        val channelId: String
    ) {
        fun args(): Array<String> = arrayOf(accountName, accountAction, channelId)
    }

    private data class HistoryEntry(
        val key: BookmarkHistoryKey,
        val playCount: Int,
        val lastPlayedAt: Long,
        val bookmark: MobileBookmark?
    )

    private fun MobileBookmark.snapshotJson(): String = JSONObject().apply {
        put("accountName", accountName)
        put("accountAction", mode.accountAction())
        put("categoryTitle", categoryTitle)
        put("channelId", channelId)
        put("channelName", channelName)
        put("cmd", command)
        put("logo", logo)
        put("drmType", drmType)
        put("drmLicenseUrl", drmLicenseUrl)
        put("clearKeysJson", clearKeysJson)
        put("inputstreamaddon", inputstreamAddon)
        put("manifestType", manifestType)
        put("categoryJson", JSONObject()
            .put("dbId", categoryRowId.toString())
            .put("categoryId", categoryProviderId)
            .apply { catalogCacheEnabledAtSave?.let { put("catalogCacheEnabledAtSave", it) } }
            .toString())
    }.toString()

    private fun readSnapshot(id: Long, key: BookmarkHistoryKey, value: String): MobileBookmark? = runCatching {
        val json = JSONObject(value)
        val category = runCatching { JSONObject(json.optString("categoryJson")) }.getOrNull()
        val accountId = db.rawQuery("SELECT id FROM Account WHERE accountName = ? LIMIT 1", arrayOf(key.accountName)).use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
        MobileBookmark(
            rowId = -id, accountId = accountId, accountName = key.accountName,
            categoryTitle = json.optString("categoryTitle"), channelId = key.channelId,
            channelName = json.optString("channelName"), command = json.optString("cmd"),
            mode = when (key.accountAction) { "vod" -> BrowseMode.VOD; "series" -> BrowseMode.SERIES; else -> BrowseMode.LIVE },
            logo = json.optString("logo"), drmType = json.optString("drmType"),
            drmLicenseUrl = json.optString("drmLicenseUrl"), clearKeysJson = json.optString("clearKeysJson"),
            inputstreamAddon = json.optString("inputstreamaddon"), manifestType = json.optString("manifestType"),
            categoryProviderId = category?.optString("categoryId").orEmpty(),
            categoryRowId = category?.optString("dbId")?.toLongOrNull() ?: 0L,
            catalogCacheEnabledAtSave = category?.takeIf { it.has("catalogCacheEnabledAtSave") }
                ?.optBoolean("catalogCacheEnabledAtSave")
        )
    }.getOrNull()

    private companion object {
        private const val TABLE = "BookmarkPlayHistory"
        private const val LEGACY_TABLE = "AndroidBookmarkPlayHistory"
        private const val KEY_WHERE = "accountName = ? AND accountAction = ? AND channelId = ?"
        private const val RECENT_BOOKMARK_LIMIT = 25
    }
}
