@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.uiptv.mobile.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiptv.mobile.shared.accounts.AccountCacheSummary
import com.uiptv.mobile.shared.accounts.MobileAccount
import com.uiptv.mobile.shared.accounts.MobileAccountType
import com.uiptv.mobile.shared.accounts.XtremeCredential
import com.uiptv.mobile.shared.accounts.withXtremeCredentialSelection
import com.uiptv.mobile.shared.accounts.xtremeCredentialOptions
import com.uiptv.mobile.shared.i18n.MobileMessages
import com.uiptv.mobile.shared.browse.BrowseAccountOption
import com.uiptv.mobile.shared.browse.BrowseMode
import com.uiptv.mobile.shared.browse.MobileCategoryCacheRemovalResult
import com.uiptv.mobile.shared.browse.MobileBookmark
import com.uiptv.mobile.shared.browse.MobileBookmarkCategory
import com.uiptv.mobile.shared.browse.MobileBrowseCategory
import com.uiptv.mobile.shared.browse.MobileBrowseItem
import com.uiptv.mobile.shared.browse.MobileBrowseSnapshot
import com.uiptv.mobile.shared.browse.RECENTLY_PLAYED_BOOKMARKS_CATEGORY_ID
import com.uiptv.mobile.shared.browse.MobileSeriesDetails
import com.uiptv.mobile.shared.browse.MobileSeriesSeasonTab
import com.uiptv.mobile.shared.browse.MobileWatchingNowEpisode
import com.uiptv.mobile.shared.browse.MobileWatchingNowItem
import com.uiptv.mobile.shared.browse.resolvedEpisodeNumber
import com.uiptv.mobile.shared.browse.resolvedSeason
import com.uiptv.mobile.shared.browse.seasonTab
import com.uiptv.mobile.shared.browse.seasonTabs
import com.uiptv.mobile.shared.cache.CacheRefreshAction
import com.uiptv.mobile.shared.cache.CacheRefreshJobRequest
import com.uiptv.mobile.shared.cache.CacheRefreshJobState
import com.uiptv.mobile.shared.cache.CacheRefreshJobStatus
import com.uiptv.mobile.shared.playback.PlaybackLaunchResult
import com.uiptv.mobile.shared.playback.MobilePlaybackIndicator
import com.uiptv.mobile.shared.playback.PlayerChoice
import com.uiptv.mobile.shared.settings.AndroidPlayerPreference
import com.uiptv.mobile.shared.settings.AndroidPreferenceSnapshot
import com.uiptv.mobile.shared.settings.AndroidFilterSettings
import com.uiptv.mobile.shared.settings.BackupRestoreResult
import com.uiptv.mobile.shared.settings.MobileBackupArchive
import com.uiptv.mobile.shared.settings.PanelVisibilityPreference
import com.uiptv.mobile.shared.settings.PlayerPreference
import com.uiptv.mobile.shared.sync.RemoteSyncProgress
import com.uiptv.mobile.shared.sync.RemoteSyncProgressStep
import com.uiptv.mobile.shared.sync.RemoteSyncPullResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

typealias LocalPlaylistPicker = (onSelected: (String) -> Unit) -> Unit
typealias BackupFileCreator = (suggestedName: String, onSelected: (String?) -> Unit) -> Unit
typealias RestoreFilePicker = (onSelected: (String?) -> Unit) -> Unit
typealias LogoRenderer = @Composable (String, String, Modifier) -> Unit
typealias PlayerIconRenderer = @Composable (PlayerChoice, Modifier) -> Unit

private val DeepNightPrimaryBase = Color(0xFFB7C6FF)
private val DeepNightSurfaceBase = Color(0xFF111318)
private val DeepNightSurfaceHighBase = Color(0xFF1D2026)
private val DeepNightSurfaceHighestBase = Color(0xFF292C32)
private val DeepNightAccentBase = Color(0xFFC1C6DD)
private val RecordingDotRed = Color(0xFFE53935)
private val DeepNightTextBase = Color(0xFFF4F7FA)
private val DeepNightMutedTextBase = Color(0xFFAEB8C2)
private val DeepNightBackgroundBase = Color(0xFF0F1216)
private val DefaultMobileTimeZones = listOf("Etc/UTC", "Europe/London")

private data class UiptvPalette(
    val primary: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    val accent: Color,
    val text: Color,
    val mutedText: Color,
    val background: Color
)

