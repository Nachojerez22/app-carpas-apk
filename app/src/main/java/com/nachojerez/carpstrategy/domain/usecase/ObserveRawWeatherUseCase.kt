package com.nachojerez.carpstrategy.domain.usecase

import com.nachojerez.carpstrategy.domain.derived.EnsembleHour
import com.nachojerez.carpstrategy.domain.derived.ModelEnsemble
import com.nachojerez.carpstrategy.domain.model.Cached
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.model.MultiModelForecast
import com.nachojerez.carpstrategy.domain.model.NearbyObservations
import com.nachojerez.carpstrategy.domain.repository.WeatherRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Datos en bruto en caché más la combinación entre modelos (media y divergencia). */
data class RawWeather(
    val forecast: Cached<MultiModelForecast>?,
    val ensemble: List<EnsembleHour>,
    val observations: Cached<NearbyObservations>?,
)

class ObserveRawWeatherUseCase @Inject constructor(
    private val repository: WeatherRepository,
) {
    operator fun invoke(location: GeoPoint): Flow<RawWeather> = combine(
        repository.observeForecast(location),
        repository.observeObservations(location),
    ) { forecast, observations ->
        RawWeather(
            forecast = forecast,
            ensemble = forecast?.data?.series?.let(ModelEnsemble::combine).orEmpty(),
            observations = observations,
        )
    }
}
