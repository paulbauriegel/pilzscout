package de.pilzscout.app.ui.history

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.pilzscout.app.data.history.HistoryRepository
import de.pilzscout.app.data.history.ObservationWithPhotos
import de.pilzscout.app.data.species.SpeciesEntity
import de.pilzscout.app.data.species.SpeciesRepository
import de.pilzscout.app.data.species.SpeciesSummary
import de.pilzscout.app.export.ObservationExporter
import de.pilzscout.core.model.Edibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import de.pilzscout.app.location.PlaceResolver
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryFilter(
    val query: String = "",
    val minConfidence: Float = 0f,
    val fromEpochMs: Long? = null,
    val toEpochMs: Long? = null,
    val onlyConfirmed: Boolean = false,
    /** Only species whose reference edibility is EDIBLE or CHOICE (never a statement about the photographed mushroom). */
    val onlyEdible: Boolean = false,
) {
    /** Pure predicate shared by list and map; [normalizedQuery] is [query] passed through [SpeciesRepository.normalize]. */
    fun matches(item: HistoryItem, normalizedQuery: String = SpeciesRepository.normalize(query)): Boolean {
        val o = item.entry.observation
        val shown = item.shown
        val nameHit = normalizedQuery.isBlank() || listOfNotNull(shown?.binomial, shown?.commonDe, shown?.commonEn, item.primary?.binomial)
            .any { SpeciesRepository.normalize(it).contains(normalizedQuery) }
        val e = item.edibility
        return nameHit && o.primaryProb >= minConfidence &&
            (fromEpochMs == null || o.capturedAt >= fromEpochMs) &&
            (toEpochMs == null || o.capturedAt <= toEpochMs) &&
            (!onlyConfirmed || o.userConfirmed) &&
            (!onlyEdible || e == Edibility.EDIBLE || e == Edibility.CHOICE)
    }
}

enum class HistoryView { LIST, MAP }

data class HistoryItem(val entry: ObservationWithPhotos, val primary: SpeciesEntity?, val corrected: SpeciesEntity?) {
    /** The species the user sees: their correction if any, otherwise the model's primary candidate. */
    val shown: SpeciesEntity? get() = corrected ?: primary
    val edibility: Edibility? get() = Edibility.parse(shown?.edibility)
    val located: Boolean get() = entry.observation.let { it.locationIncluded && it.lat != null && it.lon != null }
}

data class HistoryUiState(
    val items: List<HistoryItem> = emptyList(),
    val total: Int = 0,
    val filter: HistoryFilter = HistoryFilter(),
    val view: HistoryView = HistoryView.LIST,
    val selected: Set<String> = emptySet(),
    val exporting: Boolean = false,
    val message: String? = null,
    val correctionTarget: String? = null,
    val correctionResults: List<SpeciesSummary> = emptyList(),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: HistoryRepository,
    private val species: SpeciesRepository,
    private val exporter: ObservationExporter,
    private val places: PlaceResolver,
) : ViewModel() {

    init {
        // Older finds were saved before offline place names existed; resolve them once so the list shows "near X".
        viewModelScope.launch {
            species.database.filterNotNull().first()
            history.observeAll().first()
                .map { it.observation }
                .filter { it.locationIncluded && it.placeName == null && it.lat != null && it.lon != null }
                .forEach { o -> places.nearest(o.lat!!, o.lon!!)?.let { history.update(o.copy(placeName = it.name)) } }
        }
    }

    private val filter = MutableStateFlow(HistoryFilter())
    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val transient = MutableStateFlow(HistoryUiState())

    val state: StateFlow<HistoryUiState> = combine(history.observeAll(), filter, selected, transient, species.database) { all, f, sel, t, db ->
        val ids = all.flatMap { listOfNotNull(it.observation.primarySpeciesId, it.observation.correctedSpeciesId) }.distinct()
        val map = if (db != null) species.byIds(ids) else emptyMap()
        val q = SpeciesRepository.normalize(f.query)
        val items = all.map { HistoryItem(it, map[it.observation.primarySpeciesId], it.observation.correctedSpeciesId?.let { c -> map[c] }) }
            .filter { f.matches(it, q) }
        t.copy(items = items, total = all.size, filter = f, selected = sel intersect all.map { it.observation.id }.toSet())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setQuery(q: String) = filter.value.let { filter.value = it.copy(query = q) }
    fun setMinConfidence(v: Float) = filter.value.let { filter.value = it.copy(minConfidence = v) }
    fun setDateRange(from: Long?, to: Long?) = filter.value.let { filter.value = it.copy(fromEpochMs = from, toEpochMs = to) }
    fun setOnlyConfirmed(v: Boolean) = filter.value.let { filter.value = it.copy(onlyConfirmed = v) }
    fun setOnlyEdible(v: Boolean) = filter.value.let { filter.value = it.copy(onlyEdible = v) }
    fun setView(v: HistoryView) { transient.value = transient.value.copy(view = v) }

    fun toggleSelect(id: String) = selected.value.let { selected.value = if (id in it) it - id else it + id }
    fun clearSelection() { selected.value = emptySet() }
    fun selectAllVisible() { selected.value = state.value.items.map { it.entry.observation.id }.toSet() }

    fun delete(ids: Collection<String>) = viewModelScope.launch {
        history.delete(ids.toList())
        selected.value = selected.value - ids.toSet()
    }

    fun confirm(id: String, confirmed: Boolean) = viewModelScope.launch {
        history.byId(id)?.observation?.let { history.update(it.copy(userConfirmed = confirmed)) }
    }

    fun startCorrection(id: String) {
        transient.value = transient.value.copy(correctionTarget = id, correctionResults = emptyList())
        searchCorrection("")
    }

    fun cancelCorrection() { transient.value = transient.value.copy(correctionTarget = null, correctionResults = emptyList()) }

    fun searchCorrection(query: String) = viewModelScope.launch {
        transient.value = transient.value.copy(correctionResults = runCatching { species.search(query, 30) }.getOrDefault(emptyList()))
    }

    fun applyCorrection(speciesId: String?) = viewModelScope.launch {
        val id = transient.value.correctionTarget ?: return@launch
        history.byId(id)?.observation?.let {
            history.update(it.copy(correctedSpeciesId = speciesId, correctedAt = if (speciesId != null) System.currentTimeMillis() else null, userConfirmed = true))
        }
        cancelCorrection()
    }

    fun export(ids: Collection<String>, target: Uri) = viewModelScope.launch {
        transient.value = transient.value.copy(exporting = true, message = null)
        val result = runCatching { exporter.export(ids.toList(), target) }
        transient.value = transient.value.copy(exporting = false, message = if (result.isSuccess) "ok:${ids.size}" else "error:${result.exceptionOrNull()?.message}")
        if (result.isSuccess) selected.value = emptySet()
    }

    fun consumeMessage() { transient.value = transient.value.copy(message = null) }
}
