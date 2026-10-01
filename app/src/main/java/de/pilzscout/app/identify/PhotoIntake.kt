package de.pilzscout.app.identify

import android.net.Uri
import de.pilzscout.app.location.PlaceResolver
import de.pilzscout.app.ml.ViewTypeDetector
import de.pilzscout.core.model.ViewType
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single path by which photos enter the draft, whether captured with the camera or picked from
 * the gallery. A view the user picked is stored as is. Untyped photos are offered to the
 * [ViewTypeDetector], whose guess never overrides a view the user set in the meantime. Gallery imports
 * also bring their own EXIF position and capture date into the draft, so a find photographed last week
 * in the woods is not placed at today's date and the phone's current position.
 */
@Singleton
class PhotoIntake @Inject constructor(
    private val drafts: DraftRepository,
    private val photoStore: PhotoStore,
    private val detector: ViewTypeDetector,
    private val metadataReader: PhotoMetadataReader,
    private val places: PlaceResolver,
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
            val file = importFile(uri) ?: continue
            // Metadata is read before the photo joins the draft, so the draft never shows a photo without
            // its position and the screen never sees a gap in which to ask the device for a fix instead.
            val (metadata, placeName) = readMetadata(file)
            val useDate = drafts.draft.value.photos.isEmpty()
            val id = drafts.add(file)
            drafts.adoptPhotoMetadata(metadata, placeName, useDate)
            tagIfUntyped(id, file)
        }
    }

    /** Gallery replacement keeps the photo's view; its date is adopted only when it becomes the sole photo. */
    suspend fun replaceFromGallery(photoId: String, uri: Uri) {
        val file = importFile(uri) ?: return
        val (metadata, placeName) = readMetadata(file)
        drafts.replace(photoId, file)
        drafts.adoptPhotoMetadata(metadata, placeName, useDate = drafts.draft.value.photos.size == 1)
        tagIfUntyped(photoId, file)
    }

    private suspend fun importFile(uri: Uri): File? =
        runCatching { photoStore.importFromUri(uri, original = metadataReader.originalUri(uri)) }.getOrNull()

    private suspend fun readMetadata(file: File): Pair<PhotoMetadata, String?> {
        val metadata = metadataReader.read(file)
        val place = metadata.location?.let { runCatching { places.nearest(it.lat, it.lon) }.getOrNull() }
        return metadata to place?.name
    }

    private suspend fun tagIfUntyped(photoId: String, file: File) {
        val guess = runCatching { detector.detect(file) }.getOrNull() ?: return
        val current = drafts.draft.value.photos.firstOrNull { it.id == photoId } ?: return
        if (current.viewType == ViewType.OTHER) drafts.setViewType(photoId, guess.viewType)
    }
}
