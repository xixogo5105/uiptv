package com.uiptv.mobile.shared.settings

import com.uiptv.mobile.shared.db.UiptvSyncSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidPreferencesTest {
    @Test
    fun defaultRemoteEndpointIsBlankHostWithDefaultPort() {
        val endpoint = RemoteEndpointPreference()

        assertEquals("", endpoint.host)
        assertEquals(8888, endpoint.port)
        assertNull(endpoint.lastSuccessfulSyncEpochSeconds)
    }

    @Test
    fun defaultPlayerPreferenceUsesEmbeddedPlayerAndRemembersIt() {
        val preference = PlayerPreference()

        assertEquals(AndroidPlayerPreference.EMBEDDED_PLAYER, preference.selectedPlayer)
        assertEquals("", preference.packageName)
        assertTrue(preference.rememberForFutureStreams)
    }

    @Test
    fun snapshotDefaultsAreSafeForFreshInstall() {
        val snapshot = AndroidPreferenceSnapshot()

        assertEquals(RemoteEndpointPreference(), snapshot.remoteEndpoint)
        assertEquals(PlayerPreference(), snapshot.playerPreference)
        assertEquals(EmbeddedPlayerPreference(), snapshot.embeddedPlayerPreference)
        assertEquals(PanelVisibilityPreference(), snapshot.panelVisibilityPreference)
        assertFalse(snapshot.firstRunCompleted)
    }

    @Test
    fun androidOnlyPreferenceKeysContainEveryPersistedKey() {
        val expected = setOf(
            AndroidOnlyPreferenceKeys.REMOTE_HOST,
            AndroidOnlyPreferenceKeys.REMOTE_PORT,
            AndroidOnlyPreferenceKeys.LAST_SUCCESSFUL_SYNC_EPOCH_SECONDS,
            AndroidOnlyPreferenceKeys.PLAYER_TYPE,
            AndroidOnlyPreferenceKeys.PLAYER_PACKAGE,
            AndroidOnlyPreferenceKeys.PLAYER_REMEMBER,
            AndroidOnlyPreferenceKeys.EMBEDDED_PLAYER_REPEAT_RECONNECT,
            AndroidOnlyPreferenceKeys.EMBEDDED_PLAYER_MUTED,
            AndroidOnlyPreferenceKeys.PANEL_BOOKMARKS_CATEGORY_VISIBLE,
            AndroidOnlyPreferenceKeys.PANEL_WATCHING_NOW_DETAILS_VISIBLE,
            AndroidOnlyPreferenceKeys.PANEL_ACCOUNTS_ACTIONS_VISIBLE,
            AndroidOnlyPreferenceKeys.FIRST_RUN_COMPLETED
        )

        assertEquals(expected, AndroidOnlyPreferenceKeys.all)
    }

    @Test
    fun playerPreferenceIsAndroidOnlyData() {
        assertTrue(AndroidOnlyPreferenceKeys.PLAYER_TYPE in AndroidOnlyPreferenceKeys.all)
        assertTrue(AndroidOnlyPreferenceKeys.PLAYER_PACKAGE in AndroidOnlyPreferenceKeys.all)
        assertTrue(AndroidOnlyPreferenceKeys.PLAYER_REMEMBER in AndroidOnlyPreferenceKeys.all)
        assertTrue(AndroidOnlyPreferenceKeys.EMBEDDED_PLAYER_REPEAT_RECONNECT in AndroidOnlyPreferenceKeys.all)
        assertTrue(AndroidOnlyPreferenceKeys.EMBEDDED_PLAYER_MUTED in AndroidOnlyPreferenceKeys.all)
        assertFalse(UiptvSyncSchema.syncableTables.any { it.contains("Player", ignoreCase = true) })
    }

    @Test
    fun androidOnlyPreferenceKeysDoNotOverlapPortableConfigurationColumns() {
        assertTrue(AndroidOnlyPreferenceKeys.all.isNotEmpty())
        assertTrue(AndroidOnlyPreferenceKeys.all.none { key ->
            key in UiptvSyncSchema.androidPortableConfigurationColumns
        })
        assertTrue("defaultPlayerPath" in UiptvSyncSchema.androidNeverSyncConfigurationColumns)
        assertTrue("serverPort" in UiptvSyncSchema.androidNeverSyncConfigurationColumns)
    }
}
