package de.pilzscout.app.ui.navigation

import androidx.navigation3.runtime.NavKey
import de.pilzscout.core.model.ViewType
import kotlinx.serialization.Serializable

/** All navigation destinations. [HomeKey] hosts the three bottom-navigation tabs as swipeable pages. */
sealed interface AppKey : NavKey

@Serializable data object HomeKey : AppKey

@Serializable data object SettingsKey : AppKey

/**
 * Full-screen camera. [replacePhotoId] retakes an existing draft photo in place; [recommendedView]
 * preselects that view in the camera (the result screen asks for a specific view); the user can still change it.
 */
@Serializable data class CameraKey(val replacePhotoId: String? = null, val recommendedView: ViewType? = null) : AppKey
@Serializable data object AnalysisKey : AppKey
@Serializable data class ResultKey(val observationId: String) : AppKey
@Serializable data class ComparisonKey(val observationId: String, val alternativeSpeciesId: String) : AppKey
@Serializable data class SpeciesDetailKey(val speciesId: String, val source: String) : AppKey
