package de.pilzscout.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** Approximate position, rounded to two decimals (about 1 km) so exact spots are never stored. */
data class ApproxLocation(val lat: Double, val lon: Double)

class CoarseLocationProvider @Inject constructor(@ApplicationContext private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun current(timeoutMs: Long = 8_000): ApproxLocation? = withContext(Dispatchers.Main) {
        if (!hasPermission()) return@withContext null
        val manager = context.getSystemService(LocationManager::class.java) ?: return@withContext null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        val fresh = withTimeoutOrNull(timeoutMs) {
            providers.firstNotNullOfOrNull { provider -> currentFromProvider(manager, provider) }
        }
        val location = fresh ?: providers.firstNotNullOfOrNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        location?.let { ApproxLocation(round(it.latitude), round(it.longitude)) }
    }

    @Suppress("MissingPermission")
    private suspend fun currentFromProvider(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, context.mainExecutor) { loc -> if (cont.isActive) cont.resume(loc) }
            } else {
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, { loc -> if (cont.isActive) cont.resume(loc) }, context.mainLooper)
            }
        }

    private fun round(v: Double) = (v * 100).roundToInt() / 100.0
}
