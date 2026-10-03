package com.nachojerez.carpstrategy.ui.conditions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.domain.manual.DataSource
import com.nachojerez.carpstrategy.domain.manual.SourcePriority
import com.nachojerez.carpstrategy.domain.model.DefaultLocation
import com.nachojerez.carpstrategy.domain.model.FishingLocation
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import com.nachojerez.carpstrategy.domain.usecase.ObserveRawWeatherUseCase
import com.nachojerez.carpstrategy.domain.usecase.RefreshWeatherUseCase
import com.nachojerez.carpstrategy.domain.usecase.WeatherRefreshResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConditionsViewModel @Inject constructor(
    private val observeRawWeather: ObserveRawWeatherUseCase,
    private val refreshWeather: RefreshWeatherUseCase,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    /** Ubicación elegida en Lugar (por defecto, Brovales). */
    private val location: StateFlow<FishingLocation> =
        settings.observeLocation().stateIn(viewModelScope, SharingStarted.Eagerly, DefaultLocation.value)
    private val raw = location.flatMapLatest { observeRawWeather(it.point) }
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
        combine(location, raw, refreshing, lastRefresh, minuteTicker) { place, raw, refreshing, result, now ->
            buildConditionsState(place.name, raw, refreshing, result, now, place.point)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ConditionsUiState(locationName = DefaultLocation.value.name, now = clock.instant()),
        )

    init {
        // Al abrir y al cambiar de ubicación: descargar si faltan datos o son de hace más de 1 h.
        viewModelScope.launch {
            location.collectLatest { place ->
                lastRefresh.value = null
                if (needsRefresh(observeRawWeather(place.point).first(), clock.instant())) refresh()
            }
        }
    }

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                lastRefresh.value = refreshWeather(location.value.point)
            } finally {
                refreshing.value = false
            }
        }
    }

    fun movePriorityUp(source: DataSource) = updatePriority { it.moveUp(source) }

    fun movePriorityDown(source: DataSource) = updatePriority { it.moveDown(source) }

    fun togglePriority(source: DataSource) = updatePriority { it.toggle(source) }

    private fun updatePriority(change: (SourcePriority) -> SourcePriority) {
        val current = uiState.value.raw?.priority ?: return
        viewModelScope.launch { settings.setSourcePriority(change(current)) }
    }
}
