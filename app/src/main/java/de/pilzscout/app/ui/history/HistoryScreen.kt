package de.pilzscout.app.ui.history

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import de.pilzscout.app.R
import de.pilzscout.app.ui.components.EdibilityBadge
import de.pilzscout.app.ui.components.TabScaffold
import de.pilzscout.app.ui.components.floatingNavBarInset
import de.pilzscout.app.ui.result.displayName
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
    onOpenSettings: () -> Unit,
    onOpenObservation: (String) -> Unit = {},
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingExport by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf<Set<String>?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    // The field owns its text: binding it to [state] would lag a frame behind each keystroke (the
    // filtered state is rebuilt from the database flow) and reset the cursor while typing.
    var queryText by rememberSaveable { mutableStateOf(viewModel.state.value.filter.query) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null && pendingExport.isNotEmpty()) viewModel.export(pendingExport, uri)
        pendingExport = emptySet()
    }
    val okMsg = stringResource(R.string.history_export_done)
    val errMsg = stringResource(R.string.history_export_failed)
    LaunchedEffect(state.message) {
        state.message?.let { m ->
            snackbar.showSnackbar(if (m.startsWith("ok:")) okMsg else "$errMsg ${m.removePrefix("error:")}")
            viewModel.consumeMessage()
        }
    }
    fun startExport(ids: Set<String>) {
        if (ids.isEmpty()) return
        pendingExport = ids
        exportLauncher.launch(if (ids.size == 1) "pilzscout-observation-${ids.first().take(8)}.zip" else "pilzscout-observations-${ids.size}.zip")
    }

    val selecting = state.selected.isNotEmpty()
    if (selecting) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.history_selected, state.selected.size)) },
                    navigationIcon = { IconButton(onClick = viewModel::clearSelection) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.history_clear_selection)) } },
                    actions = {
                        IconButton(onClick = viewModel::selectAllVisible) { Icon(Icons.Outlined.SelectAll, contentDescription = stringResource(R.string.history_select_all)) }
                        IconButton(onClick = { startExport(state.selected) }) { Icon(Icons.Outlined.FileUpload, contentDescription = stringResource(R.string.history_export)) }
                        IconButton(onClick = { confirmDelete = state.selected }) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.history_delete)) }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            // Keep clear of the top bar and of the floating tab bar, which still overlays this page.
            val listPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = maxOf(padding.calculateBottomPadding(), floatingNavBarInset()))
            HistoryList(state, listPadding, viewModel, onOpenObservation, selecting = true, onExportOne = { startExport(setOf(it)) }) }
    } else {
        TabScaffold(
            title = stringResource(R.string.history_title),
            subtitle = stringResource(R.string.history_header_sub),
            headerIcon = Icons.Outlined.History,
            onOpenSettings = onOpenSettings,
            actions = {
                FilledTonalButton(onClick = { startExport(state.items.map { it.entry.observation.id }.toSet()) }, enabled = state.items.isNotEmpty()) {
                    Icon(Icons.Outlined.FileUpload, contentDescription = null, Modifier.size(18.dp))
                    Text(stringResource(R.string.history_export), Modifier.padding(start = 6.dp))
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { queryText = it; viewModel.setQuery(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.history_search_hint)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                    FilterChip(selected = showFilters, onClick = { showFilters = !showFilters }, label = { Text(stringResource(R.string.history_filters)) })
                }
                if (showFilters) FilterBar(state.filter, viewModel)
                ViewToggle(state.view, viewModel::setView)
                Box(Modifier.fillMaxSize()) {
                    when (state.view) {
                        HistoryView.LIST -> HistoryList(state, padding, viewModel, onOpenObservation, selecting = false, onExportOne = { startExport(setOf(it)) })
                        HistoryView.MAP -> HistoryMap(
                            items = state.items,
                            total = state.total,
                            bottomPadding = padding.calculateBottomPadding(),
                            card = { item -> HistoryCard(item, selecting = false, selected = false, viewModel, onOpenObservation, onExportOne = { startExport(setOf(it)) }) },
                        )
                    }
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = floatingNavBarInset()))
                }
            }
        }
    }

    confirmDelete?.let { ids ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.history_delete)) },
            text = { Text(pluralStringResource(R.plurals.history_delete_confirm, ids.size, ids.size)) },
            confirmButton = { TextButton(onClick = { viewModel.delete(ids); confirmDelete = null }) { Text(stringResource(R.string.history_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (state.correctionTarget != null) CorrectionSheet(state, viewModel)
}

@Composable
private fun ViewToggle(view: HistoryView, onSelect: (HistoryView) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        HistoryView.entries.forEachIndexed { i, v ->
            SegmentedButton(
                selected = view == v,
                onClick = { onSelect(v) },
                shape = SegmentedButtonDefaults.itemShape(i, HistoryView.entries.size),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary),
                icon = { Icon(if (v == HistoryView.LIST) Icons.AutoMirrored.Outlined.List else Icons.Outlined.Map, contentDescription = null, Modifier.size(18.dp)) },
            ) { Text(stringResource(if (v == HistoryView.LIST) R.string.history_view_list else R.string.history_view_map)) }
        }
    }
}

@Composable
private fun FilterBar(filter: HistoryFilter, viewModel: HistoryViewModel) {
    val now = System.currentTimeMillis()
    val day = 86_400_000L
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = filter.fromEpochMs == null, onClick = { viewModel.setDateRange(null, null) }, label = { Text(stringResource(R.string.history_filter_any_date)) })
        FilterChip(selected = filter.fromEpochMs != null && now - filter.fromEpochMs!! <= 7 * day + 1000, onClick = { viewModel.setDateRange(now - 7 * day, null) }, label = { Text(stringResource(R.string.history_filter_week)) })
        FilterChip(selected = filter.fromEpochMs != null && now - filter.fromEpochMs!! in (7 * day + 1000)..(31 * day + 1000), onClick = { viewModel.setDateRange(now - 30 * day, null) }, label = { Text(stringResource(R.string.history_filter_month)) })
        FilterChip(selected = filter.minConfidence >= 0.6f, onClick = { viewModel.setMinConfidence(if (filter.minConfidence >= 0.6f) 0f else 0.6f) }, label = { Text(stringResource(R.string.history_filter_confident)) })
        FilterChip(selected = filter.onlyConfirmed, onClick = { viewModel.setOnlyConfirmed(!filter.onlyConfirmed) }, label = { Text(stringResource(R.string.history_filter_confirmed)) })
        FilterChip(selected = filter.onlyEdible, onClick = { viewModel.setOnlyEdible(!filter.onlyEdible) }, label = { Text(stringResource(R.string.history_filter_edible)) })
    }
}

