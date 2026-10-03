package com.nachojerez.carpstrategy.domain.derived

import com.nachojerez.carpstrategy.domain.model.HourlyWeather
import com.nachojerez.carpstrategy.domain.model.WeatherModel
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelEnsembleTest {
    private val t0 = Instant.parse("2026-09-25T22:00:00Z")
    private val t1 = t0.plusSeconds(3600)

    private fun hour(
        time: Instant = t0,
        temperature: Double? = null,
        windSpeed: Double? = null,
        windDirection: Double? = null,
    ) = HourlyWeather(time, temperature, null, windSpeed, windDirection, null, null, null, null)

    @Test
    fun `media y dispersion de la primera hora real de Brovales`() {
        // Primera hora de la respuesta real (26/09/2026 00:00 local): ICON-EU, ARPEGE, ECMWF.
        val result = ModelEnsemble.combine(
            mapOf(
                WeatherModel.ICON_EU to listOf(hour(temperature = 24.4, windSpeed = 4.9, windDirection = 343.0)),
                WeatherModel.ARPEGE_EUROPE to listOf(hour(temperature = 26.3, windSpeed = 3.2, windDirection = 297.0)),
                WeatherModel.ECMWF_IFS025 to listOf(hour(temperature = 24.7, windSpeed = 3.7, windDirection = 331.0)),
            ),
        ).single()
        assertEquals(25.133, result.temperatureC!!.mean, 0.001)
        assertEquals(1.9, result.temperatureC!!.spread, 1e-9)
        assertEquals(3, result.temperatureC!!.count)
        assertEquals(1.7, result.windSpeedKmh!!.spread, 1e-9)
        assertEquals(46.0, result.windDirection!!.spreadDeg, 1e-9)
        assertFalse(result.windDivergent, "Con viento flojo y poca diferencia no hay aviso")
        assertNull(result.pressureMslHpa)
    }

    @Test
    fun `la direccion media es circular alrededor del norte`() {
        val stats = ModelEnsemble.directionStats(listOf(350.0, 10.0))!!
        assertEquals(0.0, minOf(stats.meanDeg, 360.0 - stats.meanDeg), 1e-9)
        assertEquals(20.0, stats.spreadDeg, 1e-9)
    }

    @Test
    fun `distancia angular toma el camino corto`() {
        assertEquals(20.0, ModelEnsemble.angularDistance(350.0, 10.0), 1e-9)
        assertEquals(180.0, ModelEnsemble.angularDistance(0.0, 180.0), 1e-9)
        assertEquals(90.0, ModelEnsemble.angularDistance(-45.0, 45.0), 1e-9)
    }

    @Test
    fun `divergencia por velocidad`() {
        val speed = ModelEnsemble.stats(listOf(5.0, 15.0))
        assertTrue(ModelEnsemble.isWindDivergent(speed, null))
        assertFalse(ModelEnsemble.isWindDivergent(ModelEnsemble.stats(listOf(5.0, 14.9)), null))
    }

    @Test
    fun `divergencia por direccion solo con viento apreciable`() {
        val direction = ModelEnsemble.directionStats(listOf(0.0, 120.0))
        assertTrue(ModelEnsemble.isWindDivergent(ModelEnsemble.stats(listOf(6.0, 8.0)), direction))
        assertFalse(ModelEnsemble.isWindDivergent(ModelEnsemble.stats(listOf(2.0, 3.0)), direction))
    }

    @Test
    fun `un solo modelo nunca diverge`() {
        assertFalse(ModelEnsemble.isWindDivergent(ModelEnsemble.stats(listOf(30.0)), ModelEnsemble.directionStats(listOf(90.0))))
    }

    @Test
    fun `une horas de todos los modelos ordenadas e ignora valores ausentes`() {
        val result = ModelEnsemble.combine(
            mapOf(
                WeatherModel.ICON_EU to listOf(hour(t1, temperature = 20.0), hour(t0, temperature = 10.0)),
                WeatherModel.ECMWF_IFS025 to listOf(hour(t0, temperature = null), hour(t1, temperature = 22.0)),
            ),
        )
        assertEquals(listOf(t0, t1), result.map { it.time })
        assertEquals(1, result[0].temperatureC!!.count)
        assertEquals(10.0, result[0].temperatureC!!.mean, 1e-9)
        assertEquals(21.0, result[1].temperatureC!!.mean, 1e-9)
    }

    @Test
    fun `sin datos no hay estadisticas`() {
        assertNull(ModelEnsemble.stats(emptyList()))
        assertNull(ModelEnsemble.directionStats(emptyList()))
        assertTrue(ModelEnsemble.combine(emptyMap()).isEmpty())
    }
}