private data class MobileSettingsDestination(
    val id: String,
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val DarkUiptvPalette = UiptvPalette(
    primary = DeepNightPrimaryBase,
    surface = DeepNightSurfaceBase,
    surfaceHigh = DeepNightSurfaceHighBase,
    surfaceHighest = DeepNightSurfaceHighestBase,
    accent = DeepNightAccentBase,
    text = DeepNightTextBase,
    mutedText = DeepNightMutedTextBase,
    background = DeepNightBackgroundBase
)

private val LocalUiptvPalette = staticCompositionLocalOf { DarkUiptvPalette }
private val LocalWidePhoneLayout = staticCompositionLocalOf { false }
val LocalAppVersion = staticCompositionLocalOf<AppVersionInfo> { AppVersionInfo.EMPTY }

data class AppVersionInfo(
    val versionName: String,
    val buildNumber: String,
    val androidSdkVersion: Int
) {
    companion object {
        val EMPTY = AppVersionInfo("", "", 0)
    }
}

expect fun formatEpochSeconds(epochSeconds: Long): String

private enum class UiptvLayoutMode {
    Compact,
    WidePhone,
    WideLarge
}

private enum class WatchingNowFilter(val label: String) {
    VOD("VOD"),
    SERIES("Series");

    fun matches(item: MobileWatchingNowItem): Boolean =
        when (this) {
            VOD -> item.mode == BrowseMode.VOD
            SERIES -> item.mode == BrowseMode.SERIES
        }
}

private val DeepNightPrimary: Color
    @Composable get() = LocalUiptvPalette.current.primary
private val DeepNightSurface: Color
    @Composable get() = LocalUiptvPalette.current.surface
private val DeepNightSurfaceHigh: Color
    @Composable get() = LocalUiptvPalette.current.surfaceHigh
private val DeepNightSurfaceHighest: Color
    @Composable get() = LocalUiptvPalette.current.surfaceHighest
private val DeepNightAccent: Color
    @Composable get() = LocalUiptvPalette.current.accent
private val DeepNightText: Color
    @Composable get() = LocalUiptvPalette.current.text
private val DeepNightMutedText: Color
    @Composable get() = LocalUiptvPalette.current.mutedText
private val DeepNightBackground: Color
    @Composable get() = LocalUiptvPalette.current.background

private val UiptvDarkColorScheme = darkColorScheme(
    primary = DeepNightPrimaryBase,
    onPrimary = Color(0xFF202D60),
    primaryContainer = Color(0xFF39477D),
    onPrimaryContainer = Color(0xFFDFE1FF),
    secondary = DeepNightAccentBase,
    onSecondary = Color(0xFF2B3045),
    tertiary = Color(0xFFE5B9D8),
    onTertiary = Color(0xFF43263C),
    background = DeepNightBackgroundBase,
    onBackground = DeepNightTextBase,
    surface = DeepNightSurfaceBase,
    onSurface = DeepNightTextBase,
    surfaceVariant = DeepNightSurfaceHighestBase,
    onSurfaceVariant = Color(0xFFC5C6D0),
    outline = Color(0xFF8F909A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val UiptvLightColorScheme = lightColorScheme(
    primary = Color(0xFF405D9B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE1FF),
    onPrimaryContainer = Color(0xFF00174A),
    secondary = Color(0xFF596078),
    onSecondary = Color.White,
    tertiary = Color(0xFF75546F),
    onTertiary = Color.White,
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF191B21),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF191B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF45464F),
    outline = Color(0xFF6D7B87),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

@Composable
fun UiptvMobileApp(
    resumeSignal: Int = 0,
    isForeground: Boolean = true,
    epgActions: EpgUiActions = EpgUiActions(),
    syncActions: RemoteSyncUiActions = RemoteSyncUiActions.preview(),
    accountActions: AccountUiActions = AccountUiActions.preview(),
    browseActions: BrowseUiActions = BrowseUiActions.preview(),
    playbackActions: PlaybackUiActions = PlaybackUiActions.preview(),
    filterActions: FilterUiActions = FilterUiActions.preview(),
    panelVisibilityActions: PanelVisibilityUiActions = PanelVisibilityUiActions.preview(),
    backupRestoreActions: BackupRestoreUiActions = BackupRestoreUiActions.preview(),
    localPlaylistPicker: LocalPlaylistPicker? = null,
    backupFileCreator: BackupFileCreator? = null,
    restoreFilePicker: RestoreFilePicker? = null,
    logoRenderer: LogoRenderer = { _, _, _ -> },
    playerIconRenderer: PlayerIconRenderer = { choice, modifier -> DefaultPlayerIcon(choice, modifier) },
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
    colorScheme: ColorScheme? = null,
    timeZoneOptions: List<String> = DefaultMobileTimeZones
) {
    var epgEnabled by remember { mutableStateOf(false) }
    var epgPlaybackOnly by remember { mutableStateOf(false) }
    var epgTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var epgBackgroundCheckIntervalMinutes by remember { mutableIntStateOf(3) }
    var epgDataRevision by remember { mutableIntStateOf(0) }
    LaunchedEffect(epgActions, resumeSignal) {
        epgEnabled = epgActions.loadEnabled()
        epgPlaybackOnly = epgActions.loadPlaybackOnly()
        epgBackgroundCheckIntervalMinutes = epgActions.loadBackgroundCheckIntervalMinutes().coerceIn(1, 60)
        epgTime = System.currentTimeMillis()
        epgDataRevision++
    }
    LaunchedEffect(epgEnabled, epgPlaybackOnly, isForeground) {
        while (epgEnabled && !epgPlaybackOnly && isForeground) { epgTime = System.currentTimeMillis(); kotlinx.coroutines.delay(60_000) }
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedBrowseAccount by remember { mutableStateOf<MobileAccount?>(null) }
    var showThumbnails by remember { mutableStateOf(false) }
    var wideSearchVisible by rememberSaveable { mutableStateOf(false) }
    var wideNavigationVisible by rememberSaveable { mutableStateOf(false) }
    var panelVisibilityPreference by remember { mutableStateOf(PanelVisibilityPreference()) }
    val appScope = rememberCoroutineScope()
    val screenStates = rememberSaveableStateHolder()
    fun selectTab(index: Int) {
        selectedTab = index
        wideSearchVisible = false
    }
    fun updatePanelVisibility(preference: PanelVisibilityPreference) {
        panelVisibilityPreference = preference
        appScope.launch {
            runCatching { panelVisibilityActions.save(preference) }
        }
    }
    backHandler(selectedTab == 1 && selectedBrowseAccount != null) {
        selectedBrowseAccount = null
    }
    LaunchedEffect(filterActions) {
        runCatching { filterActions.load() }
            .onSuccess { showThumbnails = it.enableThumbnails }
    }
    LaunchedEffect(panelVisibilityActions) {
        runCatching { panelVisibilityActions.load() }
            .onSuccess { panelVisibilityPreference = it }
    }

    val useDarkTheme = isSystemInDarkTheme()
    val activeColorScheme = colorScheme ?: if (useDarkTheme) UiptvDarkColorScheme else UiptvLightColorScheme
    val palette = UiptvPalette(
        primary = activeColorScheme.primary,
        surface = activeColorScheme.surface,
        surfaceHigh = activeColorScheme.surfaceContainerLow,
        surfaceHighest = activeColorScheme.surfaceContainerHigh,
        accent = activeColorScheme.secondary,
        text = activeColorScheme.onSurface,
        mutedText = activeColorScheme.onSurfaceVariant,
        background = activeColorScheme.background
    )
    CompositionLocalProvider(LocalUiptvPalette provides palette,
        LocalEpgState provides EpgUiState(
            enabled = epgEnabled && isForeground,
            playbackOnly = epgPlaybackOnly,
            time = epgTime,
            backgroundCheckIntervalMinutes = epgBackgroundCheckIntervalMinutes,
            dataRevision = epgDataRevision,
            actions = epgActions,
            onEnabled = { epgEnabled = it },
            onPlaybackOnly = { epgPlaybackOnly = it },
            onDataRefresh = { epgDataRevision++ }
        )) {
        MaterialTheme(colorScheme = activeColorScheme) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val layoutMode = remember(maxWidth, maxHeight) {
                    val largeWideLayout = maxWidth >= 840.dp && maxHeight >= 480.dp
                    val phoneLandscapeWideLayout = maxWidth.value >= 560f &&
                        maxHeight.value >= 300f &&
                        maxHeight.value < 480f &&
                        maxWidth.value > maxHeight.value * 1.35f
                    when {
                        largeWideLayout -> UiptvLayoutMode.WideLarge
                        phoneLandscapeWideLayout -> UiptvLayoutMode.WidePhone
                        else -> UiptvLayoutMode.Compact
                    }
                }
                val wideLayout = layoutMode != UiptvLayoutMode.Compact
                CompositionLocalProvider(LocalWidePhoneLayout provides (layoutMode == UiptvLayoutMode.WidePhone),
                    LocalShowNavigation provides if (wideLayout && !wideNavigationVisible) ({ wideNavigationVisible = true }) else null) {
                    if (wideLayout) {
                        val wideSearchEnabled = selectedTab != 3
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(palette.background)
                                .safeDrawingPadding()
                        ) {
                            if (wideNavigationVisible) AppTabs(
                                selectedTab = selectedTab,
                                onSelect = ::selectTab,
                                vertical = true,
                                compact = layoutMode == UiptvLayoutMode.WidePhone,
                                searchEnabled = wideSearchEnabled,
                                searchActive = wideSearchVisible && wideSearchEnabled,
                                onSearchClick = {
                                    if (wideSearchEnabled) {
                                        wideSearchVisible = !wideSearchVisible
                                    }
                                }, onHideNavigation = { wideNavigationVisible = false }
                            )
                            CurrentTab(
                                screenStates = screenStates,
                                selectedTab = selectedTab,
                                resumeSignal = resumeSignal,
                                syncActions = syncActions,
                                accountActions = accountActions,
                                timeZoneOptions = timeZoneOptions,
                                browseActions = browseActions,
                                playbackActions = playbackActions,
                                filterActions = filterActions,
                                backupRestoreActions = backupRestoreActions,
                                localPlaylistPicker = localPlaylistPicker,
                                backupFileCreator = backupFileCreator,
                                restoreFilePicker = restoreFilePicker,
                                selectedBrowseAccount = selectedBrowseAccount,
                                showThumbnails = showThumbnails,
                                panelVisibilityPreference = panelVisibilityPreference,
                                logoRenderer = logoRenderer,
                                playerIconRenderer = playerIconRenderer,
                                onOpenAccountChannels = { account ->
                                    selectedBrowseAccount = account
                                },
                                onCloseAccountChannels = { selectedBrowseAccount = null },
                                onThumbnailSettingChanged = { showThumbnails = it },
                                onPanelVisibilityPreferenceChange = ::updatePanelVisibility,
                                backHandler = backHandler,
                                wideLayout = true,
                                wideSearchVisible = wideSearchVisible && wideSearchEnabled,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f)
                            )
                        }
                    } else {
                        Scaffold(
                            containerColor = palette.background,
                            contentColor = palette.text,
                            bottomBar = { AppTabs(selectedTab = selectedTab, onSelect = ::selectTab) }
                        ) { padding ->
                            CurrentTab(
                                screenStates = screenStates,
                                selectedTab = selectedTab,
                                resumeSignal = resumeSignal,
                                syncActions = syncActions,
                                accountActions = accountActions,
                                timeZoneOptions = timeZoneOptions,
                                browseActions = browseActions,
                                playbackActions = playbackActions,
                                filterActions = filterActions,
                                backupRestoreActions = backupRestoreActions,
                                localPlaylistPicker = localPlaylistPicker,
                                backupFileCreator = backupFileCreator,
                                restoreFilePicker = restoreFilePicker,
                                selectedBrowseAccount = selectedBrowseAccount,
                                showThumbnails = showThumbnails,
                                panelVisibilityPreference = panelVisibilityPreference,
                                logoRenderer = logoRenderer,
                                playerIconRenderer = playerIconRenderer,
                                onOpenAccountChannels = { account ->
                                    selectedBrowseAccount = account
                                },
                                onCloseAccountChannels = { selectedBrowseAccount = null },
                                onThumbnailSettingChanged = { showThumbnails = it },
                                onPanelVisibilityPreferenceChange = ::updatePanelVisibility,
                                backHandler = backHandler,
                                wideLayout = false,
                                wideSearchVisible = false,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentTab(
    screenStates: SaveableStateHolder,
    selectedTab: Int,
    resumeSignal: Int,
    syncActions: RemoteSyncUiActions,
    accountActions: AccountUiActions,
    timeZoneOptions: List<String>,
    browseActions: BrowseUiActions,
    playbackActions: PlaybackUiActions,
    filterActions: FilterUiActions,
    backupRestoreActions: BackupRestoreUiActions,
    localPlaylistPicker: LocalPlaylistPicker?,
    backupFileCreator: BackupFileCreator?,
    restoreFilePicker: RestoreFilePicker?,
    selectedBrowseAccount: MobileAccount?,
    showThumbnails: Boolean,
    panelVisibilityPreference: PanelVisibilityPreference,
    logoRenderer: LogoRenderer,
    playerIconRenderer: PlayerIconRenderer,
    onOpenAccountChannels: (MobileAccount) -> Unit,
    onCloseAccountChannels: () -> Unit,
    onThumbnailSettingChanged: (Boolean) -> Unit,
    onPanelVisibilityPreferenceChange: (PanelVisibilityPreference) -> Unit,
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit,
    wideLayout: Boolean,
    wideSearchVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val tab = selectedTab
    val hasBrowseAccount = selectedBrowseAccount != null
    val routeKey = if (tab == 1 && hasBrowseAccount) "account:${selectedBrowseAccount?.id}" else "tab:$tab"
    Box(modifier.fillMaxSize()) {
    screenStates.SaveableStateProvider(routeKey) {
        when (tab) {
            0 -> {
                BookmarksScreen(
                    browseActions,
                    playbackActions,
                    showThumbnails,
                    logoRenderer,
                    playerIconRenderer,
                    wideLayout,
                    wideSearchVisible,
                    resumeSignal,
                    Modifier.fillMaxSize()
                )
            }
            1 -> {
                val account = selectedBrowseAccount.takeIf { hasBrowseAccount }
                if (account == null) {
                    AccountsScreen(
                        accountActions,
                        timeZoneOptions,
                        backHandler,
                        onOpenAccountChannels,
                        localPlaylistPicker,
                        wideLayout,
                        wideSearchVisible,
                        resumeSignal,
                        Modifier.fillMaxSize()
                    )
                } else {
                    ChannelsScreen(
                        browseActions = browseActions,
                        playbackActions = playbackActions,
                        requestedAccountId = account.id,
                        requestedAccountType = account.type,
                        requestedAccountName = account.accountName,
                        showAccountSelector = false,
                        onBackToAccounts = onCloseAccountChannels,
                        showThumbnails = showThumbnails,
                        logoRenderer = logoRenderer,
                        playerIconRenderer = playerIconRenderer,
                        backHandler = backHandler,
                        wideLayout = wideLayout,
                        wideSearchVisible = wideSearchVisible,
                        refreshSignal = resumeSignal,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            2 -> {
                WatchingNowScreen(
                    browseActions,
                    playbackActions,
                    showThumbnails,
                    logoRenderer,
                    playerIconRenderer,
                    resumeSignal,
                    wideLayout,
                    wideSearchVisible,
                    backHandler,
                    panelVisibilityPreference.watchingNowDetailsPanelVisible,
                    { visible ->
                        onPanelVisibilityPreferenceChange(
                            panelVisibilityPreference.copy(watchingNowDetailsPanelVisible = visible)
                        )
                    },
                    Modifier.fillMaxSize()
                )
            }
            3 -> {
                RemoteSyncScreen(
                    syncActions,
                    browseActions,
                    playbackActions,
                    filterActions,
                    backupRestoreActions,
                    backupFileCreator,
                    restoreFilePicker,
                    onThumbnailSettingChanged,
                    playerIconRenderer,
                    wideLayout,
                    Modifier.fillMaxSize()
                )
            }
        }
    }
}
}

@Composable
private fun ChannelsScreen(
    browseActions: BrowseUiActions,
    playbackActions: PlaybackUiActions,
    requestedAccountId: Long?,
    requestedAccountType: MobileAccountType? = null,
    requestedAccountName: String? = null,
    showAccountSelector: Boolean = true,
    onBackToAccounts: (() -> Unit)? = null,
    showThumbnails: Boolean = false,
    logoRenderer: LogoRenderer = { _, _, _ -> },
    playerIconRenderer: PlayerIconRenderer = { choice, modifier -> DefaultPlayerIcon(choice, modifier) },
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
    wideLayout: Boolean = false,
    wideSearchVisible: Boolean = false,
    refreshSignal: Int = 0,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf(MobileBrowseSnapshot()) }
    var mode by rememberSaveable(requestedAccountId) { mutableStateOf(BrowseMode.LIVE) }
    var categoryQuery by rememberSaveable(requestedAccountId) { mutableStateOf("") }
    var channelQuery by rememberSaveable(requestedAccountId) { mutableStateOf("") }
    var selectedCategoryRowId by rememberSaveable(requestedAccountId) { mutableStateOf<Long?>(null) }
    var statusText by remember { mutableStateOf("Loading") }
    var running by remember { mutableStateOf(false) }
    var reloadGeneration by remember { mutableStateOf(0L) }
    var pendingPlayback by remember { mutableStateOf<PendingPlayback?>(null) }
    var playerChoices by remember { mutableStateOf<List<PlayerChoice>>(emptyList()) }
    var selectedBrowseSeries by remember { mutableStateOf<MobileWatchingNowItem?>(null) }
    var browseSeriesEpisodes by remember { mutableStateOf<List<MobileWatchingNowEpisode>>(emptyList()) }
    var pendingEpisodeMenu by remember { mutableStateOf<MobileWatchingNowEpisode?>(null) }
    var searchVisible by rememberSaveable(requestedAccountId) { mutableStateOf(false) }
    var categorySelectionMode by remember { mutableStateOf(false) }
    var selectedCategoryIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pendingCategoryRemoval by remember { mutableStateOf(false) }

    val categoryListState = rememberSaveable(mode, saver = LazyListState.Saver) { LazyListState() }
    var itemQueries by rememberSaveable { mutableStateOf(hashMapOf<String, String>()) }
    var categoryQueries by rememberSaveable { mutableStateOf(hashMapOf<String, String>()) }
    val itemScreenStates = rememberSaveableStateHolder()
    var browseLoad by remember { mutableStateOf<Job?>(null) }

    fun activeCategoryQuery(): String = categoryQuery
    fun activeChannelQuery(): String = channelQuery

    fun reload(
        accountId: Long? = requestedAccountId ?: snapshot.selectedAccountId,
        categoryRowId: Long? = selectedCategoryRowId,
        itemQuery: String = activeChannelQuery(),
        browseMode: BrowseMode = mode
    ) {
        val generation = reloadGeneration + 1
        reloadGeneration = generation
        browseLoad?.cancel()
        if (snapshot.selectedCategoryRowId != categoryRowId || snapshot.mode != browseMode) {
            snapshot = snapshot.copy(items = emptyList())
        }
        browseLoad = scope.launch {
            if (generation != reloadGeneration) {
                return@launch
            }
            running = true
            val result = runCatching { browseActions.loadBrowse(accountId, browseMode, categoryRowId, itemQuery) }
            if (generation != reloadGeneration) {
                return@launch
            }
            result
                .onSuccess {
                    snapshot = it
                    statusText = when {
                        it.accounts.isEmpty() -> "No accounts yet"
                        it.categories.isEmpty() -> "No cached ${browseMode.displayLabel()} categories"
                        categoryRowId == null -> "${it.categories.size} ${browseMode.displayLabel()} categories"
                        it.items.isEmpty() -> "No ${browseMode.displayLabel()} items"
                        else -> "${it.items.size} ${browseMode.displayLabel()} items"
                    }
                }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    statusText = "Unable to load catalogue. Please try again."
                }
            running = false
        }
    }

    val selectedCategory = snapshot.categories.firstOrNull { it.rowId == selectedCategoryRowId }
    val selectedAccountName = requestedAccountName
        ?: snapshot.accounts.firstOrNull { it.id == snapshot.selectedAccountId }?.name
        ?: "Provider"
    val selectedAccountType = requestedAccountType
        ?: snapshot.accounts.firstOrNull { it.id == snapshot.selectedAccountId }?.type
    val visibleModes = remember(selectedAccountType) { selectedAccountType.browseModesForAccount() }
    val showingChannelList = selectedCategoryRowId != null
    fun backToCategories() {
        selectedCategoryRowId = null
        reload(snapshot.selectedAccountId, null, "", mode)
    }
    LaunchedEffect(browseActions, requestedAccountId, selectedAccountType, refreshSignal) {
        val previousMode = mode
        val activeMode = if (previousMode in visibleModes) previousMode else BrowseMode.LIVE
        val activeCategoryRowId = if (activeMode == previousMode) selectedCategoryRowId else null
        mode = activeMode
        selectedCategoryRowId = activeCategoryRowId
        categorySelectionMode = false
        selectedCategoryIds = emptySet()
        pendingCategoryRemoval = false
        reload(requestedAccountId, activeCategoryRowId, activeChannelQuery(), activeMode)
    }
    val visibleCategories = snapshot.categories.filter { category ->
        val query = activeCategoryQuery()
        query.isBlank() || category.title.contains(query.trim(), ignoreCase = true)
    }
    val browseSeries = selectedBrowseSeries
    val selectedCategoryCount = selectedCategoryIds.count { id -> snapshot.categories.any { it.rowId == id } }
    val canNavigateBackToCategories = showingChannelList

    fun clearCategorySelection() {
        categorySelectionMode = false
        selectedCategoryIds = emptySet()
        pendingCategoryRemoval = false
    }

    fun beginCategorySelection(category: MobileBrowseCategory) {
        categorySelectionMode = true
        selectedCategoryIds = setOf(category.rowId)
    }

    fun toggleCategorySelection(category: MobileBrowseCategory) {
        val nextSelection = if (category.rowId in selectedCategoryIds) {
            selectedCategoryIds - category.rowId
        } else {
            selectedCategoryIds + category.rowId
        }
        selectedCategoryIds = nextSelection
        if (nextSelection.isEmpty()) {
            categorySelectionMode = false
        }
    }

    fun requestCategoryRemoval() {
        if (selectedCategoryCount > 0) {
            pendingCategoryRemoval = true
        }
    }

    fun removeSelectedCategories() {
        val accountId = snapshot.selectedAccountId ?: requestedAccountId ?: return
        val idsToRemove = selectedCategoryIds
        val removedActiveCategory = selectedCategoryRowId != null && selectedCategoryRowId in idsToRemove
        scope.launch {
            running = true
            runCatching { browseActions.removeCachedCategories(accountId, mode, idsToRemove) }
                .onSuccess { result ->
                    statusText = "Removed ${result.removedCategoryCount} categories and ${result.removedItemCount} cached items"
                    clearCategorySelection()
                    if (removedActiveCategory) {
                        selectedCategoryRowId = null
                        channelQuery = ""
                    }
                    reload(accountId, if (removedActiveCategory) null else selectedCategoryRowId, activeChannelQuery(), mode)
                }
                .onFailure { statusText = "Unable to remove categories" }
            running = false
        }
    }

    fun openBrowseItem(item: MobileBrowseItem, forcePlayerPicker: Boolean = false) {
        scope.launch {
            running = true
            if (!forcePlayerPicker && item.mode == BrowseMode.SERIES && item.command.isBlank()) {
                val series = item.toWatchingNowSeriesItem()
                runCatching { browseActions.listWatchingNowEpisodes(series) }
                    .onSuccess { episodes ->
                        selectedBrowseSeries = series
                        browseSeriesEpisodes = episodes
                        statusText = if (episodes.isEmpty()) "No episodes for ${item.name}" else "${episodes.size} episodes"
                        runCatching { browseActions.enrichSeriesDetails(series, episodes) }
                            .onSuccess { details ->
                                selectedBrowseSeries = details.series
                                browseSeriesEpisodes = details.episodes
                            }
                    }
                    .onFailure { statusText = "Unable to open series" }
            } else if (!forcePlayerPicker) {
                val preference = playbackActions.loadPlayerPreference()
                if (preference.rememberForFutureStreams && preference.selectedPlayer != AndroidPlayerPreference.ASK_EVERY_TIME) {
                    runCatching { playbackActions.playBrowseItem(item, preference.selectedPlayer, false) }
                        .onSuccess { statusText = it.message }
                        .onFailure { statusText = "Unable to open stream" }
                } else {
                    playerChoices = playbackActions.playerChoices()
                    pendingPlayback = PendingPlayback.Browse(item)
                }
            } else {
                playerChoices = playbackActions.playerChoices()
                pendingPlayback = PendingPlayback.Browse(item)
            }
            running = false
        }
    }

    if (browseSeries != null) {
        backHandler(true) {
            selectedBrowseSeries = null
            browseSeriesEpisodes = emptyList()
        }
        WatchingNowSeriesDetail(
            series = browseSeries,
            episodes = browseSeriesEpisodes,
            running = running,
            statusText = statusText,
            showThumbnail = showThumbnails,
            logoRenderer = logoRenderer,
            onBack = {
                selectedBrowseSeries = null
                browseSeriesEpisodes = emptyList()
            },
            onPlayEpisode = { episode ->
                scope.launch {
                    running = true
                    val preference = playbackActions.loadPlayerPreference()
                    if (preference.rememberForFutureStreams && preference.selectedPlayer != AndroidPlayerPreference.ASK_EVERY_TIME) {
                        runCatching { playbackActions.playWatchingNowEpisode(episode, preference.selectedPlayer, false) }
                            .onSuccess {
                                statusText = it.message
                                if (it.launched) {
                                    browseSeriesEpisodes = browseSeriesEpisodes.withWatchingFlag(episode)
                                }
                            }
                            .onFailure { statusText = "Unable to open episode" }
                    } else {
                        playerChoices = playbackActions.playerChoices()
                        pendingPlayback = PendingPlayback.WatchingEpisode(episode)
                    }
                    running = false
                }
            },
            onBingeSeason = { seasonKey ->
                scope.launch {
                    running = true
                    val preference = playbackActions.loadPlayerPreference()
                    if (
                        preference.rememberForFutureStreams &&
                        preference.selectedPlayer.supportsBingeWatch() &&
                        preference.selectedPlayer != AndroidPlayerPreference.ASK_EVERY_TIME
                    ) {
                        runCatching {
                            playbackActions.playBingeWatchSeason(browseSeries, browseSeriesEpisodes, seasonKey, preference.selectedPlayer, false)
                        }
                            .onSuccess {
                                statusText = it.message
                                if (it.launched) {
                                    browseSeriesEpisodes = browseSeriesEpisodes.withBingeStartFlag(seasonKey)
                                }
                            }
                            .onFailure { statusText = "Unable to start binge watch" }
                    } else {
                        playerChoices = playbackActions.playerChoices().bingeWatchChoices()
                        pendingPlayback = PendingPlayback.Binge(browseSeries, browseSeriesEpisodes, seasonKey)
                    }
                    running = false
                }
            },
            onEpisodeMenu = { episode ->
                scope.launch {
                    playerChoices = playbackActions.playerChoices()
                    pendingEpisodeMenu = episode
                }
            },
            onRemoveSeries = {
                selectedBrowseSeries = null
                browseSeriesEpisodes = emptyList()
            },
            showRemove = false,
            emptyTitle = "No episodes",
            emptyDetail = "This series did not return episode links.",
            wideLayout = wideLayout,
            wideSearchVisible = wideSearchVisible,
            modifier = modifier.fillMaxSize()
        )
        EpisodeActionSheet(
            episode = pendingEpisodeMenu,
            playerChoices = playerChoices,
            playerIconRenderer = playerIconRenderer,
            wideLayout = wideLayout,
            onDismiss = { pendingEpisodeMenu = null },
            onMarkWatching = { episode ->
                pendingEpisodeMenu = null
                scope.launch {
                    running = true
                    runCatching { browseActions.markWatchingNowEpisode(episode) }
                        .onSuccess {
                            browseSeriesEpisodes = browseSeriesEpisodes.withWatchingFlag(episode)
                            statusText = "Marked ${episode.title} as Watching Now"
                        }
                        .onFailure { statusText = "Unable to mark Watching Now" }
                    running = false
                }
            },
            onClearWatching = { episode ->
                pendingEpisodeMenu = null
                scope.launch {
                    running = true
                    runCatching { browseActions.clearWatchingNowEpisode(episode) }
                        .onSuccess {
                            browseSeriesEpisodes = browseSeriesEpisodes.clearWatchingFlag(episode)
                            statusText = "Removed Watching Now flag"
                        }
                        .onFailure { statusText = "Unable to remove Watching Now flag" }
                    running = false
                }
            },
            onInstall = { choice ->
                pendingEpisodeMenu = null
                scope.launch {
                    running = true
                    runCatching { playbackActions.openPlayerInstall(choice) }
                        .onSuccess { statusText = "Opening ${choice.label} in Google Play" }
                        .onFailure { statusText = "Unable to open Google Play" }
                    running = false
                }
            },
            onPlay = { episode, player ->
                pendingEpisodeMenu = null
                scope.launch {
                    running = true
                    runCatching { playbackActions.playWatchingNowEpisode(episode, player, false) }
                        .onSuccess {
                            statusText = it.message
                            if (it.launched) {
                                browseSeriesEpisodes = browseSeriesEpisodes.withWatchingFlag(episode)
                            }
                        }
                        .onFailure { statusText = "Unable to open episode" }
                    running = false
                }
            }
        )
        PlaybackPickerDialog(
            pendingPlayback = pendingPlayback,
            playerChoices = playerChoices,
            playerIconRenderer = playerIconRenderer,
            wideLayout = wideLayout,
            onDismiss = { pendingPlayback = null },
            onInstall = { choice ->
                pendingPlayback = null
                scope.launch {
                    running = true
                    runCatching { playbackActions.openPlayerInstall(choice) }
                        .onSuccess { statusText = "Opening ${choice.label} in Google Play" }
                        .onFailure { statusText = "Unable to open Google Play" }
                    running = false
                }
            },
            onSelect = { player, remember ->
                val pending = pendingPlayback ?: return@PlaybackPickerDialog
                pendingPlayback = null
                scope.launch {
                    running = true
                    runCatching {
                        when (pending) {
                            is PendingPlayback.Browse -> playbackActions.playBrowseItem(pending.item, player, remember)
                            is PendingPlayback.Bookmark -> playbackActions.playBookmark(pending.bookmark, player, remember)
                            is PendingPlayback.Watching -> playbackActions.playWatchingNow(pending.item, player, remember)
                            is PendingPlayback.WatchingEpisode -> playbackActions.playWatchingNowEpisode(pending.episode, player, remember)
                            is PendingPlayback.Binge -> playbackActions.playBingeWatchSeason(pending.series, pending.episodes, pending.seasonKey, player, remember)
                        }
                    }
                        .onSuccess {
                            statusText = it.message
                            if (it.launched) {
                                browseSeriesEpisodes = when (pending) {
                                    is PendingPlayback.WatchingEpisode -> browseSeriesEpisodes.withWatchingFlag(pending.episode)
                                    is PendingPlayback.Binge -> browseSeriesEpisodes.withBingeStartFlag(pending.seasonKey)
                                    else -> browseSeriesEpisodes
                                }
                            }
                        }
                        .onFailure { statusText = "Unable to open episode" }
                    running = false
                }
            }
        )
        return
    }
    backHandler(categorySelectionMode || canNavigateBackToCategories) {
        if (categorySelectionMode) {
            clearCategorySelection()
        } else {
            backToCategories()
        }
    }
    if (pendingCategoryRemoval && selectedCategoryCount > 0) {
        AlertDialog(
            onDismissRequest = { pendingCategoryRemoval = false },
            title = { Text("Remove cached categories?") },
            text = {
                Text(
                    "You are about to remove $selectedCategoryCount cached categories. " +
                        "Cached ${mode.removalItemLabel()} in those categories will also be removed. " +
                        "Bookmarks and Watching Now entries will stay. Refresh cache for this account to bring them back."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingCategoryRemoval = false
                        removeSelectedCategories()
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCategoryRemoval = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    CatalogueContent(
        title = selectedCategory?.title ?: selectedAccountName,
        subtitle = if (showingChannelList) selectedAccountName else "Choose a category",
        mode = mode,
        modes = visibleModes,
        showingItems = showingChannelList,
        query = if (showingChannelList) channelQuery else categoryQuery,
        searchVisible = searchVisible,
        onSearch = { searchVisible = !searchVisible },
        onQueryChange = {
            if (showingChannelList) {
                channelQuery = it
                itemQueries = HashMap(itemQueries).apply { put("${mode.name}:$selectedCategoryRowId", it) }
                reload(snapshot.selectedAccountId, selectedCategoryRowId, it)
            } else {
                categoryQuery = it
                categoryQueries = HashMap(categoryQueries).apply { put(mode.name, it) }
            }
        },
        onBack = {
            when {
                categorySelectionMode -> clearCategorySelection()
                canNavigateBackToCategories -> backToCategories()
                else -> onBackToAccounts?.invoke()
            }
        },
        onModeChange = {
            clearCategorySelection()
            mode = it
            selectedCategoryRowId = null
            categoryQuery = categoryQueries[it.name].orEmpty()
            channelQuery = ""
            reload(snapshot.selectedAccountId, null, "", it)
        },
        running = running,
        status = statusText,
        onRetry = { reload() },
        modifier = modifier
    ) {
        if (!showingChannelList) {
            if (categorySelectionMode) CategorySelectionToolbar(selectedCategoryCount, { requestCategoryRemoval() }, { clearCategorySelection() })
            LazyColumn(state = categoryListState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (visibleCategories.isEmpty() && !running) item {
                    EmptyState(if (categoryQuery.isNotBlank()) "No matches" else "No cached categories",
                        if (categoryQuery.isNotBlank()) "Clear search to see all categories." else "Return to Accounts and refresh this account to load its catalogue.")
                }
                items(visibleCategories, key = { "${mode.name}:${it.rowId}" }) { category ->
                    CategoryListRow(category, category.rowId in selectedCategoryIds, categorySelectionMode,
                        onClick = {
                            if (categorySelectionMode) toggleCategorySelection(category)
                            else {
                                selectedCategoryRowId = category.rowId
                                channelQuery = itemQueries["${mode.name}:${category.rowId}"].orEmpty()
                                reload(snapshot.selectedAccountId, category.rowId, channelQuery)
                            }
                        }, onLongClick = { beginCategorySelection(category) })
                }
            }
        } else {
            itemScreenStates.SaveableStateProvider("${mode.name}:$selectedCategoryRowId") {
                Column(Modifier.fillMaxSize()) {
                    CatalogueItems(
                        items = snapshot.items,
                        mode = mode,
                        loading = running,
                        searching = channelQuery.isNotBlank(),
                        showThumbnails = showThumbnails,
                        logoRenderer = logoRenderer,
                        onPlay = { openBrowseItem(it) },
                        onChoosePlayer = { openBrowseItem(it, forcePlayerPicker = true) },
                        onToggleBookmark = { item ->
                            scope.launch {
                                runCatching { browseActions.toggleBookmark(item) }
                                    .onSuccess { bookmarked ->
                                        snapshot = snapshot.copy(items = snapshot.items.map {
                                            if (it.rowId == item.rowId) it.copy(isBookmarked = bookmarked) else it
                                        })
                                    }
                                    .onFailure { statusText = "Unable to update bookmark. Please try again." }
                            }
                        }
                    )
                }
            }
        }
    }

    PlaybackPickerDialog(
        pendingPlayback = pendingPlayback,
        playerChoices = playerChoices,
        playerIconRenderer = playerIconRenderer,
        wideLayout = wideLayout,
        onDismiss = { pendingPlayback = null },
        onInstall = { choice ->
            pendingPlayback = null
            scope.launch {
                running = true
                runCatching { playbackActions.openPlayerInstall(choice) }
                    .onSuccess { statusText = "Opening ${choice.label} in Google Play" }
                    .onFailure { statusText = "Unable to open Google Play" }
                running = false
            }
        },
        onSelect = { player, remember ->
            val pending = pendingPlayback ?: return@PlaybackPickerDialog
            pendingPlayback = null
            scope.launch {
                running = true
                runCatching {
                    when (pending) {
                        is PendingPlayback.Browse -> playbackActions.playBrowseItem(pending.item, player, remember)
                        is PendingPlayback.Bookmark -> playbackActions.playBookmark(pending.bookmark, player, remember)
                        is PendingPlayback.Watching -> playbackActions.playWatchingNow(pending.item, player, remember)
                        is PendingPlayback.WatchingEpisode -> playbackActions.playWatchingNowEpisode(pending.episode, player, remember)
                        is PendingPlayback.Binge -> playbackActions.playBingeWatchSeason(pending.series, pending.episodes, pending.seasonKey, player, remember)
                    }
                }
                    .onSuccess {
                        statusText = it.message
                        if (it.launched) {
                            browseSeriesEpisodes = when (pending) {
                                is PendingPlayback.WatchingEpisode -> browseSeriesEpisodes.withWatchingFlag(pending.episode)
                                is PendingPlayback.Binge -> browseSeriesEpisodes.withBingeStartFlag(pending.seasonKey)
                                else -> browseSeriesEpisodes
                            }
                        }
                    }
                    .onFailure { statusText = "Unable to open stream" }
                running = false
            }
        }
    )
}

@Composable
private fun CompactChromeIcon(label: String, description: String, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .defaultMinSize(minWidth = 32.dp, minHeight = 32.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        color = DeepNightPrimary,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun CategorySelectionToolbar(
    selectedCount: Int,
    onRemove: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DeepNightSurfaceHighest,
        contentColor = DeepNightText,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "$selectedCount selected",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
            Button(
                enabled = selectedCount > 0,
                onClick = onRemove
            ) {
                Text("Remove")
            }
        }
    }
}

@Composable
private fun CategoryListRow(
    category: MobileBrowseCategory,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            MobileM3Switch(
                checked = selected,
                onCheckedChange = { onClick() }
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                category.title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${category.itemCount} items",
                color = DeepNightMutedText,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (!selectionMode) Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = DeepNightMutedText)
    }
}

@Composable
internal fun BrowseItemRow(
    item: MobileBrowseItem,
    showThumbnail: Boolean,
    logoRenderer: LogoRenderer,
    onPlay: () -> Unit,
    onChoosePlayer: () -> Unit,
    onToggleBookmark: () -> Unit
) {
    val canPlay = item.mode == BrowseMode.SERIES || item.command.isNotBlank()
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .combinedClickable(
                enabled = canPlay,
                onClick = onPlay,
                onLongClick = if (item.command.isNotBlank()) onChoosePlayer else null
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = DeepNightSurfaceHigh)
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                ChannelLogo(
                    label = item.number.ifBlank { item.name.take(2).uppercase() },
                    logo = item.logo,
                    fallbackLoader = epgThumbnail(item.accountId, item.channelId, item.name, showThumbnail && item.mode == BrowseMode.LIVE),
                    showThumbnail = showThumbnail,
                    contentDescription = "Logo ${item.name}",
                    logoRenderer = logoRenderer
                )
            },
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (MobilePlaybackIndicator.isActive(item.accountId, item.mode, item.channelId)) {
                        PlayingIndicator()
                    }
                    if (item.hasDrmMetadata()) {
                        DrmBadge(Modifier.padding(start = 8.dp))
                    }
                    FavouriteStar(
                        selected = item.isBookmarked,
                        contentDescription = if (item.isBookmarked) {
                            "Remove favourite ${item.name}"
                        } else {
                            "Add favourite ${item.name}"
                        },
                        compact = true,
                        onClick = onToggleBookmark
                    )
                }
            },
            supportingContent = {
                Text(item.subtitle(), color = DeepNightMutedText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        )
        if (item.mode == BrowseMode.LIVE && LocalEpgState.current.enabled && !LocalEpgState.current.playbackOnly) {
            Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                MobileEpgChannel(item.accountId, item.channelId, item.name)
            }
        }
    }
}

@Composable
private fun PlayingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(14.dp)
            .background(DeepNightSurfaceBase, CircleShape)
            .semantics { contentDescription = "Currently playing" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(4.dp)
                .background(RecordingDotRed, CircleShape)
        )
    }
}

@Composable
private fun ChannelLogo(
    label: String,
    logo: String,
    showThumbnail: Boolean,
    contentDescription: String,
    logoRenderer: LogoRenderer,
    fallbackLogo: String = "",
    fallbackLoader: (suspend () -> String)? = null
) {
    Surface(
        modifier = Modifier
            .width(48.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = DeepNightSurfaceHighest,
        contentColor = DeepNightPrimary
    ) {
        if (showThumbnail && (logo.isNotBlank() || fallbackLogo.isNotBlank() || fallbackLoader != null)) {
            CompositionLocalProvider(
                LocalThumbnailFallback provides fallbackLogo,
                LocalThumbnailFallbackLoader provides fallbackLoader,
                LocalThumbnailFallbackPanel provides true
            ) {
                logoRenderer(
                    logo.ifBlank { fallbackLogo },
                    contentDescription,
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                )
            }
        } else {
            Surface(
                modifier = Modifier.padding(4.dp).fillMaxSize(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        label.take(3),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun FavouriteStar(
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    compact: Boolean = false
) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(if (selected) androidx.compose.material.icons.Icons.Outlined.Bookmark else androidx.compose.material.icons.Icons.Outlined.BookmarkBorder,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BookmarksScreen(
    browseActions: BrowseUiActions,
    playbackActions: PlaybackUiActions,
    showThumbnails: Boolean,
    logoRenderer: LogoRenderer,
    playerIconRenderer: PlayerIconRenderer,
    wideLayout: Boolean,
    wideSearchVisible: Boolean,
    refreshSignal: Int,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<MobileBookmarkCategory>>(emptyList()) }
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(RECENTLY_PLAYED_BOOKMARKS_CATEGORY_ID) }
    var bookmarks by remember { mutableStateOf<List<MobileBookmark>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Loading") }
    var running by remember { mutableStateOf(false) }
    var pendingPlayback by remember { mutableStateOf<PendingPlayback?>(null) }
    var playerChoices by remember { mutableStateOf<List<PlayerChoice>>(emptyList()) }

    fun activeBookmarkQuery(): String = query
    fun removeBookmarkFromActiveList(bookmark: MobileBookmark) {
        scope.launch {
            val clearingRecent = selectedCategoryId == RECENTLY_PLAYED_BOOKMARKS_CATEGORY_ID
            running = true
            runCatching {
                if (clearingRecent) {
                    browseActions.removeRecentlyPlayedBookmark(bookmark)
                } else {
                    browseActions.removeBookmark(bookmark.rowId)
                }
            }
                .onSuccess {
                    statusText = if (clearingRecent) "Cleared from recently played" else "Removed bookmark"
                    categories = browseActions.listBookmarkCategories()
                    bookmarks = browseActions.listBookmarks(activeBookmarkQuery(), selectedCategoryId)
                }
                .onFailure {
                    statusText = it.message ?: if (clearingRecent) {
                        "Unable to clear recently played item"
                    } else {
                        "Unable to remove bookmark"
                    }
                }
            running = false
        }
    }

    var listLoad by remember { mutableStateOf<Job?>(null) }
    fun reload(categoryId: String? = selectedCategoryId, search: String = activeBookmarkQuery()) {
        listLoad?.cancel()
        listLoad = scope.launch {
            running = true
            runCatching {
                categories = browseActions.listBookmarkCategories()
                browseActions.listBookmarks(search, categoryId)
            }
                .onSuccess {
                    bookmarks = it
                    statusText = if (it.isEmpty()) "No bookmarks" else "${it.size} bookmarks"
                }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; statusText = "Unable to load bookmarks" }
            running = false
        }
    }

    fun openBookmark(bookmark: MobileBookmark, forcePlayerPicker: Boolean = false) {
        scope.launch {
            running = true
            if (!forcePlayerPicker) {
                val preference = playbackActions.loadPlayerPreference()
                if (preference.rememberForFutureStreams && preference.selectedPlayer != AndroidPlayerPreference.ASK_EVERY_TIME) {
                    runCatching { playbackActions.playBookmark(bookmark, preference.selectedPlayer, false) }
                        .onSuccess {
                            statusText = it.message
                            if (it.launched) reload()
                        }
                        .onFailure { statusText = "Unable to open bookmark" }
                    running = false
                    return@launch
                }
            }
            playerChoices = playbackActions.playerChoices()
            pendingPlayback = PendingPlayback.Bookmark(bookmark)
            running = false
        }
    }

    LaunchedEffect(browseActions, refreshSignal) {
        reload()
    }

    BookmarkLibrary(
        categories, selectedCategoryId, bookmarks, query, searchVisible, running, statusText,
        showThumbnails, logoRenderer,
        onSearch = { searchVisible = !searchVisible },
        onQueryChange = { query = it; reload(search = it) },
        onCategorySelect = { selectedCategoryId = it.id; reload(it.id) },
        onPlay = { openBookmark(it) }, onChoosePlayer = { openBookmark(it, forcePlayerPicker = true) },
        onRemove = { removeBookmarkFromActiveList(it) }, modifier = modifier
    )

    PlaybackPickerDialog(
        pendingPlayback = pendingPlayback,
        playerChoices = playerChoices,
        playerIconRenderer = playerIconRenderer,
        wideLayout = false,
        onDismiss = { pendingPlayback = null },
        onInstall = { choice ->
            pendingPlayback = null
            scope.launch {
                running = true
                runCatching { playbackActions.openPlayerInstall(choice) }
                    .onSuccess { statusText = "Opening ${choice.label} in Google Play" }
                    .onFailure { statusText = "Unable to open Google Play" }
                running = false
            }
        },
        onSelect = { player, remember ->
            val pending = pendingPlayback ?: return@PlaybackPickerDialog
            pendingPlayback = null
            scope.launch {
                running = true
                runCatching {
                    when (pending) {
                        is PendingPlayback.Browse -> playbackActions.playBrowseItem(pending.item, player, remember)
                        is PendingPlayback.Bookmark -> playbackActions.playBookmark(pending.bookmark, player, remember)
                        is PendingPlayback.Watching -> playbackActions.playWatchingNow(pending.item, player, remember)
                        is PendingPlayback.WatchingEpisode -> playbackActions.playWatchingNowEpisode(pending.episode, player, remember)
                        is PendingPlayback.Binge -> playbackActions.playBingeWatchSeason(pending.series, pending.episodes, pending.seasonKey, player, remember)
                        }
                    }
                    .onSuccess {
                        statusText = it.message
                        if (it.launched && pending is PendingPlayback.Bookmark) reload()
                    }
                    .onFailure { statusText = "Unable to open bookmark" }
                running = false
            }
        }
    )
}

private fun bookmarkEmptyTitle(query: String, selectedCategoryId: String?): String =
    when {
        query.isNotBlank() -> "No bookmark matches"
        selectedCategoryId == RECENTLY_PLAYED_BOOKMARKS_CATEGORY_ID -> "No recent bookmarks"
        else -> "No bookmarks"
    }

private fun bookmarkEmptyDetail(query: String, selectedCategoryId: String?): String =
    when {
        query.isNotBlank() -> "Try a shorter search term."
        selectedCategoryId == RECENTLY_PLAYED_BOOKMARKS_CATEGORY_ID -> "Play a bookmark to build this list."
        else -> "Save channels from the Channels screen."
    }

@Composable
internal fun BookmarkRow(
    bookmark: MobileBookmark,
    recentlyPlayed: Boolean,
    showThumbnail: Boolean,
    logoRenderer: LogoRenderer,
    onPlay: () -> Unit,
    onChoosePlayer: () -> Unit,
    onRemove: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .combinedClickable(
                enabled = bookmark.command.isNotBlank(),
                onClick = onPlay,
                onLongClick = onChoosePlayer
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = DeepNightSurfaceHigh)
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                ChannelLogo(
                    label = bookmark.channelName.take(2).uppercase(),
                    logo = bookmark.logo,
                    fallbackLoader = epgThumbnail(bookmark.accountId, bookmark.channelId, bookmark.channelName, showThumbnail && bookmark.mode == BrowseMode.LIVE),
                    showThumbnail = showThumbnail,
                    contentDescription = "Logo ${bookmark.channelName}",
                    logoRenderer = logoRenderer
                )
            },
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        bookmark.channelName,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (MobilePlaybackIndicator.isActive(bookmark.accountId, bookmark.mode, bookmark.channelId)) {
                        PlayingIndicator()
                    }
                    if (bookmark.hasDrmMetadata()) {
                        DrmBadge(Modifier.padding(start = 8.dp))
                    }
                }
            },
            trailingContent = {
                ItemActionsMenu(bookmark.channelName, onChoosePlayer, onRemove,
                    if (recentlyPlayed) "Remove from Recent" else "Remove bookmark")
            },
            supportingContent = {
                Text(bookmark.accountName, color = DeepNightMutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        )
        if (bookmark.mode == BrowseMode.LIVE && LocalEpgState.current.enabled && !LocalEpgState.current.playbackOnly) {
            Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                MobileEpgChannel(bookmark.accountId, bookmark.channelId, bookmark.channelName)
            }
        }
    }
}

@Composable
private fun DrmBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.semantics { contentDescription = "DRM protected" },
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFFFFD54F),
        contentColor = Color(0xFF151515)
    ) {
        Text(
            "DRM",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}



@Composable
private fun WatchingNowScreen(
    browseActions: BrowseUiActions,
    playbackActions: PlaybackUiActions,
    showThumbnails: Boolean,
    logoRenderer: LogoRenderer,
    playerIconRenderer: PlayerIconRenderer,
    resumeSignal: Int,
    wideLayout: Boolean,
    wideSearchVisible: Boolean,
    backHandler: @Composable (Boolean, () -> Unit) -> Unit,
    detailsPanelVisible: Boolean,
    onDetailsPanelVisibleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<MobileWatchingNowItem>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Loading") }
    var running by remember { mutableStateOf(false) }
    var pendingPlayback by remember { mutableStateOf<PendingPlayback?>(null) }
    var playerChoices by remember { mutableStateOf<List<PlayerChoice>>(emptyList()) }
    var selectedSeries by remember { mutableStateOf<MobileWatchingNowItem?>(null) }
    var seriesEpisodes by remember { mutableStateOf<List<MobileWatchingNowEpisode>>(emptyList()) }
    var pendingRemoveWatchingNow by remember { mutableStateOf<MobileWatchingNowItem?>(null) }
    var pendingEpisodeMenu by remember { mutableStateOf<MobileWatchingNowEpisode?>(null) }
    var wideWatchingFilter by rememberSaveable { mutableStateOf(WatchingNowFilter.SERIES) }

    fun activeWatchingQuery(): String = query

    var listLoad by remember { mutableStateOf<Job?>(null) }
    fun reload(search: String = activeWatchingQuery()) {
        listLoad?.cancel()
        listLoad = scope.launch {
            running = true
            runCatching { browseActions.listWatchingNow(search) }
                .onSuccess { loaded ->
                    items = loaded
                    statusText = if (loaded.isEmpty()) "Nothing to resume" else "${loaded.size} resume entries"

                }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; statusText = "Unable to load watching-now" }
            running = false
        }
    }

    fun play(pending: PendingPlayback) {
        scope.launch {
            running = true
            val preference = playbackActions.loadPlayerPreference()
            if (
                preference.rememberForFutureStreams &&
                preference.selectedPlayer != AndroidPlayerPreference.ASK_EVERY_TIME &&
                pending.supportsRememberedPlayer(preference.selectedPlayer)
            ) {
                runCatching {
                    when (pending) {
                        is PendingPlayback.Browse -> playbackActions.playBrowseItem(pending.item, preference.selectedPlayer, false)
                        is PendingPlayback.Bookmark -> playbackActions.playBookmark(pending.bookmark, preference.selectedPlayer, false)
                        is PendingPlayback.Watching -> playbackActions.playWatchingNow(pending.item, preference.selectedPlayer, false)
                        is PendingPlayback.WatchingEpisode -> playbackActions.playWatchingNowEpisode(pending.episode, preference.selectedPlayer, false)
                        is PendingPlayback.Binge -> playbackActions.playBingeWatchSeason(pending.series, pending.episodes, pending.seasonKey, preference.selectedPlayer, false)
                    }
                }
                    .onSuccess {
                        statusText = it.message
                        if (it.launched) {
                            seriesEpisodes = when (pending) {
                                is PendingPlayback.WatchingEpisode -> seriesEpisodes.withWatchingFlag(pending.episode)
                                is PendingPlayback.Binge -> seriesEpisodes.withBingeStartFlag(pending.seasonKey)
                                else -> seriesEpisodes
                            }
                        }
                    }
                    .onFailure { statusText = "Unable to resume" }
            } else {
                playerChoices = playbackActions.playerChoices().choicesFor(pending)
                pendingPlayback = pending
            }
            running = false
        }
    }

    fun openWatchingNow(item: MobileWatchingNowItem) {
        if (item.mode != BrowseMode.SERIES) {
            play(PendingPlayback.Watching(item))
            return
        }
        scope.launch {
            running = true
            runCatching { browseActions.listWatchingNowEpisodes(item) }
                .onSuccess { episodes ->
                    selectedSeries = item
                    seriesEpisodes = episodes
                    statusText = if (episodes.isEmpty()) "No cached episodes for ${item.title}" else "${episodes.size} cached episodes"
                    runCatching { browseActions.enrichSeriesDetails(item, episodes) }
                        .onSuccess { details ->
                            selectedSeries = details.series
                            seriesEpisodes = details.episodes
                        }
                }
                .onFailure { statusText = "Unable to open series" }
            running = false
        }
    }

    fun removeWatchingNow(item: MobileWatchingNowItem) {
        scope.launch {
            running = true
            runCatching { browseActions.removeWatchingNow(item) }
                .onSuccess {
                    items = items.filterNot { it.mode == item.mode && it.rowId == item.rowId }
                    if (selectedSeries?.rowId == item.rowId && selectedSeries?.mode == item.mode) {
                        selectedSeries = null
                        seriesEpisodes = emptyList()
                    }
                    statusText = "Removed ${item.title}"
                }
                .onFailure { statusText = "Unable to remove item" }
            running = false
        }
    }

    LaunchedEffect(browseActions) {
        reload()
    }

    LaunchedEffect(resumeSignal) {
        val currentSeries = selectedSeries
        if (currentSeries != null) {
            openWatchingNow(currentSeries)
        } else {
            reload()
        }
    }

    backHandler(selectedSeries != null) { selectedSeries = null; seriesEpisodes = emptyList() }
    val series = selectedSeries
    if (series != null) {
        WatchingNowSeriesDetail(
            series = series,
            episodes = seriesEpisodes,
            running = running,
            statusText = statusText,
            showThumbnail = showThumbnails,
            logoRenderer = logoRenderer,
            onBack = {
                selectedSeries = null
                seriesEpisodes = emptyList()
            },
            onPlayEpisode = { episode -> play(PendingPlayback.WatchingEpisode(episode)) },
            onBingeSeason = { seasonKey -> play(PendingPlayback.Binge(series, seriesEpisodes, seasonKey)) },
            onEpisodeMenu = { episode ->
                scope.launch {
                    playerChoices = playbackActions.playerChoices()
                    pendingEpisodeMenu = episode
                }
            },
            onRemoveSeries = { pendingRemoveWatchingNow = series },
            wideLayout = wideLayout,
            wideSearchVisible = wideSearchVisible,
            detailsPanelVisible = detailsPanelVisible,
            onDetailsPanelVisibleChange = onDetailsPanelVisibleChange,
            modifier = modifier
        )
        EpisodeActionSheet(
            episode = pendingEpisodeMenu,
            playerChoices = playerChoices,
            playerIconRenderer = playerIconRenderer,
            wideLayout = wideLayout,
            onDismiss = { pendingEpisodeMenu = null },
            onMarkWatching = { episode ->
                pendingEpisodeMenu = null
                scope.launch {
                    running = true
                    runCatching { browseActions.markWatchingNowEpisode(episode) }
                        .onSuccess {
                            seriesEpisodes = seriesEpisodes.withWatchingFlag(episode)
                            statusText = "Marked ${episode.title} as Watching Now"
                        }
                        .onFailure { statusText = "Unable to mark Watching Now" }
                    running = false
                }
            },
            onClearWatching = { episode ->
                pendingEpisodeMenu = null
                scope.launch {
                    running = true
                    runCatching { browseActions.clearWatchingNowEpisode(episode) }
                        .onSuccess {
                            seriesEpisodes = seriesEpisodes.clearWatchingFlag(episode)
                            items = items.filterNot { item ->
                                item.mode == BrowseMode.SERIES &&
                                    item.accountId == episode.accountId &&
                                    item.categoryProviderId == episode.categoryProviderId &&
                                    item.contentId == episode.seriesId
                            }
                            statusText = "Removed Watching Now flag"
                        }
                        .onFailure { statusText = "Unable to remove Watching Now flag" }
                    running = false
                }
            },
            onInstall = { choice ->
                pendingEpisodeMenu = null
                scope.launch {
                    running = true
                    runCatching { playbackActions.openPlayerInstall(choice) }
                        .onSuccess { statusText = "Opening ${choice.label} in Google Play" }
                        .onFailure { statusText = "Unable to open Google Play" }
                    running = false
                }
            },
            onPlay = { episode, player ->
                pendingEpisodeMenu = null
                scope.launch {
                    running = true
                    runCatching { playbackActions.playWatchingNowEpisode(episode, player, false) }
                        .onSuccess {
                            statusText = it.message
                            if (it.launched) {
                                seriesEpisodes = seriesEpisodes.withWatchingFlag(episode)
                            }
                        }
                        .onFailure { statusText = "Unable to open episode" }
                    running = false
                }
            }
        )
        ConfirmRemoveWatchingNowDialog(
            item = pendingRemoveWatchingNow,
            onDismiss = { pendingRemoveWatchingNow = null },
            onConfirm = { item ->
                pendingRemoveWatchingNow = null
                removeWatchingNow(item)
            }
        )
        PlaybackPickerDialog(
            pendingPlayback = pendingPlayback,
            playerChoices = playerChoices,
            playerIconRenderer = playerIconRenderer,
            wideLayout = wideLayout,
            onDismiss = { pendingPlayback = null },
            onInstall = { choice ->
                pendingPlayback = null
                scope.launch {
                    running = true
                    runCatching { playbackActions.openPlayerInstall(choice) }
                        .onSuccess { statusText = "Opening ${choice.label} in Google Play" }
                        .onFailure { statusText = "Unable to open Google Play" }
                    running = false
                }
            },
            onSelect = { player, remember ->
                val pending = pendingPlayback ?: return@PlaybackPickerDialog
                pendingPlayback = null
                scope.launch {
                    running = true
                    runCatching {
                        when (pending) {
                            is PendingPlayback.Browse -> playbackActions.playBrowseItem(pending.item, player, remember)
                            is PendingPlayback.Bookmark -> playbackActions.playBookmark(pending.bookmark, player, remember)
                            is PendingPlayback.Watching -> playbackActions.playWatchingNow(pending.item, player, remember)
                            is PendingPlayback.WatchingEpisode -> playbackActions.playWatchingNowEpisode(pending.episode, player, remember)
                            is PendingPlayback.Binge -> playbackActions.playBingeWatchSeason(pending.series, pending.episodes, pending.seasonKey, player, remember)
                    }
                }
                        .onSuccess {
                            statusText = it.message
                            if (it.launched) {
                                seriesEpisodes = when (pending) {
                                    is PendingPlayback.WatchingEpisode -> seriesEpisodes.withWatchingFlag(pending.episode)
                                    is PendingPlayback.Binge -> seriesEpisodes.withBingeStartFlag(pending.seasonKey)
                                    else -> seriesEpisodes
                                }
                            }
                        }
                        .onFailure { statusText = "Unable to resume" }
                    running = false
                }
            }
        )
        return
    }



    val watchingListState = rememberLazyListState()
    Column(modifier.fillMaxSize()) {
        MobileScreenHeader("Continue Watching", "Pick up a movie or series", searchVisible = searchVisible,
            searchQuery = query, searchLabel = "Search movies and series", onSearchToggle = {
                searchVisible = !searchVisible
                if (!searchVisible) { query = ""; reload("") }
            }, onSearchQueryChange = { query = it; reload(it) })
        LazyRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(WatchingNowFilter.entries, key = { it.name }) { filter ->
                FilterChip(selected = wideWatchingFilter == filter, onClick = { wideWatchingFilter = filter }, label = { Text(filter.label) })
            }
        }
        LazyColumn(
            state = watchingListState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (items.none(wideWatchingFilter::matches)) {
                item {
                    EmptyState(
                        title = if (query.isBlank()) "Nothing to resume" else "No resume matches",
                        detail = if (query.isBlank()) "VOD and series appear here after playback starts." else "Try a shorter search term."
                    )
                }
            }
            items(items.filter(wideWatchingFilter::matches), key = { it.stableWatchingNowKey() }) { item ->
                var enriched by remember(item) { mutableStateOf(item) }
                LaunchedEffect(item) {
                    try { enriched = browseActions.enrichWatchingNowItem(item) }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { /* Keep the cached row when optional metadata is unavailable. */ }
                }
                WatchingNowRow(
                    item = enriched,
                    showThumbnail = showThumbnails,
                    logoRenderer = logoRenderer,
                    onOpen = { openWatchingNow(enriched) },
                    onRemove = { pendingRemoveWatchingNow = item }
                )
            }
            item {
                if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(statusText, color = DeepNightMutedText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    PlaybackPickerDialog(
        pendingPlayback = pendingPlayback,
        playerChoices = playerChoices,
        playerIconRenderer = playerIconRenderer,
        wideLayout = false,
        onDismiss = { pendingPlayback = null },
        onInstall = { choice ->
            pendingPlayback = null
            scope.launch {
                running = true
                runCatching { playbackActions.openPlayerInstall(choice) }
                    .onSuccess { statusText = "Opening ${choice.label} in Google Play" }
                    .onFailure { statusText = "Unable to open Google Play" }
                running = false
            }
        },
        onSelect = { player, remember ->
            val pending = pendingPlayback ?: return@PlaybackPickerDialog
            pendingPlayback = null
            scope.launch {
                running = true
                runCatching {
                    when (pending) {
                        is PendingPlayback.Browse -> playbackActions.playBrowseItem(pending.item, player, remember)
                        is PendingPlayback.Bookmark -> playbackActions.playBookmark(pending.bookmark, player, remember)
                        is PendingPlayback.Watching -> playbackActions.playWatchingNow(pending.item, player, remember)
                        is PendingPlayback.WatchingEpisode -> playbackActions.playWatchingNowEpisode(pending.episode, player, remember)
                        is PendingPlayback.Binge -> playbackActions.playBingeWatchSeason(pending.series, pending.episodes, pending.seasonKey, player, remember)
                    }
                }
                    .onSuccess {
                        statusText = it.message
                        if (it.launched) {
                            seriesEpisodes = when (pending) {
                                is PendingPlayback.WatchingEpisode -> seriesEpisodes.withWatchingFlag(pending.episode)
                                is PendingPlayback.Binge -> seriesEpisodes.withBingeStartFlag(pending.seasonKey)
                                else -> seriesEpisodes
                            }
                        }
                    }
                    .onFailure { statusText = "Unable to resume" }
                running = false
            }
        }
    )
    ConfirmRemoveWatchingNowDialog(
        item = pendingRemoveWatchingNow,
        onDismiss = { pendingRemoveWatchingNow = null },
        onConfirm = { item ->
            pendingRemoveWatchingNow = null
            removeWatchingNow(item)
        }
    )
}

@Composable
private fun ConfirmRemoveWatchingNowDialog(
    item: MobileWatchingNowItem?,
    onDismiss: () -> Unit,
    onConfirm: (MobileWatchingNowItem) -> Unit
) {
    val target = item ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove from Watching Now?") },
        text = { Text("Remove ${target.title} from Watching Now?") },
        confirmButton = {
            TextButton(onClick = { onConfirm(target) }) {
                Text("Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun WatchingNowRow(
    item: MobileWatchingNowItem, showThumbnail: Boolean, logoRenderer: LogoRenderer,
    wideLayout: Boolean = false, onOpen: () -> Unit, onRemove: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().clickable(enabled = item.mode == BrowseMode.SERIES || item.command.isNotBlank(), onClick = onOpen),
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(72.dp).height(108.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.PlayCircle, contentDescription = null)
                if (showThumbnail && item.logo.isNotBlank()) logoRenderer(item.logo, item.title, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(item.accountName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (item.mode == BrowseMode.SERIES) "Choose an episode" else "Watch again", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Actions for ${item.title}") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Remove from Watching", color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onRemove() })
                }
            }
        }
    }
}

@Composable
private fun WatchingNowSeriesDetail(
    series: MobileWatchingNowItem,
    episodes: List<MobileWatchingNowEpisode>,
    running: Boolean,
    statusText: String,
    showThumbnail: Boolean,
    logoRenderer: LogoRenderer,
    onBack: () -> Unit,
    onPlayEpisode: (MobileWatchingNowEpisode) -> Unit,
    onBingeSeason: (String) -> Unit,
    onEpisodeMenu: (MobileWatchingNowEpisode) -> Unit,
    onRemoveSeries: () -> Unit,
    showRemove: Boolean = true,
    emptyTitle: String = "No cached episodes",
    emptyDetail: String = "Refresh this account on desktop or Android to cache episode links.",
    wideLayout: Boolean = false,
    wideSearchVisible: Boolean = false,
    detailsPanelVisible: Boolean = true,
    onDetailsPanelVisibleChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val seasonTabs = remember(episodes) { episodes.seasonTabs() }
    val watchedSeasonKey = remember(episodes) { episodes.firstOrNull { it.isWatched }?.seasonTab()?.key.orEmpty() }
    var selectedSeasonKey by rememberSaveable(series.accountId, series.categoryProviderId, series.contentId) { mutableStateOf(watchedSeasonKey) }
    var seasonPickedByUser by rememberSaveable(series.accountId, series.categoryProviderId, series.contentId) { mutableStateOf(false) }
    val activeSeasonKey = seasonTabs.firstOrNull { it.key == selectedSeasonKey }?.key
        ?: seasonTabs.firstOrNull { it.key == watchedSeasonKey }?.key
        ?: seasonTabs.firstOrNull()?.key.orEmpty()
    LaunchedEffect(seasonTabs, watchedSeasonKey, activeSeasonKey, seasonPickedByUser) {
        val preferred = if (!seasonPickedByUser && watchedSeasonKey.isNotBlank()) watchedSeasonKey else activeSeasonKey
        if (preferred != selectedSeasonKey) selectedSeasonKey = preferred
    }
    val visibleEpisodes = remember(episodes, activeSeasonKey) {
        if (activeSeasonKey.isBlank()) episodes else episodes.filter { it.seasonTab().key == activeSeasonKey }
    }
    val listState = rememberLazyListState()
    val hasMetadata = series.logo.isNotBlank() || series.metadataLine().isNotBlank() || series.plot.isNotBlank() || series.imdbUrl.isNotBlank()
    val headerItems = 1 + (if (hasMetadata) 1 else 0) + (if (seasonTabs.isNotEmpty()) 1 else 0) +
        (if (activeSeasonKey.isNotBlank() && visibleEpisodes.isNotEmpty()) 1 else 0)
    LaunchedEffect(activeSeasonKey, visibleEpisodes) {
        if (visibleEpisodes.isNotEmpty()) {
            val episodeIndex = visibleEpisodes.indexOfFirst { it.isWatched }.takeIf { it >= 0 } ?: 0
            listState.scrollToItem(headerItems + episodeIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MobileBackButton(onBack)
                Text(series.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (showRemove) FavouriteStar(true, "Remove watching now ${series.title}", onClick = onRemoveSeries, compact = true)
            }
        }
        if (hasMetadata) item { SeriesMetadataHeader(series, logoRenderer) }
        if (seasonTabs.isNotEmpty()) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(seasonTabs, key = { it.key }) { tab ->
                    FilterChip(selected = tab.key == activeSeasonKey,
                        onClick = { seasonPickedByUser = true; selectedSeasonKey = tab.key },
                        label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
        }
        if (activeSeasonKey.isNotBlank() && visibleEpisodes.isNotEmpty()) item {
            OutlinedButton(onClick = { onBingeSeason(activeSeasonKey) }, modifier = Modifier.fillMaxWidth()) { Text("Binge Watch") }
        }
        if (episodes.isEmpty()) item { EmptyState(emptyTitle, emptyDetail) }
        items(visibleEpisodes, key = { it.stableWatchingNowEpisodeKey() }) { episode ->
            WatchingNowEpisodeRow(episode, showThumbnail, logoRenderer, wideLayout = wideLayout,
                onPlay = { onPlayEpisode(episode) }, onMenu = { onEpisodeMenu(episode) })
        }
        item {
            if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(statusText, color = DeepNightMutedText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WatchingNowEpisodeRow(
    episode: MobileWatchingNowEpisode,
    showThumbnail: Boolean,
    logoRenderer: LogoRenderer,
    wideLayout: Boolean = false,
    onPlay: () -> Unit,
    onMenu: () -> Unit
) {
    val isWatching = episode.isWatched
    val textColor = if (isWatching) MaterialTheme.colorScheme.onPrimaryContainer else DeepNightText
    val mutedColor = if (isWatching) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    if (wideLayout) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .combinedClickable(
                    onClick = onPlay,
                    onLongClick = onMenu
                ),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (isWatching) MaterialTheme.colorScheme.primaryContainer else DeepNightSurfaceHigh
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChannelLogo(
                        label = episode.title.take(2).uppercase(),
                        logo = episode.logo,
                        showThumbnail = showThumbnail,
                        contentDescription = "Logo ${episode.title}",
                        logoRenderer = logoRenderer
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (episode.isWatched) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Text(
                                    MobileMessages.text("autoWatching"),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                        Text(
                            episode.title,
                            color = textColor,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        val release = episode.releaseDate.desktopReleaseLine()
                        val rating = episode.rating.takeIf { it.isNotBlank() }?.let { "IMDb $it" }.orEmpty()
                        val meta = listOf(release, rating, episode.duration).filter { it.isNotBlank() }.joinToString(" - ")
                        if (meta.isNotBlank()) {
                            Text(meta, color = mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Text(
                    episode.plot.ifBlank { episode.seriesTitle },
                    color = mutedColor,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onMenu) {
                        Text("More")
                    }
                }
            }
        }
        return
    }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .combinedClickable(
                onClick = onPlay,
                onLongClick = onMenu
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isWatching) MaterialTheme.colorScheme.primaryContainer else DeepNightSurfaceHigh
        )
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                ChannelLogo(
                    label = episode.title.take(2).uppercase(),
                    logo = episode.logo,
                    showThumbnail = showThumbnail,
                    contentDescription = "Logo ${episode.title}",
                    logoRenderer = logoRenderer
                )
            },
            headlineContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (episode.isWatched) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Text(
                                MobileMessages.text("autoWatching"),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                    Text(
                        episode.title,
                        color = textColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            trailingContent = {
                IconButton(
                    modifier = Modifier.semantics { contentDescription = "Episode options ${episode.title}" },
                    onClick = onMenu
                ) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = null, tint = textColor)
                }
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val release = episode.releaseDate.desktopReleaseLine()
                    val rating = episode.rating.takeIf { it.isNotBlank() }?.let { "IMDb $it" }.orEmpty()
                    val meta = listOf(release, rating, episode.duration).filter { it.isNotBlank() }.joinToString(" - ")
                    if (meta.isNotBlank()) {
                        Text(meta, color = mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        episode.plot.ifBlank { episode.seriesTitle },
                        color = mutedColor,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        )
    }
}

@Composable
private fun SeriesMetadataHeader(
    series: MobileWatchingNowItem,
    logoRenderer: LogoRenderer,
    plotBelowPoster: Boolean = false
) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (series.logo.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .width(112.dp)
                    .height(160.dp)
                    .clip(RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                color = DeepNightSurfaceHighest
            ) {
                logoRenderer(
                    series.logo,
                    "Poster ${series.title}",
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (series.rating.isNotBlank() || series.imdbUrl.isNotBlank()) {
                ImdbBadge(
                    rating = series.rating,
                    imdbUrl = series.imdbUrl,
                    onOpen = { url -> uriHandler.openUri(url) }
                )
            }
            if (series.genre.isNotBlank()) {
                Text("Genre: ${series.genre}", color = DeepNightMutedText, style = MaterialTheme.typography.bodySmall)
            }
            val release = series.releaseDate.desktopShortDate()
            if (release.isNotBlank()) {
                Text("Release: $release", color = DeepNightMutedText, style = MaterialTheme.typography.bodySmall)
            }
            if (series.duration.isNotBlank()) {
                Text(series.duration, color = DeepNightMutedText, style = MaterialTheme.typography.bodySmall)
            }
            if (!plotBelowPoster && series.plot.isNotBlank()) {
                Text(
                    series.plot,
                    color = DeepNightMutedText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 7,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    if (plotBelowPoster && series.plot.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
            series.plot,
            color = DeepNightMutedText,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 10,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ImdbBadge(
    rating: String,
    imdbUrl: String,
    onOpen: (String) -> Unit
) {
    val url = imdbUrl.normalizedImdbUrl()
    Surface(
        modifier = if (url.isNotBlank()) Modifier.clickable { onOpen(url) } else Modifier,
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFFF5C518),
        contentColor = Color(0xFF111111)
    ) {
        Text(
            listOf("IMDb", rating).filter { it.isNotBlank() }.joinToString(" "),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun EpisodeActionSheet(
    episode: MobileWatchingNowEpisode?,
    playerChoices: List<PlayerChoice>,
    playerIconRenderer: PlayerIconRenderer,
    wideLayout: Boolean = false,
    onDismiss: () -> Unit,
    onMarkWatching: (MobileWatchingNowEpisode) -> Unit,
    onClearWatching: (MobileWatchingNowEpisode) -> Unit,
    onInstall: (PlayerChoice) -> Unit,
    onPlay: (MobileWatchingNowEpisode, AndroidPlayerPreference) -> Unit
) {
    val target = episode ?: return
    var confirmRemove by remember(target) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DeepNightSurface,
        contentColor = DeepNightText
    ) {
        WideSheetBox(wideLayout = wideLayout) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            Text(target.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (target.isWatched) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Watching",
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFFD700))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        color = Color(0xFF151515),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(onClick = { confirmRemove = true }) {
                        Text("Remove Watching Now")
                    }
                }
            } else {
                Button(onClick = { onMarkWatching(target) }) {
                    Text("Watching Now")
                }
            }
            PlayerChoiceGrid(
                choices = playerChoices,
                selectedPlayer = null,
                playerIconRenderer = playerIconRenderer,
                onSelect = { choice ->
                    if (choice.opensInstallFlow()) {
                        onInstall(choice)
                    } else {
                        onPlay(target, choice.player)
                    }
                }
            )
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(Modifier.height(12.dp))
            }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove Watching Now?") },
            text = { Text("Remove ${target.title} from Watching Now?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        onClearWatching(target)
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PlaybackPickerDialog(
    pendingPlayback: PendingPlayback?,
    playerChoices: List<PlayerChoice>,
    playerIconRenderer: PlayerIconRenderer,
    wideLayout: Boolean = false,
    onDismiss: () -> Unit,
    onInstall: (PlayerChoice) -> Unit,
    onSelect: (AndroidPlayerPreference, Boolean) -> Unit
) {
    if (pendingPlayback == null) {
        return
    }
    var rememberChoice by remember(pendingPlayback) { mutableStateOf(false) }
    val choices = remember(pendingPlayback, playerChoices) {
        playerChoices.choicesFor(pendingPlayback)
    }
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DeepNightSurface,
        contentColor = DeepNightText
    ) {
        WideSheetBox(wideLayout = wideLayout) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            Text(
                if (pendingPlayback is PendingPlayback.Binge) "Binge Watch" else "Open Stream",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            PlayerChoiceGrid(
                choices = choices,
                selectedPlayer = null,
                playerIconRenderer = playerIconRenderer,
                onSelect = { choice ->
                    if (choice.opensInstallFlow()) {
                        onInstall(choice)
                    } else {
                        onSelect(choice.player, rememberChoice)
                    }
                }
            )
            if (pendingPlayback !is PendingPlayback.Binge) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                MobileM3Switch(
                    modifier = Modifier.semantics { contentDescription = "Remember player choice" },
                    checked = rememberChoice,
                    onCheckedChange = { rememberChoice = it }
                )
                Text("Always use this player")
                }
            }
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PlayerSelectionSheet(
    title: String,
    choices: List<PlayerChoice>,
    selectedPlayer: AndroidPlayerPreference?,
    playerIconRenderer: PlayerIconRenderer,
    wideLayout: Boolean = false,
    onDismiss: () -> Unit,
    onInstall: (PlayerChoice) -> Unit,
    onSelect: (PlayerChoice) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DeepNightSurface,
        contentColor = DeepNightText
    ) {
        WideSheetBox(wideLayout = wideLayout) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            PlayerChoiceGrid(
                choices = choices,
                selectedPlayer = selectedPlayer,
                playerIconRenderer = playerIconRenderer,
                onSelect = { choice ->
                    if (choice.matchesSelected(selectedPlayer)) {
                        onSelect(choice)
                    } else if (choice.opensInstallFlow()) {
                        onInstall(choice)
                    } else {
                        onSelect(choice)
                    }
                }
            )
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun WideSheetBox(
    wideLayout: Boolean,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = if (wideLayout) {
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
            } else {
                Modifier.fillMaxWidth()
            }
        ) {
            content()
        }
    }
}

@Composable
private fun PlayerChoiceGrid(
    choices: List<PlayerChoice>,
    selectedPlayer: AndroidPlayerPreference?,
    playerIconRenderer: PlayerIconRenderer,
    onSelect: (PlayerChoice) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        choices.chunked(3).forEach { rowChoices ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowChoices.forEach { choice ->
                    PlayerChoiceTile(
                        choice = choice,
                        selected = choice.matchesSelected(selectedPlayer),
                        playerIconRenderer = playerIconRenderer,
                        modifier = Modifier.weight(1f),
                        onSelect = { onSelect(choice) }
                    )
                }
                repeat(3 - rowChoices.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PlayerChoiceTile(
    choice: PlayerChoice,
    selected: Boolean,
    playerIconRenderer: PlayerIconRenderer,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    val containerColor = when {
        selected -> DeepNightPrimary
        else -> DeepNightSurfaceHigh
    }
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> DeepNightText
    }
    Surface(
        modifier = modifier
            .height(88.dp)
            .clickable(onClick = onSelect)
            .semantics { contentDescription = "Select ${choice.label}" },
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                playerIconRenderer(choice, Modifier.size(46.dp))
                if (selected) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.TopEnd),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Text(
                choice.label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 11.sp
            )
        }
    }
}

@Composable
fun DefaultPlayerIcon(choice: PlayerChoice, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = choice.player.playerIconColor(),
        contentColor = choice.player.playerIconContentColor()
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (choice.player) {
                AndroidPlayerPreference.EMBEDDED_PLAYER ->
                    Icon(Icons.Outlined.PlayCircle, contentDescription = null, modifier = Modifier.size(28.dp))
                AndroidPlayerPreference.NATIVE ->
                    Icon(Icons.Outlined.Android, contentDescription = null, modifier = Modifier.size(27.dp))
                AndroidPlayerPreference.ASK_EVERY_TIME ->
                    Icon(Icons.Outlined.MoreVert, contentDescription = null, modifier = Modifier.size(27.dp))
                else ->
                    Text(
                        choice.player.playerBadge(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
            }
        }
    }
}

@Composable
private fun UserPill(
    providerName: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = DeepNightSurfaceHigh,
        contentColor = DeepNightText
    ) {
        Row(
            modifier = Modifier.padding(
                start = if (compact) 3.dp else 4.dp,
                top = if (compact) 3.dp else 4.dp,
                end = if (compact) 8.dp else 12.dp,
                bottom = if (compact) 3.dp else 4.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .width(if (compact) 20.dp else 28.dp)
                    .height(if (compact) 20.dp else 28.dp),
                shape = CircleShape,
                color = DeepNightAccent,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        providerName.trim().take(1).ifBlank { "U" }.uppercase(),
                        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                providerName,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RemoteSyncScreen(
    syncActions: RemoteSyncUiActions,
    browseActions: BrowseUiActions,
    playbackActions: PlaybackUiActions,
    filterActions: FilterUiActions,
    backupRestoreActions: BackupRestoreUiActions,
    backupFileCreator: BackupFileCreator?,
    restoreFilePicker: RestoreFilePicker?,
    onThumbnailSettingChanged: (Boolean) -> Unit,
    playerIconRenderer: PlayerIconRenderer,
    wideLayout: Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("") }
    var useHttps by remember { mutableStateOf(false) }
    var verificationCode by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Enter the desktop host and sync port.") }
    var running by remember { mutableStateOf(false) }
    var lastSyncText by remember { mutableStateOf("Never synced") }
    var playerText by remember { mutableStateOf("Player: ${AndroidPlayerPreference.EMBEDDED_PLAYER.playerLabel()}") }
    var selectedPlayer by remember { mutableStateOf(AndroidPlayerPreference.EMBEDDED_PLAYER) }
    var playerChoices by remember { mutableStateOf<List<PlayerChoice>>(emptyList()) }
    var playerSelectorVisible by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var filters by remember { mutableStateOf(AndroidFilterSettings()) }
    var categoryFilterText by remember { mutableStateOf("") }
    var channelFilterText by remember { mutableStateOf("") }
    var filterEditorVisible by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var settingsPage by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsQuery by rememberSaveable { mutableStateOf("") }
    val settingsDestinations = remember {
        listOf(
            MobileSettingsDestination("sync", "Desktop sync", "Connect to UIPTV Desktop and sync your library", Icons.Outlined.CloudSync),
            MobileSettingsDestination("backup", "Backup and restore", "Protect or restore your mobile data", Icons.Outlined.Backup),
            MobileSettingsDestination("appearance", "Appearance and browsing", "Artwork and catalogue presentation", Icons.Outlined.Palette),
            MobileSettingsDestination("playback", "Playback", "Choose the player used for streams", Icons.Outlined.PlayCircle),
            MobileSettingsDestination("guide", "Programme guide", "EPG sources and guide loading", Icons.Outlined.LiveTv),
            MobileSettingsDestination("history", "Playback history", "Manage recently played channels", Icons.Outlined.History),
            MobileSettingsDestination("filters", "Content filters", "Hide categories and channels by rule", Icons.Outlined.FilterAlt),
            MobileSettingsDestination("about", "About and data", "App information and local data", Icons.Outlined.Info)
        )
    }
    val activeSettingsDestination = settingsDestinations.firstOrNull { it.id == settingsPage }

    LaunchedEffect(syncActions) {
        val snapshot = syncActions.loadPreferences()
        val savedEndpointHost = snapshot.remoteEndpoint.host
        val savedHost = savedEndpointHost
            .withoutRemoteSyncScheme()
            .takeUnless { it.isLoopbackHost() }
            .orEmpty()
        host = savedHost
        useHttps = savedEndpointHost.hasRemoteSyncHttpsScheme()
        portText = if (savedHost.isBlank()) {
            ""
        } else {
            snapshot.remoteEndpoint.port.toString()
        }
        lastSyncText = snapshot.remoteEndpoint.lastSuccessfulSyncEpochSeconds
            ?.let { "Last sync: ${formatEpochSeconds(it)}" }
            ?: "Never synced"
        selectedPlayer = snapshot.playerPreference.selectedPlayer
        playerText = "Player: ${snapshot.playerPreference.selectedPlayer.playerLabel()}"
        playerChoices = playbackActions.playerChoices()
        val loadedFilters = filterActions.load()
        filters = loadedFilters
        onThumbnailSettingChanged(loadedFilters.enableThumbnails)
        categoryFilterText = loadedFilters.categoryFilters
        channelFilterText = loadedFilters.channelFilters
    }

    fun parsedPort(): Int? {
        val port = portText.trim().toIntOrNull()
        if (port !in 1..65_535) {
            statusText = "Enter a port from 1 to 65535."
            return null
        }
        return port
    }

    fun updateHost(value: String) {
        when {
            value.hasRemoteSyncHttpsScheme() -> useHttps = true
            value.hasRemoteSyncHttpScheme() -> useHttps = false
        }
        host = value.withoutRemoteSyncScheme()
    }

    fun requestHost(): String =
        remoteSyncRequestHost(host, useHttps)

    suspend fun reloadFiltersFromDatabase() {
        val loadedFilters = filterActions.load()
        filters = loadedFilters
        onThumbnailSettingChanged(loadedFilters.enableThumbnails)
        categoryFilterText = loadedFilters.categoryFilters
        channelFilterText = loadedFilters.channelFilters
    }

    fun launchBackup() {
        val creator = backupFileCreator
        if (creator == null) {
            statusText = "Backup destination picker is unavailable."
            return
        }
        creator(MobileBackupArchive.defaultFileName(System.currentTimeMillis() / 1000L)) { uri ->
            if (uri.isNullOrBlank()) {
                statusText = "Backup cancelled"
                return@creator
            }
            scope.launch {
                running = true
                statusText = "Creating backup"
                runCatching { backupRestoreActions.backupToUri(uri) }
                    .onSuccess { statusText = it.message }
                    .onFailure { statusText = "Backup failed" }
                running = false
            }
        }
    }

    fun launchRestore() {
        val picker = restoreFilePicker
        if (picker == null) {
            statusText = "Restore file picker is unavailable."
            return
        }
        picker { uri ->
            if (uri.isNullOrBlank()) {
                statusText = "Restore cancelled"
                return@picker
            }
            scope.launch {
                running = true
                statusText = "Restoring backup"
                runCatching { backupRestoreActions.restoreFromUri(uri) }
                    .onSuccess {
                        reloadFiltersFromDatabase()
                        statusText = it.message
                    }
                    .onFailure { statusText = "Restore failed" }
                running = false
            }
        }
    }

    fun clearRecentlyPlayedBookmarks() {
        scope.launch {
            running = true
            runCatching { browseActions.clearRecentlyPlayedBookmarks() }
                .onSuccess { statusText = "Recent cleared" }
                .onFailure { statusText = "Unable to clear recently played channels" }
            running = false
        }
    }

    val selectedPlayerChoice = remember(selectedPlayer, playerChoices) {
        (listOf(PlayerChoice(AndroidPlayerPreference.ASK_EVERY_TIME, "Ask")) + playerChoices)
            .firstOrNull { it.matchesSelected(selectedPlayer) }
            ?: PlayerChoice(selectedPlayer, selectedPlayer.playerLabel())
    }


    Column(modifier.fillMaxSize()) {
        if (activeSettingsDestination == null) {
            MobileScreenHeader("Settings", "Make UIPTV work for you")
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MobileSearchField(settingsQuery, "Search settings", { settingsQuery = it })
                SettingsWelcomeCard()
                Text("All settings", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 8.dp))
                settingsDestinations.filter { destination ->
                    settingsQuery.isBlank() || sequenceOf(destination.title, destination.description)
                        .any { it.contains(settingsQuery, ignoreCase = true) }
                }.forEach { destination ->
                    SettingsDestinationRow(destination) { settingsPage = destination.id }
                }
            }
        } else {
            MobileScreenHeader(activeSettingsDestination.title, activeSettingsDestination.description,
                onBack = { settingsPage = null })
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
        if (settingsPage == "sync") {
        SettingsPanel {
        ConfigurationIntro()
        CompactOutlinedTextField(
            value = host,
            onValueChange = ::updateHost,
            modifier = Modifier.fillMaxWidth(),
            label = "Host",
            placeholder = "Desktop IP or hostname"
        )
        CompactOutlinedTextField(
            value = portText,
            onValueChange = { portText = it.filter(Char::isDigit).take(5) },
            modifier = Modifier.fillMaxWidth(),
            label = "Port",
            placeholder = "Desktop sync port",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        SettingsSwitchRow(
            title = "Use a secure connection",
            description = "Connect to the desktop sync server over HTTPS.",
            checked = useHttps,
            enabled = !running,
            contentDescription = "Use HTTPS for desktop sync",
            onCheckedChange = { useHttps = it }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                modifier = Modifier.semantics { contentDescription = "Test desktop sync connection" },
                enabled = !running && host.isNotBlank() && portText.isNotBlank(),
                onClick = {
                    scope.launch {
                        val port = parsedPort() ?: return@launch
                        running = true
                        statusText = "Connecting"
                        runCatching {
                            syncActions.checkConnection(requestHost(), port)
                        }.onSuccess {
                            statusText = "Connected"
                        }.onFailure { ex ->
                            statusText = "Connection failed"
                        }
                        running = false
                    }
                }
            ) {
                Text("Test connection")
            }
            Button(
                modifier = Modifier.semantics { contentDescription = "Pull data from desktop" },
                enabled = !running && host.isNotBlank() && portText.isNotBlank(),
                onClick = {
                    scope.launch {
                        val port = parsedPort() ?: return@launch
                        running = true
                        statusText = "Starting"
                        verificationCode = ""
                        runCatching {
                            syncActions.pullFromDesktop(
                                requestHost(),
                                port
                            ) { progress ->
                                verificationCode = progress.verificationCode
                                statusText = progress.label()
                            }
                        }.onSuccess { result ->
                            reloadFiltersFromDatabase()
                            statusText = "Finished: ${result.report.totalRowsSynced} rows"
                            lastSyncText = "Last sync: now"
                        }.onFailure { ex ->
                            statusText = "Sync failed"
                        }
                        running = false
                    }
                }
            ) {
                Text("Sync now")
            }
        }
        if (running) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (verificationCode.isNotBlank()) {
            Text("Code $verificationCode", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Text(statusText, color = DeepNightMutedText, style = MaterialTheme.typography.bodyMedium)
        Text(lastSyncText, color = DeepNightMutedText, style = MaterialTheme.typography.bodySmall)
        }
        }
        if (settingsPage == "backup") {
        SettingsPanel {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Back up mobile data" },
                        enabled = !running,
                        onClick = ::launchBackup
                    ) {
                        Text("Back up")
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Restore mobile data" },
                        enabled = !running,
                        onClick = { confirmRestore = true }
                    ) {
                        Text("Restore")
                    }
                }
        }
        }
        if (settingsPage == "appearance") {
        SettingsPanel {
            SettingsSwitchRow(
                title = if (filters.enableThumbnails) "Thumbnail Mode is enabled" else "Plaintext mode is enabled",
                description = "Show channel and title artwork.",
                checked = filters.enableThumbnails,
                enabled = !running,
                contentDescription = if (filters.enableThumbnails) "Thumbnail Mode is enabled" else "Plaintext mode is enabled",
                onCheckedChange = { enabled ->
                    scope.launch {
                        running = true
                        runCatching { filterActions.setEnableThumbnails(enabled) }
                            .onSuccess {
                                filters = filters.copy(enableThumbnails = enabled)
                                onThumbnailSettingChanged(enabled)
                                statusText = if (enabled) "Thumbnail Mode is enabled" else "Plaintext mode is enabled"
                            }
                            .onFailure { statusText = "Unable to update thumbnails" }
                        running = false
                    }
                }
            )
        }
        }
        if (settingsPage == "playback") {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !running) { playerSelectorVisible = true }
                .semantics { contentDescription = "Change default player" },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 84.dp).padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                playerIconRenderer(selectedPlayerChoice, Modifier.size(40.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(selectedPlayer.playerLabel(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Selected default player", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        }
        if (settingsPage == "guide") {
        SettingsPanel { MobileEpgSettings() }
        }
        if (settingsPage == "history") {
        SettingsPanel {
            Text("Remove your locally stored recently played channels.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
                modifier = Modifier.semantics { contentDescription = "Clear recently played bookmark channels" },
                enabled = !running,
                onClick = ::clearRecentlyPlayedBookmarks
            ) { Text("Clear recent history") }
        }
        }
        if (settingsPage == "filters") {
        SettingsPanel {
        SettingsSwitchRow(
            title = if (filters.paused) "Filtering is paused" else "Filtering is active",
            description = if (filters.paused) "Matching categories and channels are visible." else "Matching categories and channels are hidden.",
            checked = !filters.paused,
            enabled = !running,
            contentDescription = if (filters.paused) "Resume content filters" else "Pause content filters",
            onCheckedChange = { enabled ->
                scope.launch {
                    running = true
                    runCatching { filterActions.setPaused(!enabled) }
                        .onSuccess {
                            filters = filters.copy(paused = !enabled)
                            statusText = if (!enabled) "Filtering paused" else "Filtering active"
                        }
                        .onFailure { statusText = "Unable to update filters" }
                    running = false
                }
            }
        )
        Text("${filters.categoryFilters.filterTermCount()} category terms · ${filters.channelFilters.filterTermCount()} channel terms",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        TextButton(
            modifier = Modifier.semantics { contentDescription = if (filterEditorVisible) "Hide content filters" else "View or edit content filters" },
            enabled = !running,
            onClick = {
                if (!filterEditorVisible) {
                    categoryFilterText = filters.categoryFilters
                    channelFilterText = filters.channelFilters
                }
                filterEditorVisible = !filterEditorVisible
            }
        ) { Text(if (filterEditorVisible) "Close filter editor" else "Edit filter rules") }
        if (filterEditorVisible) {
            OutlinedTextField(
                value = categoryFilterText,
                onValueChange = { categoryFilterText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Category filters") },
                placeholder = { Text("adult, xxx") },
                minLines = 3,
                colors = mobileOutlinedTextFieldColors()
            )
            OutlinedTextField(
                value = channelFilterText,
                onValueChange = { channelFilterText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Channel filters") },
                placeholder = { Text("adult, xxx") },
                minLines = 3,
                colors = mobileOutlinedTextFieldColors()
            )
            Button(
                modifier = Modifier.semantics { contentDescription = "Save content filters" },
                enabled = !running,
                onClick = {
                    scope.launch {
                        running = true
                        val updated = filters.copy(
                            categoryFilters = categoryFilterText,
                            channelFilters = channelFilterText
                        )
                        runCatching { filterActions.save(updated) }
                            .onSuccess {
                                filters = updated
                                statusText = "Filters saved"
                            }
                            .onFailure { statusText = "Unable to save filters" }
                        running = false
                    }
                }
            ) {
                Text("Save Filters")
            }
        }
        }
        }
        if (settingsPage == "about") {
            Button(
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Reset local UIPTV database" },
                enabled = !running,
                onClick = { confirmReset = true }
            ) { Text("Reset Local Data") }
        val appVersion = LocalAppVersion.current
        if (appVersion != AppVersionInfo.EMPTY) {
            Spacer(Modifier.height(8.dp))
            AboutSection(versionInfo = appVersion)
        }
        }
        }
        }
    }


    if (playerSelectorVisible) {
        PlayerSelectionSheet(
            title = "Default Player",
            choices = playerChoices,
            selectedPlayer = selectedPlayer,
            playerIconRenderer = playerIconRenderer,
            wideLayout = wideLayout,
            onDismiss = { playerSelectorVisible = false },
            onInstall = { choice ->
                playerSelectorVisible = false
                scope.launch {
                    running = true
                    runCatching { playbackActions.openPlayerInstall(choice) }
                        .onSuccess { statusText = "Opening ${choice.label} in Google Play" }
                        .onFailure { statusText = "Unable to open Google Play" }
                    running = false
                }
            },
            onSelect = { choice ->
                val targetPlayer = if (choice.matchesSelected(selectedPlayer)) {
                    AndroidPlayerPreference.ASK_EVERY_TIME
                } else {
                    choice.player
                }
                scope.launch {
                    running = true
                    runCatching { playbackActions.savePlayerPreference(targetPlayer) }
                        .onSuccess {
                            selectedPlayer = targetPlayer
                            playerText = "Player: ${targetPlayer.playerLabel()}"
                            statusText = if (targetPlayer == AndroidPlayerPreference.ASK_EVERY_TIME) {
                                "Default player cleared"
                            } else {
                                "Default player set to ${choice.label}"
                            }
                            playerSelectorVisible = false
                        }
                        .onFailure { statusText = "Unable to save player preference" }
                    running = false
                }
            }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset Local Data") },
            text = { Text("Delete all local accounts, categories, channels, bookmarks, and watch state from this Android app, then optimize the database file?") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmReset = false
                        scope.launch {
                            running = true
                            runCatching { syncActions.resetLocalData() }
                                .onSuccess {
                                    reloadFiltersFromDatabase()
                                    filterEditorVisible = false
                                    verificationCode = ""
                                    lastSyncText = "Never synced"
                                    statusText = "Local data reset and database optimized"
                                }
                                .onFailure { statusText = "Unable to reset local data" }
                            running = false
                        }
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore backup?") },
            text = {
                Text("This replaces all mobile data with the selected backup, including accounts, configuration, bookmarks, cache, and link tables.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        launchRestore()
                    }
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}


@Composable
private fun ConfigurationIntro(
    compact: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
        Text("Sync help", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "To sync this Android app, run UIPTV Desktop on the computer that has your library, start its web server, then enter that computer's IP address and server port here. Approve the request on desktop when prompted.",
            color = DeepNightMutedText,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun AboutSection(versionInfo: AppVersionInfo) {
    val uriHandler = LocalUriHandler.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = DeepNightSurfaceHigh,
        contentColor = DeepNightText
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Android,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = DeepNightAccent
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "UIPTV",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "Version ${versionInfo.versionName} · Build ${versionInfo.buildNumber}",
                color = DeepNightMutedText,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "GitHub",
                color = DeepNightAccent,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://github.com/xixogo5105/uiptv")
                }
            )
        }
    }
}

@Composable
private fun SettingsPanel(content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    contentDescription: String,
    onCheckedChange: (Boolean) -> Unit
) {
    MobilePreferenceSwitchRow(
        title = title,
        description = description,
        checked = checked,
        enabled = enabled,
        accessibilityLabel = contentDescription,
        onCheckedChange = onCheckedChange
    )
}

@Composable
private fun SettingsWelcomeCard() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Make UIPTV yours", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        Text("Manage syncing, playback, artwork and your library.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsDestinationRow(destination: MobileSettingsDestination, onClick: () -> Unit) {
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 84.dp).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(destination.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(destination.title, style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Text(destination.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AccountsScreen(
    accountActions: AccountUiActions,
    timeZoneOptions: List<String>,
    backHandler: @Composable (Boolean, () -> Unit) -> Unit,
    onOpenAccountChannels: (MobileAccount) -> Unit,
    localPlaylistPicker: LocalPlaylistPicker?,
    wideLayout: Boolean,
    wideSearchVisible: Boolean,
    refreshSignal: Int,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf<List<MobileAccount>>(emptyList()) }
    var accountCacheSummaries by remember { mutableStateOf<Map<Long, AccountCacheSummary>>(emptyMap()) }
    var cacheSummaryRevision by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(MobileAccount()) }
    var selectedType by remember { mutableStateOf(MobileAccountType.STALKER_PORTAL) }
    var statusText by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var cacheDialogJob by remember { mutableStateOf<CacheRefreshJobState?>(null) }
    var accountFeedback by remember { mutableStateOf<AccountFeedback?>(null) }
    var pendingDelete by remember { mutableStateOf<MobileAccount?>(null) }
    var pendingDeleteFailedAccounts by remember { mutableStateOf<List<FailedRefreshAccount>>(emptyList()) }
    var editorVisible by remember { mutableStateOf(false) }
    var accountFilter by rememberSaveable { mutableStateOf(AccountFilter.ALL) }
    var accountSearchVisible by rememberSaveable { mutableStateOf(false) }
    var accountSearchQuery by rememberSaveable { mutableStateOf("") }
    var actionsMenuExpanded by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            running = true
            runCatching { accountActions.loadAccounts() }
                .onSuccess { accounts = it }
                .onFailure { statusText = "Unable to load accounts" }
            running = false
        }
    }

    fun enqueueCacheJob(request: CacheRefreshJobRequest, message: String) {
        scope.launch {
            running = true
            runCatching { accountActions.enqueueCacheJob(request) }
                .onSuccess { jobId ->
                    statusText = message
                    cacheDialogJob = CacheRefreshJobState(
                        jobId = jobId,
                        action = request.action,
                        status = CacheRefreshJobStatus.QUEUED,
                        message = message,
                        accountId = request.accountId
                    )
                }
                .onFailure {
                    val messageText = "Unable to queue cache job"
                    statusText = messageText
                    accountFeedback = AccountFeedback("Cache Refresh", messageText, false)
                }
            running = false
        }
    }

    LaunchedEffect(accountActions, refreshSignal) {
        runCatching { accountActions.loadAccounts() }
            .onSuccess { accounts = it; cacheSummaryRevision++ }
            .onFailure { statusText = "Unable to load accounts" }
    }

    LaunchedEffect(accountActions, accounts, cacheSummaryRevision) {
        val summaries = mutableMapOf<Long, AccountCacheSummary>()
        accounts.mapNotNull { it.id }.distinct().forEach { accountId ->
            runCatching { accountActions.loadCacheSummary(accountId) }
                .onSuccess { summaries[accountId] = it }
        }
        accountCacheSummaries = summaries
    }

    val activeCacheJobId = cacheDialogJob?.jobId
    LaunchedEffect(activeCacheJobId) {
        if (activeCacheJobId == null) {
            return@LaunchedEffect
        }
        while (true) {
            val latest = runCatching { accountActions.loadCacheJobState(activeCacheJobId) }.getOrNull()
            if (latest != null) {
                cacheDialogJob = latest
                statusText = latest.message.ifBlank { latest.status.label() }
                if (!latest.isActive()) {
                    if (latest.status == CacheRefreshJobStatus.SUCCEEDED) {
                        runCatching { accountActions.loadAccounts() }
                            .onSuccess { accounts = it; cacheSummaryRevision++ }
                            .onFailure { statusText = "Unable to load accounts" }
                    }
                    break
                }
            }
            delay(1_000)
        }
    }

    backHandler(cacheDialogJob?.action == CacheRefreshAction.REFRESH_ALL) { cacheDialogJob = null }
    val refreshAllJob = cacheDialogJob?.takeIf { it.action == CacheRefreshAction.REFRESH_ALL }
    if (refreshAllJob != null) {
        val failedRefreshAccounts = failedRefreshAccounts(accounts, refreshAllJob.failedAccountIds)
        CacheRefreshProgressScreen(
            job = refreshAllJob,
            failedAccounts = failedRefreshAccounts,
            modifier = modifier,
            onDismiss = { cacheDialogJob = null },
            onStop = {
                scope.launch {
                    runCatching { accountActions.stopCacheJob(refreshAllJob.jobId) }
                        .onSuccess {
                            cacheDialogJob = refreshAllJob.copy(status = CacheRefreshJobStatus.SKIPPED, message = "Stop requested")
                            statusText = "Stop requested"
                        }
                        .onFailure {
                            val message = "Unable to stop cache job"
                            statusText = message
                            accountFeedback = AccountFeedback("Stop Failed", message, false)
                        }
                }
            },
            onDeleteFailedAccounts = { selectedIds ->
                pendingDeleteFailedAccounts = failedRefreshAccounts.filter { it.id in selectedIds }
            }
        )
    } else {
        val effectiveAccountSearchVisible = accountSearchVisible
        val visibleAccounts = accounts
            .filter(accountFilter::matches)
            .filter { account ->
                val query = if (effectiveAccountSearchVisible) accountSearchQuery.trim() else ""
                query.isBlank() ||
                    account.accountName.contains(query, ignoreCase = true) ||
                    account.url.contains(query, ignoreCase = true) ||
                    account.username.contains(query, ignoreCase = true) ||
                    account.macAddress.contains(query, ignoreCase = true)
            }

        val accountListState = rememberLazyListState()
        Column(modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            MobileScreenHeader(
                MobileMessages.text("mobileAccountsTitle"),
                MobileMessages.text("mobileAccountsSubtitle"),
                searchVisible = accountSearchVisible,
                searchQuery = accountSearchQuery,
                searchLabel = MobileMessages.text("commonSearch"),
                onSearchToggle = { accountSearchVisible = !accountSearchVisible },
                onSearchQueryChange = { accountSearchQuery = it },
                addMenuActions = listOf(
                    MobileHeaderAddAction(
                        MobileMessages.text("mobileAccountTypeStalkerPortal"),
                        MobileMessages.text("mobileAccountStalkerDescription"),
                        Icons.Outlined.LiveTv
                    ) {
                        editing = MobileAccount()
                        selectedType = MobileAccountType.STALKER_PORTAL
                        editorVisible = true
                    },
                    MobileHeaderAddAction(
                        MobileMessages.text("mobileAccountTypeXtremeApi"),
                        MobileMessages.text("mobileAccountXtremeDescription"),
                        Icons.Outlined.PlayCircle
                    ) {
                        editing = MobileAccount()
                        selectedType = MobileAccountType.XTREME_API
                        editorVisible = true
                    },
                    MobileHeaderAddAction(
                        MobileMessages.text("mobileAccountTypeM3uPlaylist"),
                        MobileMessages.text("mobileAccountM3uDescription"),
                        Icons.AutoMirrored.Outlined.PlaylistPlay
                    ) {
                        editing = MobileAccount()
                        selectedType = MobileAccountType.M3U8_URL
                        editorVisible = true
                    }
                )
            )
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(AccountFilter.entries, key = { it.name }) { filter ->
                            FilterChip(
                                selected = accountFilter == filter,
                                onClick = { accountFilter = filter },
                                leadingIcon = if (filter == AccountFilter.PINNED) {
                                    {
                                        Icon(
                                            Icons.Outlined.PushPin,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else {
                                    null
                                },
                                label = { Text(filter.label) }
                            )
                        }
                    }
                    Box {
                        IconButton(
                            modifier = Modifier.semantics { contentDescription = "Account page actions" },
                            enabled = !running,
                            onClick = { actionsMenuExpanded = true }
                        ) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = actionsMenuExpanded,
                            onDismissRequest = { actionsMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                                },
                                text = { Text("Clear all cache") },
                                onClick = {
                                    actionsMenuExpanded = false
                                    enqueueCacheJob(
                                        CacheRefreshJobRequest(CacheRefreshAction.CLEAR_ALL_CACHE),
                                        "Queued clear all cache"
                                    )
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Outlined.Sync, contentDescription = null)
                                },
                                text = { Text("Refresh all account catalogues") },
                                onClick = {
                                    actionsMenuExpanded = false
                                    enqueueCacheJob(
                                        CacheRefreshJobRequest(CacheRefreshAction.REFRESH_ALL),
                                        "Refreshing all account catalogues"
                                    )
                                }
                            )
                        }
                    }
                }
            LazyColumn(
                state = accountListState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
            if (visibleAccounts.isEmpty()) {
                val searchingAccounts = accountSearchVisible && accountSearchQuery.isNotBlank()
                item {
                    EmptyState(
                        title = when {
                            accounts.isEmpty() -> "No accounts"
                            searchingAccounts -> "No account matches"
                            else -> "No ${accountFilter.emptyLabel} accounts"
                        },
                        detail = if (accounts.isEmpty()) {
                            "Create an account here or pull accounts from desktop sync."
                        } else if (searchingAccounts) {
                            "Try a shorter search term."
                        } else {
                            "Change the account filter or edit account details."
                        }
                    )
                }
            }
            items(visibleAccounts, key = { it.id ?: it.accountName }) { account ->
                AccountRow(
                    account = account,
                    cacheSummary = account.id?.let(accountCacheSummaries::get),
                    onOpen = {
                        if (account.id != null) {
                            onOpenAccountChannels(account)
                        }
                    },
                    onEdit = {
                        editing = account
                        selectedType = account.type
                        statusText = "Editing ${account.accountName}"
                        editorVisible = true
                    },
                    onClearCache = {
                        val accountId = account.id ?: return@AccountRow
                        enqueueCacheJob(
                            CacheRefreshJobRequest(CacheRefreshAction.CLEAR_ACCOUNT_CACHE, accountId),
                            "Queued clear ${account.accountName}"
                        )
                    },
                    onRefreshCache = {
                        val accountId = account.id ?: return@AccountRow
                        enqueueCacheJob(
                            CacheRefreshJobRequest(CacheRefreshAction.REFRESH_ACCOUNT, accountId),
                            "Queued ${account.accountName}"
                        )
                    },
                    onDelete = {
                        pendingDelete = account.takeIf { it.id != null }
                    }
                )
            }
            item {
                if (running) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(statusText, color = DeepNightMutedText, style = MaterialTheme.typography.bodySmall)
            }
            }
        }

    }

    if (editorVisible) {
        AccountEditorDialog(
            account = editing.copy(type = selectedType),
            selectedType = selectedType,
            timeZoneOptions = timeZoneOptions,
            onDismiss = { editorVisible = false },
            onTypeChange = {
                selectedType = it
                editing = editing.copy(type = it)
            },
            onAccountChange = { editing = it },
            onBrowseLocalPlaylist = localPlaylistPicker,
            onSave = {
                scope.launch {
                    running = true
                    runCatching { accountActions.saveAccount(editing.copy(type = selectedType)) }
                        .onSuccess {
                            statusText = "Saved ${it.accountName}"
                            editing = it
                            editorVisible = false
                            runCatching { accountActions.loadAccounts() }
                                .onSuccess { loaded -> accounts = loaded }
                                .onFailure { error -> statusText = "Unable to load accounts" }
                        }
                        .onFailure {
                            val message = "Unable to save account"
                            statusText = message
                            accountFeedback = AccountFeedback("Account Save Failed", message, false)
                        }
                    running = false
                }
            },
            onDelete = {
                pendingDelete = editing.takeIf { it.id != null }
                editorVisible = false
            }
        )
    }

    pendingDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Account") },
            text = {
                Text("Delete ${account.accountName}? Local cache, bookmarks for this account, and watch state will be removed.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val accountId = account.id ?: return@Button
                        pendingDelete = null
                        scope.launch {
                            running = true
                            runCatching { accountActions.deleteAccount(accountId) }
                                .onSuccess {
                                    statusText = "Deleted account"
                                    accountFeedback = AccountFeedback("Account Deleted", "${account.accountName} was deleted.", true)
                                    editing = MobileAccount()
                                    selectedType = MobileAccountType.STALKER_PORTAL
                                    runCatching { accountActions.loadAccounts() }
                                        .onSuccess { loaded -> accounts = loaded; cacheSummaryRevision++ }
                                        .onFailure { error -> statusText = "Unable to load accounts" }
                                }
                                .onFailure {
                                    val message = "Unable to delete account"
                                    statusText = message
                                    accountFeedback = AccountFeedback("Delete Failed", message, false)
                                }
                            running = false
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (pendingDeleteFailedAccounts.isNotEmpty()) {
        val accountsToDelete = pendingDeleteFailedAccounts
        AlertDialog(
            onDismissRequest = { pendingDeleteFailedAccounts = emptyList() },
            title = { Text("Delete Failed Accounts") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Delete ${accountsToDelete.size} selected failed account${if (accountsToDelete.size == 1) "" else "s"}? Local cache, bookmarks, and watch state for the selected account${if (accountsToDelete.size == 1) "" else "s"} will be removed."
                    )
                    accountsToDelete.forEach { account ->
                        Text(
                            account.name,
                            color = DeepNightText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ids = accountsToDelete.map { it.id }.toSet()
                        pendingDeleteFailedAccounts = emptyList()
                        scope.launch {
                            running = true
                            runCatching {
                                ids.forEach { accountActions.deleteAccount(it) }
                            }.onSuccess {
                                statusText = "Deleted ${ids.size} failed account${if (ids.size == 1) "" else "s"}"
                                accountFeedback = AccountFeedback(
                                    "Failed Accounts Deleted",
                                    "Deleted ${ids.size} selected failed account${if (ids.size == 1) "" else "s"}.",
                                    true
                                )
                                cacheDialogJob = cacheDialogJob?.copy(
                                    failedAccountIds = cacheDialogJob?.failedAccountIds.orEmpty().filterNot { it in ids }
                                )
                            }.onFailure {
                                val message = "Unable to delete failed accounts"
                                statusText = message
                                accountFeedback = AccountFeedback("Delete Failed", message, false)
                            }
                            accounts = runCatching { accountActions.loadAccounts() }.getOrDefault(accounts)
                            cacheSummaryRevision++
                            running = false
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteFailedAccounts = emptyList() }) {
                    Text("Cancel")
                }
            }
        )
    }

    cacheDialogJob?.takeIf { it.action != CacheRefreshAction.REFRESH_ALL }?.let { job ->
        CacheRefreshDialog(
            job = job,
            onDismiss = { cacheDialogJob = null },
            onStop = {
                scope.launch {
                    runCatching { accountActions.stopCacheJob(job.jobId) }
                        .onSuccess {
                            cacheDialogJob = job.copy(status = CacheRefreshJobStatus.SKIPPED, message = "Stop requested")
                            statusText = "Stop requested"
                        }
                        .onFailure {
                            val message = "Unable to stop cache job"
                            statusText = message
                            accountFeedback = AccountFeedback("Stop Failed", message, false)
                        }
                }
            }
        )
    }

    accountFeedback?.let { feedback ->
        AccountFeedbackDialog(feedback = feedback, onDismiss = { accountFeedback = null })
    }
}


@Composable
private fun AccountRow(
    account: MobileAccount,
    cacheSummary: AccountCacheSummary?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onClearCache: () -> Unit,
    onRefreshCache: () -> Unit,
    onDelete: () -> Unit,
    dense: Boolean = false
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp
    ) {
      Row(
        modifier = Modifier.fillMaxWidth()
            .defaultMinSize(minHeight = if (dense) 68.dp else 76.dp)
            .padding(
                start = if (dense) 12.dp else 16.dp,
                end = if (dense) 4.dp else 8.dp,
                top = if (dense) 8.dp else 12.dp,
                bottom = if (dense) 8.dp else 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (account.pinToTop) {
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = "Pinned account",
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 4.dp),
                        tint = DeepNightAccent
                    )
                }
                Text(
                    account.accountName,
                    modifier = Modifier.weight(1f),
                    color = DeepNightText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (dense) 3 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(account.type.displayName, color = DeepNightMutedText, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            cacheSummary?.let { summary ->
                Text(
                    summary.accountRowLabel(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box {
            IconButton(
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Account actions for ${account.accountName}" },
                onClick = { menuExpanded = true }
            ) {
                Icon(Icons.Outlined.MoreVert, contentDescription = null, tint = DeepNightMutedText)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Open") },
                    onClick = {
                        menuExpanded = false
                        onOpen()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Clear cache") },
                    enabled = account.canRefreshCache,
                    onClick = {
                        menuExpanded = false
                        onClearCache()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Refresh cache") },
                    enabled = account.canRefreshCache,
                    onClick = {
                        menuExpanded = false
                        onRefreshCache()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    enabled = account.id != null,
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
      }
    }
    }
}

internal fun AccountCacheSummary.accountRowLabel(): String {
    val content = buildList {
        if (liveCategories > 0 || liveChannels > 0) {
            add(listOf(
                mobileCountPhrase(liveCategories, "mobileCountLiveCategorySingular", "mobileCountLiveCategoryPlural"),
                mobileCountPhrase(liveChannels, "mobileCountChannelSingular", "mobileCountChannelPlural")
            ).joinToString(" · "))
        }
        if (vodCategories > 0 || vodChannels > 0) {
            add(listOf(
                mobileCountPhrase(vodCategories, "mobileCountMovieCategorySingular", "mobileCountMovieCategoryPlural"),
                mobileCountPhrase(vodChannels, "mobileCountMovieSingular", "mobileCountMoviePlural")
            ).joinToString(" · "))
        }
        if (seriesCategories > 0 || seriesChannels > 0 || seriesEpisodes > 0) {
            add(listOf(
                mobileCountPhrase(seriesCategories, "mobileCountSeriesCategorySingular", "mobileCountSeriesCategoryPlural"),
                mobileCountPhrase(seriesChannels, "mobileCountSeriesSingular", "mobileCountSeriesPlural"),
                mobileCountPhrase(seriesEpisodes, "mobileCountEpisodeSingular", "mobileCountEpisodePlural")
            ).joinToString(" · "))
        }
    }
    return content.takeIf { it.isNotEmpty() }?.joinToString("  •  ")
        ?: MobileMessages.text("mobileAccountNoCatalogue")
}

private fun mobileCountPhrase(count: Int, singularKey: String, pluralKey: String): String =
    MobileMessages.text(if (count == 1) singularKey else pluralKey, count)

@Composable
private fun CacheRefreshDialog(
    job: CacheRefreshJobState,
    onDismiss: () -> Unit,
    onStop: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(job.action.label()) },
        text = {
            val summaryLines = remember(job.message) { cacheSummaryLines(job.message) }
            val maxSummaryHeight = if (LocalWidePhoneLayout.current) 220.dp else 360.dp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxSummaryHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "${job.status.label()} ${job.progressPercent.coerceIn(0, 100)}%",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (job.isActive()) {
                    LinearProgressIndicator(
                        progress = { job.progressPercent.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    cacheSummaryHeader(job.message).ifBlank { "Waiting for cache refresh status." },
                    color = DeepNightMutedText,
                    style = MaterialTheme.typography.bodyMedium
                )
                summaryLines.forEach { line ->
                    Text(line, color = DeepNightText, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (job.isActive()) "Hide" else "Done")
            }
        },
        dismissButton = {
            if (job.isActive()) {
                TextButton(
                    modifier = Modifier.semantics { contentDescription = "Stop cache job ${job.jobId.shortJobId()}" },
                    onClick = onStop
                ) {
                    Text("Stop")
                }
            }
        }
    )
}

@Composable
private fun CacheRefreshProgressScreen(
    job: CacheRefreshJobState,
    failedAccounts: List<FailedRefreshAccount>,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onStop: () -> Unit,
    onDeleteFailedAccounts: (Set<Long>) -> Unit
) {
    val progress = job.progressPercent.coerceIn(0, 100)
    val lines = remember(job.message) { cacheProgressLines(job.message) }
    val failedAccountIds = remember(failedAccounts) { failedAccounts.map { it.id }.toSet() }
    var selectedFailedAccountIds by remember(job.jobId) { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(failedAccountIds) {
        selectedFailedAccountIds = selectedFailedAccountIds.intersect(failedAccountIds)
    }
    val canDeleteFailedAccounts = !job.isActive() && failedAccounts.isNotEmpty()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Refresh All", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${job.status.label()} $progress%",
                    color = DeepNightMutedText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            TextButton(onClick = onDismiss) {
                Text(if (job.isActive()) "Hide" else "Done")
            }
            if (job.isActive()) {
                TextButton(
                    modifier = Modifier.semantics { contentDescription = "Stop cache job ${job.jobId.shortJobId()}" },
                    onClick = onStop
                ) {
                    Text("Stop")
                }
            }
        }
        LinearProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.fillMaxWidth()
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            color = DeepNightSurface,
            shape = RoundedCornerShape(8.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(lines, key = { it }) { line ->
                    Text(
                        line,
                        color = if (line.startsWith("Accounts failed:") && !line.endsWith("None")) {
                            Color(0xFFFFB4AB)
                        } else {
                            DeepNightText
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (canDeleteFailedAccounts) {
                    item(key = "failed-account-delete-header") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Failed accounts",
                                color = DeepNightText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Select the failed accounts you want to delete.",
                                color = DeepNightMutedText,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    items(failedAccounts, key = { "failed-account-${it.id}" }) { account ->
                        val selected = account.id in selectedFailedAccountIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    selectedFailedAccountIds = if (selected) {
                                        selectedFailedAccountIds - account.id
                                    } else {
                                        selectedFailedAccountIds + account.id
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MobileM3Switch(
                                checked = selected,
                                onCheckedChange = { checked ->
                                    selectedFailedAccountIds = if (checked) {
                                        selectedFailedAccountIds + account.id
                                    } else {
                                        selectedFailedAccountIds - account.id
                                    }
                                }
                            )
                            Text(
                                account.name,
                                modifier = Modifier.weight(1f),
                                color = DeepNightText,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        if (canDeleteFailedAccounts) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${selectedFailedAccountIds.size} selected",
                    modifier = Modifier.weight(1f),
                    color = DeepNightMutedText,
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(
                    enabled = selectedFailedAccountIds.size < failedAccounts.size,
                    onClick = { selectedFailedAccountIds = failedAccountIds }
                ) {
                    Text("Select all")
                }
                Button(
                    enabled = selectedFailedAccountIds.isNotEmpty(),
                    onClick = { onDeleteFailedAccounts(selectedFailedAccountIds) }
                ) {
                    Text("Delete selected")
                }
            }
        }
    }
}

@Composable
private fun AccountFeedbackDialog(feedback: AccountFeedback, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(feedback.title) },
        text = {
            Text(
                feedback.message,
                color = if (feedback.success) DeepNightMutedText else Color(0xFFFFB4AB)
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
private fun AccountEditorDialog(
    account: MobileAccount,
    selectedType: MobileAccountType,
    timeZoneOptions: List<String>,
    onDismiss: () -> Unit,
    onTypeChange: (MobileAccountType) -> Unit,
    onAccountChange: (MobileAccount) -> Unit,
    onBrowseLocalPlaylist: LocalPlaylistPicker?,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
            contentAlignment = Alignment.Center
        ) {
            val popup = maxWidth >= 840.dp
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (popup) Modifier.widthIn(max = 760.dp).heightIn(max = maxHeight) else Modifier.fillMaxHeight()),
                shape = if (popup) MaterialTheme.shapes.extraLarge else androidx.compose.ui.graphics.RectangleShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = if (popup) 6.dp else 0.dp
            ) {
                Column(Modifier.fillMaxSize()) {
                    MobileScreenHeader(if (account.id == null) "Add account" else "Edit account", onBack = onDismiss)
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                        AccountEditor(
                            account = account,
                            selectedType = selectedType,
                            timeZoneOptions = timeZoneOptions,
                            onTypeChange = onTypeChange,
                            onAccountChange = onAccountChange,
                            onBrowseLocalPlaylist = onBrowseLocalPlaylist,
                            onSave = onSave,
                            onDelete = onDelete
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (account.id != null) TextButton(onClick = onDelete) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = onSave, enabled = account.canSaveForType(selectedType)) { Text("Save account") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountEditor(
    account: MobileAccount,
    selectedType: MobileAccountType,
    timeZoneOptions: List<String>,
    onTypeChange: (MobileAccountType) -> Unit,
    onAccountChange: (MobileAccount) -> Unit,
    onBrowseLocalPlaylist: LocalPlaylistPicker?,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    var macManagerVisible by remember(account.id) { mutableStateOf(false) }
    val isNewAccount = account.id == null
    var stalkerAdvancedVisible by remember(account.id, selectedType) {
        mutableStateOf(account.hasAdvancedStalkerFields())
    }
    val macOptions = remember(account.macAddress, account.macAddressList) {
        account.stalkerMacOptions()
    }
    val xtremeCredentialOptions = remember(account.xtremeCredentialsJson, account.username, account.password) {
        account.xtremeCredentialOptions()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            accountEditorHelpText(selectedType, isNewAccount),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        AccountTextField("Account name", account.accountName) { onAccountChange(account.copy(accountName = it)) }
        if (account.accountName.isBlank()) {
            Text(
                "Choose a name to recognise this source.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AccountTypeDropdown(selectedType, onTypeChange)
        when (selectedType) {
            MobileAccountType.STALKER_PORTAL -> {
                AccountTextField("Portal URL", account.url) { onAccountChange(account.copy(url = it)) }
                if (isNewAccount) {
                    AccountMultilineTextField(
                        label = "MAC addresses",
                        value = account.macAddressList.ifBlank { account.macAddress },
                        onValueChange = { onAccountChange(account.withStalkerMacInput(it)) },
                        placeholder = "00:1A:79:00:00:01, 00:1A:79:00:00:02"
                    )
                    Text(
                        "Add one or more MAC addresses separated by comma or semicolon. The first valid entry becomes the default.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    StalkerMacSelector(
                        selectedMac = account.macAddress,
                        macOptions = macOptions,
                        onMacSelected = { onAccountChange(account.withStalkerMacs(macOptions, it)) },
                        onManage = { macManagerVisible = true }
                    )
                }
                TextButton(onClick = { stalkerAdvancedVisible = !stalkerAdvancedVisible }) {
                    Text(if (stalkerAdvancedVisible) "Hide advanced options" else "Advanced options")
                }
                if (stalkerAdvancedVisible) {
                    AccountTextField("Username (optional)", account.username) { onAccountChange(account.copy(username = it)) }
                    AccountTextField("Password (optional)", account.password) { onAccountChange(account.copy(password = it)) }
                    AccountTextField("Serial", account.serialNumber) { onAccountChange(account.copy(serialNumber = it)) }
                    AccountTextField("Device ID 1", account.deviceId1) { onAccountChange(account.copy(deviceId1 = it)) }
                    AccountTextField("Device ID 2", account.deviceId2) { onAccountChange(account.copy(deviceId2 = it)) }
                    AccountTextField("Signature", account.signature) { onAccountChange(account.copy(signature = it)) }
                    AccountTextField("HTTP Method", account.httpMethod) { onAccountChange(account.copy(httpMethod = it)) }
                }
            }
            MobileAccountType.XTREME_API -> {
                AccountTextField("Server URL", account.url) { onAccountChange(account.copy(url = it)) }
                if (!isNewAccount && xtremeCredentialOptions.size > 1) {
                    XtremeCredentialSelector(
                        selectedUsername = account.username,
                        selectedPassword = account.password,
                        credentials = xtremeCredentialOptions,
                        onCredentialSelected = { onAccountChange(account.withXtremeCredentialSelection(it)) }
                    )
                    AccountTextField("Password", account.password) {}
                    Text(
                        "Select the username/password pair to use for cache refresh and playback.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    AccountTextField("Username", account.username) { onAccountChange(account.copy(username = it)) }
                    AccountTextField("Password", account.password) { onAccountChange(account.copy(password = it)) }
                }
            }
            MobileAccountType.M3U8_URL,
            MobileAccountType.M3U8_LOCAL -> {
                AccountTextField("Playlist URL", account.m3uPlaylistUrlInput()) {
                    onAccountChange(account.copy(url = it))
                }
                Text(
                    "Or choose a local M3U file. If both fields are filled, the URL is used.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                CompactOutlinedTextField(
                    value = account.m3uPlaylistFileInput(),
                    onValueChange = { onAccountChange(account.copy(m3u8Path = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Playlist file"
                )
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Browse for local M3U playlist file" },
                    enabled = onBrowseLocalPlaylist != null,
                    onClick = {
                        onBrowseLocalPlaylist?.invoke { selectedUri ->
                            onAccountChange(account.copy(m3u8Path = selectedUri))
                        }
                    }
                ) {
                    Text("Browse")
                }
                AccountTextField("EPG URL (optional)", account.epg) { onAccountChange(account.copy(epg = it)) }
            }
        }
        if (selectedType == MobileAccountType.STALKER_PORTAL) {
            MobileAccountChoiceDropdown(
                value = account.timezone.ifBlank { "Europe/London" },
                label = "Timezone",
                accessibilityLabel = "Select account timezone",
                options = (timeZoneOptions + account.timezone)
                    .filter(String::isNotBlank)
                    .distinct()
                    .sorted(),
                searchable = true,
                onSelected = { onAccountChange(account.copy(timezone = it)) }
            )
        }
        MobilePreferenceSwitchRow(
            title = "Pin to top",
            description = "Keep this account at the top of the account list.",
            accessibilityLabel = "Pin account to top",
            checked = account.pinToTop,
            onCheckedChange = { onAccountChange(account.copy(pinToTop = it)) }
        )
        MobilePreferenceSwitchRow(
            title = "Resolve redirects",
            description = "Follow chained stream URLs when loading content.",
            accessibilityLabel = "Resolve redirects for account",
            checked = account.resolveChainAndDeepRedirects,
            onCheckedChange = { onAccountChange(account.copy(resolveChainAndDeepRedirects = it)) }
        )

    }
    if (macManagerVisible) {
        MacAddressManagerDialog(
            account = account,
            macOptions = macOptions,
            onAccountChange = onAccountChange,
            onDismiss = { macManagerVisible = false }
        )
    }
}

@Composable
private fun AccountTypeDropdown(
    selectedType: MobileAccountType,
    onTypeChange: (MobileAccountType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = selectedType.choiceLabel()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(MobileMessages.text("mobileAccountTypeLabel")) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                .semantics { contentDescription = MobileMessages.text("mobileAccountTypeLabel") },
            colors = mobileOutlinedTextFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            mobileAccountTypeChoices.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.choiceLabel()) },
                    onClick = {
                        expanded = false
                        onTypeChange(type)
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun StalkerMacSelector(
    selectedMac: String,
    macOptions: List<String>,
    onMacSelected: (String) -> Unit,
    onManage: () -> Unit
) {
    val selected = macOptions.firstOrNull { it.equals(selectedMac, ignoreCase = true) }
        ?: selectedMac.normalizedMacEntry()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MobileAccountChoiceDropdown(
            value = selected.ifBlank { MobileMessages.text("mobileAccountNoMacAddresses") },
            label = MobileMessages.text("mobileAccountMacAddress"),
            accessibilityLabel = MobileMessages.text("mobileAccountSelectMacAddress"),
            options = macOptions,
            onSelected = onMacSelected
        )
        TextButton(
            modifier = Modifier.align(Alignment.End),
            onClick = onManage
        ) {
            Text(MobileMessages.text("autoManageMACAddresses"))
        }
    }
}

@Composable
private fun XtremeCredentialSelector(
    selectedUsername: String,
    selectedPassword: String,
    credentials: List<XtremeCredential>,
    onCredentialSelected: (XtremeCredential) -> Unit
) {
    val selected = credentials.firstOrNull {
        it.username == selectedUsername && it.password == selectedPassword
    } ?: credentials.firstOrNull { it.username == selectedUsername }
        ?: credentials.firstOrNull { it.isDefault }
        ?: credentials.firstOrNull()
    MobileAccountChoiceDropdown(
        value = selected?.displayLabel(credentials).orEmpty()
            .ifBlank { MobileMessages.text("mobileAccountNoCredentials") },
        label = MobileMessages.text("mobileAccountCredentialsLabel"),
        accessibilityLabel = MobileMessages.text("mobileAccountSelectCredentials"),
        options = credentials.map { it.displayLabel(credentials) },
        onSelected = { label ->
            credentials.firstOrNull { it.displayLabel(credentials) == label }?.let(onCredentialSelected)
        }
    )
}

@Composable
private fun MobileAccountChoiceDropdown(
    value: String,
    label: String,
    accessibilityLabel: String,
    options: List<String>,
    searchable: Boolean = false,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember(value) { mutableStateOf(value) }
    val enabled = options.isNotEmpty()
    val visibleOptions = if (searchable && expanded && query.isNotBlank()) {
        options.filter { it.contains(query, ignoreCase = true) }
    } else {
        options
    }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = {
            if (enabled) {
                expanded = it
                query = if (it && searchable) "" else value
            }
        }
    ) {
        OutlinedTextField(
            value = if (searchable && expanded) query else value,
            onValueChange = { typed ->
                if (searchable && enabled) {
                    query = typed
                    expanded = true
                }
            },
            readOnly = !searchable,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(
                    if (searchable) ExposedDropdownMenuAnchorType.PrimaryEditable
                    else ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = enabled
                )
                .semantics { contentDescription = accessibilityLabel },
            colors = mobileOutlinedTextFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            if (visibleOptions.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(MobileMessages.text("mobileAccountNoMatchingTimezones")) },
                    enabled = false,
                    onClick = {}
                )
            }
            visibleOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        expanded = false
                        query = option
                        onSelected(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun MacAddressManagerDialog(
    account: MobileAccount,
    macOptions: List<String>,
    onAccountChange: (MobileAccount) -> Unit,
    onDismiss: () -> Unit
) {
    var entry by rememberSaveable(account.id) { mutableStateOf("") }
    var editingMac by rememberSaveable(account.id) { mutableStateOf<String?>(null) }
    fun applyMacs(nextMacs: List<String>, selectedMac: String) {
        onAccountChange(account.withStalkerMacs(nextMacs, selectedMac))
    }
    fun clearEditor() {
        entry = ""
        editingMac = null
    }
    fun commitEntry() {
        val normalizedEntry = entry.normalizedMacEntry()
        if (normalizedEntry.isBlank()) {
            return
        }
        val editing = editingMac
        val retainedMacs = if (editing == null) {
            macOptions
        } else {
            macOptions.filterNot { it.equals(editing, ignoreCase = true) }
        }
        val selectedMac = if (
            account.macAddress.isBlank() ||
            editing != null && account.macAddress.equals(editing, ignoreCase = true)
        ) {
            normalizedEntry
        } else {
            account.macAddress
        }
        applyMacs(retainedMacs + normalizedEntry, selectedMac)
        clearEditor()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage MAC Addresses") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactOutlinedTextField(
                        value = entry,
                        onValueChange = { entry = it },
                        modifier = Modifier.weight(1f),
                        label = if (editingMac == null) "MAC address" else "Edit MAC"
                    )
                    Button(
                        enabled = entry.normalizedMacEntry().isNotBlank(),
                        onClick = { commitEntry() }
                    ) {
                        Text(if (editingMac == null) "Add" else "Save")
                    }
                }
                if (editingMac != null) {
                    TextButton(onClick = { clearEditor() }) {
                        Text("Cancel edit")
                    }
                }
                if (macOptions.isEmpty()) {
                    Text(
                        "No MAC addresses",
                        color = DeepNightMutedText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                macOptions.forEach { mac ->
                    MacAddressManagerRow(
                        mac = mac,
                        selected = mac.equals(account.macAddress, ignoreCase = true),
                        onSelect = { applyMacs(macOptions, mac) },
                        onEdit = {
                            editingMac = mac
                            entry = mac
                        },
                        onDelete = {
                            val nextMacs = macOptions.filterNot { it.equals(mac, ignoreCase = true) }
                            val nextSelected = if (account.macAddress.equals(mac, ignoreCase = true)) {
                                nextMacs.firstOrNull().orEmpty()
                            } else {
                                account.macAddress
                            }
                            applyMacs(nextMacs, nextSelected)
                            if (editingMac?.equals(mac, ignoreCase = true) == true) {
                                clearEditor()
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun MacAddressManagerRow(
    mac: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) DeepNightPrimary.copy(alpha = 0.18f) else DeepNightSurfaceHighest,
        contentColor = DeepNightText
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    mac,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (selected) {
                    Text(
                        "Selected",
                        color = DeepNightAccent,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !selected, onClick = onSelect) {
                    Text("Use")
                }
                TextButton(onClick = onEdit) {
                    Text("Edit")
                }
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
        }
    }
}

private val mobileAccountTypeChoices = listOf(
    MobileAccountType.STALKER_PORTAL,
    MobileAccountType.XTREME_API,
    MobileAccountType.M3U8_URL
)

private fun accountEditorHelpText(type: MobileAccountType, isNewAccount: Boolean): String =
    when (type) {
        MobileAccountType.STALKER_PORTAL -> if (isNewAccount) {
            "Enter the portal URL and paste all MAC addresses in one field. You can fine-tune MACs later from Edit."
        } else {
            "Update portal details and manage the saved MAC addresses for this account."
        }
        MobileAccountType.XTREME_API -> "Enter the server URL plus username and password from your provider."
        MobileAccountType.M3U8_URL,
        MobileAccountType.M3U8_LOCAL -> "Paste a playlist URL or choose a local M3U file. If both are filled, the URL is used."
    }

private fun MobileAccount.canSaveForType(type: MobileAccountType): Boolean =
    accountName.isNotBlank() && when (type) {
        MobileAccountType.STALKER_PORTAL -> url.isNotBlank() && normalizeMacEntries(macAddressList, macAddress).isNotEmpty()
        MobileAccountType.XTREME_API -> url.isNotBlank() && username.isNotBlank() && password.isNotBlank()
        MobileAccountType.M3U8_URL,
        MobileAccountType.M3U8_LOCAL -> m3u8Path.isNotBlank() || url.isNotBlank()
    }

private fun MobileAccountType.choiceLabel(): String =
    when (this) {
        MobileAccountType.STALKER_PORTAL -> MobileMessages.text("mobileAccountTypeStalkerPortal")
        MobileAccountType.XTREME_API -> MobileMessages.text("mobileAccountTypeXtremeApi")
        MobileAccountType.M3U8_URL, MobileAccountType.M3U8_LOCAL ->
            MobileMessages.text("mobileAccountTypeM3uPlaylist")
    }

private fun MobileAccountType.isM3uPlaylistType(): Boolean =
    this == MobileAccountType.M3U8_URL || this == MobileAccountType.M3U8_LOCAL

private fun MobileAccount.m3uPlaylistUrlInput(): String =
    url.ifBlank {
        m3u8Path
            .takeIf { type == MobileAccountType.M3U8_URL && it.isRemotePlaylistUrl() }
            .orEmpty()
    }

private fun MobileAccount.m3uPlaylistFileInput(): String =
    if (type == MobileAccountType.M3U8_URL && url.isBlank() && m3u8Path.isRemotePlaylistUrl()) {
        ""
    } else {
        m3u8Path
    }

private fun String.isRemotePlaylistUrl(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

private fun XtremeCredential.displayLabel(credentials: List<XtremeCredential>): String {
    val duplicateUsername = credentials.count { it.username == username } > 1
    val base = if (duplicateUsername) {
        "$username / ${password.maskedCredentialPassword()}"
    } else {
        username
    }
    return if (isDefault) "$base (default)" else base
}

private fun String.maskedCredentialPassword(): String =
    if (isBlank()) "" else "*".repeat(length.coerceIn(4, 8))

private fun MobileAccount.hasAdvancedStalkerFields(): Boolean =
    username.isNotBlank() ||
        password.isNotBlank() ||
        serialNumber.isNotBlank() ||
        deviceId1.isNotBlank() ||
        deviceId2.isNotBlank() ||
        signature.isNotBlank() ||
        httpMethod.isNotBlank() && !httpMethod.equals("GET", ignoreCase = true) ||
        timezone.isNotBlank() && timezone != "Europe/London"

private fun MobileAccount.stalkerMacOptions(): List<String> =
    normalizeMacEntries(macAddressList, macAddress)

private fun MobileAccount.withStalkerMacInput(input: String): MobileAccount {
    val normalizedMacs = normalizeMacEntries(input)
    return copy(
        macAddress = normalizedMacs.firstOrNull().orEmpty(),
        macAddressList = input
    )
}

private fun MobileAccount.withStalkerMacs(macOptions: List<String>, selectedMac: String): MobileAccount {
    val normalizedMacs = normalizeMacEntries(macOptions)
    val normalizedSelected = selectedMac.normalizedMacEntry()
    val selected = normalizedMacs.firstOrNull { it.equals(normalizedSelected, ignoreCase = true) }
        ?: normalizedMacs.firstOrNull()
        ?: ""
    return copy(
        macAddress = selected,
        macAddressList = normalizedMacs.joinToString(",")
    )
}

private fun normalizeMacEntries(vararg values: String): List<String> =
    normalizeMacEntries(values.asIterable())

private fun normalizeMacEntries(values: Iterable<String>): List<String> =
    values
        .flatMap { it.split(',', ';') }
        .map { it.normalizedMacEntry() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }

private fun String.normalizedMacEntry(): String =
    filterNot { it.isWhitespace() }

@Composable
private fun AccountTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    val secret = label.contains("Password", ignoreCase = true)
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
        label = { Text(label) }, singleLine = true,
        visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = when {
            secret -> KeyboardType.Password
            label.contains("URL") -> KeyboardType.Uri
            else -> KeyboardType.Text
        }),
        colors = mobileOutlinedTextFieldColors()
    )
}

@Composable
private fun AccountMultilineTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp),
        minLines = 3,
        textStyle = MaterialTheme.typography.bodyLarge,
        label = {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        placeholder = placeholder?.let { placeholderText ->
            {
                Text(
                    placeholderText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        colors = mobileOutlinedTextFieldColors()
    )
}

@Composable
private fun CompactOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
    readOnly: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        readOnly = readOnly,
        modifier = modifier.heightIn(min = 56.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        label = {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        placeholder = placeholder?.let { placeholderText ->
            {
                Text(
                    placeholderText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        keyboardOptions = keyboardOptions,
        colors = mobileOutlinedTextFieldColors()
    )
}

@Composable
internal fun mobileOutlinedTextFieldColors(): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        disabledBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f),
        errorBorderColor = MaterialTheme.colorScheme.error
    )

@Composable
private fun AppTabs(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    vertical: Boolean = false,
    compact: Boolean = false,
    searchEnabled: Boolean = false,
    searchActive: Boolean = false,
    onSearchClick: () -> Unit = {},
    onHideNavigation: () -> Unit = {}
) {
    val tabs = listOf(
        BottomTab("Bookmarks", Icons.Outlined.Bookmarks),
        BottomTab("Accounts", Icons.Outlined.AccountCircle),
        BottomTab("Watching", Icons.Outlined.PlayCircle),
        BottomTab("Settings", Icons.Outlined.Settings)
    )
    if (vertical) {
        androidx.compose.material3.NavigationRail {
            IconButton(onClick = onHideNavigation) { Icon(Icons.Outlined.ChevronLeft, contentDescription = "Hide navigation") }
            tabs.forEachIndexed { index, tab ->
                androidx.compose.material3.NavigationRailItem(
                    selected = selectedTab == index, onClick = { onSelect(index) },
                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                    alwaysShowLabel = false
                )
            }
        }
    } else {
        androidx.compose.material3.NavigationBar {
            tabs.forEachIndexed { index, tab ->
                NavigationBarItem(
                    selected = selectedTab == index, onClick = { onSelect(index) },
                    icon = { Icon(tab.icon, contentDescription = tab.label) }
                )
            }
        }
    }
}

private data class BottomTab(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
internal fun EmptyState(title: String, detail: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DeepNightSurfaceHigh)
            .padding(14.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(detail, color = DeepNightMutedText, style = MaterialTheme.typography.bodySmall)
    }
}

data class RemoteSyncUiActions(
    val loadPreferences: suspend () -> AndroidPreferenceSnapshot,
    val checkConnection: suspend (host: String, port: Int) -> Boolean,
    val pullFromDesktop: suspend (
        host: String,
        port: Int,
        onProgress: (RemoteSyncProgress) -> Unit
    ) -> RemoteSyncPullResult,
    val resetLocalData: suspend () -> Unit
) {
    companion object {
        fun preview(): RemoteSyncUiActions =
            RemoteSyncUiActions(
                loadPreferences = { AndroidPreferenceSnapshot() },
                checkConnection = { _, _ -> true },
                pullFromDesktop = { _, _, onProgress ->
                    onProgress(RemoteSyncProgress(RemoteSyncProgressStep.FINISHED, "1234"))
                    RemoteSyncPullResult(
                        report = com.uiptv.mobile.shared.db.DatabaseSyncReport(emptyList()),
                        message = "Preview"
                    )
                },
                resetLocalData = {}
            )
    }
}

data class BackupRestoreUiActions(
    val backupToUri: suspend (String) -> BackupRestoreResult,
    val restoreFromUri: suspend (String) -> BackupRestoreResult
) {
    companion object {
        fun preview(): BackupRestoreUiActions =
            BackupRestoreUiActions(
                backupToUri = { BackupRestoreResult("Preview backup created") },
                restoreFromUri = { BackupRestoreResult("Preview backup restored") }
            )
    }
}

data class BrowseUiActions(
    val loadBrowse: suspend (
        accountId: Long?,
        mode: BrowseMode,
        categoryRowId: Long?,
        query: String
    ) -> MobileBrowseSnapshot,
    val listBookmarkCategories: suspend () -> List<MobileBookmarkCategory>,
    val listBookmarks: suspend (String, String?) -> List<MobileBookmark>,
    val toggleBookmark: suspend (MobileBrowseItem) -> Boolean,
    val removeCachedCategories: suspend (Long, BrowseMode, Set<Long>) -> MobileCategoryCacheRemovalResult,
    val removeBookmark: suspend (Long) -> Unit,
    val clearRecentlyPlayedBookmarks: suspend () -> Unit,
    val removeRecentlyPlayedBookmark: suspend (MobileBookmark) -> Unit,
    val listWatchingNow: suspend (String) -> List<MobileWatchingNowItem>,
    val listWatchingNowEpisodes: suspend (MobileWatchingNowItem) -> List<MobileWatchingNowEpisode>,
    val enrichWatchingNowItem: suspend (MobileWatchingNowItem) -> MobileWatchingNowItem,
    val enrichSeriesDetails: suspend (MobileWatchingNowItem, List<MobileWatchingNowEpisode>) -> MobileSeriesDetails,
    val markWatchingNowEpisode: suspend (MobileWatchingNowEpisode) -> Unit,
    val clearWatchingNowEpisode: suspend (MobileWatchingNowEpisode) -> Unit,
    val removeWatchingNow: suspend (MobileWatchingNowItem) -> Unit
) {
    companion object {
        fun preview(): BrowseUiActions {
            val category = MobileBrowseCategory(1, "all", 1, "All", 2)
            val account = com.uiptv.mobile.shared.browse.BrowseAccountOption(1, "Demo")
            val item = MobileBrowseItem(
                rowId = 1,
                accountId = 1,
                accountName = "Demo",
                mode = BrowseMode.LIVE,
                categoryRowId = 1,
                categoryProviderId = "all",
                categoryTitle = "All",
                channelId = "news-1",
                name = "Demo News",
                number = "1",
                command = "https://stream.test/news.m3u8",
                isHd = true
            )
            return BrowseUiActions(
                loadBrowse = { accountId, mode, _, _ ->
                    MobileBrowseSnapshot(
                        accounts = listOf(account),
                        selectedAccountId = accountId ?: 1,
                        mode = mode,
                        categories = listOf(category),
                        selectedCategoryRowId = category.rowId,
                        items = listOf(item.copy(mode = mode))
                    )
                },
                listBookmarkCategories = {
                    listOf(MobileBookmarkCategory(null, "All", 1), MobileBookmarkCategory("1", "Demo", 1))
                },
                listBookmarks = { _, _ ->
                    listOf(
                        MobileBookmark(
                            rowId = 1,
                            accountId = 1,
                            accountName = "Demo",
                            bookmarkCategoryId = "1",
                            categoryTitle = "All",
                            channelId = "news-1",
                            channelName = "Demo News",
                            command = item.command,
                            mode = BrowseMode.LIVE
                        )
                    )
                },
                toggleBookmark = { true },
                removeCachedCategories = { _, _, ids ->
                    MobileCategoryCacheRemovalResult(ids.size, ids.size, 0)
                },
                removeBookmark = {},
                clearRecentlyPlayedBookmarks = {},
                removeRecentlyPlayedBookmark = {},
                listWatchingNow = {
                    listOf(MobileWatchingNowItem(1, 1, "Demo", BrowseMode.VOD, "Demo Movie", "Demo", updatedAtEpochSeconds = 1))
                },
                listWatchingNowEpisodes = { emptyList() },
                enrichWatchingNowItem = { it },
                enrichSeriesDetails = { series, episodes -> MobileSeriesDetails(series, episodes) },
                markWatchingNowEpisode = {},
                clearWatchingNowEpisode = {},
                removeWatchingNow = {}
            )
        }
    }
}

data class PlaybackUiActions(
    val loadPlayerPreference: suspend () -> PlayerPreference,
    val playerChoices: suspend () -> List<PlayerChoice>,
    val playBrowseItem: suspend (MobileBrowseItem, AndroidPlayerPreference, Boolean) -> PlaybackLaunchResult,
    val playBookmark: suspend (MobileBookmark, AndroidPlayerPreference, Boolean) -> PlaybackLaunchResult,
    val playWatchingNow: suspend (MobileWatchingNowItem, AndroidPlayerPreference, Boolean) -> PlaybackLaunchResult,
    val playWatchingNowEpisode: suspend (MobileWatchingNowEpisode, AndroidPlayerPreference, Boolean) -> PlaybackLaunchResult,
    val playBingeWatchSeason: suspend (MobileWatchingNowItem, List<MobileWatchingNowEpisode>, String, AndroidPlayerPreference, Boolean) -> PlaybackLaunchResult,
    val openPlayerInstall: suspend (PlayerChoice) -> Unit,
    val savePlayerPreference: suspend (AndroidPlayerPreference) -> Unit,
    val clearPlayerPreference: suspend () -> Unit
) {
    companion object {
        fun preview(): PlaybackUiActions =
            PlaybackUiActions(
                loadPlayerPreference = { PlayerPreference() },
                playerChoices = {
                    listOf(
                        PlayerChoice(AndroidPlayerPreference.EMBEDDED_PLAYER, "Embedded"),
                        PlayerChoice(AndroidPlayerPreference.NATIVE, "Android Media")
                    )
                },
                playBrowseItem = { item, _, _ -> PlaybackLaunchResult(true, "Opening ${item.name}") },
                playBookmark = { bookmark, _, _ -> PlaybackLaunchResult(true, "Opening ${bookmark.channelName}") },
                playWatchingNow = { item, _, _ -> PlaybackLaunchResult(true, "Opening ${item.title}") },
                playWatchingNowEpisode = { episode, _, _ -> PlaybackLaunchResult(true, "Opening ${episode.title}") },
                playBingeWatchSeason = { series, _, _, _, _ -> PlaybackLaunchResult(true, "Starting ${series.title}") },
                openPlayerInstall = {},
                savePlayerPreference = {},
                clearPlayerPreference = {}
            )
    }
}

data class FilterUiActions(
    val load: suspend () -> AndroidFilterSettings,
    val save: suspend (AndroidFilterSettings) -> Unit,
    val setPaused: suspend (Boolean) -> Unit,
    val setEnableThumbnails: suspend (Boolean) -> Unit
) {
    companion object {
        fun preview(): FilterUiActions =
            FilterUiActions(
                load = { AndroidFilterSettings(categoryFilters = "adult", channelFilters = "xxx") },
                save = {},
                setPaused = {},
                setEnableThumbnails = {}
            )
    }
}

data class PanelVisibilityUiActions(
    val load: suspend () -> PanelVisibilityPreference,
    val save: suspend (PanelVisibilityPreference) -> Unit
) {
    companion object {
        fun preview(): PanelVisibilityUiActions =
            PanelVisibilityUiActions(
                load = { PanelVisibilityPreference() },
                save = {}
            )
    }
}

data class AccountUiActions(
    val loadAccounts: suspend () -> List<MobileAccount>,
    val saveAccount: suspend (MobileAccount) -> MobileAccount,
    val deleteAccount: suspend (Long) -> Unit,
    val clearCache: suspend (Long) -> AccountCacheSummary,
    val clearAllCache: suspend () -> AccountCacheSummary,
    val enqueueCacheJob: suspend (CacheRefreshJobRequest) -> String,
    val loadCacheJobState: suspend (String) -> CacheRefreshJobState?,
    val loadRecentCacheJobs: suspend () -> List<CacheRefreshJobState>,
    val stopCacheJob: suspend (String) -> Unit,
    val loadCacheSummary: suspend (Long) -> AccountCacheSummary = { AccountCacheSummary() }
) {
    companion object {
        fun preview(): AccountUiActions =
            AccountUiActions(
                loadAccounts = {
                    listOf(
                        MobileAccount(id = 1, accountName = "Demo Portal", type = MobileAccountType.STALKER_PORTAL),
                        MobileAccount(id = 2, accountName = "Demo M3U", type = MobileAccountType.M3U8_URL)
                    )
                },
                saveAccount = { it.copy(id = it.id ?: 1) },
                deleteAccount = {},
                clearCache = { AccountCacheSummary(liveCategories = 1, liveChannels = 4) },
                clearAllCache = { AccountCacheSummary(liveCategories = 2, liveChannels = 8) },
                enqueueCacheJob = { "preview-job-${it.action.name}" },
                loadCacheJobState = {
                    CacheRefreshJobState(
                        jobId = it,
                        action = CacheRefreshAction.REFRESH_ALL,
                        status = CacheRefreshJobStatus.SUCCEEDED,
                        progressPercent = 100,
                        message = "Preview complete"
                    )
                },
                loadRecentCacheJobs = { emptyList() },
                stopCacheJob = {},
                loadCacheSummary = { AccountCacheSummary(liveCategories = 1, liveChannels = 4) }
            )
    }
}

private data class AccountFeedback(
    val title: String,
    val message: String,
    val success: Boolean
)

private data class FailedRefreshAccount(
    val id: Long,
    val name: String
)

private fun failedRefreshAccounts(
    accounts: List<MobileAccount>,
    failedAccountIds: List<Long>
): List<FailedRefreshAccount> {
    val namesById = accounts.mapNotNull { account ->
        account.id?.let { it to account.accountName }
    }.toMap()
    return failedAccountIds
        .distinct()
        .map { id -> FailedRefreshAccount(id, namesById[id] ?: "Account $id") }
}

private fun RemoteSyncProgress.label(): String =
    message.ifBlank {
        when (step) {
            RemoteSyncProgressStep.CONNECTING -> "Connecting"
            RemoteSyncProgressStep.WAITING_FOR_APPROVAL -> "Waiting for desktop approval"
            RemoteSyncProgressStep.DOWNLOADING -> "Downloading"
            RemoteSyncProgressStep.APPLYING_SYNC -> "Applying"
            RemoteSyncProgressStep.COMPLETING_REMOTE -> "Completing"
            RemoteSyncProgressStep.FINISHED -> "Finished"
        }
    }

private fun String.shortAccountType(): String =
    when (this) {
        "STALKER_PORTAL" -> "Stalker"
        "XTREME_API" -> "Xtreme"
        "M3U8_URL",
        "M3U8_LOCAL" -> "M3U Playlist"
        else -> this
    }

private fun String.shortJobId(): String =
    if (length <= 8) this else take(8)

private fun BrowseMode.displayLabel(): String =
    when (this) {
        BrowseMode.LIVE -> "Live TV"
        BrowseMode.VOD -> "vod"
        BrowseMode.SERIES -> "series"
    }

private fun BrowseMode.removalItemLabel(): String =
    when (this) {
        BrowseMode.LIVE -> "channels"
        BrowseMode.VOD -> "VOD items"
        BrowseMode.SERIES -> "series items and episodes"
    }

private fun MobileAccountType?.browseModesForAccount(): List<BrowseMode> =
    when (this) {
        MobileAccountType.XTREME_API,
        MobileAccountType.STALKER_PORTAL -> BrowseMode.entries
        else -> listOf(BrowseMode.LIVE)
    }

private enum class AccountFilter(val label: String, val emptyLabel: String) {
    ALL("All", ""),
    PINNED("Pinned", "pinned"),
    STALKER("Stalker", "stalker"),
    XTREME("Xtreme", "xtreme"),
    M3U("M3U", "m3u");

    fun matches(account: MobileAccount): Boolean =
        when (this) {
            ALL -> true
            PINNED -> account.pinToTop
            STALKER -> account.type == MobileAccountType.STALKER_PORTAL
            XTREME -> account.type == MobileAccountType.XTREME_API
            M3U -> account.type == MobileAccountType.M3U8_URL || account.type == MobileAccountType.M3U8_LOCAL
        }
}

private fun MobileBrowseItem.subtitle(): String =
    listOf(
        number.takeIf { it.isNotBlank() },
        categoryTitle,
        mode.displayLabel(),
        "HD".takeIf { isHd },
        metadataLine().takeIf { it.isNotBlank() }
    ).filterNotNull().joinToString(" - ")

private fun MobileBrowseItem.hasDrmMetadata(): Boolean =
    drmType.isCleanMetadataValue() ||
        drmLicenseUrl.isCleanMetadataValue() ||
        clearKeysJson.isCleanMetadataValue() ||
        inputstreamAddon.isCleanMetadataValue() ||
        manifestType.isCleanMetadataValue()

private fun MobileBookmark.hasDrmMetadata(): Boolean =
    drmType.isCleanMetadataValue() ||
        drmLicenseUrl.isCleanMetadataValue() ||
        clearKeysJson.isCleanMetadataValue() ||
        inputstreamAddon.isCleanMetadataValue() ||
        manifestType.isCleanMetadataValue()

private fun String.isCleanMetadataValue(): Boolean {
    val value = trim()
    return value.isNotBlank() &&
        !value.equals("null", ignoreCase = true) &&
        !value.equals("undefined", ignoreCase = true)
}

private fun MobileBrowseItem.toWatchingNowSeriesItem(): MobileWatchingNowItem =
    MobileWatchingNowItem(
        rowId = rowId,
        accountId = accountId,
        accountName = accountName,
        mode = BrowseMode.SERIES,
        title = name,
        subtitle = accountName,
        logo = logo,
        categoryProviderId = categoryProviderId,
        categoryRowId = categoryRowId,
        contentId = channelId,
        plot = plot,
        releaseDate = releaseDate,
        rating = rating,
        duration = duration,
        genre = genre,
        imdbUrl = imdbUrl
    )

private fun MobileBrowseItem.metadataLine(): String =
    metadataLine(rating, releaseDate, duration, genre)

private fun MobileWatchingNowItem.metadataLine(): String =
    metadataLine(rating, releaseDate, duration, genre)

private fun metadataLine(
    rating: String,
    releaseDate: String,
    duration: String,
    genre: String
): String =
    listOf(
        rating.takeIf { it.isNotBlank() }?.let { "IMDb $it" },
        releaseDate.desktopShortDate().takeIf { it.isNotBlank() },
        duration.takeIf { it.isNotBlank() },
        genre.takeIf { it.isNotBlank() }
    ).filterNotNull().joinToString(" - ")

private fun String.desktopReleaseLine(): String {
    val value = desktopShortDate()
    return if (value.isBlank()) "" else "Release: $value"
}

private fun String.desktopShortDate(): String {
    val value = trim()
    if (value.isBlank()) {
        return ""
    }
    val iso = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""").find(value)
    if (iso != null) {
        val year = iso.groupValues[1].toIntOrNull()
        val month = iso.groupValues[2].toIntOrNull()
        val day = iso.groupValues[3].toIntOrNull()
        if (year != null && month != null && day != null && month in 1..12 && day in 1..31) {
            return "${day.englishOrdinal()} ${ENGLISH_MONTHS[month - 1]} $year"
        }
    }
    return value.substringBefore('T').takeIf { it.matches(Regex("""\d{4}-\d{2}-\d{2}""")) }
        ?.desktopShortDate()
        ?: value
}

private fun Int.englishOrdinal(): String {
    val suffix = if (this % 100 in 11..13) {
        "th"
    } else {
        when (this % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
    }
    return "$this$suffix"
}

private fun String.normalizedImdbUrl(): String {
    val value = trim()
    if (value.isBlank()) {
        return ""
    }
    if (value.startsWith("http://") || value.startsWith("https://")) {
        return value
    }
    val id = Regex("""tt\d+""").find(value)?.value.orEmpty()
    return if (id.isNotBlank()) "https://www.imdb.com/title/$id/" else ""
}

private val ENGLISH_MONTHS = listOf(
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December"
)

private fun List<MobileWatchingNowEpisode>.withWatchingFlag(target: MobileWatchingNowEpisode): List<MobileWatchingNowEpisode> =
    map { episode ->
        if (episode.accountId == target.accountId &&
            episode.categoryProviderId == target.categoryProviderId &&
            episode.seriesId == target.seriesId
        ) {
            episode.copy(isWatched = episode.sameEpisodeAs(target))
        } else {
            episode
        }
    }

private fun List<MobileWatchingNowEpisode>.withBingeStartFlag(seasonKey: String): List<MobileWatchingNowEpisode> {
    val target = bingeStartEpisode(seasonKey) ?: return this
    return withWatchingFlag(target)
}

private fun List<MobileWatchingNowEpisode>.bingeStartEpisode(seasonKey: String): MobileWatchingNowEpisode? {
    val seasonEpisodes = filter { it.matchesBingeSeasonKey(seasonKey) }
        .sortedWith(
            compareBy<MobileWatchingNowEpisode> { it.resolvedSeason().toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.resolvedEpisodeNumber().toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.title.lowercase() }
        )
    return seasonEpisodes.firstOrNull { it.isWatched } ?: seasonEpisodes.firstOrNull()
}

private fun MobileWatchingNowEpisode.matchesBingeSeasonKey(seasonKey: String): Boolean =
    when {
        seasonKey.isBlank() -> true
        seasonKey == "other" -> seasonTab().key == "other"
        else -> seasonTab().key == seasonKey
    }

private fun List<MobileWatchingNowEpisode>.clearWatchingFlag(target: MobileWatchingNowEpisode): List<MobileWatchingNowEpisode> =
    map { episode ->
        if (episode.accountId == target.accountId &&
            episode.categoryProviderId == target.categoryProviderId &&
            episode.seriesId == target.seriesId
        ) {
            episode.copy(isWatched = false)
        } else {
            episode
        }
    }

private fun MobileWatchingNowEpisode.sameEpisodeAs(other: MobileWatchingNowEpisode): Boolean {
    val thisKey = episodeId.ifBlank { rowId.toString() }
    val otherKey = other.episodeId.ifBlank { other.rowId.toString() }
    return thisKey == otherKey
}

private fun MobileWatchingNowItem.stableWatchingNowKey(): String {
    val contentKey = contentId.ifBlank { rowId.toString() }
    return "${mode.name}-$accountId-$categoryProviderId-$contentKey-$rowId"
}

private fun MobileWatchingNowEpisode.stableWatchingNowEpisodeKey(): String {
    val episodeKey = episodeId.ifBlank { rowId.toString() }
    return "$accountId-$categoryProviderId-$seriesId-${seasonTab().key}-$episodeKey-$episodeNumber-$rowId"
}

private fun AndroidPlayerPreference.playerLabel(): String =
    when (this) {
        AndroidPlayerPreference.ASK_EVERY_TIME -> "Ask Every Time"
        AndroidPlayerPreference.EMBEDDED_PLAYER -> "Embedded"
        AndroidPlayerPreference.NATIVE -> "Android Media"
        AndroidPlayerPreference.VLC -> "VLC"
        AndroidPlayerPreference.MX_PLAYER_PRO,
        AndroidPlayerPreference.MX_PLAYER_FREE -> "MX Player"
        AndroidPlayerPreference.KODI -> "Kodi"
        AndroidPlayerPreference.JUST_PLAYER -> "Just Player"
        AndroidPlayerPreference.XPLAYER -> "XPlayer"
        AndroidPlayerPreference.SYSTEM_CHOOSER -> "System Chooser"
    }

private fun AndroidPlayerPreference.playerBadge(): String =
    when (this) {
        AndroidPlayerPreference.ASK_EVERY_TIME -> "?"
        AndroidPlayerPreference.EMBEDDED_PLAYER -> "EP"
        AndroidPlayerPreference.NATIVE -> "AM"
        AndroidPlayerPreference.VLC -> "VLC"
        AndroidPlayerPreference.MX_PLAYER_PRO,
        AndroidPlayerPreference.MX_PLAYER_FREE -> "MX"
        AndroidPlayerPreference.KODI -> "K"
        AndroidPlayerPreference.JUST_PLAYER -> "J"
        AndroidPlayerPreference.XPLAYER -> "XP"
        AndroidPlayerPreference.SYSTEM_CHOOSER -> "SYS"
    }

private fun AndroidPlayerPreference.playerIconColor(): Color =
    when (this) {
        AndroidPlayerPreference.ASK_EVERY_TIME -> Color(0xFF374151)
        AndroidPlayerPreference.EMBEDDED_PLAYER -> DeepNightAccentBase
        AndroidPlayerPreference.NATIVE -> Color(0xFF3DDC84)
        AndroidPlayerPreference.VLC -> Color(0xFFFF9800)
        AndroidPlayerPreference.MX_PLAYER_PRO,
        AndroidPlayerPreference.MX_PLAYER_FREE -> Color(0xFF2563EB)
        AndroidPlayerPreference.KODI -> Color(0xFF2F9ED8)
        AndroidPlayerPreference.JUST_PLAYER -> Color(0xFF111827)
        AndroidPlayerPreference.XPLAYER -> Color(0xFFE11D48)
        AndroidPlayerPreference.SYSTEM_CHOOSER -> Color(0xFF64748B)
    }

private fun AndroidPlayerPreference.playerIconContentColor(): Color =
    when (this) {
        AndroidPlayerPreference.EMBEDDED_PLAYER,
        AndroidPlayerPreference.NATIVE -> Color(0xFF052E1B)
        else -> Color.White
    }

private fun PlayerChoice.opensInstallFlow(): Boolean =
    !installed && storeUrl.isNotBlank()

private fun PlayerChoice.matchesSelected(selectedPlayer: AndroidPlayerPreference?): Boolean =
    selectedPlayer != null && (player == selectedPlayer || (player.isMxPlayer() && selectedPlayer.isMxPlayer()))

private fun AndroidPlayerPreference.isMxPlayer(): Boolean =
    when (this) {
        AndroidPlayerPreference.MX_PLAYER_PRO,
        AndroidPlayerPreference.MX_PLAYER_FREE -> true
        AndroidPlayerPreference.VLC,
        AndroidPlayerPreference.ASK_EVERY_TIME,
        AndroidPlayerPreference.EMBEDDED_PLAYER,
        AndroidPlayerPreference.NATIVE,
        AndroidPlayerPreference.KODI,
        AndroidPlayerPreference.JUST_PLAYER,
        AndroidPlayerPreference.XPLAYER,
        AndroidPlayerPreference.SYSTEM_CHOOSER -> false
    }

private fun AndroidPlayerPreference.supportsBingeWatch(): Boolean =
    this == AndroidPlayerPreference.EMBEDDED_PLAYER ||
        this == AndroidPlayerPreference.NATIVE ||
        this == AndroidPlayerPreference.ASK_EVERY_TIME

private fun PendingPlayback.supportsRememberedPlayer(player: AndroidPlayerPreference): Boolean =
    this !is PendingPlayback.Binge || player.supportsBingeWatch()

private fun List<PlayerChoice>.choicesFor(pending: PendingPlayback): List<PlayerChoice> =
    if (pending is PendingPlayback.Binge) bingeWatchChoices() else this

private fun List<PlayerChoice>.bingeWatchChoices(): List<PlayerChoice> =
    filter { it.player.supportsBingeWatch() && !it.opensInstallFlow() }

private sealed interface PendingPlayback {
    data class Browse(val item: MobileBrowseItem) : PendingPlayback
    data class Bookmark(val bookmark: MobileBookmark) : PendingPlayback
    data class Watching(val item: MobileWatchingNowItem) : PendingPlayback
    data class WatchingEpisode(val episode: MobileWatchingNowEpisode) : PendingPlayback
    data class Binge(
        val series: MobileWatchingNowItem,
        val episodes: List<MobileWatchingNowEpisode>,
        val seasonKey: String
    ) : PendingPlayback
}

private fun CacheRefreshJobState.isActive(): Boolean =
    status == CacheRefreshJobStatus.QUEUED || status == CacheRefreshJobStatus.RUNNING

private fun CacheRefreshAction.label(): String =
    when (this) {
        CacheRefreshAction.REFRESH_ACCOUNT -> "Refresh Account"
        CacheRefreshAction.REFRESH_ALL -> "Refresh All"
        CacheRefreshAction.CLEAR_ACCOUNT_CACHE -> "Clear Account"
        CacheRefreshAction.CLEAR_ALL_CACHE -> "Clear All"
    }

private fun CacheRefreshJobStatus.label(): String =
    when (this) {
        CacheRefreshJobStatus.QUEUED -> "Queued"
        CacheRefreshJobStatus.RUNNING -> "Running"
        CacheRefreshJobStatus.SUCCEEDED -> "Done"
        CacheRefreshJobStatus.FAILED -> "Failed"
        CacheRefreshJobStatus.SKIPPED -> "Skipped"
    }

private fun String.filterTermCount(): Int =
    split(',', '\n', ';')
        .map { it.trim() }
        .count { it.isNotBlank() }

private fun cacheSummaryHeader(message: String): String =
    if (message.contains('\n')) {
        message.lineSequence().firstOrNull()?.trim().orEmpty()
    } else {
        message
            .split(" | ")
            .firstOrNull()
            ?.trim()
            .orEmpty()
    }

private fun cacheSummaryLines(message: String): List<String> {
    if (message.isBlank()) {
        return emptyList()
    }
    if (message.contains('\n')) {
        return message
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .drop(1)
            .toList()
    }
    return message
        .split(" | ")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .drop(1)
}

private fun cacheProgressLines(message: String): List<String> =
    message
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()
        .ifEmpty { listOf("Waiting for cache refresh status.") }

private fun String.isLoopbackHost(): Boolean {
    val normalized = trim().lowercase()
    return normalized == "localhost" || normalized == "127.0.0.1" || normalized == "::1"
}

private fun String.hasRemoteSyncHttpsScheme(): Boolean =
    trim().startsWith("https://", ignoreCase = true)

private fun String.hasRemoteSyncHttpScheme(): Boolean =
    trim().startsWith("http://", ignoreCase = true)

private fun String.withoutRemoteSyncScheme(): String {
    val trimmed = trim()
    return when {
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed.substring("https://".length)
        trimmed.startsWith("http://", ignoreCase = true) -> trimmed.substring("http://".length)
        else -> trimmed
    }.substringBefore("/")
}

private fun remoteSyncRequestHost(host: String, useHttps: Boolean): String {
    val normalizedHost = host.withoutRemoteSyncScheme()
    return if (useHttps && normalizedHost.isNotBlank()) {
        "https://$normalizedHost"
    } else {
        normalizedHost
    }
}
