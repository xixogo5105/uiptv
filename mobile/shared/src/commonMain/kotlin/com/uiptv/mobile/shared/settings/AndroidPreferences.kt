package com.uiptv.mobile.shared.settings

data class RemoteEndpointPreference(
    val host: String = "",
    val port: Int = 8888,
    val lastSuccessfulSyncEpochSeconds: Long? = null
)

enum class AndroidPlayerPreference {
    ASK_EVERY_TIME,
    EMBEDDED_PLAYER,
    NATIVE,
    VLC,
    MX_PLAYER_PRO,
    MX_PLAYER_FREE,
    KODI,
    JUST_PLAYER,
    XPLAYER,
    SYSTEM_CHOOSER
}

data class PlayerPreference(
    val selectedPlayer: AndroidPlayerPreference = AndroidPlayerPreference.EMBEDDED_PLAYER,
    val packageName: String = "",
    val rememberForFutureStreams: Boolean = true
)

data class EmbeddedPlayerPreference(
    val repeatReconnect: Boolean = false,
    val muted: Boolean = false
)

data class AndroidPreferenceSnapshot(
    val remoteEndpoint: RemoteEndpointPreference = RemoteEndpointPreference(),
    val playerPreference: PlayerPreference = PlayerPreference(),
    val embeddedPlayerPreference: EmbeddedPlayerPreference = EmbeddedPlayerPreference(),
    val firstRunCompleted: Boolean = false
)

interface AndroidPreferencesRepository {
    suspend fun load(): AndroidPreferenceSnapshot

    suspend fun saveRemoteEndpoint(host: String, port: Int)

    suspend fun markRemoteSyncSucceeded(epochSeconds: Long)

    suspend fun savePlayerPreference(preference: PlayerPreference)

    suspend fun saveEmbeddedPlayerPreference(preference: EmbeddedPlayerPreference)

    suspend fun setFirstRunCompleted(completed: Boolean)
}

object AndroidOnlyPreferenceKeys {
    const val REMOTE_HOST = "remote_host"
    const val REMOTE_PORT = "remote_port"
    const val LAST_SUCCESSFUL_SYNC_EPOCH_SECONDS = "last_successful_sync_epoch_seconds"
    const val PLAYER_TYPE = "player_type"
    const val PLAYER_PACKAGE = "player_package"
    const val PLAYER_REMEMBER = "player_remember"
    const val EMBEDDED_PLAYER_REPEAT_RECONNECT = "embedded_player_repeat_reconnect"
    const val EMBEDDED_PLAYER_MUTED = "embedded_player_muted"
    const val FIRST_RUN_COMPLETED = "first_run_completed"

    val all: Set<String> = setOf(
        REMOTE_HOST,
        REMOTE_PORT,
        LAST_SUCCESSFUL_SYNC_EPOCH_SECONDS,
        PLAYER_TYPE,
        PLAYER_PACKAGE,
        PLAYER_REMEMBER,
        EMBEDDED_PLAYER_REPEAT_RECONNECT,
        EMBEDDED_PLAYER_MUTED,
        FIRST_RUN_COMPLETED
    )
}
