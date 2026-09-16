package de.pilzscout.app.identify

import android.net.Uri
import de.pilzscout.app.ml.ViewTypeDetector
import de.pilzscout.core.model.ViewType
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single path by which photos enter the draft, whether captured with the camera or picked from
 * the gallery. A view the user picked is stored as is. Untyped photos are offered to the
 * [ViewTypeDetector], whose guess never overrides a view the user set in the meantime.
 */
@Singleton
class PhotoIntake @Inject constructor(
    private val drafts: DraftRepository,
    private val photoStore: PhotoStore,
    private val detector: ViewTypeDetector,
) {
    suspend fun addCapture(file: File, view: ViewType? = null) {
        val id = drafts.add(file, view ?: ViewType.OTHER)
        if (view == null) tagIfUntyped(id, file)
    }

    /** Retake: the camera's view choice replaces the old one, including clearing it. */
    suspend fun replaceCapture(photoId: String, file: File, view: ViewType? = null) {
        drafts.replace(photoId, file)
        drafts.setViewType(photoId, view ?: ViewType.OTHER)
        if (view == null) tagIfUntyped(photoId, file)
    }

    suspend fun importFromGallery(uris: List<Uri>) {
        for (uri in uris) {
            val file = runCatching { photoStore.importFromUri(uri) }.getOrNull() ?: continue
            addCapture(file)
        }
    }

    /** Gallery replacement keeps the photo's view. */
    suspend fun replaceFromGallery(photoId: String, uri: Uri) {
        val file = runCatching { photoStore.importFromUri(uri) }.getOrNull() ?: return
        drafts.replace(photoId, file)
        tagIfUntyped(photoId, file)
    }

    private suspend fun tagIfUntyped(photoId: String, file: File) {
        val guess = runCatching { detector.detect(file) }.getOrNull() ?: return
        val current = drafts.draft.value.photos.firstOrNull { it.id == photoId } ?: return
        if (current.viewType == ViewType.OTHER) drafts.setViewType(photoId, guess.viewType)
    }
}
