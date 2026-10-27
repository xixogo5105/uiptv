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
    val loadEnabled: suspend () -> Boolean = { false },
    val saveEnabled: suspend (Boolean) -> Unit = {},
    val schedule: suspend (Long, String, String) -> MobileEpgResult = { _, _, _ -> MobileEpgResult() }
)
internal data class EpgUiState(
    val enabled: Boolean = false,
    val actions: EpgUiActions = EpgUiActions(),
    val onEnabled: (Boolean) -> Unit = {}
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
internal fun epgThumbnail(accountId: Long, providerId: String, name: String, enabled: Boolean): (suspend () -> String)? = null

@Composable
internal fun MobileEpgSettings() {
    val state = LocalEpgState.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MobilePreferenceSwitchRow(
            title = MobileMessages.text("configEnableEpg"),
            checked = state.enabled,
            accessibilityLabel = MobileMessages.text("configEnableEpg"),
            onCheckedChange = { value -> scope.launch {
                try {
                    state.actions.saveEnabled(value)
                    state.onEnabled(value)
                    status = ""
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { status = MobileMessages.text("epgMobileSettingSaveFailed") }
            } }
        )
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
    }
}

/** Each opening loads one fresh channel guide. Dismissing releases its programme list. */
@Composable
internal fun MobileChannelGuideDialog(accountId: Long, channelId: String, name: String, onDismiss: () -> Unit) {
    val state = LocalEpgState.current
    var result by remember(accountId, channelId) { mutableStateOf<MobileEpgResult?>(null) }
    LaunchedEffect(accountId, channelId, state.enabled) {
        result = if (state.enabled) loadGuide(state, accountId, channelId, name) else MobileEpgResult()
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(16.dp)) {
                MobileScreenHeader(name, MobileMessages.text("epgMobileProgrammeGuide")) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = MobileMessages.text("commonClose"))
                    }
                }
                ProgrammeSchedule(result, Modifier.weight(1f, fill = false))
            }
        }
    }
}

@Composable
internal fun MobileLiveChannelMenu(name: String, onGuide: () -> Unit, onChoosePlayer: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(name) }, text = {
        Column {
            TextButton(onClick = onGuide) { Text(MobileMessages.text("epgOpenGuide")) }
            TextButton(onClick = onChoosePlayer) { Text(MobileMessages.text("configSelectStreamingPlayer")) }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(MobileMessages.text("commonClose")) } })
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
