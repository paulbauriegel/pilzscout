package de.pilzscout.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import de.pilzscout.app.R
import de.pilzscout.app.identify.guidanceFor
import de.pilzscout.app.ui.analysis.AnalysisScreen
import de.pilzscout.app.ui.browse.BrowseScreen
import de.pilzscout.app.ui.browse.BrowseSource
import de.pilzscout.app.ui.camera.CameraScreen
import de.pilzscout.app.ui.comparison.ComparisonScreen
import de.pilzscout.app.ui.components.rememberForestLayers
import de.pilzscout.app.ui.history.HistoryScreen
import de.pilzscout.app.ui.home.HomeScreen
import de.pilzscout.app.ui.identify.IdentifyScreen
import de.pilzscout.app.ui.identify.titleRes
import de.pilzscout.app.ui.result.ResultScreen
import de.pilzscout.app.ui.settings.SettingsScreen
import de.pilzscout.app.ui.species.SpeciesDetailScreen
import de.pilzscout.core.model.ViewType
import kotlinx.coroutines.launch

@Composable
fun AppNavDisplay() {
    val nav = remember { AppBackStack() }
    val drafts: NavDraftAccess = hiltViewModel()
    // Decoded once per theme up here so the bitmaps survive detail screens being pushed over the home entry.
    val layers = rememberForestLayers()

    NavDisplay(
        backStack = nav.backStack,
        modifier = Modifier.fillMaxSize(),
        onBack = { nav.pop() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<HomeKey> {
                HomeScreen(
                    pendingPage = nav.pendingHomePage,
                    onPendingPageConsumed = { nav.pendingHomePage = null },
                    layers = layers,
                    identify = {
                        IdentifyScreen(
                            onOpenSettings = { nav.push(SettingsKey) },
                            onOpenCamera = { replaceId -> nav.push(CameraKey(replacePhotoId = replaceId)) },
                            onIdentify = { nav.push(AnalysisKey) },
                        )
                    },
                    browse = {
                        BrowseScreen(
                            onOpenSettings = { nav.push(SettingsKey) },
                            onOpenSpecies = { id -> nav.push(SpeciesDetailKey(id, BrowseSource.FUNGITASTIC.name)) },
                        )
                    },
                    history = {
                        HistoryScreen(onOpenSettings = { nav.push(SettingsKey) }, onOpenObservation = { id -> nav.push(ResultKey(id)) })
                    },
                )
            }
            entry<SpeciesDetailKey> { key ->
                SpeciesDetailScreen(
                    speciesId = key.speciesId,
                    initialSource = runCatching { BrowseSource.valueOf(key.source) }.getOrDefault(BrowseSource.FUNGITASTIC),
                    onBack = { nav.pop() },
                )
            }
            entry<SettingsKey> { SettingsScreen(onBack = { nav.pop() }) }
            entry<CameraKey> { key ->
                val captureFile = remember(key) { drafts.photoStore.newCaptureFile() }
                val draft by drafts.drafts.draft.collectAsStateWithLifecycle()
                val retaken = key.replacePhotoId?.let { id -> draft.photos.firstOrNull { it.id == id } }
                CameraScreen(
                    hint = if (key.replacePhotoId != null) stringResource(R.string.camera_hint_retake) else stringResource(guidanceFor(draft.photos.size).titleRes),
                    initialView = key.recommendedView ?: retaken?.viewType?.takeIf { it != ViewType.OTHER },
                    captureFile = captureFile,
                    onCaptured = { file, view ->
                        drafts.onCaptured(file, key.replacePhotoId, view)
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
                    onAddPhoto = { view -> nav.popToRoot(); nav.push(CameraKey(recommendedView = view)) },
                    onNewObservation = { drafts.drafts.clear(); nav.popToRoot() },
                )
            }
        },
    )
}

/** Tiny holder so navigation callbacks can reach the draft and photo store without a screen-level ViewModel. */
@dagger.hilt.android.lifecycle.HiltViewModel
class NavDraftAccess @javax.inject.Inject constructor(
    val drafts: de.pilzscout.app.identify.DraftRepository,
    val photoStore: de.pilzscout.app.identify.PhotoStore,
    private val intake: de.pilzscout.app.identify.PhotoIntake,
) : androidx.lifecycle.ViewModel() {
    /** Adds a fresh capture to the draft, or swaps the file of [replacePhotoId] when retaking; [view] is the optional choice made in the camera. */
    fun onCaptured(file: java.io.File, replacePhotoId: String?, view: ViewType?) {
        viewModelScope.launch {
            if (replacePhotoId != null) intake.replaceCapture(replacePhotoId, file, view) else intake.addCapture(file, view)
        }
    }
}
