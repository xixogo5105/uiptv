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
        }
    }
    @Test fun rejectsDtdAndInvalidCalendarDates() {
        val xml = "<!DOCTYPE tv [<!ENTITY x SYSTEM 'file:///private/secret'>]><tv><channel id='x'><display-name>&x;</display-name></channel></tv>"
        val result = runCatching { AndroidXmltvReader.read(ByteArrayInputStream(xml.toByteArray()), System.currentTimeMillis()) }
        assertTrue(result.isFailure || result.getOrNull()?.names?.values?.none { it.contains("secret") } == true)
        assertEquals(0L, AndroidXmltvReader.time("20260230180000 +0000"))
    }
    @Test fun nativeXtremeUsesStreamIdentityCachesAndFallsBackBetweenActions() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helper = AndroidUiptvDatabaseHelper(context)
        val preferences = AndroidDataStorePreferencesRepository(context)
        preferences.saveEpgEnabled(true)
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
        val now = Instant.now(); val format = DateTimeFormatter.ofPattern("uuuuMMddHHmmss xx").withZone(ZoneOffset.UTC)
        val xml = "<tv><programme channel='stream-id' start='${format.format(now.minusSeconds(60))}' stop='${format.format(now.plusSeconds(3600))}'><title>Disk fixture</title></programme></tv>"
        val responses = mutableMapOf("xml" to xml)
        com.uiptv.mobile.android.AndroidUiptvDatabaseCompatibilityTest.TestHttpServer(responses).use { server ->
            val accounts = AndroidSQLiteAccountRepository(helper)
            val account = accounts.saveAccount(MobileAccount(accountName = "Disk EPG fixture", type = MobileAccountType.M3U8_LOCAL))
            val repository = AndroidEpgRepository(context, helper, preferences)
            val source = "http://127.0.0.1:${server.port}/guide?action=xml&secret=private"
            val field = AndroidEpgRepository::class.java.getDeclaredField("guides").apply { isAccessible = true }
            fun discardMemory() { (field.get(null) as MutableMap<*, *>).clear() }
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
