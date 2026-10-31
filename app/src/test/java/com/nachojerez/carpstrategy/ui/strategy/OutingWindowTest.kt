package com.nachojerez.carpstrategy.ui.strategy

import com.nachojerez.carpstrategy.domain.derived.LegalWindow
import com.nachojerez.carpstrategy.domain.derived.SolarCalculator
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.rules.FavorabilityBand
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OutingWindowTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val point = GeoPoint(38.35, -6.7)
    private val t0 = Instant.parse("2026-11-01T07:00:00Z")

    private fun hour(i: Long, band: FavorabilityBand?, blocked: Boolean = false) = OutingHour(t0.plus(Duration.ofHours(i)), band, blocked)

    @Test
    fun `la mejor franja es la mas larga con la mejor escala`() {
        val hours = listOf(
            hour(0, FavorabilityBand.FAVORABLE),
            hour(1, FavorabilityBand.INTERMEDIATE),
            hour(2, FavorabilityBand.FAVORABLE),
            hour(3, FavorabilityBand.FAVORABLE),
            hour(4, FavorabilityBand.VERY_FAVORABLE, blocked = true),
        )
        val best = bestWindow(hours, cap = t0.plus(Duration.ofHours(10)))!!
        assertEquals(FavorabilityBand.FAVORABLE, best.band)
        assertEquals(t0.plus(Duration.ofHours(2)), best.start)
        assertEquals(t0.plus(Duration.ofHours(4)), best.end)
    }

    @Test
    fun `el fin no pasa del fin legal y sin horas validas no hay franja`() {
        val best = bestWindow(listOf(hour(0, FavorabilityBand.VERY_FAVORABLE)), cap = t0.plus(Duration.ofMinutes(20)))!!
        assertEquals(t0.plus(Duration.ofMinutes(20)), best.end)
        assertNull(bestWindow(listOf(hour(0, FavorabilityBand.FAVORABLE, blocked = true), hour(1, null)), cap = t0))
    }

    @Test
    fun `las horas cubren la jornada legal`() {
        val legal = LegalWindow.of(SolarCalculator.sunTimes(java.time.LocalDate.of(2026, 11, 1), point))!!
        val start = legal.start.plus(PREP_AFTER_LEGAL_START)
        val hours = outingHours(start, point, zone)
        assertEquals(start, hours.first())
        assertTrue(!hours.last().isAfter(legal.end.minus(PREP_AFTER_LEGAL_START)))
        assertTrue(hours.size >= 10)
    }
}
