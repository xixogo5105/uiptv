package com.uiptv.mobile.android.epg

import androidx.test.platform.app.InstrumentationRegistry
import com.uiptv.mobile.shared.epg.*
import com.uiptv.mobile.shared.accounts.*
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import com.uiptv.mobile.shared.settings.AndroidDataStorePreferencesRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class AndroidEpgTest {
    @Test fun sharedGuidesResolveAccountsCreatedLaterAcrossProviderTypes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("epg-shared-fixture", ".db", context.cacheDir)
        val db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file,null)
        try {
            AndroidUiptvDatabaseHelper(context).use { it.applyMigrations(db) }
            val now = System.currentTimeMillis()
            db.execSQL("INSERT INTO Configuration(globalEpgSource,epgMappingsJson) VALUES('','{}')")
            db.execSQL("INSERT INTO epg_shared_source VALUES('shared:fixture',40,'IN','')")
            db.execSQL("INSERT INTO epg_source VALUES('shared:fixture',?,?, '')",arrayOf<Any>(now,now+3600000))
            db.execSQL("INSERT INTO epg_alias VALUES('shared:fixture',0,'star sports 1 hd','guide')")
            val output = java.io.ByteArrayOutputStream()
            java.util.zip.DeflaterOutputStream(output).use { it.write("Real description".toByteArray()) }
            db.execSQL("INSERT INTO epg_programme VALUES('shared:fixture','guide',?,?,?,?)",
                arrayOf<Any>(now-60000,now+3600000,"Shared programme",output.toByteArray()))
            val cache = AndroidSyncedEpgCache(db)
            listOf("STALKER_PORTAL","XTREME_API","M3U8_LOCAL").forEachIndexed { index,type ->
                val id = 900L+index
                db.execSQL("INSERT INTO Account(id,accountName,type,epg) VALUES(?,?,?,'')",arrayOf<Any>(id,type,type))
                assertEquals("Shared programme",cache.matchCloned(id,"different-provider-id","┃WC┃ STAR SPORTS 1 HD",now,shared=true)?.result?.programmes?.first()?.title)
                assertNull(cache.matchCloned(id,"different-provider-id","┃USA┃ STAR SPORTS 1 HD",now,shared=true))
                assertNull(cache.matchCloned(id,"different-provider-id","Star Sports 1 +1",now,shared=true))
            }
            assertEquals(0,db.rawQuery("SELECT COUNT(*) FROM epg_channel_mapping",null).use { it.moveToFirst();it.getInt(0) })
        } finally { db.close();file.delete() }
    }

    @Test fun databaseNameRulesDriveClonedMatchingAndRuleEditsInvalidateAliasIndexes() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File.createTempFile("epg-rules-fixture", ".db", context.cacheDir)
        val db=android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file,null)
        try {
            AndroidUiptvDatabaseHelper(context).use { it.applyMigrations(db) }
            val source="https://example.invalid/rules.xml"
            val sourceId=java.security.MessageDigest.getInstance("SHA-256").digest("xml:$source".toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            val now=System.currentTimeMillis()
            db.execSQL("INSERT INTO Configuration(globalEpgSource,epgMappingsJson) VALUES(?,?)",arrayOf(source,"{}"))
            db.execSQL("INSERT INTO Account(id,accountName,type,epg) VALUES(?,?,?,?)",arrayOf("999","Fixture","M3U8_LOCAL",""))
            db.execSQL("INSERT INTO epg_source VALUES(?,?,?,?)",arrayOf<Any>(sourceId,now,now+3600000,""))
            db.execSQL("INSERT INTO epg_alias VALUES(?,?,?,?)",arrayOf<Any>(sourceId,0,"station","guide-station"))
            val output=java.io.ByteArrayOutputStream()
            java.util.zip.DeflaterOutputStream(output).use { it.write("Description".toByteArray()) }
            db.execSQL("INSERT INTO epg_programme VALUES(?,?,?,?,?,?)",arrayOf<Any>(sourceId,"guide-station",now-60000,now+3600000,"Named programme",output.toByteArray()))
            val cache=AndroidSyncedEpgCache(db)
            assertEquals("Named programme",cache.matchCloned(999,"playlist-id","Station (504p) [Not 24/7]",now)?.result?.programmes?.first()?.title)
            assertNull(cache.matchCloned(999,"playlist-id","Station [Trial]",now))
            db.execSQL("INSERT INTO epg_name_exclusion VALUES('trial','annotation',1)")
            assertEquals("Named programme",cache.matchCloned(999,"playlist-id","Station [Trial]",now)?.result?.programmes?.first()?.title)
            db.execSQL("UPDATE epg_name_exclusion SET enabled=0 WHERE term='trial'")
            assertNull(cache.matchCloned(999,"playlist-id","Station [Trial]",now))
            assertNull(cache.matchCloned(999,"playlist-id","Station +1 (504p)",now))
            assertNull(cache.matchCloned(999,"playlist-id","Station West (504p)",now))
            assertEquals("Named programme",cache.matchCloned(999,"guide-station","Unrelated name",now)?.result?.programmes?.first()?.title)
            db.execSQL("DELETE FROM schema_migrations WHERE name='0212_add_epg_name_exclusions.sql'")
            AndroidUiptvDatabaseHelper(context).use { it.applyMigrations(db) }
            assertEquals(0,db.rawQuery("SELECT enabled FROM epg_name_exclusion WHERE term='trial'",null).use { it.moveToFirst();it.getInt(0) })
        } finally { db.close(); file.delete() }
    }

    @Test fun clonedSpacedAliasesServeRealProgrammesWithoutChannelMappings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("epg-clone-fixture", ".db", context.cacheDir)
        val db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            AndroidUiptvDatabaseHelper(context).use { it.applyMigrations(db) }
            val source = "https://example.invalid/uk.xml"
            val sourceId = java.security.MessageDigest.getInstance("SHA-256").digest("xml:$source".toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            val now = System.currentTimeMillis()
            db.execSQL("INSERT INTO Configuration(globalEpgSource,epgMappingsJson) VALUES(?,?)", arrayOf(source,"{}"))
            db.execSQL("INSERT INTO Account(id,accountName,type,epg) VALUES(?,?,?,?)", arrayOf("999","Fixture","M3U8_LOCAL",""))
            db.execSQL("INSERT INTO epg_source VALUES(?,?,?,?)", arrayOf<Any>(sourceId,now,now+3600000,""))
            db.execSQL("INSERT INTO epg_alias VALUES(?,?,?,?)", arrayOf<Any>(sourceId,0,"bbc two hd","BBC.Two.HD.uk"))
            db.execSQL("INSERT INTO epg_alias VALUES(?,?,?,?)", arrayOf<Any>(sourceId,1,"bbc two","BBC.Two.HD.uk"))
            db.execSQL("INSERT INTO epg_alias VALUES(?,?,?,?)", arrayOf<Any>(sourceId,0,"bbc two","empty-sd"))
            val output = java.io.ByteArrayOutputStream()
            java.util.zip.DeflaterOutputStream(output).use { it.write("Real description".toByteArray()) }
            db.execSQL("INSERT INTO epg_programme VALUES(?,?,?,?,?,?)", arrayOf<Any>(sourceId,"BBC.Two.HD.uk",now-60000,now+3600000,"Real UK programme",output.toByteArray()))
            db.execSQL("INSERT INTO epg_channel_icon VALUES(?,?,?)", arrayOf(sourceId,"BBC.Two.HD.uk","https://example.invalid/station.png"))
            val cache = AndroidSyncedEpgCache(db)
            assertEquals("https://example.invalid/station.png",cache.matchedIcon(999,"BBCTwo.uk","BBC Two Ⓖ"))
            assertTrue(cache.matchedIcon(999,"other","BBC Two +1").isEmpty())
            val result = cache.matchCloned(999,"BBCTwo.uk","bBc Two Ⓖ",now)
            assertNotNull(result)
            assertEquals("Real UK programme",result!!.result.now(now)!!.title)
            assertEquals("Real description",result.result.now(now)!!.description)
            assertNull(cache.matchCloned(999,"other","BBC Two +1",now))
            assertEquals(0, db.rawQuery("SELECT COUNT(*) FROM epg_channel_mapping",null).use { it.moveToFirst();it.getInt(0) })
        } finally { db.close(); file.delete() }
    }

    @Test fun syncedDesktopCacheServesSchedulesDescriptionsAndArtworkWithoutSourceFiles() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helper = AndroidUiptvDatabaseHelper(context)
        val preferences = AndroidDataStorePreferencesRepository(context)
        val db = helper.writableDatabase
        val accountId = 987654321L
        val source = "desktop-cache-fixture"
        val repository = AndroidEpgRepository(context, helper, preferences)
        val previousConfig = db.rawQuery("SELECT globalEpgSource,epgMappingsJson FROM Configuration LIMIT 1", null).use {
            it.moveToFirst(); it.getString(0).orEmpty() to it.getString(1).orEmpty()
        }
        val wasEnabled = preferences.load().epgEnabled
        val now = System.currentTimeMillis()
        fun values(vararg pairs: Pair<String, String>) = android.content.ContentValues().apply {
            pairs.forEach { (key, value) -> put(key, value) }
        }
        fun compress(text: String): ByteArray {
            val output = java.io.ByteArrayOutputStream()
            java.util.zip.DeflaterOutputStream(output).use { it.write(text.toByteArray()) }
            return output.toByteArray()
        }
        try {
            preferences.saveEpgEnabled(true)
            db.update("Configuration", values("globalEpgSource" to "", "epgMappingsJson" to "{}"), null, null)
            db.insertOrThrow("Account", null, values("id" to accountId.toString(), "accountName" to "Synced fixture",
                "type" to "M3U8_LOCAL", "url" to "", "username" to "", "password" to "", "macAddress" to "", "epg" to "", "m3u8Path" to ""))
            val categoryId = db.insertOrThrow("Category", null, values("accountId" to accountId.toString(), "categoryId" to "provider-category", "title" to "Fixture"))
            val channelId = db.insertOrThrow("Channel", null, values("categoryId" to categoryId.toString(), "channelId" to "provider-stream",
                "name" to "Synced station", "extraJson" to "{\"xmltv_id\":\"station\"}"))
            db.execSQL("INSERT INTO epg_source VALUES(?,?,?,?)", arrayOf<Any>(source, now, now + 1_800_000, ""))
            // Golden signature is verified by the Java EpgPersistentCacheTest as well.
            val signature = "6c5946350f540c3a39861441107fbca5da02de06bc25b0b1e2175b100a6d48ad"
            db.execSQL("INSERT INTO epg_channel_mapping VALUES(?,?,?,?,?,?)", arrayOf<Any>(accountId.toString(), "provider-stream", signature, source, "station", 0))
            db.execSQL("INSERT INTO epg_programme VALUES(?,?,?,?,?,?)", arrayOf<Any>(source, "station", now - 60_000, now + 3_600_000, "Synced film", compress("Synced full description")))
            db.execSQL("INSERT INTO epg_channel_icon VALUES(?,?,?)", arrayOf<Any>(source, "station", "https://example.invalid/station.png"))
            MobileEpgCache.clear()
            val result = repository.schedule(accountId, "provider-stream", "Old bookmark name")
            assertEquals(MobileEpgStatus.AVAILABLE, result.status)
            assertEquals("Synced film", result.programmes.first().title)
            assertEquals("Synced full description", result.programmes.first().description)
            assertEquals("https://example.invalid/station.png", repository.icon(accountId, "provider-stream"))
            assertTrue(repository.icon(accountId + 1, "provider-stream").isEmpty())
            assertTrue(repository.schedule(accountId, channelId.toString(), "Synced station").programmes.isEmpty())
            MobileEpgCache.clear()
            db.execSQL("UPDATE epg_source SET retry_at=? WHERE source_id=?", arrayOf<Any>(now - 1, source))
            assertEquals(MobileEpgStatus.STALE, repository.schedule(accountId, "provider-stream", "Synced station").status)
            db.update("Channel", values("extraJson" to "{\"xmltv_id\":\"changed\"}"), "id=?", arrayOf(channelId.toString()))
            MobileEpgCache.clear()
            assertTrue(repository.schedule(accountId, "provider-stream", "Synced station").programmes.isEmpty())
            assertEquals("", repository.icon(accountId, "provider-stream"))
            db.update("Channel", values("extraJson" to "{\"xmltv_id\":\"station\"}"), "id=?", arrayOf(channelId.toString()))
            db.execSQL("UPDATE epg_programme SET starts_at=?,stops_at=? WHERE source_id=?", arrayOf<Any>(now - 7_200_000, now - 1, source))
            MobileEpgCache.clear()
            assertTrue(repository.schedule(accountId, "provider-stream", "Synced station").programmes.isEmpty())
        } finally {
            MobileEpgCache.clear()
            db.delete("epg_channel_mapping", "account_id=?", arrayOf(accountId.toString()))
            for (table in listOf("epg_programme", "epg_channel_icon", "epg_source")) db.delete(table, "source_id=?", arrayOf(source))
            db.execSQL("DELETE FROM Channel WHERE categoryId IN (SELECT id FROM Category WHERE accountId=?)", arrayOf<Any>(accountId))
            db.delete("Category", "accountId=?", arrayOf(accountId.toString()))
            db.delete("Account", "id=?", arrayOf(accountId.toString()))
            db.update("Configuration", values("globalEpgSource" to previousConfig.first, "epgMappingsJson" to previousConfig.second), null, null)
            preferences.saveEpgEnabled(wasEnabled)
            helper.close()
        }
    }

    @Test fun epgIconIsUsedWhenPrimaryThumbnailIsMissingOrFails() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cache = com.uiptv.mobile.android.ThumbnailCache.getInstance(context)
        val fallback = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
        val primary = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
        val iconUrl = "https://example.invalid/epg-icon-fixture.png"
        val logoUrl = "https://example.invalid/channel-logo-fixture.png"
        cache.put(iconUrl, fallback)
        assertSame(fallback, com.uiptv.mobile.android.loadLogoBitmap(cache, "", iconUrl))
        cache.negativeCache(logoUrl, 404)
        assertSame(fallback, com.uiptv.mobile.android.loadLogoBitmap(cache, logoUrl, iconUrl))
        val validLogo = "https://example.invalid/valid-logo-fixture.png"
        cache.put(validLogo, primary)
        assertSame(primary, com.uiptv.mobile.android.loadLogoBitmap(cache, validLogo, iconUrl))
        var resolutions = 0
        val resolve: suspend () -> String = { resolutions++; iconUrl }
        assertSame(primary, com.uiptv.mobile.android.loadLogoBitmap(cache, validLogo, "", resolve))
        assertEquals(0,resolutions)
        assertSame(fallback, com.uiptv.mobile.android.loadLogoBitmap(cache, logoUrl, "", resolve))
        assertEquals(1,resolutions)
        assertSame(fallback, com.uiptv.mobile.android.loadLogoBitmap(cache, "", "", resolve))
        assertEquals(2,resolutions)
    }

    @Test fun xmltvChannelIconsResolveRelativeUrlsWithoutTakingProgrammeArtwork() {
        val xml = """<tv>
            <channel id="station"><display-name>Station HD</display-name><icon src="../logos/station.png"/></channel>
            <channel id="invalid"><display-name>Invalid</display-name><icon src="javascript:bad"/></channel>
            <programme channel="station" start="20261015120000 +0000" stop="20261015140000 +0000">
                <title>Film</title><icon src="https://example.invalid/programme.png"/>
            </programme></tv>"""
        val guide = AndroidXmltvReader.read(ByteArrayInputStream(xml.toByteArray()),
            Instant.parse("2026-10-15T12:30:00Z").toEpochMilli(),"https://example.invalid/guides/uk.xml")
        assertEquals("https://example.invalid/logos/station.png",guide.iconForChannel("stream","STATION Ⓖ"))
        assertEquals("https://example.invalid/logos/station.png",guide.iconForChannel("station","other"))
        assertTrue(guide.iconForChannel("invalid","Invalid").isEmpty())
        assertEquals(1,guide.icons.size)
        assertTrue(guide.iconForChannel("other","Station +1").isEmpty())
    }

    @Test fun identicalDesktopFixturePinsDstMatchingAndMissingStops() {
        val context = InstrumentationRegistry.getInstrumentation().context
        context.assets.open("epg/parity.xml").use { input ->
            val guide = AndroidXmltvReader.read(input, Instant.parse("2026-10-25T00:45:00Z").toEpochMilli())
            val list = guide.forChannel("bbc", "other")
            assertEquals(2, list.size)
            assertEquals(Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(), list.first().start)
            assertEquals(list[1].start, list[0].stop)
            assertEquals(list, guide.forChannel("stream", " BBC  ONE HD "))
            assertTrue(guide.forChannel("local-row", "News").isEmpty())
            assertEquals(list, guide.forChannel("stream", "BBC One"))
            assertEquals(list, guide.forChannel("stream", "bBc OnE Ⓖ"))
            assertEquals(list, guide.forChannel("stream", "BBCOne"))
            assertTrue(guide.forChannel("stream", "BBC One +1").isEmpty())
            assertTrue(guide.forChannel("stream", "BBC One Scotland").isEmpty())
        }
    }
    @Test fun rejectsDtdAndInvalidCalendarDates() {
        val xml = "<!DOCTYPE tv [<!ENTITY x SYSTEM 'file:///private/secret'>]><tv><channel id='x'><display-name>&x;</display-name></channel></tv>"
        val result = runCatching { AndroidXmltvReader.read(ByteArrayInputStream(xml.toByteArray()), System.currentTimeMillis()) }
        assertTrue(result.isFailure || result.getOrNull()?.names?.values?.none { it.contains("secret") } == true)
        assertEquals(0L, AndroidXmltvReader.time("20260230180000 +0000"))
    }
    @Test fun standardXmltvDoctypeIsIgnoredButInternalEntitiesAreRejected() {
        val xml = "<!DOCTYPE tv SYSTEM 'http://127.0.0.1:1/xmltv.dtd'><tv><channel id='IN4000600Q'><display-name>Bollygold</display-name></channel></tv>"
        assertEquals("IN4000600Q", AndroidXmltvReader.read(ByteArrayInputStream(xml.toByteArray()), System.currentTimeMillis()).names["bollygold"])
        val internal = "<!DOCTYPE tv [<!ENTITY secret 'secret'>]><tv/>"
        assertTrue(runCatching { AndroidXmltvReader.read(ByteArrayInputStream(internal.toByteArray()), System.currentTimeMillis()) }.isFailure)
    }
    @Test fun desktopLoopbackEpgAddressesUseSyncHostWithoutChangingEscapedCredentials() {
        assertEquals("http://192.0.2.10:8181/epg.xml?key=a%2Bb", AndroidEpgRepository.resolveDesktopSource("http://127.0.0.1:8181/epg.xml?key=a%2Bb", "192.0.2.10"))
        assertEquals("https://user%2Bname:secret%26value@desktop.example:8181/epg.xml", AndroidEpgRepository.resolveDesktopSource("https://user%2Bname:secret%26value@localhost:8181/epg.xml", "desktop.example"))
        assertEquals("http://192.0.2.10:8181/epg.xml", AndroidEpgRepository.resolveDesktopSource("http://[::1]:8181/epg.xml", "192.0.2.10"))
        assertEquals("https://example.test/epg.xml", AndroidEpgRepository.resolveDesktopSource("https://example.test/epg.xml", "desktop.example"))
        assertEquals("http://127.0.0.1/epg.xml", AndroidEpgRepository.resolveDesktopSource("http://127.0.0.1/epg.xml", ""))
    }

    @Test fun nativeXtremeUsesStreamIdentityCachesAndFallsBackBetweenActions() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helper = AndroidUiptvDatabaseHelper(context)
        val preferences = AndroidDataStorePreferencesRepository(context)
        preferences.saveEpgEnabled(true)
        preferences.saveRemoteEndpoint("", 8888)
        val now = System.currentTimeMillis() / 1000
        val body = "{\"epg_listings\":[{\"title\":\"TmF0aXZl\",\"start_timestamp\":${now - 60},\"stop_timestamp\":${now + 3600}}]}"
        com.uiptv.mobile.android.AndroidUiptvDatabaseCompatibilityTest.TestHttpServer(
            mapOf("get_simple_data_table" to "{}", "get_short_epg" to body)
        ).use { server ->
            val accounts = AndroidSQLiteAccountRepository(helper)
            val account = accounts.saveAccount(MobileAccount(accountName = "EPG Xtreme fixture", type = MobileAccountType.XTREME_API,
                url = "http://127.0.0.1:${server.port}", username = "user+name", password = "secret&value"))
            try {
                val repository = AndroidEpgRepository(context, helper, preferences)
                assertEquals("Native", repository.schedule(account.id!!, "provider-stream", "Station").programmes.first().title)
                assertEquals("Native", repository.schedule(account.id!!, "provider-stream", "Station").programmes.first().title)
                assertEquals(2, server.requests.size)
                assertTrue(server.requests.all { it.contains("stream_id=provider-stream") })
                assertTrue(server.requests.all { it.contains("username=user%2Bname") && it.contains("password=secret%26value") })
            } finally { preferences.saveEpgEnabled(false); accounts.deleteAccount(account.id!!); helper.close() }
        }
    }
    @Test fun decompressedStreamAllows200MiBThenRejectsAdditionalBytes() {
        val type = AndroidEpgRepository::class.java.declaredClasses.first { it.simpleName == "LimitedStream" }
        val constructor = type.getDeclaredConstructor(java.io.InputStream::class.java).apply { isAccessible = true }
        val synthetic = object : java.io.InputStream() {
            override fun read(): Int = 0
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = length
        }
        val stream = constructor.newInstance(synthetic) as java.io.InputStream
        stream.use {
            val buffer = ByteArray(64 * 1024)
            repeat(3200) { assertEquals(buffer.size, stream.read(buffer)) }
            try { stream.read(); fail("Expected the 200 MiB decompressed limit") }
            catch (error: java.io.IOException) { assertEquals("Guide exceeds size limit", error.message) }
        }
    }

    @Test fun downloadedXmltvPersistsAcrossRestartExpiresAndRetainsLastValidFeed() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helper = AndroidUiptvDatabaseHelper(context)
        val preferences = AndroidDataStorePreferencesRepository(context)
        preferences.saveEpgEnabled(true)
        preferences.saveRemoteEndpoint("", 8888)
        val now = Instant.now(); val format = DateTimeFormatter.ofPattern("uuuuMMddHHmmss xx").withZone(ZoneOffset.UTC)
        val xml = "<tv><programme channel='stream-id' start='${format.format(now.minusSeconds(60))}' stop='${format.format(now.plusSeconds(3600))}'><title>Disk fixture</title></programme></tv>"
        val responses = mutableMapOf("xml" to xml)
        com.uiptv.mobile.android.AndroidUiptvDatabaseCompatibilityTest.TestHttpServer(responses).use { server ->
            val accounts = AndroidSQLiteAccountRepository(helper)
            val account = accounts.saveAccount(MobileAccount(accountName = "Disk EPG fixture", type = MobileAccountType.M3U8_LOCAL))
            val repository = AndroidEpgRepository(context, helper, preferences)
            val source = "http://127.0.0.1:${server.port}/guide?action=xml&secret=private"
            val field = AndroidEpgRepository::class.java.getDeclaredField("guides").apply { isAccessible = true }
            fun discardMemory() {
                (field.get(null) as MutableMap<*, *>).clear()
                com.uiptv.mobile.shared.epg.MobileEpgCache.clear(account.id!!, "stream-id")
            }
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            val cached = File(context.filesDir, "epg/epg-$hash.xml")
            try {
                repository.clearCache(); repository.saveGlobalSource(source)
                assertEquals("Disk fixture", repository.schedule(account.id!!, "stream-id", "Station").programmes.first().title)
                assertTrue(cached.isFile); assertFalse(cached.name.contains("private"))
                discardMemory()
                val restarted = AndroidEpgRepository(context, helper, preferences)
                assertEquals("Disk fixture", restarted.schedule(account.id!!, "stream-id", "Station").programmes.first().title)
                assertEquals(1, server.requests.size)
                cached.setLastModified(System.currentTimeMillis() - 25 * 3600_000L); discardMemory()
                assertEquals("Disk fixture", restarted.schedule(account.id!!, "stream-id", "Station").programmes.first().title)
                assertEquals(2, server.requests.size)
                responses["xml"] = "invalid XML"
                cached.setLastModified(System.currentTimeMillis() - 25 * 3600_000L); discardMemory()
                assertEquals(MobileEpgStatus.STALE, restarted.schedule(account.id!!, "stream-id", "Station").status)
                assertEquals(xml, cached.readText())
                restarted.clearCache(); assertFalse(cached.exists())
            } finally { repository.clearCache(); preferences.saveEpgEnabled(false); accounts.deleteAccount(account.id!!); helper.close() }
        }
    }

    @Test fun olderConfigurationMigratesEveryEpgColumnOff() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helper = AndroidUiptvDatabaseHelper(context)
        try {
            val db = helper.writableDatabase
            val kept = com.uiptv.mobile.shared.db.UiptvSyncSchema.configurationColumns
                .filter { it !in setOf("epgEnabled", "globalEpgSource", "epgMappingsJson") }.joinToString(",") { "\"$it\"" }
            db.execSQL("CREATE TABLE Configuration_old AS SELECT $kept FROM Configuration")
            db.execSQL("DROP TABLE Configuration")
            db.execSQL("ALTER TABLE Configuration_old RENAME TO Configuration")
            db.execSQL("DELETE FROM schema_migrations WHERE name='0208_add_configuration_epg.sql'")
            helper.applyMigrations(db)
            val actual = mutableSetOf<String>()
            db.rawQuery("PRAGMA table_info(Configuration)", null).use { cursor ->
                while (cursor.moveToNext()) actual += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
            assertTrue(actual.containsAll(setOf("epgEnabled", "globalEpgSource", "epgMappingsJson")))
        } finally { helper.close() }
    }
    @Test fun defaultOffIndependentEnablementFallbackAndMappingPersist() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helper = AndroidUiptvDatabaseHelper(context)
        val preferences = AndroidDataStorePreferencesRepository(context)
        preferences.saveEpgEnabled(false)
        val repository = AndroidEpgRepository(context, helper, preferences)
        val accounts = AndroidSQLiteAccountRepository(helper)
        val account = accounts.saveAccount(MobileAccount(accountName = "EPG fixture", type = MobileAccountType.M3U8_LOCAL,
            m3u8Path = File(context.cacheDir, "not-present.m3u").absolutePath))
        val source = File(context.cacheDir, "epg-global-fixture.xml")
        val unmatched = File(context.cacheDir, "epg-unmatched-fixture.xml").apply { writeText("<tv/>") }
        val globalSources = unmatched.absolutePath + "\n" + source.absolutePath
        val now = Instant.now(); val format = DateTimeFormatter.ofPattern("uuuuMMddHHmmss xx").withZone(ZoneOffset.UTC)
        source.writeText("<tv><programme channel='mapped' start='${format.format(now.minusSeconds(60))}' stop='${format.format(now.plusSeconds(3600))}'><title>Global fixture</title></programme></tv>")
        try {
            repository.saveGlobalSource(globalSources)
            assertEquals(MobileEpgStatus.DISABLED, repository.schedule(account.id!!, "stream-id", "Station").status)
            preferences.saveEpgEnabled(true)
        preferences.saveRemoteEndpoint("", 8888)
            assertEquals(MobileEpgStatus.NO_MATCH, repository.schedule(account.id!!, "stream-id", "Station").status)
            repository.saveMapping(account.id!!, "stream-id", "mapped")
            val fresh = AndroidEpgRepository(context, helper, AndroidDataStorePreferencesRepository(context))
            assertEquals("Global fixture", fresh.schedule(account.id!!, "stream-id", "Station").programmes.first().title)
            source.writeText(source.readText().replace("Global fixture", "Refreshed fixture"))
            fresh.clearCache()
            assertEquals("Refreshed fixture", fresh.schedule(account.id!!, "stream-id", "Station").programmes.first().title)
            helper.readableDatabase.rawQuery("SELECT epgEnabled FROM Configuration LIMIT 1", null).use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals("0", cursor.getString(0))
            }
            preferences.saveEpgEnabled(false)
            assertEquals(MobileEpgStatus.DISABLED, fresh.schedule(account.id!!, "stream-id", "Station").status)
            assertEquals(globalSources, fresh.globalSource())
        } finally {
            source.delete(); unmatched.delete(); accounts.deleteAccount(account.id!!); helper.close()
        }
    }
}
