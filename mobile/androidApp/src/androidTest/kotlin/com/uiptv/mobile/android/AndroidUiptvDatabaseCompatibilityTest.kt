package com.uiptv.mobile.android

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.uiptv.mobile.shared.accounts.AndroidSQLiteAccountRepository
import com.uiptv.mobile.shared.accounts.MobileAccount
import com.uiptv.mobile.shared.accounts.MobileAccountType
import com.uiptv.mobile.shared.browse.AndroidEpisodeMetadata
import com.uiptv.mobile.shared.browse.AndroidBookmarkPlayHistoryStore
import com.uiptv.mobile.shared.browse.AndroidImdbMetadata
import com.uiptv.mobile.shared.browse.AndroidImdbMetadataProvider
import com.uiptv.mobile.shared.browse.AndroidSQLiteBrowseRepository
import com.uiptv.mobile.shared.browse.BrowseMode
import com.uiptv.mobile.shared.browse.MobileBrowseItem
import com.uiptv.mobile.shared.browse.RECENTLY_PLAYED_BOOKMARKS_CATEGORY_ID
import com.uiptv.mobile.shared.browse.MobileWatchingNowEpisode
import com.uiptv.mobile.shared.browse.MobileWatchingNowItem
import com.uiptv.mobile.shared.cache.AndroidM3uCacheReloader
import com.uiptv.mobile.shared.cache.AndroidStalkerCacheReloader
import com.uiptv.mobile.shared.cache.AndroidXtremeCacheReloader
import com.uiptv.mobile.shared.cache.CacheRefreshJobStatus
import com.uiptv.mobile.shared.db.AndroidMigrationSource
import com.uiptv.mobile.shared.db.AndroidSQLiteSnapshotSyncApplier
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import com.uiptv.mobile.shared.db.AndroidUiptvMigrationApplier
import com.uiptv.mobile.shared.db.UiptvSchemaInfo
import com.uiptv.mobile.shared.db.UiptvSyncSchema
import com.uiptv.mobile.shared.playback.PlaybackTarget
import com.uiptv.mobile.shared.settings.AndroidDataStorePreferencesRepository
import com.uiptv.mobile.shared.settings.AndroidPlayerPreference
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidUiptvDatabaseCompatibilityTest {
    private lateinit var context: Context
    private lateinit var helper: AndroidUiptvDatabaseHelper

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(UiptvSchemaInfo.DATABASE_NAME)
        helper = AndroidUiptvDatabaseHelper(context)
    }

    @After
    fun tearDown() {
        helper.close()
        context.deleteDatabase(UiptvSchemaInfo.DATABASE_NAME)
    }

    @Test
    fun createsDesktopCompatibleTablesFromPackagedMigrations() {
        val db = helper.writableDatabase
        val expectedMigrationCount = AndroidMigrationSource(context).migrationNames().size

        (UiptvSyncSchema.androidRequiredTables + UiptvSyncSchema.desktopTablesPreservedButHiddenInV1)
            .forEach { table ->
                assertTrue("Missing table $table", db.tableExists(table))
            }

        assertEquals(expectedMigrationCount, db.countRows("schema_migrations", "status = 'success'"))
        assertEquals(UiptvSyncSchema.configurationColumns.toSet(), db.tableColumns("Configuration").toSet())
        assertTrue(db.tableColumns("Configuration").containsAll(UiptvSyncSchema.androidPortableConfigurationColumns))
    }

    @Test
    fun appliesFixtureSnapshotAsFullDatabaseClone() {
        val snapshot = File.createTempFile("uiptv-sync-fixture-", ".db", context.cacheDir)
        try {
            SQLiteDatabase.openOrCreateDatabase(snapshot, null).use { source ->
                AndroidUiptvMigrationApplier(AndroidMigrationSource(context)).applyAll(source)
                source.execSQL("ALTER TABLE Account ADD COLUMN futureDesktopColumn TEXT")
                source.execSQL("INSERT INTO Account(id, accountName, type, futureDesktopColumn) VALUES(7, 'Desktop Account', 'M3U8_URL', 'ignored')")
                source.execSQL("INSERT INTO AccountInfo(id, accountId, expireDate) VALUES(8, '7', '1893456000')")
                source.execSQL("INSERT INTO Bookmark(id, accountName, channelId, channelName) VALUES(9, 'Desktop Account', 'c1', 'News One')")
                source.execSQL("INSERT INTO BookmarkCategory(id, name) VALUES(10, 'News')")
                source.execSQL("INSERT INTO BookmarkOrder(id, bookmark_db_id, category_id, display_order) VALUES(11, '9', '10', 1)")
                source.execSQL("INSERT INTO Category(id, categoryId, accountId, accountType, title) VALUES(12, 'live-desktop', '7', 'itv', 'Live Desktop')")
                source.execSQL("INSERT INTO Channel(id, channelId, categoryId, name, cmd) VALUES(13, 'live-one', '12', 'Live One', 'http://desktop/live-one')")
                source.execSQL("INSERT INTO Configuration(id, defaultPlayerPath, embeddedPlayer, filterCategoriesList, filterChannelsList, pauseFiltering, cacheExpiryDays, enableThumbnails, publishedM3uCategoryMode, filterLockUnlockDurationMinutes) VALUES(1, '/desktop/player', '1', 'sports,news', 'kids', '1', '9', '1', 'ALL', '30')")
            }
            helper.writableDatabase.execSQL("INSERT INTO Configuration(id, defaultPlayerPath, embeddedPlayer, enableThumbnails) VALUES(1, 'mobile-player', '0', '0')")

            val report = AndroidSQLiteSnapshotSyncApplier(helper, context.cacheDir).apply(snapshot)
            val target = helper.writableDatabase

            assertEquals(8, report.totalRowsSynced)
            assertEquals(1, target.countRows("Account", "id = 7 AND accountName = 'Desktop Account' AND type = 'M3U8_URL'"))
            assertEquals(1, target.countRows("Bookmark", "id = 9 AND channelName = 'News One'"))
            assertEquals(1, target.countRows("Category", "id = 12 AND accountId = '7' AND title = 'Live Desktop'"))
            assertEquals(1, target.countRows("Channel", "id = 13 AND categoryId = '12' AND name = 'Live One'"))
            assertEquals(1, target.countRows("Configuration", "filterCategoriesList = 'sports,news' AND filterChannelsList = 'kids' AND pauseFiltering = '1' AND cacheExpiryDays = '9' AND enableThumbnails = '1' AND publishedM3uCategoryMode = 'ALL' AND filterLockUnlockDurationMinutes = '30'"))
            assertEquals(1, target.countRows("Configuration", "defaultPlayerPath = '/desktop/player' AND embeddedPlayer = '1'"))
            assertTrue(target.tableColumns("Account").contains("futureDesktopColumn"))
        } finally {
            snapshot.delete()
        }
    }

    @Test
    fun accountRepositorySavesListsClearsCacheAndDeletesAccountData() = runBlocking {
        val repository = AndroidSQLiteAccountRepository(helper)
        val saved = repository.saveAccount(
            MobileAccount(
                accountName = "Phone M3U",
                type = MobileAccountType.M3U8_URL,
                m3u8Path = "https://example.test/list.m3u",
                epg = "https://example.test/epg.xml",
                pinToTop = true
            )
        )
        val accountId = requireNotNull(saved.id)
        val db = helper.writableDatabase

        assertEquals(listOf("Phone M3U"), repository.listAccounts().map { it.accountName })

        db.execSQL("INSERT INTO Category(id, categoryId, accountId, accountType, title) VALUES(101, 'live-cat', ?, 'itv', 'Live')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO Channel(id, channelId, categoryId, name) VALUES(201, 'live-1', '101', 'Live One')")
        db.execSQL("INSERT INTO VodCategory(id, categoryId, accountId, title) VALUES(301, 'vod-cat', ?, 'VOD')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO VodChannel(id, channelId, categoryId, accountId, name) VALUES(401, 'vod-1', 'vod-cat', ?, 'Movie')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO SeriesCategory(id, categoryId, accountId, title) VALUES(501, 'series-cat', ?, 'Series')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO SeriesChannel(id, channelId, categoryId, accountId, name) VALUES(601, 'series-1', 'series-cat', ?, 'Series One')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO SeriesEpisode(id, accountId, seriesId, channelId, name) VALUES(701, ?, 'series-1', 'episode-1', 'Episode One')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO Bookmark(id, accountName, channelId, channelName) VALUES(801, 'Phone M3U', 'live-1', 'Live One')")
        db.execSQL("INSERT INTO BookmarkOrder(id, bookmark_db_id, display_order) VALUES(901, '801', 1)")

        val cleared = repository.clearAccountCache(accountId)

        assertEquals(7, cleared.totalItems)
        assertEquals(0, db.countRows("Category", "accountId = '$accountId'"))
        assertEquals(0, db.countRows("Channel", "categoryId = '101'"))
        assertEquals(1, db.countRows("Bookmark", "accountName = 'Phone M3U'"))

        repository.deleteAccount(accountId)

        assertEquals(0, db.countRows("Account", "id = $accountId"))
        assertEquals(0, db.countRows("Bookmark", "accountName = 'Phone M3U'"))
        assertEquals(0, db.countRows("BookmarkOrder", "bookmark_db_id = '801'"))
    }

    @Test
    fun m3uReloaderCreatesLiveCategoriesAndChannelsFromLocalPlaylist() = runBlocking {
        val playlist = File.createTempFile("uiptv-playlist-", ".m3u", context.cacheDir).apply {
            writeText(
                """
                #EXTM3U
                #EXTINF:-1 tvg-id="news-1" tvg-logo="https://logo.test/news.png" group-title="News",News One HD
                https://stream.test/news.m3u8
                #EXTINF:-1 group-title="Sports",Sports One
                https://stream.test/sports.ts
                #EXTINF:-1 group-title="DRM",Widevine Dash
                #KODIPROP:inputstreamaddon=inputstream.adaptive
                #KODIPROP:inputstream.adaptive.manifest_type=mpd
                #KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
                #KODIPROP:inputstream.adaptive.license_key=https://license.test/widevine
                https://stream.test/widevine.mpd
                #EXTINF:-1 group-title="DRM",ClearKey Dash
                #KODIPROP:inputstream.adaptive.license_type=clearkey
                #KODIPROP:inputstream.adaptive.license_key=00112233445566778899aabbccddeeff:ffeeddccbbaa99887766554433221100
                https://stream.test/clearkey.mpd
                """.trimIndent()
            )
        }
        try {
            val repository = AndroidSQLiteAccountRepository(helper)
            val account = repository.saveAccount(
                MobileAccount(
                    accountName = "Local Playlist",
                    type = MobileAccountType.M3U8_LOCAL,
                    m3u8Path = playlist.absolutePath
                )
            )

            val result = AndroidM3uCacheReloader(context, helper).refreshAccount(requireNotNull(account.id))
            val db = helper.writableDatabase

            assertEquals(CacheRefreshJobStatus.SUCCEEDED, result.status)
            assertEquals(4, db.countRows("Category", "accountId = '${account.id}'"))
            assertEquals(8, db.countRows("Channel", "cmd LIKE 'https://stream.test/%'"))
            assertEquals(2, db.countRows("Channel", "name = 'News One HD'"))
            assertEquals(2, db.countRows("Channel", "name = 'Sports One'"))
            assertEquals("com.widevine.alpha", db.singleString("Channel", "drmType", "name = 'Widevine Dash'"))
            assertEquals("https://license.test/widevine", db.singleString("Channel", "drmLicenseUrl", "name = 'Widevine Dash'"))
            assertEquals("inputstream.adaptive", db.singleString("Channel", "inputstreamaddon", "name = 'Widevine Dash'"))
            assertEquals("mpd", db.singleString("Channel", "manifestType", "name = 'Widevine Dash'"))
            assertEquals("org.w3.clearkey", db.singleString("Channel", "drmType", "name = 'ClearKey Dash'"))
            assertTrue(db.singleString("Channel", "clearKeysJson", "name = 'ClearKey Dash'").contains("00112233445566778899aabbccddeeff"))
        } finally {
            playlist.delete()
        }
    }

    @Test
    fun xtremeReloaderCreatesLiveCategoriesAndChannelsFromPlayerApi() = runBlocking {
        TestHttpServer(
            mapOf(
                "get_live_categories" to """
                    [
                      {"category_id":"10","category_name":"News"}
                    ]
                """.trimIndent(),
                "get_live_streams" to """
                    [
                      {"stream_id":"77","name":"World News HD","category_id":"10","stream_icon":"https://logo.test/news.png","container_extension":"m3u8"},
                      {"stream_id":"88","name":"Orphan Sports","category_id":"999","stream_icon":"","container_extension":"ts"}
                    ]
                """.trimIndent(),
                "get_vod_categories" to """
                    [
                      {"category_id":"20","category_name":"Movies"}
                    ]
                """.trimIndent(),
                "get_series_categories" to """
                    [
                      {"category_id":"30","category_name":"Shows"}
                    ]
                """.trimIndent()
            )
        ).use { server ->
            val repository = AndroidSQLiteAccountRepository(helper)
            val account = repository.saveAccount(
                MobileAccount(
                    accountName = "Xtreme",
                    type = MobileAccountType.XTREME_API,
                    url = "http://127.0.0.1:${server.port}/player_api.php?legacy=true",
                    username = "phone user",
                    password = "p@ss"
                )
            )

            val result = AndroidXtremeCacheReloader(helper).refreshAccount(requireNotNull(account.id))
            val db = helper.writableDatabase

            assertEquals(CacheRefreshJobStatus.SUCCEEDED, result.status)
            assertEquals(3, db.countRows("Category", "accountId = '${account.id}'"))
            assertEquals(4, db.countRows("Channel", "cmd LIKE 'http://127.0.0.1:%'"))
            assertEquals(2, db.countRows("Channel", "name = 'World News HD'"))
            assertEquals(2, db.countRows("Channel", "name = 'Orphan Sports'"))
            assertEquals(4, db.countRows("Channel", "cmd LIKE '%phone%20user/p%40ss/%'"))
            assertEquals(1, db.countRows("VodCategory", "accountId = '${account.id}' AND categoryId = '20'"))
            assertEquals(1, db.countRows("SeriesCategory", "accountId = '${account.id}' AND categoryId = '30'"))
            assertEquals(0, db.countRows("VodChannel", "accountId = '${account.id}'"))
            assertEquals(0, db.countRows("SeriesChannel", "accountId = '${account.id}'"))
        }
    }

    @Test
    fun stalkerReloaderCreatesLiveCategoriesAndChannelsFromPortalApi() = runBlocking {
        TestHttpServer(
            mapOf(
                "handshake" to """{"js":{"token":"abc123"}}""",
                "get_profile" to """{"js":{}}""",
                "get_genres" to """
                    {"js":[
                      {"id":"1","title":"News","alias":"News","active_sub":false,"censored":0}
                    ]}
                """.trimIndent(),
                "get_all_channels" to """
                    {"js":{"data":[
                      {"id":"100","name":"Portal News HD","number":"1","cmd":"ffmpeg http://stream.test/news","cmd_1":"","cmd_2":"","cmd_3":"","logo":"https://logo.test/news.png","censored":0,"status":1,"hd":1,"tv_genre_id":"1"},
                      {"id":"200","name":"Portal Orphan","number":"2","cmd":"ffmpeg http://stream.test/orphan","cmd_1":"","cmd_2":"","cmd_3":"","logo":"","censored":0,"status":1,"hd":0,"tv_genre_id":"999"}
                    ]}}
                """.trimIndent(),
                "get_categories" to """
                    {"js":[
                      {"id":"2","title":"Portal Movies","alias":"Portal Movies","active_sub":false,"censored":0}
                    ]}
                """.trimIndent()
            )
        ).use { server ->
            val repository = AndroidSQLiteAccountRepository(helper)
            val account = repository.saveAccount(
                MobileAccount(
                    accountName = "Portal",
                    type = MobileAccountType.STALKER_PORTAL,
                    url = "http://127.0.0.1:${server.port}",
                    macAddress = "00:1A:79:00:00:01"
                )
            )

            val result = AndroidStalkerCacheReloader(helper).refreshAccount(requireNotNull(account.id))
            val db = helper.writableDatabase

            assertEquals(CacheRefreshJobStatus.SUCCEEDED, result.status)
            assertEquals(3, db.countRows("Category", "accountId = '${account.id}'"))
            assertEquals(4, db.countRows("Channel", "cmd LIKE 'ffmpeg http://stream.test/%'"))
            assertEquals(2, db.countRows("Channel", "name = 'Portal News HD'"))
            assertEquals(2, db.countRows("Channel", "name = 'Portal Orphan'"))
            assertEquals(1, db.countRows("VodCategory", "accountId = '${account.id}' AND categoryId = '2'"))
            assertEquals(1, db.countRows("SeriesCategory", "accountId = '${account.id}' AND categoryId = '2'"))
            assertEquals(0, db.countRows("VodChannel", "accountId = '${account.id}'"))
            assertEquals(0, db.countRows("SeriesChannel", "accountId = '${account.id}'"))
        }
    }

    @Test
    fun stalkerReloaderDefersLiveChannelsWhenGetAllChannelsIsEmpty() = runBlocking {
        TestHttpServer(
            mapOf(
                "handshake" to """{"js":{"token":"abc123"}}""",
                "get_profile" to """{"js":{}}""",
                "get_genres" to """
                    {"js":[
                      {"id":"1","title":"News","alias":"News","active_sub":false,"censored":0}
                    ]}
                """.trimIndent(),
                "get_all_channels" to """{"js":{"data":[]}}""",
                "get_categories" to """{"js":[]}""",
                "get_ordered_list" to """
                    {"js":{"data":[
                      {"id":"100","name":"Deferred News HD","number":"1","cmd":"ffmpeg http://stream.test/deferred-news","cmd_1":"","cmd_2":"","cmd_3":"","logo":"https://logo.test/news.png","censored":0,"status":1,"hd":1,"tv_genre_id":"1"}
                    ]}}
                """.trimIndent()
            )
        ).use { server ->
            val repository = AndroidSQLiteAccountRepository(helper)
            val account = repository.saveAccount(
                MobileAccount(
                    accountName = "Deferred Portal",
                    type = MobileAccountType.STALKER_PORTAL,
                    url = "http://127.0.0.1:${server.port}",
                    macAddress = "00:1A:79:00:00:01"
                )
            )
            val accountId = requireNotNull(account.id)

            val result = AndroidStalkerCacheReloader(helper).refreshAccount(accountId)
            val db = helper.writableDatabase

            assertEquals(CacheRefreshJobStatus.SUCCEEDED, result.status)
            assertTrue(result.message.contains("will load when a category is opened"))
            assertEquals(2, db.countRows("Category", "accountId = '$accountId'"))
            assertEquals(0, db.countRows("Channel", "1 = 1"))
            assertFalse(server.requests.any { it.contains("action=get_ordered_list") })

            val newsCategoryRowId = db.singleString("Category", "id", "accountId = '$accountId' AND categoryId = '1'").toLong()
            val snapshot = AndroidSQLiteBrowseRepository(helper).loadBrowse(accountId, BrowseMode.LIVE, newsCategoryRowId, "")

            assertEquals("Deferred News HD", snapshot.items.single().name)
            assertEquals(1, db.countRows("Channel", "categoryId = '$newsCategoryRowId'"))
            assertTrue(server.requests.any { it.contains("action=get_ordered_list") && it.contains("genre=1") })
        }
    }

    @Test
    fun browseRepositoryFetchesVodSeriesContentsAndWatchingNowEpisodesOnDemand() = runBlocking {
        TestHttpServer(
            mapOf(
                "get_vod_streams" to """
                    [
                      {"stream_id":"501","name":"Remote Movie","category_id":"movies","stream_icon":"https://logo.test/movie.jpg","container_extension":"mp4"}
                    ]
                """.trimIndent(),
                "get_series" to """
                    [
                      {"series_id":"show-1","name":"Remote Show","category_id":"shows","cover":"https://logo.test/show.jpg"}
                    ]
                """.trimIndent(),
                "get_series_info" to """
                    {
                      "info":{"name":"Remote Show"},
                      "episodes":{
                        "1":[
                          {"id":"900","episode_num":"1","title":"Pilot","container_extension":"mkv","season":"1","info":{"movie_image":"https://logo.test/pilot.jpg","plot":"Intro"}}
                        ]
                      }
                    }
                """.trimIndent()
            )
        ).use { server ->
            val account = AndroidSQLiteAccountRepository(helper).saveAccount(
                MobileAccount(
                    accountName = "On Demand Xtreme",
                    type = MobileAccountType.XTREME_API,
                    url = "http://127.0.0.1:${server.port}",
                    username = "u",
                    password = "p"
                )
            )
            val accountId = requireNotNull(account.id)
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO VodCategory(id, categoryId, accountId, accountType, title) VALUES(2101, 'movies', ?, 'vod', 'Movies')", arrayOf(accountId.toString()))
            db.execSQL("INSERT INTO SeriesCategory(id, categoryId, accountId, accountType, title) VALUES(2102, 'shows', ?, 'series', 'Shows')", arrayOf(accountId.toString()))
            db.execSQL("INSERT INTO SeriesWatchState(id, accountId, mode, categoryId, seriesId, episodeId, episodeName, updatedAt) VALUES(2103, ?, 'series', 'shows', 'show-1', '900', 'Pilot', 222)", arrayOf(accountId.toString()))
            db.execSQL("INSERT INTO SeriesEpisode(id, accountId, categoryId, seriesId, channelId, name, cmd, season, episodeNum) VALUES(2104, ?, 'shows', 'show-1', '900', 'Stale Pilot', '', '1', '1')", arrayOf(accountId.toString()))

            val repository = AndroidSQLiteBrowseRepository(helper)
            val vodCategoriesSnapshot = repository.loadBrowse(accountId, BrowseMode.VOD, null, "")
            val seriesCategoriesSnapshot = repository.loadBrowse(accountId, BrowseMode.SERIES, null, "")

            assertEquals(null, vodCategoriesSnapshot.selectedCategoryRowId)
            assertEquals(emptyList<MobileBrowseItem>(), vodCategoriesSnapshot.items)
            assertEquals(null, seriesCategoriesSnapshot.selectedCategoryRowId)
            assertEquals(emptyList<MobileBrowseItem>(), seriesCategoriesSnapshot.items)
            assertEquals(emptyList<String>(), server.requests.toList())

            val vodSnapshot = repository.loadBrowse(accountId, BrowseMode.VOD, 2101, "")
            val seriesSnapshot = repository.loadBrowse(accountId, BrowseMode.SERIES, 2102, "")
            val watchingNow = repository.listWatchingNow("")
            val episodes = repository.listWatchingNowEpisodes(watchingNow.single { it.contentId == "show-1" })

            assertEquals("Remote Movie", vodSnapshot.items.single().name)
            assertEquals(1, db.countRows("VodChannel", "accountId = '$accountId' AND categoryId = 'movies'"))
            assertEquals("Remote Show", seriesSnapshot.items.single().name)
            assertEquals(1, db.countRows("SeriesChannel", "accountId = '$accountId' AND categoryId = 'shows'"))
            assertEquals("Remote Show", watchingNow.single { it.contentId == "show-1" }.title)
            assertEquals("Pilot", episodes.single().title)
            assertTrue(episodes.single().command.contains("/series/u/p/900.mkv"))
            assertEquals(1, db.countRows("SeriesEpisode", "accountId = '$accountId' AND seriesId = 'show-1'"))
            assertTrue(server.requests.any { it.contains("action=get_vod_streams") && it.contains("category_id=movies") })
            assertTrue(server.requests.any { it.contains("action=get_series") && it.contains("category_id=shows") })
        }
    }

    @Test
    fun browseRepositoryLoadsChannelsBookmarksAndWatchingNow() = runBlocking {
        val account = AndroidSQLiteAccountRepository(helper).saveAccount(
            MobileAccount(
                accountName = "Browse Account",
                type = MobileAccountType.M3U8_URL,
                m3u8Path = "https://example.test/list.m3u"
            )
        )
        val accountId = requireNotNull(account.id)
        val db = helper.writableDatabase
        db.execSQL("INSERT INTO Category(id, categoryId, accountId, accountType, title) VALUES(1101, 'all', ?, 'itv', 'All')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO Category(id, categoryId, accountId, accountType, title) VALUES(1102, 'news', ?, 'itv', 'News')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO Channel(id, channelId, categoryId, name, number, cmd, hd) VALUES(1201, 'n1', '1101', 'News One HD', '1', 'https://stream.test/news', 1)")
        db.execSQL("INSERT INTO Channel(id, channelId, categoryId, name, number, cmd, hd) VALUES(1202, 'n1', '1102', 'News One HD', '1', 'https://stream.test/news', 1)")
        db.execSQL("INSERT INTO VodWatchState(id, accountId, categoryId, vodId, vodName, vodCmd, vodLogo, updatedAt) VALUES(1301, ?, 'movies', 'm1', 'Resume Movie', 'https://stream.test/movie', '', 99)", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO SeriesWatchingNowSnapshot(id, accountId, categoryId, seriesId, seriesTitle, seriesPoster, updatedAt) VALUES(1401, ?, 'series', 's1', 'Resume Series', '', 100)", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO SeriesWatchingNowSnapshot(id, accountId, categoryId, seriesId, seriesTitle, seriesPoster, updatedAt) VALUES(1402, ?, 'series', 's1', 'Resume Series Duplicate', '', 98)", arrayOf(accountId.toString()))

        val repository = AndroidSQLiteBrowseRepository(helper)
        val snapshot = repository.loadBrowse(accountId, BrowseMode.LIVE, 1102, "News")

        assertEquals(listOf("News"), snapshot.categories.filter { it.rowId == 1102L }.map { it.title })
        assertEquals(1, snapshot.items.size)
        assertEquals("News One HD", snapshot.items.single().name)
        assertFalse(snapshot.items.single().isBookmarked)

        assertTrue(repository.toggleBookmark(snapshot.items.single()))
        val bookmarkedSnapshot = repository.loadBrowse(accountId, BrowseMode.LIVE, 1102, "News")
        assertTrue(bookmarkedSnapshot.items.single().isBookmarked)
        val bookmarks = repository.listBookmarks("News", null)
        assertEquals(1, bookmarks.size)
        assertEquals("Browse Account", bookmarks.single().accountName)

        repository.removeBookmark(bookmarks.single().rowId)

        assertEquals(0, repository.listBookmarks("News", null).size)
        assertEquals(listOf("Resume Series", "Resume Movie"), repository.listWatchingNow("").map { it.title })
    }

    @Test
    fun browseRepositoryClearsSingleRecentlyPlayedBookmarkWithoutRemovingFavourite() = runBlocking {
        val account = AndroidSQLiteAccountRepository(helper).saveAccount(
            MobileAccount(
                accountName = "Recent Account",
                type = MobileAccountType.M3U8_URL,
                m3u8Path = "https://example.test/list.m3u"
            )
        )
        val accountId = requireNotNull(account.id)
        val db = helper.writableDatabase
        db.execSQL("INSERT INTO Category(id, categoryId, accountId, accountType, title) VALUES(1501, 'all', ?, 'itv', 'All')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO Channel(id, channelId, categoryId, name, number, cmd, hd) VALUES(1502, 'recent-1', '1501', 'Recent News', '1', 'https://stream.test/recent', 1)")

        val repository = AndroidSQLiteBrowseRepository(helper)
        val item = repository.loadBrowse(accountId, BrowseMode.LIVE, 1501, "").items.single()
        assertTrue(repository.toggleBookmark(item))
        val bookmark = repository.listBookmarks("", null).single()
        AndroidBookmarkPlayHistoryStore(db).record(bookmark)

        assertEquals(listOf("Recent News"), repository.listBookmarks("", RECENTLY_PLAYED_BOOKMARKS_CATEGORY_ID).map { it.channelName })
        assertEquals(1, repository.listBookmarkCategories().single { it.id == RECENTLY_PLAYED_BOOKMARKS_CATEGORY_ID }.itemCount)

        repository.removeRecentlyPlayedBookmark(bookmark)

        assertEquals(emptyList<String>(), repository.listBookmarks("", RECENTLY_PLAYED_BOOKMARKS_CATEGORY_ID).map { it.channelName })
        assertEquals(0, repository.listBookmarkCategories().single { it.id == RECENTLY_PLAYED_BOOKMARKS_CATEGORY_ID }.itemCount)
        assertEquals(listOf("Recent News"), repository.listBookmarks("", null).map { it.channelName })
    }

    @Test
    fun browseRepositoryRemovesSelectedCachedCategoriesOnly() = runBlocking {
        val account = AndroidSQLiteAccountRepository(helper).saveAccount(
            MobileAccount(
                accountName = "Removal Account",
                type = MobileAccountType.XTREME_API,
                url = "https://example.test",
                username = "u",
                password = "p"
            )
        )
        val accountId = requireNotNull(account.id)
        val db = helper.writableDatabase
        db.execSQL("INSERT INTO Category(id, categoryId, accountId, accountType, title) VALUES(2101, 'live-remove', ?, 'itv', 'Live Remove')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO Category(id, categoryId, accountId, accountType, title) VALUES(2102, 'live-keep', ?, 'itv', 'Live Keep')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO Channel(id, channelId, categoryId, name) VALUES(2201, 'live-1', '2101', 'Live One')")
        db.execSQL("INSERT INTO Channel(id, channelId, categoryId, name) VALUES(2202, 'live-2', '2102', 'Live Two')")
        db.execSQL("INSERT INTO VodCategory(id, categoryId, accountId, accountType, title) VALUES(2301, 'vod-remove', ?, 'vod', 'Vod Remove')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO VodCategory(id, categoryId, accountId, accountType, title) VALUES(2302, 'vod-keep', ?, 'vod', 'Vod Keep')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO VodChannel(id, channelId, categoryId, accountId, name) VALUES(2401, 'vod-1', 'vod-remove', ?, 'Movie One')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO VodChannel(id, channelId, categoryId, accountId, name) VALUES(2402, 'vod-2', 'vod-keep', ?, 'Movie Two')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO SeriesCategory(id, categoryId, accountId, accountType, title) VALUES(2501, 'series-remove', ?, 'series', 'Series Remove')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO SeriesCategory(id, categoryId, accountId, accountType, title) VALUES(2502, 'series-keep', ?, 'series', 'Series Keep')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO SeriesChannel(id, channelId, categoryId, accountId, name) VALUES(2601, 'series-1', 'series-remove', ?, 'Series One')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO SeriesChannel(id, channelId, categoryId, accountId, name) VALUES(2602, 'series-2', 'series-keep', ?, 'Series Two')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO SeriesEpisode(id, accountId, categoryId, seriesId, channelId, name) VALUES(2701, ?, 'series-remove', 'series-1', 'episode-1', 'Episode One')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO SeriesEpisode(id, accountId, categoryId, seriesId, channelId, name) VALUES(2702, ?, 'series-keep', 'series-2', 'episode-2', 'Episode Two')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO Bookmark(id, accountName, categoryId, channelId, channelName) VALUES(2801, 'Removal Account', 'live-remove', 'live-1', 'Live One')")
        db.execSQL("INSERT INTO VodWatchState(id, accountId, categoryId, vodId, vodName) VALUES(2901, ?, 'vod-remove', 'vod-1', 'Movie One')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO SeriesWatchState(id, accountId, mode, categoryId, seriesId, episodeId, episodeName) VALUES(3001, ?, 'series', 'series-remove', 'series-1', 'episode-1', 'Episode One')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO SeriesWatchingNowSnapshot(id, accountId, categoryId, seriesId, seriesTitle) VALUES(3002, ?, 'series-remove', 'series-1', 'Series One')", arrayOf(accountId.toString()))

        val repository = AndroidSQLiteBrowseRepository(helper)
        val liveResult = repository.removeCachedCategories(accountId, BrowseMode.LIVE, setOf(2101L))
        val vodResult = repository.removeCachedCategories(accountId, BrowseMode.VOD, setOf(2301L))
        val seriesResult = repository.removeCachedCategories(accountId, BrowseMode.SERIES, setOf(2501L))

        assertEquals(1, liveResult.removedCategoryCount)
        assertEquals(1, liveResult.removedItemCount)
        assertEquals(1, vodResult.removedCategoryCount)
        assertEquals(1, vodResult.removedItemCount)
        assertEquals(1, seriesResult.removedCategoryCount)
        assertEquals(2, seriesResult.removedItemCount)
        assertEquals(0, db.countRows("Channel", "categoryId = '2101'"))
        assertEquals(1, db.countRows("Channel", "categoryId = '2102'"))
        assertEquals(0, db.countRows("VodChannel", "accountId = '$accountId' AND categoryId = 'vod-remove'"))
        assertEquals(1, db.countRows("VodChannel", "accountId = '$accountId' AND categoryId = 'vod-keep'"))
        assertEquals(0, db.countRows("SeriesChannel", "accountId = '$accountId' AND categoryId = 'series-remove'"))
        assertEquals(1, db.countRows("SeriesChannel", "accountId = '$accountId' AND categoryId = 'series-keep'"))
        assertEquals(0, db.countRows("SeriesEpisode", "accountId = '$accountId' AND categoryId = 'series-remove'"))
        assertEquals(1, db.countRows("SeriesEpisode", "accountId = '$accountId' AND categoryId = 'series-keep'"))
        assertEquals(1, db.countRows("Bookmark", "accountName = 'Removal Account'"))
        assertEquals(1, db.countRows("VodWatchState", "accountId = '$accountId' AND categoryId = 'vod-remove'"))
        assertEquals(1, db.countRows("SeriesWatchState", "accountId = '$accountId' AND categoryId = 'series-remove'"))
        assertEquals(1, db.countRows("SeriesWatchingNowSnapshot", "accountId = '$accountId' AND categoryId = 'series-remove'"))
    }

    @Test
    fun playbackCoordinatorPersistsPreferenceAndMarksWatchingNow() = runBlocking {
        val account = AndroidSQLiteAccountRepository(helper).saveAccount(
            MobileAccount(
                accountName = "Playback Account",
                type = MobileAccountType.XTREME_API,
                url = "https://example.test",
                username = "u",
                password = "p"
            )
        )
        val accountId = requireNotNull(account.id)
        val startedIntents = mutableListOf<Intent>()
        val coordinator = AndroidPlaybackCoordinator(
            context = context,
            preferences = AndroidDataStorePreferencesRepository(context),
            databaseHelper = helper,
            epochSeconds = { 1234 },
            activityStarter = { startedIntents += it }
        )

        val vodResult = coordinator.playBrowseItem(
            playbackItem(accountId, BrowseMode.VOD, "vod-cat", "vod-1", "Movie One"),
            AndroidPlayerPreference.SYSTEM_CHOOSER,
            remember = true
        )
        val seriesResult = coordinator.playBrowseItem(
            playbackItem(accountId, BrowseMode.SERIES, "series-cat", "series-1", "Series One"),
            AndroidPlayerPreference.SYSTEM_CHOOSER,
            remember = false
        )

        assertTrue(vodResult.launched)
        assertTrue(seriesResult.launched)
        assertEquals(AndroidPlayerPreference.SYSTEM_CHOOSER, coordinator.loadPlayerPreference().selectedPlayer)
        assertEquals(1, helper.writableDatabase.countRows("VodWatchState", "accountId = '$accountId' AND vodId = 'vod-1'"))
        assertEquals("https://stream.test/vod-1.m3u8", helper.writableDatabase.singleString("VodWatchState", "vodCmd", "accountId = '$accountId' AND vodId = 'vod-1'"))
        assertEquals(1, helper.writableDatabase.countRows("SeriesWatchingNowSnapshot", "accountId = '$accountId' AND seriesId = 'series-1'"))

        val embeddedResult = coordinator.playBrowseItem(
            playbackItem(accountId, BrowseMode.VOD, "vod-cat", "vod-embedded", "Embedded Movie"),
            AndroidPlayerPreference.EMBEDDED_PLAYER,
            remember = false
        )

        assertTrue(embeddedResult.launched)
        assertEquals(0, helper.writableDatabase.countRows("VodWatchState", "accountId = '$accountId' AND vodId = 'vod-embedded'"))
        assertEquals(MpvEmbeddedPlayerActivity::class.java.name, startedIntents.last().component?.className)

        val nativeResult = coordinator.playBrowseItem(
            playbackItem(accountId, BrowseMode.VOD, "vod-cat", "vod-native", "Native Movie"),
            AndroidPlayerPreference.NATIVE,
            remember = false
        )

        assertTrue(nativeResult.launched)
        assertEquals(0, helper.writableDatabase.countRows("VodWatchState", "accountId = '$accountId' AND vodId = 'vod-native'"))
        val nativeIntent = startedIntents.last()
        assertEquals(accountId, nativeIntent.getLongExtra(NativePlayerActivity.EXTRA_ACCOUNT_ID, 0L))
        assertEquals(BrowseMode.VOD.name, nativeIntent.getStringExtra(NativePlayerActivity.EXTRA_MODE))
        assertEquals("vod-native", nativeIntent.getStringExtra(NativePlayerActivity.EXTRA_CHANNEL_ID))

        AndroidPlaybackWatchStateStore(helper, epochSeconds = { 4321 }).markOpened(
            PlaybackTarget(
                accountId = accountId,
                accountName = "Playback Account",
                mode = BrowseMode.VOD,
                categoryProviderId = "vod-cat",
                categoryRowId = 9,
                channelId = "vod-native",
                title = "Native Movie",
                url = "https://stream.test/vod-native.m3u8"
            )
        )
        assertEquals(1, helper.writableDatabase.countRows("VodWatchState", "accountId = '$accountId' AND vodId = 'vod-native'"))

        val drmResult = coordinator.playBrowseItem(
            playbackItem(accountId, BrowseMode.VOD, "vod-cat", "vod-2", "DRM Movie", command = "https://stream.test/movie.mpd", drmType = "widevine"),
            AndroidPlayerPreference.NATIVE,
            remember = false
        )

        assertFalse(drmResult.launched)
        assertTrue(drmResult.message.contains("DRM"))
        assertEquals(0, helper.writableDatabase.countRows("VodWatchState", "accountId = '$accountId' AND vodId = 'vod-2'"))

        val licensedDrmResult = coordinator.playBrowseItem(
            playbackItem(
                accountId,
                BrowseMode.VOD,
                "vod-cat",
                "vod-3",
                "Licensed DRM Movie",
                command = "https://stream.test/movie.mpd",
                drmType = "widevine",
                drmLicenseUrl = "https://license.test/widevine",
                inputstreamAddon = "inputstream.adaptive",
                manifestType = "mpd"
            ),
            AndroidPlayerPreference.NATIVE,
            remember = false
        )

        assertTrue(licensedDrmResult.launched)
        assertEquals(0, helper.writableDatabase.countRows("VodWatchState", "accountId = '$accountId' AND vodId = 'vod-3'"))
        assertEquals("widevine", startedIntents.last().getStringExtra(NativePlayerActivity.EXTRA_DRM_TYPE))
        assertEquals("https://license.test/widevine", startedIntents.last().getStringExtra(NativePlayerActivity.EXTRA_DRM_LICENSE_URL))
        assertEquals("inputstream.adaptive", startedIntents.last().getStringExtra(NativePlayerActivity.EXTRA_INPUTSTREAM_ADDON))
        assertEquals("mpd", startedIntents.last().getStringExtra(NativePlayerActivity.EXTRA_MANIFEST_TYPE))

        val clearKeyResult = coordinator.playBrowseItem(
            playbackItem(
                accountId,
                BrowseMode.VOD,
                "vod-cat",
                "vod-4",
                "ClearKey Movie",
                command = "https://stream.test/clearkey.mpd",
                drmType = "org.w3.clearkey",
                clearKeysJson = "{\"00112233445566778899aabbccddeeff\":\"ffeeddccbbaa99887766554433221100\"}",
                inputstreamAddon = "inputstream.adaptive",
                manifestType = "mpd"
            ),
            AndroidPlayerPreference.NATIVE,
            remember = false
        )

        assertTrue(clearKeyResult.launched)
        assertEquals("org.w3.clearkey", startedIntents.last().getStringExtra(NativePlayerActivity.EXTRA_DRM_TYPE))
        assertTrue(startedIntents.last().getStringExtra(NativePlayerActivity.EXTRA_CLEAR_KEYS_JSON).orEmpty().contains("00112233445566778899aabbccddeeff"))
        assertEquals("application/dash+xml", startedIntents.last().getStringExtra(NativePlayerActivity.EXTRA_MIME_TYPE))

        val embeddedDrmResult = coordinator.playBrowseItem(
            playbackItem(
                accountId,
                BrowseMode.VOD,
                "vod-cat",
                "vod-5",
                "Embedded DRM Movie",
                command = "https://stream.test/embedded-drm.mpd",
                drmType = "widevine",
                drmLicenseUrl = "https://license.test/widevine"
            ),
            AndroidPlayerPreference.EMBEDDED_PLAYER,
            remember = false
        )

        assertTrue(embeddedDrmResult.launched)
        assertEquals(NativePlayerActivity::class.java.name, startedIntents.last().component?.className)

        coordinator.clearPlayerPreference()

        assertEquals(AndroidPlayerPreference.EMBEDDED_PLAYER, coordinator.loadPlayerPreference().selectedPlayer)
    }

    @Test
    fun playbackCoordinatorSanitizesAdsQueryParamsLikeDesktop() = runBlocking {
        val account = AndroidSQLiteAccountRepository(helper).saveAccount(
            MobileAccount(
                accountName = "Ads Account",
                type = MobileAccountType.M3U8_URL,
                m3u8Path = "https://example.test/list.m3u"
            )
        )
        val accountId = requireNotNull(account.id)
        val startedIntents = mutableListOf<Intent>()
        val coordinator = AndroidPlaybackCoordinator(
            context = context,
            preferences = AndroidDataStorePreferencesRepository(context),
            databaseHelper = helper,
            activityStarter = { startedIntents += it }
        )

        val result = coordinator.playBrowseItem(
            playbackItem(
                accountId = accountId,
                mode = BrowseMode.LIVE,
                categoryId = "news",
                channelId = "ads-1",
                title = "Ads Stream",
                command = "https://cdn.test/playlist.m3u8?ads.deviceid=[DEVICE_ID]&ads.ifa=[IFA]&coppa=0"
            ),
            AndroidPlayerPreference.EMBEDDED_PLAYER,
            remember = false
        )

        assertTrue(result.launched)
        assertEquals(
            "https://cdn.test/playlist.m3u8?ads.deviceid=%5BDEVICE_ID%5D&ads.ifa=%5BIFA%5D&coppa=0",
            startedIntents.single().getStringExtra(NativePlayerActivity.EXTRA_URL)
        )
    }

    @Test
    fun playbackCoordinatorResolvesStalkerSeriesEpisodeCreateLink() = runBlocking {
        TestHttpServer(
            mapOf(
                "handshake" to """{"js":{"token":"abc123"}}""",
                "get_profile" to """{"js":{}}""",
                "create_link" to """{"js":{"cmd":"ffmpeg http://stream.test/series?stream=.&token=abc"}}"""
            )
        ).use { server ->
            val account = AndroidSQLiteAccountRepository(helper).saveAccount(
                MobileAccount(
                    accountName = "Portal Playback",
                    type = MobileAccountType.STALKER_PORTAL,
                    url = "http://127.0.0.1:${server.port}",
                    macAddress = "00:1A:79:00:00:01"
                )
            )
            val accountId = requireNotNull(account.id)
            val startedIntents = mutableListOf<Intent>()
            val coordinator = AndroidPlaybackCoordinator(
                context = context,
                preferences = AndroidDataStorePreferencesRepository(context),
                databaseHelper = helper,
                activityStarter = { startedIntents += it }
            )

            val result = coordinator.playWatchingNowEpisode(
                MobileWatchingNowEpisode(
                    rowId = 7,
                    parentRowId = 6,
                    accountId = accountId,
                    accountName = "Portal Playback",
                    seriesId = "show-1",
                    seriesTitle = "Portal Show",
                    categoryProviderId = "series-cat",
                    categoryRowId = 5,
                    episodeId = "episode-7",
                    title = "Episode 7",
                    season = "1",
                    episodeNumber = "7",
                    command = "eyJzZXJpZXNfaWQiOjEsInNlYXNvbl9udW0iOjEsInR5cGUiOiJzZXJpZXMifQ=="
                ),
                AndroidPlayerPreference.EMBEDDED_PLAYER,
                remember = false
            )

            assertTrue(result.launched)
            assertEquals(
                "http://stream.test/series?stream=7&token=abc",
                startedIntents.single().getStringExtra(NativePlayerActivity.EXTRA_URL)
            )
            assertTrue(server.requests.any { it.contains("action=create_link") && it.contains("series=7") })
        }
    }

    @Test
    fun playbackCoordinatorMergesMissingStalkerLiveStreamFromOriginalCommand() = runBlocking {
        TestHttpServer(
            mapOf(
                "handshake" to """{"js":{"token":"abc123"}}""",
                "get_profile" to """{"js":{}}""",
                "create_link" to """{"js":{"cmd":"ffmpeg http://stream.test/live.php?mac=00:1A:79:00:00:01&stream=&extension=ts&play_token=new-token"}}"""
            )
        ).use { server ->
            val account = AndroidSQLiteAccountRepository(helper).saveAccount(
                MobileAccount(
                    accountName = "Portal Live",
                    type = MobileAccountType.STALKER_PORTAL,
                    url = "http://127.0.0.1:${server.port}",
                    macAddress = "00:1A:79:00:00:01"
                )
            )
            val accountId = requireNotNull(account.id)
            val db = helper.writableDatabase
            db.execSQL(
                "INSERT INTO Category(id, categoryId, accountId, accountType, title) VALUES(6101, 'uk', ?, 'itv', 'UK')",
                arrayOf(accountId.toString())
            )
            db.execSQL(
                """
                INSERT INTO Channel(id, channelId, categoryId, name, cmd)
                VALUES(6102, '30581', '6101', 'UK BBC 1 HD', 'ffmpeg http://stream.test/live.php?mac=00:1A:79:00:00:01&stream=30581&extension=ts&play_token=old-token')
                """.trimIndent()
            )
            val startedIntents = mutableListOf<Intent>()
            val coordinator = AndroidPlaybackCoordinator(
                context = context,
                preferences = AndroidDataStorePreferencesRepository(context),
                databaseHelper = helper,
                activityStarter = { startedIntents += it }
            )

            val result = coordinator.playBrowseItem(
                playbackItem(
                    accountId = accountId,
                    mode = BrowseMode.LIVE,
                    categoryId = "uk",
                    channelId = "30581",
                    title = "UK BBC 1 HD",
                    command = "ffmpeg http://stream.test/live.php?mac=00:1A:79:00:00:01&stream=30581&extension=ts&play_token=old-token"
                ),
                AndroidPlayerPreference.EMBEDDED_PLAYER,
                remember = false
            )

            assertTrue(result.launched)
            assertEquals(
                "http://stream.test/live.php?mac=00%3A1A%3A79%3A00%3A00%3A01&stream=30581&extension=ts&play_token=new-token",
                startedIntents.single().getStringExtra(NativePlayerActivity.EXTRA_URL)
            )
        }
    }

    @Test
    fun playbackCoordinatorNormalizesRelativeStalkerCreateLinkBaseLikeDesktop() = runBlocking {
        TestHttpServer(
            mapOf(
                "handshake" to """{"js":{"token":"abc123"}}""",
                "get_profile" to """{"js":{}}""",
                "create_link" to """{"js":{"cmd":"ffmpeg live.php?stream=&play_token=new-token"}}"""
            )
        ).use { server ->
            val account = AndroidSQLiteAccountRepository(helper).saveAccount(
                MobileAccount(
                    accountName = "Portal Relative Live",
                    type = MobileAccountType.STALKER_PORTAL,
                    url = "http://127.0.0.1:${server.port}",
                    macAddress = "00:1A:79:00:00:01"
                )
            )
            val accountId = requireNotNull(account.id)
            val startedIntents = mutableListOf<Intent>()
            val coordinator = AndroidPlaybackCoordinator(
                context = context,
                preferences = AndroidDataStorePreferencesRepository(context),
                databaseHelper = helper,
                activityStarter = { startedIntents += it }
            )

            val result = coordinator.playBrowseItem(
                playbackItem(
                    accountId = accountId,
                    mode = BrowseMode.LIVE,
                    categoryId = "uk",
                    channelId = "30581",
                    title = "Relative Live",
                    command = "ffmpeg http://origin.test/path/original.php?stream=30581&play_token=old-token"
                ),
                AndroidPlayerPreference.EMBEDDED_PLAYER,
                remember = false
            )

            assertTrue(result.launched)
            assertEquals(
                "http://origin.test/path/live.php?stream=30581&play_token=new-token",
                startedIntents.single().getStringExtra(NativePlayerActivity.EXTRA_URL)
            )
        }
    }

    @Test
    fun browseRepositoryEnrichesWatchingNowAndSeriesEpisodesFromMetadataProvider() = runBlocking {
        val provider = object : AndroidImdbMetadataProvider {
            override fun findSeriesDetails(title: String, preferredImdbId: String, hints: List<String>): AndroidImdbMetadata =
                AndroidImdbMetadata(
                    name = "Localized Show",
                    cover = "https://image.test/show.jpg",
                    plot = "Series plot",
                    releaseDate = "2024-02-01",
                    rating = "8.7",
                    genre = "Drama",
                    imdbUrl = "https://www.imdb.com/title/tt1234567/",
                    episodesMeta = listOf(
                        AndroidEpisodeMetadata(
                            title = "The Real Pilot",
                            season = "4",
                            episodeNumber = "1",
                            plot = "Episode plot",
                            logo = "https://image.test/episode.jpg",
                            releaseDate = "2024-02-02",
                            rating = "8.9"
                        )
                    )
                )

            override fun findMovieDetails(title: String, preferredImdbId: String, hints: List<String>): AndroidImdbMetadata =
                AndroidImdbMetadata(
                    name = "Provider Movie",
                    cover = "https://image.test/movie.jpg",
                    plot = "Movie plot",
                    releaseDate = "2023-01-01",
                    rating = "7.5",
                    duration = "110 min",
                    genre = "Action",
                    imdbUrl = "https://www.imdb.com/title/tt7654321/"
                )
        }
        val repository = AndroidSQLiteBrowseRepository(helper, provider)

        val movie = repository.enrichWatchingNowItem(
            MobileWatchingNowItem(
                rowId = 1,
                accountId = 2,
                accountName = "Demo",
                mode = BrowseMode.VOD,
                title = "Cached Movie",
                subtitle = "Demo"
            )
        )
        val details = repository.enrichSeriesDetails(
            MobileWatchingNowItem(
                rowId = 2,
                accountId = 2,
                accountName = "Demo",
                mode = BrowseMode.SERIES,
                title = "Cached Show",
                subtitle = "Demo",
                contentId = "show-1"
            ),
            listOf(
                MobileWatchingNowEpisode(
                    rowId = 3,
                    parentRowId = 2,
                    accountId = 2,
                    accountName = "Demo",
                    seriesId = "show-1",
                    seriesTitle = "Cached Show",
                    categoryProviderId = "series",
                    categoryRowId = 4,
                    episodeId = "episode-1",
                    title = "Season 4 - Episode 1",
                    episodeNumber = "1"
                )
            )
        )

        assertEquals("Cached Movie", movie.title)
        assertEquals("Movie plot", movie.plot)
        assertEquals("7.5", movie.rating)
        assertEquals("110 min", movie.duration)
        assertEquals("Localized Show", details.series.title)
        assertEquals("Series plot", details.series.plot)
        assertEquals("The Real Pilot", details.episodes.single().title)
        assertEquals("Episode plot", details.episodes.single().plot)
        assertEquals("8.9", details.episodes.single().rating)
    }

    @Test
    fun browseRepositoryCachesWatchingNowImdbMetadataUntilInvalidated() = runBlocking {
        var movieRequests = 0
        var seriesRequests = 0
        val provider = object : AndroidImdbMetadataProvider {
            override fun findSeriesDetails(title: String, preferredImdbId: String, hints: List<String>): AndroidImdbMetadata {
                seriesRequests += 1
                return AndroidImdbMetadata(
                    name = "Cached Series $seriesRequests",
                    plot = "Series plot $seriesRequests",
                    episodesMeta = listOf(
                        AndroidEpisodeMetadata(
                            title = "Episode $seriesRequests",
                            season = "1",
                            episodeNumber = "1",
                            plot = "Episode plot $seriesRequests"
                        )
                    )
                )
            }

            override fun findMovieDetails(title: String, preferredImdbId: String, hints: List<String>): AndroidImdbMetadata {
                movieRequests += 1
                return AndroidImdbMetadata(plot = "Movie plot $movieRequests")
            }
        }
        val repository = AndroidSQLiteBrowseRepository(helper, provider)
        val movie = MobileWatchingNowItem(
            rowId = 1,
            accountId = 2,
            accountName = "Demo",
            mode = BrowseMode.VOD,
            title = "Cached Movie",
            subtitle = "Demo",
            contentId = "tt7654321"
        )
        val series = MobileWatchingNowItem(
            rowId = 2,
            accountId = 2,
            accountName = "Demo",
            mode = BrowseMode.SERIES,
            title = "Cached Show",
            subtitle = "Demo",
            contentId = "tt1234567"
        )
        val episodes = listOf(
            MobileWatchingNowEpisode(
                rowId = 3,
                parentRowId = 2,
                accountId = 2,
                accountName = "Demo",
                seriesId = "tt1234567",
                seriesTitle = "Cached Show",
                categoryProviderId = "series",
                categoryRowId = 4,
                episodeId = "episode-1",
                title = "Season 1 - Episode 1",
                season = "1",
                episodeNumber = "1"
            )
        )

        assertEquals("Movie plot 1", repository.enrichWatchingNowItem(movie).plot)
        assertEquals("Movie plot 1", repository.enrichWatchingNowItem(movie.copy(updatedAtEpochSeconds = 999)).plot)
        assertEquals(1, movieRequests)

        val firstDetails = repository.enrichSeriesDetails(series, episodes)
        val secondDetails = repository.enrichSeriesDetails(series.copy(updatedAtEpochSeconds = 999), episodes)
        assertEquals("Series plot 1", firstDetails.series.plot)
        assertEquals("Episode plot 1", secondDetails.episodes.single().plot)
        assertEquals(1, seriesRequests)

        repository.enrichWatchingNowItem(movie.copy(title = "Changed Movie"))
        assertEquals(2, movieRequests)

        repository.invalidateCaches()
        repository.enrichWatchingNowItem(movie)
        assertEquals(3, movieRequests)
    }

    @Test
    fun playbackCoordinatorStartsBingeWatchWithoutResolvingEpisodeUrlsUpfront() = runBlocking {
        AndroidBingeWatchSessionStore.clear()
        val account = AndroidSQLiteAccountRepository(helper).saveAccount(
            MobileAccount(
                accountName = "Binge Account",
                type = MobileAccountType.STALKER_PORTAL,
                url = "http://127.0.0.1:1",
                macAddress = "00:1A:79:00:00:02"
            )
        )
        val accountId = requireNotNull(account.id)
        helper.writableDatabase.execSQL(
            """
            INSERT INTO SeriesWatchState(
                id, accountId, mode, categoryId, seriesId, episodeId, episodeName, season, episodeNum, updatedAt
            ) VALUES(3101, ?, 'series', 'shows', 'show-1', 'ep-2', 'Episode 2', '1', '2', 999)
            """.trimIndent(),
            arrayOf(accountId.toString())
        )
        val startedIntents = mutableListOf<Intent>()
        val coordinator = AndroidPlaybackCoordinator(
            context = context,
            preferences = AndroidDataStorePreferencesRepository(context),
            databaseHelper = helper,
            activityStarter = { startedIntents += it }
        )

        val result = coordinator.playBingeWatchSeason(
            series = MobileWatchingNowItem(
                rowId = 1,
                accountId = accountId,
                accountName = "Binge Account",
                mode = BrowseMode.SERIES,
                title = "Portal Show",
                subtitle = "Binge Account",
                categoryProviderId = "shows",
                contentId = "show-1"
            ),
            episodes = listOf(
                bingeEpisode(accountId, "ep-1", "Season 1 - Episode 1", "ffrt http://localhost/ch/ep-1", "1"),
                bingeEpisode(accountId, "ep-2", "Season 1 - Episode 2", "ffrt http://localhost/ch/ep-2", "2")
            ),
            seasonKey = "season:1",
            player = AndroidPlayerPreference.EMBEDDED_PLAYER,
            remember = false
        )

        assertTrue(result.launched)
        val intent = startedIntents.single()
        val sessionId = intent.getStringExtra(NativePlayerActivity.EXTRA_BINGE_SESSION_ID).orEmpty()
        val session = requireNotNull(AndroidBingeWatchSessionStore.get(sessionId))
        assertEquals(1, session.startIndex)
        assertEquals("ffrt http://localhost/ch/ep-2", intent.getStringExtra(NativePlayerActivity.EXTRA_URL))
        assertEquals(listOf("ffrt http://localhost/ch/ep-1", "ffrt http://localhost/ch/ep-2"), session.targets.map { it.url })
        assertEquals(MpvEmbeddedPlayerActivity::class.java.name, intent.component?.className)
        AndroidBingeWatchSessionStore.clear()
    }

    @Test
    fun playbackCoordinatorStartsBingeAtFirstEpisodeWhenSeasonHasNoFlagAndMovesWatchingPointer() = runBlocking {
        AndroidBingeWatchSessionStore.clear()
        val account = AndroidSQLiteAccountRepository(helper).saveAccount(
            MobileAccount(
                accountName = "Binge Season Account",
                type = MobileAccountType.STALKER_PORTAL,
                url = "http://127.0.0.1:1",
                macAddress = "00:1A:79:00:00:03"
            )
        )
        val accountId = requireNotNull(account.id)
        val db = helper.writableDatabase
        db.execSQL(
            """
            INSERT INTO SeriesWatchState(
                id, accountId, mode, categoryId, seriesId, episodeId, episodeName, season, episodeNum, updatedAt
            ) VALUES(3201, ?, 'series', 'shows', 'show-1', 's1e3', 'Season 1 - Episode 3', '1', '3', 999)
            """.trimIndent(),
            arrayOf(accountId.toString())
        )
        val startedIntents = mutableListOf<Intent>()
        val coordinator = AndroidPlaybackCoordinator(
            context = context,
            preferences = AndroidDataStorePreferencesRepository(context),
            databaseHelper = helper,
            activityStarter = { startedIntents += it }
        )
        val episodes = listOf(
            bingeEpisode(accountId, "s2e1", "Season 2 - Episode 1", "ffrt http://localhost/ch/s2e1", "1", season = "2"),
            bingeEpisode(accountId, "s2e2", "Season 2 - Episode 2", "ffrt http://localhost/ch/s2e2", "2", season = "2")
        )

        val result = coordinator.playBingeWatchSeason(
            series = MobileWatchingNowItem(
                rowId = 1,
                accountId = accountId,
                accountName = "Binge Season Account",
                mode = BrowseMode.SERIES,
                title = "Portal Show",
                subtitle = "Binge Season Account",
                categoryProviderId = "shows",
                contentId = "show-1"
            ),
            episodes = episodes,
            seasonKey = "season:2",
            player = AndroidPlayerPreference.EMBEDDED_PLAYER,
            remember = false
        )

        assertTrue(result.launched)
        val session = requireNotNull(AndroidBingeWatchSessionStore.get(startedIntents.single().getStringExtra(NativePlayerActivity.EXTRA_BINGE_SESSION_ID).orEmpty()))
        assertEquals(0, session.startIndex)
        assertEquals("s2e1", db.singleString("SeriesWatchState", "episodeId", "accountId = '$accountId' AND seriesId = 'show-1'"))
        assertEquals("2", db.singleString("SeriesWatchState", "season", "accountId = '$accountId' AND seriesId = 'show-1'"))
        assertEquals("1", db.singleString("SeriesWatchState", "episodeNum", "accountId = '$accountId' AND seriesId = 'show-1'"))
        assertEquals(1, db.countRows("SeriesWatchingNowSnapshot", "accountId = '$accountId' AND seriesId = 'show-1'"))

        AndroidPlaybackWatchStateStore(helper, epochSeconds = { 5001 }).markOpened(session.targets[1])

        assertEquals("s2e2", db.singleString("SeriesWatchState", "episodeId", "accountId = '$accountId' AND seriesId = 'show-1'"))
        assertEquals("2", db.singleString("SeriesWatchState", "episodeNum", "accountId = '$accountId' AND seriesId = 'show-1'"))
        AndroidBingeWatchSessionStore.clear()
    }

    @Test
    fun browseRepositoryMarksClearsWatchingFlagAndBingeStartsThere() = runBlocking {
        AndroidBingeWatchSessionStore.clear()
        val account = AndroidSQLiteAccountRepository(helper).saveAccount(
            MobileAccount(
                accountName = "Manual Flag",
                type = MobileAccountType.XTREME_API,
                url = "https://example.test",
                username = "u",
                password = "p"
            )
        )
        val accountId = requireNotNull(account.id)
        val db = helper.writableDatabase
        db.execSQL("INSERT INTO SeriesCategory(id, categoryId, accountId, accountType, title) VALUES(4101, 'shows', ?, 'series', 'Shows')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO SeriesEpisode(id, accountId, categoryId, seriesId, channelId, name, cmd, season, episodeNum) VALUES(4102, ?, 'shows', 'show-1', 'ep-1', 'Episode 1', 'https://stream.test/ep1.m3u8', '1', '1')", arrayOf(accountId.toString()))
        db.execSQL("INSERT INTO SeriesEpisode(id, accountId, categoryId, seriesId, channelId, name, cmd, season, episodeNum) VALUES(4103, ?, 'shows', 'show-1', 'ep-2', 'Episode 2', 'https://stream.test/ep2.m3u8', '1', '2')", arrayOf(accountId.toString()))
        val series = MobileWatchingNowItem(
            rowId = 1,
            accountId = accountId,
            accountName = "Manual Flag",
            mode = BrowseMode.SERIES,
            title = "Flagged Show",
            subtitle = "Manual Flag",
            categoryProviderId = "shows",
            categoryRowId = 4101,
            contentId = "show-1"
        )
        val repository = AndroidSQLiteBrowseRepository(helper)
        val initial = repository.listWatchingNowEpisodes(series)
        val startedIntents = mutableListOf<Intent>()
        val coordinator = AndroidPlaybackCoordinator(
            context = context,
            preferences = AndroidDataStorePreferencesRepository(context),
            databaseHelper = helper,
            activityStarter = { startedIntents += it }
        )

        assertEquals(listOf(false, false), initial.map { it.isWatched })

        val playResult = coordinator.playWatchingNowEpisode(initial[0], AndroidPlayerPreference.EMBEDDED_PLAYER, remember = false)
        assertTrue(playResult.launched)
        assertEquals("ep-1", db.singleString("SeriesWatchState", "episodeId", "accountId = '$accountId' AND seriesId = 'show-1'"))
        assertEquals(1, db.countRows("SeriesWatchingNowSnapshot", "accountId = '$accountId' AND seriesId = 'show-1'"))
        val played = repository.listWatchingNowEpisodes(series)
        assertEquals(listOf(true, false), played.map { it.isWatched })

        repository.markWatchingNowEpisode(played[1])
        val marked = repository.listWatchingNowEpisodes(series)

        assertEquals(listOf(false, true), marked.map { it.isWatched })
        assertEquals(1, db.countRows("SeriesWatchState", "accountId = '$accountId' AND seriesId = 'show-1' AND episodeId = 'ep-2'"))

        startedIntents.clear()
        val result = coordinator.playBingeWatchSeason(series, marked, "season:1", AndroidPlayerPreference.EMBEDDED_PLAYER, remember = false)

        assertTrue(result.launched)
        val session = requireNotNull(AndroidBingeWatchSessionStore.get(startedIntents.single().getStringExtra(NativePlayerActivity.EXTRA_BINGE_SESSION_ID).orEmpty()))
        assertEquals(1, session.startIndex)

        repository.clearWatchingNowEpisode(marked[1])
        val cleared = repository.listWatchingNowEpisodes(series)

        assertEquals(listOf(false, false), cleared.map { it.isWatched })
        assertEquals(0, db.countRows("SeriesWatchState", "accountId = '$accountId' AND seriesId = 'show-1'"))
        AndroidBingeWatchSessionStore.clear()
    }

    private fun SQLiteDatabase.tableExists(table: String): Boolean {
        rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private fun SQLiteDatabase.tableColumns(table: String): List<String> {
        rawQuery("PRAGMA table_info(\"${table.replace("\"", "\"\"")}\")", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
            return columns
        }
    }

    private fun SQLiteDatabase.countRows(table: String, where: String): Int {
        rawQuery("SELECT COUNT(*) FROM \"${table.replace("\"", "\"\"")}\" WHERE $where", null).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    private fun SQLiteDatabase.singleString(table: String, column: String, where: String): String {
        rawQuery("SELECT \"${column.replace("\"", "\"\"")}\" FROM \"${table.replace("\"", "\"\"")}\" WHERE $where LIMIT 1", null).use { cursor ->
            cursor.moveToFirst()
            return cursor.getString(0)
        }
    }

    private fun bingeEpisode(
        accountId: Long,
        episodeId: String,
        title: String,
        command: String,
        episodeNumber: String,
        season: String = "1"
    ): MobileWatchingNowEpisode =
        MobileWatchingNowEpisode(
            rowId = episodeId.hashCode().toLong(),
            parentRowId = 1,
            accountId = accountId,
            accountName = "Binge Account",
            seriesId = "show-1",
            seriesTitle = "Portal Show",
            categoryProviderId = "shows",
            categoryRowId = 0,
            episodeId = episodeId,
            title = title,
            season = season,
            episodeNumber = episodeNumber,
            command = command
        )

    private class TestHttpServer(private val bodiesByAction: Map<String, String>) : AutoCloseable {
        private val socket = ServerSocket(0)
        val port: Int = socket.localPort
        val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())
        private val worker = thread(start = true) {
            while (!socket.isClosed) {
                runCatching { socket.accept().use(::respond) }
                    .onFailure { if (!socket.isClosed) throw it }
            }
        }

        private fun respond(client: Socket) {
            val reader = client.getInputStream().bufferedReader()
            val requestLine = reader.readLine().orEmpty()
            requests += requestLine
            while (reader.readLine().orEmpty().isNotEmpty()) {
                // Drain headers.
            }
            val action = Regex("[?&]action=([^&\\s]+)").find(requestLine)?.groupValues?.getOrNull(1)
            val body = action?.let { bodiesByAction[it] } ?: "[]"
            val bytes = body.toByteArray(Charsets.UTF_8)
            client.getOutputStream().use { output ->
                output.write("HTTP/1.1 200 OK\r\n".toByteArray())
                output.write("Content-Type: application/json\r\n".toByteArray())
                output.write("Content-Length: ${bytes.size}\r\n".toByteArray())
                output.write("Connection: close\r\n\r\n".toByteArray())
                output.write(bytes)
            }
        }

        override fun close() {
            socket.close()
            worker.join(1_000)
        }
    }

    private fun playbackItem(
        accountId: Long,
        mode: BrowseMode,
        categoryId: String,
        channelId: String,
        title: String,
        command: String = "ffmpeg https://stream.test/$channelId.m3u8",
        drmType: String = "",
        drmLicenseUrl: String = "",
        clearKeysJson: String = "",
        inputstreamAddon: String = "",
        manifestType: String = ""
    ): MobileBrowseItem =
        MobileBrowseItem(
            rowId = channelId.hashCode().toLong(),
            accountId = accountId,
            accountName = "Playback Account",
            mode = mode,
            categoryRowId = 9,
            categoryProviderId = categoryId,
            categoryTitle = categoryId,
            channelId = channelId,
            name = title,
            command = command,
            drmType = drmType,
            drmLicenseUrl = drmLicenseUrl,
            clearKeysJson = clearKeysJson,
            inputstreamAddon = inputstreamAddon,
            manifestType = manifestType
        )

}
