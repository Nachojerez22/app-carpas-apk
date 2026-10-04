package com.nachojerez.carpstrategy.ui.strategy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.domain.journal.JournalStats
import com.nachojerez.carpstrategy.domain.repository.JournalRepository
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import com.nachojerez.carpstrategy.domain.rules.RulesRepository
import com.nachojerez.carpstrategy.domain.usecase.ObserveRawWeatherUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StrategyViewModel @Inject constructor(
    observeRawWeather: ObserveRawWeatherUseCase,
    rulesRepository: RulesRepository,
    settings: SettingsRepository,
    private val journal: JournalRepository,
    clock: Clock,
) : ViewModel() {
    private val location = settings.observeLocation()
    private val rules = flow { emit(rulesRepository.load()) }

    /** Se reevalúa cada 5 minutos: la hora cambia las ventanas y el "ahora es legal". */
    private val ticker = flow {
        while (true) {
            emit(clock.instant())
            delay(5 * 60_000)
        }
    }

    private val placeAndRaw = location.flatMapLatest { place -> observeRawWeather(place.point).map { place to it } }

    val uiState: StateFlow<StrategyUiState> =
        combine(placeAndRaw, rules, journal.observeSessions(), ticker) { (place, raw), rules, sessions, now ->
            place to buildStrategyState(raw, rules, now, place.point, sessions)
        }.onEach { (place, state) ->
            // Cada valoración se guarda (una por hora) para copiarla en el diario como
            // "lo que dijo la app antes de salir", sin sesgo retrospectivo.
            state.result?.let { result -> viewModelScope.launch { journal.recordPrediction(JournalStats.snapshotOf(result, place.point, state.ruleContext, state.rulesFingerprint)) } }
        }.map { it.second }
            .combine(settings.observeSpots()) { state, spots -> state.copy(spots = spots) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StrategyUiState())
}
