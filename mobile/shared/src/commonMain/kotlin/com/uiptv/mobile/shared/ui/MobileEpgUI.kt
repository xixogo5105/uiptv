package com.uiptv.mobile.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
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
    val globalSource: suspend () -> String = { "" },
    val saveGlobalSource: suspend (String) -> Unit = {},
    val schedule: suspend (Long, String, String) -> MobileEpgResult = { _, _, _ -> MobileEpgResult() },
    val saveMapping: suspend (Long, String, String) -> Unit = { _, _, _ -> }
)
internal data class EpgUiState(
    val enabled: Boolean = false,
    val time: Long = 0,
    val actions: EpgUiActions = EpgUiActions(),
    val onEnabled: (Boolean) -> Unit = {}
)
internal val LocalEpgState = staticCompositionLocalOf { EpgUiState() }

@Composable
internal fun MobileEpgSettings() {
    val state = LocalEpgState.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    LaunchedEffect(state.actions) { source = state.actions.globalSource() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = state.enabled, onCheckedChange = { value ->
                scope.launch {
                    runCatching { state.actions.saveEnabled(value) }
                        .onSuccess { state.onEnabled(value) }
                        .onFailure { status = "Unable to save EPG setting" }
                }
            })
            Text("Enable EPG on this device")
        }
        OutlinedTextField(value = source, onValueChange = { source = it }, label = { Text("Global EPG sources") },
            modifier = Modifier.fillMaxWidth(), minLines = 4)
        Text("Shared through database sync. Used after provider and account guides. One XMLTV URL or local file per line, in priority order. Gzip is supported.",
            style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { scope.launch {
            runCatching { state.actions.saveGlobalSource(source) }.onSuccess { status = "EPG source saved" }
                .onFailure { status = it.message ?: "Unable to save source" }
        } }) { Text("Save EPG source") }
        TextButton(onClick = { scope.launch { state.actions.clearCache(); status = "EPG cache cleared" } }) { Text("Clear EPG cache") }
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
    internal fun MobileEpgChannel(accountId: Long, channelId: String, name: String) {
        val state = LocalEpgState.current
        if (!state.enabled) return
        val scope = rememberCoroutineScope()
        var result by remember(accountId, channelId) { mutableStateOf<MobileEpgResult?>(MobileEpgCache.peek(accountId, channelId, state.time)) }
        var details by remember(accountId, channelId) { mutableStateOf(false) }
        var mapping by remember(accountId, channelId) { mutableStateOf("") }
        var revision by remember { mutableIntStateOf(0) }
        var error by remember { mutableStateOf("") }
        LaunchedEffect(accountId, channelId, state.time / (30 * 60_000), revision) {
            // Peek first: the cache is shared with the embedded player, so a channel already fetched
            // elsewhere (or a moment ago) is shown without another XMLTV parse. Only fetch when the
            // cache is empty or expired.
            val cached = MobileEpgCache.peek(accountId, channelId, state.time)
            if (cached != null) { result = cached; return@LaunchedEffect }
            val fetched = state.actions.schedule(accountId, channelId, name)
            result = fetched
            MobileEpgCache.put(accountId, channelId, fetched, state.time)
        }
        val now = result?.now(state.time)
        val next = result?.next(state.time)
        Column {
            if (now != null) {
                Text("Now: ${now.title}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall)
                Text("${programmeTime(now.start)} – ${programmeTime(now.stop)}", style = MaterialTheme.typography.labelSmall)
                LinearProgressIndicator(progress = { ((state.time - now.start).toFloat() / (now.stop - now.start)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth())
            }
            if (next != null) Text("Next: ${next.title}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall)
            if (result == null) Text("Loading guide…", style = MaterialTheme.typography.bodySmall)
            else if (result?.status == MobileEpgStatus.STALE) Text("Cached guide", style = MaterialTheme.typography.labelSmall)
            else if (now == null && next == null) Text(result?.message?.takeIf { it.isNotBlank() } ?: if (result?.status == MobileEpgStatus.UNAVAILABLE) "Guide source unavailable" else "No matching programme",
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { details = true }, contentPadding = PaddingValues(0.dp)) { Text("View Guide") }
        }
        if (details) Dialog(onDismissRequest = { details = false }) {
            Surface(shape = MaterialTheme.shapes.large) {
                BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(16.dp)) {
                    val wide = maxWidth >= 600.dp
                    @Composable fun header() {
                        Column(Modifier.then(if (wide) Modifier.width(220.dp) else Modifier.fillMaxWidth())) {
                            Text(name, style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(value = mapping, onValueChange = { mapping = it }, singleLine = true,
                                label = { Text("Guide ID override") }, modifier = Modifier.fillMaxWidth())
                            Text("Leave empty to use provider mapping.", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { scope.launch {
                                runCatching { state.actions.saveMapping(accountId, channelId, mapping) }
                                    .onSuccess { revision++ }.onFailure { error = "Unable to save mapping" }
                            } }) { Text("Save mapping") }
                            if (error.isNotBlank()) Text(error)
                            TextButton(onClick = { details = false }) { Text("Close") }
                        }
                    }
                    @Composable fun schedule(modifier: Modifier) {
                        LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(result?.programmes.orEmpty(), key = { "${it.start}:${it.stop}:${it.title}" }, contentType = { "programme" }) { p ->
                                Column {
                                    Text(p.title, style = MaterialTheme.typography.titleSmall)
                                    Text("${programmeTime(p.start)} – ${programmeTime(p.stop)}", style = MaterialTheme.typography.labelMedium)
                                    if (p.description.isNotBlank()) Text(p.description, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (result?.programmes.isNullOrEmpty()) item { Text("No EPG available for this channel.") }
                        }
                    }
                    if (wide) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { header(); schedule(Modifier.weight(1f)) }
                    else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { header(); schedule(Modifier.weight(1f, fill = false)) }
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
    if (!state.enabled || channels.isEmpty() || channels.first().mode != com.uiptv.mobile.shared.browse.BrowseMode.LIVE) return
    var open by remember { mutableStateOf(false) }
    var selected by remember(channels) { mutableStateOf(channels.first()) }
    var result by remember { mutableStateOf<MobileEpgResult?>(null) }
    TextButton(onClick = { open = true }) { Text("Live Guide") }
    if (!open) return
    LaunchedEffect(selected.accountId, selected.channelId, state.time / (30 * 60_000)) {
        result = null
        result = state.actions.schedule(selected.accountId, selected.channelId, selected.name)
    }
    Dialog(onDismissRequest = { open = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(12.dp).widthIn(max = 1000.dp), shape = MaterialTheme.shapes.large) {
            BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = 680.dp).padding(12.dp)) {
                val wide = maxWidth >= 700.dp
                @Composable fun channelList(modifier: Modifier) {
                    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(channels, key = { "${it.accountId}:${it.channelId}:${it.rowId}" }, contentType = { "guide-channel" }) { channel ->
                            Column {
                                TextButton(onClick = { selected = channel }) { Text(channel.name) }
                                MobileEpgChannel(channel.accountId, channel.channelId, channel.name)
                                TextButton(onClick = { onPlay(channel) }) { Text("Play") }
                            }
                        }
                    }
                }
                @Composable fun programmes(modifier: Modifier) {
                    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item { Text(selected.name, style = MaterialTheme.typography.titleMedium) }
                        if (result == null) item { Text("Loading guide…") }
                        else if (result?.programmes.isNullOrEmpty()) item { Text("No EPG available") }
                        items(result?.programmes.orEmpty(), key = { "${it.start}:${it.stop}:${it.title}" }, contentType = { "programme" }) { p ->
                            Column {
                                Text(p.title, style = MaterialTheme.typography.titleSmall)
                                Text("${programmeTime(p.start)} – ${programmeTime(p.stop)}", style = MaterialTheme.typography.labelSmall)
                                if (p.description.isNotBlank()) Text(p.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Live Guide", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { open = false }) { Text("Close") }
                    }
                    if (wide) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        channelList(Modifier.weight(1f)); programmes(Modifier.weight(1f))
                    } else channelList(Modifier.weight(1f))
                }
            }
        }
    }
}
