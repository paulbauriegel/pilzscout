package de.pilzscout.core.compare

import de.pilzscout.core.identify.FeatureViews
import de.pilzscout.core.model.Basis
import de.pilzscout.core.model.EvidenceState
import de.pilzscout.core.model.Feature
import de.pilzscout.core.model.ViewType
import kotlinx.serialization.Serializable

/** One statement about one species for one feature, with its provenance. */
@Serializable
data class Statement(val text: String, val basis: Basis, val source: String? = null, val valueKey: String? = null)

/** All statements known for a species, grouped by feature. */
data class SpeciesTraits(val speciesId: String, val byFeature: Map<Feature, List<Statement>>) {
    fun valueKey(feature: Feature): String? = byFeature[feature]?.firstNotNullOfOrNull { it.valueKey }
    fun has(feature: Feature): Boolean = byFeature[feature]?.isNotEmpty() == true
}

@Serializable
data class FeatureRow(
    val feature: Feature,
    val state: EvidenceState,
    val primary: List<Statement>,
    val alternative: List<Statement>,
    /** The basis on which the state itself was decided (e.g. OBSERVED_IN_PHOTOS), null for INSUFFICIENT/NOT_VISIBLE. */
    val decidedBy: Basis? = null,
)

@Serializable
data class ComparisonResult(
    val primarySpeciesId: String,
    val alternativeSpeciesId: String,
    val rows: List<FeatureRow>,
) {
    val differingFeatures: Set<Feature>
        get() = rows.filter { it.state == EvidenceState.SUPPORTS_PRIMARY || it.state == EvidenceState.SUPPORTS_ALTERNATIVE || it.state == EvidenceState.NOT_VISIBLE }
            .map { it.feature }.toSet()
}

/** Per-view ranked species ids from the photo of that view (best first). */
typealias PhotoRanking = Map<ViewType, List<String>>

/**
 * Offline comparison of the primary candidate with one alternative, feature by feature.
 *
 * Rules:
 * - no trait on either side                       -> INSUFFICIENT_REFERENCE
 * - both sides have the same normalised value      -> SHARED
 * - values differ but the view needed is missing   -> NOT_VISIBLE
 * - values differ and the view exists              -> whichever species that photo ranks higher
 * - REGION_SEASON compares month frequencies from FungiTastic (ratio >= 1.5 decides), no month -> INSUFFICIENT
 */
object ComparisonEngine {

    const val SEASON_RATIO = 1.5f

    fun compare(
        primary: SpeciesTraits,
        alternative: SpeciesTraits,
        capturedViews: Set<ViewType>,
        photoRanking: PhotoRanking,
        month: Int?,
        monthHistPrimary: IntArray?,
        monthHistAlternative: IntArray?,
    ): ComparisonResult {
        val rows = Feature.entries.map { feature ->
            val p = primary.byFeature[feature].orEmpty()
            val a = alternative.byFeature[feature].orEmpty()
            if (feature == Feature.REGION_SEASON) {
                return@map seasonRow(feature, p, a, month, monthHistPrimary, monthHistAlternative)
            }
            if (p.isEmpty() || a.isEmpty()) return@map FeatureRow(feature, EvidenceState.INSUFFICIENT_REFERENCE, p, a)
            val pv = primary.valueKey(feature)
            val av = alternative.valueKey(feature)
            if (pv != null && av != null && pv == av) return@map FeatureRow(feature, EvidenceState.SHARED, p, a, Basis.REFERENCE)
            val view = FeatureViews.requiredView(feature)
            if (view == null || view !in capturedViews) return@map FeatureRow(feature, EvidenceState.NOT_VISIBLE, p, a)
            val ranking = photoRanking[view].orEmpty()
            val pi = ranking.indexOf(primary.speciesId).let { if (it < 0) Int.MAX_VALUE else it }
            val ai = ranking.indexOf(alternative.speciesId).let { if (it < 0) Int.MAX_VALUE else it }
            when {
                pi == Int.MAX_VALUE && ai == Int.MAX_VALUE -> FeatureRow(feature, EvidenceState.NOT_VISIBLE, p, a)
                pi <= ai -> FeatureRow(feature, EvidenceState.SUPPORTS_PRIMARY, p, a, Basis.OBSERVED_IN_PHOTOS)
                else -> FeatureRow(feature, EvidenceState.SUPPORTS_ALTERNATIVE, p, a, Basis.OBSERVED_IN_PHOTOS)
            }
        }
        return ComparisonResult(primary.speciesId, alternative.speciesId, rows)
    }

    private fun seasonRow(feature: Feature, p: List<Statement>, a: List<Statement>, month: Int?, hp: IntArray?, ha: IntArray?): FeatureRow {
        if (month == null || hp == null || ha == null || hp.sum() == 0 || ha.sum() == 0) {
            return FeatureRow(feature, if (p.isEmpty() && a.isEmpty()) EvidenceState.INSUFFICIENT_REFERENCE else EvidenceState.NOT_VISIBLE, p, a)
        }
        val fp = (hp[month - 1] + 1f) / (hp.sum() + 12f)
        val fa = (ha[month - 1] + 1f) / (ha.sum() + 12f)
        val state = when {
            fp / fa >= SEASON_RATIO -> EvidenceState.SUPPORTS_PRIMARY
            fa / fp >= SEASON_RATIO -> EvidenceState.SUPPORTS_ALTERNATIVE
            else -> EvidenceState.SHARED
        }
        return FeatureRow(feature, state, p, a, Basis.FUNGITASTIC)
    }
}
