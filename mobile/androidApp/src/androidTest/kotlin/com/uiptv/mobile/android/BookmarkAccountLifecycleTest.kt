package com.uiptv.mobile.android

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.uiptv.mobile.shared.accounts.AndroidSQLiteAccountRepository
import com.uiptv.mobile.shared.accounts.MobileAccount
import com.uiptv.mobile.shared.accounts.MobileAccountType
import com.uiptv.mobile.shared.browse.AndroidSQLiteBrowseRepository
import com.uiptv.mobile.shared.browse.BrowseMode
import com.uiptv.mobile.shared.browse.MobileBrowseItem
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BookmarkAccountLifecycleTest {
    @Test fun renamesAndCacheClearsPreserveBookmarksAndOtherAccounts() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(base.cacheDir, "bookmark-lifecycle-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String): File = File(directory, name)
        }
        val helper = AndroidUiptvDatabaseHelper(context)
        try {
            val repository = AndroidSQLiteAccountRepository(helper)
            val first = repository.saveAccount(MobileAccount(accountName = "Before", type = MobileAccountType.XTREME_API))
            val second = repository.saveAccount(MobileAccount(accountName = "Other", type = MobileAccountType.XTREME_API))
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO Bookmark(id,accountName,channelId,channelName) VALUES(1,'Before','provider-id','Station')")
            db.execSQL("INSERT INTO BookmarkOrder(bookmark_db_id,display_order) VALUES('1',1)")
            db.execSQL("INSERT INTO BookmarkPlayHistory(accountName,accountAction,channelId,playCount,lastPlayedAt) VALUES('Before','itv','provider-id',1,1)")
            for (account in listOf(first, second)) {
                db.execSQL("INSERT INTO VodCategory(categoryId,accountId,title) VALUES('shared-category',?,'Movies')", arrayOf(account.id.toString()))
                db.execSQL("INSERT INTO VodChannel(channelId,categoryId,accountId,name) VALUES('shared-movie','shared-category',?,'Movie')", arrayOf(account.id.toString()))
                db.execSQL("INSERT INTO SeriesChannel(channelId,accountId,name) VALUES('shared-series',?,'Series')", arrayOf(account.id.toString()))
                db.execSQL("INSERT INTO SeriesEpisode(channelId,seriesId,accountId,name) VALUES('shared-episode','shared-series',?,'Episode')", arrayOf(account.id.toString()))
            }
            repository.saveAccount(first.copy(accountName = "After"))
            for (table in listOf("Bookmark", "BookmarkPlayHistory")) {
                db.rawQuery("SELECT accountName FROM $table", null).use {
                    assertTrue(it.moveToFirst()); assertEquals("After", it.getString(0))
                }
            }
            repository.clearAccountCache(requireNotNull(first.id))
            for (table in listOf("VodChannel", "SeriesEpisode")) {
                db.rawQuery("SELECT accountId FROM $table", null).use {
                    assertTrue(it.moveToFirst()); assertEquals(second.id.toString(), it.getString(0)); assertFalse(it.moveToNext())
                }
            }
            db.rawQuery("SELECT count(*) FROM Bookmark", null).use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            db.rawQuery("PRAGMA freelist_count", null).use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            repository.deleteAccount(requireNotNull(first.id))
            for (table in listOf("Bookmark", "BookmarkOrder", "BookmarkPlayHistory")) {
                db.rawQuery("SELECT count(*) FROM $table", null).use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            }
            val browse = AndroidSQLiteBrowseRepository(helper)
            browse.toggleBookmark(MobileBrowseItem(rowId = 1, accountId = requireNotNull(second.id), accountName = "Other",
                mode = BrowseMode.LIVE, categoryRowId = 99, categoryProviderId = "provider-source", categoryTitle = "Provider news",
                channelId = "another-channel", name = "Another station", command = "command"))
            db.rawQuery("SELECT categoryId,categoryJson FROM Bookmark", null).use {
                assertTrue(it.moveToFirst()); assertTrue(it.isNull(0))
                assertEquals("provider-source", org.json.JSONObject(it.getString(1)).getString("categoryId"))
            }
        } finally {
            helper.close()
            directory.deleteRecursively()
        }
    }
}
