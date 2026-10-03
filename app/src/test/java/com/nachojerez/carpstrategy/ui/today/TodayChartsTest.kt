package com.nachojerez.carpstrategy.ui.today

import com.nachojerez.carpstrategy.domain.derived.EnsembleHour
import com.nachojerez.carpstrategy.domain.derived.MergedHour
import com.nachojerez.carpstrategy.domain.derived.ModelEnsemble
import com.nachojerez.carpstrategy.domain.derived.SourcedValue
import com.nachojerez.carpstrategy.domain.manual.DataSource
import com.nachojerez.carpstrategy.domain.manual.WeatherVariable
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TodayChartsTest {
    private val now = Instant.parse("2026-10-03T10:00:00Z")
    private val hours = (-24 * 7..24 * 4).map { now.plus(Duration.ofHours(it.toLong())) }

    private val merged = hours.map {
        MergedHour(it, mapOf(
            WeatherVariable.PRESSURE_MSL to SourcedValue(1018.0, DataSource.MODELS),
            WeatherVariable.AIR_TEMPERATURE to SourcedValue(20.0, DataSource.AEMET),
        ))
    }
    private val ensemble = hours.map {
        EnsembleHour(it, ModelEnsemble.stats(listOf(18.0, 22.0)), ModelEnsemble.stats(listOf(1015.0, 1021.0)), null, null, null, null, null, null, false)
    }

    @Test
    fun `presion - cinco dias observados y tres de prevision con banda min-max`() {
        val chart = TodayCharts.pressure(merged, ensemble, now)
        assertEquals(now.minus(Duration.ofDays(5)), chart.from)
        assertEquals(now.plus(Duration.ofDays(3)), chart.to)
        assertTrue(chart.observed.all { !it.time.isAfter(now) && !it.time.isBefore(chart.from) })
        assertEquals(5 * 24 + 1, chart.observed.size)
        assertEquals(3 * 24 + 1, chart.forecast.size)
        assertEquals(1018.0, chart.forecast.first().value, 1e-9)
        assertEquals(1015.0, chart.bandLow.first().value, 1e-9)
        assertEquals(1021.0, chart.bandHigh.first().value, 1e-9)
        val range = chart.valueRange()!!
        assertTrue(range.start < 1015.0 && range.endInclusive > 1021.0)
    }

    @Test
    fun `temperatura incluye el agua estimada dia a dia`() {
        val chart = TodayCharts.temperature(merged, ensemble, now)
        assertTrue(chart.secondary.isNotEmpty())
        assertTrue(chart.secondary.all { it.value == 20.0 })
        assertEquals(now, chart.secondary.last().time)
    }

    @Test
    fun `sin datos la grafica esta vacia`() {
        val chart = TodayCharts.pressure(emptyList(), emptyList(), now)
        assertTrue(chart.isEmpty)
        assertEquals(null, chart.valueRange())
    }
}
