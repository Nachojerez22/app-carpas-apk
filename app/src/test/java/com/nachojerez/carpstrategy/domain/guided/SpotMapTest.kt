package com.nachojerez.carpstrategy.domain.guided

import com.nachojerez.carpstrategy.data.userdata.GuidedJson
import com.nachojerez.carpstrategy.domain.derived.Geo
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpotMapTest {
    private val spot = GeoPoint(38.35, -6.70)

    @Test
    fun `rumbo y distancia entre el puesto y la orilla de enfrente`() {
        // 0,005° de latitud al norte ≈ 556 m.
        val north = SpotMap.geometry(spot, GeoPoint(38.355, -6.70))!!
        assertEquals(0, north.facingDeg)
        assertEquals(556.0, north.fetchM, 2.0)
        val east = SpotMap.geometry(spot, GeoPoint(38.35, -6.69))!!
        assertEquals(90, east.facingDeg)
        val southWest = SpotMap.geometry(spot, GeoPoint(38.346, -6.705))!!
        assertTrue(southWest.facingDeg in 220..230, "${southWest.facingDeg}")
        // Un toque casi encima del puesto no es una orilla de enfrente.
        assertNull(SpotMap.geometry(spot, GeoPoint(38.35005, -6.70)))
    }

    @Test
    fun `destino a una distancia y rumbo vuelve al mismo punto`() {
        val end = SpotMap.destination(spot, 225.0, 800.0)
        assertEquals(800.0, Geo.haversineKm(spot, end) * 1000, 1.0)
        assertEquals(225.0, Geo.bearingDeg(spot, end), 0.5)
    }

    @Test
    fun `color del puesto segun el viento respecto al agua`() {
        val facingSouthWest = Spot(1, "Punta", facingDeg = 225, point = spot)
        // Viento del SO (le da de cara), cálido respecto al agua, sostenido y agua a 17 °C: le favorece.
        val warm = MapWind(fromDeg = 225.0, kmh = 14.0, persistence = 0.8, airC = 21.0, waterC = 17.0)
        assertEquals(SpotWindKind.FAVOURED, SpotMap.windKind(facingSouthWest, warm))
        // Mismo viento con el agua ya caliente: lo recibe, pero no suma.
        assertEquals(SpotWindKind.RECEIVES, SpotMap.windKind(facingSouthWest, warm.copy(airC = 29.0, waterC = 25.0)))
        // Viento frío del NE (de espaldas): resguardado y favorable.
        assertEquals(SpotWindKind.FAVOURED, SpotMap.windKind(facingSouthWest, MapWind(45.0, 12.0, airC = 10.0, waterC = 15.0)))
        assertEquals(SpotWindKind.SHELTERED, SpotMap.windKind(facingSouthWest, MapWind(45.0, 12.0, airC = 15.0, waterC = 15.0)))
        assertEquals(SpotWindKind.NEUTRAL, SpotMap.windKind(facingSouthWest.copy(facingDeg = null), warm))
        assertEquals(SpotWindKind.NEUTRAL, SpotMap.windKind(facingSouthWest, null))
    }

    @Test
    fun `el punto del puesto se guarda`() {
        val saved = Spot(1, "Punta", facingDeg = 225, fetchM = 800.0, point = spot)
        assertEquals(spot, GuidedJson.decodeSpots(GuidedJson.encodeSpots(listOf(saved))).single().point)
        assertNull(GuidedJson.decodeSpots(GuidedJson.encodeSpots(listOf(saved.copy(point = null)))).single().point)
    }
}
