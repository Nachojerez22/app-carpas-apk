package com.nachojerez.carpstrategy.ui.place

import com.nachojerez.carpstrategy.domain.model.GeoPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaceInputTest {
    @Test
    fun `admite coma o punto decimal y espacios`() {
        assertEquals(CoordinateInput.Valid(GeoPoint(38.35, -6.7)), parseCoordinates(" 38,35 ", "-6.70"))
    }

    @Test
    fun `rechaza latitud o longitud fuera de rango o no numerica`() {
        assertEquals(CoordinateInput.Invalid(CoordinateError.LATITUDE), parseCoordinates("91", "0"))
        assertEquals(CoordinateInput.Invalid(CoordinateError.LATITUDE), parseCoordinates("", "0"))
        assertEquals(CoordinateInput.Invalid(CoordinateError.LONGITUDE), parseCoordinates("38", "-180,5"))
        assertEquals(CoordinateInput.Invalid(CoordinateError.LONGITUDE), parseCoordinates("38", "abc"))
    }
}
