@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.uiptv.mobile.shared.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
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
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
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
import androidx.compose.material3.rememberDrawerState
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
import com.uiptv.mobile.shared.browse.BrowseMode
import com.uiptv.mobile.shared.browse.MobileBookmark
import com.uiptv.mobile.shared.browse.MobileBookmarkCategory
import com.uiptv.mobile.shared.browse.MobileBrowseCategory
import com.uiptv.mobile.shared.browse.MobileBrowseItem
import com.uiptv.mobile.shared.browse.MobileBrowseSnapshot
import com.uiptv.mobile.shared.browse.MobileWatchingNowEpisode
import com.uiptv.mobile.shared.browse.MobileWatchingNowItem
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
    syncActions: RemoteSyncUiActions = RemoteSyncUiActions.preview(),
    accountActions: AccountUiActions = AccountUiActions.preview(),
    browseActions: BrowseUiActions = BrowseUiActions.preview(),
    playbackActions: PlaybackUiActions = PlaybackUiActions.preview(),
    filterActions: FilterUiActions = FilterUiActions.preview(),
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
    fun selectTab(index: Int) {
        selectedTab = index
        if (index != 1) {
            selectedBrowseAccount = null
        }
    }
    backHandler(selectedBrowseAccount != null) {
        selectedBrowseAccount = null
    }
    LaunchedEffect(filterActions) {
        runCatching { filterActions.load() }
            .onSuccess { showThumbnails = it.enableThumbnails }
    }

    val useDarkTheme = isSystemInDarkTheme()
    val palette = if (useDarkTheme) DarkUiptvPalette else LightUiptvPalette
    CompositionLocalProvider(LocalUiptvPalette provides palette) {
        MaterialTheme(colorScheme = if (useDarkTheme) UiptvDarkColorScheme else UiptvLightColorScheme) {
            Scaffold(
                containerColor = palette.background,
                contentColor = palette.text,
                bottomBar = { AppTabs(selectedTab = selectedTab, onSelect = ::selectTab) }
            ) { padding ->
                CurrentTab(
                    selectedTab = selectedTab,
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
                    logoRenderer = logoRenderer,
                    playerIconRenderer = playerIconRenderer,
                    onOpenAccountChannels = { account ->
                        selectedBrowseAccount = account
                    },
                    onCloseAccountChannels = { selectedBrowseAccount = null },
                    onThumbnailSettingChanged = { showThumbnails = it },
                    backHandler = backHandler,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
        }
    }
}

@Composable
private fun CurrentTab(
    selectedTab: Int,
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
    logoRenderer: LogoRenderer,
    playerIconRenderer: PlayerIconRenderer,
    onOpenAccountChannels: (MobileAccount) -> Unit,
    onCloseAccountChannels: () -> Unit,
    onThumbnailSettingChanged: (Boolean) -> Unit,
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = selectedTab to (selectedBrowseAccount != null),
        modifier = modifier.fillMaxSize(),
        label = "main-content"
    ) { (tab, hasBrowseAccount) ->
        when (tab) {
            0 -> {
                BookmarksScreen(browseActions, playbackActions, showThumbnails, logoRenderer, playerIconRenderer, Modifier.fillMaxSize())
            }
            1 -> {
                val account = selectedBrowseAccount.takeIf { hasBrowseAccount }
                if (account == null) {
                    AccountsScreen(accountActions, onOpenAccountChannels, localPlaylistPicker, Modifier.fillMaxSize())
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
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            2 -> {
                WatchingNowScreen(browseActions, playbackActions, showThumbnails, logoRenderer, playerIconRenderer, Modifier.fillMaxSize())
            }
            3 -> {
                RemoteSyncScreen(
                    syncActions,
                    playbackActions,
                    filterActions,
                    backupRestoreActions,
                    backupFileCreator,
                    restoreFilePicker,
                    onThumbnailSettingChanged,
                    playerIconRenderer,
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
    var pendingPlayback by remember { mutableStateOf<PendingPlayback?>(null) }
    var playerChoices by remember { mutableStateOf<List<PlayerChoice>>(emptyList()) }
    var selectedBrowseSeries by remember { mutableStateOf<MobileWatchingNowItem?>(null) }
    var browseSeriesEpisodes by remember { mutableStateOf<List<MobileWatchingNowEpisode>>(emptyList()) }
    var searchVisible by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    fun reload(
        accountId: Long? = requestedAccountId ?: snapshot.selectedAccountId,
        categoryRowId: Long? = selectedCategoryRowId,
        itemQuery: String = channelQuery,
        browseMode: BrowseMode = mode
    ) {
        scope.launch {
            running = true
            runCatching { browseActions.loadBrowse(accountId, browseMode, categoryRowId, itemQuery) }
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

    val selectedCategory = snapshot.categories.firstOrNull { it.rowId == selectedCategoryRowId }
    val selectedAccountName = requestedAccountName
        ?: snapshot.accounts.firstOrNull { it.id == snapshot.selectedAccountId }?.name
        ?: "Provider"
    val selectedAccountType = requestedAccountType
        ?: snapshot.accounts.firstOrNull { it.id == snapshot.selectedAccountId }?.type
    val visibleModes = remember(selectedAccountType) { selectedAccountType.browseModesForAccount() }
    LaunchedEffect(browseActions, requestedAccountId, selectedAccountType) {
        val activeMode = if (mode in visibleModes) mode else BrowseMode.LIVE
        mode = activeMode
        selectedCategoryRowId = null
        channelQuery = ""
        reload(requestedAccountId, null, "", activeMode)
    }
    val visibleCategories = snapshot.categories.filter { category ->
        categoryQuery.isBlank() || category.title.contains(categoryQuery.trim(), ignoreCase = true)
    }
    val browseSeries = selectedBrowseSeries
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
                            .onSuccess { statusText = it.message }
                            .onFailure { statusText = it.message ?: "Unable to open episode" }
                    } else {
                        playerChoices = playbackActions.playerChoices()
                        pendingPlayback = PendingPlayback.WatchingEpisode(episode)
                    }
                    running = false
                }
            },
            onRemoveSeries = {
                selectedBrowseSeries = null
                browseSeriesEpisodes = emptyList()
            },
            showRemove = false,
            emptyTitle = "No episodes",
            emptyDetail = "This series did not return episode links.",
            modifier = modifier.fillMaxSize()
        )
        PlaybackPickerDialog(
            pendingPlayback = pendingPlayback,
            playerChoices = playerChoices,
            playerIconRenderer = playerIconRenderer,
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
                        }
                    }
                        .onSuccess { statusText = it.message }
                        .onFailure { statusText = it.message ?: "Unable to open episode" }
                    running = false
                }
            }
        )
        return
    }
    backHandler(selectedCategoryRowId != null) {
        selectedCategoryRowId = null
        channelQuery = ""
        reload(snapshot.selectedAccountId, null, "")
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DeepNightSurface,
                drawerContentColor = DeepNightText
            ) {
                Text(
                    "Categories",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = categoryQuery,
                    onValueChange = { categoryQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    singleLine = true,
                    label = { Text("Search categories") },
                    colors = darkTextFieldColors()
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (visibleCategories.isEmpty()) {
                        item {
                            EmptyState(
                                title = if (snapshot.categories.isEmpty()) "No ${mode.displayLabel()} categories" else "No categories match",
                                detail = if (snapshot.categories.isEmpty()) "Refresh this account cache after adding or syncing it." else "Try a shorter search term."
                            )
                        }
                    }
                    items(visibleCategories, key = { it.rowId }) { category ->
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    "${category.title}  ${category.itemCount}",
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            selected = category.rowId == selectedCategoryRowId,
                            onClick = {
                                selectedCategoryRowId = category.rowId
                                channelQuery = ""
                                reload(snapshot.selectedAccountId, category.rowId, "")
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                }
            }
        },
        gesturesEnabled = true
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = DeepNightBackground,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DeepNightSurface,
                        titleContentColor = DeepNightText,
                        navigationIconContentColor = DeepNightPrimary,
                        actionIconContentColor = DeepNightPrimary
                    ),
                    navigationIcon = {
                        IconButton(
                            modifier = Modifier.semantics { contentDescription = "Open categories" },
                            onClick = { scope.launch { drawerState.open() } }
                        ) {
                            Text("≡", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    title = {
                        UserPill(providerName = selectedAccountName)
                    },
                    actions = {
                        IconButton(
                            modifier = Modifier.semantics { contentDescription = "Search channels" },
                            onClick = { searchVisible = !searchVisible }
                        ) {
                            Text(if (searchVisible) "X" else "⌕", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        ) { padding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                val compactChrome = maxWidth > maxHeight
                val contentPadding = if (compactChrome) 6.dp else 12.dp
                val verticalGap = if (compactChrome) 6.dp else 10.dp
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    verticalArrangement = Arrangement.spacedBy(verticalGap)
                ) {
                if (compactChrome) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (onBackToAccounts != null) {
                            item {
                                TextButton(
                                    modifier = Modifier.semantics { contentDescription = "Back to account list" },
                                    onClick = onBackToAccounts
                                ) {
                                    Text("Accounts")
                                }
                            }
                        }
                        if (showAccountSelector) {
                            items(snapshot.accounts, key = { it.id }) { account ->
                                FilterChip(
                                    selected = snapshot.selectedAccountId == account.id,
                                    onClick = {
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
                        if (visibleModes.size > 1) {
                            items(visibleModes, key = { it.name }) { entry ->
                                FilterChip(
                                    selected = mode == entry,
                                    onClick = {
                                        mode = entry
                                        selectedCategoryRowId = null
                                        channelQuery = ""
                                        reload(snapshot.selectedAccountId, null, "", entry)
                                    },
                                    label = { Text(entry.displayLabel()) }
                                )
                            }
                        }
                        item {
                            FilterChip(
                                selected = selectedCategoryRowId == null,
                                onClick = {
                                    selectedCategoryRowId = null
                                    channelQuery = ""
                                    reload(snapshot.selectedAccountId, null, "")
                                },
                                label = { Text("All") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedCategoryRowId != null,
                                onClick = { scope.launch { drawerState.open() } },
                                label = {
                                    Text(
                                        selectedCategory?.title ?: "Categories",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                } else {
                    if (showAccountSelector) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(snapshot.accounts, key = { it.id }) { account ->
                                FilterChip(
                                    selected = snapshot.selectedAccountId == account.id,
                                    onClick = {
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
                    if (onBackToAccounts != null) {
                        TextButton(
                            modifier = Modifier.semantics { contentDescription = "Back to account list" },
                            onClick = onBackToAccounts
                        ) {
                            Text("Accounts")
                        }
                    }
                    if (visibleModes.size > 1) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(visibleModes, key = { it.name }) { entry ->
                                FilterChip(
                                    selected = mode == entry,
                                    onClick = {
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
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedCategoryRowId == null,
                                onClick = {
                                    selectedCategoryRowId = null
                                    channelQuery = ""
                                    reload(snapshot.selectedAccountId, null, "")
                                },
                                label = { Text("All categories") }
                            )
                        }
                        items(visibleCategories.take(18), key = { it.rowId }) { category ->
                            FilterChip(
                                selected = category.rowId == selectedCategoryRowId,
                                onClick = {
                                    selectedCategoryRowId = category.rowId
                                    channelQuery = ""
                                    reload(snapshot.selectedAccountId, category.rowId, "")
                                },
                                label = {
                                    Text(
                                        category.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                        item {
                            FilterChip(
                                selected = false,
                                onClick = { scope.launch { drawerState.open() } },
                                label = { Text("More") }
                            )
                        }
                    }
                }
                if (searchVisible) {
                    OutlinedTextField(
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
                        singleLine = true,
                        label = { Text(if (selectedCategoryRowId == null) "Search categories" else "Search channels") },
                        colors = darkTextFieldColors()
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
                                    onClick = {
                                        selectedCategoryRowId = category.rowId
                                        channelQuery = ""
                                        reload(snapshot.selectedAccountId, category.rowId, "")
                                    }
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
                                                    .onSuccess {
                                                        selectedBrowseSeries = series
                                                        browseSeriesEpisodes = it
                                                        statusText = if (it.isEmpty()) "No episodes for ${item.name}" else "${it.size} episodes"
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
    }

    PlaybackPickerDialog(
        pendingPlayback = pendingPlayback,
        playerChoices = playerChoices,
        playerIconRenderer = playerIconRenderer,
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
                    }
                }
                    .onSuccess { statusText = it.message }
                    .onFailure { statusText = it.message ?: "Unable to open stream" }
                running = false
            }
        }
    )
}

@Composable
private fun CompactToolbarAction(label: String, description: String, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .defaultMinSize(minHeight = 40.dp)
            .background(DeepNightSurfaceHighest)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        color = DeepNightText,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1
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
private fun CategoryListRow(category: MobileBrowseCategory, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .background(DeepNightSurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
            .clickable(enabled = item.command.isNotBlank(), onClick = onPlay),
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
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<MobileBookmarkCategory>>(emptyList()) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var bookmarks by remember { mutableStateOf<List<MobileBookmark>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Loading") }
    var running by remember { mutableStateOf(false) }
    var pendingPlayback by remember { mutableStateOf<PendingPlayback?>(null) }
    var playerChoices by remember { mutableStateOf<List<PlayerChoice>>(emptyList()) }

    fun reload(categoryId: String? = selectedCategoryId) {
        scope.launch {
            running = true
            runCatching {
                categories = browseActions.listBookmarkCategories()
                browseActions.listBookmarks(query, categoryId)
            }
                .onSuccess {
                    bookmarks = it
                    statusText = if (it.isEmpty()) "No bookmarks" else "${it.size} bookmarks"
                }
                .onFailure { statusText = it.message ?: "Unable to load bookmarks" }
            running = false
        }
    }

    LaunchedEffect(browseActions) {
        reload()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        item {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    reload()
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search bookmarks") },
                colors = darkTextFieldColors()
            )
        }
        if (bookmarks.isEmpty()) {
            item {
                EmptyState(
                    title = if (query.isBlank()) "No bookmarks" else "No bookmark matches",
                    detail = if (query.isBlank()) "Save channels from the Channels screen." else "Try a shorter search term."
                )
            }
        }
        items(bookmarks, key = { it.rowId }) { bookmark ->
            BookmarkRow(
                bookmark = bookmark,
                showThumbnail = showThumbnails,
                logoRenderer = logoRenderer,
                onPlay = {
                    scope.launch {
                        running = true
                        val preference = playbackActions.loadPlayerPreference()
                        if (preference.rememberForFutureStreams && preference.selectedPlayer != AndroidPlayerPreference.ASK_EVERY_TIME) {
                            runCatching { playbackActions.playBookmark(bookmark, preference.selectedPlayer, false) }
                                .onSuccess { statusText = it.message }
                                .onFailure { statusText = it.message ?: "Unable to open bookmark" }
                        } else {
                            playerChoices = playbackActions.playerChoices()
                            pendingPlayback = PendingPlayback.Bookmark(bookmark)
                        }
                        running = false
                    }
                },
                onRemove = {
                    scope.launch {
                        running = true
                        runCatching { browseActions.removeBookmark(bookmark.rowId) }
                            .onSuccess {
                                statusText = "Removed bookmark"
                                categories = browseActions.listBookmarkCategories()
                                bookmarks = browseActions.listBookmarks(query, selectedCategoryId)
                            }
                            .onFailure { statusText = it.message ?: "Unable to remove bookmark" }
                        running = false
                    }
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
                    }
                }
                    .onSuccess { statusText = it.message }
                    .onFailure { statusText = it.message ?: "Unable to open bookmark" }
                running = false
            }
        }
    )
}

@Composable
private fun BookmarkRow(
    bookmark: MobileBookmark,
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
                    FavouriteStar(
                        selected = true,
                        contentDescription = "Remove favourite ${bookmark.channelName}",
                        compact = true,
                        onClick = onRemove
                    )
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
                    "${bookmark.accountName} - ${bookmark.categoryTitle} - ${bookmark.mode.displayLabel()}",
                    color = DeepNightMutedText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
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

    fun reload() {
        scope.launch {
            running = true
            runCatching { browseActions.listWatchingNow(query) }
                .onSuccess {
                    items = it
                    statusText = if (it.isEmpty()) "Nothing to resume" else "${it.size} resume entries"
                }
                .onFailure { statusText = it.message ?: "Unable to load watching-now" }
            running = false
        }
    }

    fun play(pending: PendingPlayback) {
        scope.launch {
            running = true
            val preference = playbackActions.loadPlayerPreference()
            if (preference.rememberForFutureStreams && preference.selectedPlayer != AndroidPlayerPreference.ASK_EVERY_TIME) {
                runCatching {
                    when (pending) {
                        is PendingPlayback.Browse -> playbackActions.playBrowseItem(pending.item, preference.selectedPlayer, false)
                        is PendingPlayback.Bookmark -> playbackActions.playBookmark(pending.bookmark, preference.selectedPlayer, false)
                        is PendingPlayback.Watching -> playbackActions.playWatchingNow(pending.item, preference.selectedPlayer, false)
                        is PendingPlayback.WatchingEpisode -> playbackActions.playWatchingNowEpisode(pending.episode, preference.selectedPlayer, false)
                    }
                }
                    .onSuccess { statusText = it.message }
                    .onFailure { statusText = it.message ?: "Unable to resume" }
            } else {
                playerChoices = playbackActions.playerChoices()
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
                .onSuccess {
                    selectedSeries = item
                    seriesEpisodes = it
                    statusText = if (it.isEmpty()) "No cached episodes for ${item.title}" else "${it.size} cached episodes"
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
            onRemoveSeries = { removeWatchingNow(series) },
            modifier = modifier
        )
        PlaybackPickerDialog(
            pendingPlayback = pendingPlayback,
            playerChoices = playerChoices,
            playerIconRenderer = playerIconRenderer,
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
                        }
                    }
                        .onSuccess { statusText = it.message }
                        .onFailure { statusText = it.message ?: "Unable to resume" }
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
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    reload()
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search watching now") },
                colors = darkTextFieldColors()
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
        items(items, key = { "${it.mode}-${it.rowId}" }) { item ->
            WatchingNowRow(
                item = item,
                showThumbnail = showThumbnails,
                logoRenderer = logoRenderer,
                onOpen = { openWatchingNow(item) },
                onRemove = { removeWatchingNow(item) }
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
                    }
                }
                    .onSuccess { statusText = it.message }
                    .onFailure { statusText = it.message ?: "Unable to resume" }
                running = false
            }
        }
    )
}

@Composable
private fun WatchingNowRow(
    item: MobileWatchingNowItem,
    showThumbnail: Boolean,
    logoRenderer: LogoRenderer,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
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
                Text(
                    "${item.mode.displayLabel()} - ${item.subtitle}",
                    color = DeepNightMutedText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
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
    onRemoveSeries: () -> Unit,
    showRemove: Boolean = true,
    emptyTitle: String = "No cached episodes",
    emptyDetail: String = "Refresh this account on desktop or Android to cache episode links.",
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
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
        }
        if (episodes.isEmpty()) {
            item {
                EmptyState(
                    title = emptyTitle,
                    detail = emptyDetail
                )
            }
        }
        items(episodes, key = { "${it.parentRowId}-${it.episodeId}-${it.rowId}" }) { episode ->
            WatchingNowEpisodeRow(
                episode = episode,
                showThumbnail = showThumbnail,
                logoRenderer = logoRenderer,
                onPlay = { onPlayEpisode(episode) }
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

@Composable
private fun WatchingNowEpisodeRow(
    episode: MobileWatchingNowEpisode,
    showThumbnail: Boolean,
    logoRenderer: LogoRenderer,
    onPlay: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clickable(enabled = episode.command.isNotBlank(), onClick = onPlay),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = DeepNightSurfaceHigh)
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
                Text(episode.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Text(
                    listOf(
                        episode.season.takeIf { it.isNotBlank() }?.let { "S$it" },
                        episode.episodeNumber.takeIf { it.isNotBlank() }?.let { "E$it" },
                        episode.duration.takeIf { it.isNotBlank() }
                    ).filterNotNull().joinToString(" - ").ifBlank { episode.seriesTitle },
                    color = DeepNightMutedText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
    }
}

@Composable
private fun PlaybackPickerDialog(
    pendingPlayback: PendingPlayback?,
    playerChoices: List<PlayerChoice>,
    playerIconRenderer: PlayerIconRenderer,
    onDismiss: () -> Unit,
    onInstall: (PlayerChoice) -> Unit,
    onSelect: (AndroidPlayerPreference, Boolean) -> Unit
) {
    if (pendingPlayback == null) {
        return
    }
    var rememberChoice by remember(pendingPlayback) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DeepNightSurface,
        contentColor = DeepNightText
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Open Stream", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            PlayerChoiceGrid(
                choices = playerChoices,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    modifier = Modifier.semantics { contentDescription = "Remember player choice" },
                    checked = rememberChoice,
                    onCheckedChange = { rememberChoice = it }
                )
                Text("Always use this player")
            }
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PlayerSelectionSheet(
    title: String,
    choices: List<PlayerChoice>,
    selectedPlayer: AndroidPlayerPreference?,
    playerIconRenderer: PlayerIconRenderer,
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
private fun UserPill(providerName: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = DeepNightSurfaceHigh,
        contentColor = DeepNightText
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .width(28.dp)
                    .height(28.dp),
                shape = CircleShape,
                color = DeepNightAccent,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        providerName.trim().take(1).ifBlank { "U" }.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                providerName,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RemoteSyncScreen(
    syncActions: RemoteSyncUiActions,
    playbackActions: PlaybackUiActions,
    filterActions: FilterUiActions,
    backupRestoreActions: BackupRestoreUiActions,
    backupFileCreator: BackupFileCreator?,
    restoreFilePicker: RestoreFilePicker?,
    onThumbnailSettingChanged: (Boolean) -> Unit,
    playerIconRenderer: PlayerIconRenderer,
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

    val selectedPlayerChoice = remember(selectedPlayer, playerChoices) {
        (listOf(PlayerChoice(AndroidPlayerPreference.ASK_EVERY_TIME, "Ask")) + playerChoices)
            .firstOrNull { it.matchesSelected(selectedPlayer) }
            ?: PlayerChoice(selectedPlayer, selectedPlayer.playerLabel())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Host") },
            placeholder = { Text("Desktop IP or hostname") },
            colors = darkTextFieldColors()
        )
        OutlinedTextField(
            value = portText,
            onValueChange = { portText = it.filter(Char::isDigit).take(5) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Port") },
            placeholder = { Text("Desktop sync port") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = darkTextFieldColors()
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

    if (playerSelectorVisible) {
        PlayerSelectionSheet(
            title = "Default Player",
            choices = playerChoices,
            selectedPlayer = selectedPlayer,
            playerIconRenderer = playerIconRenderer,
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
private fun AccountsScreen(
    accountActions: AccountUiActions,
    onOpenAccountChannels: (MobileAccount) -> Unit,
    localPlaylistPicker: LocalPlaylistPicker?,
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
    var editorVisible by remember { mutableStateOf(false) }
    var accountFilter by remember { mutableStateOf(AccountFilter.ALL) }
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

    LaunchedEffect(accountActions) {
        accounts = accountActions.loadAccounts()
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
                        accounts = accountActions.loadAccounts()
                    }
                    break
                }
            }
            delay(1_000)
        }
    }

    val refreshAllJob = cacheDialogJob?.takeIf { it.action == CacheRefreshAction.REFRESH_ALL }
    if (refreshAllJob != null) {
        CacheRefreshProgressScreen(
            job = refreshAllJob,
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
            }
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
            val visibleAccounts = accounts.filter(accountFilter::matches)
            if (visibleAccounts.isEmpty()) {
                item {
                    EmptyState(
                        title = if (accounts.isEmpty()) "No accounts" else "No ${accountFilter.emptyLabel} accounts",
                        detail = if (accounts.isEmpty()) {
                            "Create an account here or pull accounts from desktop sync."
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
                            accounts = accountActions.loadAccounts()
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
                                    accounts = accountActions.loadAccounts()
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
                Icon(Icons.Outlined.MoreVert, contentDescription = null)
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
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
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onStop: () -> Unit
) {
    val progress = job.progressPercent.coerceIn(0, 100)
    val lines = remember(job.message) { cacheProgressLines(job.message) }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeepNightSurfaceHigh)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MobileAccountType.entries.forEach { type ->
                SelectableChip(
                    label = type.name.shortAccountType(),
                    selected = selectedType == type,
                    description = "Use ${type.displayName} account type",
                    onClick = { onTypeChange(type) }
                )
            }
        }
        AccountTextField("Name", account.accountName) { onAccountChange(account.copy(accountName = it)) }
        AccountTextField("URL", account.url) { onAccountChange(account.copy(url = it)) }
        if (selectedType == MobileAccountType.XTREME_API || selectedType == MobileAccountType.STALKER_PORTAL) {
            AccountTextField("Username", account.username) { onAccountChange(account.copy(username = it)) }
            AccountTextField("Password", account.password) { onAccountChange(account.copy(password = it)) }
        }
        if (selectedType == MobileAccountType.STALKER_PORTAL) {
            AccountTextField("MAC", account.macAddress) { onAccountChange(account.copy(macAddress = it)) }
            AccountTextField("MAC List", account.macAddressList) { onAccountChange(account.copy(macAddressList = it)) }
            AccountTextField("Serial", account.serialNumber) { onAccountChange(account.copy(serialNumber = it)) }
            AccountTextField("Device ID 1", account.deviceId1) { onAccountChange(account.copy(deviceId1 = it)) }
            AccountTextField("Device ID 2", account.deviceId2) { onAccountChange(account.copy(deviceId2 = it)) }
            AccountTextField("Signature", account.signature) { onAccountChange(account.copy(signature = it)) }
            AccountTextField("HTTP Method", account.httpMethod) { onAccountChange(account.copy(httpMethod = it)) }
            AccountTextField("Timezone", account.timezone) { onAccountChange(account.copy(timezone = it)) }
        }
        if (selectedType == MobileAccountType.M3U8_URL) {
            AccountTextField("Playlist", account.m3u8Path) { onAccountChange(account.copy(m3u8Path = it)) }
        }
        if (selectedType == MobileAccountType.M3U8_LOCAL) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = account.m3u8Path,
                    onValueChange = { onAccountChange(account.copy(m3u8Path = it)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Playlist file") },
                    colors = darkTextFieldColors()
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
        }
        AccountTextField("EPG", account.epg) { onAccountChange(account.copy(epg = it)) }
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
                enabled = account.accountName.isNotBlank()
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
}

@Composable
private fun AccountTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
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
private fun AppTabs(selectedTab: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf(
        BottomTab("Bookmarks", Icons.Outlined.Bookmarks),
        BottomTab("Accounts", Icons.Outlined.AccountCircle),
        BottomTab("Watching", Icons.Outlined.PlayCircle),
        BottomTab("Config", Icons.Outlined.Settings)
    )
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
    val removeBookmark: suspend (Long) -> Unit,
    val listWatchingNow: suspend (String) -> List<MobileWatchingNowItem>,
    val listWatchingNowEpisodes: suspend (MobileWatchingNowItem) -> List<MobileWatchingNowEpisode>,
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
                removeBookmark = {},
                listWatchingNow = {
                    listOf(MobileWatchingNowItem(1, 1, "Demo", BrowseMode.VOD, "Demo Movie", "Demo", updatedAtEpochSeconds = 1))
                },
                listWatchingNowEpisodes = { emptyList() },
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
        "RSS_FEED" -> "RSS"
        else -> this
    }

private fun String.shortJobId(): String =
    if (length <= 8) this else take(8)

private fun BrowseMode.displayLabel(): String =
    when (this) {
        BrowseMode.LIVE -> "itv"
        BrowseMode.VOD -> "vod"
        BrowseMode.SERIES -> "series"
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
    buildString {
        if (number.isNotBlank()) {
            append(number)
            append(" - ")
        }
        append(categoryTitle)
        append(" - ")
        append(mode.displayLabel())
        if (isHd) {
            append(" - HD")
        }
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
        contentId = channelId
    )

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

private sealed interface PendingPlayback {
    data class Browse(val item: MobileBrowseItem) : PendingPlayback
    data class Bookmark(val bookmark: MobileBookmark) : PendingPlayback
    data class Watching(val item: MobileWatchingNowItem) : PendingPlayback
    data class WatchingEpisode(val episode: MobileWatchingNowEpisode) : PendingPlayback
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
