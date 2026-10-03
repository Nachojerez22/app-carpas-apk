package com.nachojerez.carpstrategy.domain.derived

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrendsTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val now = Instant.parse("2026-10-03T10:00:00Z")

    /** Serie horaria de [hours] horas que termina en [end], con valor f(horas antes de end). */
    private fun series(hours: Int, end: Instant = now, f: (Int) -> Double) =
        HourlySeries((0 until hours).map { h -> Point(end.minusSeconds(h * 3600L), f(h)) })

    @Test
    fun `tendencias de presion y estabilidad`() {
        // Baja 0,5 hPa por hora hacia el presente.
        val trend = Trends.pressure(series(100) { h -> 1010.0 + 0.5 * h }, now)
        assertEquals(-1.5, trend.delta3hHpa!!, 1e-9)
        assertEquals(-12.0, trend.delta24hHpa!!, 1e-9)
        assertEquals(-36.0, trend.delta72hHpa!!, 1e-9)
        assertTrue(trend.stdDev48hHpa!! > 6.0)

        val flat = Trends.pressure(series(100) { 1018.0 }, now)
        assertEquals(0.0, flat.stdDev48hHpa!!, 1e-9)
    }

    @Test
    fun `presion sin datos recientes`() {
        val old = Trends.pressure(series(10, now.minus(Duration.ofHours(6))) { 1018.0 }, now)
        assertNull(old.delta3hHpa)
        assertNull(old.stdDev48hHpa)
    }

    @Test
    fun `temperatura de hoy frente a los tres dias anteriores`() {
        val trend = Trends.airTemperature(series(96) { h -> if (h < 24) 25.0 else 20.0 }, now)!!
        assertEquals(25.0, trend.last24hMeanC, 1e-9)
        assertEquals(20.0, trend.previous3DaysMeanC, 1e-9)
        assertEquals(5.0, trend.deltaC, 1e-9)
        assertNull(Trends.airTemperature(series(30) { 20.0 }, now), "Hacen falta 4 días de serie")
    }

    @Test
    fun `viento dominante y persistencia`() {
        val speed = series(48) { 10.0 }
        val west = Trends.wind(speed, series(48) { 270.0 }, series(48) { h -> if (h == 5) 40.0 else 15.0 }, now, Duration.ofHours(24))!!
        assertEquals(270.0, west.dominantDirectionDeg!!, 1e-6)
        assertEquals(1.0, west.persistence, 1e-9)
        assertEquals(10.0, west.meanSpeedKmh, 1e-9)
        assertEquals(40.0, west.maxGustKmh)

        // Mitad del norte y mitad del sur con la misma fuerza: sin rumbo dominante.
        val variable = Trends.wind(speed, series(48) { h -> if (h % 2 == 0) 0.0 else 180.0 }, HourlySeries(emptyList()), now, Duration.ofHours(24))!!
        assertNull(variable.dominantDirectionDeg)
        assertEquals(0.0, variable.persistence, 1e-9)
        assertNull(variable.maxGustKmh)

        // Alrededor del norte la media es circular: 350° y 10° → 0°.
        val north = Trends.wind(speed, series(48) { h -> if (h % 2 == 0) 350.0 else 10.0 }, HourlySeries(emptyList()), now, Duration.ofHours(24))!!
        assertEquals(0.0, minOf(north.dominantDirectionDeg!!, 360 - north.dominantDirectionDeg!!), 1e-6)
        assertTrue(north.persistence > 0.98)
    }

    @Test
    fun `lluvia acumulada y horas desde la ultima`() {
        val rain = Trends.rainfall(series(240) { h -> if (h == 5 || h == 30) 2.0 else 0.0 }, now, madrid)
        assertEquals(2.0, rain.last24hMm, 1e-9)
        assertEquals(4.0, rain.last72hMm, 1e-9)
        assertEquals(5L, rain.hoursSinceRain)
        assertEquals(0.0, rain.antecedentIndexMm, 1e-9)
        assertFalse(rain.runoffLikely)
    }

    @Test
    fun `20 mm sobre suelo seco no son escorrentia, con suelo humedo si`() {
        val dry = Trends.rainfall(series(240) { h -> if (h in 10..19) 2.5 else 0.0 }, now, madrid)
        assertEquals(25.0, dry.last72hMm, 1e-9)
        assertFalse(dry.runoffLikely, "Suelo seco: no cuenta")

        // Además, 15 mm cuatro días antes de la ventana de 72 h.
        val wet = Trends.rainfall(series(240) { h -> if (h in 10..19) 2.5 else if (h in 80..82) 5.0 else 0.0 }, now, madrid)
        assertTrue(wet.antecedentIndexMm >= 10.0, "API ${wet.antecedentIndexMm}")
        assertTrue(wet.runoffLikely)

        val storm = Trends.rainfall(series(240) { h -> if (h in 0..3) 11.0 else 0.0 }, now, madrid)
        assertTrue(storm.runoffLikely, "≥ 40 mm en 24 h")
    }

    @Test
    fun `la lluvia diaria manual sustituye a la horaria en el indice antecedente`() {
        // Primer bloque antecedente: de 96 h a 72 h antes (29/9 10:00Z–30/9 10:00Z); su centro es el 30/9 00:00 en Madrid.
        val rain = Trends.rainfall(series(240) { 0.0 }, now, madrid, mapOf(LocalDate.parse("2026-09-30") to 12.0))
        assertEquals(12.0, rain.antecedentIndexMm, 1e-9)
    }

    @Test
    fun `rachas de dias calidos y frios hasta ayer`() {
        // Hoy 3/10 (Madrid). Ayer y anteayer máximas de 32 °C; el día anterior, 25 °C.
        val hot = HourlySeries(
            (0 until 24 * 5).map { h ->
                val t = LocalDate.parse("2026-09-28").atStartOfDay(madrid).toInstant().plusSeconds(h * 3600L)
                val day = t.atZone(madrid).toLocalDate()
                val max = if (day >= LocalDate.parse("2026-10-01")) 32.0 else 25.0
                Point(t, if (h % 24 == 15) max else max - 8)
            },
        )
        val streaks = Trends.streaks(hot, now, madrid)
        assertEquals(2, streaks.hotDays)
        assertEquals(0, streaks.coldDays)

        val cold = Trends.streaks(series(24 * 6) { 5.0 }, now, madrid)
        assertTrue(cold.coldDays >= 4)
        assertEquals(0, cold.hotDays)
    }
}
