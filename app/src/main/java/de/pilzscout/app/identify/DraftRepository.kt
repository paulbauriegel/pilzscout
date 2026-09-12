package de.pilzscout.app.identify

import de.pilzscout.app.location.ApproxLocation
import de.pilzscout.core.model.ViewType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class DraftPhoto(val id: String = UUID.randomUUID().toString(), val file: File, val viewType: ViewType)

data class Draft(
    val photos: List<DraftPhoto> = emptyList(),
    val capturedAt: Long = System.currentTimeMillis(),
    val location: ApproxLocation? = null,
    val includeLocation: Boolean = true,
    val locationRequested: Boolean = false,
    /** Content language ("de" or "en") at the time of identification; used for the stored comparison texts. */
    val language: String = "en",
) {
    val capturedViews: Set<ViewType> get() = photos.map { it.viewType }.toSet()
    fun nextSuggestedView(): ViewType? = ViewType.entries.firstOrNull { it !in capturedViews }
}

/** The observation currently being composed on the Identify screen (in memory; photos are cache files). */
@Singleton
class DraftRepository @Inject constructor() {
    private val _draft = MutableStateFlow(Draft())
    val draft: StateFlow<Draft> = _draft

    fun add(file: File, viewType: ViewType) = _draft.update { d ->
        val photos = d.photos.toMutableList()
        val existing = photos.indexOfFirst { it.viewType == viewType && it.file == file }
        if (existing < 0) photos += DraftPhoto(file = file, viewType = viewType)
        d.copy(photos = photos, capturedAt = if (d.photos.isEmpty()) System.currentTimeMillis() else d.capturedAt)
    }

    fun replace(photoId: String, file: File) = _draft.update { d ->
        d.copy(photos = d.photos.map { if (it.id == photoId) it.copy(file = file) else it })
    }

    fun remove(photoId: String) = _draft.update { d -> d.copy(photos = d.photos.filterNot { it.id == photoId }) }

    fun move(photoId: String, delta: Int) = _draft.update { d ->
        val list = d.photos.toMutableList()
        val i = list.indexOfFirst { it.id == photoId }
        val j = (i + delta).coerceIn(0, list.lastIndex)
        if (i < 0 || i == j) return@update d
        val item = list.removeAt(i)
        list.add(j, item)
        d.copy(photos = list)
    }

    fun setViewType(photoId: String, viewType: ViewType) = _draft.update { d ->
        d.copy(photos = d.photos.map { if (it.id == photoId) it.copy(viewType = viewType) else it })
    }

    fun setLanguage(language: String) = _draft.update { it.copy(language = language) }

    fun setLocation(location: ApproxLocation?) = _draft.update { it.copy(location = location, locationRequested = true) }
    fun setIncludeLocation(include: Boolean) = _draft.update { it.copy(includeLocation = include) }

    fun clear() {
        _draft.value = Draft(language = _draft.value.language)
    }
}
