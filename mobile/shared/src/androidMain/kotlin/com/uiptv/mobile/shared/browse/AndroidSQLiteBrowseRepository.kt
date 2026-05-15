package com.uiptv.mobile.shared.browse

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.uiptv.mobile.shared.accounts.MobileAccountType
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AndroidSQLiteBrowseRepository(
    private val databaseHelper: AndroidUiptvDatabaseHelper
) : BrowseRepository {
    override suspend fun loadBrowse(
        accountId: Long?,
        mode: BrowseMode,
        categoryRowId: Long?,
        query: String
    ): MobileBrowseSnapshot = withContext(Dispatchers.IO) {
        val db = databaseHelper.writableDatabase
        val filters = loadFilterConfig(db)
        val accounts = loadAccounts(db)
        val cachedAccountIds = loadCachedAccountIds(db, mode)
        val selectedAccountId = accountId
            ?: accounts.firstOrNull { it.id in cachedAccountIds }?.id
            ?: accounts.firstOrNull()?.id
        val categories = if (selectedAccountId == null) emptyList() else {
            loadCategories(db, selectedAccountId, mode).filterByCategoryFilters(filters)
        }
        val selectedCategoryRowId = categoryRowId?.takeIf { selected -> categories.any { it.rowId == selected } }
            ?: categories.firstOrNull()?.rowId
        val items = if (selectedAccountId == null || selectedCategoryRowId == null) {
            emptyList()
        } else {
            loadItems(db, selectedAccountId, mode, selectedCategoryRowId, query).filterByChannelFilters(filters)
        }
        MobileBrowseSnapshot(
            accounts = accounts,
            selectedAccountId = selectedAccountId,
            mode = mode,
            categories = categories,
            selectedCategoryRowId = selectedCategoryRowId,
            items = items
        )
    }

    override suspend fun listBookmarkCategories(): List<MobileBookmarkCategory> = withContext(Dispatchers.IO) {
        val db = databaseHelper.writableDatabase
        val categories = mutableListOf<MobileBookmarkCategory>()
        val total = db.rawQuery("SELECT COUNT(*) FROM Bookmark", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
        categories += MobileBookmarkCategory(null, "All", total)
        db.rawQuery(
            """
            SELECT bc.id, bc.name, COUNT(b.id) AS itemCount
            FROM BookmarkCategory bc
            LEFT JOIN Bookmark b ON b.categoryId = CAST(bc.id AS TEXT)
            GROUP BY bc.id, bc.name
            ORDER BY bc.id
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                categories += MobileBookmarkCategory(
                    id = cursor.string("id"),
                    name = cursor.string("name").ifBlank { "Untitled" },
                    itemCount = cursor.int("itemCount")
                )
            }
        }
        categories
    }

    override suspend fun listBookmarks(query: String, bookmarkCategoryId: String?): List<MobileBookmark> = withContext(Dispatchers.IO) {
        val db = databaseHelper.writableDatabase
        val logoSelect = if (columnExists(db, "Bookmark", "logo")) "bm.logo AS bookmarkLogo" else "'' AS bookmarkLogo"
        val channelJsonSelect = if (columnExists(db, "Bookmark", "channelJson")) "bm.channelJson" else "'' AS channelJson"
        val vodJsonSelect = if (columnExists(db, "Bookmark", "vodJson")) "bm.vodJson" else "'' AS vodJson"
        val seriesJsonSelect = if (columnExists(db, "Bookmark", "seriesJson")) "bm.seriesJson" else "'' AS seriesJson"
        val args = mutableListOf<String>()
        val whereParts = mutableListOf<String>()
        if (!bookmarkCategoryId.isNullOrBlank()) {
            whereParts += "bm.categoryId = ?"
            args += bookmarkCategoryId
        }
        if (query.isNotBlank()) {
            whereParts += "(bm.channelName LIKE ? OR bm.accountName LIKE ? OR bm.categoryTitle LIKE ?)"
            val pattern = "%${query.trim()}%"
            args += pattern
            args += pattern
            args += pattern
        }
        val where = if (whereParts.isEmpty()) "" else "WHERE ${whereParts.joinToString(" AND ")}"
        db.rawQuery(
            """
            SELECT bm.id, COALESCE(acc.id, 0) AS accountId, bm.accountName, bm.categoryId, bm.categoryTitle,
                bm.channelId, bm.channelName, bm.cmd, bm.accountAction, $logoSelect,
                bm.drmType AS bookmarkDrmType, bm.drmLicenseUrl AS bookmarkDrmLicenseUrl,
                bm.clearKeysJson AS bookmarkClearKeysJson, bm.inputstreamaddon AS bookmarkInputstreamaddon,
                bm.manifestType AS bookmarkManifestType,
                $channelJsonSelect, $vodJsonSelect, $seriesJsonSelect,
                (
                    SELECT ch.logo
                    FROM Channel ch
                    JOIN Category cat ON ch.categoryId = CAST(cat.id AS TEXT)
                    WHERE CAST(cat.accountId AS INTEGER) = COALESCE(acc.id, 0)
                        AND ch.channelId = bm.channelId
                        AND COALESCE(ch.logo, '') <> ''
                    LIMIT 1
                ) AS liveLogo,
                (
                    SELECT ch.drmType
                    FROM Channel ch
                    JOIN Category cat ON ch.categoryId = CAST(cat.id AS TEXT)
                    WHERE CAST(cat.accountId AS INTEGER) = COALESCE(acc.id, 0)
                        AND ch.channelId = bm.channelId
                        AND COALESCE(ch.drmType, '') <> ''
                    LIMIT 1
                ) AS liveDrmType,
                (
                    SELECT ch.drmLicenseUrl
                    FROM Channel ch
                    JOIN Category cat ON ch.categoryId = CAST(cat.id AS TEXT)
                    WHERE CAST(cat.accountId AS INTEGER) = COALESCE(acc.id, 0)
                        AND ch.channelId = bm.channelId
                        AND COALESCE(ch.drmLicenseUrl, '') <> ''
                    LIMIT 1
                ) AS liveDrmLicenseUrl,
                (
                    SELECT ch.clearKeysJson
                    FROM Channel ch
                    JOIN Category cat ON ch.categoryId = CAST(cat.id AS TEXT)
                    WHERE CAST(cat.accountId AS INTEGER) = COALESCE(acc.id, 0)
                        AND ch.channelId = bm.channelId
                        AND COALESCE(ch.clearKeysJson, '') <> ''
                    LIMIT 1
                ) AS liveClearKeysJson,
                (
                    SELECT ch.inputstreamaddon
                    FROM Channel ch
                    JOIN Category cat ON ch.categoryId = CAST(cat.id AS TEXT)
                    WHERE CAST(cat.accountId AS INTEGER) = COALESCE(acc.id, 0)
                        AND ch.channelId = bm.channelId
                        AND COALESCE(ch.inputstreamaddon, '') <> ''
                    LIMIT 1
                ) AS liveInputstreamaddon,
                (
                    SELECT ch.manifestType
                    FROM Channel ch
                    JOIN Category cat ON ch.categoryId = CAST(cat.id AS TEXT)
                    WHERE CAST(cat.accountId AS INTEGER) = COALESCE(acc.id, 0)
                        AND ch.channelId = bm.channelId
                        AND COALESCE(ch.manifestType, '') <> ''
                    LIMIT 1
                ) AS liveManifestType,
                (
                    SELECT vc.logo
                    FROM VodChannel vc
                    WHERE CAST(vc.accountId AS INTEGER) = COALESCE(acc.id, 0)
                        AND vc.channelId = bm.channelId
                        AND COALESCE(vc.logo, '') <> ''
                    LIMIT 1
                ) AS vodLogo,
                (
                    SELECT vc.drmType
                    FROM VodChannel vc
                    WHERE CAST(vc.accountId AS INTEGER) = COALESCE(acc.id, 0)
                        AND vc.channelId = bm.channelId
                        AND COALESCE(vc.drmType, '') <> ''
                    LIMIT 1
                ) AS vodDrmType,
                (
                    SELECT vc.drmLicenseUrl
                    FROM VodChannel vc
                    WHERE CAST(vc.accountId AS INTEGER) = COALESCE(acc.id, 0)
                        AND vc.channelId = bm.channelId
                        AND COALESCE(vc.drmLicenseUrl, '') <> ''
                    LIMIT 1
                ) AS vodDrmLicenseUrl,
                (
                    SELECT vc.clearKeysJson
                    FROM VodChannel vc
                    WHERE CAST(vc.accountId AS INTEGER) = COALESCE(acc.id, 0)
                        AND vc.channelId = bm.channelId
                        AND COALESCE(vc.clearKeysJson, '') <> ''
                    LIMIT 1
                ) AS vodClearKeysJson,
                (
                    SELECT vc.inputstreamaddon
                    FROM VodChannel vc
                    WHERE CAST(vc.accountId AS INTEGER) = COALESCE(acc.id, 0)
                        AND vc.channelId = bm.channelId
                        AND COALESCE(vc.inputstreamaddon, '') <> ''
                    LIMIT 1
                ) AS vodInputstreamaddon,
                (
                    SELECT vc.manifestType
                    FROM VodChannel vc
                    WHERE CAST(vc.accountId AS INTEGER) = COALESCE(acc.id, 0)
                        AND vc.channelId = bm.channelId
                        AND COALESCE(vc.manifestType, '') <> ''
                    LIMIT 1
                ) AS vodManifestType,
                (
                    SELECT sc.logo
                    FROM SeriesChannel sc
                    WHERE CAST(sc.accountId AS INTEGER) = COALESCE(acc.id, 0)
                        AND sc.channelId = bm.channelId
                        AND COALESCE(sc.logo, '') <> ''
                    LIMIT 1
                ) AS seriesLogo,
                (
                    SELECT sc.drmType
                    FROM SeriesChannel sc
                    WHERE CAST(sc.accountId AS INTEGER) = COALESCE(acc.id, 0)
                        AND sc.channelId = bm.channelId
                        AND COALESCE(sc.drmType, '') <> ''
                    LIMIT 1
                ) AS seriesDrmType,
                (
                    SELECT sc.drmLicenseUrl
                    FROM SeriesChannel sc
                    WHERE CAST(sc.accountId AS INTEGER) = COALESCE(acc.id, 0)
                        AND sc.channelId = bm.channelId
                        AND COALESCE(sc.drmLicenseUrl, '') <> ''
                    LIMIT 1
                ) AS seriesDrmLicenseUrl,
                (
                    SELECT sc.clearKeysJson
                    FROM SeriesChannel sc
                    WHERE CAST(sc.accountId AS INTEGER) = COALESCE(acc.id, 0)
                        AND sc.channelId = bm.channelId
                        AND COALESCE(sc.clearKeysJson, '') <> ''
                    LIMIT 1
                ) AS seriesClearKeysJson,
                (
                    SELECT sc.inputstreamaddon
                    FROM SeriesChannel sc
                    WHERE CAST(sc.accountId AS INTEGER) = COALESCE(acc.id, 0)
                        AND sc.channelId = bm.channelId
                        AND COALESCE(sc.inputstreamaddon, '') <> ''
                    LIMIT 1
                ) AS seriesInputstreamaddon,
                (
                    SELECT sc.manifestType
                    FROM SeriesChannel sc
                    WHERE CAST(sc.accountId AS INTEGER) = COALESCE(acc.id, 0)
                        AND sc.channelId = bm.channelId
                        AND COALESCE(sc.manifestType, '') <> ''
                    LIMIT 1
                ) AS seriesManifestType
            FROM Bookmark bm
            LEFT JOIN Account acc ON acc.accountName = bm.accountName
            LEFT JOIN BookmarkOrder bo ON bo.bookmark_db_id = CAST(bm.id AS TEXT)
            $where
            ORDER BY COALESCE(bo.display_order, bm.id), bm.accountName COLLATE NOCASE, bm.categoryTitle COLLATE NOCASE, bm.channelName COLLATE NOCASE
            """.trimIndent(),
            args.toTypedArray()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toBookmark())
                }
            }
        }
    }

    override suspend fun toggleBookmark(item: MobileBrowseItem): Boolean = withContext(Dispatchers.IO) {
        val db = databaseHelper.writableDatabase
        val existingId = findBookmarkId(db, item.accountName, item.channelId, item.mode)
        val supportsLogo = columnExists(db, "Bookmark", "logo")
        if (existingId != null) {
            db.delete("Bookmark", "id = ?", arrayOf(existingId.toString()))
            db.delete("BookmarkOrder", "bookmark_db_id = ?", arrayOf(existingId.toString()))
            false
        } else {
            db.insert(
                "Bookmark",
                null,
                ContentValues().apply {
                    put("accountName", item.accountName)
                    put("categoryTitle", item.categoryTitle)
                    put("channelId", item.channelId)
                    put("channelName", item.name)
                    put("cmd", item.command)
                    if (supportsLogo) {
                        put("logo", item.logo)
                    }
                    put("categoryId", item.categoryProviderId)
                    put("accountAction", item.mode.accountAction())
                    put("drmType", item.drmType)
                    put("drmLicenseUrl", item.drmLicenseUrl)
                    put("clearKeysJson", item.clearKeysJson)
                    put("inputstreamaddon", item.inputstreamAddon)
                    put("manifestType", item.manifestType)
                    put("categoryJson", "")
                    put("channelJson", "")
                    put("vodJson", "")
                    put("seriesJson", "")
                }
            )
            true
        }
    }

    override suspend fun removeBookmark(bookmarkId: Long): Unit = withContext(Dispatchers.IO) {
        val db = databaseHelper.writableDatabase
        db.delete("Bookmark", "id = ?", arrayOf(bookmarkId.toString()))
        db.delete("BookmarkOrder", "bookmark_db_id = ?", arrayOf(bookmarkId.toString()))
    }

    override suspend fun listWatchingNow(query: String): List<MobileWatchingNowItem> = withContext(Dispatchers.IO) {
        val db = databaseHelper.writableDatabase
        val items = loadVodWatchingNow(db) + loadSeriesWatchingNow(db)
        val normalizedQuery = query.trim()
        items
            .filter {
                normalizedQuery.isBlank() ||
                    it.title.contains(normalizedQuery, ignoreCase = true) ||
                    it.subtitle.contains(normalizedQuery, ignoreCase = true) ||
                    it.accountName.contains(normalizedQuery, ignoreCase = true)
            }
            .sortedByDescending { it.updatedAtEpochSeconds }
    }

    override suspend fun listWatchingNowEpisodes(item: MobileWatchingNowItem): List<MobileWatchingNowEpisode> = withContext(Dispatchers.IO) {
        if (item.mode != BrowseMode.SERIES) {
            return@withContext emptyList()
        }
        val db = databaseHelper.readableDatabase
        db.rawQuery(
            """
            SELECT s.id, CAST(s.accountId AS INTEGER) AS accountId, acc.accountName, s.categoryId,
                s.categoryDbId, s.seriesId, s.seriesTitle, s.episodesJson
            FROM SeriesWatchingNowSnapshot s
            LEFT JOIN Account acc ON CAST(s.accountId AS INTEGER) = acc.id
            WHERE s.id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(item.rowId.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                emptyList()
            } else {
                cursor.toWatchingNowEpisodes()
            }
        }
    }

    override suspend fun removeWatchingNow(item: MobileWatchingNowItem): Unit = withContext(Dispatchers.IO) {
        val db = databaseHelper.writableDatabase
        when (item.mode) {
            BrowseMode.VOD -> {
                db.delete("VodWatchState", "id = ?", arrayOf(item.rowId.toString()))
            }
            BrowseMode.SERIES -> {
                val details = db.rawQuery(
                    "SELECT accountId, seriesId FROM SeriesWatchingNowSnapshot WHERE id = ? LIMIT 1",
                    arrayOf(item.rowId.toString())
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.string("accountId") to cursor.string("seriesId") else item.accountId.toString() to item.contentId
                }
                db.delete("SeriesWatchingNowSnapshot", "id = ?", arrayOf(item.rowId.toString()))
                if (details.first.isNotBlank() && details.second.isNotBlank()) {
                    db.delete("SeriesWatchState", "accountId = ? AND seriesId = ?", arrayOf(details.first, details.second))
                }
            }
            BrowseMode.LIVE -> Unit
        }
    }

    private fun loadAccounts(db: SQLiteDatabase): List<BrowseAccountOption> =
        db.rawQuery(
            """
            SELECT id, accountName, type
            FROM Account
            ORDER BY CASE WHEN LOWER(COALESCE(pinToTop, '0')) IN ('1', 'true', 'yes') THEN 1 ELSE 0 END DESC,
                accountName COLLATE NOCASE
            """.trimIndent(),
            null
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        BrowseAccountOption(
                            id = cursor.long("id"),
                            name = cursor.string("accountName"),
                            type = cursor.string("type").toMobileAccountTypeOrNull()
                        )
                    )
                }
            }
        }

    private fun loadFilterConfig(db: SQLiteDatabase): FilterConfig =
        db.rawQuery(
            """
            SELECT filterCategoriesList, filterChannelsList, pauseFiltering
            FROM Configuration
            ORDER BY id
            LIMIT 1
            """.trimIndent(),
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                FilterConfig()
            } else {
                FilterConfig(
                    categoryTerms = cursor.string("filterCategoriesList").csvTerms(),
                    channelTerms = cursor.string("filterChannelsList").csvTerms(),
                    paused = cursor.string("pauseFiltering").isTruthy()
                )
            }
        }

    private fun loadCachedAccountIds(db: SQLiteDatabase, mode: BrowseMode): Set<Long> {
        val table = when (mode) {
            BrowseMode.LIVE -> "Category"
            BrowseMode.VOD -> "VodCategory"
            BrowseMode.SERIES -> "SeriesCategory"
        }
        return db.rawQuery(
            "SELECT DISTINCT accountId FROM ${quoteIdentifier(table)}",
            null
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    cursor.string("accountId").toLongOrNull()?.let(::add)
                }
            }
        }
    }

    private fun loadCategories(db: SQLiteDatabase, accountId: Long, mode: BrowseMode): List<MobileBrowseCategory> {
        val sql = when (mode) {
            BrowseMode.LIVE -> """
                SELECT cat.id, cat.categoryId, cat.accountId, cat.title, COUNT(ch.id) AS itemCount
                FROM Category cat
                LEFT JOIN Channel ch ON ch.categoryId = CAST(cat.id AS TEXT)
                WHERE cat.accountId = ?
                GROUP BY cat.id, cat.categoryId, cat.accountId, cat.title
                ORDER BY CASE WHEN LOWER(cat.title) = 'all' THEN 0 ELSE 1 END, cat.title COLLATE NOCASE
            """
            BrowseMode.VOD -> """
                SELECT cat.id, cat.categoryId, cat.accountId, cat.title, COUNT(ch.id) AS itemCount
                FROM VodCategory cat
                LEFT JOIN VodChannel ch ON ch.categoryId = cat.categoryId AND ch.accountId = cat.accountId
                WHERE cat.accountId = ?
                GROUP BY cat.id, cat.categoryId, cat.accountId, cat.title
                ORDER BY cat.title COLLATE NOCASE
            """
            BrowseMode.SERIES -> """
                SELECT cat.id, cat.categoryId, cat.accountId, cat.title, COUNT(ch.id) AS itemCount
                FROM SeriesCategory cat
                LEFT JOIN SeriesChannel ch ON ch.categoryId = cat.categoryId AND ch.accountId = cat.accountId
                WHERE cat.accountId = ?
                GROUP BY cat.id, cat.categoryId, cat.accountId, cat.title
                ORDER BY cat.title COLLATE NOCASE
            """
        }
        return db.rawQuery(sql.trimIndent(), arrayOf(accountId.toString())).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        MobileBrowseCategory(
                            rowId = cursor.long("id"),
                            providerId = cursor.string("categoryId"),
                            accountId = cursor.string("accountId").toLongOrNull() ?: accountId,
                            title = cursor.string("title").ifBlank { "Untitled" },
                            itemCount = cursor.int("itemCount")
                        )
                    )
                }
            }
        }
    }

    private fun loadItems(
        db: SQLiteDatabase,
        accountId: Long,
        mode: BrowseMode,
        categoryRowId: Long,
        query: String
    ): List<MobileBrowseItem> {
        val category = categoryByRowId(db, mode, categoryRowId) ?: return emptyList()
        val args = mutableListOf<String>()
        val sql = when (mode) {
            BrowseMode.LIVE -> {
                args += categoryRowId.toString()
                buildItemQuery(
                    select = """
                        SELECT ch.id, CAST(cat.accountId AS INTEGER) AS accountId, acc.accountName, cat.id AS categoryRowId,
                            cat.categoryId, cat.title AS categoryTitle, ch.channelId, ch.name, ch.number, ch.cmd,
                            ch.logo, ch.drmType, ch.drmLicenseUrl, ch.clearKeysJson, ch.inputstreamaddon, ch.manifestType,
                            ch.hd, bm.id AS bookmarkId
                        FROM Channel ch
                        JOIN Category cat ON ch.categoryId = CAST(cat.id AS TEXT)
                        JOIN Account acc ON CAST(cat.accountId AS INTEGER) = acc.id
                        LEFT JOIN Bookmark bm ON bm.accountName = acc.accountName AND bm.channelId = ch.channelId AND bm.accountAction = ?
                        WHERE cat.id = ?
                    """,
                    query = query,
                    args = args,
                    mode = mode
                )
            }
            BrowseMode.VOD -> {
                args += accountId.toString()
                args += category.providerId
                buildItemQuery(
                    select = """
                        SELECT ch.id, CAST(ch.accountId AS INTEGER) AS accountId, acc.accountName, cat.id AS categoryRowId,
                            cat.categoryId, cat.title AS categoryTitle, ch.channelId, ch.name, ch.number, ch.cmd,
                            ch.logo, ch.drmType, ch.drmLicenseUrl, ch.clearKeysJson, ch.inputstreamaddon, ch.manifestType,
                            ch.hd, bm.id AS bookmarkId
                        FROM VodChannel ch
                        JOIN Account acc ON CAST(ch.accountId AS INTEGER) = acc.id
                        JOIN VodCategory cat ON cat.accountId = ch.accountId AND cat.categoryId = ch.categoryId
                        LEFT JOIN Bookmark bm ON bm.accountName = acc.accountName AND bm.channelId = ch.channelId AND bm.accountAction = ?
                        WHERE ch.accountId = ? AND ch.categoryId = ?
                    """,
                    query = query,
                    args = args,
                    mode = mode
                )
            }
            BrowseMode.SERIES -> {
                args += accountId.toString()
                args += category.providerId
                buildItemQuery(
                    select = """
                        SELECT ch.id, CAST(ch.accountId AS INTEGER) AS accountId, acc.accountName, cat.id AS categoryRowId,
                            cat.categoryId, cat.title AS categoryTitle, ch.channelId, ch.name, ch.number, ch.cmd,
                            ch.logo, ch.drmType, ch.drmLicenseUrl, ch.clearKeysJson, ch.inputstreamaddon, ch.manifestType,
                            ch.hd, bm.id AS bookmarkId
                        FROM SeriesChannel ch
                        JOIN Account acc ON CAST(ch.accountId AS INTEGER) = acc.id
                        JOIN SeriesCategory cat ON cat.accountId = ch.accountId AND cat.categoryId = ch.categoryId
                        LEFT JOIN Bookmark bm ON bm.accountName = acc.accountName AND bm.channelId = ch.channelId AND bm.accountAction = ?
                        WHERE ch.accountId = ? AND ch.categoryId = ?
                    """,
                    query = query,
                    args = args,
                    mode = mode
                )
            }
        }
        args.add(0, mode.accountAction())
        return db.rawQuery(sql, args.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toBrowseItem(mode))
                }
            }
        }
    }

    private fun buildItemQuery(select: String, query: String, args: MutableList<String>, mode: BrowseMode): String {
        if (query.isNotBlank()) {
            val pattern = "%${query.trim()}%"
            args += pattern
            args += pattern
        }
        return buildString {
            append(select.trimIndent())
            if (query.isNotBlank()) {
                append(" AND (ch.name LIKE ? OR ch.number LIKE ?)")
            }
            append(" ORDER BY CAST(NULLIF(ch.number, '') AS INTEGER), ch.name COLLATE NOCASE")
        }
    }

    private fun categoryByRowId(db: SQLiteDatabase, mode: BrowseMode, rowId: Long): MobileBrowseCategory? {
        val table = when (mode) {
            BrowseMode.LIVE -> "Category"
            BrowseMode.VOD -> "VodCategory"
            BrowseMode.SERIES -> "SeriesCategory"
        }
        return db.rawQuery(
            "SELECT id, categoryId, accountId, title FROM ${quoteIdentifier(table)} WHERE id = ?",
            arrayOf(rowId.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                MobileBrowseCategory(
                    rowId = cursor.long("id"),
                    providerId = cursor.string("categoryId"),
                    accountId = cursor.string("accountId").toLongOrNull() ?: 0,
                    title = cursor.string("title")
                )
            }
        }
    }

    private fun loadVodWatchingNow(db: SQLiteDatabase): List<MobileWatchingNowItem> =
        db.rawQuery(
            """
            SELECT v.id, CAST(v.accountId AS INTEGER) AS accountId, acc.accountName, v.vodName,
                v.categoryId, v.vodId, v.vodCmd, v.vodLogo, v.updatedAt
            FROM VodWatchState v
            LEFT JOIN Account acc ON CAST(v.accountId AS INTEGER) = acc.id
            ORDER BY v.updatedAt DESC
            """.trimIndent(),
            null
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        MobileWatchingNowItem(
                            rowId = cursor.long("id"),
                            accountId = cursor.long("accountId"),
                            accountName = cursor.string("accountName"),
                            mode = BrowseMode.VOD,
                            title = cursor.string("vodName").ifBlank { "Untitled VOD" },
                            subtitle = cursor.string("accountName"),
                            command = cursor.string("vodCmd"),
                            logo = cursor.string("vodLogo"),
                            updatedAtEpochSeconds = cursor.long("updatedAt"),
                            categoryProviderId = cursor.string("categoryId"),
                            contentId = cursor.string("vodId")
                        )
                    )
                }
            }
        }

    private fun loadSeriesWatchingNow(db: SQLiteDatabase): List<MobileWatchingNowItem> =
        db.rawQuery(
            """
            SELECT s.id, CAST(s.accountId AS INTEGER) AS accountId, acc.accountName, s.seriesTitle,
                s.categoryId, s.categoryDbId, s.seriesId, s.seriesPoster, s.updatedAt
            FROM SeriesWatchingNowSnapshot s
            LEFT JOIN Account acc ON CAST(s.accountId AS INTEGER) = acc.id
            ORDER BY s.updatedAt DESC
            """.trimIndent(),
            null
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        MobileWatchingNowItem(
                            rowId = cursor.long("id"),
                            accountId = cursor.long("accountId"),
                            accountName = cursor.string("accountName"),
                            mode = BrowseMode.SERIES,
                            title = cursor.string("seriesTitle").ifBlank { "Untitled Series" },
                            subtitle = cursor.string("accountName"),
                            logo = cursor.string("seriesPoster"),
                            updatedAtEpochSeconds = cursor.long("updatedAt"),
                            categoryProviderId = cursor.string("categoryId"),
                            categoryRowId = cursor.string("categoryDbId").toLongOrNull() ?: 0,
                            contentId = cursor.string("seriesId")
                        )
                    )
                }
            }
        }

    private fun Cursor.toWatchingNowEpisodes(): List<MobileWatchingNowEpisode> {
        val parentRowId = long("id")
        val accountId = long("accountId")
        val accountName = string("accountName")
        val categoryProviderId = string("categoryId")
        val categoryRowId = string("categoryDbId").toLongOrNull() ?: 0
        val seriesId = string("seriesId")
        val seriesTitle = string("seriesTitle").ifBlank { "Untitled Series" }
        val payload = string("episodesJson")
        if (payload.isBlank()) {
            return emptyList()
        }
        return runCatching {
            val array = JSONArray(payload)
            buildList {
                for (index in 0 until array.length()) {
                    val channel = array.optJSONObject(index)
                        ?: runCatching { JSONObject(array.optString(index)) }.getOrNull()
                        ?: continue
                    val episodeId = channel.optString("channelId").ifBlank { channel.optString("id") }
                    add(
                        MobileWatchingNowEpisode(
                            rowId = index.toLong(),
                            parentRowId = parentRowId,
                            accountId = accountId,
                            accountName = accountName,
                            seriesId = seriesId,
                            seriesTitle = seriesTitle,
                            categoryProviderId = categoryProviderId,
                            categoryRowId = categoryRowId,
                            episodeId = episodeId,
                            title = channel.optString("name").ifBlank { channel.optString("title").ifBlank { episodeId } },
                            season = channel.optString("season"),
                            episodeNumber = channel.optString("episodeNum"),
                            command = channel.optString("cmd"),
                            logo = channel.optString("logo"),
                            plot = channel.optString("description"),
                            releaseDate = channel.optString("releaseDate"),
                            rating = channel.optString("rating"),
                            duration = channel.optString("duration")
                        )
                    )
                }
            }.sortedWith(
                compareBy<MobileWatchingNowEpisode> { it.season.toIntOrNull() ?: Int.MAX_VALUE }
                    .thenBy { it.episodeNumber.toIntOrNull() ?: Int.MAX_VALUE }
                    .thenBy { it.title.lowercase() }
            )
        }.getOrDefault(emptyList())
    }

    private fun findBookmarkId(db: SQLiteDatabase, accountName: String, channelId: String, mode: BrowseMode): Long? =
        db.rawQuery(
            """
            SELECT id FROM Bookmark
            WHERE accountName = ? AND channelId = ? AND accountAction = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(accountName, channelId, mode.accountAction())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }

    private fun Cursor.toBrowseItem(mode: BrowseMode): MobileBrowseItem =
        MobileBrowseItem(
            rowId = long("id"),
            accountId = long("accountId"),
            accountName = string("accountName"),
            mode = mode,
            categoryRowId = long("categoryRowId"),
            categoryProviderId = string("categoryId"),
            categoryTitle = string("categoryTitle"),
            channelId = string("channelId"),
            name = string("name").ifBlank { string("channelId") },
            number = string("number"),
            command = string("cmd"),
            logo = string("logo"),
            drmType = string("drmType"),
            drmLicenseUrl = string("drmLicenseUrl"),
            clearKeysJson = string("clearKeysJson"),
            inputstreamAddon = string("inputstreamaddon"),
            manifestType = string("manifestType"),
            isHd = int("hd") == 1,
            isBookmarked = !isNull(getColumnIndexOrThrow("bookmarkId"))
        )

    private fun Cursor.toBookmark(): MobileBookmark {
        val mode = string("accountAction").toBrowseMode()
        val renderData = resolvedBookmarkRenderData(mode)
        return MobileBookmark(
            rowId = long("id"),
            accountId = long("accountId"),
            accountName = string("accountName"),
            bookmarkCategoryId = string("categoryId"),
            categoryTitle = string("categoryTitle"),
            channelId = string("channelId"),
            channelName = string("channelName"),
            command = string("cmd"),
            mode = mode,
            logo = renderData.logo,
            drmType = renderData.drmType,
            drmLicenseUrl = renderData.drmLicenseUrl,
            clearKeysJson = renderData.clearKeysJson,
            inputstreamAddon = renderData.inputstreamaddon,
            manifestType = renderData.manifestType
        )
    }

    private fun Cursor.resolvedBookmarkRenderData(mode: BrowseMode): BookmarkRenderData {
        val data = BookmarkRenderData(
            logo = string("bookmarkLogo"),
            drmType = string("bookmarkDrmType"),
            drmLicenseUrl = string("bookmarkDrmLicenseUrl"),
            clearKeysJson = string("bookmarkClearKeysJson"),
            inputstreamaddon = string("bookmarkInputstreamaddon"),
            manifestType = string("bookmarkManifestType")
        )
        data.mergeChannelJson(string("channelJson"))
        data.mergeChannelJson(string("vodJson"))
        data.mergeSeriesJson(string("seriesJson"))
        data.mergeCachedFallback(this, mode)
        return data
    }

    private fun BookmarkRenderData.mergeCachedFallback(cursor: Cursor, mode: BrowseMode) {
        val prefix = when (mode) {
            BrowseMode.LIVE -> "live"
            BrowseMode.VOD -> "vod"
            BrowseMode.SERIES -> "series"
        }
        fillMissing(
            logo = cursor.string("${prefix}Logo"),
            drmType = cursor.string("${prefix}DrmType"),
            drmLicenseUrl = cursor.string("${prefix}DrmLicenseUrl"),
            clearKeysJson = cursor.string("${prefix}ClearKeysJson"),
            inputstreamaddon = cursor.string("${prefix}Inputstreamaddon"),
            manifestType = cursor.string("${prefix}ManifestType")
        )
    }

    private fun BookmarkRenderData.mergeChannelJson(json: String) {
        val payload = jsonObjectOrNull(json) ?: return
        fillMissing(
            logo = payload.cleanString("logo"),
            drmType = payload.cleanString("drmType"),
            drmLicenseUrl = payload.cleanString("drmLicenseUrl"),
            clearKeysJson = payload.cleanString("clearKeysJson"),
            inputstreamaddon = payload.cleanString("inputstreamaddon"),
            manifestType = payload.cleanString("manifestType")
        )
    }

    private fun BookmarkRenderData.mergeSeriesJson(json: String) {
        val payload = jsonObjectOrNull(json) ?: return
        val info = payload.optJSONObject("info")
        fillMissing(
            logo = info?.firstCleanString(
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
            ).orEmpty().ifBlank {
                payload.firstCleanString(
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
            }
        )
    }

    private fun jsonObjectOrNull(json: String): JSONObject? =
        if (json.isBlank()) null else runCatching { JSONObject(json) }.getOrNull()

    private fun JSONObject.firstCleanString(vararg keys: String): String {
        for (key in keys) {
            val value = cleanString(key)
            if (value.isNotBlank()) {
                return value
            }
        }
        return ""
    }

    private fun JSONObject.cleanString(key: String): String {
        if (!has(key) || isNull(key)) {
            return ""
        }
        val value = optString(key).trim()
        return if (value.isBlank() || value.equals("null", ignoreCase = true) || value.equals("n/a", ignoreCase = true)) {
            ""
        } else {
            value
        }
    }

    private data class BookmarkRenderData(
        var logo: String = "",
        var drmType: String = "",
        var drmLicenseUrl: String = "",
        var clearKeysJson: String = "",
        var inputstreamaddon: String = "",
        var manifestType: String = ""
    ) {
        fun fillMissing(
            logo: String = "",
            drmType: String = "",
            drmLicenseUrl: String = "",
            clearKeysJson: String = "",
            inputstreamaddon: String = "",
            manifestType: String = ""
        ) {
            if (this.logo.isBlank()) this.logo = logo
            if (this.drmType.isBlank()) this.drmType = drmType
            if (this.drmLicenseUrl.isBlank()) this.drmLicenseUrl = drmLicenseUrl
            if (this.clearKeysJson.isBlank()) this.clearKeysJson = clearKeysJson
            if (this.inputstreamaddon.isBlank()) this.inputstreamaddon = inputstreamaddon
            if (this.manifestType.isBlank()) this.manifestType = manifestType
        }
    }

    private fun BrowseMode.accountAction(): String =
        when (this) {
            BrowseMode.LIVE -> "itv"
            BrowseMode.VOD -> "vod"
            BrowseMode.SERIES -> "series"
        }

    private fun String.toBrowseMode(): BrowseMode =
        when {
            equals("vod", ignoreCase = true) -> BrowseMode.VOD
            equals("series", ignoreCase = true) -> BrowseMode.SERIES
            else -> BrowseMode.LIVE
        }

    private fun Cursor.string(column: String): String {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) "" else getString(index).orEmpty()
    }

    private fun String.toMobileAccountTypeOrNull(): MobileAccountType? =
        runCatching { MobileAccountType.valueOf(this) }.getOrNull()

    private fun Cursor.long(column: String): Long {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) 0L else getLong(index)
    }

    private fun Cursor.int(column: String): Int {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) 0 else getInt(index)
    }

    private fun columnExists(db: SQLiteDatabase, table: String, column: String): Boolean {
        db.rawQuery("PRAGMA table_info(${quoteIdentifier(table)})", null).use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex).equals(column, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }
    }

    private fun List<MobileBrowseCategory>.filterByCategoryFilters(filters: FilterConfig): List<MobileBrowseCategory> {
        if (filters.paused || filters.categoryTerms.isEmpty()) {
            return this
        }
        return filter { category ->
            filters.categoryTerms.none { term -> category.title.contains(term, ignoreCase = true) }
        }
    }

    private fun List<MobileBrowseItem>.filterByChannelFilters(filters: FilterConfig): List<MobileBrowseItem> {
        if (filters.paused || filters.channelTerms.isEmpty()) {
            return this
        }
        return filter { item ->
            filters.channelTerms.none { term -> item.name.contains(term, ignoreCase = true) }
        }
    }

    private fun String.csvTerms(): List<String> =
        split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun String.isTruthy(): Boolean =
        equals("1", ignoreCase = true) ||
            equals("true", ignoreCase = true) ||
            equals("yes", ignoreCase = true)

    private fun quoteIdentifier(identifier: String): String =
        "\"" + identifier.replace("\"", "\"\"") + "\""

    private data class FilterConfig(
        val categoryTerms: List<String> = emptyList(),
        val channelTerms: List<String> = emptyList(),
        val paused: Boolean = false
    )
}
