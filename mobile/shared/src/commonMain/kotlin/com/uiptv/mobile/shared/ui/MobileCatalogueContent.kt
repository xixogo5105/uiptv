@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.uiptv.mobile.shared.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uiptv.mobile.shared.browse.BrowseMode
import com.uiptv.mobile.shared.browse.MobileBrowseItem
import com.uiptv.mobile.shared.i18n.MobileMessages

internal val LocalShowNavigation = staticCompositionLocalOf<(() -> Unit)?> { null }

internal data class MobileHeaderAddAction(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/** Shared app chrome keeps navigation and search in the same place at every window size. */
@Composable
internal fun MobileScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    searchVisible: Boolean = false,
    searchQuery: String = "",
    searchLabel: String = "Search",
    onSearchToggle: (() -> Unit)? = null,
    onSearchQueryChange: ((String) -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
    addMenuActions: List<MobileHeaderAddAction> = emptyList(),
    actions: @Composable RowScope.() -> Unit = {}
) {
    val showNavigation = LocalShowNavigation.current
    var addMenuExpanded by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            if (searchVisible && onSearchQueryChange != null) {
                MobileSearchField(searchQuery, searchLabel, onSearchQueryChange)
            } else {
                Column {
                    Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        navigationIcon = {
            if (onBack != null) MobileBackButton(onBack)
            else if (showNavigation != null) IconButton(onClick = showNavigation) {
                Icon(Icons.Outlined.Menu, contentDescription = "Show navigation")
            }
        },
        actions = {
            actions()
            if (onSearchToggle != null) IconButton(onClick = onSearchToggle) {
                Icon(if (searchVisible) Icons.Outlined.Close else Icons.Outlined.Search,
                    contentDescription = if (searchVisible) "Close search" else "Search")
            }
            if (onAdd != null || addMenuActions.isNotEmpty()) {
                Box {
                    IconButton(
                        onClick = {
                            if (addMenuActions.isNotEmpty()) addMenuExpanded = true else onAdd?.invoke()
                        },
                        modifier = Modifier.semantics {
                            contentDescription = MobileMessages.text("mobileAddAccount")
                        }
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                    }
                    if (addMenuActions.isNotEmpty()) {
                        DropdownMenu(
                            expanded = addMenuExpanded,
                            onDismissRequest = { addMenuExpanded = false },
                            shape = MaterialTheme.shapes.extraLarge,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            addMenuActions.forEach { action ->
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Surface(
                                            modifier = Modifier.size(40.dp),
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(action.icon, contentDescription = null)
                                            }
                                        }
                                    },
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(action.title, style = MaterialTheme.typography.labelLarge)
                                            Text(
                                                action.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        addMenuExpanded = false
                                        action.onClick()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        ),
        windowInsets = WindowInsets(0, 0, 0, 0)
    )
}

@Composable
internal fun MobileBackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Navigate back")
    }
}

@Composable
internal fun MobileSearchField(query: String, label: String, onChange: (String) -> Unit) {
    SearchBarDefaults.InputField(
        query = query,
        onQueryChange = onChange,
        onSearch = onChange,
        expanded = false,
        onExpandedChange = {},
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) {
                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
            }
        }
    )
}

@Composable
internal fun CatalogueContent(
    title: String, subtitle: String, mode: BrowseMode, modes: List<BrowseMode>, showingItems: Boolean,
    query: String, searchVisible: Boolean, onSearch: () -> Unit, onQueryChange: (String) -> Unit,
    onBack: () -> Unit, onModeChange: (BrowseMode) -> Unit, running: Boolean, status: String,
    onRetry: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground) {
      Column(Modifier.fillMaxSize()) {
        MobileScreenHeader(title, subtitle, onBack, searchVisible, query,
            if (!showingItems) "Search categories" else if (mode == BrowseMode.LIVE) "Search channels" else "Search titles",
            onSearch, onQueryChange)
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!showingItems && modes.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(modes, key = { it.name }) { entry ->
                    FilterChip(selected = entry == mode, onClick = { onModeChange(entry) }, label = {
                        Text(when (entry) { BrowseMode.LIVE -> "Live TV"; BrowseMode.VOD -> "Movies"; BrowseMode.SERIES -> "Series" })
                    })
                }
            }
            if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (status.startsWith("Unable")) {
                Text(status, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Retry") }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = if (showingItems && mode != BrowseMode.LIVE) 1400.dp else 840.dp).fillMaxSize(), content = content)
            }
        }
      }
    }
}

