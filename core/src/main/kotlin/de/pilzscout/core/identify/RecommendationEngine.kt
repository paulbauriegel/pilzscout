package de.pilzscout.core.identify

import de.pilzscout.core.model.Feature
import de.pilzscout.core.model.ViewType

/**
 * Suggests one additional photograph when evidence is missing.
 * Priority: the first missing view whose features differ between primary and top alternative;
 * otherwise (result not STRONG) the first missing view in the order underside, stem base, cap, habitat.
 */
object RecommendationEngine {
    private val order = listOf(ViewType.UNDERSIDE, ViewType.STEM_BASE, ViewType.CAP, ViewType.HABITAT)

    fun recommend(
        capturedViews: Set<ViewType>,
        descriptor: ConfidenceDescriptor,
        differingFeatures: Set<Feature>,
    ): ViewType? {
        val missing = order.filter { it !in capturedViews }
        if (missing.isEmpty()) return null
        // Three or more photos of any kind already give a rounded picture; only suggest a specific view when it matters.
        val unassigned = capturedViews.count { it == ViewType.OTHER }
        if (unassigned > 0 && descriptor == ConfidenceDescriptor.STRONG && differingFeatures.isEmpty()) return null
        missing.firstOrNull { view -> FeatureViews.featuresFor(view).any { it in differingFeatures } }?.let { return it }
        return if (descriptor != ConfidenceDescriptor.STRONG) missing.first() else null
    }
}
