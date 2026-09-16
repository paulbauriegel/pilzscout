package de.pilzscout.app.identify

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import de.pilzscout.app.ml.ImagePreprocessor
import de.pilzscout.core.model.ViewType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class StoredPhoto(val file: File, val thumb: File, val width: Int, val height: Int)

/** Photos live under filesDir/observations/<observationId>/ and never leave the device. */
@Singleton
class PhotoStore @Inject constructor(@ApplicationContext private val context: Context) {

    val root: File get() = File(context.filesDir, "observations")

    /** Temporary capture target for the camera. */
    fun newCaptureFile(): File {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        return File(dir, "capture-${UUID.randomUUID()}.jpg")
    }

    /**
     * Copies a gallery Uri into the camera cache so it is handled exactly like a capture. [original] is tried
     * first (a MediaStore "require original" Uri that keeps GPS tags); if the provider rejects it the plain
     * [uri] is copied instead so the import itself never fails because of metadata.
     */
    suspend fun importFromUri(uri: Uri, original: Uri = uri): File = withContext(Dispatchers.IO) {
        val target = newCaptureFile()
        val copied = runCatching { copy(original, target) }.getOrDefault(false) || (original != uri && copy(uri, target))
        if (!copied) error("Could not open $uri")
        target
    }

    private fun copy(source: Uri, target: File): Boolean =
        context.contentResolver.openInputStream(source)?.use { input -> target.outputStream().use { input.copyTo(it) }; true } ?: false

    /**
     * Moves a draft photo into the observation folder as <position>-<view>.jpg (re-encoded, longest edge
     * <= 2048 px) and writes a 320 px thumbnail next to it.
     */
    suspend fun store(observationId: String, source: File, viewType: ViewType, position: Int): StoredPhoto = withContext(Dispatchers.IO) {
        val dir = File(root, observationId).apply { mkdirs() }
        val bitmap = ImagePreprocessor.decodeOriented(source, minShortEdge = 1024)
        val scaled = fitLongest(bitmap, MAX_EDGE)
        val file = File(dir, "${position + 1}-${viewType.exportName}.jpg")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val thumbBitmap = fitLongest(scaled, THUMB_EDGE)
        val thumb = File(dir, "${position + 1}-${viewType.exportName}.thumb.jpg")
        thumb.outputStream().use { thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        val result = StoredPhoto(file, thumb, scaled.width, scaled.height)
        if (thumbBitmap !== scaled) thumbBitmap.recycle()
        if (scaled !== bitmap) bitmap.recycle()
        scaled.recycle()
        result
    }

    fun deleteObservation(observationId: String) {
        File(root, observationId).deleteRecursively()
    }

    fun clearCaptureCache() {
        File(context.cacheDir, "camera").deleteRecursively()
    }

    private fun fitLongest(src: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxEdge) return src
        val scale = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(src, (src.width * scale).roundToInt(), (src.height * scale).roundToInt(), true)
    }

    companion object {
        const val MAX_EDGE = 2048
        const val THUMB_EDGE = 320
    }
}
