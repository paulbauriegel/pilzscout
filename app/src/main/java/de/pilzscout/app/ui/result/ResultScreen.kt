package de.pilzscout.app.ui.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import de.pilzscout.app.R
import de.pilzscout.app.data.species.SpeciesEntity
import de.pilzscout.app.ui.identify.label
import de.pilzscout.app.ui.identify.shortLabel
import de.pilzscout.core.identify.Agreement
import de.pilzscout.core.identify.Candidate
import de.pilzscout.core.identify.ConfidenceDescriptor
import de.pilzscout.core.identify.FusionResult
import de.pilzscout.core.model.ViewType
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ResultScreen(
    observationId: String,
    onBack: () -> Unit,
    onCompare: (alternativeSpeciesId: String) -> Unit,
    onOpenSpecies: (speciesId: String) -> Unit = {},
    onAddPhoto: (ViewType) -> Unit,
    onNewObservation: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel<ResultViewModel, ResultViewModel.Factory> { it.create(observationId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showModelInfo by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.result_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val obs = state.observation
        val fusion = state.fusion
        if (state.loading || obs == null || fusion == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { if (!state.loading) Text(stringResource(R.string.unknown_species)) }
            return@Scaffold
        }
        val primary = fusion.primary
        val primarySpecies = primary.speciesId?.let { state.species[it] }
        val lead = obs.photos.minByOrNull { it.position }

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Hero
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                onClick = { primary.speciesId?.let(onOpenSpecies) },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (lead != null) {
                            AsyncImage(
                                model = File(lead.thumbPath),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(96.dp).clip(MaterialTheme.shapes.extraLarge),
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.result_likely, primarySpecies.displayName()),
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(primarySpecies?.binomial ?: "", style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Italic)
                        }
                    }
                    Text(stringResource(R.string.result_confidence, (primary.prob * 100).roundToInt()), style = MaterialTheme.typography.titleLarge)
                    Text(pluralStringResource(R.plurals.result_based_on, obs.observation.nPhotos, obs.observation.nPhotos), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SuggestionChip(onClick = {}, label = { Text(descriptorLabel(fusion.descriptor)) })
                        SuggestionChip(onClick = {}, icon = { Icon(Icons.Outlined.CloudOff, null, Modifier.size(18.dp)) }, label = { Text("Offline") })
                    }
                }
            }

            // Safety
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.result_safety_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.result_safety_body), style = MaterialTheme.typography.bodyMedium)
                        if (primarySpecies?.poisonous == 1) Text(stringResource(R.string.result_poisonous_warning), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Alternatives
            if (fusion.alternatives.isNotEmpty()) {
                Text(stringResource(R.string.result_alternatives), style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(fusion.alternatives) { alt ->
                        val sp = alt.speciesId?.let { state.species[it] }
                        FilledTonalButton(onClick = { alt.speciesId?.let(onCompare) }, shapes = ButtonDefaults.shapes()) {
                            Column {
                                Text(sp.displayName(), style = MaterialTheme.typography.labelLarge)
                                Text("${(alt.prob * 100).roundToInt()} % · ${sp?.binomial ?: ""}", style = MaterialTheme.typography.labelSmall, fontStyle = FontStyle.Italic)
                            }
                        }
                    }
                }
            }

            // Distribution
            Text(stringResource(R.string.result_distribution), style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                fusion.combined.take(5).forEach { c -> DistributionBar(c, c.speciesId?.let { state.species[it] }) }
            }

            // Recommendation
            fusion.recommendedView?.let { view ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                            Text(stringResource(R.string.result_recommend_title), style = MaterialTheme.typography.titleMedium)
                        }
                        Text(stringResource(R.string.result_recommend_body, view.label()), style = MaterialTheme.typography.bodyMedium)
                        FilledTonalButton(onClick = { onAddPhoto(view) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.result_recommend_action)) }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.result_saved), style = MaterialTheme.typography.bodyMedium)
            }

            PhotoEvidenceSection(fusion, state.species, state.totalClasses, obs.photos.associateBy { it.position }.mapValues { File(it.value.thumbPath) })

            TextButton(onClick = { showModelInfo = true }) {
                Icon(Icons.Outlined.Info, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.result_model_info))
            }
            Button(onClick = onNewObservation, modifier = Modifier.fillMaxWidth().height(56.dp), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.result_new))
            }
            Spacer(Modifier.height(24.dp))
        }

        if (showModelInfo) {
            ModalBottomSheet(onDismissRequest = { showModelInfo = false }) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.result_model_info), style = MaterialTheme.typography.titleLarge)
                    InfoRow(stringResource(R.string.model_info_version), obs.observation.modelVersion)
                    InfoRow(stringResource(R.string.model_info_precision), obs.observation.modelPrecision)
                    InfoRow(stringResource(R.string.model_info_duration), stringResource(R.string.model_info_total, obs.observation.totalInferenceMs))
                    fusion.photos.forEach { p -> Text(stringResource(R.string.model_info_per_photo, p.viewType.shortLabel(), p.inferenceMs), style = MaterialTheme.typography.bodyMedium) }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DistributionBar(c: Candidate, species: SpeciesEntity?) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(species.displayName(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${(c.prob * 100).roundToInt()} %", style = MaterialTheme.typography.labelLarge)
        }
        LinearProgressIndicator(progress = { c.prob }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(MaterialTheme.shapes.small))
    }
}

@Composable
fun descriptorLabel(d: ConfidenceDescriptor): String = stringResource(
    when (d) {
        ConfidenceDescriptor.STRONG -> R.string.descriptor_strong
        ConfidenceDescriptor.UNCERTAIN -> R.string.descriptor_uncertain
        ConfidenceDescriptor.SEVERAL_PLAUSIBLE -> R.string.descriptor_several
    },
)

/** Collapsed by default: per-photo top candidate, confidence, agreement, other candidates, and the combination rule. */
@Composable
fun PhotoEvidenceSection(fusion: FusionResult, species: Map<String, SpeciesEntity>, totalClasses: Int, thumbs: Map<Int, File>) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer)) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.result_photo_evidence), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                fusion.photos.forEach { p ->
                    val top = p.top.firstOrNull()
                    val topSpecies = top?.speciesId?.let { species[it] }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        thumbs[p.position]?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.medium)) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.evidence_view, p.viewType.label()), style = MaterialTheme.typography.labelLarge)
                            Text(stringResource(R.string.evidence_top, topSpecies.displayName()), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.evidence_confidence, ((top?.prob ?: 0f) * 100).roundToInt()), style = MaterialTheme.typography.bodySmall)
                            Text(
                                stringResource(
                                    when (p.agreement) {
                                        Agreement.SUPPORTS -> R.string.evidence_supports
                                        Agreement.PARTIAL -> R.string.evidence_partial
                                        Agreement.CONFLICTS -> R.string.evidence_conflicts
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (p.agreement == Agreement.CONFLICTS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                            val others = p.top.drop(1).take(3).joinToString { c -> "${c.speciesId?.let { species[it] }?.binomial ?: "?"} ${(c.prob * 100).roundToInt()} %" }
                            if (others.isNotBlank()) Text(stringResource(R.string.evidence_others, others), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                }
                Text(stringResource(R.string.evidence_how_combined), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.evidence_how_combined_body), style = MaterialTheme.typography.bodySmall)
                if (fusion.maskedOutClasses > 0) Text(stringResource(R.string.evidence_masked, fusion.maskedOutClasses, totalClasses), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
