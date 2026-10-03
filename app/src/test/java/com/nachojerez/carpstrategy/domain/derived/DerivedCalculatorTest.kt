package com.nachojerez.carpstrategy.domain.derived

import com.nachojerez.carpstrategy.domain.manual.DataSource
import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.ManualOrigin
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.manual.RecordPeriod
import com.nachojerez.carpstrategy.domain.manual.WeatherVariable
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.model.StationObservation
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DerivedCalculatorTest {
    private val now = Instant.parse("2026-10-03T10:00:00Z")
    private val brovales = GeoPoint(38.35, -6.70)
    private val hours = (0 until 24 * 8).map { now.minusSeconds(it * 3600L) }

    private fun merged(temperature: Double, source: DataSource = DataSource.MODELS) = hours.map { t ->
        MergedHour(
            t,
            mapOf(
                WeatherVariable.AIR_TEMPERATURE to SourcedValue(temperature, source),
                WeatherVariable.PRESSURE_MSL to SourcedValue(1018.0, DataSource.MODELS),
                WeatherVariable.WIND_SPEED to SourcedValue(12.0, DataSource.MODELS),
                WeatherVariable.WIND_DIRECTION to SourcedValue(225.0, DataSource.MODELS),
                WeatherVariable.PRECIPITATION to SourcedValue(0.0, DataSource.MODELS),
                WeatherVariable.CLOUD_COVER to SourcedValue(40.0, DataSource.MODELS),
            ),
        )
    }

    private val ensemble = hours.take(12).map { EnsembleHour(it, VariableStats(22.0, 1.0, 3), null, null, null, null, null, null, null, false) }
    private val observations = hours.take(12).map { StationObservation("X", it, 20.0, null, null, null, null, null, null) }

    private fun compute(merged: List<MergedHour>, manual: List<ManualRecord> = emptyList(), manualEnabled: Boolean = true) =
        DerivedCalculator.compute(merged, ensemble, observations, null, null, manual, brovales, now, manualEnabled = manualEnabled)

    @Test
    fun `corrige con el sesgo de AEMET solo las temperaturas de los modelos`() {
        val fromModels = compute(merged(22.0))
        assertEquals(2.0, fromModels.temperatureBias!!.biasC, 1e-9)
        // 22 °C de modelo − 2 °C de sesgo = 20 °C en todos los días → agua estimada 20 °C.
        assertEquals(20.0, fromModels.water!!.valueC, 1e-9)
        assertEquals(WaterTempSource.ESTIMATED, fromModels.water!!.source)

        val fromManual = compute(merged(22.0, DataSource.MANUAL))
        assertEquals(22.0, fromManual.water!!.valueC, 1e-9, "Lo manual no se corrige")
    }

    @Test
    fun `resto de parametros y astronomia`() {
        val d = compute(merged(15.0))
        assertEquals(0.0, d.pressure.delta24hHpa!!, 1e-9)
        assertEquals(225.0, d.wind24h!!.dominantDirectionDeg!!, 1e-6)
        assertEquals(0.0, d.rain.last72hMm, 1e-9)
        assertEquals(40.0, d.cloudCover24hPct!!, 1e-9)
        assertNotNull(d.legalToday)
        assertTrue(d.legalToday!!.start.isBefore(d.sunToday.sunrise))
        // Agua estimada ~13 °C (15 − 2 de sesgo) con tendencia plana en octubre → otoño por calendario.
        assertEquals(SeasonByWater.AUTUMN, d.season!!.season)
        assertTrue(d.season!!.fromCalendar)
        assertEquals(LocalDate.parse("2026-10-03"), d.sunToday.date)
    }

    @Test
    fun `usa la medida del agua y el nivel del embalse de los datos manuales`() {
        fun record(period: RecordPeriod, vararg values: Pair<ManualField, Double>) = ManualRecord(
            period = period, location = brovales, source = "x", origin = ManualOrigin.Typed,
            values = values.toMap(), createdAt = now,
        )
        val manual = listOf(
            record(RecordPeriod.At(now.minusSeconds(3 * 3600)), ManualField.WATER_TEMP_SURFACE to 19.5),
            record(RecordPeriod.Day(LocalDate.parse("2026-09-21")), ManualField.RESERVOIR_VOLUME to 5.0),
            record(RecordPeriod.Day(LocalDate.parse("2026-09-28")), ManualField.RESERVOIR_PERCENT to 57.1),
        )
        val d = compute(merged(20.0), manual)
        assertEquals(WaterTempSource.MEASURED, d.water!!.source)
        assertEquals(19.5, d.water!!.valueC, 1e-9)
        assertEquals(0.571 * 6.98 - 5.0, d.reservoir!!.delta7dHm3!!, 1e-9)

        val disabled = compute(merged(20.0), manual, manualEnabled = false)
        assertEquals(WaterTempSource.ESTIMATED, disabled.water!!.source)
        assertNull(disabled.reservoir)
    }
}