@Composable
internal fun CatalogueItems(
    items: List<MobileBrowseItem>, mode: BrowseMode, loading: Boolean, searching: Boolean,
    showThumbnails: Boolean, logoRenderer: LogoRenderer,
    onPlay: (MobileBrowseItem) -> Unit, onChoosePlayer: (MobileBrowseItem) -> Unit,
    onToggleBookmark: (MobileBrowseItem) -> Unit
) {
    if (items.isEmpty() && !loading) {
        EmptyState(if (searching) "No matches" else "Nothing cached here",
            if (searching) "Clear search to see every item in this category." else "Refresh this account from Accounts to update its catalogue.")
    } else if (mode == BrowseMode.LIVE) {
        LazyColumn(state = rememberLazyListState(), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { "${it.accountId}:${it.mode}:${it.rowId}:${it.categoryRowId}" }, contentType = { "channel" }) { item ->
                BrowseItemRow(item, showThumbnails, logoRenderer, { onPlay(item) }, { onChoosePlayer(item) }, { onToggleBookmark(item) })
            }
        }
    } else {
        LazyVerticalGrid(columns = GridCells.Adaptive(148.dp), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(items, key = { "${it.accountId}:${it.mode}:${it.rowId}:${it.categoryRowId}" }, contentType = { "poster" }) { item ->
                MobilePosterCard(item.name, item.logo, showThumbnails, logoRenderer, { onPlay(item) },
                    onLongClick = { onChoosePlayer(item) }, secondaryAction = {
                        IconButton(onClick = { onToggleBookmark(item) }) {
                            Icon(if (item.isBookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (item.isBookmarked) "Remove bookmark ${item.name}" else "Bookmark ${item.name}")
                        }
                    })
            }
        }
    }
}

@Composable
internal fun MobilePosterCard(
    title: String, logo: String, showThumbnail: Boolean, logoRenderer: LogoRenderer,
    onClick: () -> Unit, onLongClick: (() -> Unit)? = null, secondaryAction: @Composable () -> Unit = {}
) {
    Card(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Movie, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showThumbnail && logo.isNotBlank()) logoRenderer(logo, title, Modifier.fillMaxSize())
        }
        Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
            Text(title, modifier = Modifier.weight(1f).padding(top = 8.dp), style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            secondaryAction()
        }
    }
}

@Composable
internal fun SettingsSectionHeading(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(Modifier.padding(bottom = 12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ItemActionsMenu(title: String, onChoosePlayer: () -> Unit, onRemove: () -> Unit, removalLabel: String) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Actions for $title") }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Choose player") }, onClick = { expanded = false; onChoosePlayer() })
            HorizontalDivider()
            DropdownMenuItem(text = { Text(removalLabel, color = MaterialTheme.colorScheme.error) }, onClick = { expanded = false; onRemove() })
        }
    }
}

@Composable
internal fun BookmarkLibrary(
    categories: List<com.uiptv.mobile.shared.browse.MobileBookmarkCategory>, selectedCategoryId: String?,
    bookmarks: List<com.uiptv.mobile.shared.browse.MobileBookmark>, query: String, searchVisible: Boolean,
    running: Boolean, status: String, showThumbnails: Boolean, logoRenderer: LogoRenderer,
    onSearch: () -> Unit, onQueryChange: (String) -> Unit,
    onCategorySelect: (com.uiptv.mobile.shared.browse.MobileBookmarkCategory) -> Unit,
    onPlay: (com.uiptv.mobile.shared.browse.MobileBookmark) -> Unit,
    onChoosePlayer: (com.uiptv.mobile.shared.browse.MobileBookmark) -> Unit,
    onRemove: (com.uiptv.mobile.shared.browse.MobileBookmark) -> Unit, modifier: Modifier = Modifier
) {
    val recent = selectedCategoryId == com.uiptv.mobile.shared.browse.RECENTLY_PLAYED_BOOKMARKS_CATEGORY_ID
    Column(modifier.fillMaxSize()) {
        MobileScreenHeader("Bookmarks", "Your saved channels and titles", searchVisible = searchVisible,
            searchQuery = query, searchLabel = "Search bookmarks", onSearchToggle = onSearch,
            onSearchQueryChange = onQueryChange)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories, key = { it.id ?: "all" }) { category ->
                    FilterChip(selected = selectedCategoryId == category.id, onClick = { onCategorySelect(category) }, label = { Text(category.name) })
                }
            }
            if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (status.startsWith("Unable")) Text(status, color = MaterialTheme.colorScheme.error)
            if (bookmarks.isEmpty() && !running) EmptyState(
                when { query.isNotBlank() -> "No matches"; recent -> "Nothing played recently"; else -> "No bookmarks here" },
                when { query.isNotBlank() -> "Clear search to see saved items."; recent -> "Play a bookmark and it will appear here."; else -> "Browse an account and tap the bookmark icon to save a channel or title." })
            LazyVerticalGrid(columns = GridCells.Adaptive(148.dp), modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(bookmarks, key = { "${it.accountId}:${it.rowId}" },
                    span = { androidx.compose.foundation.lazy.grid.GridItemSpan(if (it.mode == BrowseMode.LIVE) maxLineSpan else 1) },
                    contentType = { it.mode }) { bookmark ->
                    if (bookmark.mode == BrowseMode.LIVE) BookmarkRow(bookmark, recent, showThumbnails, logoRenderer,
                        { onPlay(bookmark) }, { onChoosePlayer(bookmark) }, { onRemove(bookmark) })
                    else MobilePosterCard(bookmark.channelName, bookmark.logo, showThumbnails, logoRenderer,
                        { onPlay(bookmark) }, { onChoosePlayer(bookmark) }, secondaryAction = {
                            ItemActionsMenu(bookmark.channelName, { onChoosePlayer(bookmark) }, { onRemove(bookmark) },
                                if (recent) "Remove from Recent" else "Remove bookmark")
                        })
                }
            }
        }
    }
}
