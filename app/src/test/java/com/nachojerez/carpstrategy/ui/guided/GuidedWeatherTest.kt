package com.nachojerez.carpstrategy.ui.guided

import com.nachojerez.carpstrategy.domain.derived.DerivedCalculator
import com.nachojerez.carpstrategy.domain.derived.MergedHour
import com.nachojerez.carpstrategy.domain.derived.SourcedValue
import com.nachojerez.carpstrategy.domain.manual.DataSource
import com.nachojerez.carpstrategy.domain.manual.WeatherVariable
import com.nachojerez.carpstrategy.domain.model.Cached
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.model.HourlyWeather
import com.nachojerez.carpstrategy.domain.model.MultiModelForecast
import com.nachojerez.carpstrategy.domain.model.WeatherModel
import com.nachojerez.carpstrategy.domain.usecase.RawWeather
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuidedWeatherTest {
    private val point = GeoPoint(38.35, -6.70)
    private val t0 = Instant.parse("2026-10-04T08:00:00Z")

    private fun hour(i: Long, air: Double, wind: Double = 10.0, rain: Double = 0.0) = MergedHour(
        t0.plus(Duration.ofHours(i)),
        mapOf(
            WeatherVariable.AIR_TEMPERATURE to SourcedValue(air, DataSource.MODELS),
            WeatherVariable.WIND_SPEED to SourcedValue(wind, DataSource.MODELS),
            WeatherVariable.WIND_DIRECTION to SourcedValue(270.0, DataSource.MODELS),
            WeatherVariable.PRECIPITATION to SourcedValue(rain, DataSource.MODELS),
            WeatherVariable.PRESSURE_MSL to SourcedValue(1015.0, DataSource.MODELS),
        ),
    )

    private fun model(code: Int?) = listOf(
        HourlyWeather(t0.plus(Duration.ofHours(1)), null, null, null, null, null, null, null, null, weatherCode = code),
    )

    private val merged = (-48L..3L).map { hour(it, air = 15.0 + it.coerceAtLeast(0)) }
    private val raw = RawWeather(
        forecast = Cached(
            MultiModelForecast(point, point, 300.0, "Europe/Madrid", mapOf(WeatherModel.ICON_EU to model(3), WeatherModel.ECMWF_IFS025 to model(95))),
            t0,
        ),
        ensemble = emptyList(),
        observations = null,
        merged = merged,
    )

    @Test
    fun `toma la hora mas cercana y el codigo mas severo de los modelos`() {
        val now = t0.plus(Duration.ofMinutes(70))
        val derived = DerivedCalculator.compute(merged, emptyList(), emptyList(), null, null, emptyList(), point, now)
        val snapshot = weatherSnapshotAt(raw, derived, now)!!
        assertEquals(now, snapshot.time)
        assertEquals(16.0, snapshot.airC)
        assertEquals(270.0, snapshot.windFromDeg)
        assertEquals(95, snapshot.weatherCode)
        assertTrue(snapshot.forecastStorm)
        assertEquals(1015.0, snapshot.pressureHpa)
        assertEquals(derived.water?.valueC, snapshot.waterC)
        assertFalse(snapshot.waterMeasured)
        assertEquals(derived.sunToday.sunset, snapshot.sunset)
        assertEquals(derived.legalToday?.end, snapshot.legalEnd)
    }

    @Test
    fun `sin datos cerca del aviso no hay tiempo`() {
        assertNull(weatherSnapshotAt(raw, null, t0.plus(Duration.ofHours(6))))
        assertNull(weatherSnapshotAt(raw.copy(merged = emptyList()), null, t0))
        val noDerived = weatherSnapshotAt(raw, null, t0)!!
        assertNull(noDerived.waterC)
        assertNull(noDerived.weatherCode)
    }
}
