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
import androidx.compose.ui.window.Dialog
import com.uiptv.mobile.shared.epg.*
import com.uiptv.mobile.shared.i18n.MobileMessages
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Repository actions keep IO and parsing outside composables. */
data class EpgUiActions(
    val clearCache: suspend () -> Unit = {},
    val loadBackgroundCheckIntervalMinutes: suspend () -> Int = { 3 },
    val loadGlobalRefreshIntervalHours: suspend () -> Int = { 6 },
    val saveGlobalRefreshIntervalHours: suspend (Int) -> Unit = {},
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
    val backgroundCheckIntervalMinutes: Int = 3,
    val globalRefreshIntervalHours: Int = 6,
    val dataRevision: Int = 0,
    val actions: EpgUiActions = EpgUiActions(),
    val onEnabled: (Boolean) -> Unit = {},
    val onPlaybackOnly: (Boolean) -> Unit = {},
    val onDataRefresh: () -> Unit = {}
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
    var globalRefreshInterval by remember(state.actions) { mutableIntStateOf(state.globalRefreshIntervalHours) }
    var intervalMenuExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(state.actions) {
        runCatching { state.actions.globalSource() }.onSuccess { source = it }
            .onFailure { status = MobileMessages.text("epgMobileSourcesLoadFailed") }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MobilePreferenceSwitchRow(
            title = MobileMessages.text("configEnableEpg"),
            checked = state.enabled,
            accessibilityLabel = MobileMessages.text("configEnableEpg"),
            onCheckedChange = { value ->
                scope.launch {
                    runCatching { state.actions.saveEnabled(value) }
                        .onSuccess { state.onEnabled(value); state.onDataRefresh() }
                        .onFailure { status = MobileMessages.text("epgMobileSettingSaveFailed") }
                }
            }
        )
        var playbackOnly by remember { mutableStateOf(false) }
        LaunchedEffect(state.actions) { playbackOnly = state.actions.loadPlaybackOnly() }
        MobilePreferenceSwitchRow(
            title = MobileMessages.text("epgEnablePlaybackOnly"),
            checked = playbackOnly && state.enabled,
            enabled = state.enabled && !busy,
            accessibilityLabel = MobileMessages.text("epgEnablePlaybackOnly"),
            onCheckedChange = { value ->
                scope.launch {
                    runCatching { state.actions.savePlaybackOnly(value) }
                        .onSuccess { playbackOnly = value; state.onPlaybackOnly(value) }
                        .onFailure { status = MobileMessages.text("epgMobileModeSaveFailed") }
                }
            }
        )
        if (!state.enabled) Text(MobileMessages.text("epgMobilePlaybackOnlyNeedsEpg"), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = source,
            onValueChange = { source = it },
            label = { Text(MobileMessages.text("configGlobalEpgSource")) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            colors = mobileOutlinedTextFieldColors()
        )
        Text(MobileMessages.text("epgMobileSourcesSyncHelp"),
            style = MaterialTheme.typography.bodySmall)
        TextButton(enabled = !busy, onClick = { scope.launch {
            busy = true
            runCatching { state.actions.saveGlobalSource(source) }
                .onSuccess { status = MobileMessages.text("epgMobileSourcesSaved"); state.onDataRefresh() }
                .onFailure { status = MobileMessages.text("epgMobileSourcesSaveFailed") }
            busy = false
        } }) { Text(MobileMessages.text("epgMobileSaveSources")) }
        TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
            Text(MobileMessages.text(if (advancedExpanded) "epgMobileHideAdvanced" else "epgAdvancedSettings"))
        }
        if (advancedExpanded) {
            HorizontalDivider()
            Text(MobileMessages.text("configEpgRefreshInterval"), style = MaterialTheme.typography.titleSmall)
            Box {
                OutlinedButton(onClick = { intervalMenuExpanded = true }, enabled = !busy) {
                    Text(if (globalRefreshInterval < 24) "${globalRefreshInterval}h" else "${globalRefreshInterval / 24}d")
                }
                DropdownMenu(expanded = intervalMenuExpanded, onDismissRequest = { intervalMenuExpanded = false }) {
                    listOf(1, 2, 3, 4, 5, 6, 12, 24, 48, 72, 96, 120, 144, 168).forEach { hours ->
                        DropdownMenuItem(
                            text = { Text(if (hours < 24) "${hours}h" else "${hours / 24}d") },
                            onClick = {
                                intervalMenuExpanded = false
                                scope.launch {
                                    busy = true
                                    runCatching { state.actions.saveGlobalRefreshIntervalHours(hours) }
                                        .onSuccess { globalRefreshInterval = hours; state.onDataRefresh() }
                                        .onFailure { status = MobileMessages.text("epgMobileSettingSaveFailed") }
                                    busy = false
                                }
                            }
                        )
                    }
                }
            }
            Text(MobileMessages.text("configEpgRefreshIntervalHelp"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(MobileMessages.text("epgMobileMaintenance"), style = MaterialTheme.typography.titleSmall)
            Text(MobileMessages.text("epgMobileCacheDescription"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(enabled = !busy, onClick = { scope.launch {
                busy = true
                runCatching { state.actions.clearCache() }
                    .onSuccess { status = MobileMessages.text("epgMobileCacheCleared"); state.onDataRefresh() }
                    .onFailure { status = MobileMessages.text("epgMobileCacheClearFailed") }
                busy = false
            } }) { Text(MobileMessages.text("configClearCache")) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun MobileEpgChannel(accountId: Long, channelId: String, name: String) {
    val state = LocalEpgState.current
    if (!state.enabled || state.playbackOnly) return
    var result by remember(accountId, channelId) { mutableStateOf<MobileEpgResult?>(MobileEpgCache.peek(accountId, channelId, state.time)) }
    var details by remember(accountId, channelId) { mutableStateOf(false) }
    LaunchedEffect(accountId, channelId, state.time / 60_000, state.dataRevision) {
        val cached = MobileEpgCache.peek(accountId, channelId, state.time)
        if (cached != null) {
            result = cached
            return@LaunchedEffect
        }
        result = null
        val fetched = loadGuide(state, accountId, channelId, name)
        result = fetched
        MobileEpgCache.put(accountId, channelId, fetched, state.time,
            state.backgroundCheckIntervalMinutes * 60_000L)
    }
    val now = result?.now(state.time)?.takeIf { it.hasProgrammeInformation() }
    val next = result?.next(state.time)?.takeIf { it.hasProgrammeInformation() }
    val hasGuideDetails = result?.programmes.orEmpty().any { it.hasProgrammeInformation() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (now != null) {
            Text("${MobileMessages.text("epgNow")}: ${now.title}", maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium)
            Text("${programmeTime(now.start)} – ${programmeTime(now.stop)}", style = MaterialTheme.typography.labelMedium)
            LinearProgressIndicator(progress = { ((state.time - now.start).toFloat() / (now.stop - now.start).coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth())
        } else {
            if (!hasGuideDetails && result != null) HorizontalDivider()
            Text(mobileEpgNowStatus(result, now, next).orEmpty(),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        mobileEpgSourceStatus(result, now, next)?.let { status ->
            Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (next != null) Text("${MobileMessages.text("epgNext")}: ${next.title} · ${programmeTime(next.start)}", maxLines = 2,
            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        if (hasGuideDetails) {
            TextButton(onClick = { details = true }, contentPadding = PaddingValues(0.dp)) {
                Text(MobileMessages.text("epgMobileProgrammeGuide"))
            }
        }
    }
    if (details) Dialog(onDismissRequest = { details = false }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(16.dp)) {
                MobileScreenHeader(name, MobileMessages.text("epgMobileProgrammeGuide")) {
                    IconButton(onClick = { details = false }) {
                        Icon(Icons.Outlined.Close, contentDescription = MobileMessages.text("commonClose"))
                    }
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
    start > 0 && stop > start && title.isNotBlank() && title.trim().lowercase() !in setOf(
        "no details available", "no details", "no programme details", "no program details",
        "no programme details available", "no program details available", "no programme guide available",
        "no guide available", "no information available", "programme information unavailable",
        "no information", "no info", "no data", "unknown", "unavailable", "n/a", "na", "-"
    )

internal fun mobileEpgNowStatus(
    result: MobileEpgResult?,
    now: MobileEpgProgramme?,
    next: MobileEpgProgramme?
): String? = when {
    now != null -> null
    result == null -> MobileMessages.text("epgLoading")
    next != null -> MobileMessages.text("epgMobileNoProgrammeNow")
    else -> MobileMessages.text("epgNoGuide")
}

internal fun mobileEpgSourceStatus(
    result: MobileEpgResult?,
    now: MobileEpgProgramme?,
    next: MobileEpgProgramme?
): String? = when {
    result?.status == MobileEpgStatus.STALE && (now != null || next != null) -> MobileMessages.text("epgSourceStale")
    result?.status == MobileEpgStatus.UNAVAILABLE && (now != null || next != null) -> MobileMessages.text("epgSourceStale")
    else -> null
}

@Composable
private fun ProgrammeSchedule(result: MobileEpgResult?, modifier: Modifier = Modifier) {
    val programmes = remember(result) { result?.programmes.orEmpty().filter { it.hasProgrammeInformation() }.distinctBy { "${it.start}:${it.stop}:${it.title}" } }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (result == null) item { Text(MobileMessages.text("epgLoading")); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (result != null && programmes.isEmpty()) item { Text(MobileMessages.text("epgNoGuide")) }
        else if (result?.status == MobileEpgStatus.STALE || result?.status == MobileEpgStatus.UNAVAILABLE) {
            item { Text(MobileMessages.text("epgSourceStale")) }
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
