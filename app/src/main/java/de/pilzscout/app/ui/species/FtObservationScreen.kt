package de.pilzscout.app.ui.species

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import de.pilzscout.app.R
import de.pilzscout.app.data.species.FtObservationEntity
import de.pilzscout.app.data.species.FtPhotoEntity
import de.pilzscout.app.data.species.SpeciesEntity
import de.pilzscout.app.data.species.SpeciesRepository
import de.pilzscout.app.pack.PackFiles
import de.pilzscout.app.ui.result.displayName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

data class FtObservationState(
    val loading: Boolean = true,
    val observation: FtObservationEntity? = null,
    val species: SpeciesEntity? = null,
    val photos: List<Triple<FtPhotoEntity, File?, File?>> = emptyList(), // photo, thumb/hd file, mask file
)

@HiltViewModel(assistedFactory = FtObservationViewModel.Factory::class)
class FtObservationViewModel @AssistedInject constructor(
    @Assisted private val observationId: Long,
    private val repo: SpeciesRepository,
    private val packFiles: PackFiles,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(observationId: Long): FtObservationViewModel
    }

    private val _state = MutableStateFlow(FtObservationState())
    val state: StateFlow<FtObservationState> = _state

    init {
        viewModelScope.launch {
            val obs = repo.ftObservation(observationId)
            val sp = obs?.let { repo.byId(it.speciesId) }
            val photos = repo.ftPhotos(observationId).map { p ->
                val image = packFiles.existing(p.hdFile) ?: packFiles.existing(p.thumbFile)
                val mask = if (p.hasMask == 1) packFiles.existing("fungitastic/masks/${p.filename.substringBeforeLast('.')}.png") else null
                Triple(p, image, mask)
            }
            _state.value = FtObservationState(false, obs, sp, photos)
        }
    }
}

@Composable
fun FtObservationScreen(
    observationId: Long,
    onBack: () -> Unit,
    onOpenSpecies: (String) -> Unit,
    viewModel: FtObservationViewModel = hiltViewModel<FtObservationViewModel, FtObservationViewModel.Factory> { it.create(observationId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showMask by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ft_observation_title, observationId)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        val o = state.observation
        if (state.loading || o == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { if (state.loading) CircularProgressIndicator() else Text(stringResource(R.string.unknown_species)) }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val pager = rememberPagerState { state.photos.size }
            if (state.photos.isNotEmpty()) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(300.dp), pageSpacing = 12.dp) { page ->
                    val (photo, image, mask) = state.photos[page]
                    Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        image?.let { AsyncImage(model = it, contentDescription = photo.caption, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                        if (showMask && mask != null) AsyncImage(model = mask, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().alpha(0.5f))
                    }
                }
                Text(stringResource(R.string.ft_photo_counter, pager.currentPage + 1, state.photos.size), style = MaterialTheme.typography.labelMedium)
                val current = state.photos[pager.currentPage]
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (current.third != null) FilterChip(selected = showMask, onClick = { showMask = !showMask }, label = { Text(stringResource(R.string.ft_mask_toggle)) })
                    else AssistChip(onClick = {}, label = { Text(stringResource(R.string.ft_mask_none)) })
                }
                current.first.caption?.let {
                    Text(stringResource(R.string.ft_caption_title), style = MaterialTheme.typography.titleSmall)
                    Text(it, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.ft_caption_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            state.species?.let { sp ->
                Text(sp.displayName(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth().clickable { onOpenSpecies(sp.id) })
                Text(sp.binomial, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyMedium)
            }
            MetaRow(stringResource(R.string.ft_meta_date), o.eventDate)
            MetaRow(stringResource(R.string.ft_meta_region), listOfNotNull(o.region, o.district).joinToString(", ").ifBlank { null })
            MetaRow(stringResource(R.string.ft_meta_coords), if (o.lat != null && o.lon != null) "%.1f, %.1f".format(o.lat, o.lon) + (o.coordUncert?.let { " (± ${it.toInt()} m)" } ?: "") else null)
            MetaRow(stringResource(R.string.ft_meta_habitat), o.habitat)
            MetaRow(stringResource(R.string.ft_meta_substrate), listOfNotNull(o.substrate, o.metaSubstrate).distinct().joinToString(" · ").ifBlank { null })
            MetaRow(stringResource(R.string.ft_meta_elevation), o.elevation?.let { "${it.toInt()} m" })
            MetaRow(stringResource(R.string.ft_meta_biogeo), o.biogeoRegion)
            HorizontalDivider()
            Text(stringResource(R.string.ft_provenance_title), style = MaterialTheme.typography.titleSmall)
            MetaRow(stringResource(R.string.ft_meta_split), o.split)
            MetaRow(stringResource(R.string.ft_meta_label), stringResource(if (o.dnaSequenced == 1) R.string.ft_label_dna else R.string.ft_label_expert))
            Text(stringResource(R.string.ft_dataset_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
    }
}
