package de.pilzscout.app.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import de.pilzscout.app.R
import de.pilzscout.app.data.species.SpeciesSummary
import de.pilzscout.app.ui.components.EdibilityBadge
import de.pilzscout.app.ui.components.TabScaffold
import de.pilzscout.app.ui.components.contentLanguage
import de.pilzscout.app.ui.components.speciesThumb
import de.pilzscout.core.model.Edibility

@Composable
fun BrowseScreen(
    onOpenSettings: () -> Unit,
    onOpenSpecies: (String, BrowseSource) -> Unit = { _, _ -> },
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val queryText by viewModel.queryText.collectAsStateWithLifecycle()

    TabScaffold(
        title = stringResource(R.string.browse_title),
        subtitle = stringResource(R.string.browse_header_sub),
        headerIcon = Icons.Outlined.MenuBook,
        onOpenSettings = onOpenSettings,
    ) { padding ->
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = queryText,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text(stringResource(R.string.browse_search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                BrowseSource.entries.forEachIndexed { i, src ->
                    SegmentedButton(
                        selected = state.source == src,
                        onClick = { viewModel.setSource(src) },
                        shape = SegmentedButtonDefaults.itemShape(i, BrowseSource.entries.size),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary),
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
                TabRow(selectedTabIndex = state.tab.ordinal, modifier = Modifier.padding(top = 4.dp), containerColor = MaterialTheme.colorScheme.background) {
                    BrowseTab.entries.forEach { t ->
                        Tab(
                            selected = state.tab == t,
                            onClick = { viewModel.setTab(t) },
                            text = { Text(stringResource(when (t) { BrowseTab.SPECIES -> R.string.browse_tab_species; BrowseTab.GROUPS -> R.string.browse_tab_groups })) },
                        )
                    }
                }
            }
            val tab = if (state.source == BrowseSource.WIKIPEDIA) BrowseTab.SPECIES else state.tab
            val bottom = padding.calculateBottomPadding()
            when (tab) {
                BrowseTab.SPECIES -> SpeciesList(state, bottom, onOpenSpecies)
                BrowseTab.GROUPS -> GroupsList(groups, bottom, viewModel::selectFamily, viewModel::selectGenus) { onOpenSpecies(it, state.source) }
            }
        }
    }
}

@Composable
private fun SpeciesList(state: BrowseUiState, bottom: androidx.compose.ui.unit.Dp, onOpenSpecies: (String, BrowseSource) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.browse_species_counts, state.inGermany, state.total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottom), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.results, key = { it.id }) { s -> SpeciesRow(s, onClick = { onOpenSpecies(s.id, state.source) }) }
        }
    }
}

@Composable
private fun GroupsList(groups: GroupsState, bottom: androidx.compose.ui.unit.Dp, onFamily: (String?) -> Unit, onGenus: (String?) -> Unit, onOpenSpecies: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (groups.selectedFamily != null) {
                IconButton(onClick = { if (groups.selectedGenus != null) onGenus(null) else onFamily(null) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
            }
            Text(listOfNotNull(stringResource(R.string.browse_groups_families), groups.selectedFamily, groups.selectedGenus).joinToString(" › "), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottom), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                groups.selectedGenus != null -> items(groups.species, key = { it.id }) { s -> SpeciesRow(s) { onOpenSpecies(s.id) } }
                groups.selectedFamily != null -> items(groups.genera, key = { it.name }) { g -> GroupRow(g.name, g.count, italic = true) { onGenus(g.name) } }
                else -> items(groups.families, key = { it.name }) { f -> GroupRow(f.name, f.count, italic = false) { onFamily(f.name) } }
            }
        }
    }
}

@Composable
private fun GroupRow(name: String, count: Int, italic: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Species row with reference image, names and tags, used by Browse, Groups and the correction sheet. */
@Composable
fun SpeciesRow(s: SpeciesSummary, onClick: () -> Unit) {
    val lang = contentLanguage()
    val common = if (lang == "de") s.commonDe ?: s.commonEn else s.commonEn ?: s.commonDe
    val thumb by speciesThumb(s.id)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            thumb?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(common ?: s.binomial, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (common != null) Text(s.binomial, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (s.nObservations >= 100) Text(stringResource(R.string.browse_tag_common), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (s.inGermany == 0) Text(stringResource(R.string.species_not_in_germany), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                EdibilityBadge(Edibility.parse(s.edibility))
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

