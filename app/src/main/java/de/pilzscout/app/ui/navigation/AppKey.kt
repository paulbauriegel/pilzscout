package de.pilzscout.app.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** All navigation destinations. Top-level keys are the three bottom-navigation tabs. */
sealed interface AppKey : NavKey

sealed interface TopLevelKey : AppKey

@Serializable data object IdentifyKey : TopLevelKey
@Serializable data object BrowseKey : TopLevelKey
@Serializable data object HistoryKey : TopLevelKey

@Serializable data object SettingsKey : AppKey
