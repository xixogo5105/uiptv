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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.PushPin
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

typealias LocalPlaylistPicker = (onSelected: (String) -> Unit) -> Unit
typealias BackupFileCreator = (suggestedName: String, onSelected: (String?) -> Unit) -> Unit
typealias RestoreFilePicker = (onSelected: (String?) -> Unit) -> Unit
typealias LogoRenderer = @Composable (String, String, Modifier) -> Unit
typealias PlayerIconRenderer = @Composable (PlayerChoice, Modifier) -> Unit

private val DeepNightPrimaryBase = Color(0xFFD1E4FF)
private val DeepNightSurfaceBase = Color(0xFF1B1F23)
private val DeepNightSurfaceHighBase = Color(0xFF22282E)
private val DeepNightSurfaceHighestBase = Color(0xFF2A3138)
private val DeepNightAccentBase = Color(0xFF4FD8EB)
private val DeepNightTextBase = Color(0xFFF4F7FA)
private val DeepNightMutedTextBase = Color(0xFFAEB8C2)
private val DeepNightBackgroundBase = Color(0xFF0F1216)

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

private val LightUiptvPalette = UiptvPalette(
    primary = Color(0xFF17456A),
    surface = Color(0xFFF7FAFD),
    surfaceHigh = Color(0xFFEAF1F7),
    surfaceHighest = Color(0xFFDDE7F0),
    accent = Color(0xFF007D8D),
    text = Color(0xFF111820),
    mutedText = Color(0xFF52616E),
    background = Color(0xFFFFFFFF)
)

private val LocalUiptvPalette = staticCompositionLocalOf { DarkUiptvPalette }
private val LocalWidePhoneLayout = staticCompositionLocalOf { false }
private const val DesktopDownloadUrl = "https://github.com/xixogo5105/uiptv/releases/latest"

private enum class UiptvLayoutMode {
    Compact,
    WidePhone,
    WideLarge
}

private enum class WatchingNowFilter(val label: String) {
    ALL("All"),
    VOD("VOD"),
    SERIES("Series");

