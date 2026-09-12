package de.pilzscout.core.model

import kotlinx.serialization.Serializable

/**
 * Photo views. The four suggested views are a guide, not a requirement: any photo can stay
 * unassigned as OTHER and still counts as evidence. Order of the suggested views is the capture order.
 */
@Serializable
enum class ViewType(val exportName: String) {
    CAP("cap"),
    UNDERSIDE("underside"),
    STEM_BASE("stem-base"),
    HABITAT("habitat"),
    OTHER("photo");

    val suggested: Boolean get() = this != OTHER

    companion object {
        val suggestedViews: List<ViewType> = entries.filter { it.suggested }
    }
}