@Composable
private fun dayLabel(epochMs: Long): String {
    val context = LocalContext.current
    val date = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    val formatted = DateFormat.getMediumDateFormat(context).format(Date(epochMs))
    return when (date) {
        today -> stringResource(R.string.history_today) + ", " + formatted
        today.minusDays(1) -> stringResource(R.string.history_yesterday) + ", " + formatted
        else -> formatted
    }
}

@Composable
private fun HistoryList(
    state: HistoryUiState,
    padding: PaddingValues,
    viewModel: HistoryViewModel,
    onOpen: (String) -> Unit,
    selecting: Boolean,
    onExportOne: (String) -> Unit,
) {
    if (state.items.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (state.total == 0) R.string.history_empty else R.string.history_no_matches), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(8.dp))
        }
        return
    }
    val groups = state.items.groupBy { Instant.ofEpochMilli(it.entry.observation.capturedAt).atZone(ZoneId.systemDefault()).toLocalDate() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = (if (selecting) padding.calculateTopPadding() else 0.dp) + 4.dp, bottom = padding.calculateBottomPadding() + 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        groups.forEach { (_, items) ->
            item(key = "h-${items.first().entry.observation.id}") {
                Text(dayLabel(items.first().entry.observation.capturedAt), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            }
            items(items, key = { it.entry.observation.id }) { item -> HistoryCard(item, selecting, item.entry.observation.id in state.selected, viewModel, onOpen, onExportOne) }
        }
    }
}

@Composable
private fun HistoryCard(item: HistoryItem, selecting: Boolean, selected: Boolean, viewModel: HistoryViewModel, onOpen: (String) -> Unit, onExportOne: (String) -> Unit) {
    val o = item.entry.observation
    val shown = item.shown
    val lead = item.entry.photos.minByOrNull { it.position }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(onClick = { if (selecting) viewModel.toggleSelect(o.id) else onOpen(o.id) }, onLongClick = { viewModel.toggleSelect(o.id) })
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            if (lead != null) AsyncImage(model = File(lead.thumbPath), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (selecting) {
                // A scrim plus a solid badge stays legible on any photo, light or dark.
                if (selected) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f))
                        .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(shown.displayName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                if (o.userConfirmed) Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.history_confirmed), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                if (item.corrected != null) Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.history_correct), modifier = Modifier.size(16.dp))
            }
            Text(shown?.binomial ?: o.primarySpeciesId, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${(o.primaryProb * 100).roundToInt()} %", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                EdibilityBadge(item.edibility)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.located) Icon(Icons.Outlined.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    when {
                        o.locationIncluded && o.placeName != null -> stringResource(R.string.location_near, o.placeName) + " · " + stringResource(R.string.history_offline_result)
                        o.locationIncluded && o.lat != null && o.lon != null -> "%.5f, %.5f".format(o.lat, o.lon) + " · " + stringResource(R.string.history_offline_result)
                        else -> stringResource(R.string.history_offline_result)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!selecting) {
            Column {
                IconButton(onClick = { viewModel.startCorrection(o.id) }, Modifier.size(36.dp)) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.history_correct)) }
                IconButton(onClick = { onExportOne(o.id) }, Modifier.size(36.dp)) { Icon(Icons.Outlined.FileUpload, contentDescription = stringResource(R.string.history_export)) }
            }
        }
    }
}

@Composable
private fun CorrectionSheet(state: HistoryUiState, viewModel: HistoryViewModel) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = viewModel::cancelCorrection) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.history_correct_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.history_correct_hint), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(value = query, onValueChange = { query = it; viewModel.searchCorrection(it) }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.browse_search_hint)) }, singleLine = true)
            TextButton(onClick = { viewModel.applyCorrection(null) }) { Text(stringResource(R.string.history_confirm_as_is)) }
            LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                items(state.correctionResults, key = { it.id }) { s ->
                    ListItem(
                        modifier = Modifier.combinedClickable(onClick = { viewModel.applyCorrection(s.id) }),
                        headlineContent = { Text(s.commonDe ?: s.commonEn ?: s.binomial) },
                        supportingContent = { Text(s.binomial, fontStyle = FontStyle.Italic) },
                    )
                }
            }
        }
    }
}