    fun matches(item: MobileWatchingNowItem): Boolean =
        when (this) {
            ALL -> true
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
    onPrimary = Color(0xFF07151E),
    primaryContainer = Color(0xFF24384C),
    onPrimaryContainer = Color(0xFFEAF3FF),
    secondary = DeepNightAccentBase,
    onSecondary = Color(0xFF001F25),
    tertiary = Color(0xFFFFD54F),
    onTertiary = Color(0xFF221A00),
    background = DeepNightBackgroundBase,
    onBackground = DeepNightTextBase,
    surface = DeepNightSurfaceBase,
    onSurface = DeepNightTextBase,
    surfaceVariant = DeepNightSurfaceHighBase,
    onSurfaceVariant = DeepNightMutedTextBase,
    outline = Color(0xFF60707D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val UiptvLightColorScheme = lightColorScheme(
    primary = LightUiptvPalette.primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE5F7),
    onPrimaryContainer = Color(0xFF001D31),
    secondary = LightUiptvPalette.accent,
    onSecondary = Color.White,
    tertiary = Color(0xFF775C00),
    onTertiary = Color.White,
    background = LightUiptvPalette.background,
    onBackground = LightUiptvPalette.text,
    surface = LightUiptvPalette.surface,
    onSurface = LightUiptvPalette.text,
    surfaceVariant = LightUiptvPalette.surfaceHigh,
    onSurfaceVariant = LightUiptvPalette.mutedText,
    outline = Color(0xFF6D7B87),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

@Composable
fun UiptvMobileApp(
    resumeSignal: Int = 0,
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
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> }
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedBrowseAccount by remember { mutableStateOf<MobileAccount?>(null) }
    var showThumbnails by remember { mutableStateOf(false) }
    var wideSearchVisible by rememberSaveable { mutableStateOf(false) }
    var panelVisibilityPreference by remember { mutableStateOf(PanelVisibilityPreference()) }
    val appScope = rememberCoroutineScope()
    fun selectTab(index: Int) {
        selectedTab = index
        selectedBrowseAccount = null
        wideSearchVisible = false
    }
    fun updatePanelVisibility(preference: PanelVisibilityPreference) {
        panelVisibilityPreference = preference
        appScope.launch {
            runCatching { panelVisibilityActions.save(preference) }
        }
    }
    backHandler(selectedBrowseAccount != null) {
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
    val palette = if (useDarkTheme) DarkUiptvPalette else LightUiptvPalette
    CompositionLocalProvider(LocalUiptvPalette provides palette) {
        MaterialTheme(colorScheme = if (useDarkTheme) UiptvDarkColorScheme else UiptvLightColorScheme) {
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
                CompositionLocalProvider(LocalWidePhoneLayout provides (layoutMode == UiptvLayoutMode.WidePhone)) {
                    if (wideLayout) {
                        val wideSearchEnabled = selectedTab != 3
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(palette.background)
                                .safeDrawingPadding()
                        ) {
                            AppTabs(
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
                                }
                            )
                            CurrentTab(
                                selectedTab = selectedTab,
                                resumeSignal = resumeSignal,
                                syncActions = syncActions,
                                accountActions = accountActions,
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
                                selectedTab = selectedTab,
                                resumeSignal = resumeSignal,
                                syncActions = syncActions,
                                accountActions = accountActions,
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
    selectedTab: Int,
    resumeSignal: Int,
    syncActions: RemoteSyncUiActions,
    accountActions: AccountUiActions,
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
    AnimatedContent(
        targetState = selectedTab to (selectedBrowseAccount != null),
        modifier = modifier.fillMaxSize(),
        label = "main-content"
    ) { (tab, hasBrowseAccount) ->
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
                    panelVisibilityPreference.bookmarksCategoryPanelVisible,
                    { visible ->
                        onPanelVisibilityPreferenceChange(
                            panelVisibilityPreference.copy(bookmarksCategoryPanelVisible = visible)
                        )
                    },
                    Modifier.fillMaxSize()
                )
            }
            1 -> {
                val account = selectedBrowseAccount.takeIf { hasBrowseAccount }
                if (account == null) {
                    AccountsScreen(
                        accountActions,
                        onOpenAccountChannels,
                        localPlaylistPicker,
                        wideLayout,
                        wideSearchVisible,
                        resumeSignal,
                        panelVisibilityPreference.accountsActionsPanelVisible,
                        { visible ->
                            onPanelVisibilityPreferenceChange(
                                panelVisibilityPreference.copy(accountsActionsPanelVisible = visible)
                            )
                        },
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
    var mode by remember { mutableStateOf(BrowseMode.LIVE) }
    var categoryQuery by remember { mutableStateOf("") }
    var channelQuery by remember { mutableStateOf("") }
    var selectedCategoryRowId by remember { mutableStateOf<Long?>(null) }
    var statusText by remember { mutableStateOf("Loading") }
    var running by remember { mutableStateOf(false) }
    var reloadGeneration by remember { mutableStateOf(0L) }
    var pendingPlayback by remember { mutableStateOf<PendingPlayback?>(null) }
    var playerChoices by remember { mutableStateOf<List<PlayerChoice>>(emptyList()) }
    var selectedBrowseSeries by remember { mutableStateOf<MobileWatchingNowItem?>(null) }
    var browseSeriesEpisodes by remember { mutableStateOf<List<MobileWatchingNowEpisode>>(emptyList()) }
    var pendingEpisodeMenu by remember { mutableStateOf<MobileWatchingNowEpisode?>(null) }
    var searchVisible by remember { mutableStateOf(false) }
    var categorySelectionMode by remember { mutableStateOf(false) }
    var selectedCategoryIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pendingCategoryRemoval by remember { mutableStateOf(false) }

    fun activeSearchVisible(): Boolean = if (wideLayout) wideSearchVisible else searchVisible
    fun activeCategoryQuery(): String = if (activeSearchVisible()) categoryQuery else ""
    fun activeChannelQuery(): String = if (activeSearchVisible()) channelQuery else ""

    fun reload(
        accountId: Long? = requestedAccountId ?: snapshot.selectedAccountId,
        categoryRowId: Long? = selectedCategoryRowId,
        itemQuery: String = activeChannelQuery(),
        browseMode: BrowseMode = mode
    ) {
        val generation = reloadGeneration + 1
        reloadGeneration = generation
        scope.launch {
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
                .onFailure { statusText = it.message ?: "Unable to load channels" }
            running = false
        }
    }

    fun toggleCompactSearch() {
        val nextVisible = !searchVisible
        searchVisible = nextVisible
        if (!nextVisible) {
            if (selectedCategoryRowId == null) {
                categoryQuery = ""
            } else if (channelQuery.isNotBlank()) {
                channelQuery = ""
                reload(snapshot.selectedAccountId, selectedCategoryRowId, "")
            }
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
        channelQuery = ""
        reload(snapshot.selectedAccountId, null, "", mode)
    }
    LaunchedEffect(browseActions, requestedAccountId, selectedAccountType, refreshSignal) {
        val activeMode = if (mode in visibleModes) mode else BrowseMode.LIVE
        mode = activeMode
        selectedCategoryRowId = null
        categorySelectionMode = false
        selectedCategoryIds = emptySet()
        pendingCategoryRemoval = false
        channelQuery = ""
        reload(requestedAccountId, null, "", activeMode)
    }
    LaunchedEffect(wideLayout, wideSearchVisible) {
        if (wideLayout && !wideSearchVisible && (categoryQuery.isNotBlank() || channelQuery.isNotBlank())) {
            categoryQuery = ""
            if (channelQuery.isNotBlank()) {
                channelQuery = ""
                if (selectedCategoryRowId != null) {
                    reload(snapshot.selectedAccountId, selectedCategoryRowId, "")
                }
            }
        }
    }
    val visibleCategories = snapshot.categories.filter { category ->
        val query = activeCategoryQuery()
        query.isBlank() || category.title.contains(query.trim(), ignoreCase = true)
    }
    val currentTitle = selectedCategory?.title ?: "${mode.displayLabel()} Categories"
    val screenTitle = "$selectedAccountName - $currentTitle"
    val browseSeries = selectedBrowseSeries
    val selectedCategoryCount = selectedCategoryIds.count { id -> snapshot.categories.any { it.rowId == id } }

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
                .onFailure { statusText = it.message ?: "Unable to remove categories" }
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
                            .onFailure { statusText = it.message ?: "Unable to open episode" }
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
                            .onFailure { statusText = it.message ?: "Unable to start binge watch" }
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
                        .onFailure { statusText = it.message ?: "Unable to mark Watching Now" }
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
                        .onFailure { statusText = it.message ?: "Unable to remove Watching Now flag" }
                    running = false
                }
            },
            onInstall = { choice ->
                pendingEpisodeMenu = null
                scope.launch {
                    running = true
                    runCatching { playbackActions.openPlayerInstall(choice) }
                        .onSuccess { statusText = "Opening ${choice.label} in Google Play" }
                        .onFailure { statusText = it.message ?: "Unable to open Google Play" }
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
                        .onFailure { statusText = it.message ?: "Unable to open episode" }
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
                        .onFailure { statusText = it.message ?: "Unable to open Google Play" }
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
                        .onFailure { statusText = it.message ?: "Unable to open episode" }
                    running = false
                }
            }
        )
        return
    }
    backHandler(categorySelectionMode || selectedCategoryRowId != null) {
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

    if (wideLayout) {
        WideChannelsContent(
            snapshot = snapshot,
            visibleCategories = visibleCategories,
            visibleModes = visibleModes,
            mode = mode,
            selectedCategoryRowId = selectedCategoryRowId,
            screenTitle = screenTitle,
            categoryQuery = categoryQuery,
            channelQuery = channelQuery,
            showAccountSelector = showAccountSelector,
            showThumbnails = showThumbnails,
            running = running,
            statusText = statusText,
            logoRenderer = logoRenderer,
            searchVisible = wideSearchVisible,
            categorySelectionMode = categorySelectionMode,
            selectedCategoryIds = selectedCategoryIds,
            selectedCategoryCount = selectedCategoryCount,
            onBack = {
                if (showingChannelList) {
                    backToCategories()
                } else {
                    onBackToAccounts?.invoke()
                }
            },
            showBack = showingChannelList || onBackToAccounts != null,
            onCategoryQueryChange = { categoryQuery = it },
            onChannelQueryChange = {
                channelQuery = it
                if (selectedCategoryRowId != null) {
                    reload(snapshot.selectedAccountId, selectedCategoryRowId, it)
                }
            },
            onAccountSelect = { account ->
                clearCategorySelection()
                selectedCategoryRowId = null
                channelQuery = ""
                val nextMode = if (mode in account.type.browseModesForAccount()) mode else BrowseMode.LIVE
                mode = nextMode
                reload(account.id, null, "", nextMode)
            },
            onModeSelect = { entry ->
                clearCategorySelection()
                mode = entry
                selectedCategoryRowId = null
                channelQuery = ""
                reload(snapshot.selectedAccountId, null, "", entry)
            },
            onCategorySelect = { category ->
                if (categorySelectionMode) {
                    toggleCategorySelection(category)
                } else {
                    selectedCategoryRowId = category.rowId
                    channelQuery = ""
                    reload(snapshot.selectedAccountId, category.rowId, "")
                }
            },
            onCategoryLongPress = { category -> beginCategorySelection(category) },
            onClearCategorySelection = { clearCategorySelection() },
            onRemoveSelectedCategories = { requestCategoryRemoval() },
            onPlayItem = { item ->
                scope.launch {
                    running = true
                    if (item.mode == BrowseMode.SERIES && item.command.isBlank()) {
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
                            .onFailure { statusText = it.message ?: "Unable to open series" }
                    } else {
                        val preference = playbackActions.loadPlayerPreference()
                        if (preference.rememberForFutureStreams && preference.selectedPlayer != AndroidPlayerPreference.ASK_EVERY_TIME) {
                            runCatching { playbackActions.playBrowseItem(item, preference.selectedPlayer, false) }
                                .onSuccess { statusText = it.message }
                                .onFailure { statusText = it.message ?: "Unable to open stream" }
                        } else {
                            playerChoices = playbackActions.playerChoices()
                            pendingPlayback = PendingPlayback.Browse(item)
                        }
                    }
                    running = false
                }
            },
            onToggleBookmark = { item ->
                scope.launch {
                    running = true
                    runCatching { browseActions.toggleBookmark(item) }
                        .onSuccess { bookmarked ->
                            statusText = if (bookmarked) "Bookmarked ${item.name}" else "Removed bookmark"
                            reload()
                        }
                        .onFailure { statusText = it.message ?: "Unable to update bookmark" }
                    running = false
                }
            },
            modifier = modifier
        )
        PlaybackPickerDialog(
            pendingPlayback = pendingPlayback,
            playerChoices = playerChoices,
            playerIconRenderer = playerIconRenderer,
            wideLayout = true,
            onDismiss = { pendingPlayback = null },
            onInstall = { choice ->
                pendingPlayback = null
                scope.launch {
                    running = true
                    runCatching { playbackActions.openPlayerInstall(choice) }
                        .onSuccess { statusText = "Opening ${choice.label} in Google Play" }
                        .onFailure { statusText = it.message ?: "Unable to open Google Play" }
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
                        .onFailure { statusText = it.message ?: "Unable to open stream" }
                    running = false
                }
            }
        )
        return
    }

        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val compactChrome = maxWidth > maxHeight
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = DeepNightBackground,
                topBar = {
                    if (!compactChrome) {
                        CenterAlignedTopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = DeepNightSurface,
                                titleContentColor = DeepNightText,
                                navigationIconContentColor = DeepNightPrimary,
                                actionIconContentColor = DeepNightPrimary
                            ),
                            navigationIcon = {
                                if (showingChannelList || onBackToAccounts != null) {
                                    IconButton(
                                        modifier = Modifier.semantics {
                                            contentDescription = if (showingChannelList) "Back to categories" else "Back to accounts"
                                        },
                                        onClick = {
                                            if (showingChannelList) {
                                                backToCategories()
                                            } else {
                                                onBackToAccounts?.invoke()
                                            }
                                        }
                                    ) {
                                        Text("←", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            },
                            title = {
                                Text(
                                    screenTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            actions = {
                                val searchDescription = if (selectedCategoryRowId == null) "Search categories" else "Search channels"
                                IconButton(
                                    modifier = Modifier.semantics { contentDescription = searchDescription },
                                    onClick = { toggleCompactSearch() }
                                ) {
                                    Text(if (searchVisible) "X" else "⌕", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                    }
                }
            ) { padding ->
                val contentPadding = if (compactChrome) 4.dp else 12.dp
                val verticalGap = if (compactChrome) 4.dp else 10.dp
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(contentPadding),
                    verticalArrangement = Arrangement.spacedBy(verticalGap)
                ) {
                    if (compactChrome) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (showingChannelList || onBackToAccounts != null) {
                                item {
                                    CompactChromeIcon("←", if (showingChannelList) "Back to categories" else "Back to accounts") {
                                        if (showingChannelList) {
                                            backToCategories()
                                        } else {
                                            onBackToAccounts?.invoke()
                                        }
                                    }
                                }
                            }
                            item {
                                CompactChromeIcon(
                                    if (searchVisible) "X" else "⌕",
                                    if (selectedCategoryRowId == null) "Search categories" else "Search channels"
                                ) {
                                    toggleCompactSearch()
                                }
                            }
                            if (showAccountSelector) {
                                items(snapshot.accounts, key = { it.id }) { account ->
                                    FilterChip(
                                        selected = snapshot.selectedAccountId == account.id,
                                        onClick = {
                                            clearCategorySelection()
                                            selectedCategoryRowId = null
                                            channelQuery = ""
                                            val nextMode = if (mode in account.type.browseModesForAccount()) mode else BrowseMode.LIVE
                                            mode = nextMode
                                            reload(account.id, null, "", nextMode)
                                        },
                                        label = { Text(account.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    )
                                }
                            }
                            if (!showingChannelList && visibleModes.size > 1) {
                                items(visibleModes, key = { it.name }) { entry ->
                                    FilterChip(
                                        selected = mode == entry,
                                        onClick = {
                                            clearCategorySelection()
                                            mode = entry
                                            selectedCategoryRowId = null
                                            channelQuery = ""
                                            reload(snapshot.selectedAccountId, null, "", entry)
                                        },
                                        label = { Text(entry.displayLabel()) }
                                    )
                                }
                            }
                        }
                } else {
                    if (showAccountSelector) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(snapshot.accounts, key = { it.id }) { account ->
                                FilterChip(
                                    selected = snapshot.selectedAccountId == account.id,
                                    onClick = {
                                        clearCategorySelection()
                                        selectedCategoryRowId = null
                                        channelQuery = ""
                                        val nextMode = if (mode in account.type.browseModesForAccount()) mode else BrowseMode.LIVE
                                        mode = nextMode
                                        reload(account.id, null, "", nextMode)
                                    },
                                    label = { Text(account.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                            }
                        }
                    }
                    if (!showingChannelList && visibleModes.size > 1) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(visibleModes, key = { it.name }) { entry ->
                                FilterChip(
                                    selected = mode == entry,
                                    onClick = {
                                        clearCategorySelection()
                                        mode = entry
                                        selectedCategoryRowId = null
                                        channelQuery = ""
                                        reload(snapshot.selectedAccountId, null, "", entry)
                                    },
                                    label = { Text(entry.displayLabel()) }
                                )
                            }
                        }
                    }
                }
                if (searchVisible) {
                    CompactOutlinedTextField(
                        value = if (selectedCategoryRowId == null) categoryQuery else channelQuery,
                        onValueChange = {
                            if (selectedCategoryRowId == null) {
                                categoryQuery = it
                            } else {
                                channelQuery = it
                                reload(snapshot.selectedAccountId, selectedCategoryRowId, it)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = if (selectedCategoryRowId == null) "Search categories" else "Search channels"
                    )
                }
                AnimatedContent(
                    targetState = selectedCategoryRowId,
                    modifier = Modifier.weight(1f),
                    label = "channel-list"
                ) { categoryId ->
                    if (categoryId == null) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (categorySelectionMode) {
                                item {
                                    CategorySelectionToolbar(
                                        selectedCount = selectedCategoryCount,
                                        onRemove = { requestCategoryRemoval() },
                                        onCancel = { clearCategorySelection() }
                                    )
                                }
                            }
                            if (visibleCategories.isEmpty()) {
                                item {
                                    EmptyState(
                                        title = when {
                                            snapshot.accounts.isEmpty() -> "No accounts"
                                            snapshot.categories.isEmpty() -> "No ${mode.displayLabel()} categories"
                                            categoryQuery.isNotBlank() -> "No categories match"
                                            else -> "No ${mode.displayLabel()} categories"
                                        },
                                        detail = when {
                                            snapshot.accounts.isEmpty() -> "Add an account or pull data from desktop sync."
                                            snapshot.categories.isEmpty() -> "Refresh this account cache after adding or syncing it."
                                            else -> "Try a shorter search term."
                                        }
                                    )
                                }
                            }
                            items(visibleCategories, key = { it.rowId }) { category ->
                                CategoryListRow(
                                    category = category,
                                    selected = category.rowId in selectedCategoryIds,
                                    selectionMode = categorySelectionMode,
                                    onClick = {
                                        if (categorySelectionMode) {
                                            toggleCategorySelection(category)
                                        } else {
                                            selectedCategoryRowId = category.rowId
                                            channelQuery = ""
                                            reload(snapshot.selectedAccountId, category.rowId, "")
                                        }
                                    },
                                    onLongClick = { beginCategorySelection(category) }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (snapshot.items.isEmpty()) {
                                item {
                                    EmptyState(
                                        title = when {
                                            snapshot.accounts.isEmpty() -> "No accounts"
                                            snapshot.categories.isEmpty() -> "No ${mode.displayLabel()} categories"
                                            channelQuery.isNotBlank() -> "No channel matches"
                                            else -> "No ${mode.displayLabel()} items"
                                        },
                                        detail = when {
                                            snapshot.accounts.isEmpty() -> "Add an account or pull data from desktop sync."
                                            snapshot.categories.isEmpty() -> "Refresh this account cache after adding or syncing it."
                                            channelQuery.isNotBlank() -> "Try a shorter search term."
                                            else -> "This category has no cached items."
                                        }
                                    )
                                }
                            }
                            items(snapshot.items, key = { "${it.mode}-${it.rowId}-${it.categoryRowId}" }) { item ->
                                BrowseItemRow(
                                    item = item,
                                    showThumbnail = showThumbnails,
                                    logoRenderer = logoRenderer,
                                    onPlay = {
                                        scope.launch {
                                            running = true
                                            if (item.mode == BrowseMode.SERIES && item.command.isBlank()) {
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
                                                    .onFailure { statusText = it.message ?: "Unable to open series" }
                                            } else {
                                                val preference = playbackActions.loadPlayerPreference()
                                                if (preference.rememberForFutureStreams && preference.selectedPlayer != AndroidPlayerPreference.ASK_EVERY_TIME) {
                                                    runCatching { playbackActions.playBrowseItem(item, preference.selectedPlayer, false) }
                                                        .onSuccess { statusText = it.message }
                                                        .onFailure { statusText = it.message ?: "Unable to open stream" }
                                                } else {
                                                    playerChoices = playbackActions.playerChoices()
                                                    pendingPlayback = PendingPlayback.Browse(item)
                                                }
                                            }
                                            running = false
                                        }
                                    },
                                    onToggleBookmark = {
                                        scope.launch {
                                            running = true
                                            runCatching { browseActions.toggleBookmark(item) }
                                                .onSuccess { bookmarked ->
                                                    statusText = if (bookmarked) "Bookmarked ${item.name}" else "Removed bookmark"
                                                    reload()
                                                }
                                                .onFailure { statusText = it.message ?: "Unable to update bookmark" }
                                            running = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                if (running) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (!compactChrome) {
                    Text(statusText, color = DeepNightMutedText, style = MaterialTheme.typography.bodySmall)
                }
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
                    .onFailure { statusText = it.message ?: "Unable to open Google Play" }
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
                    .onFailure { statusText = it.message ?: "Unable to open stream" }
                running = false
            }
        }
    )
}

@Composable
private fun WideChannelsContent(
    snapshot: MobileBrowseSnapshot,
    visibleCategories: List<MobileBrowseCategory>,
    visibleModes: List<BrowseMode>,
    mode: BrowseMode,
    selectedCategoryRowId: Long?,
    screenTitle: String,
    categoryQuery: String,
    channelQuery: String,
    showAccountSelector: Boolean,
    showThumbnails: Boolean,
    running: Boolean,
    statusText: String,
    logoRenderer: LogoRenderer,
    searchVisible: Boolean,
    categorySelectionMode: Boolean,
    selectedCategoryIds: Set<Long>,
    selectedCategoryCount: Int,
    showBack: Boolean,
    onBack: () -> Unit,
    onCategoryQueryChange: (String) -> Unit,
    onChannelQueryChange: (String) -> Unit,
    onAccountSelect: (BrowseAccountOption) -> Unit,
    onModeSelect: (BrowseMode) -> Unit,
    onCategorySelect: (MobileBrowseCategory) -> Unit,
    onCategoryLongPress: (MobileBrowseCategory) -> Unit,
    onClearCategorySelection: () -> Unit,
    onRemoveSelectedCategories: () -> Unit,
    onPlayItem: (MobileBrowseItem) -> Unit,
    onToggleBookmark: (MobileBrowseItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val compactWide = LocalWidePhoneLayout.current
    val outerPadding = if (compactWide) 6.dp else 12.dp
    val gap = if (compactWide) 8.dp else 12.dp
    val sideWidth = if (compactWide) 224.dp else 300.dp
    val itemColumns = if (compactWide) 1 else 2
    Column(modifier = modifier.fillMaxSize()) {
        if (!compactWide) {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepNightSurface,
                    titleContentColor = DeepNightText,
                    navigationIconContentColor = DeepNightPrimary,
                    actionIconContentColor = DeepNightPrimary
                ),
                navigationIcon = {
                    if (showBack) {
                        OutlinedButton(onClick = onBack) {
                            Text("Back")
                        }
                    }
                },
                title = {
                    Text(
                        screenTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(outerPadding),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(sideWidth),
                shape = RoundedCornerShape(14.dp),
                color = DeepNightSurface,
                contentColor = DeepNightText
            ) {
                Column(
                    modifier = Modifier.padding(if (compactWide) 8.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compactWide) 8.dp else 10.dp)
                ) {
                    if (compactWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (showBack) {
                                OutlinedButton(onClick = onBack) {
                                    Text("Back")
                                }
                            }
                            Text(
                                screenTitle,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (showAccountSelector) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(snapshot.accounts, key = { it.id }) { account ->
                                FilterChip(
                                    selected = snapshot.selectedAccountId == account.id,
                                    onClick = { onAccountSelect(account) },
                                    label = { Text(account.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                            }
                        }
                    }
                    if (visibleModes.size > 1) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(visibleModes, key = { it.name }) { entry ->
                                FilterChip(
                                    selected = mode == entry,
                                    onClick = { onModeSelect(entry) },
                                    label = { Text(entry.displayLabel()) }
                                )
                            }
                        }
                    }
                    if (searchVisible) {
                        CompactOutlinedTextField(
                            value = categoryQuery,
                            onValueChange = onCategoryQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = "Search categories"
                        )
                    }
                    if (categorySelectionMode) {
                        CategorySelectionToolbar(
                            selectedCount = selectedCategoryCount,
                            onRemove = onRemoveSelectedCategories,
                            onCancel = onClearCategorySelection
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (visibleCategories.isEmpty()) {
                            item {
                                EmptyState(
                                    title = when {
                                        snapshot.accounts.isEmpty() -> "No accounts"
                                        snapshot.categories.isEmpty() -> "No ${mode.displayLabel()} categories"
                                        categoryQuery.isNotBlank() -> "No categories match"
                                        else -> "No ${mode.displayLabel()} categories"
                                    },
                                    detail = when {
                                        snapshot.accounts.isEmpty() -> "Add an account or pull data from desktop sync."
                                        snapshot.categories.isEmpty() -> "Refresh this account cache after adding or syncing it."
                                        else -> "Try a shorter search term."
                                    }
                                )
                            }
                        }
                        items(visibleCategories, key = { it.rowId }) { category ->
                            CategoryListRow(
                                category = category,
                                selected = category.rowId in selectedCategoryIds,
                                selectionMode = categorySelectionMode,
                                onClick = { onCategorySelect(category) },
                                onLongClick = { onCategoryLongPress(category) }
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (searchVisible) {
                    CompactOutlinedTextField(
                        value = channelQuery,
                        onValueChange = onChannelQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = "Search channels"
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (selectedCategoryRowId == null) {
                        item {
                            EmptyState(
                                title = "Select a category",
                                detail = "Categories stay visible on wide screens while items open here."
                            )
                        }
                    } else if (snapshot.items.isEmpty()) {
                        item {
                            EmptyState(
                                title = when {
                                    snapshot.accounts.isEmpty() -> "No accounts"
                                    snapshot.categories.isEmpty() -> "No ${mode.displayLabel()} categories"
                                    channelQuery.isNotBlank() -> "No channel matches"
                                    else -> "No ${mode.displayLabel()} items"
                                },
                                detail = when {
                                    snapshot.accounts.isEmpty() -> "Add an account or pull data from desktop sync."
                                    snapshot.categories.isEmpty() -> "Refresh this account cache after adding or syncing it."
                                    channelQuery.isNotBlank() -> "Try a shorter search term."
                                    else -> "This category has no cached items."
                                }
                            )
                        }
                    }
                    items(snapshot.items.chunked(itemColumns)) { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowItems.forEach { item ->
                                Box(modifier = Modifier.weight(1f)) {
                                    BrowseItemRow(
                                        item = item,
                                        showThumbnail = showThumbnails,
                                        logoRenderer = logoRenderer,
                                        onPlay = { onPlayItem(item) },
                                        onToggleBookmark = { onToggleBookmark(item) }
                                    )
                                }
                            }
                            repeat(itemColumns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (running) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(statusText, color = DeepNightMutedText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
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
private fun CompactModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .defaultMinSize(minHeight = 40.dp)
            .background(if (selected) DeepNightPrimary else DeepNightSurfaceHighest)
            .clickable(enabled = !selected, onClick = onClick)
            .semantics { contentDescription = "Show $label channels" }
            .padding(horizontal = 7.dp, vertical = 10.dp),
        color = if (selected) MaterialTheme.colorScheme.onPrimary else DeepNightText,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1
    )
}

@Composable
private fun SelectableChip(label: String, selected: Boolean, description: String, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .defaultMinSize(minHeight = 40.dp)
            .background(if (selected) DeepNightPrimary else DeepNightSurfaceHighest)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        color = if (selected) MaterialTheme.colorScheme.onPrimary else DeepNightText,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
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
        DeepNightPrimary.copy(alpha = 0.24f)
    } else {
        DeepNightSurfaceHigh
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() }
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                category.title,
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
    }
}

@Composable
private fun CategoryRail(
    categories: List<MobileBrowseCategory>,
    selectedCategoryRowId: Long?,
    onSelect: (MobileBrowseCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (categories.isEmpty()) {
            item {
                EmptyState(
                    title = "No categories",
                    detail = "Refresh cache to load categories.",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        items(categories, key = { it.rowId }) { category ->
            CategoryPill(
                name = "${category.title} ${category.itemCount}",
                selected = category.rowId == selectedCategoryRowId,
                onClick = { onSelect(category) }
            )
        }
    }
}

@Composable
private fun CategoryPill(name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else DeepNightSurfaceHigh)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        TextButton(onClick = onClick) {
            Text(name, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BrowseItemRow(
    item: MobileBrowseItem,
    showThumbnail: Boolean,
    logoRenderer: LogoRenderer,
    onPlay: () -> Unit,
    onToggleBookmark: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clickable(enabled = item.mode == BrowseMode.SERIES || item.command.isNotBlank(), onClick = onPlay),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = DeepNightSurfaceHigh)
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                ChannelLogo(
                    label = item.number.ifBlank { item.name.take(2).uppercase() },
                    logo = item.logo,
                    showThumbnail = showThumbnail,
                    contentDescription = "Logo ${item.name}",
                    logoRenderer = logoRenderer
                )
            },
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Text(
                        item.name,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            supportingContent = {
                Text(item.subtitle(), color = DeepNightMutedText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        )
    }
}

@Composable
private fun ChannelLogo(
    label: String,
    logo: String,
    showThumbnail: Boolean,
    contentDescription: String,
    logoRenderer: LogoRenderer
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
        if (showThumbnail && logo.isNotBlank()) {
            logoRenderer(
                logo,
                contentDescription,
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            )
        } else {
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

@Composable
private fun FavouriteStar(
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    compact: Boolean = false
) {
    val touchModifier = if (compact) {
        Modifier.size(34.dp)
    } else {
        Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
    }
    Text(
        text = if (selected) "★" else "☆",
        modifier = touchModifier
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = if (compact) 4.dp else 10.dp, vertical = if (compact) 2.dp else 6.dp),
        color = if (selected) Color(0xFFFFD54F) else DeepNightText,
        fontSize = if (compact) 22.sp else 28.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1
    )
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
    categoryPanelVisible: Boolean,
    onCategoryPanelVisibleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<MobileBookmarkCategory>>(emptyList()) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var bookmarks by remember { mutableStateOf<List<MobileBookmark>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Loading") }
    var running by remember { mutableStateOf(false) }
    var pendingPlayback by remember { mutableStateOf<PendingPlayback?>(null) }
    var playerChoices by remember { mutableStateOf<List<PlayerChoice>>(emptyList()) }

    fun activeSearchVisible(): Boolean = if (wideLayout) wideSearchVisible else searchVisible
    fun activeBookmarkQuery(): String = if (activeSearchVisible()) query else ""
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

    fun reload(categoryId: String? = selectedCategoryId, search: String = activeBookmarkQuery()) {
        scope.launch {
            running = true
            runCatching {
                categories = browseActions.listBookmarkCategories()
                browseActions.listBookmarks(search, categoryId)
            }
                .onSuccess {
                    bookmarks = it
                    statusText = if (it.isEmpty()) "No bookmarks" else "${it.size} bookmarks"
                }
                .onFailure { statusText = it.message ?: "Unable to load bookmarks" }
            running = false
        }
    }

    LaunchedEffect(browseActions, refreshSignal) {
        reload()
    }
    LaunchedEffect(wideLayout, wideSearchVisible, searchVisible) {
        if (!activeSearchVisible() && query.isNotBlank()) {
            query = ""
            reload(search = "")
        }
    }

    if (wideLayout) {
        WideBookmarksContent(
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            bookmarks = bookmarks,
            query = query,
            running = running,
            statusText = statusText,
            showThumbnails = showThumbnails,
            logoRenderer = logoRenderer,
            searchVisible = wideSearchVisible,
            categoryPanelVisible = categoryPanelVisible,
            onQueryChange = {
                query = it
                reload(search = it)
            },
            onCategorySelect = { category ->
                selectedCategoryId = category.id
                reload(category.id)
            },
            onToggleCategoryPanel = { onCategoryPanelVisibleChange(!categoryPanelVisible) },
            onPlay = { bookmark ->
                scope.launch {
                    running = true
                    val preference = playbackActions.loadPlayerPreference()
                    if (preference.rememberForFutureStreams && preference.selectedPlayer != AndroidPlayerPreference.ASK_EVERY_TIME) {
                        runCatching { playbackActions.playBookmark(bookmark, preference.selectedPlayer, false) }
                            .onSuccess {
                                statusText = it.message
                                if (it.launched) reload()
                            }
                            .onFailure { statusText = it.message ?: "Unable to open bookmark" }
                    } else {
                        playerChoices = playbackActions.playerChoices()
                        pendingPlayback = PendingPlayback.Bookmark(bookmark)
                    }
                    running = false
                }
            },
            onRemove = { bookmark ->
                removeBookmarkFromActiveList(bookmark)
            },
            modifier = modifier
        )
        PlaybackPickerDialog(
            pendingPlayback = pendingPlayback,
            playerChoices = playerChoices,
            playerIconRenderer = playerIconRenderer,
            wideLayout = true,
            onDismiss = { pendingPlayback = null },
            onInstall = { choice ->
                pendingPlayback = null
                scope.launch {
                    running = true
                    runCatching { playbackActions.openPlayerInstall(choice) }
                        .onSuccess { statusText = "Opening ${choice.label} in Google Play" }
                        .onFailure { statusText = it.message ?: "Unable to open Google Play" }
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
                        .onFailure { statusText = it.message ?: "Unable to open bookmark" }
                    running = false
                }
            }
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "bookmark-search-toggle") {
                    IconButton(
                        modifier = Modifier.semantics {
                            contentDescription = if (searchVisible) "Hide bookmark search" else "Show bookmark search"
                        },
                        onClick = {
                            searchVisible = !searchVisible
                            if (!searchVisible && query.isNotBlank()) {
                                query = ""
                                reload(search = "")
                            }
                        }
                    ) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            tint = if (searchVisible) DeepNightAccent else DeepNightPrimary
                        )
                    }
                }
                items(categories, key = { it.id ?: it.name }) { category ->
                    FilterChip(
                        selected = selectedCategoryId == category.id,
                        onClick = {
                            selectedCategoryId = category.id
                            reload(category.id)
                        },
                        label = { Text(category.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }
        if (searchVisible) {
            item(key = "bookmark-search-field") {
                CompactOutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        reload(search = it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Search bookmarks"
                )
            }
        }
        if (bookmarks.isEmpty()) {
            item {
                EmptyState(
                    title = bookmarkEmptyTitle(query, selectedCategoryId),
                    detail = bookmarkEmptyDetail(query, selectedCategoryId)
                )
            }
        }
        items(bookmarks, key = { it.rowId }) { bookmark ->
            BookmarkRow(
                bookmark = bookmark,
                recentlyPlayed = selectedCategoryId == RECENTLY_PLAYED_BOOKMARKS_CATEGORY_ID,
                showThumbnail = showThumbnails,
                logoRenderer = logoRenderer,
                onPlay = {
                    scope.launch {
                        running = true
                        val preference = playbackActions.loadPlayerPreference()
                        if (preference.rememberForFutureStreams && preference.selectedPlayer != AndroidPlayerPreference.ASK_EVERY_TIME) {
                            runCatching { playbackActions.playBookmark(bookmark, preference.selectedPlayer, false) }
                                .onSuccess {
                                    statusText = it.message
                                    if (it.launched) reload()
                                }
                                .onFailure { statusText = it.message ?: "Unable to open bookmark" }
                        } else {
                            playerChoices = playbackActions.playerChoices()
                            pendingPlayback = PendingPlayback.Bookmark(bookmark)
                        }
                        running = false
                    }
                },
                onRemove = {
                    removeBookmarkFromActiveList(bookmark)
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
                    .onFailure { statusText = it.message ?: "Unable to open Google Play" }
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
                    .onFailure { statusText = it.message ?: "Unable to open bookmark" }
                running = false
            }
        }
    )
}

private fun bookmarkEmptyTitle(query: String, selectedCategoryId: String?): String =
    when {
        query.isNotBlank() -> "No bookmark matches"
        selectedCategoryId == RECENTLY_PLAYED_BOOKMARKS_CATEGORY_ID -> "No recently played channels"
        else -> "No bookmarks"
    }

private fun bookmarkEmptyDetail(query: String, selectedCategoryId: String?): String =
    when {
        query.isNotBlank() -> "Try a shorter search term."
        selectedCategoryId == RECENTLY_PLAYED_BOOKMARKS_CATEGORY_ID -> "Play bookmarks to build this list."
        else -> "Save channels from the Channels screen."
    }

@Composable
private fun BookmarkRow(
    bookmark: MobileBookmark,
    recentlyPlayed: Boolean,
    showThumbnail: Boolean,
    logoRenderer: LogoRenderer,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clickable(enabled = bookmark.command.isNotBlank(), onClick = onPlay),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = DeepNightSurfaceHigh)
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                ChannelLogo(
                    label = bookmark.channelName.take(2).uppercase(),
                    logo = bookmark.logo,
                    showThumbnail = showThumbnail,
                    contentDescription = "Logo ${bookmark.channelName}",
                    logoRenderer = logoRenderer
                )
            },
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (recentlyPlayed) {
                        IconButton(
                            modifier = Modifier
                                .size(34.dp)
                                .semantics { contentDescription = "Clear recently played ${bookmark.channelName}" },
                            onClick = onRemove
                        ) {
                            Icon(
                                Icons.Outlined.DeleteSweep,
                                contentDescription = null,
                                tint = DeepNightAccent
                            )
                        }
                    } else {
                        FavouriteStar(
                            selected = true,
                            contentDescription = "Remove favourite ${bookmark.channelName}",
                            compact = true,
                            onClick = onRemove
                        )
                    }
                    Text(
                        bookmark.channelName,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            supportingContent = {
                Text(
                    bookmark.accountName,
                    color = DeepNightMutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
    }
}

@Composable
private fun WideBookmarksContent(
    categories: List<MobileBookmarkCategory>,
    selectedCategoryId: String?,
    bookmarks: List<MobileBookmark>,
    query: String,
    running: Boolean,
    statusText: String,
    showThumbnails: Boolean,
    logoRenderer: LogoRenderer,
    searchVisible: Boolean,
    categoryPanelVisible: Boolean,
    onQueryChange: (String) -> Unit,
    onCategorySelect: (MobileBookmarkCategory) -> Unit,
    onToggleCategoryPanel: () -> Unit,
    onPlay: (MobileBookmark) -> Unit,
    onRemove: (MobileBookmark) -> Unit,
    modifier: Modifier = Modifier
) {
    val compactWide = LocalWidePhoneLayout.current
    val outerPadding = if (compactWide) 6.dp else 12.dp
    val gap = if (compactWide) 8.dp else 12.dp
    val sideWidth = if (compactWide) 216.dp else 280.dp
    val itemColumns = if (compactWide) 1 else 2
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(outerPadding),
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        if (categoryPanelVisible) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(sideWidth),
                shape = RoundedCornerShape(14.dp),
                color = DeepNightSurface,
                contentColor = DeepNightText
            ) {
                Column(
                    modifier = Modifier.padding(if (compactWide) 8.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compactWide) 8.dp else 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Bookmarks",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(onClick = onToggleCategoryPanel) {
                            Text("Hide")
                        }
                    }
                    if (searchVisible) {
                        CompactOutlinedTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = "Search bookmarks"
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (categories.isEmpty()) {
                            item {
                                EmptyState(
                                    title = "No bookmark categories",
                                    detail = "Saved channels appear here.",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        items(categories, key = { it.id ?: it.name }) { category ->
                            FilterChip(
                                modifier = Modifier.fillMaxWidth(),
                                selected = selectedCategoryId == category.id,
                                onClick = { onCategorySelect(category) },
                                label = { Text(category.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                    if (running) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(statusText, color = DeepNightMutedText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(if (compactWide) 8.dp else 10.dp)
        ) {
            if (!categoryPanelVisible || searchVisible) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!categoryPanelVisible) {
                        OutlinedButton(onClick = onToggleCategoryPanel) {
                            Text("Categories")
                        }
                    }
                    if (searchVisible && !categoryPanelVisible) {
                        CompactOutlinedTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier.weight(1f),
                            label = "Search bookmarks"
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (compactWide) 8.dp else 10.dp)
            ) {
                if (bookmarks.isEmpty()) {
                    item {
                        EmptyState(
                            title = bookmarkEmptyTitle(query, selectedCategoryId),
                            detail = bookmarkEmptyDetail(query, selectedCategoryId)
                        )
                    }
                }
                items(bookmarks.chunked(itemColumns)) { rowBookmarks ->
                    Row(horizontalArrangement = Arrangement.spacedBy(if (compactWide) 8.dp else 10.dp)) {
                        rowBookmarks.forEach { bookmark ->
                            Box(modifier = Modifier.weight(1f)) {
                                BookmarkRow(
                                    bookmark = bookmark,
                                    recentlyPlayed = selectedCategoryId == RECENTLY_PLAYED_BOOKMARKS_CATEGORY_ID,
                                    showThumbnail = showThumbnails,
                                    logoRenderer = logoRenderer,
                                    onPlay = { onPlay(bookmark) },
                                    onRemove = { onRemove(bookmark) }
                                )
                            }
                        }
                        repeat(itemColumns - rowBookmarks.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            if (!categoryPanelVisible) {
                if (running) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(statusText, color = DeepNightMutedText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WideWatchingNowContent(
    items: List<MobileWatchingNowItem>,
    query: String,
    filter: WatchingNowFilter,
    running: Boolean,
    statusText: String,
    showThumbnails: Boolean,
    logoRenderer: LogoRenderer,
    searchVisible: Boolean,
    onQueryChange: (String) -> Unit,
    onFilterSelect: (WatchingNowFilter) -> Unit,
    onOpen: (MobileWatchingNowItem) -> Unit,
    onRemove: (MobileWatchingNowItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val compactWide = LocalWidePhoneLayout.current
    val outerPadding = if (compactWide) 6.dp else 12.dp
    val gap = if (compactWide) 6.dp else 8.dp
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(outerPadding),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        if (searchVisible) {
            CompactOutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = "Search watching now"
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(WatchingNowFilter.entries, key = { it.name }) { entry ->
                FilterChip(
                    selected = filter == entry,
                    onClick = { onFilterSelect(entry) },
                    label = { Text(entry.label) }
                )
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            if (items.isEmpty()) {
                item {
                    val filtered = filter != WatchingNowFilter.ALL
                    EmptyState(
                        title = when {
                            filtered -> "No ${filter.label} entries"
                            query.isBlank() -> "Nothing to resume"
                            else -> "No resume matches"
                        },
                        detail = if (query.isBlank()) "VOD and series appear here after playback starts." else "Try a shorter search term."
                    )
                }
            }
            items(items, key = { it.stableWatchingNowKey() }) { item ->
                WatchingNowRow(
                    item = item,
                    showThumbnail = showThumbnails,
                    logoRenderer = logoRenderer,
                    wideLayout = true,
                    onOpen = { onOpen(item) },
                    onRemove = { onRemove(item) }
                )
            }
        }
        if (running) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Text(statusText, color = DeepNightMutedText, style = MaterialTheme.typography.bodySmall)
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
    detailsPanelVisible: Boolean,
    onDetailsPanelVisibleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<MobileWatchingNowItem>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Loading") }
    var running by remember { mutableStateOf(false) }
    var pendingPlayback by remember { mutableStateOf<PendingPlayback?>(null) }
    var playerChoices by remember { mutableStateOf<List<PlayerChoice>>(emptyList()) }
    var selectedSeries by remember { mutableStateOf<MobileWatchingNowItem?>(null) }
    var seriesEpisodes by remember { mutableStateOf<List<MobileWatchingNowEpisode>>(emptyList()) }
    var pendingRemoveWatchingNow by remember { mutableStateOf<MobileWatchingNowItem?>(null) }
    var pendingEpisodeMenu by remember { mutableStateOf<MobileWatchingNowEpisode?>(null) }
    var wideWatchingFilter by rememberSaveable { mutableStateOf(WatchingNowFilter.ALL) }

    fun activeWatchingQuery(): String = if (!wideLayout || wideSearchVisible) query else ""

    fun reload(search: String = activeWatchingQuery()) {
        scope.launch {
            running = true
            runCatching { browseActions.listWatchingNow(search) }
                .onSuccess { loaded ->
                    items = loaded
                    statusText = if (loaded.isEmpty()) "Nothing to resume" else "${loaded.size} resume entries"
                    val enriched = loaded.map { item ->
                        runCatching { browseActions.enrichWatchingNowItem(item) }.getOrDefault(item)
                    }
                    items = enriched
                }
                .onFailure { statusText = it.message ?: "Unable to load watching-now" }
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
                    .onFailure { statusText = it.message ?: "Unable to resume" }
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
                .onFailure { statusText = it.message ?: "Unable to open series" }
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
                .onFailure { statusText = it.message ?: "Unable to remove item" }
            running = false
        }
    }

    LaunchedEffect(browseActions) {
        reload()
    }
    LaunchedEffect(wideLayout, wideSearchVisible) {
        if (wideLayout && !wideSearchVisible && query.isNotBlank()) {
            query = ""
            reload("")
        }
    }
    LaunchedEffect(resumeSignal) {
        val currentSeries = selectedSeries
        if (currentSeries != null) {
            openWatchingNow(currentSeries)
        } else {
            reload()
        }
    }

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
                        .onFailure { statusText = it.message ?: "Unable to mark Watching Now" }
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
                        .onFailure { statusText = it.message ?: "Unable to remove Watching Now flag" }
                    running = false
                }
            },
            onInstall = { choice ->
                pendingEpisodeMenu = null
                scope.launch {
                    running = true
                    runCatching { playbackActions.openPlayerInstall(choice) }
                        .onSuccess { statusText = "Opening ${choice.label} in Google Play" }
                        .onFailure { statusText = it.message ?: "Unable to open Google Play" }
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
                        .onFailure { statusText = it.message ?: "Unable to open episode" }
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
                        .onFailure { statusText = it.message ?: "Unable to open Google Play" }
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
                        .onFailure { statusText = it.message ?: "Unable to resume" }
                    running = false
                }
            }
        )
        return
    }

    if (wideLayout) {
        val visibleItems = remember(items, wideWatchingFilter) {
            items.filter(wideWatchingFilter::matches)
        }
        WideWatchingNowContent(
            items = visibleItems,
            query = query,
            filter = wideWatchingFilter,
            running = running,
            statusText = if (wideWatchingFilter == WatchingNowFilter.ALL) {
                statusText
            } else {
                "${visibleItems.size} ${wideWatchingFilter.label} resume entries"
            },
            showThumbnails = showThumbnails,
            logoRenderer = logoRenderer,
            searchVisible = wideSearchVisible,
            onQueryChange = {
                query = it
                reload(it)
            },
            onFilterSelect = { wideWatchingFilter = it },
            onOpen = { openWatchingNow(it) },
            onRemove = { pendingRemoveWatchingNow = it },
            modifier = modifier
        )
        PlaybackPickerDialog(
            pendingPlayback = pendingPlayback,
            playerChoices = playerChoices,
            playerIconRenderer = playerIconRenderer,
            wideLayout = true,
            onDismiss = { pendingPlayback = null },
            onInstall = { choice ->
                pendingPlayback = null
                scope.launch {
                    running = true
                    runCatching { playbackActions.openPlayerInstall(choice) }
                        .onSuccess { statusText = "Opening ${choice.label} in Google Play" }
                        .onFailure { statusText = it.message ?: "Unable to open Google Play" }
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
                        .onFailure { statusText = it.message ?: "Unable to resume" }
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
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            CompactOutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    reload(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = "Search watching now"
            )
        }
        if (items.isEmpty()) {
            item {
                EmptyState(
                    title = if (query.isBlank()) "Nothing to resume" else "No resume matches",
                    detail = if (query.isBlank()) "VOD and series appear here after playback starts." else "Try a shorter search term."
                )
            }
        }
        items(items, key = { it.stableWatchingNowKey() }) { item ->
            WatchingNowRow(
                item = item,
                showThumbnail = showThumbnails,
                logoRenderer = logoRenderer,
                onOpen = { openWatchingNow(item) },
                onRemove = { pendingRemoveWatchingNow = item }
            )
        }
        item {
            if (running) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(statusText, color = DeepNightMutedText, style = MaterialTheme.typography.bodySmall)
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
                    .onFailure { statusText = it.message ?: "Unable to open Google Play" }
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
                    .onFailure { statusText = it.message ?: "Unable to resume" }
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
    item: MobileWatchingNowItem,
    showThumbnail: Boolean,
    logoRenderer: LogoRenderer,
    wideLayout: Boolean = false,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    if (wideLayout) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .clickable(enabled = item.mode == BrowseMode.SERIES || item.command.isNotBlank(), onClick = onOpen),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = DeepNightSurfaceHigh)
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
                        label = item.title.take(2).uppercase(),
                        logo = item.logo,
                        showThumbnail = showThumbnail,
                        contentDescription = "Logo ${item.title}",
                        logoRenderer = logoRenderer
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FavouriteStar(
                                selected = true,
                                contentDescription = "Remove watching now ${item.title}",
                                compact = true,
                                onClick = onRemove
                            )
                            Text(
                                item.title,
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            listOf(item.mode.displayLabel(), item.subtitle).filter { it.isNotBlank() }.joinToString(" - "),
                            color = DeepNightMutedText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val metadata = item.metadataLine()
                        if (metadata.isNotBlank()) {
                            Text(metadata, color = DeepNightMutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (item.plot.isNotBlank()) {
                    Text(
                        item.plot,
                        color = DeepNightMutedText,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        return
    }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clickable(enabled = item.mode == BrowseMode.SERIES || item.command.isNotBlank(), onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = DeepNightSurfaceHigh)
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                ChannelLogo(
                    label = item.title.take(2).uppercase(),
                    logo = item.logo,
                    showThumbnail = showThumbnail,
                    contentDescription = "Logo ${item.title}",
                    logoRenderer = logoRenderer
                )
            },
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FavouriteStar(
                        selected = true,
                        contentDescription = "Remove watching now ${item.title}",
                        compact = true,
                        onClick = onRemove
                    )
                    Text(
                        item.title,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        listOf(item.mode.displayLabel(), item.subtitle).filter { it.isNotBlank() }.joinToString(" - "),
                        color = DeepNightMutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val metadata = item.metadataLine()
                    if (metadata.isNotBlank()) {
                        Text(metadata, color = DeepNightMutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (item.plot.isNotBlank()) {
                        Text(item.plot, color = DeepNightMutedText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        )
    }
}

@Composable
private fun WideWatchingNowSeriesDetailContent(
    series: MobileWatchingNowItem,
    episodes: List<MobileWatchingNowEpisode>,
    visibleEpisodes: List<MobileWatchingNowEpisode>,
    seasonTabs: List<MobileSeriesSeasonTab>,
    activeSeasonKey: String,
    episodeQuery: String,
    searchVisible: Boolean,
    posterPanelVisible: Boolean,
    listState: LazyListState,
    running: Boolean,
    statusText: String,
    showThumbnail: Boolean,
    logoRenderer: LogoRenderer,
    showRemove: Boolean,
    emptyTitle: String,
    emptyDetail: String,
    onBack: () -> Unit,
    onRemoveSeries: () -> Unit,
    onEpisodeQueryChange: (String) -> Unit,
    onTogglePosterPanel: () -> Unit,
    onSeasonSelect: (MobileSeriesSeasonTab) -> Unit,
    onBingeSeason: (String) -> Unit,
    onPlayEpisode: (MobileWatchingNowEpisode) -> Unit,
    onEpisodeMenu: (MobileWatchingNowEpisode) -> Unit,
    modifier: Modifier = Modifier
) {
    val compactWide = LocalWidePhoneLayout.current
    val outerPadding = if (compactWide) 6.dp else 12.dp
    val gap = if (compactWide) 8.dp else 12.dp
    val sideWidth = if (compactWide) 236.dp else 340.dp
    val summaryScrollState = rememberScrollState()
    val seasonTitle = seasonTabs.firstOrNull { it.key == activeSeasonKey }?.label.orEmpty()
    val activeSeasonHasEpisodes = activeSeasonKey.isNotBlank() &&
        episodes.any { it.seasonTab().key == activeSeasonKey }
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(outerPadding),
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        if (posterPanelVisible) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(sideWidth),
                shape = RoundedCornerShape(14.dp),
                color = DeepNightSurface,
                contentColor = DeepNightText
            ) {
                Column(
                    modifier = Modifier
                        .padding(if (compactWide) 8.dp else 12.dp)
                        .then(if (compactWide) Modifier.verticalScroll(summaryScrollState) else Modifier),
                    verticalArrangement = Arrangement.spacedBy(if (compactWide) 8.dp else 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Details",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(onClick = onTogglePosterPanel) {
                            Text("Hide")
                        }
                    }
                    SeriesMetadataHeader(
                        series = series,
                        logoRenderer = logoRenderer,
                        plotBelowPoster = true
                    )
                    Text(statusText, color = DeepNightMutedText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(if (compactWide) 6.dp else 8.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(if (compactWide) 6.dp else 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            modifier = Modifier.semantics { contentDescription = "Back to series list" },
                            onClick = onBack
                        ) {
                            Text("Back to list")
                        }
                        Text(
                            series.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (showRemove) {
                            FavouriteStar(
                                selected = true,
                                contentDescription = "Remove watching now ${series.title}",
                                compact = true,
                                onClick = onRemoveSeries
                            )
                        }
                        OutlinedButton(onClick = onTogglePosterPanel) {
                            Text(if (posterPanelVisible) "Hide details" else "Details")
                        }
                    }
                    if (searchVisible) {
                        CompactOutlinedTextField(
                            value = episodeQuery,
                            onValueChange = onEpisodeQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = "Search episodes"
                        )
                    }
                    if (seasonTabs.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(seasonTabs, key = { it.key }) { tab ->
                                FilterChip(
                                    selected = tab.key == activeSeasonKey,
                                    onClick = { onSeasonSelect(tab) },
                                    label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                            }
                        }
                    }
                    if (activeSeasonHasEpisodes) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onBingeSeason(activeSeasonKey) }
                        ) {
                            Text("Binge Watch")
                        }
                    }
                    Text(
                        "Season $seasonTitle episodes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (episodes.isEmpty()) {
                item {
                    EmptyState(
                        title = emptyTitle,
                        detail = emptyDetail
                    )
                }
            }
            if (visibleEpisodes.isEmpty() && episodes.isNotEmpty()) {
                item {
                    EmptyState(
                        title = "No episodes",
                        detail = if (episodeQuery.isBlank()) "This season has no cached episodes." else "No episode matches this search."
                    )
                }
            }
            items(visibleEpisodes, key = { it.stableWatchingNowEpisodeKey() }) { episode ->
                WatchingNowEpisodeRow(
                    episode = episode,
                    showThumbnail = showThumbnail,
                    logoRenderer = logoRenderer,
                    wideLayout = true,
                    onPlay = { onPlayEpisode(episode) },
                    onMenu = { onEpisodeMenu(episode) }
                )
            }
            item {
                if (running) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    "Episodes stay beside series metadata on wide screens.",
                    color = DeepNightMutedText,
                    style = MaterialTheme.typography.bodySmall
                )
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
    val watchedSeasonKey = remember(episodes) {
        episodes.firstOrNull { it.isWatched }?.seasonTab()?.key.orEmpty()
    }
    var selectedSeasonKey by rememberSaveable(series.accountId, series.categoryProviderId, series.contentId) {
        mutableStateOf(watchedSeasonKey)
    }
    var seasonPickedByUser by rememberSaveable(series.accountId, series.categoryProviderId, series.contentId) {
        mutableStateOf(false)
    }
    var episodeQuery by rememberSaveable(series.accountId, series.categoryProviderId, series.contentId) {
        mutableStateOf("")
    }
    val activeSeasonKey = seasonTabs.firstOrNull { it.key == selectedSeasonKey }?.key
        ?: seasonTabs.firstOrNull { it.key == watchedSeasonKey }?.key
        ?: seasonTabs.firstOrNull()?.key.orEmpty()
    LaunchedEffect(seasonTabs, watchedSeasonKey, activeSeasonKey, seasonPickedByUser) {
        val preferredSeasonKey = if (!seasonPickedByUser && watchedSeasonKey.isNotBlank()) watchedSeasonKey else activeSeasonKey
        if (preferredSeasonKey != selectedSeasonKey) {
            selectedSeasonKey = preferredSeasonKey
        }
    }
    LaunchedEffect(wideLayout, wideSearchVisible) {
        if (wideLayout && !wideSearchVisible && episodeQuery.isNotBlank()) {
            episodeQuery = ""
        }
    }
    val seasonEpisodes = remember(episodes, activeSeasonKey) {
        if (activeSeasonKey.isBlank()) episodes else episodes.filter { it.seasonTab().key == activeSeasonKey }
    }
    val effectiveEpisodeQuery = if (wideLayout && !wideSearchVisible) "" else episodeQuery
    val visibleEpisodes = remember(seasonEpisodes, effectiveEpisodeQuery) {
        val query = effectiveEpisodeQuery.trim()
        if (query.isBlank()) {
            seasonEpisodes
        } else {
            seasonEpisodes.filter { episode ->
                episode.title.contains(query, ignoreCase = true) ||
                    episode.seriesTitle.contains(query, ignoreCase = true) ||
                    episode.plot.contains(query, ignoreCase = true)
            }
        }
    }
    val listState = rememberLazyListState()
    var lastAutoScrollKey by remember(series.accountId, series.categoryProviderId, series.contentId) {
        mutableStateOf("")
    }
    val hasSeriesMetadata = series.logo.isNotBlank() || series.metadataLine().isNotBlank() || series.plot.isNotBlank() || series.imdbUrl.isNotBlank()
    LaunchedEffect(activeSeasonKey, visibleEpisodes) {
        if (visibleEpisodes.isEmpty() || activeSeasonKey.isBlank()) {
            return@LaunchedEffect
        }
        val targetIndex = visibleEpisodes.indexOfFirst { it.isWatched }.takeIf { it >= 0 } ?: 0
        val target = visibleEpisodes[targetIndex]
        val scrollKey = "$activeSeasonKey-${target.stableWatchingNowEpisodeKey()}-${visibleEpisodes.size}"
        if (scrollKey != lastAutoScrollKey) {
            lastAutoScrollKey = scrollKey
            listState.scrollToItem(targetIndex)
        }
    }

    if (wideLayout) {
        WideWatchingNowSeriesDetailContent(
            series = series,
            episodes = episodes,
            visibleEpisodes = visibleEpisodes,
            seasonTabs = seasonTabs,
            activeSeasonKey = activeSeasonKey,
            episodeQuery = episodeQuery,
            searchVisible = wideSearchVisible,
            posterPanelVisible = detailsPanelVisible,
            listState = listState,
            running = running,
            statusText = statusText,
            showThumbnail = showThumbnail,
            logoRenderer = logoRenderer,
            showRemove = showRemove,
            emptyTitle = emptyTitle,
            emptyDetail = emptyDetail,
            onBack = onBack,
            onRemoveSeries = onRemoveSeries,
            onEpisodeQueryChange = { episodeQuery = it },
            onTogglePosterPanel = { onDetailsPanelVisibleChange(!detailsPanelVisible) },
            onSeasonSelect = { tab ->
                seasonPickedByUser = true
                selectedSeasonKey = tab.key
            },
            onBingeSeason = onBingeSeason,
            onPlayEpisode = onPlayEpisode,
            onEpisodeMenu = onEpisodeMenu,
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onBack) {
                Text("Back")
            }
            Text(
                series.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (showRemove) {
                FavouriteStar(
                    selected = true,
                    contentDescription = "Remove watching now ${series.title}",
                    compact = true,
                    onClick = onRemoveSeries
                )
            }
        }
        if (hasSeriesMetadata) {
            SeriesMetadataHeader(
                series = series,
                logoRenderer = logoRenderer
            )
        }
        if (seasonTabs.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(seasonTabs, key = { it.key }) { tab ->
                    FilterChip(
                        selected = tab.key == activeSeasonKey,
                        onClick = {
                            seasonPickedByUser = true
                            selectedSeasonKey = tab.key
                        },
                        label = {
                            Text(
                                tab.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }
        if (activeSeasonKey.isNotBlank() && visibleEpisodes.isNotEmpty()) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onBingeSeason(activeSeasonKey) }
            ) {
                Text("Binge Watch")
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (episodes.isEmpty()) {
                item {
                    EmptyState(
                        title = emptyTitle,
                        detail = emptyDetail
                    )
                }
            }
            if (visibleEpisodes.isEmpty() && episodes.isNotEmpty()) {
                item {
                    EmptyState(
                        title = "No episodes",
                        detail = "This season has no cached episodes."
                    )
                }
            }
            items(visibleEpisodes, key = { it.stableWatchingNowEpisodeKey() }) { episode ->
                WatchingNowEpisodeRow(
                    episode = episode,
                    showThumbnail = showThumbnail,
                    logoRenderer = logoRenderer,
                    onPlay = { onPlayEpisode(episode) },
                    onMenu = { onEpisodeMenu(episode) }
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
    val textColor = if (isWatching) Color.White else DeepNightText
    val mutedColor = if (isWatching) Color(0xFFE0F7FF) else DeepNightMutedText
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
                containerColor = if (isWatching) Color(0xFF069AC0) else DeepNightSurfaceHigh
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
                            Text(
                                "WATCHING",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFFD700))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                color = Color(0xFF151515),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
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
            containerColor = if (isWatching) Color(0xFF069AC0) else DeepNightSurfaceHigh
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
                        Text(
                            "WATCHING",
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFFD700))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            color = Color(0xFF151515),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
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
                Checkbox(
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
    val uriHandler = LocalUriHandler.current
    fun openDesktopDownload() {
        uriHandler.openUri(DesktopDownloadUrl)
    }

    LaunchedEffect(syncActions) {
        val snapshot = syncActions.loadPreferences()
        val savedHost = snapshot.remoteEndpoint.host.takeUnless { it.isLoopbackHost() }.orEmpty()
        host = savedHost
        portText = if (savedHost.isBlank()) {
            ""
        } else {
            snapshot.remoteEndpoint.port.toString()
        }
        lastSyncText = snapshot.remoteEndpoint.lastSuccessfulSyncEpochSeconds
            ?.let { "Last sync: $it" }
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
                    .onFailure { statusText = it.message ?: "Backup failed" }
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
                    .onFailure { statusText = it.message ?: "Restore failed" }
                running = false
            }
        }
    }

    fun clearRecentlyPlayedBookmarks() {
        scope.launch {
            running = true
            runCatching { browseActions.clearRecentlyPlayedBookmarks() }
                .onSuccess { statusText = "Recently played channels cleared" }
                .onFailure { statusText = it.message ?: "Unable to clear recently played channels" }
            running = false
        }
    }

    val selectedPlayerChoice = remember(selectedPlayer, playerChoices) {
        (listOf(PlayerChoice(AndroidPlayerPreference.ASK_EVERY_TIME, "Ask")) + playerChoices)
            .firstOrNull { it.matchesSelected(selectedPlayer) }
            ?: PlayerChoice(selectedPlayer, selectedPlayer.playerLabel())
    }

    if (wideLayout) {
        WideRemoteSyncContent(
            host = host,
            portText = portText,
            verificationCode = verificationCode,
            statusText = statusText,
            running = running,
            lastSyncText = lastSyncText,
            filters = filters,
            categoryFilterText = categoryFilterText,
            channelFilterText = channelFilterText,
            filterEditorVisible = filterEditorVisible,
            selectedPlayerChoice = selectedPlayerChoice,
            selectedPlayer = selectedPlayer,
            playerIconRenderer = playerIconRenderer,
            onHostChange = { host = it },
            onPortChange = { portText = it.filter(Char::isDigit).take(5) },
            onTest = {
                scope.launch {
                    val port = parsedPort() ?: return@launch
                    running = true
                    statusText = "Connecting"
                    runCatching {
                        syncActions.checkConnection(host.trim(), port)
                    }.onSuccess {
                        statusText = "Connected"
                    }.onFailure { ex ->
                        statusText = ex.message ?: "Connection failed"
                    }
                    running = false
                }
            },
            onPull = {
                scope.launch {
                    val port = parsedPort() ?: return@launch
                    running = true
                    statusText = "Starting"
                    verificationCode = ""
                    runCatching {
                        syncActions.pullFromDesktop(
                            host.trim(),
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
                        statusText = ex.message ?: "Sync failed"
                    }
                    running = false
                }
            },
            onBackup = ::launchBackup,
            onRestore = { confirmRestore = true },
            onClearRecentlyPlayedBookmarks = ::clearRecentlyPlayedBookmarks,
            onThumbnailChange = { enabled ->
                scope.launch {
                    running = true
                    runCatching { filterActions.setEnableThumbnails(enabled) }
                        .onSuccess {
                            filters = filters.copy(enableThumbnails = enabled)
                            onThumbnailSettingChanged(enabled)
                            statusText = if (enabled) "Thumbnails enabled" else "Thumbnails disabled"
                        }
                        .onFailure { statusText = it.message ?: "Unable to update thumbnails" }
                    running = false
                }
            },
            onPlayerClick = { playerSelectorVisible = true },
            onPausedToggle = {
                scope.launch {
                    val paused = !filters.paused
                    running = true
                    runCatching { filterActions.setPaused(paused) }
                        .onSuccess {
                            filters = filters.copy(paused = paused)
                            statusText = if (paused) "Filtering paused" else "Filtering active"
                        }
                        .onFailure { statusText = it.message ?: "Unable to update filters" }
                    running = false
                }
            },
            onFilterEditorToggle = {
                if (!filterEditorVisible) {
                    categoryFilterText = filters.categoryFilters
                    channelFilterText = filters.channelFilters
                }
                filterEditorVisible = !filterEditorVisible
            },
            onCategoryFiltersChange = { categoryFilterText = it },
            onChannelFiltersChange = { channelFilterText = it },
            onSaveFilters = {
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
                        .onFailure { statusText = it.message ?: "Unable to save filters" }
                    running = false
                }
            },
            onReset = { confirmReset = true },
            onDownloadDesktop = { openDesktopDownload() },
            modifier = modifier
        )
    } else {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Config", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        ConfigurationIntro(onDownloadDesktop = { openDesktopDownload() })
        CompactOutlinedTextField(
            value = host,
            onValueChange = { host = it },
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
                            syncActions.checkConnection(host.trim(), port)
                        }.onSuccess {
                            statusText = "Connected"
                        }.onFailure { ex ->
                            statusText = ex.message ?: "Connection failed"
                        }
                        running = false
                    }
                }
            ) {
                Text("Test")
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
                                host.trim(),
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
                            statusText = ex.message ?: "Sync failed"
                        }
                        running = false
                    }
                }
            ) {
                Text("Pull")
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
        Text("Backup & Restore", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = DeepNightSurfaceHigh,
            contentColor = DeepNightText
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
        Text("Appearance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                modifier = Modifier.semantics { contentDescription = "Enable thumbnails" },
                checked = filters.enableThumbnails,
                enabled = !running,
                onCheckedChange = { enabled ->
                    scope.launch {
                        running = true
                        runCatching { filterActions.setEnableThumbnails(enabled) }
                            .onSuccess {
                                filters = filters.copy(enableThumbnails = enabled)
                                onThumbnailSettingChanged(enabled)
                                statusText = if (enabled) "Thumbnails enabled" else "Thumbnails disabled"
                            }
                            .onFailure { statusText = it.message ?: "Unable to update thumbnails" }
                        running = false
                    }
                }
            )
            Text("Enable thumbnails")
        }
        Text("Default Player", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !running) { playerSelectorVisible = true }
                .semantics { contentDescription = "Change default player" },
            shape = RoundedCornerShape(10.dp),
            color = DeepNightSurfaceHigh,
            contentColor = DeepNightText
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                playerIconRenderer(selectedPlayerChoice, Modifier.size(40.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(selectedPlayer.playerLabel(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Selected default player", color = DeepNightMutedText, style = MaterialTheme.typography.bodySmall)
                }
                Text("Change", color = DeepNightPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        Text("Playback History", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OutlinedButton(
            modifier = Modifier.semantics { contentDescription = "Clear recently played bookmark channels" },
            enabled = !running,
            onClick = ::clearRecentlyPlayedBookmarks
        ) {
            Text("Clear Recently Played")
        }
        Text("Filters", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            if (filters.paused) {
                "Current state: filtering is paused. Matching categories and channels are visible."
            } else {
                "Current state: filtering is active. Matching categories and channels are hidden."
            },
            color = DeepNightMutedText,
            style = MaterialTheme.typography.bodySmall
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                modifier = Modifier.semantics { contentDescription = if (filters.paused) "Resume content filters" else "Pause content filters" },
                enabled = !running,
                onClick = {
                    scope.launch {
                        val paused = !filters.paused
                        running = true
                        runCatching { filterActions.setPaused(paused) }
                            .onSuccess {
                                filters = filters.copy(paused = paused)
                                statusText = if (paused) "Filtering paused" else "Filtering active"
                            }
                            .onFailure { statusText = it.message ?: "Unable to update filters" }
                        running = false
                    }
                }
            ) {
                Text(if (filters.paused) "Resume Filtering" else "Pause Filtering")
            }
            OutlinedButton(
                modifier = Modifier.semantics { contentDescription = if (filterEditorVisible) "Hide content filters" else "View or edit content filters" },
                enabled = !running,
                onClick = {
                    if (!filterEditorVisible) {
                        categoryFilterText = filters.categoryFilters
                        channelFilterText = filters.channelFilters
                    }
                    filterEditorVisible = !filterEditorVisible
                }
            ) {
                Text(if (filterEditorVisible) "Hide Filters" else "View / Edit Filters")
            }
        }
        Text(
            "Loaded filters: ${filters.categoryFilters.filterTermCount()} category terms, ${filters.channelFilters.filterTermCount()} channel terms.",
            color = DeepNightMutedText,
            style = MaterialTheme.typography.bodySmall
        )
        if (filterEditorVisible) {
            OutlinedTextField(
                value = categoryFilterText,
                onValueChange = { categoryFilterText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Category filters") },
                placeholder = { Text("adult, xxx") },
                minLines = 3,
                colors = darkTextFieldColors()
            )
            OutlinedTextField(
                value = channelFilterText,
                onValueChange = { channelFilterText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Channel filters") },
                placeholder = { Text("adult, xxx") },
                minLines = 3,
                colors = darkTextFieldColors()
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
                            .onFailure { statusText = it.message ?: "Unable to save filters" }
                        running = false
                    }
                }
            ) {
                Text("Save Filters")
            }
        }
        Button(
            modifier = Modifier.semantics { contentDescription = "Reset local UIPTV database" },
            enabled = !running,
            onClick = { confirmReset = true }
        ) {
            Text("Reset Local Data")
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
                        .onFailure { statusText = it.message ?: "Unable to open Google Play" }
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
                        .onFailure { statusText = it.message ?: "Unable to save player preference" }
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
                                .onFailure { statusText = it.message ?: "Unable to reset local data" }
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
private fun WideRemoteSyncContent(
    host: String,
    portText: String,
    verificationCode: String,
    statusText: String,
    running: Boolean,
    lastSyncText: String,
    filters: AndroidFilterSettings,
    categoryFilterText: String,
    channelFilterText: String,
    filterEditorVisible: Boolean,
    selectedPlayerChoice: PlayerChoice,
    selectedPlayer: AndroidPlayerPreference,
    playerIconRenderer: PlayerIconRenderer,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onTest: () -> Unit,
    onPull: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onClearRecentlyPlayedBookmarks: () -> Unit,
    onThumbnailChange: (Boolean) -> Unit,
    onPlayerClick: () -> Unit,
    onPausedToggle: () -> Unit,
    onFilterEditorToggle: () -> Unit,
    onCategoryFiltersChange: (String) -> Unit,
    onChannelFiltersChange: (String) -> Unit,
    onSaveFilters: () -> Unit,
    onReset: () -> Unit,
    onDownloadDesktop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val compactWide = LocalWidePhoneLayout.current
    val rowScrollState = rememberScrollState()
    val outerPadding = if (compactWide) 6.dp else 12.dp
    val gap = if (compactWide) 8.dp else 12.dp
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(outerPadding)
            .then(if (compactWide) Modifier.horizontalScroll(rowScrollState) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        Surface(
            modifier = if (compactWide) {
                Modifier
                    .width(260.dp)
                    .fillMaxHeight()
            } else {
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            },
            shape = RoundedCornerShape(14.dp),
            color = DeepNightSurface,
            contentColor = DeepNightText
        ) {
            Column(
                modifier = Modifier
                    .padding(if (compactWide) 8.dp else 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(if (compactWide) 8.dp else 10.dp)
            ) {
                Text("Config", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                ConfigurationIntro(onDownloadDesktop = onDownloadDesktop, compact = compactWide)
                CompactOutlinedTextField(
                    value = host,
                    onValueChange = onHostChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = "Host",
                    placeholder = "Desktop IP or hostname"
                )
                CompactOutlinedTextField(
                    value = portText,
                    onValueChange = onPortChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = "Port",
                    placeholder = "Desktop sync port",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Test desktop sync connection" },
                        enabled = !running && host.isNotBlank() && portText.isNotBlank(),
                        onClick = onTest
                    ) {
                        Text("Test")
                    }
                    Button(
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Pull data from desktop" },
                        enabled = !running && host.isNotBlank() && portText.isNotBlank(),
                        onClick = onPull
                    ) {
                        Text("Pull")
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
        Surface(
            modifier = if (compactWide) {
                Modifier
                    .width(260.dp)
                    .fillMaxHeight()
            } else {
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            },
            shape = RoundedCornerShape(14.dp),
            color = DeepNightSurface,
            contentColor = DeepNightText
        ) {
            Column(
                modifier = Modifier
                    .padding(if (compactWide) 8.dp else 12.dp)
                    .then(if (compactWide) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                verticalArrangement = Arrangement.spacedBy(if (compactWide) 8.dp else 10.dp)
            ) {
                Text("Backup & Restore", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Back up mobile data" },
                        enabled = !running,
                        onClick = onBackup
                    ) {
                        Text("Back up")
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Restore mobile data" },
                        enabled = !running,
                        onClick = onRestore
                    ) {
                        Text("Restore")
                    }
                }
                Text("Appearance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        modifier = Modifier.semantics { contentDescription = "Enable thumbnails" },
                        checked = filters.enableThumbnails,
                        enabled = !running,
                        onCheckedChange = onThumbnailChange
                    )
                    Text("Enable thumbnails")
                }
                Text("Default Player", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !running, onClick = onPlayerClick)
                        .semantics { contentDescription = "Change default player" },
                    shape = RoundedCornerShape(10.dp),
                    color = DeepNightSurfaceHigh,
                    contentColor = DeepNightText
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        playerIconRenderer(selectedPlayerChoice, Modifier.size(40.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(selectedPlayer.playerLabel(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Selected default player", color = DeepNightMutedText, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Change", color = DeepNightPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text("Playback History", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                OutlinedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Clear recently played bookmark channels" },
                    enabled = !running,
                    onClick = onClearRecentlyPlayedBookmarks
                ) {
                    Text("Clear Recently Played")
                }
                Text("Reset", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Button(
                    modifier = Modifier.semantics { contentDescription = "Reset local UIPTV database" },
                    enabled = !running,
                    onClick = onReset
                ) {
                    Text("Reset Local Data")
                }
            }
        }
        Surface(
            modifier = if (compactWide) {
                Modifier
                    .width(300.dp)
                    .fillMaxHeight()
            } else {
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            },
            shape = RoundedCornerShape(14.dp),
            color = DeepNightSurface,
            contentColor = DeepNightText
        ) {
            Column(
                modifier = Modifier
                    .padding(if (compactWide) 8.dp else 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(if (compactWide) 8.dp else 10.dp)
            ) {
                Text("Filters", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (filters.paused) {
                        "Current state: filtering is paused. Matching categories and channels are visible."
                    } else {
                        "Current state: filtering is active. Matching categories and channels are hidden."
                    },
                    color = DeepNightMutedText,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.semantics { contentDescription = if (filters.paused) "Resume content filters" else "Pause content filters" },
                        enabled = !running,
                        onClick = onPausedToggle
                    ) {
                        Text(if (filters.paused) "Resume Filtering" else "Pause Filtering")
                    }
                    OutlinedButton(
                        modifier = Modifier.semantics { contentDescription = if (filterEditorVisible) "Hide content filters" else "View or edit content filters" },
                        enabled = !running,
                        onClick = onFilterEditorToggle
                    ) {
                        Text(if (filterEditorVisible) "Hide Filters" else "View / Edit Filters")
                    }
                }
                Text(
                    "Loaded filters: ${filters.categoryFilters.filterTermCount()} category terms, ${filters.channelFilters.filterTermCount()} channel terms.",
                    color = DeepNightMutedText,
                    style = MaterialTheme.typography.bodySmall
                )
                if (filterEditorVisible) {
                    OutlinedTextField(
                        value = categoryFilterText,
                        onValueChange = onCategoryFiltersChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Category filters") },
                        placeholder = { Text("adult, xxx") },
                        minLines = 3,
                        colors = darkTextFieldColors()
                    )
                    OutlinedTextField(
                        value = channelFilterText,
                        onValueChange = onChannelFiltersChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Channel filters") },
                        placeholder = { Text("adult, xxx") },
                        minLines = 3,
                        colors = darkTextFieldColors()
                    )
                    Button(
                        modifier = Modifier.semantics { contentDescription = "Save content filters" },
                        enabled = !running,
                        onClick = onSaveFilters
                    ) {
                        Text("Save Filters")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigurationIntro(
    onDownloadDesktop: () -> Unit,
    compact: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
        Text("About", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Use this page for desktop sync, mobile backup and restore, player preference, thumbnails, content filters, and local reset.",
            color = DeepNightMutedText,
            style = MaterialTheme.typography.bodySmall
        )
        Text("Sync help", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Sync needs UIPTV Desktop running on the computer that has your library. Install the desktop version, start its web server, then enter that computer's IP address and server port here. Approve the request on desktop when prompted.",
            color = DeepNightMutedText,
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(
            modifier = Modifier.semantics { contentDescription = "Open UIPTV desktop download page" },
            onClick = onDownloadDesktop
        ) {
            Text("Download Desktop")
        }
    }
}

@Composable
private fun AccountsScreen(
    accountActions: AccountUiActions,
    onOpenAccountChannels: (MobileAccount) -> Unit,
    localPlaylistPicker: LocalPlaylistPicker?,
    wideLayout: Boolean,
    wideSearchVisible: Boolean,
    refreshSignal: Int,
    actionsPanelVisible: Boolean,
    onActionsPanelVisibleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf<List<MobileAccount>>(emptyList()) }
    var editing by remember { mutableStateOf(MobileAccount()) }
    var selectedType by remember { mutableStateOf(MobileAccountType.STALKER_PORTAL) }
    var statusText by remember { mutableStateOf("Idle") }
    var running by remember { mutableStateOf(false) }
    var cacheDialogJob by remember { mutableStateOf<CacheRefreshJobState?>(null) }
    var accountFeedback by remember { mutableStateOf<AccountFeedback?>(null) }
    var pendingDelete by remember { mutableStateOf<MobileAccount?>(null) }
    var pendingDeleteFailedAccounts by remember { mutableStateOf<List<FailedRefreshAccount>>(emptyList()) }
    var editorVisible by remember { mutableStateOf(false) }
    var accountFilter by remember { mutableStateOf(AccountFilter.ALL) }
    var accountSearchVisible by remember { mutableStateOf(false) }
    var accountSearchQuery by remember { mutableStateOf("") }
    var actionsMenuExpanded by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            running = true
            runCatching { accountActions.loadAccounts() }
                .onSuccess { accounts = it }
                .onFailure { statusText = it.message ?: "Unable to load accounts" }
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
                    val messageText = it.message ?: "Unable to queue cache job"
                    statusText = messageText
                    accountFeedback = AccountFeedback("Cache Refresh", messageText, false)
                }
            running = false
        }
    }

    LaunchedEffect(accountActions, refreshSignal) {
        runCatching { accountActions.loadAccounts() }
            .onSuccess { accounts = it }
            .onFailure { statusText = it.message ?: "Unable to load accounts" }
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
                            .onSuccess { accounts = it }
                            .onFailure { statusText = it.message ?: "Unable to load accounts" }
                    }
                    break
                }
            }
            delay(1_000)
        }
    }

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
                            val message = it.message ?: "Unable to stop cache job"
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
        val effectiveAccountSearchVisible = if (wideLayout) wideSearchVisible else accountSearchVisible
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
        if (wideLayout) {
            WideAccountsContent(
                accounts = accounts,
                visibleAccounts = visibleAccounts,
                accountFilter = accountFilter,
                accountSearchVisible = effectiveAccountSearchVisible,
                accountSearchQuery = accountSearchQuery,
                actionsPanelVisible = actionsPanelVisible,
                running = running,
                statusText = statusText,
                onFilterSelect = { accountFilter = it },
                onSearchChange = { accountSearchQuery = it },
                onToggleActionsPanel = { onActionsPanelVisibleChange(!actionsPanelVisible) },
                onNewAccount = {
                    editing = MobileAccount()
                    selectedType = MobileAccountType.STALKER_PORTAL
                    statusText = "New account"
                    editorVisible = true
                },
                onReload = { reload() },
                onClearAllCache = {
                    enqueueCacheJob(
                        CacheRefreshJobRequest(CacheRefreshAction.CLEAR_ALL_CACHE),
                        "Queued clear all cache"
                    )
                },
                onRefreshAllCaches = {
                    enqueueCacheJob(
                        CacheRefreshJobRequest(CacheRefreshAction.REFRESH_ALL),
                        "Queued refresh all"
                    )
                },
                onOpen = { account ->
                    if (account.id != null) {
                        onOpenAccountChannels(account)
                    }
                },
                onEdit = { account ->
                    editing = account
                    selectedType = account.type
                    statusText = "Editing ${account.accountName}"
                    editorVisible = true
                },
                onClearCache = { account ->
                    val accountId = account.id
                    if (accountId != null) {
                        enqueueCacheJob(
                            CacheRefreshJobRequest(CacheRefreshAction.CLEAR_ACCOUNT_CACHE, accountId),
                            "Queued clear ${account.accountName}"
                        )
                    }
                },
                onRefreshCache = { account ->
                    val accountId = account.id
                    if (accountId != null) {
                        enqueueCacheJob(
                            CacheRefreshJobRequest(CacheRefreshAction.REFRESH_ACCOUNT, accountId),
                            "Queued ${account.accountName}"
                        )
                    }
                },
                onDelete = { account ->
                    pendingDelete = account.takeIf { it.id != null }
                },
                modifier = modifier
            )
        } else {
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
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
                    IconButton(
                        modifier = Modifier.semantics { contentDescription = "Search accounts" },
                        enabled = !running,
                        onClick = { accountSearchVisible = !accountSearchVisible }
                    ) {
                        Icon(Icons.Outlined.Search, contentDescription = null)
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
                                    Icon(Icons.Outlined.Add, contentDescription = null)
                                },
                                text = { Text("New account") },
                                onClick = {
                                    actionsMenuExpanded = false
                                    editing = MobileAccount()
                                    selectedType = MobileAccountType.STALKER_PORTAL
                                    statusText = "New account"
                                    editorVisible = true
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                                },
                                text = { Text("Reload accounts") },
                                onClick = {
                                    actionsMenuExpanded = false
                                    reload()
                                }
                            )
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
                                text = { Text("Refresh all caches") },
                                onClick = {
                                    actionsMenuExpanded = false
                                    enqueueCacheJob(
                                        CacheRefreshJobRequest(CacheRefreshAction.REFRESH_ALL),
                                        "Queued refresh all"
                                    )
                                }
                            )
                        }
                    }
                }
            }
            if (accountSearchVisible) {
                item {
                    CompactOutlinedTextField(
                        value = accountSearchQuery,
                        onValueChange = { accountSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = "Search accounts"
                    )
                }
            }
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
                            accountFeedback = AccountFeedback(
                                title = "Account Saved",
                                message = "${it.accountName} was saved successfully.",
                                success = true
                            )
                            editing = it
                            editorVisible = false
                            runCatching { accountActions.loadAccounts() }
                                .onSuccess { loaded -> accounts = loaded }
                                .onFailure { error -> statusText = error.message ?: "Unable to load accounts" }
                        }
                        .onFailure {
                            val message = it.message ?: "Unable to save account"
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
                                        .onSuccess { loaded -> accounts = loaded }
                                        .onFailure { error -> statusText = error.message ?: "Unable to load accounts" }
                                }
                                .onFailure {
                                    val message = it.message ?: "Unable to delete account"
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
                                val message = it.message ?: "Unable to delete failed accounts"
                                statusText = message
                                accountFeedback = AccountFeedback("Delete Failed", message, false)
                            }
                            accounts = runCatching { accountActions.loadAccounts() }.getOrDefault(accounts)
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
                            val message = it.message ?: "Unable to stop cache job"
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
private fun WideAccountsContent(
    accounts: List<MobileAccount>,
    visibleAccounts: List<MobileAccount>,
    accountFilter: AccountFilter,
    accountSearchVisible: Boolean,
    accountSearchQuery: String,
    actionsPanelVisible: Boolean,
    running: Boolean,
    statusText: String,
    onFilterSelect: (AccountFilter) -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleActionsPanel: () -> Unit,
    onNewAccount: () -> Unit,
    onReload: () -> Unit,
    onClearAllCache: () -> Unit,
    onRefreshAllCaches: () -> Unit,
    onOpen: (MobileAccount) -> Unit,
    onEdit: (MobileAccount) -> Unit,
    onClearCache: (MobileAccount) -> Unit,
    onRefreshCache: (MobileAccount) -> Unit,
    onDelete: (MobileAccount) -> Unit,
    modifier: Modifier = Modifier
) {
    val compactWide = LocalWidePhoneLayout.current
    val outerPadding = if (compactWide) 6.dp else 12.dp
    val gap = if (compactWide) 8.dp else 12.dp
    val actionWidth = if (compactWide) 220.dp else 320.dp
    val accountColumns = if (compactWide) 1 else 2
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(outerPadding),
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(if (compactWide) 8.dp else 10.dp)
        ) {
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
                            onClick = { onFilterSelect(filter) },
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
                if (!actionsPanelVisible) {
                    OutlinedButton(
                        modifier = Modifier.semantics { contentDescription = "Show account page actions" },
                        enabled = !running,
                        onClick = onToggleActionsPanel
                    ) {
                        Text("Actions")
                    }
                }
            }
            if (accountSearchVisible) {
                CompactOutlinedTextField(
                    value = accountSearchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = "Search accounts"
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
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
                items(visibleAccounts.chunked(accountColumns)) { rowAccounts ->
                    Row(horizontalArrangement = Arrangement.spacedBy(if (compactWide) 8.dp else 10.dp)) {
                        rowAccounts.forEach { account ->
                            Box(modifier = Modifier.weight(1f)) {
                                AccountRow(
                                    account = account,
                                    onOpen = { onOpen(account) },
                                    onEdit = { onEdit(account) },
                                    onClearCache = { onClearCache(account) },
                                    onRefreshCache = { onRefreshCache(account) },
                                    onDelete = { onDelete(account) }
                                )
                            }
                        }
                        repeat(accountColumns - rowAccounts.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            if (running) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(statusText, color = DeepNightMutedText, style = MaterialTheme.typography.bodySmall)
        }
        if (actionsPanelVisible) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(actionWidth),
                shape = RoundedCornerShape(14.dp),
                color = DeepNightSurface,
                contentColor = DeepNightText
            ) {
                Column(
                    modifier = Modifier
                        .padding(if (compactWide) 8.dp else 12.dp)
                        .then(if (compactWide) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                    verticalArrangement = Arrangement.spacedBy(if (compactWide) 8.dp else 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Account page actions",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(onClick = onToggleActionsPanel) {
                            Text("Hide")
                        }
                    }
                    Button(modifier = Modifier.fillMaxWidth(), enabled = !running, onClick = onNewAccount) {
                        Text("New account")
                    }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !running, onClick = onReload) {
                        Text("Reload accounts")
                    }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !running, onClick = onClearAllCache) {
                        Text("Clear all cache")
                    }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !running, onClick = onRefreshAllCaches) {
                        Text("Refresh all caches")
                    }
                    Text(
                        "Use each account row menu for Open, Edit, Clear cache, Refresh cache, and Delete.",
                        color = DeepNightMutedText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: MobileAccount,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onClearCache: () -> Unit,
    onRefreshCache: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .background(DeepNightSurfaceHigh)
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(account.type.displayName, color = DeepNightMutedText, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(
                modifier = Modifier.semantics { contentDescription = "Account actions for ${account.accountName}" },
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
                    text = { Text("Delete") },
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
                            Checkbox(
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
    onDismiss: () -> Unit,
    onTypeChange: (MobileAccountType) -> Unit,
    onAccountChange: (MobileAccount) -> Unit,
    onBrowseLocalPlaylist: LocalPlaylistPicker?,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (account.id == null) "New Account" else "Edit Account") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                AccountEditor(
                    account = account,
                    selectedType = selectedType,
                    onTypeChange = onTypeChange,
                    onAccountChange = onAccountChange,
                    onBrowseLocalPlaylist = onBrowseLocalPlaylist,
                    onSave = onSave,
                    onDelete = onDelete
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun AccountEditor(
    account: MobileAccount,
    selectedType: MobileAccountType,
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeepNightSurfaceHigh)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            if (isNewAccount) "Create account" else "Edit account",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            accountEditorHelpText(selectedType, isNewAccount),
            color = DeepNightMutedText,
            style = MaterialTheme.typography.bodySmall
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            mobileAccountTypeChoices.forEach { type ->
                SelectableChip(
                    label = type.name.shortAccountType(),
                    selected = selectedType == type,
                    description = "Use ${type.displayName} account type",
                    onClick = { onTypeChange(type) }
                )
            }
        }
        AccountTextField("Account name", account.accountName) { onAccountChange(account.copy(accountName = it)) }
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
                        color = DeepNightMutedText,
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
                    AccountTextField("Timezone", account.timezone) { onAccountChange(account.copy(timezone = it)) }
                }
            }
            MobileAccountType.XTREME_API -> {
                AccountTextField("Server URL", account.url) { onAccountChange(account.copy(url = it)) }
                AccountTextField("Username", account.username) { onAccountChange(account.copy(username = it)) }
                AccountTextField("Password", account.password) { onAccountChange(account.copy(password = it)) }
            }
            MobileAccountType.M3U8_URL -> {
                AccountTextField("Playlist URL", account.m3u8Path.ifBlank { account.url }) {
                    onAccountChange(account.copy(m3u8Path = it))
                }
                AccountTextField("EPG URL (optional)", account.epg) { onAccountChange(account.copy(epg = it)) }
            }
            MobileAccountType.M3U8_LOCAL -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactOutlinedTextField(
                        value = account.m3u8Path.ifBlank { account.url },
                        onValueChange = { onAccountChange(account.copy(m3u8Path = it)) },
                        modifier = Modifier.weight(1f),
                        label = "Playlist file"
                    )
                    Button(
                        modifier = Modifier.semantics { contentDescription = "Browse for local M3U playlist file" },
                        enabled = onBrowseLocalPlaylist != null,
                        onClick = {
                            onBrowseLocalPlaylist?.invoke { selectedUri ->
                                onAccountChange(account.copy(m3u8Path = selectedUri))
                            }
                        }
                    ) {
                        Text("Browse")
                    }
                }
                AccountTextField("EPG URL (optional)", account.epg) { onAccountChange(account.copy(epg = it)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                modifier = Modifier.semantics { contentDescription = "Pin account to top" },
                checked = account.pinToTop,
                onCheckedChange = { onAccountChange(account.copy(pinToTop = it)) }
            )
            Text("Pin to top")
            Spacer(Modifier.width(12.dp))
            Checkbox(
                modifier = Modifier.semantics { contentDescription = "Resolve redirects for account" },
                checked = account.resolveChainAndDeepRedirects,
                onCheckedChange = { onAccountChange(account.copy(resolveChainAndDeepRedirects = it)) }
            )
            Text("Resolve redirects")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                modifier = Modifier.semantics { contentDescription = "Save account" },
                onClick = onSave,
                enabled = account.canSaveForType(selectedType)
            ) {
                Text("Save")
            }
            Button(
                modifier = Modifier.semantics { contentDescription = "Delete account" },
                onClick = onDelete,
                enabled = account.id != null
            ) {
                Text("Delete")
            }
        }
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
private fun StalkerMacSelector(
    selectedMac: String,
    macOptions: List<String>,
    onMacSelected: (String) -> Unit,
    onManage: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = macOptions.firstOrNull { it.equals(selectedMac, ignoreCase = true) }
        ?: selectedMac.normalizedMacEntry()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "MAC",
                modifier = Modifier.weight(1f),
                color = DeepNightMutedText,
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = onManage) {
                Text("Manage")
            }
        }
        Box {
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Select Stalker MAC address" },
                enabled = macOptions.isNotEmpty(),
                onClick = { expanded = true }
            ) {
                Text(
                    selected.ifBlank { "No MAC addresses" },
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("v")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                macOptions.forEach { mac ->
                    DropdownMenuItem(
                        text = { Text(mac) },
                        onClick = {
                            expanded = false
                            onMacSelected(mac)
                        }
                    )
                }
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
    MobileAccountType.M3U8_URL,
    MobileAccountType.M3U8_LOCAL
)

private fun accountEditorHelpText(type: MobileAccountType, isNewAccount: Boolean): String =
    when (type) {
        MobileAccountType.STALKER_PORTAL -> if (isNewAccount) {
            "Enter the portal URL and paste all MAC addresses in one field. You can fine-tune MACs later from Edit."
        } else {
            "Update portal details and manage the saved MAC addresses for this account."
        }
        MobileAccountType.XTREME_API -> "Enter the server URL plus username and password from your provider."
        MobileAccountType.M3U8_URL -> "Paste the remote M3U playlist URL. Add an EPG URL only if you have one."
        MobileAccountType.M3U8_LOCAL -> "Choose a local M3U file stored on this device."
    }

private fun MobileAccount.canSaveForType(type: MobileAccountType): Boolean =
    accountName.isNotBlank() && when (type) {
        MobileAccountType.STALKER_PORTAL -> url.isNotBlank() && normalizeMacEntries(macAddressList, macAddress).isNotEmpty()
        MobileAccountType.XTREME_API -> url.isNotBlank() && username.isNotBlank() && password.isNotBlank()
        MobileAccountType.M3U8_URL,
        MobileAccountType.M3U8_LOCAL -> m3u8Path.isNotBlank() || url.isNotBlank()
    }

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
    CompactOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = label
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
        textStyle = MaterialTheme.typography.bodySmall,
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
        colors = darkTextFieldColors()
    )
}

@Composable
private fun CompactOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 56.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall,
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
        colors = darkTextFieldColors()
    )
}

@Composable
private fun darkTextFieldColors(): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = DeepNightText,
        unfocusedTextColor = DeepNightText,
        disabledTextColor = DeepNightMutedText,
        errorTextColor = Color(0xFFFFD6D6),
        cursorColor = DeepNightAccent,
        focusedLabelColor = DeepNightAccent,
        unfocusedLabelColor = DeepNightMutedText,
        disabledLabelColor = DeepNightMutedText,
        errorLabelColor = Color(0xFFFFB4AB),
        focusedPlaceholderColor = DeepNightMutedText,
        unfocusedPlaceholderColor = DeepNightMutedText,
        focusedBorderColor = DeepNightAccent,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
        errorBorderColor = Color(0xFFFFB4AB),
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent
    )

@Composable
private fun AppTabs(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    vertical: Boolean = false,
    compact: Boolean = false,
    searchEnabled: Boolean = false,
    searchActive: Boolean = false,
    onSearchClick: () -> Unit = {}
) {
    val tabs = listOf(
        BottomTab("Bookmarks", Icons.Outlined.Bookmarks),
        BottomTab("Accounts", Icons.Outlined.AccountCircle),
        BottomTab("Watching", Icons.Outlined.PlayCircle),
        BottomTab("Config", Icons.Outlined.Settings)
    )
    if (vertical) {
        val railWidth = if (compact) 64.dp else 78.dp
        val railPadding = if (compact) 6.dp else 10.dp
        val itemHeight = if (compact) 58.dp else 72.dp
        val iconWidth = if (compact) 30.dp else 34.dp
        val iconHeight = if (compact) 24.dp else 26.dp
        val iconSize = if (compact) 15.dp else 16.dp
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .width(railWidth),
            color = DeepNightSurface,
            contentColor = DeepNightText
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = railPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)
            ) {
                IconButton(
                    modifier = Modifier.semantics {
                        contentDescription = if (searchEnabled) {
                            if (searchActive) "Hide search" else "Show search"
                        } else {
                            "Search unavailable"
                        }
                    },
                    enabled = searchEnabled,
                    onClick = onSearchClick
                ) {
                    Surface(
                        modifier = Modifier
                            .width(iconWidth)
                            .height(iconHeight),
                        shape = RoundedCornerShape(999.dp),
                        color = if (searchActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        contentColor = when {
                            searchActive -> DeepNightPrimary
                            searchEnabled -> DeepNightMutedText
                            else -> DeepNightMutedText.copy(alpha = 0.42f)
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                modifier = Modifier.size(iconSize)
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = if (compact) Arrangement.SpaceEvenly else Arrangement.Center
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val selected = index == selectedTab
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(itemHeight)
                                .clickable { onSelect(index) }
                                .semantics { contentDescription = "Open ${tab.label}" },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                modifier = Modifier
                                    .width(iconWidth)
                                    .height(iconHeight),
                                shape = RoundedCornerShape(999.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                contentColor = if (selected) DeepNightPrimary else DeepNightMutedText
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(iconSize)
                                    )
                                }
                            }
                            Spacer(Modifier.height(if (compact) 1.dp else 2.dp))
                            Text(
                                tab.label,
                                color = if (selected) DeepNightAccent else DeepNightMutedText,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = if (compact) 8.sp else 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeepNightSurface)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            color = DeepNightSurface,
            contentColor = DeepNightText
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = index == selectedTab
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable { onSelect(index) }
                            .semantics { contentDescription = "Open ${tab.label}" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .width(36.dp)
                                .height(24.dp),
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            contentColor = if (selected) DeepNightPrimary else DeepNightMutedText
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            tab.label,
                            color = if (selected) DeepNightAccent else DeepNightMutedText,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        )
    }
}

private data class BottomTab(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
private fun EmptyState(title: String, detail: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 96.dp)
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
    val stopCacheJob: suspend (String) -> Unit
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
                stopCacheJob = {}
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
    when (step) {
        RemoteSyncProgressStep.CONNECTING -> "Connecting"
        RemoteSyncProgressStep.WAITING_FOR_APPROVAL -> "Waiting for desktop approval"
        RemoteSyncProgressStep.DOWNLOADING -> "Downloading"
        RemoteSyncProgressStep.APPLYING_SYNC -> "Applying"
        RemoteSyncProgressStep.COMPLETING_REMOTE -> "Completing"
        RemoteSyncProgressStep.FINISHED -> "Finished"
    }

private fun String.shortAccountType(): String =
    when (this) {
        "STALKER_PORTAL" -> "Stalker"
        "XTREME_API" -> "Xtreme"
        "M3U8_URL" -> "M3U URL"
        "M3U8_LOCAL" -> "M3U File"
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
