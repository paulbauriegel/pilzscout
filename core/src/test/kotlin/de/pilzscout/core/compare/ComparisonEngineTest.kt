package de.pilzscout.core.compare

import de.pilzscout.core.model.Basis
import de.pilzscout.core.model.EvidenceState
import de.pilzscout.core.model.Feature
import de.pilzscout.core.model.ViewType
import kotlin.test.Test
import kotlin.test.assertEquals

class ComparisonEngineTest {
    private fun st(text: String, key: String? = null) = Statement(text, Basis.REFERENCE, "wiki:en:1", key)

    private val flyAgaric = SpeciesTraits(
        "amanita-muscaria",
        mapOf(
            Feature.CAP to listOf(st("Red cap with white warts", "red")),
            Feature.GILLS_PORES to listOf(st("White gills", "gills")),
            Feature.RING to listOf(st("Ring present", "present")),
            Feature.BASE_VOLVA to listOf(st("Bulbous base with rings of warts", "bulbous")),
            Feature.HABITAT_SUBSTRATE to listOf(st("Under birch and spruce", null)),
        ),
    )
    private val pantherCap = SpeciesTraits(
        "amanita-pantherina",
        mapOf(
            Feature.CAP to listOf(st("Brown cap with white warts", "brown")),
            Feature.GILLS_PORES to listOf(st("White gills", "gills")),
            Feature.RING to listOf(st("Ring present", "present")),
            Feature.BASE_VOLVA to listOf(st("Rimmed bulb", "volva")),
            Feature.HABITAT_SUBSTRATE to listOf(st("Deciduous woods", null)),
        ),
    )

    private fun row(result: ComparisonResult, f: Feature) = result.rows.first { it.feature == f }

    @Test
    fun statesFollowTheRules() {
        val result = ComparisonEngine.compare(
            flyAgaric, pantherCap,
            capturedViews = setOf(ViewType.CAP),
            photoRanking = mapOf(ViewType.CAP to listOf("amanita-muscaria", "amanita-pantherina")),
            month = 9,
            monthHistPrimary = IntArray(12) { if (it == 8) 100 else 10 },
            monthHistAlternative = IntArray(12) { 10 },
        )
        assertEquals(EvidenceState.SUPPORTS_PRIMARY, row(result, Feature.CAP).state)
        assertEquals(Basis.OBSERVED_IN_PHOTOS, row(result, Feature.CAP).decidedBy)
        assertEquals(EvidenceState.SHARED, row(result, Feature.GILLS_PORES).state)
        assertEquals(EvidenceState.SHARED, row(result, Feature.RING).state)
        assertEquals(EvidenceState.NOT_VISIBLE, row(result, Feature.BASE_VOLVA).state) // stem base photo missing
        assertEquals(EvidenceState.NOT_VISIBLE, row(result, Feature.HABITAT_SUBSTRATE).state) // no value keys, no habitat photo
        assertEquals(EvidenceState.INSUFFICIENT_REFERENCE, row(result, Feature.STEM).state)
        assertEquals(EvidenceState.SUPPORTS_PRIMARY, row(result, Feature.REGION_SEASON).state)
        assertEquals(Basis.FUNGITASTIC, row(result, Feature.REGION_SEASON).decidedBy)
        assertEquals(setOf(Feature.CAP, Feature.BASE_VOLVA, Feature.HABITAT_SUBSTRATE, Feature.REGION_SEASON), result.differingFeatures)
    }

    @Test
    fun alternativeWinsWhenPhotoRanksItHigher() {
        val result = ComparisonEngine.compare(
            flyAgaric, pantherCap,
            capturedViews = setOf(ViewType.STEM_BASE),
            photoRanking = mapOf(ViewType.STEM_BASE to listOf("amanita-pantherina", "amanita-muscaria")),
            month = null, monthHistPrimary = null, monthHistAlternative = null,
        )
        assertEquals(EvidenceState.SUPPORTS_ALTERNATIVE, row(result, Feature.BASE_VOLVA).state)
        assertEquals(EvidenceState.NOT_VISIBLE, row(result, Feature.CAP).state)
        assertEquals(EvidenceState.INSUFFICIENT_REFERENCE, row(result, Feature.REGION_SEASON).state)
    }

    @Test
    fun seasonSharedWhenSimilar() {
        val result = ComparisonEngine.compare(
            flyAgaric, pantherCap, emptySet(), emptyMap(), 9,
            IntArray(12) { 10 }, IntArray(12) { 12 },
        )
        assertEquals(EvidenceState.SHARED, row(result, Feature.REGION_SEASON).state)
    }
}
