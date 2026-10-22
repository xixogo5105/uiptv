package com.uiptv.mobile.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.uiptv.mobile.shared.epg.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Repository actions keep IO and parsing outside composables. */
data class EpgUiActions(
    val clearCache: suspend () -> Unit = {},
    val loadEnabled: suspend () -> Boolean = { false },
    val saveEnabled: suspend (Boolean) -> Unit = {},
    val loadPlaybackOnly: suspend () -> Boolean = { false },
    val savePlaybackOnly: suspend (Boolean) -> Unit = {},
    val globalSource: suspend () -> String = { "" },
    val saveGlobalSource: suspend (String) -> Unit = {},
    val schedule: suspend (Long, String, String) -> MobileEpgResult = { _, _, _ -> MobileEpgResult() },
    val saveMapping: suspend (Long, String, String) -> Unit = { _, _, _ -> },
    val icon: suspend (Long, String, String) -> String = { _, _, _ -> "" }
)
internal data class EpgUiState(
    val enabled: Boolean = false,
    val playbackOnly: Boolean = false,
    val time: Long = 0,
    val actions: EpgUiActions = EpgUiActions(),
    val onEnabled: (Boolean) -> Unit = {},
    val onPlaybackOnly: (Boolean) -> Unit = {}
)

/** Uses the Material 3 Switch and the current app color-scheme tokens consistently. */
@Composable
internal fun MobileM3Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        modifier = modifier,
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors()
    )
}
val LocalThumbnailFallback = staticCompositionLocalOf { "" }
val LocalThumbnailFallbackLoader = staticCompositionLocalOf<(suspend () -> String)?> { null }
internal val LocalEpgState = staticCompositionLocalOf { EpgUiState() }

@Composable
internal fun epgThumbnail(accountId: Long, providerId: String, name: String, enabled: Boolean): (suspend () -> String)? {
    val state = LocalEpgState.current
    return remember(accountId, providerId, name, enabled, state.enabled, state.playbackOnly, state.actions, state.time / (30 * 60_000)) {
        if (enabled && state.enabled && !state.playbackOnly) suspend { state.actions.icon(accountId, providerId, name) } else null
    }
}

