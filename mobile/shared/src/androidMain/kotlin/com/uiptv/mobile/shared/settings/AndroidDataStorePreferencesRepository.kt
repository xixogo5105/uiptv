package com.uiptv.mobile.shared.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.uiptvPreferencesDataStore by preferencesDataStore(name = "uiptv_android_preferences")

class AndroidDataStorePreferencesRepository(
    context: Context
) : AndroidPreferencesRepository {
    private val dataStore = context.applicationContext.uiptvPreferencesDataStore

    override suspend fun load(): AndroidPreferenceSnapshot {
        val values = dataStore.data.first()
        val playerType = values[PLAYER_TYPE]
            ?.let { runCatching { AndroidPlayerPreference.valueOf(it) }.getOrNull() }
            ?: AndroidPlayerPreference.EMBEDDED_PLAYER

        return AndroidPreferenceSnapshot(
            remoteEndpoint = RemoteEndpointPreference(
                host = values[REMOTE_HOST].orEmpty(),
                port = values[REMOTE_PORT] ?: 8888,
                lastSuccessfulSyncEpochSeconds = values[LAST_SUCCESSFUL_SYNC]
            ),
            playerPreference = PlayerPreference(
                selectedPlayer = playerType,
                packageName = values[PLAYER_PACKAGE].orEmpty(),
                rememberForFutureStreams = values[PLAYER_REMEMBER] ?: true
            ),
            embeddedPlayerPreference = EmbeddedPlayerPreference(
                repeatReconnect = values[EMBEDDED_PLAYER_REPEAT_RECONNECT] ?: false,
                muted = values[EMBEDDED_PLAYER_MUTED] ?: false
            ),
            panelVisibilityPreference = PanelVisibilityPreference(
                bookmarksCategoryPanelVisible = values[PANEL_BOOKMARKS_CATEGORY_VISIBLE] ?: false,
                watchingNowDetailsPanelVisible = values[PANEL_WATCHING_NOW_DETAILS_VISIBLE] ?: true,
                accountsActionsPanelVisible = values[PANEL_ACCOUNTS_ACTIONS_VISIBLE] ?: false
            ),
            firstRunCompleted = values[FIRST_RUN_COMPLETED] ?: false
        )
    }

    override suspend fun saveRemoteEndpoint(host: String, port: Int) {
        dataStore.edit { values ->
            values[REMOTE_HOST] = host.trim()
            values[REMOTE_PORT] = port
        }
    }

    override suspend fun markRemoteSyncSucceeded(epochSeconds: Long) {
        dataStore.edit { values ->
            values[LAST_SUCCESSFUL_SYNC] = epochSeconds
        }
    }

    override suspend fun savePlayerPreference(preference: PlayerPreference) {
        dataStore.edit { values ->
            values[PLAYER_TYPE] = preference.selectedPlayer.name
            values[PLAYER_PACKAGE] = preference.packageName
            values[PLAYER_REMEMBER] = preference.rememberForFutureStreams
        }
    }

    override suspend fun saveEmbeddedPlayerPreference(preference: EmbeddedPlayerPreference) {
        dataStore.edit { values ->
            values[EMBEDDED_PLAYER_REPEAT_RECONNECT] = preference.repeatReconnect
            values[EMBEDDED_PLAYER_MUTED] = preference.muted
        }
    }

    override suspend fun savePanelVisibilityPreference(preference: PanelVisibilityPreference) {
        dataStore.edit { values ->
            values[PANEL_BOOKMARKS_CATEGORY_VISIBLE] = preference.bookmarksCategoryPanelVisible
            values[PANEL_WATCHING_NOW_DETAILS_VISIBLE] = preference.watchingNowDetailsPanelVisible
            values[PANEL_ACCOUNTS_ACTIONS_VISIBLE] = preference.accountsActionsPanelVisible
        }
    }

    override suspend fun setFirstRunCompleted(completed: Boolean) {
        dataStore.edit { values ->
            values[FIRST_RUN_COMPLETED] = completed
        }
    }

    private companion object {
        val REMOTE_HOST = stringPreferencesKey(AndroidOnlyPreferenceKeys.REMOTE_HOST)
        val REMOTE_PORT = intPreferencesKey(AndroidOnlyPreferenceKeys.REMOTE_PORT)
        val LAST_SUCCESSFUL_SYNC = longPreferencesKey(AndroidOnlyPreferenceKeys.LAST_SUCCESSFUL_SYNC_EPOCH_SECONDS)
        val PLAYER_TYPE = stringPreferencesKey(AndroidOnlyPreferenceKeys.PLAYER_TYPE)
        val PLAYER_PACKAGE = stringPreferencesKey(AndroidOnlyPreferenceKeys.PLAYER_PACKAGE)
        val PLAYER_REMEMBER = booleanPreferencesKey(AndroidOnlyPreferenceKeys.PLAYER_REMEMBER)
        val EMBEDDED_PLAYER_REPEAT_RECONNECT = booleanPreferencesKey(AndroidOnlyPreferenceKeys.EMBEDDED_PLAYER_REPEAT_RECONNECT)
        val EMBEDDED_PLAYER_MUTED = booleanPreferencesKey(AndroidOnlyPreferenceKeys.EMBEDDED_PLAYER_MUTED)
        val PANEL_BOOKMARKS_CATEGORY_VISIBLE = booleanPreferencesKey(AndroidOnlyPreferenceKeys.PANEL_BOOKMARKS_CATEGORY_VISIBLE)
        val PANEL_WATCHING_NOW_DETAILS_VISIBLE = booleanPreferencesKey(AndroidOnlyPreferenceKeys.PANEL_WATCHING_NOW_DETAILS_VISIBLE)
        val PANEL_ACCOUNTS_ACTIONS_VISIBLE = booleanPreferencesKey(AndroidOnlyPreferenceKeys.PANEL_ACCOUNTS_ACTIONS_VISIBLE)
        val FIRST_RUN_COMPLETED = booleanPreferencesKey(AndroidOnlyPreferenceKeys.FIRST_RUN_COMPLETED)
    }
}
