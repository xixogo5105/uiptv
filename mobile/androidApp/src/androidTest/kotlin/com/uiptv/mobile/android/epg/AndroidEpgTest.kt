package com.uiptv.mobile.android.epg

import androidx.test.platform.app.InstrumentationRegistry
import com.uiptv.mobile.shared.epg.*
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import com.uiptv.mobile.shared.settings.AndroidDataStorePreferencesRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class AndroidEpgTest {
    @Test fun cancellationStopsXmltvParsingInsideBufferedText() {
        val xml = "<tv><channel id='station'><display-name>" +
            (1..500).joinToString("") { "<![CDATA[Station]]>" } +
            "</display-name></channel></tv>"
        var checks = 0
        val failure = runCatching {
            AndroidXmltvReader.read(
                xml.byteInputStream(), System.currentTimeMillis(),
                checkCancelled = {
                    if (++checks == 25) throw kotlinx.coroutines.CancellationException("Guide closed")
                }
            )
        }.exceptionOrNull()
        assertTrue(failure is kotlinx.coroutines.CancellationException)
        assertEquals(25, checks)
    }

    @Test fun accountGuideIsFetchedAgainAndNoCacheFileOrEpgTableIsCreated() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helper = AndroidUiptvDatabaseHelper(context)
        val preferences = AndroidDataStorePreferencesRepository(context)
        val wasEnabled = preferences.load().epgEnabled
        val accountId = 987654322L
        val source = File.createTempFile("live-guide-", ".xml", context.cacheDir)
        val format = DateTimeFormatter.ofPattern("uuuuMMddHHmmss Z").withZone(ZoneOffset.UTC)
        fun xml(title: String): String {
            val now = Instant.now()
            return "<tv><channel id='station'><display-name>Station</display-name></channel>" +
                "<programme channel='station' start='${format.format(now.minusSeconds(60))}' stop='${format.format(now.plusSeconds(3600))}'><title>$title</title></programme></tv>"
        }
        val db = helper.writableDatabase
        try {
            preferences.saveEpgEnabled(true)
            db.execSQL("INSERT INTO Account(id,accountName,type,url,username,password,macAddress,epg,m3u8Path) VALUES(?,?,'M3U8_LOCAL','','','', '',?,'')",
                arrayOf<Any>(accountId, "Live EPG fixture", source.absolutePath))
            val repository = AndroidEpgRepository(context, helper, preferences)
            source.writeText(xml("First programme"))
            assertEquals("First programme", repository.schedule(accountId, "station", "Station").programmes.first().title)
            source.writeText(xml("Second programme"))
            assertEquals("Second programme", repository.schedule(accountId, "station", "Station").programmes.first().title)
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND (name GLOB 'epg_*' OR name='source_blacklist')", null).use {
                assertFalse(it.moveToFirst())
            }
            assertFalse(File(context.filesDir, "epg").exists())
            preferences.saveEpgEnabled(false)
            assertEquals(MobileEpgStatus.DISABLED, repository.schedule(accountId, "station", "Station").status)
        } finally {
            db.delete("Account", "id=?", arrayOf(accountId.toString()))
            preferences.saveEpgEnabled(wasEnabled)
            source.delete(); helper.close()
        }
    }
}
