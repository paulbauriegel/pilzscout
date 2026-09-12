package de.pilzscout.app.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.pilzscout.app.R
import de.pilzscout.app.data.species.SpeciesSummary
import de.pilzscout.app.ui.components.TabScaffold
import de.pilzscout.app.ui.components.contentLanguage

@Composable
fun BrowseScreen(
    onOpenSettings: () -> Unit,
    onOpenSpecies: (String, BrowseSource) -> Unit = { _, _ -> },
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TabScaffold(title = stringResource(R.string.browse_title), onOpenSettings = onOpenSettings) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                BrowseSource.entries.forEachIndexed { i, src ->
                    SegmentedButton(
                        selected = state.source == src,
                        onClick = { viewModel.setSource(src) },
                        shape = SegmentedButtonDefaults.itemShape(i, BrowseSource.entries.size),
                    ) {
                        Text(if (src == BrowseSource.FUNGITASTIC) "FungiTastic" else "Wikipedia")
                    }
                }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.browse_search_hint)) },
                singleLine = true,
            )
            if (!state.dbAvailable) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.browse_db_missing), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(24.dp))
                }
                return@Column
            }
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (s.inGermany == 0) AssistChip(onClick = {}, label = { Text(stringResource(R.string.species_not_in_germany)) })
                    if (s.poisonous == 1) AssistChip(onClick = {}, label = { Text(stringResource(R.string.species_poisonous_flag)) })
                }
            }
        },
    )
}
