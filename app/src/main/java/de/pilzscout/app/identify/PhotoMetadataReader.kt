package de.pilzscout.app.identify

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import de.pilzscout.app.location.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** What an imported photo says about itself: where and when it was taken, each null when the tag is missing. */
data class PhotoMetadata(val location: GeoPoint? = null, val takenAt: Long? = null)

/**
 * EXIF position and capture time of an imported photo. Since Android 10 the media provider redacts location
 * tags unless the app holds ACCESS_MEDIA_LOCATION and asks for the original file, so imports copy from
 * [originalUri] and then read the tags from that copy with [read].
 */
class PhotoMetadataReader @Inject constructor(@ApplicationContext private val context: Context) {

    /** Below API 29 nothing is redacted and there is no permission to ask for. */
    fun needsPermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasMediaLocationPermission()

    fun hasMediaLocationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Uri to copy from so GPS tags survive; the plain [uri] when the provider does not support originals. */
    fun originalUri(uri: Uri): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !hasMediaLocationPermission()) return uri
        return runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)
    }

    /** Position and capture time from the file's EXIF tags; unreadable files yield empty metadata. */
    suspend fun read(file: File): PhotoMetadata = withContext(Dispatchers.IO) {
        val exif = runCatching { ExifInterface(file) }.getOrNull() ?: return@withContext PhotoMetadata()
        PhotoMetadata(location = readLocation(exif), takenAt = readTakenAt(exif))
    }

    private fun readLocation(exif: ExifInterface): GeoPoint? {
        val latLong = runCatching { exif.latLong }.getOrNull() ?: return null
        val (lat, lon) = latLong[0] to latLong[1]
        return GeoPoint(lat, lon).takeIf { isPlausible(lat, lon) }
    }

    private fun readTakenAt(exif: ExifInterface): Long? {
        val original = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
        val dateTime = original ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
        val offset = if (original != null) exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL) else exif.getAttribute(ExifInterface.TAG_OFFSET_TIME)
        return parseExifDateTime(dateTime, offset, ZoneId.systemDefault())
    }

    private fun isPlausible(lat: Double, lon: Double): Boolean =
        lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)

    companion object {
        private val EXIF_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

        /**
         * Epoch millis for an EXIF "yyyy:MM:dd HH:mm:ss" wall-clock time. Cameras write local time without a
         * zone; the OffsetTime tag ("+02:00") fixes it when present, otherwise the phone's [fallbackZone] is
         * assumed, which is right for photos taken where the phone lives and at worst a few hours off.
         * Unset placeholders like "0000:00:00 00:00:00" and unparseable values yield null.
         */
        fun parseExifDateTime(dateTime: String?, offset: String?, fallbackZone: ZoneId): Long? {
            val text = dateTime?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("0000") } ?: return null
            val local = runCatching { LocalDateTime.parse(text, EXIF_DATE_TIME) }.getOrNull() ?: return null
            val zone: ZoneId = offset?.trim()?.let { runCatching { ZoneOffset.of(it) }.getOrNull() } ?: fallbackZone
            return local.atZone(zone).toInstant().toEpochMilli()
        }
    }
}
