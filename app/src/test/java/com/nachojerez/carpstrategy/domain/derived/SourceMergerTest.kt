package com.nachojerez.carpstrategy.domain.derived

import com.nachojerez.carpstrategy.domain.manual.DataSource
import com.nachojerez.carpstrategy.domain.manual.DataSource.AEMET
import com.nachojerez.carpstrategy.domain.manual.DataSource.MANUAL
import com.nachojerez.carpstrategy.domain.manual.DataSource.MODELS
import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.ManualOrigin
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.manual.RecordPeriod
import com.nachojerez.carpstrategy.domain.manual.SourcePriority
import com.nachojerez.carpstrategy.domain.manual.WeatherVariable
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.model.StationObservation
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SourceMergerTest {
    private val h6 = Instant.parse("2026-10-03T06:00:00Z")
    private val h7 = Instant.parse("2026-10-03T07:00:00Z")
    private val place = GeoPoint(38.35, -6.70)

    private fun ensemble(time: Instant, temperature: Double) = EnsembleHour(
        time = time,
        temperatureC = VariableStats(temperature, 1.0, 3),
        pressureMslHpa = VariableStats(1018.0, 0.5, 3),
        windSpeedKmh = VariableStats(5.0, 2.0, 3),
        windDirection = DirectionStats(200.0, 20.0, 3),
        windGustsKmh = null,
        cloudCoverPct = VariableStats(50.0, 10.0, 3),
        precipitationMm = VariableStats(0.0, 0.0, 3),
        shortwaveRadiationWm2 = VariableStats(100.0, 10.0, 3),
        windDivergent = false,
    )

    private fun aemet(time: Instant, temperature: Double) =
        StationObservation("X", time, temperature, null, null, null, null, null, 80.0)

    private fun manual(time: Instant, created: Long, vararg values: Pair<ManualField, Double>) = ManualRecord(
        period = RecordPeriod.At(time),
        location = place,
        source = "termómetro",
        origin = ManualOrigin.Typed,
        values = values.toMap(),
        createdAt = Instant.ofEpochSecond(created),
    )

    private fun priority(vararg order: DataSource) = SourcePriority(order.toList())

    @Test
    fun `cada variable sale de la primera fuente activa con valor`() {
        val merged = SourceMerger.merge(
            ensemble = listOf(ensemble(h6, 20.0)),
            observations = listOf(aemet(h6, 18.0)),
            manual = listOf(manual(h6.plusSeconds(600), 1, ManualField.AIR_TEMPERATURE to 17.0, ManualField.WATER_TEMP_SURFACE to 19.5)),
            priority = SourcePriority.DEFAULT,
        ).single()

        assertEquals(SourcedValue(17.0, MANUAL), merged[WeatherVariable.AIR_TEMPERATURE])
        assertEquals(SourcedValue(19.5, MANUAL), merged[WeatherVariable.WATER_TEMP_SURFACE])
        assertEquals(SourcedValue(80.0, AEMET), merged[WeatherVariable.RELATIVE_HUMIDITY])
        assertEquals(SourcedValue(1018.0, MODELS), merged[WeatherVariable.PRESSURE_MSL])
        assertEquals(SourcedValue(200.0, MODELS), merged[WeatherVariable.WIND_DIRECTION])
        assertNull(merged[WeatherVariable.WIND_GUSTS])
    }

    @Test
    fun `el orden elegido por el usuario manda`() {
        val inputs = Triple(listOf(ensemble(h6, 20.0)), listOf(aemet(h6, 18.0)), listOf(manual(h6, 1, ManualField.AIR_TEMPERATURE to 17.0)))
        fun temp(p: SourcePriority) = SourceMerger.merge(inputs.first, inputs.second, inputs.third, p)
            .single()[WeatherVariable.AIR_TEMPERATURE]

        assertEquals(SourcedValue(20.0, MODELS), temp(priority(MODELS, AEMET, MANUAL)))
        assertEquals(SourcedValue(18.0, AEMET), temp(priority(AEMET, MANUAL, MODELS)))
        assertEquals(SourcedValue(18.0, AEMET), temp(priority(AEMET)))
    }

    @Test
    fun `una fuente desactivada no aporta ni horas ni valores`() {
        val merged = SourceMerger.merge(
            ensemble = listOf(ensemble(h6, 20.0)),
            observations = listOf(aemet(h7, 18.0)),
            manual = emptyList(),
            priority = priority(MODELS),
        )
        assertEquals(listOf(h6), merged.map { it.time })
    }

    @Test
    fun `une las horas de todas las fuentes en orden`() {
        val merged = SourceMerger.merge(
            ensemble = listOf(ensemble(h7, 20.0)),
            observations = listOf(aemet(h6, 18.0)),
            manual = emptyList(),
            priority = SourcePriority.DEFAULT,
        )
        assertEquals(listOf(h6, h7), merged.map { it.time })
        assertEquals(AEMET, merged[0][WeatherVariable.AIR_TEMPERATURE]!!.source)
    }

    @Test
    fun `manual redondeado a la hora y el mas reciente gana`() {
        val merged = SourceMerger.merge(
            ensemble = emptyList(),
            observations = emptyList(),
            manual = listOf(
                manual(h7.minusSeconds(29 * 60), created = 20, ManualField.WATER_TEMP_SURFACE to 19.0),
                manual(h7.plusSeconds(10 * 60), created = 10, ManualField.WATER_TEMP_SURFACE to 18.0, ManualField.TURBIDITY to 2.0),
            ),
            priority = SourcePriority.DEFAULT,
        ).single()
        assertEquals(h7, merged.time)
        assertEquals(19.0, merged[WeatherVariable.WATER_TEMP_SURFACE]!!.value)
        assertEquals(2.0, merged[WeatherVariable.TURBIDITY]!!.value)
    }

    @Test
    fun `los registros por dia no entran en la serie horaria`() {
        val daily = ManualRecord(
            period = RecordPeriod.Day(LocalDate.parse("2026-10-01")),
            location = place,
            source = "MITECO",
            origin = ManualOrigin.Typed,
            values = mapOf(ManualField.RESERVOIR_VOLUME to 4.0),
            createdAt = h6,
        )
        assertEquals(emptyList<MergedHour>(), SourceMerger.merge(emptyList(), emptyList(), listOf(daily), SourcePriority.DEFAULT))
    }

    @Test
    fun `redondeo a la hora mas cercana`() {
        assertEquals(h6, SourceMerger.roundToHour(h6.plusSeconds(29 * 60 + 59)))
        assertEquals(h7, SourceMerger.roundToHour(h6.plusSeconds(30 * 60)))
        assertEquals(h6, SourceMerger.roundToHour(h6.minusSeconds(60)))
    }
}
