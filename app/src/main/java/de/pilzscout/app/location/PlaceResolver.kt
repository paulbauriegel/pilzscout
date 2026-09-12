package de.pilzscout.app.location

import de.pilzscout.app.data.species.SpeciesDatabaseHolder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class PlaceName(val name: String, val admin1: String?, val distanceKm: Double)

/**
 * Nearest populated place for an approximate coordinate, entirely offline from the bundled GeoNames table.
 * Larger places win ties within a few kilometres so "near Freiburg" beats a hamlet next door.
 */
@Singleton
class PlaceResolver @Inject constructor(private val holder: SpeciesDatabaseHolder) {

    suspend fun nearest(lat: Double, lon: Double): PlaceName? {
        val db = holder.db.value ?: return null
        val dLat = 0.35 // ~40 km
        val dLon = dLat / cos(Math.toRadians(lat)).coerceAtLeast(0.2)
        val candidates = runCatching { db.placeDao().inBox(lat - dLat, lat + dLat, lon - dLon, lon + dLon) }.getOrDefault(emptyList())
        if (candidates.isEmpty()) return null
        val kmPerDegLat = 111.2
        val kmPerDegLon = 111.2 * cos(Math.toRadians(lat))
        val scored = candidates.map { p ->
            val d = sqrt(((p.lat - lat) * kmPerDegLat).let { it * it } + ((p.lon - lon) * kmPerDegLon).let { it * it })
            // score: distance, discounted for population so towns outrank hamlets at similar distance
            val weight = when {
                p.population >= 100_000 -> 0.55
                p.population >= 20_000 -> 0.7
                p.population >= 5_000 -> 0.85
                else -> 1.0
            }
            Triple(p, d, d * weight)
        }
        val best = scored.minByOrNull { it.third } ?: return null
        val (place, distance) = best.first to best.second
        return PlaceName(place.name, place.admin1, (distance * 10).roundToInt() / 10.0)
    }
}

@Suppress("unused")
private fun unusedAbs(v: Double) = abs(v)
