package de.pilzscout.app.ui.identify

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.pilzscout.app.identify.Draft
import de.pilzscout.app.identify.DraftRepository
import de.pilzscout.app.identify.PhotoStore
import de.pilzscout.app.location.CoarseLocationProvider
import de.pilzscout.app.ml.ClassifierProvider
import de.pilzscout.app.ml.ClassifierState
import de.pilzscout.core.model.ViewType
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IdentifyViewModel @Inject constructor(
    private val drafts: DraftRepository,
    private val photoStore: PhotoStore,
    private val location: CoarseLocationProvider,
    classifierProvider: ClassifierProvider,
) : ViewModel() {

    val draft: StateFlow<Draft> = drafts.draft
    val classifierState: StateFlow<ClassifierState> = classifierProvider.state

    fun hasLocationPermission() = location.hasPermission()

    fun fetchLocation() {
        viewModelScope.launch { drafts.setLocation(location.current()) }
    }

    fun removeLocation() = drafts.setIncludeLocation(false)
    fun includeLocation() {
        drafts.setIncludeLocation(true)
        if (draft.value.location == null) fetchLocation()
    }

    fun importFromGallery(uris: List<Uri>) {
        viewModelScope.launch {
            for (uri in uris) {
                val file = runCatching { photoStore.importFromUri(uri) }.getOrNull() ?: continue
                drafts.add(file, draft.value.nextSuggestedView() ?: ViewType.HABITAT)
            }
        }
    }

    fun remove(photoId: String) = drafts.remove(photoId)
    fun move(photoId: String, delta: Int) = drafts.move(photoId, delta)
    fun setViewType(photoId: String, viewType: ViewType) = drafts.setViewType(photoId, viewType)
    fun setLanguage(language: String) = drafts.setLanguage(language)
    fun clear() = drafts.clear()
}
