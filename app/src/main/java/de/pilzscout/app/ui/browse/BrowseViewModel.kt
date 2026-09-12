package de.pilzscout.app.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.pilzscout.app.data.species.FtObservationEntity
import de.pilzscout.app.data.species.FtPhotoEntity
import de.pilzscout.app.data.species.GroupCount
import de.pilzscout.app.data.species.SpeciesRepository
import de.pilzscout.app.data.species.SpeciesSummary
import de.pilzscout.app.pack.PackFiles
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class BrowseSource { FUNGITASTIC, WIKIPEDIA }
enum class BrowseTab { SPECIES, GROUPS, OBSERVATIONS }

data class GroupsState(
    val families: List<GroupCount> = emptyList(),
    val selectedFamily: String? = null,
    val genera: List<GroupCount> = emptyList(),
    val selectedGenus: String? = null,
    val species: List<SpeciesSummary> = emptyList(),
)

data class ObservationRow(val observation: FtObservationEntity, val lead: FtPhotoEntity?, val thumb: File?, val speciesName: String)

data class BrowseUiState(
    val dbAvailable: Boolean = false,
    val query: String = "",
    val source: BrowseSource = BrowseSource.FUNGITASTIC,
    val tab: BrowseTab = BrowseTab.SPECIES,
    val results: List<SpeciesSummary> = emptyList(),
    val total: Int = 0,
    val inGermany: Int = 0,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val species: SpeciesRepository,
    private val packFiles: PackFiles,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val source = MutableStateFlow(BrowseSource.FUNGITASTIC)
    private val tab = MutableStateFlow(BrowseTab.SPECIES)

    val state: StateFlow<BrowseUiState> = combine(species.database, query.debounce(150), source, tab) { db, q, src, t ->
        if (db == null) return@combine BrowseUiState(dbAvailable = false, query = q, source = src, tab = t)
        val (total, de) = species.counts()
        val results = species.search(q, limit = 100).let { list ->
            if (src == BrowseSource.WIKIPEDIA) list // all species stay browsable; the detail page says when no article exists
            else list
        }
        BrowseUiState(dbAvailable = true, query = q, source = src, tab = t, results = results, total = total, inGermany = de)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseUiState())

    private val _groups = MutableStateFlow(GroupsState())
    val groups: StateFlow<GroupsState> = _groups

    private val _observations = MutableStateFlow<List<ObservationRow>>(emptyList())
    val observations: StateFlow<List<ObservationRow>> = _observations

    fun setQuery(q: String) { query.value = q }
    fun setSource(s: BrowseSource) { source.value = s }
    fun setTab(t: BrowseTab) {
        tab.value = t
        when (t) {
            BrowseTab.GROUPS -> if (_groups.value.families.isEmpty()) loadFamilies()
            BrowseTab.OBSERVATIONS -> if (_observations.value.isEmpty()) loadObservations()
            else -> Unit
        }
    }

    private fun loadFamilies() = viewModelScope.launch {
        _groups.value = _groups.value.copy(families = species.families())
    }

    fun selectFamily(family: String?) = viewModelScope.launch {
        _groups.value = _groups.value.copy(
            selectedFamily = family, selectedGenus = null, species = emptyList(),
            genera = if (family == null) emptyList() else species.generaInFamily(family),
        )
    }

    fun selectGenus(genus: String?) = viewModelScope.launch {
        _groups.value = _groups.value.copy(selectedGenus = genus, species = if (genus == null) emptyList() else species.byGenus(genus))
    }

    fun loadObservations(offset: Int = 0) = viewModelScope.launch {
        val obs = species.ftRecent(limit = 60, offset = offset)
        val names = species.byIds(obs.map { it.speciesId }.distinct())
        val rows = obs.map { o ->
            val lead = species.ftPhotos(o.observationId).firstOrNull()
            ObservationRow(o, lead, packFiles.existing(lead?.thumbFile), names[o.speciesId]?.binomial ?: o.speciesId)
        }
        _observations.value = if (offset == 0) rows else _observations.value + rows
    }
}
