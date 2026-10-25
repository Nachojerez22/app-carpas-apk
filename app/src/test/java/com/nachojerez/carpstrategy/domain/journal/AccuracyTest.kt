package com.nachojerez.carpstrategy.domain.journal

import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.rules.FavorabilityBand
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AccuracyTest {
    private val t0 = Instant.parse("2026-06-01T06:00:00Z")
    private val point = GeoPoint(38.35, -6.70)

    /** Sesión terminada de 4 h con 2 cañas (8 horas-caña). */
    private fun session(i: Int, band: FavorabilityBand, catches: Int, bites: Int = catches): Session {
        val start = t0.plus(Duration.ofDays(i.toLong()))
        return Session(
            id = i.toLong(), start = start, end = start.plus(Duration.ofHours(4)), location = point, rods = 2,
            bites = bites, catches = List(catches) { Catch(time = start.plus(Duration.ofHours(1))) },
            prediction = PredictionSnapshot(computedAt = start, location = point, blocked = false, favorability = null, band = band, limitingLevel = null, demand = null),
            createdAt = start,
        )
    }

    @Test
    fun `con pocas sesiones no hay veredicto`() {
        val report = Accuracy.report((0 until 5).map { session(it, FavorabilityBand.FAVORABLE, 2) } + (5 until 10).map { session(it, FavorabilityBand.UNFAVORABLE, 0) })
        assertEquals(AccuracyVerdict.TOO_FEW, report.verdict)
        assertEquals(5, report.favorable?.sessions)
    }

    @Test
    fun `con datos suficientes y bien separados, acierta, y si es al reves, no`() {
        val good = (0 until 12).map { session(it, FavorabilityBand.FAVORABLE, 3) } + (12 until 24).map { session(it, FavorabilityBand.VERY_UNFAVORABLE, 0) }
        assertEquals(AccuracyVerdict.AGREES, Accuracy.report(good).verdict)
        val bad = (0 until 12).map { session(it, FavorabilityBand.FAVORABLE, 0) } + (12 until 24).map { session(it, FavorabilityBand.UNFAVORABLE, 3) }
        assertEquals(AccuracyVerdict.DISAGREES, Accuracy.report(bad).verdict)
        val same = (0 until 12).map { session(it, FavorabilityBand.FAVORABLE, it % 2) } + (12 until 24).map { session(it, FavorabilityBand.UNFAVORABLE, it % 2) }
        assertEquals(AccuracyVerdict.UNCLEAR, Accuracy.report(same).verdict)
    }

    @Test
    fun `la tasa se contrae hacia tu media y el intervalo la contiene`() {
        // 1 captura en 8 horas-caña, con tu media en 0,5/h: la estimación sube hacia la media.
        val e = Accuracy.estimate(1, 8.0, 0.5)
        assertTrue(e.mean > 1 / 8.0 && e.mean < 0.5)
        assertTrue(e.low < e.mean && e.mean < e.high)
        // Con mucho esfuerzo manda lo observado.
        val big = Accuracy.estimate(100, 1000.0, 0.5)
        assertEquals(0.1, big.mean, 0.01)
    }
}
