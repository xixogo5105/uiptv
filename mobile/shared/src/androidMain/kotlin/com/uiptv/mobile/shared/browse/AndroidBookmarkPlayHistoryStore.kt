package com.uiptv.mobile.shared.browse

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

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
            val now = epochSeconds()
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
                    }
                )
            } else {
                db.update(
                    TABLE,
                    ContentValues().apply {
                        put("playCount", previousCount + 1)
                        put("lastPlayedAt", now)
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
        if (bookmarks.isEmpty()) {
            return emptyList()
        }
        ensureTable()
        val history = loadHistory()
        if (history.isEmpty()) {
            return emptyList()
        }
        return bookmarks.asSequence()
            .filter { it.matchesBookmarkQuery(query) }
            .mapNotNull { bookmark ->
                val key = bookmark.historyKey() ?: return@mapNotNull null
                val entry = history[key] ?: return@mapNotNull null
                BookmarkHistoryMatch(bookmark, entry)
            }
            .distinctBy { it.entry.key }
            .sortedWith(
                compareByDescending<BookmarkHistoryMatch> { it.entry.playCount }
                    .thenByDescending { it.entry.lastPlayedAt }
                    .thenBy { it.bookmark.channelName.lowercase() }
            )
            .take(RECENT_BOOKMARK_LIMIT)
            .map { it.bookmark }
            .toList()
    }

    fun currentBookmarkCount(bookmarks: List<MobileBookmark>): Int =
        topBookmarks(bookmarks, query = "").size

    fun clear() {
        ensureTable()
        db.delete(TABLE, null, null)
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

    private fun loadHistory(): Map<BookmarkHistoryKey, HistoryEntry> =
        db.rawQuery(
            """
            SELECT accountName, accountAction, channelId, playCount, lastPlayedAt
            FROM $TABLE
            ORDER BY lastPlayedAt DESC
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
                            lastPlayedAt = cursor.getLong(4)
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
                ORDER BY lastPlayedAt DESC, rowid DESC
                LIMIT $RECENT_BOOKMARK_LIMIT
            )
            """.trimIndent()
        )
    }

    private fun ensureTable() {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE (
                accountName TEXT NOT NULL,
                accountAction TEXT NOT NULL,
                channelId TEXT NOT NULL,
                playCount INTEGER NOT NULL DEFAULT 0,
                lastPlayedAt INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(accountName, accountAction, channelId)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_android_bookmark_play_history_rank
            ON $TABLE(lastPlayedAt DESC, playCount DESC)
            """.trimIndent()
        )
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
        val lastPlayedAt: Long
    )

    private data class BookmarkHistoryMatch(
        val bookmark: MobileBookmark,
        val entry: HistoryEntry
    )

    private companion object {
        private const val TABLE = "AndroidBookmarkPlayHistory"
        private const val KEY_WHERE = "accountName = ? AND accountAction = ? AND channelId = ?"
        private const val RECENT_BOOKMARK_LIMIT = 15
    }
}
