package com.nachojerez.carpstrategy.domain.derived

import com.nachojerez.carpstrategy.domain.model.StationObservation
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WaterAndBiasTest {
    private val now = Instant.parse("2026-10-03T10:00:00Z")

    private fun air(hours: Int = 24 * 10, f: (Int) -> Double) =
        HourlySeries((0 until hours).map { h -> Point(now.minusSeconds(h * 3600L), f(h)) })

    @Test
    fun `con aire constante la estimacion es esa temperatura`() {
        val water = WaterTemperatureModel.evaluate(air { 20.0 }, emptyList(), now)!!
        assertEquals(WaterTempSource.ESTIMATED, water.source)
        assertEquals(20.0, water.valueC, 1e-9)
        assertEquals(0.0, water.trend3dC!!, 1e-9)
        assertNull(water.calibrationOffsetC)
    }

    @Test
    fun `los dias recientes pesan mas`() {
        // Último día a 28 °C, los seis anteriores a 14 °C: (7·28 + 21·14) / 28 = 17,5.
        val estimate = WaterTemperatureModel.estimate(air { h -> if (h < 24) 28.0 else 14.0 }, now)!!
        assertEquals(17.5, estimate, 1e-9)
    }

    @Test
    fun `sin suficientes dias de aire no se estima`() {
        assertNull(WaterTemperatureModel.estimate(air(24 * 3) { 20.0 }, now))
        assertNull(WaterTemperatureModel.evaluate(air(24 * 3) { 20.0 }, emptyList(), now))
    }

    @Test
    fun `una medida reciente manda y calibra la estimacion`() {
        val measurement = Point(now.minus(Duration.ofHours(5)), 18.0)
        val water = WaterTemperatureModel.evaluate(air { 20.0 }, listOf(measurement), now)!!
        assertEquals(WaterTempSource.MEASURED, water.source)
        assertEquals(18.0, water.valueC, 1e-9)
        assertEquals(measurement.time, water.measuredAt)
        assertEquals(-2.0, water.calibrationOffsetC!!, 1e-9)
        assertEquals(1, water.calibrationSamples)
    }

    @Test
    fun `con una medida antigua se usa la estimacion calibrada`() {
        val old = Point(now.minus(Duration.ofDays(3)), 17.0)
        val water = WaterTemperatureModel.evaluate(air { 20.0 }, listOf(old), now)!!
        assertEquals(WaterTempSource.ESTIMATED, water.source)
        assertEquals(17.0, water.valueC, 1e-9)
        assertEquals(-3.0, water.calibrationOffsetC!!, 1e-9)
    }

    @Test
    fun `la calibracion se limita a 5 grados`() {
        val absurd = Point(now.minus(Duration.ofDays(3)), 2.0)
        val water = WaterTemperatureModel.evaluate(air { 20.0 }, listOf(absurd), now)!!
        assertEquals(-5.0, water.calibrationOffsetC!!, 1e-9)
    }

    @Test
    fun `tendencia de tres dias positiva si el aire se calienta`() {
        val water = WaterTemperatureModel.evaluate(air { h -> 25.0 - h * 0.05 }, emptyList(), now)!!
        assertTrue(water.trend3dC!! > 3.0, "tendencia ${water.trend3dC}")
    }

    private fun ensembleHour(time: Instant, temperature: Double) = EnsembleHour(
        time, VariableStats(temperature, 0.0, 3), null, null, null, null, null, null, null, false,
    )

    private fun obs(time: Instant, temperature: Double) =
        StationObservation("X", time, temperature, null, null, null, null, null, null)

    @Test
    fun `sesgo de los modelos frente a AEMET con correccion por altitud`() {
        val hours = (0 until 10).map { now.minusSeconds(it * 3600L) }
        // Modelo 22 °C a 305 m; estación a 511 m marca 19 °C → a 305 m serían 19 + 1,339 = 20,339.
        val bias = TemperatureBiasModel.estimate(
            ensemble = hours.map { ensembleHour(it, 22.0) },
            observations = hours.map { obs(it, 19.0) },
            stationAltitudeM = 511.0,
            gridElevationM = 305.0,
            now = now,
        )!!
        assertEquals(1.339, bias.altitudeAdjustmentC, 1e-9)
        assertEquals(22.0 - 20.339, bias.biasC, 1e-9)
        assertEquals(10, bias.samples)
    }

    @Test
    fun `sin horas suficientes en comun no hay sesgo`() {
        val hours = (0 until 5).map { now.minusSeconds(it * 3600L) }
        assertNull(TemperatureBiasModel.estimate(hours.map { ensembleHour(it, 22.0) }, hours.map { obs(it, 19.0) }, null, null, now))
    }

    @Test
    fun `sesgo limitado y datos incompatibles descartados`() {
        val hours = (0 until 8).map { now.minusSeconds(it * 3600L) }
        val big = TemperatureBiasModel.estimate(hours.map { ensembleHour(it, 30.0) }, hours.map { obs(it, 22.0) }, null, null, now)!!
        assertEquals(5.0, big.biasC, 1e-9)
        assertNull(TemperatureBiasModel.estimate(hours.map { ensembleHour(it, 50.0) }, hours.map { obs(it, 20.0) }, null, null, now))
    }
}
