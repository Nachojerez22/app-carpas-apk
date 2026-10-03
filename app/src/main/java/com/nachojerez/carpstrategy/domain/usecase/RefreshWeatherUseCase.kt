package com.nachojerez.carpstrategy.domain.usecase

import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.model.RefreshOutcome
import com.nachojerez.carpstrategy.domain.repository.WeatherRepository
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class WeatherRefreshResult(
    val forecast: RefreshOutcome,
    val observations: RefreshOutcome,
)

/** Actualiza en paralelo la previsión multimodelo y las observaciones de AEMET. */
class RefreshWeatherUseCase @Inject constructor(
    private val repository: WeatherRepository,
) {
    suspend operator fun invoke(location: GeoPoint): WeatherRefreshResult = coroutineScope {
        val forecast = async { repository.refreshForecast(location) }
        val observations = async { repository.refreshObservations(location) }
        WeatherRefreshResult(forecast.await(), observations.await())
    }
}
