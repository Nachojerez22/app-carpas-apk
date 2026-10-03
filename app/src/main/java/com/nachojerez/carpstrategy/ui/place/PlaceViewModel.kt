package com.nachojerez.carpstrategy.ui.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.data.location.DeviceLocationProvider
import com.nachojerez.carpstrategy.data.location.LocationResult
import com.nachojerez.carpstrategy.domain.model.AppStyle
import com.nachojerez.carpstrategy.domain.model.AppThemeMode
import com.nachojerez.carpstrategy.domain.model.AppearanceSettings
import com.nachojerez.carpstrategy.domain.model.DefaultLocation
import com.nachojerez.carpstrategy.domain.model.FishingLocation
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import com.nachojerez.carpstrategy.domain.rules.RuleLoadResult
import com.nachojerez.carpstrategy.domain.rules.RulesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado de la petición de ubicación al GPS. */
enum class GpsStatus { IDLE, LOCATING, FOUND, PERMISSION_DENIED, UNAVAILABLE }

data class PlaceUiState(
    val location: FishingLocation = DefaultLocation.value,
    val appearance: AppearanceSettings = AppearanceSettings(),
    val gps: GpsStatus = GpsStatus.IDLE,
    val coordinateError: CoordinateError? = null,
    val regulationReviewed: String? = null,
)

@HiltViewModel
class PlaceViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val device: DeviceLocationProvider,
    rulesRepository: RulesRepository,
) : ViewModel() {
    private val gps = MutableStateFlow(GpsStatus.IDLE)
    private val coordinateError = MutableStateFlow<CoordinateError?>(null)
    private val regulation = flow { emit((rulesRepository.load() as? RuleLoadResult.Loaded)?.ruleSet?.regulationReviewed) }

    val uiState: StateFlow<PlaceUiState> = combine(
        settings.observeLocation(),
        settings.observeAppearance(),
        gps,
        coordinateError,
        regulation,
    ) { location, appearance, gps, error, reviewed ->
        PlaceUiState(location, appearance, gps, error, reviewed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaceUiState())

    /** Se llama tras conceder el permiso: una única lectura, sin seguimiento. */
    fun useDeviceLocation(name: String) {
        if (gps.value == GpsStatus.LOCATING) return
        gps.value = GpsStatus.LOCATING
        viewModelScope.launch {
            gps.value = when (val result = device.current()) {
                is LocationResult.Found -> {
                    settings.setLocation(FishingLocation(name, result.point))
                    GpsStatus.FOUND
                }
                LocationResult.PermissionDenied -> GpsStatus.PERMISSION_DENIED
                LocationResult.Unavailable -> GpsStatus.UNAVAILABLE
            }
        }
    }

    fun onPermissionDenied() {
        gps.value = GpsStatus.PERMISSION_DENIED
    }

    fun saveCoordinates(latitude: String, longitude: String, name: String) {
        when (val input = parseCoordinates(latitude, longitude)) {
            is CoordinateInput.Invalid -> coordinateError.value = input.error
            is CoordinateInput.Valid -> {
                coordinateError.value = null
                val current = uiState.value.location.name
                viewModelScope.launch { settings.setLocation(FishingLocation(name.trim().ifBlank { current }, input.point)) }
            }
        }
    }

    fun resetToDefault() {
        coordinateError.value = null
        gps.value = GpsStatus.IDLE
        viewModelScope.launch { settings.setLocation(DefaultLocation.value) }
    }

    fun setStyle(style: AppStyle) = updateAppearance { it.copy(style = style) }

    fun setMode(mode: AppThemeMode) = updateAppearance { it.copy(mode = mode) }

    fun setDynamicColor(enabled: Boolean) = updateAppearance { it.copy(dynamicColor = enabled) }

    private fun updateAppearance(change: (AppearanceSettings) -> AppearanceSettings) {
        viewModelScope.launch { settings.setAppearance(change(settings.observeAppearance().first())) }
    }
}
