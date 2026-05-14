package com.uiptv.mobile.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
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
import com.uiptv.mobile.shared.settings.PlayerPreference
import com.uiptv.mobile.shared.sync.RemoteSyncProgress
import com.uiptv.mobile.shared.sync.RemoteSyncProgressStep
import com.uiptv.mobile.shared.sync.RemoteSyncPullResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

typealias LocalPlaylistPicker = (onSelected: (String) -> Unit) -> Unit
typealias LogoRenderer = @Composable (String, String, Modifier) -> Unit

private val UiptvDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8FD7FF),
    onPrimary = Color(0xFF0B1A22),
    primaryContainer = Color(0xFF244B5F),
    onPrimaryContainer = Color(0xFFEAF7FF),
    secondary = Color(0xFFA7D8B8),
    onSecondary = Color(0xFF102117),
    tertiary = Color(0xFFFFD54F),
    onTertiary = Color(0xFF221A00),
    background = Color(0xFF101418),
    onBackground = Color(0xFFF4F7FA),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFF4F7FA),
    surfaceVariant = Color(0xFF172029),
    onSurfaceVariant = Color(0xFFAEB8C2),
    outline = Color(0xFF60707D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun UiptvMobileApp(
    syncActions: RemoteSyncUiActions = RemoteSyncUiActions.preview(),
    accountActions: AccountUiActions = AccountUiActions.preview(),
    browseActions: BrowseUiActions = BrowseUiActions.preview(),
    playbackActions: PlaybackUiActions = PlaybackUiActions.preview(),
    filterActions: FilterUiActions = FilterUiActions.preview(),
    localPlaylistPicker: LocalPlaylistPicker? = null,
    logoRenderer: LogoRenderer = { _, _, _ -> },
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> }
) {
    var selectedTab by remember { mutableIntStateOf(0) }
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

    MaterialTheme(colorScheme = UiptvDarkColorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF101418),
            contentColor = Color(0xFFF4F7FA)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val wideLayout = maxWidth >= 720.dp
                if (wideLayout) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        AppTabs(selectedTab = selectedTab, onSelect = ::selectTab, vertical = true)
                        Column(modifier = Modifier.fillMaxSize()) {
                            CurrentTab(
                                selectedTab = selectedTab,
                                syncActions = syncActions,
                                accountActions = accountActions,
                                browseActions = browseActions,
                                playbackActions = playbackActions,
                                filterActions = filterActions,
                                localPlaylistPicker = localPlaylistPicker,
                                selectedBrowseAccount = selectedBrowseAccount,
                                showThumbnails = showThumbnails,
                                logoRenderer = logoRenderer,
                                onOpenAccountChannels = { account ->
                                    selectedBrowseAccount = account
                                },
                                onCloseAccountChannels = { selectedBrowseAccount = null },
                                onThumbnailSettingChanged = { showThumbnails = it },
                                backHandler = backHandler,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        CurrentTab(
                            selectedTab = selectedTab,
                            syncActions = syncActions,
                            accountActions = accountActions,
                            browseActions = browseActions,
                            playbackActions = playbackActions,
                            filterActions = filterActions,
                            localPlaylistPicker = localPlaylistPicker,
                            selectedBrowseAccount = selectedBrowseAccount,
                            showThumbnails = showThumbnails,
                            logoRenderer = logoRenderer,
                            onOpenAccountChannels = { account ->
                                selectedBrowseAccount = account
                            },
                            onCloseAccountChannels = { selectedBrowseAccount = null },
                            onThumbnailSettingChanged = { showThumbnails = it },
                            backHandler = backHandler,
                            modifier = Modifier.weight(1f)
                        )
                        AppTabs(selectedTab = selectedTab, onSelect = ::selectTab, vertical = false)
                    }
                }
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
    localPlaylistPicker: LocalPlaylistPicker?,
    selectedBrowseAccount: MobileAccount?,
    showThumbnails: Boolean,
    logoRenderer: LogoRenderer,
    onOpenAccountChannels: (MobileAccount) -> Unit,
    onCloseAccountChannels: () -> Unit,
    onThumbnailSettingChanged: (Boolean) -> Unit,
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        when (selectedTab) {
            0 -> {
                BookmarksScreen(browseActions, playbackActions, showThumbnails, logoRenderer, Modifier.weight(1f))
            }
            1 -> {
                val account = selectedBrowseAccount
                if (account == null) {
                    AccountsScreen(accountActions, onOpenAccountChannels, localPlaylistPicker, Modifier.weight(1f))
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
                        backHandler = backHandler,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            2 -> {
                WatchingNowScreen(browseActions, playbackActions, showThumbnails, logoRenderer, Modifier.weight(1f))
            }
            3 -> {
                RemoteSyncScreen(syncActions, playbackActions, filterActions, onThumbnailSettingChanged, Modifier.weight(1f))
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

    val visibleModes = remember(requestedAccountType) {
        when (requestedAccountType) {
            MobileAccountType.XTREME_API, MobileAccountType.STALKER_PORTAL -> BrowseMode.entries
            else -> listOf(BrowseMode.LIVE)
        }
    }

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

    LaunchedEffect(browseActions, requestedAccountId, requestedAccountType) {
        val activeMode = if (mode in visibleModes) mode else BrowseMode.LIVE
        mode = activeMode
        selectedCategoryRowId = null
        channelQuery = ""
        reload(requestedAccountId, null, "", activeMode)
    }

    val selectedCategory = snapshot.categories.firstOrNull { it.rowId == selectedCategoryRowId }
    val visibleCategories = snapshot.categories.filter { category ->
        categoryQuery.isBlank() || category.title.contains(categoryQuery.trim(), ignoreCase = true)
    }
    backHandler(selectedCategoryRowId != null) {
        selectedCategoryRowId = null
        channelQuery = ""
        reload(snapshot.selectedAccountId, null, "")
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compactBrowseToolbar = maxWidth > maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        if (selectedCategoryRowId == null) {
            if (showAccountSelector) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    snapshot.accounts.forEach { account ->
                        SelectableChip(
                            label = account.name,
                            selected = snapshot.selectedAccountId == account.id,
                            description = "Select account ${account.name}",
                            onClick = {
                                selectedCategoryRowId = null
                                channelQuery = ""
                                reload(account.id, null, "")
                            }
                        )
                    }
                }
            }
            if (onBackToAccounts != null && compactBrowseToolbar) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompactToolbarAction("Accounts", "Back to account list", onBackToAccounts)
                    Text(
                        requestedAccountName ?: snapshot.accounts.firstOrNull { it.id == snapshot.selectedAccountId }?.name ?: "Account",
                        modifier = Modifier.widthIn(min = 56.dp, max = 96.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    visibleModes.forEach { entry ->
                        CompactModeChip(
                            label = entry.displayLabel(),
                            selected = mode == entry,
                            onClick = {
                                mode = entry
                                selectedCategoryRowId = null
                                channelQuery = ""
                                reload(snapshot.selectedAccountId, null, "", entry)
                            }
                        )
                    }
                    OutlinedTextField(
                        value = categoryQuery,
                        onValueChange = { categoryQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        singleLine = true,
                        placeholder = { Text("Search") },
                        colors = darkTextFieldColors()
                    )
                }
            } else {
                if (onBackToAccounts != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            modifier = Modifier.semantics { contentDescription = "Back to account list" },
                            onClick = onBackToAccounts
                        ) {
                            Text("Accounts")
                        }
                        Text(
                            requestedAccountName ?: snapshot.accounts.firstOrNull { it.id == snapshot.selectedAccountId }?.name ?: "Account",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    visibleModes.forEach { entry ->
                        SelectableChip(
                            label = entry.displayLabel(),
                            selected = mode == entry,
                            description = "Show ${entry.displayLabel()} channels",
                            onClick = {
                                mode = entry
                                selectedCategoryRowId = null
                                channelQuery = ""
                                reload(snapshot.selectedAccountId, null, "", entry)
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value = categoryQuery,
                    onValueChange = { categoryQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search categories") },
                    colors = darkTextFieldColors()
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
                        onClick = {
                            selectedCategoryRowId = category.rowId
                            channelQuery = ""
                            reload(snapshot.selectedAccountId, category.rowId, "")
                        }
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    modifier = Modifier.semantics { contentDescription = "Back to category list" },
                    onClick = {
                        selectedCategoryRowId = null
                        channelQuery = ""
                        reload(snapshot.selectedAccountId, null, "")
                    }
                ) {
                    Text("Categories")
                }
                Text(
                    selectedCategory?.title ?: "Category",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedTextField(
                value = channelQuery,
                onValueChange = {
                    channelQuery = it
                    reload(snapshot.selectedAccountId, selectedCategoryRowId, it)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search channels") },
                colors = darkTextFieldColors()
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
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
                                val preference = playbackActions.loadPlayerPreference()
                                if (preference.rememberForFutureStreams && preference.selectedPlayer != AndroidPlayerPreference.ASK_EVERY_TIME) {
                                    runCatching { playbackActions.playBrowseItem(item, preference.selectedPlayer, false) }
                                        .onSuccess { statusText = it.message }
                                        .onFailure { statusText = it.message ?: "Unable to open stream" }
                                } else {
                                    playerChoices = playbackActions.playerChoices()
                                    pendingPlayback = PendingPlayback.Browse(item)
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
        if (running) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Text(statusText, color = Color(0xFFAEB8C2), style = MaterialTheme.typography.bodySmall)
        }
    }

    PlaybackPickerDialog(
        pendingPlayback = pendingPlayback,
        playerChoices = playerChoices,
        onDismiss = { pendingPlayback = null },
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
            .background(Color(0xFF24313C))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        color = Color(0xFFF4F7FA),
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
            .background(if (selected) Color(0xFF8FD7FF) else Color(0xFF24313C))
            .clickable(enabled = !selected, onClick = onClick)
            .semantics { contentDescription = "Show $label channels" }
            .padding(horizontal = 7.dp, vertical = 10.dp),
        color = if (selected) Color(0xFF101418) else Color(0xFFF4F7FA),
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
            .background(if (selected) Color(0xFFEAF7FF) else Color(0xFF24313C))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        color = if (selected) Color(0xFF101418) else Color(0xFFF4F7FA),
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
            .background(Color(0xFF172029))
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
                color = Color(0xFFAEB8C2),
                style = MaterialTheme.typography.bodySmall
            )
        }
        TextButton(
            modifier = Modifier.semantics { contentDescription = "Open category ${category.title}" },
            onClick = onClick
        ) {
            Text("Open")
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
            .background(if (selected) Color(0xFF2E5C75) else Color(0xFF202A33))
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .background(Color(0xFF172029))
            .clickable(enabled = item.command.isNotBlank(), onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FavouriteStar(
            selected = item.isBookmarked,
            contentDescription = if (item.isBookmarked) {
                "Remove favourite ${item.name}"
            } else {
                "Add favourite ${item.name}"
            },
            onClick = onToggleBookmark
        )
        if (showThumbnail && item.logo.isNotBlank()) {
            logoRenderer(
                item.logo,
                "Logo ${item.name}",
                Modifier
                    .width(44.dp)
                    .height(44.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle(), color = Color(0xFFAEB8C2), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FavouriteStar(selected: Boolean, contentDescription: String, onClick: () -> Unit) {
    Text(
        text = if (selected) "★" else "☆",
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        color = if (selected) Color(0xFFFFD54F) else Color(0xFFF4F7FA),
        fontSize = 28.sp,
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
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    SelectableChip(
                        label = category.name,
                        selected = selectedCategoryId == category.id,
                        description = "Show bookmark tab ${category.name}",
                        onClick = {
                            selectedCategoryId = category.id
                            reload(category.id)
                        }
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
            Text(statusText, color = Color(0xFFAEB8C2), style = MaterialTheme.typography.bodySmall)
        }
    }

    PlaybackPickerDialog(
        pendingPlayback = pendingPlayback,
        playerChoices = playerChoices,
        onDismiss = { pendingPlayback = null },
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .background(Color(0xFF172029))
            .clickable(enabled = bookmark.command.isNotBlank(), onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FavouriteStar(
            selected = true,
            contentDescription = "Remove favourite ${bookmark.channelName}",
            onClick = onRemove
        )
        if (showThumbnail && bookmark.logo.isNotBlank()) {
            logoRenderer(
                bookmark.logo,
                "Logo ${bookmark.channelName}",
                Modifier
                    .width(44.dp)
                    .height(44.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(bookmark.channelName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${bookmark.accountName} - ${bookmark.categoryTitle} - ${bookmark.mode.displayLabel()}", color = Color(0xFFAEB8C2), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WatchingNowScreen(
    browseActions: BrowseUiActions,
    playbackActions: PlaybackUiActions,
    showThumbnails: Boolean,
    logoRenderer: LogoRenderer,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<MobileWatchingNowItem>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Loading") }
    var running by remember { mutableStateOf(false) }
    var pendingPlayback by remember { mutableStateOf<PendingPlayback?>(null) }
    var playerChoices by remember { mutableStateOf<List<PlayerChoice>>(emptyList()) }

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
                onPlay = {
                    scope.launch {
                        running = true
                        val preference = playbackActions.loadPlayerPreference()
                        if (preference.rememberForFutureStreams && preference.selectedPlayer != AndroidPlayerPreference.ASK_EVERY_TIME) {
                            runCatching { playbackActions.playWatchingNow(item, preference.selectedPlayer, false) }
                                .onSuccess { statusText = it.message }
                                .onFailure { statusText = it.message ?: "Unable to resume" }
                        } else {
                            playerChoices = playbackActions.playerChoices()
                            pendingPlayback = PendingPlayback.Watching(item)
                        }
                        running = false
                    }
                }
            )
        }
        item {
            if (running) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(statusText, color = Color(0xFFAEB8C2), style = MaterialTheme.typography.bodySmall)
        }
    }

    PlaybackPickerDialog(
        pendingPlayback = pendingPlayback,
        playerChoices = playerChoices,
        onDismiss = { pendingPlayback = null },
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
    onPlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .background(Color(0xFF172029))
            .clickable(enabled = item.command.isNotBlank(), onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showThumbnail && item.logo.isNotBlank()) {
            logoRenderer(
                item.logo,
                "Logo ${item.title}",
                Modifier
                    .width(44.dp)
                    .height(44.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${item.mode.displayLabel()} - ${item.subtitle}", color = Color(0xFFAEB8C2), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PlaybackPickerDialog(
    pendingPlayback: PendingPlayback?,
    playerChoices: List<PlayerChoice>,
    onDismiss: () -> Unit,
    onSelect: (AndroidPlayerPreference, Boolean) -> Unit
) {
    if (pendingPlayback == null) {
        return
    }
    var rememberChoice by remember(pendingPlayback) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open Stream") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                playerChoices.forEach { choice ->
                    TextButton(
                        modifier = Modifier.semantics { contentDescription = "Open with ${choice.label}" },
                        enabled = choice.installed,
                        onClick = { onSelect(choice.player, rememberChoice) }
                    ) {
                        Text(if (choice.installed) choice.label else "${choice.label} not installed")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        modifier = Modifier.semantics { contentDescription = "Remember player choice" },
                        checked = rememberChoice,
                        onCheckedChange = { rememberChoice = it }
                    )
                    Text("Remember")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RemoteSyncScreen(
    syncActions: RemoteSyncUiActions,
    playbackActions: PlaybackUiActions,
    filterActions: FilterUiActions,
    onThumbnailSettingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("localhost") }
    var portText by remember { mutableStateOf("8080") }
    var verificationCode by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Use localhost:8080 with adb reverse, or 10.0.2.2:8080 on the emulator.") }
    var running by remember { mutableStateOf(false) }
    var lastSyncText by remember { mutableStateOf("Never synced") }
    var playerText by remember { mutableStateOf("Player: ask every time") }
    var playerChoices by remember { mutableStateOf<List<PlayerChoice>>(emptyList()) }
    var confirmReset by remember { mutableStateOf(false) }
    var filters by remember { mutableStateOf(AndroidFilterSettings()) }
    var categoryFilterText by remember { mutableStateOf("") }
    var channelFilterText by remember { mutableStateOf("") }
    var filterEditorVisible by remember { mutableStateOf(false) }

    LaunchedEffect(syncActions) {
        val snapshot = syncActions.loadPreferences()
        val savedHost = snapshot.remoteEndpoint.host
        host = savedHost.ifBlank { "localhost" }
        portText = if (savedHost.isBlank() && snapshot.remoteEndpoint.port == 8888) {
            "8080"
        } else {
            snapshot.remoteEndpoint.port.toString()
        }
        lastSyncText = snapshot.remoteEndpoint.lastSuccessfulSyncEpochSeconds
            ?.let { "Last sync: $it" }
            ?: "Never synced"
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
            placeholder = { Text("localhost, 10.0.2.2, or desktop IP") },
            colors = darkTextFieldColors()
        )
        OutlinedTextField(
            value = portText,
            onValueChange = { portText = it.filter(Char::isDigit).take(5) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Port") },
            placeholder = { Text("8080") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = darkTextFieldColors()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                modifier = Modifier.semantics { contentDescription = "Test desktop sync connection" },
                enabled = !running && host.isNotBlank(),
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
                enabled = !running && host.isNotBlank(),
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
        Text(statusText, color = Color(0xFFAEB8C2), style = MaterialTheme.typography.bodyMedium)
        Text(lastSyncText, color = Color(0xFFAEB8C2), style = MaterialTheme.typography.bodySmall)
        Text(playerText, color = Color(0xFFAEB8C2), style = MaterialTheme.typography.bodySmall)
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
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                enabled = !running,
                onClick = {
                    scope.launch {
                        running = true
                        runCatching { playbackActions.clearPlayerPreference() }
                            .onSuccess {
                                playerText = "Player: ask every time"
                                statusText = "Default player cleared"
                            }
                            .onFailure { statusText = it.message ?: "Unable to clear player preference" }
                        running = false
                    }
                }
            ) {
                Text("Ask")
            }
            playerChoices.forEach { choice ->
                TextButton(
                    enabled = !running && choice.installed,
                    onClick = {
                        scope.launch {
                            running = true
                            runCatching { playbackActions.savePlayerPreference(choice.player) }
                                .onSuccess {
                                    playerText = "Player: ${choice.player.playerLabel()}"
                                    statusText = "Default player set to ${choice.label}"
                                }
                                .onFailure { statusText = it.message ?: "Unable to save player preference" }
                            running = false
                        }
                    }
                ) {
                    Text(if (choice.installed) choice.label else "${choice.label} unavailable")
                }
            }
        }
        Button(
            modifier = Modifier.semantics { contentDescription = "Clear remembered player preference" },
            enabled = !running,
            onClick = {
                scope.launch {
                    running = true
                    runCatching { playbackActions.clearPlayerPreference() }
                        .onSuccess {
                            playerText = "Player: ask every time"
                            statusText = "Player preference cleared"
                        }
                        .onFailure { statusText = it.message ?: "Unable to clear player preference" }
                    running = false
                }
            }
        ) {
            Text("Clear Player")
        }
        Text("Filters", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            if (filters.paused) {
                "Current state: filtering is paused. Matching categories and channels are visible."
            } else {
                "Current state: filtering is active. Matching categories and channels are hidden."
            },
            color = Color(0xFFAEB8C2),
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
            color = Color(0xFFAEB8C2),
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

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.semantics { contentDescription = "Create new account" },
                    onClick = {
                        editing = MobileAccount()
                        selectedType = MobileAccountType.STALKER_PORTAL
                        statusText = "New account"
                        editorVisible = true
                    }
                ) {
                    Text("New")
                }
                Button(
                    modifier = Modifier.semantics { contentDescription = "Reload accounts" },
                    enabled = !running,
                    onClick = { reload() }
                ) {
                    Text("Reload")
                }
                Button(
                    modifier = Modifier.semantics { contentDescription = "Clear all cached channels" },
                    enabled = !running,
                    onClick = {
                    enqueueCacheJob(CacheRefreshJobRequest(CacheRefreshAction.CLEAR_ALL_CACHE), "Queued clear all cache")
                }) {
                    Text("Clear All")
                }
                Button(
                    modifier = Modifier.semantics { contentDescription = "Refresh all account caches" },
                    enabled = !running,
                    onClick = {
                    enqueueCacheJob(CacheRefreshJobRequest(CacheRefreshAction.REFRESH_ALL), "Queued refresh all")
                }) {
                    Text("Refresh All")
                }
            }
        }
        if (accounts.isEmpty()) {
            item {
                EmptyState(
                    title = "No accounts",
                    detail = "Create an account here or pull accounts from desktop sync."
                )
            }
        }
        items(accounts, key = { it.id ?: it.accountName }) { account ->
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
            Text(statusText, color = Color(0xFFAEB8C2), style = MaterialTheme.typography.bodySmall)
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

    cacheDialogJob?.let { job ->
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .background(Color(0xFF172029))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(account.accountName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(account.type.displayName, color = Color(0xFFAEB8C2), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        TextButton(
            modifier = Modifier.semantics { contentDescription = "Edit account ${account.accountName}" },
            onClick = onEdit
        ) {
            Text("Edit")
        }
        TextButton(
            modifier = Modifier.semantics { contentDescription = "Clear cache for ${account.accountName}" },
            enabled = account.canRefreshCache,
            onClick = onClearCache
        ) {
            Text("Clear")
        }
        TextButton(
            modifier = Modifier.semantics { contentDescription = "Refresh cache for ${account.accountName}" },
            enabled = account.canRefreshCache,
            onClick = onRefreshCache
        ) {
            Text("Refresh")
        }
        TextButton(
            modifier = Modifier.semantics { contentDescription = "Delete account ${account.accountName}" },
            enabled = account.id != null,
            onClick = onDelete
        ) {
            Text("Delete")
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
                    color = Color(0xFFAEB8C2),
                    style = MaterialTheme.typography.bodyMedium
                )
                summaryLines.forEach { line ->
                    Text(line, color = Color(0xFFF4F7FA), style = MaterialTheme.typography.bodySmall)
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
private fun AccountFeedbackDialog(feedback: AccountFeedback, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(feedback.title) },
        text = {
            Text(
                feedback.message,
                color = if (feedback.success) Color(0xFFAEB8C2) else Color(0xFFFFB4AB)
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
            .background(Color(0xFF172029))
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
        focusedTextColor = Color(0xFFF4F7FA),
        unfocusedTextColor = Color(0xFFF4F7FA),
        disabledTextColor = Color(0xFF7D8790),
        errorTextColor = Color(0xFFFFD6D6),
        cursorColor = Color(0xFF8FD7FF),
        focusedLabelColor = Color(0xFF8FD7FF),
        unfocusedLabelColor = Color(0xFFAEB8C2),
        disabledLabelColor = Color(0xFF7D8790),
        errorLabelColor = Color(0xFFFFB4AB),
        focusedPlaceholderColor = Color(0xFF7D8790),
        unfocusedPlaceholderColor = Color(0xFF7D8790),
        focusedBorderColor = Color(0xFF8FD7FF),
        unfocusedBorderColor = Color(0xFF60707D),
        disabledBorderColor = Color(0xFF39444D),
        errorBorderColor = Color(0xFFFFB4AB),
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent
    )

@Composable
private fun AppTabs(selectedTab: Int, onSelect: (Int) -> Unit, vertical: Boolean) {
    val tabs = listOf("Bookmarks", "Accounts", "Watching", "Config")
    if (vertical) {
        NavigationRail(containerColor = Color(0xFF182028), contentColor = Color(0xFFF4F7FA)) {
            tabs.forEachIndexed { index, label ->
                NavigationRailItem(
                    modifier = Modifier.semantics { contentDescription = "Open $label" },
                    selected = index == selectedTab,
                    onClick = { onSelect(index) },
                    icon = {},
                    label = { Text(label) }
                )
            }
        }
    } else {
        NavigationBar(containerColor = Color(0xFF182028), contentColor = Color(0xFFF4F7FA)) {
            tabs.forEachIndexed { index, label ->
                NavigationBarItem(
                    modifier = Modifier.semantics { contentDescription = "Open $label" },
                    selected = index == selectedTab,
                    onClick = { onSelect(index) },
                    icon = {},
                    label = { Text(label) }
                )
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, detail: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 96.dp)
            .background(Color(0xFF172029))
            .padding(14.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(detail, color = Color(0xFFAEB8C2), style = MaterialTheme.typography.bodySmall)
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
    val listWatchingNow: suspend (String) -> List<MobileWatchingNowItem>
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
                }
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
    val savePlayerPreference: suspend (AndroidPlayerPreference) -> Unit,
    val clearPlayerPreference: suspend () -> Unit
) {
    companion object {
        fun preview(): PlaybackUiActions =
            PlaybackUiActions(
                loadPlayerPreference = { PlayerPreference() },
                playerChoices = {
                    listOf(
                        PlayerChoice(AndroidPlayerPreference.NATIVE, "Native"),
                        PlayerChoice(AndroidPlayerPreference.SYSTEM_CHOOSER, "System")
                    )
                },
                playBrowseItem = { item, _, _ -> PlaybackLaunchResult(true, "Opening ${item.name}") },
                playBookmark = { bookmark, _, _ -> PlaybackLaunchResult(true, "Opening ${bookmark.channelName}") },
                playWatchingNow = { item, _, _ -> PlaybackLaunchResult(true, "Opening ${item.title}") },
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

private fun AndroidPlayerPreference.playerLabel(): String =
    when (this) {
        AndroidPlayerPreference.ASK_EVERY_TIME -> "ask every time"
        AndroidPlayerPreference.NATIVE -> "Native"
        AndroidPlayerPreference.VLC -> "VLC"
        AndroidPlayerPreference.MX_PLAYER_PRO -> "MX Player Pro"
        AndroidPlayerPreference.MX_PLAYER_FREE -> "MX Player Free"
        AndroidPlayerPreference.KODI -> "Kodi"
        AndroidPlayerPreference.JUST_PLAYER -> "Just Player"
        AndroidPlayerPreference.XPLAYER -> "XPlayer"
        AndroidPlayerPreference.SYSTEM_CHOOSER -> "System chooser"
    }

private sealed interface PendingPlayback {
    data class Browse(val item: MobileBrowseItem) : PendingPlayback
    data class Bookmark(val bookmark: MobileBookmark) : PendingPlayback
    data class Watching(val item: MobileWatchingNowItem) : PendingPlayback
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
    message
        .split(" | ")
        .firstOrNull()
        ?.trim()
        .orEmpty()

private fun cacheSummaryLines(message: String): List<String> {
    if (message.isBlank()) {
        return emptyList()
    }
    return message
        .split(" | ")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .drop(1)
}
