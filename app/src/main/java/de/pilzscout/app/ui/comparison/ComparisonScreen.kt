package de.pilzscout.app.ui.comparison

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import de.pilzscout.app.R
import de.pilzscout.app.data.species.SpeciesEntity
import de.pilzscout.app.ui.components.BrandRow
import de.pilzscout.app.ui.components.EdibilityBadge
import de.pilzscout.app.ui.components.SectionHeader
import de.pilzscout.app.ui.components.contentLanguage
import de.pilzscout.app.ui.components.edibilityColors
import de.pilzscout.app.ui.components.edibilityIcon
import de.pilzscout.app.ui.components.edibilitySourceLabel
import de.pilzscout.app.ui.components.speciesThumb
import de.pilzscout.app.ui.identify.label
import de.pilzscout.app.ui.result.displayName
import de.pilzscout.core.compare.FeatureRow
import de.pilzscout.core.compare.Statement
import de.pilzscout.core.identify.FeatureViews
import de.pilzscout.core.model.Basis
import de.pilzscout.core.model.Edibility
import de.pilzscout.core.model.EvidenceState
import de.pilzscout.core.model.Feature
import kotlin.math.roundToInt

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

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        BrandRow(onOpenSettings = null, leading = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } })
        SectionHeader(stringResource(R.string.comparison_title), icon = Icons.Outlined.Compare)
        val primary = state.primary
        val alt = state.species[state.selectedAlternativeId]
        if (state.loading || primary == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val primaryProb = state.alternatives.let { _ -> null }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.alternatives) { c ->
                    val sp = c.speciesId?.let { state.species[it] }
                    FilterChip(selected = c.speciesId == state.selectedAlternativeId, onClick = { c.speciesId?.let(viewModel::select) }, label = { Text(sp.displayName()) })
                }
            }
            // Two reference photos side by side
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SpeciesColumn(primary, stringResource(R.string.result_chip_result, state.primaryProbPercent), true, Modifier.weight(1f))
                SpeciesColumn(alt, stringResource(R.string.result_chip_alternative, state.alternatives.firstOrNull { it.speciesId == state.selectedAlternativeId }?.let { (it.prob * 100).roundToInt() } ?: 0), false, Modifier.weight(1f))
            }
            // Compact verdict rows, expandable to the full statements
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                state.result?.rows?.forEachIndexed { i, row ->
                    VerdictRow(row, primary, alt, state.aiRows[row.feature], i == 0)
                }
            }
            EdibilityPairCard(primary, alt)
            RecommendationCard(state)
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
            Text(stringResource(if (online) R.string.comparison_generate_hint else R.string.comparison_offline_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.aiError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(24.dp))
        }
        @Suppress("UNUSED_VARIABLE") val unused = primaryProb
    }
}

