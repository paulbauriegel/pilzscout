package de.pilzscout.core.model

import kotlinx.serialization.Serializable

/** Where a statement in a comparison comes from. Every statement must carry one. */
@Serializable
enum class Basis {
    OBSERVED_IN_PHOTOS,
    REFERENCE,
    FUNGITASTIC,
    WIKIPEDIA,
    AI_GENERATED,
}

/** The eight feature rows of the similar-species comparison. */
@Serializable
enum class Feature {
    CAP,
    GILLS_PORES,
    STEM,
    RING,
    BASE_VOLVA,
    SURFACE_TEXTURE,
    HABITAT_SUBSTRATE,
    REGION_SEASON,
}

@Serializable
enum class EvidenceState {
    SUPPORTS_PRIMARY,
    SUPPORTS_ALTERNATIVE,
    SHARED,
    NOT_VISIBLE,
    INSUFFICIENT_REFERENCE,
}
