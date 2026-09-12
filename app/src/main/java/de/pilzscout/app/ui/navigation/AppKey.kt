package de.pilzscout.app.ui.navigation

import androidx.navigation3.runtime.NavKey
import de.pilzscout.core.model.ViewType
import kotlinx.serialization.Serializable

/** All navigation destinations. Top-level keys are the three bottom-navigation tabs. */
sealed interface AppKey : NavKey

sealed interface TopLevelKey : AppKey

@Serializable data object IdentifyKey : TopLevelKey
@Serializable data object BrowseKey : TopLevelKey
@Serializable data object HistoryKey : TopLevelKey

@Serializable data object SettingsKey : AppKey
@Serializable data class CameraKey(val viewType: ViewType, val replacePhotoId: String? = null) : AppKey
@Serializable data object AnalysisKey : AppKey
@Serializable data class ResultKey(val observationId: String) : AppKey
@Serializable data class ComparisonKey(val observationId: String, val alternativeSpeciesId: String) : AppKey
@Serializable data class SpeciesDetailKey(val speciesId: String, val source: String) : AppKey
@Serializable data class FtObservationKey(val observationId: Long) : AppKey
