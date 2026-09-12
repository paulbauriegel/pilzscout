package de.pilzscout.app.ui.comparison

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import de.pilzscout.app.compare.CompareUseCase
import de.pilzscout.app.data.history.HistoryRepository
import de.pilzscout.app.data.species.SpeciesEntity
import de.pilzscout.app.data.species.SpeciesRepository
import de.pilzscout.app.explain.ConnectivityObserver
import de.pilzscout.app.explain.ExplanationProvider
import de.pilzscout.app.explain.ExplanationRequest
import de.pilzscout.core.compare.ComparisonResult
import de.pilzscout.core.compare.Statement
import de.pilzscout.core.identify.Candidate
import de.pilzscout.core.identify.FusionResult
import de.pilzscout.core.model.Feature
import de.pilzscout.core.model.ViewType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class ComparisonUiState(
    val loading: Boolean = true,
    val primary: SpeciesEntity? = null,
    val primaryProbPercent: Int = 0,
    val alternatives: List<Candidate> = emptyList(),
    val selectedAlternativeId: String? = null,
    val species: Map<String, SpeciesEntity> = emptyMap(),
    val result: ComparisonResult? = null,
    val aiRows: Map<Feature, Statement> = emptyMap(),
    val aiLoading: Boolean = false,
    val aiError: String? = null,
)

@HiltViewModel(assistedFactory = ComparisonViewModel.Factory::class)
class ComparisonViewModel @AssistedInject constructor(
    @Assisted("observationId") private val observationId: String,
    @Assisted("alternativeId") private val initialAlternativeId: String,
    private val history: HistoryRepository,
    private val speciesRepository: SpeciesRepository,
    private val compare: CompareUseCase,
    private val explanation: ExplanationProvider,
    connectivity: ConnectivityObserver,
    private val json: Json,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(@Assisted("observationId") observationId: String, @Assisted("alternativeId") alternativeId: String): ComparisonViewModel
    }

    private val _state = MutableStateFlow(ComparisonUiState())
    val state: StateFlow<ComparisonUiState> = _state
    val online: StateFlow<Boolean> = connectivity.online.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), connectivity.isOnline())

    private var fusion: FusionResult? = null
    private var capturedViews: Set<ViewType> = emptySet()
    private var photoRanking: Map<ViewType, List<String>> = emptyMap()
    private var month: Int? = null
    private var lang = "en"

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val obs = history.byId(observationId) ?: return
        val f = runCatching { json.decodeFromString(FusionResult.serializer(), obs.observation.fusionJson) }.getOrNull() ?: return
        fusion = f
        month = f.month
        capturedViews = f.photos.map { it.viewType }.toSet()
        photoRanking = f.photos.groupBy { it.viewType }.mapValues { (_, ps) -> ps.flatMap { p -> p.top.mapNotNull { it.speciesId } }.distinct() }
        val ids = (f.alternatives.mapNotNull { it.speciesId } + f.primary.speciesId).filterNotNull()
        val species = speciesRepository.byIds(ids)
        _state.value = ComparisonUiState(
            loading = false,
            primary = f.primary.speciesId?.let { species[it] },
            primaryProbPercent = (f.primary.prob * 100).toInt(),
            alternatives = f.alternatives,
            selectedAlternativeId = initialAlternativeId,
            species = species,
        )
        select(initialAlternativeId)
    }

    fun setLanguage(language: String) {
        if (language != lang) {
            lang = language
            _state.value.selectedAlternativeId?.let { select(it) }
        }
    }

    fun select(alternativeId: String) {
        val primaryId = fusion?.primary?.speciesId ?: return
        viewModelScope.launch {
            val result = compare.compare(primaryId, alternativeId, lang, capturedViews, photoRanking, month)
            _state.value = _state.value.copy(selectedAlternativeId = alternativeId, result = result, aiRows = emptyMap(), aiError = null)
        }
    }

    fun generateExplanation(primaryName: String, alternativeName: String) {
        val s = _state.value
        val result = s.result ?: return
        val primary = s.primary ?: return
        val alt = s.species[s.selectedAlternativeId] ?: return
        _state.value = s.copy(aiLoading = true, aiError = null)
        viewModelScope.launch {
            try {
                val e = explanation.explain(ExplanationRequest(primaryName, primary.binomial, alternativeName, alt.binomial, result, lang))
                _state.value = _state.value.copy(aiRows = e.perFeature, aiLoading = false)
            } catch (ex: Exception) {
                _state.value = _state.value.copy(aiLoading = false, aiError = ex.message ?: ex.toString())
            }
        }
    }
}
