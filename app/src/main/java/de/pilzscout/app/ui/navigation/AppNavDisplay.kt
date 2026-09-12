package de.pilzscout.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import de.pilzscout.app.R
import androidx.hilt.navigation.compose.hiltViewModel
import de.pilzscout.app.ui.analysis.AnalysisScreen
import de.pilzscout.app.ui.browse.BrowseScreen
import de.pilzscout.app.ui.browse.BrowseSource
import de.pilzscout.app.ui.species.FtObservationScreen
import de.pilzscout.app.ui.species.SpeciesDetailScreen
import de.pilzscout.app.ui.camera.CameraScreen
import de.pilzscout.app.ui.comparison.ComparisonScreen
import de.pilzscout.app.ui.result.ResultScreen
import de.pilzscout.app.ui.history.HistoryScreen
import de.pilzscout.app.ui.identify.IdentifyScreen
import de.pilzscout.app.ui.settings.SettingsScreen

private data class Tab(val key: TopLevelKey, val label: Int, val selected: ImageVector, val unselected: ImageVector)

private val tabs = listOf(
    Tab(IdentifyKey, R.string.nav_identify, Icons.Filled.PhotoCamera, Icons.Outlined.PhotoCamera),
    Tab(BrowseKey, R.string.nav_browse, Icons.Filled.Search, Icons.Outlined.Search),
    Tab(HistoryKey, R.string.nav_history, Icons.Filled.History, Icons.Outlined.History),
)

@Composable
fun AppNavDisplay() {
    val nav = remember { TopLevelBackStack(IdentifyKey) }
    val drafts: NavDraftAccess = hiltViewModel()
    val current = nav.backStack.lastOrNull()
    val showBottomBar = current is TopLevelKey

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = nav.topLevelKey == tab.key
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.switchTo(tab.key) },
                            icon = { Icon(if (selected) tab.selected else tab.unselected, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = nav.backStack,
            modifier = Modifier.padding(padding),
            onBack = { nav.pop() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<IdentifyKey> {
                    IdentifyScreen(
                        onOpenSettings = { nav.push(SettingsKey) },
                        onOpenCamera = { view, replaceId -> nav.push(CameraKey(view, replaceId)) },
                        onIdentify = { nav.push(AnalysisKey) },
                    )
                }
                entry<BrowseKey> {
                    BrowseScreen(
                        onOpenSettings = { nav.push(SettingsKey) },
                        onOpenSpecies = { id, source -> nav.push(SpeciesDetailKey(id, source.name)) },
                        onOpenObservation = { id -> nav.push(FtObservationKey(id)) },
                    )
                }
                entry<SpeciesDetailKey> { key ->
                    SpeciesDetailScreen(
                        speciesId = key.speciesId,
                        initialSource = runCatching { BrowseSource.valueOf(key.source) }.getOrDefault(BrowseSource.FUNGITASTIC),
                        onBack = { nav.pop() },
                        onOpenObservation = { id -> nav.push(FtObservationKey(id)) },
                    )
                }
                entry<FtObservationKey> { key ->
                    FtObservationScreen(observationId = key.observationId, onBack = { nav.pop() }, onOpenSpecies = { id -> nav.push(SpeciesDetailKey(id, BrowseSource.FUNGITASTIC.name)) })
                }
                entry<HistoryKey> { HistoryScreen(onOpenSettings = { nav.push(SettingsKey) }, onOpenObservation = { id -> nav.push(ResultKey(id)) }) }
                entry<SettingsKey> { SettingsScreen(onBack = { nav.pop() }) }
                entry<CameraKey> { key ->
                    val captureFile = remember(key) { drafts.photoStore.newCaptureFile() }
                    CameraScreen(
                        viewType = key.viewType,
                        captureFile = captureFile,
                        onCaptured = { file ->
                            if (key.replacePhotoId != null) drafts.drafts.replace(key.replacePhotoId, file) else drafts.drafts.add(file, key.viewType)
                            nav.pop()
                        },
                        onBack = { nav.pop() },
                    )
                }
                entry<AnalysisKey> {
                    AnalysisScreen(
                        onDone = { id -> nav.pop(); nav.push(ResultKey(id)) },
                        onBack = { nav.pop() },
                    )
                }
                entry<ComparisonKey> { key ->
                    ComparisonScreen(observationId = key.observationId, alternativeId = key.alternativeSpeciesId, onBack = { nav.pop() })
                }
                entry<ResultKey> { key ->
                    ResultScreen(
                        observationId = key.observationId,
                        onBack = { nav.pop() },
                        onCompare = { alt -> nav.push(ComparisonKey(key.observationId, alt)) },
                        onOpenSpecies = { id -> nav.push(SpeciesDetailKey(id, BrowseSource.WIKIPEDIA.name)) },
                        onAddPhoto = { view -> nav.popToRoot(); nav.push(CameraKey(view)) },
                        onNewObservation = { drafts.drafts.clear(); nav.popToRoot() },
                    )
                }
            },
        )
    }
}

/** Tiny holder so navigation callbacks can reach the draft and photo store without a screen-level ViewModel. */
@dagger.hilt.android.lifecycle.HiltViewModel
class NavDraftAccess @javax.inject.Inject constructor(
    val drafts: de.pilzscout.app.identify.DraftRepository,
    val photoStore: de.pilzscout.app.identify.PhotoStore,
) : androidx.lifecycle.ViewModel()
