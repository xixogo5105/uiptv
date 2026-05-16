package com.uiptv.mobile.android.settings

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import com.uiptv.mobile.shared.db.UiptvSchemaInfo
import com.uiptv.mobile.shared.settings.AndroidDataStorePreferencesRepository
import com.uiptv.mobile.shared.settings.AndroidPlayerPreference
import com.uiptv.mobile.shared.settings.EmbeddedPlayerPreference
import com.uiptv.mobile.shared.settings.PlayerPreference
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class AndroidPreferencesRepositoryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "datastore/uiptv_android_preferences.preferences_pb").delete()
        context.deleteDatabase(UiptvSchemaInfo.DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(UiptvSchemaInfo.DATABASE_NAME)
        File(context.filesDir, "datastore/uiptv_android_preferences.preferences_pb").delete()
    }

    @Test
    fun storesRemoteEndpointAndPlayerPreferenceOutsideSharedDatabase() = runBlocking {
        val repository = AndroidDataStorePreferencesRepository(context)
        val defaults = repository.load()

        assertEquals(AndroidPlayerPreference.EMBEDDED_PLAYER, defaults.playerPreference.selectedPlayer)
        assertEquals(true, defaults.playerPreference.rememberForFutureStreams)
        assertEquals(false, defaults.embeddedPlayerPreference.repeatReconnect)
        assertEquals(false, defaults.embeddedPlayerPreference.muted)

        repository.saveRemoteEndpoint("192.168.1.20", 8888)
        repository.savePlayerPreference(
            PlayerPreference(
                selectedPlayer = AndroidPlayerPreference.VLC,
                packageName = "org.videolan.vlc",
                rememberForFutureStreams = true
            )
        )
        repository.saveEmbeddedPlayerPreference(
            EmbeddedPlayerPreference(
                repeatReconnect = true,
                muted = true
            )
        )
        repository.markRemoteSyncSucceeded(1_893_456_000L)
        repository.setFirstRunCompleted(true)

        val snapshot = repository.load()

        assertEquals("192.168.1.20", snapshot.remoteEndpoint.host)
        assertEquals(8888, snapshot.remoteEndpoint.port)
        assertEquals(1_893_456_000L, snapshot.remoteEndpoint.lastSuccessfulSyncEpochSeconds)
        assertEquals(AndroidPlayerPreference.VLC, snapshot.playerPreference.selectedPlayer)
        assertEquals("org.videolan.vlc", snapshot.playerPreference.packageName)
        assertEquals(true, snapshot.playerPreference.rememberForFutureStreams)
        assertEquals(true, snapshot.embeddedPlayerPreference.repeatReconnect)
        assertEquals(true, snapshot.embeddedPlayerPreference.muted)
        assertEquals(true, snapshot.firstRunCompleted)

        val columns = AndroidUiptvDatabaseHelper(context).use { helper ->
            helper.writableDatabase.rawQuery("PRAGMA table_info(Configuration)", null).use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val names = mutableSetOf<String>()
                while (cursor.moveToNext()) {
                    names += cursor.getString(nameIndex)
                }
                names
            }
        }

        assertFalse(columns.contains("androidDefaultPlayer"))
        assertFalse(columns.contains("androidPlayerPackage"))
        assertFalse(columns.contains("embeddedPlayerMuted"))
        assertFalse(columns.contains("lastDesktopHost"))
    }
}
