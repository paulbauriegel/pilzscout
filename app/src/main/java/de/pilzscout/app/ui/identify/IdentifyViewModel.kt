package de.pilzscout.app.ui.identify

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.pilzscout.app.identify.Draft
import de.pilzscout.app.identify.DraftRepository
import de.pilzscout.app.identify.PhotoIntake
import de.pilzscout.app.identify.PhotoMetadataReader
import de.pilzscout.app.location.DeviceLocationProvider
import de.pilzscout.app.location.PlaceResolver
import de.pilzscout.app.ml.ClassifierProvider
import de.pilzscout.app.ml.ClassifierState
import de.pilzscout.core.model.ViewType
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IdentifyViewModel @Inject constructor(
    private val drafts: DraftRepository,
    private val intake: PhotoIntake,
    private val metadataReader: PhotoMetadataReader,
    private val location: DeviceLocationProvider,
    private val places: PlaceResolver,
    classifierProvider: ClassifierProvider,
) : ViewModel() {

    val draft: StateFlow<Draft> = drafts.draft
    val classifierState: StateFlow<ClassifierState> = classifierProvider.state

    /** Precise location granted; otherwise the screen asks again so finds get exact coordinates. */
    fun hasPreciseLocationPermission() = location.hasFinePermission()

    fun fetchLocation() {
        viewModelScope.launch {
            val loc = location.current()
            val place = loc?.let { places.nearest(it.lat, it.lon) }
            drafts.setLocation(loc, place?.name)
        }
    }

    /** Media location access is asked for before the gallery opens so imported photos keep their GPS tags. */
    fun needsMediaLocationPermission() = metadataReader.needsPermission()

    fun removeLocation() = drafts.setIncludeLocation(false)
    fun includeLocation() {
        drafts.setIncludeLocation(true)
        if (draft.value.location == null) fetchLocation()
    }

    fun importFromGallery(uris: List<Uri>) {
        viewModelScope.launch { intake.importFromGallery(uris) }
    }

    fun replaceFromGallery(photoId: String, uri: Uri) {
        viewModelScope.launch { intake.replaceFromGallery(photoId, uri) }
    }

    fun remove(photoId: String) = drafts.remove(photoId)
    fun setViewType(photoId: String, view: ViewType) = drafts.setViewType(photoId, view)
    fun setLanguage(language: String) = drafts.setLanguage(language)
    fun clear() = drafts.clear()
}
