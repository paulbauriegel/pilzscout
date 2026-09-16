package de.pilzscout.app.ui.result

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import de.pilzscout.app.R
import de.pilzscout.app.data.history.PhotoEntity
import de.pilzscout.app.data.species.SpeciesEntity
import de.pilzscout.app.ui.components.BrandRow
import de.pilzscout.app.ui.components.SectionHeader
import de.pilzscout.app.ui.components.edibilityColors
import de.pilzscout.app.ui.components.edibilityIcon
import de.pilzscout.app.ui.components.edibilityLabel
import de.pilzscout.app.ui.components.edibilitySourceLabel
import de.pilzscout.app.ui.components.speciesThumb
import de.pilzscout.app.ui.identify.label
import de.pilzscout.app.ui.identify.shortLabel
import de.pilzscout.core.identify.Agreement
import de.pilzscout.core.identify.Candidate
import de.pilzscout.core.identify.ConfidenceDescriptor
import de.pilzscout.core.identify.FusionResult
import de.pilzscout.core.model.Edibility
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
    var showModelInfo by remember { mutableStateOf(false) }
    val obs = state.observation
    val fusion = state.fusion

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        BrandRow(onOpenSettings = null, leading = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
        })
        SectionHeader(stringResource(R.string.result_title), icon = Icons.Outlined.Info)
        if (state.loading || obs == null || fusion == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { if (!state.loading) Text(stringResource(R.string.unknown_species)) }
            return@Column
        }
        val primary = fusion.primary
        val primarySpecies = primary.speciesId?.let { state.species[it] }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Hero: all photos, swipeable, then the verdict block
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                if (obs.photos.isNotEmpty()) PhotoPager(obs.photos.sortedBy { it.position })
                Column(Modifier.padding(16.dp).clickable { primary.speciesId?.let(onOpenSpecies) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.result_likely_short, primarySpecies.displayName()), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(primarySpecies?.binomial ?: "", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 6.dp)) {
                        Text("${(primary.prob * 100).roundToInt()} %", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Text(
                                stringResource(R.string.result_photos_combined, obs.observation.nPhotos) + (obs.observation.placeName?.takeIf { obs.observation.locationIncluded }?.let { " · " + stringResource(R.string.location_near, it) } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Badge(descriptorLabel(fusion.descriptor))
                        Badge("Offline", Icons.Outlined.CloudOff)
                    }
                }
            }

            EdibilityCard(primarySpecies, fusion.combined.take(5).mapNotNull { c -> c.speciesId?.let { state.species[it] }?.takeIf { it.id != primarySpecies?.id } })

            obs.observation.correctedSpeciesId?.let { correctedId ->
                val corrected = state.species[correctedId]
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.result_corrected_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.result_corrected_body, corrected.displayName(), corrected?.binomial ?: correctedId), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.result_safety_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(stringResource(R.string.result_safety_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }

            if (fusion.alternatives.isNotEmpty()) {
                Text(stringResource(R.string.result_more_species), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                fusion.alternatives.forEach { alt -> AlternativeRow(alt, alt.speciesId?.let { state.species[it] }) { alt.speciesId?.let(onCompare) } }
            }

            Text(stringResource(R.string.result_distribution), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fusion.combined.take(5).forEach { c -> DistributionBar(c, c.speciesId?.let { state.species[it] }) }
            }

            fusion.recommendedView?.let { view ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(stringResource(R.string.result_recommend_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                        Text(stringResource(R.string.result_recommend_body, view.label()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
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
            Button(onClick = onNewObservation, modifier = Modifier.fillMaxWidth().height(56.dp), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.result_new)) }
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

/** All photos of the observation, swipeable; each page is labelled with its view and the dots show the position. */
@Composable
private fun PhotoPager(photos: List<PhotoEntity>) {
    val pagerState = rememberPagerState(pageCount = { photos.size })
    Box(Modifier.fillMaxWidth().aspectRatio(1.6f)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { photos[it].id },
            flingBehavior = PagerDefaults.flingBehavior(state = pagerState, pagerSnapDistance = PagerSnapDistance.atMost(1)),
        ) { page ->
            AsyncImage(model = File(photos[page].filePath), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        val viewType = runCatching { ViewType.valueOf(photos[pagerState.currentPage].viewType) }.getOrNull()
        if (viewType != null) {
            Text(
                viewType.label(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp).clip(MaterialTheme.shapes.small).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        if (photos.size > 1) {
            Row(Modifier.align(Alignment.BottomCenter).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(photos.size) { i ->
                    val active = i == pagerState.currentPage
                    Box(Modifier.size(if (active) 8.dp else 6.dp).clip(RoundedCornerShape(50)).background(if (active) Color.White else Color.White.copy(alpha = 0.5f)))
                }
            }
        }
    }
}

/** Alternative species row with reference image, names, probability and a chevron into the comparison. */
@Composable
private fun AlternativeRow(alt: Candidate, sp: SpeciesEntity?, onClick: () -> Unit) {
    val thumb by speciesThumb(sp?.id)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            thumb?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Column(Modifier.weight(1f)) {
            Text(sp.displayName(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(sp?.binomial ?: "", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("${(alt.prob * 100).roundToInt()} %", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.result_compare_with, sp.displayName()), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Reference edibility of the candidate species and dangerous look-alikes among the candidates. Never a verdict about the photo. */
@Composable
fun EdibilityCard(primary: SpeciesEntity?, others: List<SpeciesEntity>) {
    val e = Edibility.parse(primary?.edibility)
    val dangerousOthers = others.filter { Edibility.parse(it.edibility)?.dangerous == true }
    val worst = (listOfNotNull(e) + dangerousOthers.mapNotNull { Edibility.parse(it.edibility) }).minByOrNull { it.ordinal }
    val (bg, fg) = edibilityColors(if (worst?.dangerous == true) worst else e)
    Card(colors = CardDefaults.cardColors(containerColor = bg), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(edibilityIcon(if (worst?.dangerous == true) worst else e), contentDescription = null, tint = fg)
                Text(stringResource(R.string.edibility_title), style = MaterialTheme.typography.titleMedium, color = fg)
            }
            Text(stringResource(R.string.edibility_reference_for, primary.displayName(), edibilityLabel(e)), style = MaterialTheme.typography.bodyLarge, color = fg, fontWeight = FontWeight.SemiBold)
            Text(edibilitySourceLabel(primary?.edibilitySource), style = MaterialTheme.typography.labelSmall, color = fg)
            if (dangerousOthers.isNotEmpty()) {
                val names = dangerousOthers.map { s -> "${s.displayName()} (${edibilityLabel(Edibility.parse(s.edibility)).lowercase()})" }
                Text(stringResource(R.string.edibility_dangerous_alternatives) + " " + names.joinToString(), style = MaterialTheme.typography.bodyMedium, color = fg, fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.edibility_disclaimer), style = MaterialTheme.typography.bodySmall, color = fg)
        }
    }
}

@Composable
private fun Badge(text: String, icon: ImageVector? = null) {
    Row(
        Modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                stringResource(when (p.agreement) { Agreement.SUPPORTS -> R.string.evidence_supports; Agreement.PARTIAL -> R.string.evidence_partial; Agreement.CONFLICTS -> R.string.evidence_conflicts }),
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
