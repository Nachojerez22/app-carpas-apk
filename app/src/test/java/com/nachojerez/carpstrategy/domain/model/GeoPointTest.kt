package com.nachojerez.carpstrategy.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class GeoPointTest {

    @Test
    fun `la ubicacion por defecto es Brovales - Jerez de los Caballeros`() {
        val point = DefaultLocation.value.point
        assertEquals(38.37, point.latitude, 1e-9)
        assertEquals(-6.88, point.longitude, 1e-9)
    }

    @ParameterizedTest
    @CsvSource("90.1, 0.0", "-90.1, 0.0", "0.0, 180.1", "0.0, -180.1")
    fun `rechaza coordenadas fuera de rango`(latitude: Double, longitude: Double) {
        assertThrows<IllegalArgumentException> { GeoPoint(latitude, longitude) }
    }

    @ParameterizedTest
    @CsvSource("90.0, 180.0", "-90.0, -180.0", "0.0, 0.0")
    fun `acepta los limites del rango`(latitude: Double, longitude: Double) {
        val point = GeoPoint(latitude, longitude)
        assertEquals(latitude, point.latitude, 1e-9)
    }
}
