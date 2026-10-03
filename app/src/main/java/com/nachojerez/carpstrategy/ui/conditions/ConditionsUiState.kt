package com.nachojerez.carpstrategy.ui.conditions

import com.nachojerez.carpstrategy.domain.derived.Freshness
import com.nachojerez.carpstrategy.domain.derived.FreshnessPolicy
import com.nachojerez.carpstrategy.domain.derived.DerivedCalculator
import com.nachojerez.carpstrategy.domain.derived.DerivedConditions
import com.nachojerez.carpstrategy.domain.manual.DataSource
import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.manual.RecordPeriod
import com.nachojerez.carpstrategy.domain.model.DataError
import com.nachojerez.carpstrategy.domain.model.DefaultLocation
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.model.RefreshOutcome
import com.nachojerez.carpstrategy.domain.usecase.RawWeather
import com.nachojerez.carpstrategy.domain.usecase.WeatherRefreshResult
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

data class ConditionsUiState(
    val locationName: String,
    val now: Instant,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val raw: RawWeather? = null,
    val forecastFreshness: Freshness? = null,
    val observationsFreshness: Freshness? = null,
    val forecastError: DataError? = null,
    val observationsError: DataError? = null,
    /** Horas futuras en las que los modelos discrepan en el viento. */
    val divergentForecastHours: Int = 0,
    /** Registro manual más reciente con nivel del embalse. */
    val latestReservoir: ManualRecord? = null,
    /** Parámetros derivados (fase 3); null si no hay ningún dato. */
    val derived: DerivedConditions? = null,
)

/** Construye el estado de la pantalla. Función pura para poder testearla. */
fun buildConditionsState(
    locationName: String,
    raw: RawWeather,
    refreshing: Boolean,
    lastRefresh: WeatherRefreshResult?,
    now: Instant,
    location: GeoPoint = DefaultLocation.value.point,
) = ConditionsUiState(
    locationName = locationName,
    now = now,
    isLoading = false,
    isRefreshing = refreshing,
    raw = raw,
    forecastFreshness = raw.forecast?.let { FreshnessPolicy.evaluate(it.fetchedAt, now) },
    observationsFreshness = raw.observations?.let { FreshnessPolicy.evaluate(it.fetchedAt, now) },
    forecastError = (lastRefresh?.forecast as? RefreshOutcome.Failure)?.error,
    observationsError = (lastRefresh?.observations as? RefreshOutcome.Failure)?.error,
    divergentForecastHours = raw.ensemble.count { it.windDivergent && !it.time.isBefore(now) },
    latestReservoir = raw.manualRecords
        .filter { record -> RESERVOIR_FIELDS.any { it in record.values } }
        .maxByOrNull { it.period.startInstant() },
    derived = if (raw.merged.isEmpty() && raw.manualRecords.isEmpty()) {
        null
    } else {
        DerivedCalculator.compute(
            merged = raw.merged,
            ensemble = raw.ensemble,
            observations = raw.observations?.data?.observations.orEmpty(),
            stationAltitudeM = raw.observations?.data?.station?.altitudeM,
            gridElevationM = raw.forecast?.data?.elevationM,
            manual = raw.manualRecords,
            location = location,
            now = now,
            zone = Formatting.MADRID,
            manualEnabled = raw.priority.isEnabled(DataSource.MANUAL),
        )
    },
)

private val RESERVOIR_FIELDS = setOf(
    ManualField.RESERVOIR_VOLUME,
    ManualField.RESERVOIR_PERCENT,
    ManualField.RESERVOIR_ELEVATION,
)

/** Inicio del periodo (los días, a las 00:00 de Madrid) para ordenar registros. */
fun RecordPeriod.startInstant(zone: ZoneId = Formatting.MADRID): Instant = when (this) {
    is RecordPeriod.At -> time
    is RecordPeriod.Day -> date.atStartOfDay(zone).toInstant()
}

/** Se descarga de nuevo al abrir la pantalla si falta algún dato o tiene más de 1 h. */
fun needsRefresh(raw: RawWeather, now: Instant, maxAge: Duration = Duration.ofHours(1)): Boolean {
    fun old(fetchedAt: Instant?) = fetchedAt == null || Duration.between(fetchedAt, now) > maxAge
    return old(raw.forecast?.fetchedAt) || old(raw.observations?.fetchedAt)
}
