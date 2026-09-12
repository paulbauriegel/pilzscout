package de.pilzscout.app.data.species

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeciesRepository @Inject constructor(
    private val holder: SpeciesDatabaseHolder,
) {
    val available = holder.db.map { it != null }
    val database: StateFlow<SpeciesDatabase?> get() = holder.db

    private val maskMutex = Mutex()
    private var cachedMask: Pair<SpeciesDatabase, BooleanArray>? = null
    private var cachedIndex: Pair<SpeciesDatabase, Array<String?>>? = null

    private suspend fun db(): SpeciesDatabase = holder.await()

    suspend fun byId(id: String): SpeciesEntity? = db().speciesDao().byId(id)

    suspend fun byIds(ids: List<String>): Map<String, SpeciesEntity> =
        if (ids.isEmpty()) emptyMap() else db().speciesDao().byIds(ids).associateBy { it.id }

    suspend fun byClassIndex(index: Int): SpeciesEntity? = db().speciesDao().byClassIndex(index)

    suspend fun page(limit: Int, offset: Int): List<SpeciesSummary> = db().speciesDao().page(limit, offset)

    suspend fun search(query: String, limit: Int = 50): List<SpeciesSummary> {
        val norm = normalize(query)
        if (norm.isBlank()) return page(limit, 0)
        return db().speciesDao().search("%$norm%", limit)
    }

    suspend fun byGenus(genus: String) = db().speciesDao().byGenus(genus)
    suspend fun families() = db().speciesDao().families()
    suspend fun generaInFamily(family: String) = db().speciesDao().generaInFamily(family)
    suspend fun counts(): Pair<Int, Int> = db().speciesDao().let { it.count() to it.countInGermany() }

    suspend fun traits(speciesId: String, lang: String) = db().traitDao().forSpecies(speciesId, lang)
    suspend fun stats(speciesId: String) = db().traitDao().stats(speciesId)
    suspend fun statsFor(ids: List<String>) = if (ids.isEmpty()) emptyList() else db().traitDao().statsFor(ids)
    suspend fun wikiArticle(speciesId: String, lang: String) = db().wikiDao().article(speciesId, lang)
    suspend fun wikiLanguages(speciesId: String) = db().wikiDao().availableLanguages(speciesId)
    suspend fun ftObservations(speciesId: String) = db().fungiTasticDao().observationsForSpecies(speciesId)
    suspend fun ftObservation(id: Long) = db().fungiTasticDao().observation(id)
    suspend fun ftPhotos(observationId: Long) = db().fungiTasticDao().photosForObservation(observationId)
    suspend fun ftPhotosForSpecies(speciesId: String, limit: Int = 8) = db().fungiTasticDao().photosForSpecies(speciesId, limit)
    suspend fun ftLeadPhoto(speciesId: String) = db().fungiTasticDao().leadPhoto(speciesId)
    suspend fun ftRecent(limit: Int, offset: Int) = db().fungiTasticDao().recentObservations(limit, offset)

    /** mask[classIndex] == true when the species is recorded in Germany. Cached per opened database. */
    suspend fun germanyMask(numClasses: Int): BooleanArray = maskMutex.withLock {
        val current = db()
        cachedMask?.takeIf { it.first === current }?.second ?: run {
            val mask = BooleanArray(numClasses)
            val ids = arrayOfNulls<String>(numClasses)
            current.speciesDao().classIndexTable().forEach { row ->
                if (row.modelClassIndex in 0 until numClasses) {
                    mask[row.modelClassIndex] = row.inGermany == 1
                    ids[row.modelClassIndex] = row.id
                }
            }
            cachedMask = current to mask
            cachedIndex = current to ids
            mask
        }
    }

    /** speciesId per class index (null when the DB knows fewer classes than the model). */
    suspend fun classIndexToId(numClasses: Int): Array<String?> = maskMutex.withLock {
        val current = db()
        cachedIndex?.takeIf { it.first === current }?.second ?: run {
            germanyMask(numClasses)
            cachedIndex!!.second
        }
    }

    companion object {
        /** Lower-case, strip diacritics, fold German umlauts and ß the way tools/ does when building species_search. */
        fun normalize(text: String): String {
            val folded = text.lowercase()
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            return Normalizer.normalize(folded, Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace(Regex("[^a-z0-9 ]+"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")
        }
    }
}
