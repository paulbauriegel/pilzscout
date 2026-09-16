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
import javax.inject.Inject

/**
 * GPS position embedded in an imported photo. Since Android 10 the media provider redacts location tags
 * unless the app holds ACCESS_MEDIA_LOCATION and asks for the original file, so imports copy from
 * [originalUri] and then read the tags from that copy with [read].
 */
class PhotoLocationReader @Inject constructor(@ApplicationContext private val context: Context) {

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

    /** Position from the file's EXIF GPS tags, or null when absent, unreadable or obviously bogus. */
    suspend fun read(file: File): GeoPoint? = withContext(Dispatchers.IO) {
        val latLong = runCatching { ExifInterface(file).latLong }.getOrNull() ?: return@withContext null
        val (lat, lon) = latLong[0] to latLong[1]
        GeoPoint(lat, lon).takeIf { isPlausible(lat, lon) }
    }

    private fun isPlausible(lat: Double, lon: Double): Boolean =
        lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)
}
