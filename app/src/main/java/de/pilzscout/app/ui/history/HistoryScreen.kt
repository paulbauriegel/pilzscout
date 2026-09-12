package de.pilzscout.app.ui.history

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import de.pilzscout.app.R
import de.pilzscout.app.ui.components.TabScaffold
import de.pilzscout.app.ui.result.displayName
import java.io.File
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
    onOpenSettings: () -> Unit,
    onOpenObservation: (String) -> Unit = {},
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var pendingExport by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf<Set<String>?>(null) }
    var showFilters by remember { mutableStateOf(false) }
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
                        IconButton(onClick = { startExport(state.selected) }) { Icon(Icons.Outlined.FileDownload, contentDescription = stringResource(R.string.history_export)) }
                        IconButton(onClick = { confirmDelete = state.selected }) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.history_delete)) }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding -> HistoryList(state, padding, viewModel, onOpenObservation, selecting = true, onExportOne = { startExport(setOf(it)) }, onDeleteOne = { confirmDelete = setOf(it) }) }
    } else {
        TabScaffold(title = stringResource(R.string.history_title), onOpenSettings = onOpenSettings) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.filter.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.history_search_hint)) },
                        singleLine = true,
                    )
                    FilterChip(selected = showFilters, onClick = { showFilters = !showFilters }, label = { Text(stringResource(R.string.history_filters)) })
                }
                if (showFilters) FilterBar(state.filter, viewModel)
                Text(
                    stringResource(R.string.history_counts, state.items.size, state.total),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Box(Modifier.fillMaxSize()) {
                    HistoryList(state, androidx.compose.foundation.layout.PaddingValues(0.dp), viewModel, onOpenObservation, selecting = false, onExportOne = { startExport(setOf(it)) }, onDeleteOne = { confirmDelete = setOf(it) })
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
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
    @Suppress("UNUSED_VARIABLE") val unused = context
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
    }
}

@Composable
private fun HistoryList(
    state: HistoryUiState,
    padding: androidx.compose.foundation.layout.PaddingValues,
    viewModel: HistoryViewModel,
    onOpen: (String) -> Unit,
    selecting: Boolean,
    onExportOne: (String) -> Unit,
    onDeleteOne: (String) -> Unit,
) {
    val context = LocalContext.current
    if (state.items.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(stringResource(if (state.total == 0) R.string.history_empty else R.string.history_no_matches), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(24.dp))
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(padding)) {
        items(state.items, key = { it.entry.observation.id }) { item ->
            val o = item.entry.observation
            val id = o.id
            val selected = id in state.selected
            val shown = item.corrected ?: item.primary
            val lead = item.entry.photos.minByOrNull { it.position }
            ListItem(
                modifier = Modifier
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                    .combinedClickable(onClick = { if (selecting) viewModel.toggleSelect(id) else onOpen(id) }, onLongClick = { viewModel.toggleSelect(id) }),
                leadingContent = {
                    Box(Modifier.size(64.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        if (lead != null) AsyncImage(model = File(lead.thumbPath), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        if (selecting) Checkbox(checked = selected, onCheckedChange = { viewModel.toggleSelect(id) }, modifier = Modifier.align(Alignment.TopStart))
                    }
                },
                headlineContent = {
                    Text(shown.displayName() + if (item.corrected != null) " ✎" else "")
                },
                supportingContent = {
                    Column {
                        Text(shown?.binomial ?: o.primarySpeciesId, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodySmall)
                        Text(
                            listOfNotNull(
                                DateFormat.getMediumDateFormat(context).format(Date(o.capturedAt)),
                                if (o.locationIncluded && o.lat != null && o.lon != null) "%.2f, %.2f".format(o.lat, o.lon) else null,
                                "${(o.primaryProb * 100).roundToInt()} %",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CloudOff, contentDescription = stringResource(R.string.history_offline_result), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(if (o.mode == "offline") R.string.history_offline_result else R.string.history_online_result), style = MaterialTheme.typography.labelSmall)
                            Icon(
                                if (o.userConfirmed) Icons.Filled.CheckCircle else Icons.Outlined.HelpOutline,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (o.userConfirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(stringResource(if (o.userConfirmed) R.string.history_confirmed else R.string.history_unconfirmed), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                trailingContent = {
                    if (!selecting) {
                        Column {
                            IconButton(onClick = { viewModel.startCorrection(id) }) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.history_correct)) }
                            IconButton(onClick = { onExportOne(id) }) { Icon(Icons.Outlined.FileDownload, contentDescription = stringResource(R.string.history_export)) }
                        }
                    }
                },
            )
            HorizontalDivider()
        }
        item { Box(Modifier.height(24.dp)) }
    }
    @Suppress("UNUSED_VARIABLE") val unused = onDeleteOne
}

@Composable
private fun CorrectionSheet(state: HistoryUiState, viewModel: HistoryViewModel) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = viewModel::cancelCorrection) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.history_correct_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.history_correct_hint), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; viewModel.searchCorrection(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.browse_search_hint)) },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { viewModel.applyCorrection(null) }) { Text(stringResource(R.string.history_confirm_as_is)) }
            }
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
