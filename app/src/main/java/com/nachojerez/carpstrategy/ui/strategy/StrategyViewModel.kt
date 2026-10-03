package com.nachojerez.carpstrategy.ui.strategy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.domain.model.DefaultLocation
import com.nachojerez.carpstrategy.domain.rules.RulesRepository
import com.nachojerez.carpstrategy.domain.usecase.ObserveRawWeatherUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class StrategyViewModel @Inject constructor(
    observeRawWeather: ObserveRawWeatherUseCase,
    rulesRepository: RulesRepository,
    clock: Clock,
) : ViewModel() {
    private val location = DefaultLocation.value.point
    private val rules = flow { emit(rulesRepository.load()) }

    /** Se reevalúa cada 5 minutos: la hora cambia las ventanas y el "ahora es legal". */
    private val ticker = flow {
        while (true) {
            emit(clock.instant())
            delay(5 * 60_000)
        }
    }

    val uiState: StateFlow<StrategyUiState> =
        combine(observeRawWeather(location), rules, ticker) { raw, rules, now ->
            buildStrategyState(raw, rules, now, location)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StrategyUiState())
}
