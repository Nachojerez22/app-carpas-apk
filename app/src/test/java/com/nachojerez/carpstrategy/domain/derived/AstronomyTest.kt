package com.nachojerez.carpstrategy.domain.derived

import com.nachojerez.carpstrategy.domain.model.GeoPoint
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class AstronomyTest {
    private val brovales = GeoPoint(38.35, -6.70)

    private fun assertNear(expected: String, actual: Instant?, toleranceSeconds: Long = 120) {
        val diff = Duration.between(Instant.parse(expected), actual!!).abs()
        assertTrue(diff.seconds <= toleranceSeconds, "Esperado $expected, obtenido $actual (Δ ${diff.seconds} s)")
    }

    /** Referencias calculadas con la librería astral 3.2 (ver docs del PR). */
    @ParameterizedTest
    @CsvSource(
        "2026-10-03, 2026-10-03T05:57:36Z, 2026-10-03T06:24:30Z, 2026-10-03T18:06:32Z, 2026-10-03T18:33:24Z",
        "2026-06-21, 2026-06-21T04:31:18Z, 2026-06-21T05:03:38Z, 2026-06-21T19:53:37Z, 2026-06-21T20:25:57Z",
        "2026-12-21, 2026-12-21T07:10:04Z, 2026-12-21T07:40:22Z, 2026-12-21T17:09:24Z, 2026-12-21T17:39:41Z",
    )
    fun `orto, ocaso y crepusculo civil en Brovales`(date: String, dawn: String, sunrise: String, sunset: String, dusk: String) {
        val sun = SolarCalculator.sunTimes(LocalDate.parse(date), brovales)
        assertNear(dawn, sun.civilDawn)
        assertNear(sunrise, sun.sunrise)
        assertNear(sunset, sun.sunset)
        assertNear(dusk, sun.civilDusk)
    }

    @Test
    fun `ventana legal de una hora antes del orto a una hora despues del ocaso`() {
        val sun = SolarCalculator.sunTimes(LocalDate.parse("2026-10-03"), brovales)
        val window = LegalWindow.of(sun)!!
        assertEquals(sun.sunrise!!.minus(Duration.ofHours(1)), window.start)
        assertEquals(sun.sunset!!.plus(Duration.ofHours(1)), window.end)
        assertTrue(Instant.parse("2026-10-03T12:00:00Z") in window)
        assertFalse(Instant.parse("2026-10-03T03:00:00Z") in window, "La noche nunca es legal en Brovales")
        assertFalse(Instant.parse("2026-10-03T21:00:00Z") in window)
    }

    @Test
    fun `sin orto en la noche polar no hay ventana legal`() {
        val sun = SolarCalculator.sunTimes(LocalDate.parse("2026-12-21"), GeoPoint(78.2, 15.6))
        assertNull(sun.sunrise)
        assertNull(LegalWindow.of(sun))
    }

    @Test
    fun `luna llena el 26 de septiembre de 2026 y cuarto menguante a primeros de octubre`() {
        val full = MoonCalculator.moon(Instant.parse("2026-09-26T12:00:00Z"))
        assertTrue(full.illumination > 0.95, "iluminación ${full.illumination}")
        assertEquals(MoonPhaseName.FULL, full.phase)

        val waning = MoonCalculator.moon(Instant.parse("2026-10-03T12:00:00Z"))
        assertTrue(waning.phase == MoonPhaseName.LAST_QUARTER || waning.phase == MoonPhaseName.WANING_GIBBOUS, "${waning.phase}")
        assertTrue(waning.ageDays in 20.0..23.5, "edad ${waning.ageDays}")
    }

    @Test
    fun `edad lunar siempre entre 0 y un mes sinodico`() {
        listOf("1990-01-01T00:00:00Z", "2000-01-06T18:14:00Z", "2040-05-05T05:05:05Z").forEach {
            val moon = MoonCalculator.moon(Instant.parse(it))
            assertTrue(moon.ageDays >= 0 && moon.ageDays < MoonCalculator.SYNODIC_MONTH_DAYS)
            assertTrue(moon.illumination in 0.0..1.0)
        }
        assertEquals(MoonPhaseName.NEW, MoonCalculator.moon(Instant.parse("2000-01-06T18:14:00Z")).phase)
    }
}
