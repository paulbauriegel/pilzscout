package de.pilzscout.app.identify

import de.pilzscout.app.location.GeoPoint
import de.pilzscout.core.model.ViewType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class DraftPhoto(val id: String = UUID.randomUUID().toString(), val file: File, val viewType: ViewType = ViewType.OTHER)

data class Draft(
    val photos: List<DraftPhoto> = emptyList(),
    val capturedAt: Long = System.currentTimeMillis(),
    val location: GeoPoint? = null,
    val placeName: String? = null,
    val includeLocation: Boolean = true,
    val locationRequested: Boolean = false,
    /** Content language ("de" or "en") at the time of identification; used for the stored comparison texts. */
    val language: String = "en",
) {
    val capturedViews: Set<ViewType> get() = photos.map { it.viewType }.toSet()
}

/** The observation currently being composed on the Identify screen (in memory; photos are cache files). */
@Singleton
class DraftRepository @Inject constructor() {
    private val _draft = MutableStateFlow(Draft())
    val draft: StateFlow<Draft> = _draft

    /** Appends [file] (untyped unless a detector tags it later) and returns the photo's id; a file already in the draft is not added twice. */
    fun add(file: File, viewType: ViewType = ViewType.OTHER): String {
        var id = ""
        _draft.update { d ->
            val existing = d.photos.firstOrNull { it.file == file }
            if (existing != null) {
                id = existing.id
                return@update d
            }
            val photo = DraftPhoto(file = file, viewType = viewType)
            id = photo.id
            d.copy(photos = d.photos + photo, capturedAt = if (d.photos.isEmpty()) System.currentTimeMillis() else d.capturedAt)
        }
        return id
    }

    fun replace(photoId: String, file: File) = _draft.update { d ->
        d.copy(photos = d.photos.map { if (it.id == photoId) it.copy(file = file) else it })
    }

    fun remove(photoId: String) = _draft.update { d -> d.copy(photos = d.photos.filterNot { it.id == photoId }) }

    fun setViewType(photoId: String, viewType: ViewType) = _draft.update { d ->
        d.copy(photos = d.photos.map { if (it.id == photoId) it.copy(viewType = viewType) else it })
    }

    fun setLanguage(language: String) = _draft.update { it.copy(language = language) }

    /**
     * Records the device fix. A position an imported photo already carried wins, because that is where the
     * mushroom was found, whereas the device fix only says where the phone is now.
     */
    fun setLocation(location: GeoPoint?, placeName: String? = null) = _draft.update { d ->
        if (d.location != null) d.copy(locationRequested = true)
        else d.copy(location = location, placeName = placeName, locationRequested = true)
    }

    /**
     * Fills the draft from an imported photo's EXIF: the capture date when [useDate] (the photo is the
     * draft's first or only one), and the position when the draft has none yet. Marking the location as
     * requested keeps the screen from replacing it with the device's current fix.
     */
    fun adoptPhotoMetadata(metadata: PhotoMetadata, placeName: String?, useDate: Boolean) = _draft.update { d ->
        var next = d
        if (useDate && metadata.takenAt != null) next = next.copy(capturedAt = metadata.takenAt)
        if (metadata.location != null && next.location == null) {
            next = next.copy(location = metadata.location, placeName = placeName, locationRequested = true)
        }
        next
    }
    fun setIncludeLocation(include: Boolean) = _draft.update { it.copy(includeLocation = include) }

    fun clear() {
        _draft.value = Draft(language = _draft.value.language)
    }
}
