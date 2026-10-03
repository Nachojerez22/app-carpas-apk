package com.nachojerez.carpstrategy.domain.usecase

import com.nachojerez.carpstrategy.domain.derived.EnsembleHour
import com.nachojerez.carpstrategy.domain.derived.MergedHour
import com.nachojerez.carpstrategy.domain.derived.ModelEnsemble
import com.nachojerez.carpstrategy.domain.derived.SourceMerger
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.manual.SourcePriority
import com.nachojerez.carpstrategy.domain.model.Cached
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.model.MultiModelForecast
import com.nachojerez.carpstrategy.domain.model.NearbyObservations
import com.nachojerez.carpstrategy.domain.repository.ManualDataRepository
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import com.nachojerez.carpstrategy.domain.repository.WeatherRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Datos en caché, combinación entre modelos (media y divergencia) y serie combinada de todas las
 * fuentes según la prioridad elegida por el usuario.
 */
data class RawWeather(
    val forecast: Cached<MultiModelForecast>?,
    val ensemble: List<EnsembleHour>,
    val observations: Cached<NearbyObservations>?,
    val manualRecords: List<ManualRecord> = emptyList(),
    val priority: SourcePriority = SourcePriority.DEFAULT,
    val merged: List<MergedHour> = emptyList(),
)

class ObserveRawWeatherUseCase @Inject constructor(
    private val weather: WeatherRepository,
    private val manualData: ManualDataRepository,
    private val settings: SettingsRepository,
) {
    operator fun invoke(location: GeoPoint): Flow<RawWeather> = combine(
        weather.observeForecast(location),
        weather.observeObservations(location),
        manualData.observeRecords(location),
        settings.observeSourcePriority(),
    ) { forecast, observations, manual, priority ->
        val ensemble = forecast?.data?.series?.let(ModelEnsemble::combine).orEmpty()
        RawWeather(
            forecast = forecast,
            ensemble = ensemble,
            observations = observations,
            manualRecords = manual,
            priority = priority,
            merged = SourceMerger.merge(
                ensemble = ensemble,
                observations = observations?.data?.observations.orEmpty(),
                manual = manual,
                priority = priority,
            ),
        )
    }
}
