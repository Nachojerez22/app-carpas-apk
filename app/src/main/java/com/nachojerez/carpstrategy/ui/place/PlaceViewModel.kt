package com.nachojerez.carpstrategy.ui.place

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import com.nachojerez.carpstrategy.ui.strategy.chosenSpotKey
import com.nachojerez.carpstrategy.ui.strategy.PreparedOutingJson
import com.nachojerez.carpstrategy.ui.strategy.PreparedOuting
import com.nachojerez.carpstrategy.ui.strategy.PrepUiState
import com.nachojerez.carpstrategy.ui.strategy.OutingPreparer
import com.nachojerez.carpstrategy.ui.strategy.OutingPicker
import com.nachojerez.carpstrategy.ui.strategy.chooseOuting
import com.nachojerez.carpstrategy.ui.strategy.nextOuting
import com.nachojerez.carpstrategy.ui.strategy.outingDays
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import com.nachojerez.carpstrategy.ui.strategy.KEY_PREPARED_OUTING
import com.nachojerez.carpstrategy.domain.guided.Spots
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.data.userdata.LocalSettings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.data.location.DeviceLocationProvider
import com.nachojerez.carpstrategy.data.location.LocationResult
import com.nachojerez.carpstrategy.data.reservoir.ReservoirRepository
import com.nachojerez.carpstrategy.domain.model.AppStyle
import com.nachojerez.carpstrategy.domain.model.AppThemeMode
import com.nachojerez.carpstrategy.domain.model.AppearanceSettings
import com.nachojerez.carpstrategy.domain.model.DefaultLocation
import com.nachojerez.carpstrategy.domain.model.FishingLocation
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirContext
import com.nachojerez.carpstrategy.domain.rules.RuleLoadResult
import com.nachojerez.carpstrategy.domain.rules.RulesRepository
import com.nachojerez.carpstrategy.ui.outlook.OutlookScheduler
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
    /** Embalse de la ubicación (fase 7.6). */
    val reservoir: ReservoirContext? = null,
    /** Aviso «Mañana pinta bien» activado en este móvil (fase 7.17). */
    val outlookEnabled: Boolean = false,
    /** Puestos del embalse activo y el elegido (null = automático), fase 7.25. */
    val spots: List<Spot> = emptyList(),
    val chosenSpotId: Long? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaceViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val device: DeviceLocationProvider,
    rulesRepository: RulesRepository,
    private val reservoirs: ReservoirRepository,
    private val outlook: OutlookScheduler,
    private val localSettings: LocalSettings,
    private val preparer: OutingPreparer,
    private val clock: Clock,
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
    }.combine(reservoirs.observeActive()) { state, reservoir -> state.copy(reservoir = reservoir) }
        .combine(outlook.observeEnabled()) { state, enabled -> state.copy(outlookEnabled = enabled) }
        .combine(chosenSpot()) { state, (spots, chosen) -> state.copy(spots = spots, chosenSpotId = chosen?.takeIf { id -> spots.any { it.id == id } }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaceUiState())

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

    /** Puestos del embalse activo y el elegido para él (el mismo ajuste que usa Estrategia). */
    private fun chosenSpot() = combine(reservoirs.observeActive(), settings.observeSpots()) { r, all -> r?.reservoir?.id to Spots.of(all, r?.reservoir?.id) }
        .flatMapLatest { (id, spots) -> localSettings.observe(chosenSpotKey(id)).map { spots to it?.toLongOrNull() } }

    /** Elige el puesto (null = automático: la app o la IA lo eligen). */
    fun chooseSpot(id: Long?) {
        viewModelScope.launch {
            val reservoirId = reservoirs.observeActive().first()?.reservoir?.id
            localSettings.put(chosenSpotKey(reservoirId), id?.toString())
        }
    }

    /** «Preparar la próxima salida» (fase 7.25: se pulsa aquí; Estrategia lee lo que sale). */
    val prep: StateFlow<PrepUiState> = preparer.state

    val prepared: StateFlow<PreparedOuting?> = localSettings.observe(KEY_PREPARED_OUTING).map(PreparedOutingJson::decode)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun prepareNextOuting() = preparer.prepare()

    /** Cada minuto: las horas que ya pasaron dejan de ofrecerse. */
    private val ticker = flow {
        while (true) {
            emit(clock.instant())
            delay(60_000)
        }
    }

    /** Día y hora de la salida que se prepara (fase 7.27). */
    val outing: StateFlow<OutingPicker> = combine(settings.observeLocation(), preparer.chosen, ticker) { place, chosen, now ->
        val days = outingDays(now, place.point, Formatting.MADRID)
        OutingPicker(days, chooseOuting(chosen, days, nextOuting(now, place.point, Formatting.MADRID)), now.atZone(Formatting.MADRID).toLocalDate())
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OutingPicker())

    fun selectOuting(target: Instant) = preparer.choose(target)

    fun setOutlook(enabled: Boolean) {
        viewModelScope.launch { outlook.setEnabled(enabled) }
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
