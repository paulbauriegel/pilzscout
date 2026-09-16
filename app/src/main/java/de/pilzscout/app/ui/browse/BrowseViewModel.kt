package de.pilzscout.app.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.pilzscout.app.data.species.GroupCount
import de.pilzscout.app.data.species.SpeciesRepository
import de.pilzscout.app.data.species.SpeciesSummary
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which section the species detail page shows first; the toggle lives on that page. */
enum class BrowseSource { FUNGITASTIC, WIKIPEDIA }
enum class BrowseTab { SPECIES, GROUPS }

data class GroupsState(
    val families: List<GroupCount> = emptyList(),
    val selectedFamily: String? = null,
    val genera: List<GroupCount> = emptyList(),
    val selectedGenus: String? = null,
    val species: List<SpeciesSummary> = emptyList(),
)

data class BrowseUiState(
    val dbAvailable: Boolean = false,
    val query: String = "",
    val tab: BrowseTab = BrowseTab.SPECIES,
    val results: List<SpeciesSummary> = emptyList(),
    val total: Int = 0,
    val inGermany: Int = 0,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val species: SpeciesRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    /** Undebounced text for the search field; the debounced [query] drives the actual search. */
    val queryText: StateFlow<String> = query
    private val tab = MutableStateFlow(BrowseTab.SPECIES)

    val state: StateFlow<BrowseUiState> = combine(species.database, query.debounce(150), tab) { db, q, t ->
        if (db == null) return@combine BrowseUiState(dbAvailable = false, query = q, tab = t)
        val (total, de) = species.counts()
        val results = species.search(q, limit = 100)
        BrowseUiState(dbAvailable = true, query = q, tab = t, results = results, total = total, inGermany = de)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseUiState())

    private val _groups = MutableStateFlow(GroupsState())
    val groups: StateFlow<GroupsState> = _groups

    fun setQuery(q: String) { query.value = q }
    fun setTab(t: BrowseTab) {
        tab.value = t
        if (t == BrowseTab.GROUPS && _groups.value.families.isEmpty()) loadFamilies()
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

}
