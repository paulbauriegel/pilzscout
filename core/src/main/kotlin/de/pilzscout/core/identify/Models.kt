package de.pilzscout.core.identify

import de.pilzscout.core.model.Feature
import de.pilzscout.core.model.ViewType
import kotlinx.serialization.Serializable

/** One entry of a ranked prediction list. speciesId is null only if the DB knows fewer classes than the model. */
@Serializable
data class Candidate(val classIndex: Int, val speciesId: String?, val prob: Float)

@Serializable
enum class ConfidenceDescriptor { STRONG, UNCERTAIN, SEVERAL_PLAUSIBLE }

/** How one photograph's own top prediction relates to the combined result. */
@Serializable
enum class Agreement { SUPPORTS, PARTIAL, CONFLICTS }

@Serializable
data class PhotoPrediction(
    val viewType: ViewType,
    val position: Int,
    val top: List<Candidate>,
    val agreement: Agreement,
    val inferenceMs: Long,
)

/** Everything the result screen, history and export need about how the photos were combined. */
@Serializable
data class FusionResult(
    val combined: List<Candidate>,
    val descriptor: ConfidenceDescriptor,
    val alternatives: List<Candidate>,
    val photos: List<PhotoPrediction>,
    val germanyFilter: Boolean,
    val maskedOutClasses: Int,
    val month: Int?,
    val seasonPriorWeight: Float,
    val recommendedView: ViewType?,
) {
    val primary: Candidate get() = combined.first()
}

/** Which suggested photo view is needed to judge a comparison feature. null = judged from context, not a photo. */
object FeatureViews {
    fun requiredView(feature: Feature): ViewType? = when (feature) {
        Feature.CAP, Feature.SURFACE_TEXTURE -> ViewType.CAP
        Feature.GILLS_PORES -> ViewType.UNDERSIDE
        Feature.STEM, Feature.RING, Feature.BASE_VOLVA -> ViewType.STEM_BASE
        Feature.HABITAT_SUBSTRATE -> ViewType.HABITAT
        Feature.REGION_SEASON -> null
    }

    fun featuresFor(view: ViewType): List<Feature> = Feature.entries.filter { requiredView(it) == view }
}
