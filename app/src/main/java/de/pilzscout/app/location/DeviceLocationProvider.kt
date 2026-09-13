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
import kotlin.math.roundToInt

/** Approximate position, rounded to two decimals (about 1 km) so exact spots are never stored. */
data class ApproxLocation(val lat: Double, val lon: Double)

/**
 * Coarse location only. With ACCESS_COARSE_LOCATION the GPS provider is not accessible, so the
 * fused provider (API 31+) or the network provider is used, with a last-known-location fallback.
 */
class CoarseLocationProvider @Inject constructor(@ApplicationContext private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasFinePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun current(timeoutMs: Long = 8_000): ApproxLocation? = withContext(Dispatchers.Main) {
        if (!hasPermission()) return@withContext null
        val manager = context.getSystemService(LocationManager::class.java) ?: return@withContext null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (hasFinePermission()) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        // Ask every usable provider at once and take the first fix; a single slow provider must not eat the timeout.
        val fresh = withTimeoutOrNull(timeoutMs) {
            coroutineScope {
                val channel = Channel<Location?>(providers.size)
                providers.forEach { provider ->
                    launch {
                        val loc = runCatching { currentFromProvider(manager, provider) }.onFailure { Log.w(TAG, "provider $provider failed: $it") }.getOrNull()
                        channel.send(loc)
                    }
                }
                var result: Location? = null
                repeat(providers.size) { if (result == null) result = channel.receive() }
                coroutineContext[Job]?.children?.forEach { it.cancel() }
                result
            }
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

    companion object {
        private const val TAG = "CoarseLocation"
    }
}
