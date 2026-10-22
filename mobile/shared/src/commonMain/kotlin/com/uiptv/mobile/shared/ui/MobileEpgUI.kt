package com.uiptv.mobile.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

/** A Material 3 preference row with a roomy, accessible switch target. */
@Composable
internal fun MobilePreferenceSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    accessibilityLabel: String = title
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange
                )
                .semantics(mergeDescendants = true) { contentDescription = accessibilityLabel }
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (!description.isNullOrBlank()) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}
val LocalThumbnailFallback = staticCompositionLocalOf { "" }
val LocalThumbnailFallbackLoader = staticCompositionLocalOf<(suspend () -> String)?> { null }
val LocalThumbnailFallbackPanel = staticCompositionLocalOf { false }
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
    var advancedExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(state.actions) {
        runCatching { state.actions.globalSource() }.onSuccess { source = it }
            .onFailure { status = "Unable to load EPG sources. Try reopening Settings." }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MobilePreferenceSwitchRow(
            title = "Enable EPG on this device",
            checked = state.enabled,
            accessibilityLabel = "Enable EPG on this device",
            onCheckedChange = { value ->
                scope.launch {
                    runCatching { state.actions.saveEnabled(value) }
                        .onSuccess { state.onEnabled(value) }
                        .onFailure { status = "Unable to save EPG setting" }
                }
            }
        )
        var playbackOnly by remember { mutableStateOf(false) }
        LaunchedEffect(state.actions) { playbackOnly = state.actions.loadPlaybackOnly() }
        MobilePreferenceSwitchRow(
            title = "Load EPG only for the channel being played",
            checked = playbackOnly && state.enabled,
            enabled = state.enabled && !busy,
            accessibilityLabel = "Load EPG only for the channel being played",
            onCheckedChange = { value ->
                scope.launch {
                    runCatching { state.actions.savePlaybackOnly(value) }
                        .onSuccess { playbackOnly = value; state.onPlaybackOnly(value) }
                        .onFailure { status = "Unable to save EPG display mode" }
                }
            }
        )
        if (!state.enabled) Text("Enable EPG to use playback-only mode.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = source,
            onValueChange = { source = it },
            label = { Text("Global EPG sources") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            colors = mobileOutlinedTextFieldColors()
        )
        Text("Shared through database sync. Used after provider and account guides. One XMLTV URL or local file per line, in priority order. Gzip is supported.",
            style = MaterialTheme.typography.bodySmall)
        TextButton(enabled = !busy, onClick = { scope.launch {
            busy = true
            runCatching { state.actions.saveGlobalSource(source) }.onSuccess { status = "EPG sources saved" }
                .onFailure { status = "Unable to save sources. Check each XMLTV URL or local file and try again." }
            busy = false
        } }) { Text("Save EPG sources") }
        TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
            Text(if (advancedExpanded) "Hide advanced EPG settings" else "Advanced EPG settings")
        }
        if (advancedExpanded) {
            HorizontalDivider()
            Text("Maintenance", style = MaterialTheme.typography.titleSmall)
            Text("Clearing the local guide cache is only needed for troubleshooting. Normal guide refresh keeps saved data available.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(enabled = !busy, onClick = { scope.launch {
                busy = true
                runCatching { state.actions.clearCache() }
                    .onSuccess { status = "EPG cache cleared. Guides reload when you next open a channel." }
                    .onFailure { status = "Unable to clear EPG cache. Please try again." }
                busy = false
            } }) { Text("Clear EPG cache") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
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
    val nowStatus = mobileEpgNowStatus(result, now, next)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            when {
                now != null -> {
                    Text("Now · ${now.title}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium)
                    Text("${programmeTime(now.start)} – ${programmeTime(now.stop)}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(progress = { ((state.time - now.start).toFloat() / (now.stop - now.start).coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth())
                }
                result == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text("Loading guide…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> Text(nowStatus.orEmpty(), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            mobileEpgSourceStatus(result, now, next)?.let { status ->
                Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (next != null) Text("Next · ${next.title} · ${programmeTime(next.start)}", maxLines = 1,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { details = true }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Outlined.Info, contentDescription = "Programme details for $name",
                tint = MaterialTheme.colorScheme.primary)
        }
    }
    if (details) {
        ModalBottomSheet(onDismissRequest = { details = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.82f).padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("Programme guide", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { details = false }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close programme guide")
                    }
                }
                ProgrammeSchedule(result, Modifier.weight(1f).padding(top = 12.dp))
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

internal fun mobileEpgNowStatus(
    result: MobileEpgResult?,
    now: MobileEpgProgramme?,
    next: MobileEpgProgramme?
): String? = when {
    now != null -> null
    result == null -> "Loading guide…"
    result.status == MobileEpgStatus.UNAVAILABLE && next == null -> "Guide source unavailable"
    next != null -> "No programme airing now"
    result.status == MobileEpgStatus.UNAVAILABLE -> "Guide source unavailable"
    else -> "No guide available"
}

internal fun mobileEpgSourceStatus(
    result: MobileEpgResult?,
    now: MobileEpgProgramme?,
    next: MobileEpgProgramme?
): String? = when {
    result?.status == MobileEpgStatus.STALE -> "Showing saved guide data"
    result?.status == MobileEpgStatus.UNAVAILABLE && (now != null || next != null) -> "Showing saved guide data"
    else -> null
}

@Composable
private fun ProgrammeSchedule(result: MobileEpgResult?, modifier: Modifier = Modifier) {
    val programmes = remember(result) { result?.programmes.orEmpty().filter { it.hasProgrammeInformation() }.distinctBy { "${it.start}:${it.stop}:${it.title}" } }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (result == null) item { Text("Loading guide…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (result?.status == MobileEpgStatus.STALE) item { Text("Showing a saved schedule. It may be out of date.") }
        if (result?.status == MobileEpgStatus.UNAVAILABLE) item { Text("Guide source unavailable. Saved data is retained when available.") }
        if (result != null && programmes.isEmpty() && result.status != MobileEpgStatus.UNAVAILABLE) item {
            Text("No guide available. Check the EPG sources in Settings.")
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
