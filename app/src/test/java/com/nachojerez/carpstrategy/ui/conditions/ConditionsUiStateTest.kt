package com.nachojerez.carpstrategy.ui.conditions

import com.nachojerez.carpstrategy.domain.derived.EnsembleHour
import com.nachojerez.carpstrategy.domain.derived.FreshnessLevel
import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.ManualOrigin
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.manual.RecordPeriod
import com.nachojerez.carpstrategy.domain.model.Cached
import com.nachojerez.carpstrategy.domain.model.DataError
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.model.MultiModelForecast
import com.nachojerez.carpstrategy.domain.model.NearbyObservations
import com.nachojerez.carpstrategy.domain.model.RefreshOutcome
import com.nachojerez.carpstrategy.domain.model.WeatherStation
import com.nachojerez.carpstrategy.domain.usecase.RawWeather
import com.nachojerez.carpstrategy.domain.usecase.WeatherRefreshResult
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConditionsUiStateTest {
    private val now = Instant.parse("2026-10-03T10:00:00Z")
    private val point = GeoPoint(38.35, -6.70)
    private val forecast = MultiModelForecast(point, point, 305.0, "Europe/Madrid", emptyMap())

    private fun ensembleHour(time: Instant, divergent: Boolean) =
        EnsembleHour(time, null, null, null, null, null, null, null, null, windDivergent = divergent)

    @Test
    fun `cuenta solo las horas futuras con viento divergente`() {
        val raw = RawWeather(
            forecast = Cached(forecast, now.minus(Duration.ofHours(5))),
            ensemble = listOf(
                ensembleHour(now.minus(Duration.ofHours(1)), divergent = true),
                ensembleHour(now, divergent = true),
                ensembleHour(now.plus(Duration.ofHours(1)), divergent = false),
                ensembleHour(now.plus(Duration.ofHours(2)), divergent = true),
            ),
            observations = null,
        )
        val state = buildConditionsState("Brovales", raw, refreshing = false, lastRefresh = null, now = now)

        assertEquals(2, state.divergentForecastHours)
        assertFalse(state.isLoading)
        assertEquals(FreshnessLevel.AGING, state.forecastFreshness!!.level)
        assertNull(state.observationsFreshness)
    }

    @Test
    fun `muestra los errores del ultimo refresco`() {
        val raw = RawWeather(null, emptyList(), null)
        val result = WeatherRefreshResult(
            forecast = RefreshOutcome.Success,
            observations = RefreshOutcome.Failure(DataError.MISSING_API_KEY),
        )
        val state = buildConditionsState("Brovales", raw, refreshing = true, lastRefresh = result, now = now)
        assertNull(state.forecastError)
        assertEquals(DataError.MISSING_API_KEY, state.observationsError)
        assertTrue(state.isRefreshing)
    }

    @Test
    fun `se refresca si falta algun dato o tiene mas de una hora`() {
        val recent = Cached(forecast, now.minus(Duration.ofMinutes(30)))
        val old = Cached(forecast, now.minus(Duration.ofMinutes(61)))
        val obsRecent = Cached(
            NearbyObservations(
                WeatherStation("X", "X", null, point, null),
                5.0,
                emptyList(),
            ),
            now.minus(Duration.ofMinutes(10)),
        )
        assertTrue(needsRefresh(RawWeather(null, emptyList(), null), now))
        assertTrue(needsRefresh(RawWeather(recent, emptyList(), null), now))
        assertTrue(needsRefresh(RawWeather(old, emptyList(), obsRecent), now))
        assertFalse(needsRefresh(RawWeather(recent, emptyList(), obsRecent), now))
    }

    @Test
    fun `ultimo nivel del embalse entre registros por hora y por dia`() {
        fun record(period: RecordPeriod, field: ManualField, value: Double) = ManualRecord(
            period = period, location = point, source = "x", origin = ManualOrigin.Typed,
            values = mapOf(field to value), createdAt = now,
        )
        val older = record(RecordPeriod.Day(LocalDate.parse("2026-09-28")), ManualField.RESERVOIR_VOLUME, 4.0)
        val newer = record(RecordPeriod.At(Instant.parse("2026-10-01T08:00:00Z")), ManualField.RESERVOIR_PERCENT, 56.0)
        val noLevel = record(RecordPeriod.At(Instant.parse("2026-10-02T08:00:00Z")), ManualField.WATER_TEMP_SURFACE, 19.0)
        val raw = RawWeather(null, emptyList(), null, manualRecords = listOf(older, noLevel, newer))
        assertEquals(newer, buildConditionsState("Brovales", raw, false, null, now).latestReservoir)
    }

    @Test
    fun `sin ningun dato no hay parametros derivados, con datos manuales si`() {
        assertNull(buildConditionsState("Brovales", RawWeather(null, emptyList(), null), false, null, now).derived)

        val water = ManualRecord(
            period = RecordPeriod.At(now.minus(Duration.ofHours(2))), location = point, source = "termómetro",
            origin = ManualOrigin.Typed, values = mapOf(ManualField.WATER_TEMP_SURFACE to 19.0), createdAt = now,
        )
        val derived = buildConditionsState(
            "Brovales", RawWeather(null, emptyList(), null, manualRecords = listOf(water)), false, null, now,
        ).derived!!
        assertEquals(19.0, derived.water!!.valueC, 1e-9)
        assertTrue(derived.legalToday != null)
    }
}
