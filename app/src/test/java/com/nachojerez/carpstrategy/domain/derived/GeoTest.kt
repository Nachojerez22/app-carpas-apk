package com.nachojerez.carpstrategy.domain.derived

import com.nachojerez.carpstrategy.domain.model.GeoPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GeoTest {
    private val madrid = GeoPoint(40.4168, -3.7038)
    private val barcelona = GeoPoint(41.3874, 2.1686)

    @Test
    fun `distancia Madrid-Barcelona por haversine`() {
        assertEquals(505.0, Geo.haversineKm(madrid, barcelona), 2.0)
    }

    @Test
    fun `distancia a si mismo es cero y es simetrica`() {
        assertEquals(0.0, Geo.haversineKm(madrid, madrid), 1e-9)
        assertEquals(Geo.haversineKm(madrid, barcelona), Geo.haversineKm(barcelona, madrid), 1e-9)
    }

    @Test
    fun `un minuto de latitud son unos 1,853 km`() {
        val a = GeoPoint(38.0, -6.7)
        val b = GeoPoint(38.0 + 1.0 / 60.0, -6.7)
        assertEquals(1.853, Geo.haversineKm(a, b), 0.005)
    }

    @Test
    fun `nearest ordena, filtra por distancia y limita`() {
        val origin = GeoPoint(38.35, -6.70)
        val points = listOf(
            "lejos" to GeoPoint(40.4, -3.7),
            "cerca" to GeoPoint(38.32, -6.77),
            "medio" to GeoPoint(38.88, -6.83),
        )
        val result = Geo.nearest(origin, points, maxDistanceKm = 100.0, limit = 5) { it.second }
        assertEquals(listOf("cerca", "medio"), result.map { it.first.first })
        assertEquals(1, Geo.nearest(origin, points, limit = 1) { it.second }.size)
    }
}
