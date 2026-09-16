package de.pilzscout.app.ui.history

import de.pilzscout.app.data.history.ObservationEntity
import de.pilzscout.app.data.history.ObservationWithPhotos
import de.pilzscout.app.data.species.SpeciesEntity
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryFilterTest {

    private fun species(id: String, edibility: String?, commonDe: String? = null) = SpeciesEntity(
        id = id, scientificName = id, binomial = id, genus = id.substringBefore(' '), specificEpithet = id.substringAfter(' ', ""),
        family = null, orderName = null, className = null, commonDe = commonDe, commonEn = null, poisonous = 0, gbifKey = null,
        deOccurrences = 0, inGermany = 1, modelClassIndex = 0, hasWikiDe = 0, hasWikiEn = 0, nObservations = 0,
        edibility = edibility, edibilitySource = null,
    )

    private fun observation(
        speciesId: String,
        prob: Float = 0.8f,
        capturedAt: Long = 1_000L,
        confirmed: Boolean = false,
        corrected: String? = null,
        lat: Double? = 51.0,
        lon: Double? = 10.0,
    ) = ObservationEntity(
        id = "obs-$speciesId-$capturedAt", createdAt = capturedAt, capturedAt = capturedAt, lat = lat, lon = lon,
        locationIncluded = lat != null, placeName = null, primarySpeciesId = speciesId, primaryProb = prob, descriptor = "",
        nPhotos = 1, modelVersion = "v", modelPrecision = "fp16", totalInferenceMs = 1, mode = "single", userConfirmed = confirmed,
        correctedSpeciesId = corrected, correctedAt = null, leadPhotoId = "p", fusionJson = "{}", comparisonJson = "{}", contextualJson = "{}",
    )

    private fun item(o: ObservationEntity, primary: SpeciesEntity?, corrected: SpeciesEntity? = null) =
        HistoryItem(ObservationWithPhotos(o, emptyList(), emptyList()), primary, corrected)

    private val boletus = species("Boletus edulis", "CHOICE", commonDe = "Steinpilz")
    private val amanita = species("Amanita phalloides", "DEADLY", commonDe = "Grüner Knollenblätterpilz")
    private val morchella = species("Morchella esculenta", "CAUTION")
    private val unknown = species("Mycena sp", null)

    @Test
    fun edibleChipKeepsEdibleAndChoiceOnly() {
        val f = HistoryFilter(onlyEdible = true)
        assertTrue(f.matches(item(observation("b"), boletus)))
        assertTrue(f.matches(item(observation("e"), species("Cantharellus cibarius", "EDIBLE"))))
        assertFalse(f.matches(item(observation("a"), amanita)))
        assertFalse(f.matches(item(observation("m"), morchella)))
        assertFalse(f.matches(item(observation("u"), unknown)))
        assertFalse(f.matches(item(observation("x"), null)), "missing species must not count as edible")
    }

    @Test
    fun edibleChipUsesCorrectedSpeciesOverPrediction() {
        val f = HistoryFilter(onlyEdible = true)
        assertTrue(f.matches(item(observation("a", corrected = boletus.id), amanita, corrected = boletus)))
        assertFalse(f.matches(item(observation("b", corrected = amanita.id), boletus, corrected = amanita)))
    }

    @Test
    fun edibleChipOffLeavesOtherClausesUntouched() {
        assertTrue(HistoryFilter().matches(item(observation("a"), amanita)))
        assertTrue(HistoryFilter().matches(item(observation("u"), unknown)))
    }

    @Test
    fun edibleCombinesWithConfirmedAndDateAndQuery() {
        val f = HistoryFilter(onlyEdible = true, onlyConfirmed = true, fromEpochMs = 500L, query = "stein")
        assertTrue(f.matches(item(observation("b", confirmed = true, capturedAt = 600L), boletus)))
        assertFalse(f.matches(item(observation("b", confirmed = false, capturedAt = 600L), boletus)))
        assertFalse(f.matches(item(observation("b", confirmed = true, capturedAt = 400L), boletus)))
        assertFalse(f.matches(item(observation("b", confirmed = true, capturedAt = 600L), species("Cantharellus cibarius", "EDIBLE"))))
    }

    @Test
    fun locatedRequiresIncludedCoordinates() {
        assertTrue(item(observation("b"), boletus).located)
        assertFalse(item(observation("b", lat = null, lon = null), boletus).located)
    }
}
