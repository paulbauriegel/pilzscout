package de.pilzscout.app.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.pilzscout.app.data.species.SpeciesRepository
import de.pilzscout.app.data.species.SpeciesSummary
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class BrowseSource { FUNGITASTIC, WIKIPEDIA }

data class BrowseUiState(
    val dbAvailable: Boolean = false,
    val query: String = "",
    val source: BrowseSource = BrowseSource.FUNGITASTIC,
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
    private val source = MutableStateFlow(BrowseSource.FUNGITASTIC)

    val state: StateFlow<BrowseUiState> = combine(species.database, query.debounce(150), source) { db, q, src ->
        if (db == null) return@combine BrowseUiState(dbAvailable = false, query = q, source = src)
        val (total, de) = species.counts()
        BrowseUiState(dbAvailable = true, query = q, source = src, results = species.search(q, limit = 100), total = total, inGermany = de)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseUiState())

    fun setQuery(q: String) { query.value = q }
    fun setSource(s: BrowseSource) { source.value = s }
}