@Composable
internal fun MobileEpgSettings() {
    val state = LocalEpgState.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(state.actions) {
        runCatching { state.actions.globalSource() }.onSuccess { source = it }
            .onFailure { status = "Unable to load EPG sources. Try reopening Settings." }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MobileM3Switch(checked = state.enabled, onCheckedChange = { value ->
                scope.launch {
                    runCatching { state.actions.saveEnabled(value) }
                        .onSuccess { state.onEnabled(value) }
                        .onFailure { status = "Unable to save EPG setting" }
                }
            })
            Text("Enable EPG on this device", Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            var playbackOnly by remember { mutableStateOf(false) }
            LaunchedEffect(state.actions) { playbackOnly = state.actions.loadPlaybackOnly() }
            MobileM3Switch(checked = playbackOnly && state.enabled, enabled = state.enabled && !busy, onCheckedChange = { value ->
                scope.launch {
                    runCatching { state.actions.savePlaybackOnly(value) }
                        .onSuccess { playbackOnly = value; state.onPlaybackOnly(value) }
                        .onFailure { status = "Unable to save EPG display mode" }
                }
            })
            Text("Load EPG only for the channel being played", Modifier.weight(1f))
        }
        if (!state.enabled) Text("Enable EPG to use playback-only mode.", style = MaterialTheme.typography.bodySmall)
        TextField(
            value = source,
            onValueChange = { source = it },
            label = { Text("Global EPG sources") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            colors = mobileTextFieldColors()
        )
        Text("Shared through database sync. Used after provider and account guides. One XMLTV URL or local file per line, in priority order. Gzip is supported.",
            style = MaterialTheme.typography.bodySmall)
        TextButton(enabled = !busy, onClick = { scope.launch {
            busy = true
            runCatching { state.actions.saveGlobalSource(source) }.onSuccess { status = "EPG sources saved" }
                .onFailure { status = "Unable to save sources. Check each XMLTV URL or local file and try again." }
            busy = false
        } }) { Text("Save EPG sources") }
        TextButton(enabled = !busy, onClick = { scope.launch {
            busy = true
            runCatching { state.actions.clearCache() }
                .onSuccess { status = "EPG cache cleared. Guides reload when you next open a channel." }
                .onFailure { status = "Unable to clear EPG cache. Please try again." }
            busy = false
        } }) { Text("Clear EPG cache") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun MobileEpgChannel(accountId: Long, channelId: String, name: String) {
    val state = LocalEpgState.current
    if (!state.enabled || state.playbackOnly) return
    val cacheRevision by MobileEpgCache.revision.collectAsState()
    var result by remember(accountId, channelId) { mutableStateOf<MobileEpgResult?>(MobileEpgCache.peek(accountId, channelId, state.time)) }
    var details by remember(accountId, channelId) { mutableStateOf(false) }
    LaunchedEffect(accountId, channelId, state.time / 60_000, cacheRevision) {
        val cached = MobileEpgCache.peek(accountId, channelId, state.time)
        if (cached?.status == MobileEpgStatus.AVAILABLE || cached?.status == MobileEpgStatus.STALE) {
            result = cached
            return@LaunchedEffect
        }
        val fetched = loadGuide(state, accountId, channelId, name)
        result = fetched
        MobileEpgCache.put(accountId, channelId, fetched, state.time)
    }
    val now = result?.now(state.time)?.takeIf { it.hasProgrammeInformation() }
    val next = result?.next(state.time)?.takeIf { it.hasProgrammeInformation() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (now != null) {
            Text("Now: ${now.title}", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text("${programmeTime(now.start)} – ${programmeTime(now.stop)}", style = MaterialTheme.typography.labelMedium)
            LinearProgressIndicator(progress = { ((state.time - now.start).toFloat() / (now.stop - now.start).coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth())
        } else Text(if (result == null) "Loading programme information…" else "Programme information unavailable",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (next != null) Text("Next: ${next.title} · ${programmeTime(next.start)}", maxLines = 2,
            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { details = true }, contentPadding = PaddingValues(0.dp)) { Text("View Guide") }
    }
    if (details) Dialog(onDismissRequest = { details = false }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(16.dp)) {
                MobileScreenHeader(name, "Programme guide") {
                    IconButton(onClick = { details = false }) { Icon(Icons.Outlined.Close, contentDescription = "Close guide") }
                }
                ProgrammeSchedule(result, Modifier.weight(1f, fill = false))
            }
        }
    }
}

private suspend fun loadGuide(state: EpgUiState, accountId: Long, channelId: String, name: String): MobileEpgResult =
    try { state.actions.schedule(accountId, channelId, name) }
    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (_: Exception) { MobileEpgResult(MobileEpgStatus.UNAVAILABLE) }

/** Suppress upstream missing-data markers without inventing programme titles or times. */
internal fun MobileEpgProgramme.hasProgrammeInformation(): Boolean =
    stop > start && title.isNotBlank() && title.trim().lowercase() !in setOf(
        "no programme details available", "no program details available", "no information available",
        "programme information unavailable", "no information", "no data", "unknown", "n/a"
    )

@Composable
private fun ProgrammeSchedule(result: MobileEpgResult?, modifier: Modifier = Modifier) {
    val programmes = remember(result) { result?.programmes.orEmpty().filter { it.hasProgrammeInformation() }.distinctBy { "${it.start}:${it.stop}:${it.title}" } }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (result == null) item { Text("Loading guide…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (result?.status == MobileEpgStatus.STALE) item { Text("Showing a saved schedule. It may be out of date.") }
        if (result != null && programmes.isEmpty()) item {
            Text("No schedule available. You can check your EPG sources in Settings.")
        }
        items(programmes, key = { "${it.start}:${it.stop}:${it.title}" }, contentType = { "programme" }) { p ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(p.title, style = MaterialTheme.typography.titleMedium)
                Text("${programmeTime(p.start)} – ${programmeTime(p.stop)}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                if (p.description.isNotBlank()) Text(p.description, style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }
        }
    }
}

private fun programmeTime(time: Long): String = SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(time))

@Composable
internal fun MobileLiveGuideButton(
    channels: List<com.uiptv.mobile.shared.browse.MobileBrowseItem>,
    onPlay: (com.uiptv.mobile.shared.browse.MobileBrowseItem) -> Unit
) {
    val state = LocalEpgState.current
    if (!state.enabled || state.playbackOnly || channels.isEmpty() || channels.first().mode != com.uiptv.mobile.shared.browse.BrowseMode.LIVE) return
    var open by remember { mutableStateOf(false) }
    var selected by remember(channels) { mutableStateOf<com.uiptv.mobile.shared.browse.MobileBrowseItem?>(null) }
    var result by remember { mutableStateOf<MobileEpgResult?>(null) }
    TextButton(onClick = { selected = null; open = true }) { Text("Live Guide") }
    if (!open) return
    LaunchedEffect(selected?.accountId, selected?.channelId, state.time / (30 * 60_000)) {
        result = null
        selected?.let { result = loadGuide(state, it.accountId, it.channelId, it.name) }
    }
    Dialog(onDismissRequest = { if (selected != null) selected = null else open = false }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).padding(16.dp)) {
                val channel = selected
                MobileScreenHeader(channel?.name ?: "Live Guide", onBack = if (channel != null) ({ selected = null }) else null,
                    actions = {
                        if (channel == null) IconButton(onClick = { open = false }) { Icon(Icons.Outlined.Close, contentDescription = "Close guide") }
                    })
                if (channel == null) LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(channels, key = { "${it.accountId}:${it.rowId}" }) { item ->
                        ListItem(headlineContent = { Text(item.name) }, modifier = Modifier.clickable { selected = item })
                    }
                } else {
                    TextButton(onClick = { onPlay(channel) }) { Text("Play channel") }
                    ProgrammeSchedule(result, Modifier.weight(1f, fill = false))
                }
            }
        }
    }
}
