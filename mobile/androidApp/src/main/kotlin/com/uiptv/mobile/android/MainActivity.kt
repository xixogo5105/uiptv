package com.uiptv.mobile.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uiptv.mobile.shared.accounts.AndroidSQLiteAccountRepository
import com.uiptv.mobile.shared.browse.AndroidSQLiteBrowseRepository
import com.uiptv.mobile.shared.cache.AndroidCacheRefreshScheduler
import com.uiptv.mobile.shared.db.AndroidLocalDataResetter
import com.uiptv.mobile.shared.db.AndroidSQLiteSnapshotSyncApplier
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import com.uiptv.mobile.shared.playback.PlayerChoice
import com.uiptv.mobile.shared.settings.AndroidPlayerPreference
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
        AndroidPlayerFallbackIcon(choice, modifier)
    }
}

@Composable
private fun AndroidPlayerFallbackIcon(choice: PlayerChoice, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = choice.player.androidPlayerIconColor(),
        contentColor = choice.player.androidPlayerIconContentColor()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = choice.player.androidPlayerBadge(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
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

private fun AndroidPlayerPreference.androidPlayerBadge(): String =
    when (this) {
        AndroidPlayerPreference.ASK_EVERY_TIME -> "?"
        AndroidPlayerPreference.EMBEDDED_PLAYER -> "EP"
        AndroidPlayerPreference.NATIVE -> "AM"
        AndroidPlayerPreference.VLC -> "VLC"
        AndroidPlayerPreference.MX_PLAYER_PRO,
        AndroidPlayerPreference.MX_PLAYER_FREE -> "MX"
        AndroidPlayerPreference.KODI -> "K"
        AndroidPlayerPreference.JUST_PLAYER -> "JP"
        AndroidPlayerPreference.XPLAYER -> "XP"
        AndroidPlayerPreference.SYSTEM_CHOOSER -> "SYS"
    }

private fun AndroidPlayerPreference.androidPlayerIconColor(): Color =
    when (this) {
        AndroidPlayerPreference.ASK_EVERY_TIME -> Color(0xFF374151)
        AndroidPlayerPreference.EMBEDDED_PLAYER -> Color(0xFF4FD8EB)
        AndroidPlayerPreference.NATIVE -> Color(0xFF7DD3FC)
        AndroidPlayerPreference.VLC -> Color(0xFFFF9800)
        AndroidPlayerPreference.MX_PLAYER_PRO,
        AndroidPlayerPreference.MX_PLAYER_FREE -> Color(0xFF2563EB)
        AndroidPlayerPreference.KODI -> Color(0xFF2F9ED8)
        AndroidPlayerPreference.JUST_PLAYER -> Color(0xFF111827)
        AndroidPlayerPreference.XPLAYER -> Color(0xFFE11D48)
        AndroidPlayerPreference.SYSTEM_CHOOSER -> Color(0xFF64748B)
    }

private fun AndroidPlayerPreference.androidPlayerIconContentColor(): Color =
    when (this) {
        AndroidPlayerPreference.EMBEDDED_PLAYER,
        AndroidPlayerPreference.NATIVE -> Color(0xFF001F25)
        else -> Color.White
    }
