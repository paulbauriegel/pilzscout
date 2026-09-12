package de.pilzscout.app.ui.comparison

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.pilzscout.app.R
import de.pilzscout.app.data.species.SpeciesEntity
import de.pilzscout.app.ui.components.contentLanguage
import de.pilzscout.app.ui.result.displayName
import de.pilzscout.core.compare.FeatureRow
import de.pilzscout.core.compare.Statement
import de.pilzscout.core.model.Basis
import de.pilzscout.core.model.EvidenceState
import de.pilzscout.core.model.Feature

@Composable
fun ComparisonScreen(
    observationId: String,
    alternativeId: String,
    onBack: () -> Unit,
    viewModel: ComparisonViewModel = hiltViewModel<ComparisonViewModel, ComparisonViewModel.Factory> { it.create(observationId, alternativeId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val lang = contentLanguage()
    LaunchedEffect(lang) { viewModel.setLanguage(lang) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.comparison_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        val primary = state.primary
        val alt = state.species[state.selectedAlternativeId]
        if (state.loading || primary == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Horizontal selector of alternatives; only one pair is shown at a time.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.alternatives) { c ->
                    val sp = c.speciesId?.let { state.species[it] }
                    FilterChip(
                        selected = c.speciesId == state.selectedAlternativeId,
                        onClick = { c.speciesId?.let(viewModel::select) },
                        label = { Text(sp.displayName()) },
                    )
                }
            }
            Text(
                stringResource(R.string.comparison_pair, primary.displayName(), alt.displayName()),
                style = MaterialTheme.typography.headlineSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(primary.binomial, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, modifier = Modifier.weight(1f))
                Text(alt?.binomial ?: "", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, modifier = Modifier.weight(1f))
            }
            Legend()
            state.result?.rows?.forEach { row ->
                FeatureRowCard(row, primary, alt, state.aiRows[row.feature])
            }

            val primaryName = primary.displayName()
            val altName = alt.displayName()
            OutlinedButton(
                onClick = { viewModel.generateExplanation(primaryName, altName) },
                enabled = online && !state.aiLoading && state.result != null,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (state.aiLoading) CircularProgressIndicator(Modifier.size(18.dp)) else Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.comparison_generate))
            }
            Text(
                stringResource(if (online) R.string.comparison_generate_hint else R.string.comparison_offline_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.aiError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Legend() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LegendItem(Icons.Outlined.CheckCircle, MaterialTheme.colorScheme.primary, stringResource(R.string.state_supports_primary_short))
        LegendItem(Icons.Outlined.SwapHoriz, MaterialTheme.colorScheme.tertiary, stringResource(R.string.state_supports_alternative_short))
        LegendItem(Icons.Outlined.RemoveRedEye, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.state_shared_short))
    }
}

@Composable
private fun LegendItem(icon: ImageVector, tint: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun featureLabel(f: Feature): String = stringResource(
    when (f) {
        Feature.CAP -> R.string.feature_cap
        Feature.GILLS_PORES -> R.string.feature_gills_pores
        Feature.STEM -> R.string.feature_stem
        Feature.RING -> R.string.feature_ring
        Feature.BASE_VOLVA -> R.string.feature_base_volva
        Feature.SURFACE_TEXTURE -> R.string.feature_surface_texture
        Feature.HABITAT_SUBSTRATE -> R.string.feature_habitat_substrate
        Feature.REGION_SEASON -> R.string.feature_region_season
    },
)

@Composable
fun stateLabel(s: EvidenceState): String = stringResource(
    when (s) {
        EvidenceState.SUPPORTS_PRIMARY -> R.string.state_supports_primary
        EvidenceState.SUPPORTS_ALTERNATIVE -> R.string.state_supports_alternative
        EvidenceState.SHARED -> R.string.state_shared
        EvidenceState.NOT_VISIBLE -> R.string.state_not_visible
        EvidenceState.INSUFFICIENT_REFERENCE -> R.string.state_insufficient
    },
)

@Composable
fun basisLabel(b: Basis): String = stringResource(
    when (b) {
        Basis.OBSERVED_IN_PHOTOS -> R.string.basis_observed
        Basis.REFERENCE -> R.string.basis_reference
        Basis.FUNGITASTIC -> R.string.basis_fungitastic
        Basis.WIKIPEDIA -> R.string.basis_wikipedia
        Basis.AI_GENERATED -> R.string.basis_ai
    },
)

@Composable
private fun stateIcon(s: EvidenceState): Pair<ImageVector, Color> = when (s) {
    EvidenceState.SUPPORTS_PRIMARY -> Icons.Outlined.CheckCircle to MaterialTheme.colorScheme.primary
    EvidenceState.SUPPORTS_ALTERNATIVE -> Icons.Outlined.SwapHoriz to MaterialTheme.colorScheme.tertiary
    EvidenceState.SHARED -> Icons.Outlined.RemoveRedEye to MaterialTheme.colorScheme.onSurfaceVariant
    EvidenceState.NOT_VISIBLE -> Icons.Outlined.VisibilityOff to MaterialTheme.colorScheme.outline
    EvidenceState.INSUFFICIENT_REFERENCE -> Icons.Outlined.HelpOutline to MaterialTheme.colorScheme.outline
}

@Composable
private fun FeatureRowCard(row: FeatureRow, primary: SpeciesEntity, alt: SpeciesEntity?, ai: Statement?) {
    val (icon, tint) = stateIcon(row.state)
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = tint)
            Column(Modifier.weight(1f)) {
                Text(featureLabel(row.feature), style = MaterialTheme.typography.titleMedium)
                Text(stateLabel(row.state), style = MaterialTheme.typography.labelMedium, color = tint)
            }
        }
        row.decidedBy?.let { Text(basisLabel(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (row.primary.isNotEmpty() || row.alternative.isNotEmpty()) {
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatementColumn(primary.displayName(), row.primary, Modifier.weight(1f))
                StatementColumn(alt.displayName(), row.alternative, Modifier.weight(1f))
            }
        }
        ai?.let {
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                Text(basisLabel(Basis.AI_GENERATED), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
            Text(it.text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatementColumn(title: String, statements: List<Statement>, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        if (statements.isEmpty()) Text(stringResource(R.string.state_insufficient), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        statements.forEach { s ->
            Text(s.text, style = MaterialTheme.typography.bodySmall)
            Text(basisLabel(s.basis) + (s.source?.let { src -> if (src.startsWith("wiki:")) " · Wikipedia" else "" } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
