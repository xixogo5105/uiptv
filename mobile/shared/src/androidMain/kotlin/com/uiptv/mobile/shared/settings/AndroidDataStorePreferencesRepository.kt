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
        val FIRST_RUN_COMPLETED = booleanPreferencesKey(AndroidOnlyPreferenceKeys.FIRST_RUN_COMPLETED)
    }
}