@Composable
private fun SpeciesColumn(sp: SpeciesEntity?, chip: String, isPrimary: Boolean, modifier: Modifier) {
    val thumb by speciesThumb(sp?.id)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.15f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            thumb?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Text(sp.displayName(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(sp?.binomial ?: "", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Box(
            Modifier.clip(MaterialTheme.shapes.small).background(if (isPrimary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 8.dp, vertical = 4.dp),
        ) { Text(chip, style = MaterialTheme.typography.labelMedium, color = if (isPrimary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface) }
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

/** Short verdict per side, as in the design reference: "supports" / "somewhat different" / "similar" / "not visible". */
@Composable
private fun verdicts(state: EvidenceState): Pair<Pair<String, Color>, Pair<String, Color>> {
    val ok = MaterialTheme.colorScheme.primaryContainer
    val neutral = MaterialTheme.colorScheme.surfaceContainerHigh
    val warn = MaterialTheme.colorScheme.tertiaryContainer
    return when (state) {
        EvidenceState.SUPPORTS_PRIMARY -> (stringResource(R.string.verdict_supports) to ok) to (stringResource(R.string.verdict_differs) to warn)
        EvidenceState.SUPPORTS_ALTERNATIVE -> (stringResource(R.string.verdict_rather_not) to warn) to (stringResource(R.string.verdict_supports) to ok)
        EvidenceState.SHARED -> (stringResource(R.string.verdict_similar) to neutral) to (stringResource(R.string.verdict_similar) to neutral)
        EvidenceState.NOT_VISIBLE -> (stringResource(R.string.verdict_not_visible) to neutral) to (stringResource(R.string.verdict_not_visible) to neutral)
        EvidenceState.INSUFFICIENT_REFERENCE -> (stringResource(R.string.verdict_unknown) to neutral) to (stringResource(R.string.verdict_unknown) to neutral)
    }
}

@Composable
private fun VerdictChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(MaterialTheme.shapes.small).background(color).padding(horizontal = 8.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun VerdictRow(row: FeatureRow, primary: SpeciesEntity, alt: SpeciesEntity?, ai: Statement?, first: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val (pv, av) = verdicts(row.state)
    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(featureLabel(row.feature), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.1f))
            VerdictChip(pv.first, pv.second, Modifier.weight(1f))
            VerdictChip(av.first, av.second, Modifier.weight(1f))
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stateLabel(row.state) + (row.decidedBy?.let { " · " + basisLabel(it) } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FeatureViews.requiredView(row.feature)?.let { v -> if (row.state == EvidenceState.NOT_VISIBLE) Text(stringResource(R.string.result_recommend_body, v.label()), style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatementColumn(primary.displayName(), row.primary, Modifier.weight(1f))
                    StatementColumn(alt.displayName(), row.alternative, Modifier.weight(1f))
                }
                ai?.let {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Text(basisLabel(Basis.AI_GENERATED), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    Text(it.text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (!first) Unit
}

@Composable
private fun StatementColumn(title: String, statements: List<Statement>, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        if (statements.isEmpty()) Text(stringResource(R.string.state_insufficient), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        statements.forEach { s ->
            Text(s.text, style = MaterialTheme.typography.bodySmall)
            val suffix = s.source?.let { src -> if (src.startsWith("wiki:") && s.basis != Basis.WIKIPEDIA) " · Wikipedia" else "" } ?: ""
            Text(basisLabel(s.basis) + suffix, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EdibilityPairCard(primary: SpeciesEntity, alt: SpeciesEntity?) {
    val ep = Edibility.parse(primary.edibility)
    val ea = Edibility.parse(alt?.edibility)
    val worst = listOfNotNull(ep, ea).minByOrNull { it.ordinal }
    val (bg, fg) = edibilityColors(worst)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(edibilityIcon(worst), contentDescription = null, tint = fg)
            Text(stringResource(R.string.edibility_title), style = MaterialTheme.typography.titleMedium, color = fg)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(primary.displayName(), style = MaterialTheme.typography.labelLarge, color = fg)
                EdibilityBadge(ep, showUnknown = true)
                Text(edibilitySourceLabel(primary.edibilitySource), style = MaterialTheme.typography.labelSmall, color = fg)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(alt.displayName(), style = MaterialTheme.typography.labelLarge, color = fg)
                EdibilityBadge(ea, showUnknown = true)
                Text(edibilitySourceLabel(alt?.edibilitySource), style = MaterialTheme.typography.labelSmall, color = fg)
            }
        }
        if (ep != null && ea != null && ep.dangerous != ea.dangerous) Text(stringResource(R.string.edibility_confusion_warning), style = MaterialTheme.typography.bodyMedium, color = fg, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.edibility_disclaimer), style = MaterialTheme.typography.bodySmall, color = fg)
    }
}

@Composable
private fun RecommendationCard(state: ComparisonUiState) {
    val notVisible = state.result?.rows?.filter { it.state == EvidenceState.NOT_VISIBLE }?.mapNotNull { FeatureViews.requiredView(it.feature) }?.distinct().orEmpty()
    if (notVisible.isEmpty()) return
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.tertiaryContainer).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(stringResource(R.string.comparison_recommendation), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
        notVisible.forEach { v -> Text(stringResource(R.string.result_recommend_body, v.label()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer) }
    }
}
