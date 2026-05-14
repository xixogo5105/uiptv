package com.uiptv.mobile.shared.accounts

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidSQLiteAccountRepository(
    private val databaseHelper: AndroidUiptvDatabaseHelper
) : AccountRepository {
    override suspend fun listAccounts(): List<MobileAccount> = withContext(Dispatchers.IO) {
        val db = databaseHelper.writableDatabase
        db.rawQuery(
            """
            SELECT id, accountName, username, password, xtremeCredentialsJson, url,
                macAddress, macAddressList, serialNumber, deviceId1, deviceId2, signature,
                epg, m3u8Path, type, serverPortalUrl, pinToTop,
                resolveChainAndDeepRedirects, httpMethod, timezone
            FROM Account
            ORDER BY CASE WHEN LOWER(COALESCE(pinToTop, '0')) IN ('1', 'true', 'yes') THEN 1 ELSE 0 END DESC,
                id ASC
            """.trimIndent(),
            null
        ).use { cursor ->
            val accounts = mutableListOf<MobileAccount>()
            while (cursor.moveToNext()) {
                accounts += cursor.toMobileAccount()
            }
            accounts
        }
    }

    override suspend fun saveAccount(account: MobileAccount): MobileAccount = withContext(Dispatchers.IO) {
        val normalized = account.normalizedForSave()
        require(normalized.accountName.isNotBlank()) { "Account name is required." }

        val db = databaseHelper.writableDatabase
        val values = normalized.toContentValues()
        db.beginTransaction()
        try {
            val savedId = when (val id = normalized.id) {
                null -> db.insertWithOnConflict("Account", null, values, SQLiteDatabase.CONFLICT_ABORT)
                else -> {
                    db.update("Account", values, "id = ?", arrayOf(id.toString()))
                    id
                }
            }
            db.setTransactionSuccessful()
            normalized.copy(id = savedId)
        } finally {
            db.endTransaction()
        }
    }

    override suspend fun deleteAccount(accountId: Long): Unit = withContext(Dispatchers.IO) {
        val db = databaseHelper.writableDatabase
        db.beginTransaction()
        try {
            deleteAccountRows(db, accountId)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override suspend fun clearAccountCache(accountId: Long): AccountCacheSummary = withContext(Dispatchers.IO) {
        val db = databaseHelper.writableDatabase
        val before = cacheSummaryBlocking(db, accountId)
        db.beginTransaction()
        try {
            clearAccountCacheRows(db, accountId)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        before
    }

    override suspend fun clearAllCache(): AccountCacheSummary = withContext(Dispatchers.IO) {
        val db = databaseHelper.writableDatabase
        val before = allCacheSummaryBlocking(db)
        db.beginTransaction()
        try {
            db.delete("Channel", null, null)
            db.delete("Category", null, null)
            db.delete("VodChannel", null, null)
            db.delete("VodCategory", null, null)
            db.delete("SeriesEpisode", null, null)
            db.delete("SeriesChannel", null, null)
            db.delete("SeriesCategory", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        before
    }

    override suspend fun cacheSummary(accountId: Long): AccountCacheSummary = withContext(Dispatchers.IO) {
        cacheSummaryBlocking(databaseHelper.writableDatabase, accountId)
    }

    private fun deleteAccountRows(db: SQLiteDatabase, accountId: Long) {
        val accountName = db.stringForId("Account", accountId, "accountName")
        clearAccountCacheRows(db, accountId)
        db.delete("AccountInfo", "accountId = ?", arrayOf(accountId.toString()))
        db.delete("VodWatchState", "accountId = ?", arrayOf(accountId.toString()))
        db.delete("SeriesWatchState", "accountId = ?", arrayOf(accountId.toString()))
        db.delete("SeriesWatchingNowSnapshot", "accountId = ?", arrayOf(accountId.toString()))
        db.delete("PublishedM3uSelection", "accountId = ?", arrayOf(accountId.toString()))
        db.delete("PublishedM3uCategorySelection", "accountId = ?", arrayOf(accountId.toString()))
        db.delete("PublishedM3uChannelSelection", "accountId = ?", arrayOf(accountId.toString()))
        if (accountName.isNotBlank()) {
            db.execSQL(
                "DELETE FROM BookmarkOrder WHERE bookmark_db_id IN (SELECT id FROM Bookmark WHERE accountName = ?)",
                arrayOf(accountName)
            )
            db.delete("Bookmark", "accountName = ?", arrayOf(accountName))
        }
        db.delete("Account", "id = ?", arrayOf(accountId.toString()))
    }

    private fun clearAccountCacheRows(db: SQLiteDatabase, accountId: Long) {
        db.execSQL(
            "DELETE FROM Channel WHERE categoryId IN (SELECT id FROM Category WHERE accountId = ?)",
            arrayOf(accountId.toString())
        )
        db.delete("Category", "accountId = ?", arrayOf(accountId.toString()))

        db.execSQL(
            "DELETE FROM VodChannel WHERE categoryId IN (SELECT categoryId FROM VodCategory WHERE accountId = ?)",
            arrayOf(accountId.toString())
        )
        db.delete("VodCategory", "accountId = ?", arrayOf(accountId.toString()))

        db.execSQL(
            "DELETE FROM SeriesEpisode WHERE accountId = ? OR seriesId IN (SELECT channelId FROM SeriesChannel WHERE accountId = ?)",
            arrayOf(accountId.toString(), accountId.toString())
        )
        db.delete("SeriesChannel", "accountId = ?", arrayOf(accountId.toString()))
        db.delete("SeriesCategory", "accountId = ?", arrayOf(accountId.toString()))
    }

    private fun cacheSummaryBlocking(db: SQLiteDatabase, accountId: Long): AccountCacheSummary =
        AccountCacheSummary(
            liveCategories = db.count("Category", "accountId = ?", accountId.toString()),
            liveChannels = db.count(
                "Channel",
                "categoryId IN (SELECT id FROM Category WHERE accountId = ?)",
                accountId.toString()
            ),
            vodCategories = db.count("VodCategory", "accountId = ?", accountId.toString()),
            vodChannels = db.count(
                "VodChannel",
                "categoryId IN (SELECT categoryId FROM VodCategory WHERE accountId = ?)",
                accountId.toString()
            ),
            seriesCategories = db.count("SeriesCategory", "accountId = ?", accountId.toString()),
            seriesChannels = db.count("SeriesChannel", "accountId = ?", accountId.toString()),
            seriesEpisodes = db.count("SeriesEpisode", "accountId = ?", accountId.toString())
        )

    private fun allCacheSummaryBlocking(db: SQLiteDatabase): AccountCacheSummary =
        AccountCacheSummary(
            liveCategories = db.countAll("Category"),
            liveChannels = db.countAll("Channel"),
            vodCategories = db.countAll("VodCategory"),
            vodChannels = db.countAll("VodChannel"),
            seriesCategories = db.countAll("SeriesCategory"),
            seriesChannels = db.countAll("SeriesChannel"),
            seriesEpisodes = db.countAll("SeriesEpisode")
        )

    private fun Cursor.toMobileAccount(): MobileAccount =
        MobileAccount(
            id = getLong(getColumnIndexOrThrow("id")),
            accountName = string("accountName"),
            username = string("username"),
            password = string("password"),
            xtremeCredentialsJson = string("xtremeCredentialsJson"),
            url = string("url"),
            macAddress = string("macAddress"),
            macAddressList = string("macAddressList"),
            serialNumber = string("serialNumber"),
            deviceId1 = string("deviceId1"),
            deviceId2 = string("deviceId2"),
            signature = string("signature"),
            epg = string("epg"),
            m3u8Path = string("m3u8Path"),
            type = runCatching { MobileAccountType.valueOf(string("type")) }
                .getOrDefault(MobileAccountType.STALKER_PORTAL),
            serverPortalUrl = string("serverPortalUrl"),
            pinToTop = booleanString("pinToTop"),
            resolveChainAndDeepRedirects = booleanString("resolveChainAndDeepRedirects"),
            httpMethod = string("httpMethod").ifBlank { "GET" },
            timezone = string("timezone").ifBlank { "Europe/London" }
        )

    private fun MobileAccount.toContentValues(): ContentValues =
        ContentValues().apply {
            put("accountName", accountName)
            put("username", username)
            put("password", password)
            put("xtremeCredentialsJson", xtremeCredentialsJson)
            put("url", url)
            put("macAddress", macAddress)
            put("macAddressList", macAddressList)
            put("serialNumber", serialNumber)
            put("deviceId1", deviceId1)
            put("deviceId2", deviceId2)
            put("signature", signature)
            put("epg", epg)
            put("m3u8Path", m3u8Path)
            put("type", type.name)
            put("serverPortalUrl", serverPortalUrl)
            put("pinToTop", if (pinToTop) "1" else "0")
            put("resolveChainAndDeepRedirects", if (resolveChainAndDeepRedirects) "1" else "0")
            put("httpMethod", httpMethod)
            put("timezone", timezone)
        }

    private fun Cursor.string(column: String): String {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) "" else getString(index).orEmpty()
    }

    private fun Cursor.booleanString(column: String): Boolean =
        string(column) == "1" || string(column).equals("true", ignoreCase = true)

    private fun SQLiteDatabase.stringForId(table: String, id: Long, column: String): String {
        rawQuery(
            "SELECT ${quoteIdentifier(column)} FROM ${quoteIdentifier(table)} WHERE id = ?",
            arrayOf(id.toString())
        ).use { cursor ->
            return if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0).orEmpty() else ""
        }
    }

    private fun SQLiteDatabase.count(table: String, where: String, vararg args: String): Int {
        rawQuery(
            "SELECT COUNT(*) FROM ${quoteIdentifier(table)} WHERE $where",
            args
        ).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    private fun SQLiteDatabase.countAll(table: String): Int {
        rawQuery("SELECT COUNT(*) FROM ${quoteIdentifier(table)}", null).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    private fun quoteIdentifier(identifier: String): String =
        "\"" + identifier.replace("\"", "\"\"") + "\""
}
