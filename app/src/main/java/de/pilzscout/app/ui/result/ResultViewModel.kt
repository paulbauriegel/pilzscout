package de.pilzscout.app.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import de.pilzscout.app.data.history.HistoryRepository
import de.pilzscout.app.data.history.ObservationWithPhotos
import de.pilzscout.app.data.species.SpeciesEntity
import de.pilzscout.app.data.species.SpeciesRepository
import de.pilzscout.app.identify.ContextualInputs
import de.pilzscout.core.identify.FusionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class ResultUiState(
    val loading: Boolean = true,
    val observation: ObservationWithPhotos? = null,
    val fusion: FusionResult? = null,
    val contextual: ContextualInputs? = null,
    val species: Map<String, SpeciesEntity> = emptyMap(),
    val totalClasses: Int = 0,
)

@HiltViewModel(assistedFactory = ResultViewModel.Factory::class)
class ResultViewModel @AssistedInject constructor(
    @Assisted private val observationId: String,
    private val history: HistoryRepository,
    private val speciesRepository: SpeciesRepository,
    private val json: Json,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(observationId: String): ResultViewModel
    }

    private val _state = MutableStateFlow(ResultUiState())
    val state: StateFlow<ResultUiState> = _state

    init {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        val obs = history.byId(observationId) ?: run { _state.value = ResultUiState(loading = false); return }
        val fusion = runCatching { json.decodeFromString(FusionResult.serializer(), obs.observation.fusionJson) }.getOrNull()
        val contextual = runCatching { json.decodeFromString(ContextualInputs.serializer(), obs.observation.contextualJson) }.getOrNull()
        val ids = buildSet {
            fusion?.combined?.forEach { it.speciesId?.let(::add) }
            fusion?.photos?.forEach { p -> p.top.forEach { it.speciesId?.let(::add) } }
            add(obs.observation.primarySpeciesId)
            obs.observation.correctedSpeciesId?.let(::add)
        }
        val species = speciesRepository.byIds(ids.toList())
        val (total, _) = speciesRepository.counts()
        _state.value = ResultUiState(false, obs, fusion, contextual, species, total)
    }
}
