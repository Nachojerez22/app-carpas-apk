package com.nachojerez.carpstrategy.ui.conditions

import com.nachojerez.carpstrategy.domain.derived.Freshness
import com.nachojerez.carpstrategy.domain.derived.FreshnessPolicy
import com.nachojerez.carpstrategy.domain.model.DataError
import com.nachojerez.carpstrategy.domain.model.RefreshOutcome
import com.nachojerez.carpstrategy.domain.usecase.RawWeather
import com.nachojerez.carpstrategy.domain.usecase.WeatherRefreshResult
import java.time.Duration
import java.time.Instant

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
)

/** Construye el estado de la pantalla. Función pura para poder testearla. */
fun buildConditionsState(
    locationName: String,
    raw: RawWeather,
    refreshing: Boolean,
    lastRefresh: WeatherRefreshResult?,
    now: Instant,
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
)

/** Se descarga de nuevo al abrir la pantalla si falta algún dato o tiene más de 1 h. */
fun needsRefresh(raw: RawWeather, now: Instant, maxAge: Duration = Duration.ofHours(1)): Boolean {
    fun old(fetchedAt: Instant?) = fetchedAt == null || Duration.between(fetchedAt, now) > maxAge
    return old(raw.forecast?.fetchedAt) || old(raw.observations?.fetchedAt)
}
