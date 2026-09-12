package de.pilzscout.core.model

import kotlinx.serialization.Serializable

/** The four suggested photo views. Order is the suggested capture order. */
@Serializable
enum class ViewType(val exportName: String) {
    CAP("cap"),
    UNDERSIDE("underside"),
    STEM_BASE("stem-base"),
    HABITAT("habitat"),
}
