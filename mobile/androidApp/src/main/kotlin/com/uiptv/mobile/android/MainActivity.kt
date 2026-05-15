package com.uiptv.mobile.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.uiptv.mobile.shared.accounts.AndroidSQLiteAccountRepository
import com.uiptv.mobile.shared.browse.AndroidSQLiteBrowseRepository
import com.uiptv.mobile.shared.cache.AndroidCacheRefreshScheduler
import com.uiptv.mobile.shared.db.AndroidDatabaseBackupManager
import com.uiptv.mobile.shared.db.AndroidLocalDataResetter
import com.uiptv.mobile.shared.db.AndroidSQLiteSnapshotSyncApplier
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import com.uiptv.mobile.shared.playback.PlayerChoice
import com.uiptv.mobile.shared.settings.AndroidDataStorePreferencesRepository
import com.uiptv.mobile.shared.settings.AndroidSQLiteFilterSettingsRepository
import com.uiptv.mobile.shared.settings.MobileBackupArchive
import com.uiptv.mobile.shared.sync.AndroidRemoteSyncClient
import com.uiptv.mobile.shared.sync.AndroidRemoteSyncPullService
import com.uiptv.mobile.shared.ui.AccountUiActions
import com.uiptv.mobile.shared.ui.BackupRestoreUiActions
import com.uiptv.mobile.shared.ui.BrowseUiActions
import com.uiptv.mobile.shared.ui.DefaultPlayerIcon
import com.uiptv.mobile.shared.ui.FilterUiActions
import com.uiptv.mobile.shared.ui.PlaybackUiActions
import com.uiptv.mobile.shared.ui.UiptvMobileApp
import com.uiptv.mobile.shared.ui.RemoteSyncUiActions

class MainActivity : ComponentActivity() {
    private var appResumeSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        val preferences = AndroidDataStorePreferencesRepository(this)
        val databaseHelper = AndroidUiptvDatabaseHelper(this)
        val accountRepository = AndroidSQLiteAccountRepository(databaseHelper)
        val browseRepository = AndroidSQLiteBrowseRepository(databaseHelper)
        val filterRepository = AndroidSQLiteFilterSettingsRepository(databaseHelper)
        val backupManager = AndroidDatabaseBackupManager(this, databaseHelper)
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
            var pendingBackupSelection by remember { mutableStateOf<((String?) -> Unit)?>(null) }
            var pendingRestoreSelection by remember { mutableStateOf<((String?) -> Unit)?>(null) }
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
            val backupLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument(MobileBackupArchive.MIME_TYPE)
            ) { uri: Uri? ->
                pendingBackupSelection?.invoke(uri?.toString())
                pendingBackupSelection = null
            }
            val restoreLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                pendingRestoreSelection?.invoke(uri?.toString())
                pendingRestoreSelection = null
            }

            UiptvMobileApp(
                resumeSignal = appResumeSignal,
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
                    listWatchingNow = browseRepository::listWatchingNow,
                    listWatchingNowEpisodes = browseRepository::listWatchingNowEpisodes,
                    enrichWatchingNowItem = browseRepository::enrichWatchingNowItem,
                    enrichSeriesDetails = browseRepository::enrichSeriesDetails,
                    markWatchingNowEpisode = browseRepository::markWatchingNowEpisode,
                    clearWatchingNowEpisode = browseRepository::clearWatchingNowEpisode,
                    removeWatchingNow = browseRepository::removeWatchingNow
                ),
                playbackActions = PlaybackUiActions(
                    loadPlayerPreference = playbackCoordinator::loadPlayerPreference,
                    playerChoices = playbackCoordinator::playerChoices,
                    playBrowseItem = playbackCoordinator::playBrowseItem,
                    playBookmark = playbackCoordinator::playBookmark,
                    playWatchingNow = playbackCoordinator::playWatchingNow,
                    playWatchingNowEpisode = playbackCoordinator::playWatchingNowEpisode,
                    playBingeWatchSeason = playbackCoordinator::playBingeWatchSeason,
                    openPlayerInstall = playbackCoordinator::openPlayerInstall,
                    savePlayerPreference = playbackCoordinator::savePlayerPreference,
                    clearPlayerPreference = playbackCoordinator::clearPlayerPreference
                ),
                filterActions = FilterUiActions(
                    load = filterRepository::load,
                    save = filterRepository::save,
                    setPaused = filterRepository::setPaused,
                    setEnableThumbnails = filterRepository::setEnableThumbnails
                ),
                backupRestoreActions = BackupRestoreUiActions(
                    backupToUri = backupManager::backupToUri,
                    restoreFromUri = backupManager::restoreFromUri
                ),
                logoRenderer = { logoUrl, description, modifier ->
                    RemoteLogoImage(logoUrl, description, modifier)
                },
                playerIconRenderer = { choice, modifier ->
                    AndroidPlayerIcon(choice, modifier)
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
                backupFileCreator = { suggestedName, onSelected ->
                    pendingBackupSelection = onSelected
                    backupLauncher.launch(suggestedName)
                },
                restoreFilePicker = { onSelected ->
                    pendingRestoreSelection = onSelected
                    restoreLauncher.launch(
                        arrayOf(
                            MobileBackupArchive.MIME_TYPE,
                            "application/octet-stream",
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

    override fun onResume() {
        super.onResume()
        appResumeSignal += 1
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

@Composable
private fun AndroidPlayerIcon(choice: PlayerChoice, modifier: Modifier) {
    val context = LocalContext.current
    val iconBitmap = remember(choice.packageName, choice.installed) {
        if (choice.installed && choice.packageName.isNotBlank()) {
            loadInstalledPlayerIcon(context, choice.packageName)
        } else {
            null
        }
    }

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap.asImageBitmap(),
            contentDescription = choice.label,
            modifier = modifier.clip(RoundedCornerShape(14.dp)),
            contentScale = ContentScale.Fit
        )
    } else {
        DefaultPlayerIcon(choice, modifier)
    }
}

private fun loadInstalledPlayerIcon(context: Context, packageName: String): Bitmap? =
    runCatching {
        context.packageManager.getApplicationIcon(packageName).toBitmap()
    }.getOrNull()

private fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) {
        return bitmap
    }
    val width = intrinsicWidth.takeIf { it > 0 } ?: 96
    val height = intrinsicHeight.takeIf { it > 0 } ?: 96
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return output
}
