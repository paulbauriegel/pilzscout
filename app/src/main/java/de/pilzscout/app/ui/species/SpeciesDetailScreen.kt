package de.pilzscout.app.ui.species

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import de.pilzscout.app.R
import de.pilzscout.app.ui.browse.BrowseSource
import de.pilzscout.app.ui.components.EdibilityBadge
import de.pilzscout.app.ui.components.contentLanguage
import de.pilzscout.app.ui.components.edibilitySourceLabel
import de.pilzscout.core.model.Edibility
import de.pilzscout.app.ui.result.displayName

/** Species page with a visible FungiTastic | Wikipedia switch; the two sources are never merged. */
@Composable
fun SpeciesDetailScreen(
    speciesId: String,
    initialSource: BrowseSource,
    onBack: () -> Unit,
    viewModel: SpeciesDetailViewModel = hiltViewModel<SpeciesDetailViewModel, SpeciesDetailViewModel.Factory> { it.create(speciesId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var source by rememberSaveable { mutableStateOf(initialSource) }
    val sp = state.species

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sp.displayName(), maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        if (state.loading || sp == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { if (state.loading) CircularProgressIndicator() else Text(stringResource(R.string.unknown_species)) }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(sp.binomial, style = MaterialTheme.typography.titleLarge, fontStyle = FontStyle.Italic)
            Text(listOfNotNull(sp.family, sp.orderName, sp.className).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sp.inGermany == 0) AssistChip(onClick = {}, label = { Text(stringResource(R.string.species_not_in_germany)) })
                else AssistChip(onClick = {}, label = { Text(stringResource(R.string.species_in_germany, sp.deOccurrences)) })
                if (sp.poisonous == 1) AssistChip(onClick = {}, label = { Text(stringResource(R.string.species_poisonous_flag)) })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EdibilityBadge(Edibility.parse(sp.edibility), showUnknown = true)
                Text(edibilitySourceLabel(sp.edibilitySource), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                BrowseSource.entries.forEachIndexed { i, s ->
                    SegmentedButton(selected = source == s, onClick = { source = s }, shape = SegmentedButtonDefaults.itemShape(i, 2)) {
                        Text(if (s == BrowseSource.FUNGITASTIC) "FungiTastic" else "Wikipedia")
                    }
                }
            }
            when (source) {
                BrowseSource.FUNGITASTIC -> state.fungiTastic?.let { FungiTasticSection(it) }
                BrowseSource.WIKIPEDIA -> WikipediaSection(state)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FungiTasticSection(ft: FtSection) {
    val stats = ft.stats
    if (stats == null) {
        Text(stringResource(R.string.species_ft_missing), style = MaterialTheme.typography.bodyMedium)
        return
    }
    Text(stringResource(R.string.species_ft_counts, stats.nObs, stats.nDna), style = MaterialTheme.typography.bodyMedium)
    ft.monthHist?.let { hist ->
        Text(stringResource(R.string.species_ft_season), style = MaterialTheme.typography.titleSmall)
        val max = hist.max().coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            hist.forEachIndexed { i, v ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height((48 * v / max).coerceAtLeast(if (v > 0) 2 else 0).dp).clip(MaterialTheme.shapes.extraSmall).background(MaterialTheme.colorScheme.primary))
                    Text("JFMAMJJASOND"[i].toString(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    CountList(stringResource(R.string.species_ft_habitats), ft.habitats, stats.nObs)
    CountList(stringResource(R.string.species_ft_substrates), ft.substrates, stats.nObs)
    CountList(stringResource(R.string.species_ft_regions), ft.regions, stats.nObs)
    Text(stringResource(R.string.species_ft_source_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (ft.photos.isNotEmpty()) {
        HorizontalDivider()
        Text(stringResource(R.string.species_ft_photos), style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ft.photos, key = { it.first.filename }) { (_, file) ->
                AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(150.dp).clip(MaterialTheme.shapes.large))
            }
        }
        Text(stringResource(R.string.species_ft_photos_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CountList(title: String, items: List<NamedCount>, total: Int) {
    if (items.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleSmall)
    items.forEach { c ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(c.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text("${(100f * c.count / total.coerceAtLeast(1)).let { "%.0f".format(it) }} %", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WikipediaSection(state: SpeciesDetailState) {
    val context = LocalContext.current
    val preferred = contentLanguage()
    var lang by remember(state.wikiLanguages) { mutableStateOf(if (preferred in state.wikiLanguages) preferred else state.wikiLanguages.firstOrNull() ?: preferred) }
    if (state.wiki.isEmpty()) {
        Text(stringResource(R.string.species_wiki_missing), style = MaterialTheme.typography.bodyMedium)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.wikiLanguages.forEach { l ->
            FilterChip(selected = lang == l, onClick = { lang = l }, label = { Text(if (l == "de") "Deutsch" else "English") })
        }
    }
    val w = state.wiki[lang] ?: return
    Text(w.article.title, style = MaterialTheme.typography.titleLarge)
    w.article.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    w.thumb?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.large)) }
    Text(w.article.summary, style = MaterialTheme.typography.bodyMedium)
    listOf(
        "morphology" to R.string.species_wiki_morphology,
        "habitat" to R.string.species_wiki_habitat,
        "similar" to R.string.species_wiki_similar,
        "toxicity" to R.string.species_wiki_toxicity,
    ).forEach { (key, title) ->
        w.sections[key]?.takeIf { it.isNotBlank() }?.let { text ->
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
    HorizontalDivider()
    Text(stringResource(R.string.species_wiki_attribution, w.article.license, w.article.revisionId, w.article.retrievedAt.take(10)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, w.article.url.toUri())) }) {
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.species_wiki_open))
    }
}
