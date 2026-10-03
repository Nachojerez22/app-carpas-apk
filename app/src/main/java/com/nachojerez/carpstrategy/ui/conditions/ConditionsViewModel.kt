package com.nachojerez.carpstrategy.ui.conditions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.domain.model.DefaultLocation
import com.nachojerez.carpstrategy.domain.usecase.ObserveRawWeatherUseCase
import com.nachojerez.carpstrategy.domain.usecase.RefreshWeatherUseCase
import com.nachojerez.carpstrategy.domain.usecase.WeatherRefreshResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ConditionsViewModel @Inject constructor(
    observeRawWeather: ObserveRawWeatherUseCase,
    private val refreshWeather: RefreshWeatherUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val location = DefaultLocation.value
    private val raw = observeRawWeather(location.point)
    private val refreshing = MutableStateFlow(false)
    private val lastRefresh = MutableStateFlow<WeatherRefreshResult?>(null)

    /** Reloj de pantalla para que la antigüedad mostrada se actualice sola. */
    private val minuteTicker = flow {
        while (true) {
            emit(clock.instant())
            delay(60_000)
        }
    }

    val uiState: StateFlow<ConditionsUiState> =
        combine(raw, refreshing, lastRefresh, minuteTicker) { raw, refreshing, result, now ->
            buildConditionsState(location.name, raw, refreshing, result, now)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ConditionsUiState(locationName = location.name, now = clock.instant()),
        )

    init {
        viewModelScope.launch {
            if (needsRefresh(raw.first(), clock.instant())) refresh()
        }
    }

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                lastRefresh.value = refreshWeather(location.point)
            } finally {
                refreshing.value = false
            }
        }
    }
}
