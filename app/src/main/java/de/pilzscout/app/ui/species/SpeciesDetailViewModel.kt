package de.pilzscout.app.ui.species

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import de.pilzscout.app.data.species.FtPhotoEntity
import de.pilzscout.app.data.species.SpeciesEntity
import de.pilzscout.app.data.species.SpeciesRepository
import de.pilzscout.app.data.species.SpeciesStatsEntity
import de.pilzscout.app.data.species.WikiArticleEntity
import de.pilzscout.app.pack.PackFiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

data class NamedCount(val name: String, val count: Int)

data class FtSection(
    val stats: SpeciesStatsEntity?,
    val habitats: List<NamedCount>,
    val substrates: List<NamedCount>,
    val regions: List<NamedCount>,
    val monthHist: IntArray?,
    val photos: List<Pair<FtPhotoEntity, File>>,
)

data class WikiSection(val article: WikiArticleEntity, val sections: Map<String, String>, val thumb: File?)

data class SpeciesDetailState(
    val loading: Boolean = true,
    val species: SpeciesEntity? = null,
    val fungiTastic: FtSection? = null,
    val wikiLanguages: List<String> = emptyList(),
    val wiki: Map<String, WikiSection> = emptyMap(),
)

@HiltViewModel(assistedFactory = SpeciesDetailViewModel.Factory::class)
class SpeciesDetailViewModel @AssistedInject constructor(
    @Assisted private val speciesId: String,
    private val repo: SpeciesRepository,
    private val packFiles: PackFiles,
    private val json: Json,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(speciesId: String): SpeciesDetailViewModel
    }

    private val _state = MutableStateFlow(SpeciesDetailState())
    val state: StateFlow<SpeciesDetailState> = _state

    init {
        viewModelScope.launch { load() }
    }

    private fun counts(raw: String): List<NamedCount> = runCatching {
        json.parseToJsonElement(raw).jsonArray.map { e -> NamedCount(e.jsonArray[0].jsonPrimitive.content, e.jsonArray[1].jsonPrimitive.content.toInt()) }
    }.getOrDefault(emptyList())

    private suspend fun load() {
        val sp = repo.byId(speciesId)
        val stats = repo.stats(speciesId)
        val photos = repo.ftPhotosForSpecies(speciesId, 4).mapNotNull { p -> packFiles.existing(p.hdFile ?: p.thumbFile)?.let { p to it } }
        val ft = FtSection(
            stats = stats,
            habitats = stats?.let { counts(it.habitatsJson) } ?: emptyList(),
            substrates = stats?.let { counts(it.substratesJson) } ?: emptyList(),
            regions = stats?.let { counts(it.regionsJson) } ?: emptyList(),
            monthHist = stats?.monthHistJson?.trim('[', ']')?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.toIntArray()?.takeIf { it.size == 12 },
            photos = photos,
        )
        val langs = repo.wikiLanguages(speciesId)
        val wiki = langs.mapNotNull { lang ->
            repo.wikiArticle(speciesId, lang)?.let { a ->
                val sections = runCatching {
                    json.parseToJsonElement(a.sectionsJson).jsonObject.mapValues { (_, v) -> (v as? JsonPrimitive)?.content ?: "" }
                }.getOrDefault(emptyMap())
                lang to WikiSection(a, sections, packFiles.existing(a.thumbFile))
            }
        }.toMap()
        _state.value = SpeciesDetailState(false, sp, ft, langs, wiki)
    }
}

@Suppress("unused")
private val unusedJsonArray = JsonArray::class
