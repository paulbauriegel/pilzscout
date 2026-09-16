package de.pilzscout.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.resume

/** Exact position of a find as reported by the device, stored unrounded so the history map places it correctly. */
data class GeoPoint(val lat: Double, val lon: Double)

/**
 * Device location for a find. Asks for precise location; when the user only grants approximate
 * location the GPS provider is unavailable and the fused (API 31+) or network provider answers instead,
 * with a last-known-location fallback.
 */
class DeviceLocationProvider @Inject constructor(@ApplicationContext private val context: Context) {

    /** Either precise or approximate location was granted. */
    fun hasPermission(): Boolean = hasFinePermission() || hasCoarsePermission()

    fun hasCoarsePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasFinePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun current(timeoutMs: Long = 8_000): GeoPoint? = withContext(Dispatchers.Main) {
        if (!hasPermission()) return@withContext null
        val manager = context.getSystemService(LocationManager::class.java) ?: return@withContext null
        val providers = buildList {
            if (hasFinePermission()) add(LocationManager.GPS_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        // Ask every usable provider at once and keep the most accurate fix that arrives; a single slow
        // provider must not eat the timeout, but a quick coarse network fix should not beat a GPS fix that
        // lands moments later.
        val fresh = withTimeoutOrNull(timeoutMs) {
            coroutineScope {
                val channel = Channel<Location?>(providers.size)
                providers.forEach { provider ->
                    launch {
                        val loc = runCatching { currentFromProvider(manager, provider) }.onFailure { Log.w(TAG, "provider $provider failed: $it") }.getOrNull()
                        channel.send(loc)
                    }
                }
                var best: Location? = null
                repeat(providers.size) {
                    val loc = channel.receive() ?: return@repeat
                    if (best == null || loc.accuracy < best!!.accuracy) best = loc
                    if (loc.accuracy <= GOOD_ENOUGH_METERS) return@coroutineScope loc.also { coroutineContext[Job]?.children?.forEach { c -> c.cancel() } }
                }
                best
            }
        }
        val location = fresh ?: providers.firstNotNullOfOrNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        location?.let { GeoPoint(it.latitude, it.longitude) }
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

    companion object {
        private const val TAG = "DeviceLocation"
        /** A fix at least this accurate ends the wait early; anything coarser waits for a better provider until the timeout. */
        private const val GOOD_ENOUGH_METERS = 25f
    }
}
