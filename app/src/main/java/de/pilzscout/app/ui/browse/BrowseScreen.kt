package de.pilzscout.app.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import de.pilzscout.app.R
import de.pilzscout.app.data.species.SpeciesSummary
import de.pilzscout.app.ui.components.TabScaffold
import de.pilzscout.app.ui.components.contentLanguage

@Composable
fun BrowseScreen(
    onOpenSettings: () -> Unit,
    onOpenSpecies: (String, BrowseSource) -> Unit = { _, _ -> },
    onOpenObservation: (Long) -> Unit = {},
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val observations by viewModel.observations.collectAsStateWithLifecycle()

    TabScaffold(title = stringResource(R.string.browse_title), onOpenSettings = onOpenSettings) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                BrowseSource.entries.forEachIndexed { i, src ->
                    SegmentedButton(
                        selected = state.source == src,
                        onClick = { viewModel.setSource(src) },
                        shape = SegmentedButtonDefaults.itemShape(i, BrowseSource.entries.size),
                    ) { Text(if (src == BrowseSource.FUNGITASTIC) "FungiTastic" else "Wikipedia") }
                }
            }
            if (!state.dbAvailable) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.browse_db_missing), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(24.dp))
                }
                return@Column
            }
            if (state.source == BrowseSource.FUNGITASTIC) {
                TabRow(selectedTabIndex = state.tab.ordinal, modifier = Modifier.padding(top = 8.dp)) {
                    BrowseTab.entries.forEach { t ->
                        Tab(
                            selected = state.tab == t,
                            onClick = { viewModel.setTab(t) },
                            text = {
                                Text(
                                    stringResource(
                                        when (t) {
                                            BrowseTab.SPECIES -> R.string.browse_tab_species
                                            BrowseTab.GROUPS -> R.string.browse_tab_groups
                                            BrowseTab.OBSERVATIONS -> R.string.browse_tab_observations
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
            }
            val tab = if (state.source == BrowseSource.WIKIPEDIA) BrowseTab.SPECIES else state.tab
            when (tab) {
                BrowseTab.SPECIES -> SpeciesList(state, viewModel::setQuery, onOpenSpecies)
                BrowseTab.GROUPS -> GroupsList(groups, viewModel::selectFamily, viewModel::selectGenus) { onOpenSpecies(it, state.source) }
                BrowseTab.OBSERVATIONS -> ObservationsList(observations, onOpenObservation, onLoadMore = { viewModel.loadObservations(observations.size) })
            }
        }
    }
}

@Composable
private fun SpeciesList(state: BrowseUiState, onQuery: (String) -> Unit, onOpenSpecies: (String, BrowseSource) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.browse_search_hint)) },
            singleLine = true,
        )
        Text(
            stringResource(R.string.browse_species_counts, state.inGermany, state.total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.results, key = { it.id }) { s ->
                SpeciesRow(s, onClick = { onOpenSpecies(s.id, state.source) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun GroupsList(groups: GroupsState, onFamily: (String?) -> Unit, onGenus: (String?) -> Unit, onOpenSpecies: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (groups.selectedFamily != null) {
                IconButton(onClick = { if (groups.selectedGenus != null) onGenus(null) else onFamily(null) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            }
            Text(
                listOfNotNull(stringResource(R.string.browse_groups_families), groups.selectedFamily, groups.selectedGenus).joinToString(" › "),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            when {
                groups.selectedGenus != null -> items(groups.species, key = { it.id }) { s -> SpeciesRow(s) { onOpenSpecies(s.id) }; HorizontalDivider() }
                groups.selectedFamily != null -> items(groups.genera, key = { it.name }) { g ->
                    ListItem(modifier = Modifier.clickable { onGenus(g.name) }, headlineContent = { Text(g.name, fontStyle = FontStyle.Italic) }, trailingContent = { Text("${g.count}") })
                    HorizontalDivider()
                }
                else -> items(groups.families, key = { it.name }) { f ->
                    ListItem(modifier = Modifier.clickable { onFamily(f.name) }, headlineContent = { Text(f.name) }, trailingContent = { Text("${f.count}") })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ObservationsList(rows: List<ObservationRow>, onOpen: (Long) -> Unit, onLoadMore: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(rows, key = { it.observation.observationId }) { row ->
            ListItem(
                modifier = Modifier.clickable { onOpen(row.observation.observationId) },
                leadingContent = {
                    Box(Modifier.size(56.dp).clip(MaterialTheme.shapes.medium)) {
                        if (row.thumb != null) AsyncImage(model = row.thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                },
                headlineContent = { Text(row.speciesName, fontStyle = FontStyle.Italic) },
                supportingContent = {
                    Text(listOfNotNull(row.observation.eventDate, row.observation.region, row.observation.habitat).joinToString(" · "), maxLines = 2)
                },
                trailingContent = { if (row.observation.dnaSequenced == 1) Text("DNA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) },
            )
            HorizontalDivider()
        }
        if (rows.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.browse_load_more),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onLoadMore).padding(16.dp),
                )
            }
        }
    }
}

@Composable
fun SpeciesRow(s: SpeciesSummary, onClick: () -> Unit) {
    val lang = contentLanguage()
    val common = if (lang == "de") s.commonDe ?: s.commonEn else s.commonEn ?: s.commonDe
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        headlineContent = { Text(common ?: s.binomial) },
        supportingContent = {
            Column {
                if (common != null) Text(s.binomial, fontStyle = FontStyle.Italic)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (s.inGermany == 0) AssistChip(onClick = onClick, label = { Text(stringResource(R.string.species_not_in_germany)) })
                    if (s.poisonous == 1) AssistChip(onClick = onClick, label = { Text(stringResource(R.string.species_poisonous_flag)) })
                }
            }
        },
    )
}
