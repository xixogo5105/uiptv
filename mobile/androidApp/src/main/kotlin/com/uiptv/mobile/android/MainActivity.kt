package com.uiptv.mobile.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.uiptv.mobile.shared.accounts.AndroidSQLiteAccountRepository
import com.uiptv.mobile.shared.browse.AndroidSQLiteBrowseRepository
import com.uiptv.mobile.shared.cache.AndroidCacheRefreshScheduler
import com.uiptv.mobile.shared.db.AndroidLocalDataResetter
import com.uiptv.mobile.shared.db.AndroidSQLiteSnapshotSyncApplier
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import com.uiptv.mobile.shared.settings.AndroidDataStorePreferencesRepository
import com.uiptv.mobile.shared.settings.AndroidSQLiteFilterSettingsRepository
import com.uiptv.mobile.shared.sync.AndroidRemoteSyncClient
import com.uiptv.mobile.shared.sync.AndroidRemoteSyncPullService
import com.uiptv.mobile.shared.ui.AccountUiActions
import com.uiptv.mobile.shared.ui.BrowseUiActions
import com.uiptv.mobile.shared.ui.FilterUiActions
import com.uiptv.mobile.shared.ui.PlaybackUiActions
import com.uiptv.mobile.shared.ui.UiptvMobileApp
import com.uiptv.mobile.shared.ui.RemoteSyncUiActions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        val preferences = AndroidDataStorePreferencesRepository(this)
        val databaseHelper = AndroidUiptvDatabaseHelper(this)
        val accountRepository = AndroidSQLiteAccountRepository(databaseHelper)
        val browseRepository = AndroidSQLiteBrowseRepository(databaseHelper)
        val filterRepository = AndroidSQLiteFilterSettingsRepository(databaseHelper)
        val cacheScheduler = AndroidCacheRefreshScheduler(this)
        val localDataResetter = AndroidLocalDataResetter(databaseHelper)
        val playbackCoordinator = AndroidPlaybackCoordinator(this, preferences, databaseHelper)
        val syncService = AndroidRemoteSyncPullService(
            client = AndroidRemoteSyncClient(),
            snapshotApplier = AndroidSQLiteSnapshotSyncApplier(databaseHelper, cacheDir),
            preferences = preferences
        )

        setContent {
            var pendingLocalPlaylistSelection by remember { mutableStateOf<((String) -> Unit)?>(null) }
            val localPlaylistLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    pendingLocalPlaylistSelection?.invoke(uri.toString())
                }
                pendingLocalPlaylistSelection = null
            }

            UiptvMobileApp(
                syncActions = RemoteSyncUiActions(
                    loadPreferences = preferences::load,
                    checkConnection = syncService::checkConnection,
                    pullFromDesktop = syncService::pullFromDesktop,
                    resetLocalData = localDataResetter::resetAndVacuum
                ),
                accountActions = AccountUiActions(
                    loadAccounts = accountRepository::listAccounts,
                    saveAccount = accountRepository::saveAccount,
                    deleteAccount = accountRepository::deleteAccount,
                    clearCache = accountRepository::clearAccountCache,
                    clearAllCache = accountRepository::clearAllCache,
                    enqueueCacheJob = cacheScheduler::enqueue,
                    loadCacheJobState = cacheScheduler::state,
                    loadRecentCacheJobs = { cacheScheduler.recentStates() },
                    stopCacheJob = cacheScheduler::cancel
                ),
                browseActions = BrowseUiActions(
                    loadBrowse = browseRepository::loadBrowse,
                    listBookmarkCategories = browseRepository::listBookmarkCategories,
                    listBookmarks = browseRepository::listBookmarks,
                    toggleBookmark = browseRepository::toggleBookmark,
                    removeBookmark = browseRepository::removeBookmark,
                    listWatchingNow = browseRepository::listWatchingNow
                ),
                playbackActions = PlaybackUiActions(
                    loadPlayerPreference = playbackCoordinator::loadPlayerPreference,
                    playerChoices = playbackCoordinator::playerChoices,
                    playBrowseItem = playbackCoordinator::playBrowseItem,
                    playBookmark = playbackCoordinator::playBookmark,
                    playWatchingNow = playbackCoordinator::playWatchingNow,
                    savePlayerPreference = playbackCoordinator::savePlayerPreference,
                    clearPlayerPreference = playbackCoordinator::clearPlayerPreference
                ),
                filterActions = FilterUiActions(
                    load = filterRepository::load,
                    save = filterRepository::save,
                    setPaused = filterRepository::setPaused,
                    setEnableThumbnails = filterRepository::setEnableThumbnails
                ),
                logoRenderer = { logoUrl, description, modifier ->
                    RemoteLogoImage(logoUrl, description, modifier)
                },
                localPlaylistPicker = { onSelected ->
                    pendingLocalPlaylistSelection = onSelected
                    localPlaylistLauncher.launch(
                        arrayOf(
                            "application/vnd.apple.mpegurl",
                            "application/x-mpegurl",
                            "audio/mpegurl",
                            "audio/x-mpegurl",
                            "text/plain",
                            "*/*"
                        )
                    )
                },
                backHandler = { enabled, onBack ->
                    BackHandler(enabled = enabled, onBack = onBack)
                }
            )
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
}
