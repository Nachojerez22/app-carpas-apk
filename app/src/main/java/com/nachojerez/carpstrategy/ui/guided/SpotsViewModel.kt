package com.nachojerez.carpstrategy.ui.guided

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.data.reservoir.ReservoirRepository
import com.nachojerez.carpstrategy.domain.guided.MapWind
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.Spots
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import com.nachojerez.carpstrategy.domain.rules.RulesRepository
import com.nachojerez.carpstrategy.domain.usecase.ObserveRawWeatherUseCase
import com.nachojerez.carpstrategy.ui.strategy.buildStrategyState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * «Mis puestos» del embalse activo: se guardan en el ajuste `spots` (se sincroniza con tu
 * cuenta) con el embalse al que pertenecen. También alimenta el mapa de puestos (fase 7.9).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SpotsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val reservoirs: ReservoirRepository,
    observeRawWeather: ObserveRawWeatherUseCase,
    rulesRepository: RulesRepository,
    private val clock: Clock,
) : ViewModel() {
    private val active = reservoirs.observeActive()

    val spots: StateFlow<List<Spot>> = combine(settings.observeSpots(), active) { spots, r -> Spots.of(spots, r?.reservoir?.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Nombre del embalse activo (null si la ubicación no es de ningún embalse conocido). */
    val reservoirName: StateFlow<String?> = active.map { it?.reservoir?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Centro del mapa: el embalse activo o, si no hay, la ubicación de Lugar. */
    val center: StateFlow<GeoPoint?> = combine(active, settings.observeLocation()) { r, location -> r?.reservoir?.point ?: location.point }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val rules = flow { emit(rulesRepository.load()) }

    /** Viento de las últimas 24 h, aire y agua (los mismos para todo el embalse). */
    val wind: StateFlow<MapWind?> = settings.observeLocation()
        .flatMapLatest { place -> observeRawWeather(place.point).map { place.point to it } }
        .combine(rules) { (point, raw), rules ->
            val derived = buildStrategyState(raw, rules, clock.instant(), point).derived ?: return@combine null
            MapWind(
                fromDeg = derived.wind24h?.dominantDirectionDeg,
                kmh = derived.wind24h?.meanSpeedKmh,
                persistence = derived.wind24h?.persistence,
                airC = derived.airTrend?.last24hMeanC,
                waterC = derived.water?.valueC,
                gustKmh = derived.wind24h?.maxGustKmh,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(draft: SpotDraft) {
        if (!draft.isValid) return
        viewModelScope.launch {
            val current = settings.observeSpots().first()
            val withReservoir = draft.copy(reservoirId = draft.reservoirId ?: active.first()?.reservoir?.id)
            settings.setSpots(current.upsertSpot(withReservoir.toSpot { Spots.nextId(current) }))
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            settings.setSpots(settings.observeSpots().first().filterNot { it.id == id })
        }
    }
}
