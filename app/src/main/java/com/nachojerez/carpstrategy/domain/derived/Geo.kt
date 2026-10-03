package com.nachojerez.carpstrategy.domain.derived

import com.nachojerez.carpstrategy.domain.model.GeoPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    /** Radio medio terrestre (IUGG), en km. */
    const val EARTH_RADIUS_KM = 6371.0088

    /** Distancia ortodrómica entre dos puntos (fórmula del haversine), en km. */
    fun haversineKm(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Los [limit] elementos más cercanos a [origin] dentro de [maxDistanceKm], ordenados por distancia. */
    fun <T> nearest(
        origin: GeoPoint,
        items: Iterable<T>,
        maxDistanceKm: Double = Double.MAX_VALUE,
        limit: Int = Int.MAX_VALUE,
        pointOf: (T) -> GeoPoint,
    ): List<Pair<T, Double>> = items
        .map { it to haversineKm(origin, pointOf(it)) }
        .filter { it.second <= maxDistanceKm }
        .sortedBy { it.second }
        .take(limit)
}
