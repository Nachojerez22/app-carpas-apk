package com.nachojerez.carpstrategy.ui.reservoir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.data.assistant.Assistant
import com.nachojerez.carpstrategy.data.assistant.AssistantSettings
import com.nachojerez.carpstrategy.data.assistant.KnowledgeAsset
import com.nachojerez.carpstrategy.data.reservoir.ReservoirRepository
import com.nachojerez.carpstrategy.di.IoDispatcher
import com.nachojerez.carpstrategy.domain.assistant.AiExchange
import com.nachojerez.carpstrategy.domain.derived.Geo
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import com.nachojerez.carpstrategy.domain.reservoir.FishSpecies
import com.nachojerez.carpstrategy.domain.reservoir.Reservoir
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirContext
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirSheet
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirSheets
import com.nachojerez.carpstrategy.domain.reservoir.Reservoirs
import com.nachojerez.carpstrategy.ui.place.CoordinateError
import com.nachojerez.carpstrategy.ui.place.CoordinateInput
import com.nachojerez.carpstrategy.ui.place.parseCoordinates
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Un embalse en la lista con su distancia a la ubicación actual. */
data class ReservoirRow(val reservoir: Reservoir, val km: Double)

data class ReservoirsUiState(
    val query: String = "",
    val results: List<ReservoirRow> = emptyList(),
    val total: Int = 0,
    val active: ReservoirContext? = null,
    val here: GeoPoint? = null,
    val aiConfigured: Boolean = false,
    val aiRunning: Boolean = false,
    /** Última consulta de la ficha (error o motivos si no valió). */
    val lastAi: AiExchange? = null,
    val coordinateError: CoordinateError? = null,
)

/** Elegir embalse, añadir uno propio y la ficha del activo (pantallas Embalses y Ficha). */
@HiltViewModel
class ReservoirsViewModel @Inject constructor(
    private val reservoirs: ReservoirRepository,
    settings: SettingsRepository,
    private val assistant: Assistant,
    private val assistantSettings: AssistantSettings,
    private val knowledge: KnowledgeAsset,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val ai = MutableStateFlow(false to (null as AiExchange?))
    private val coordinateError = MutableStateFlow<CoordinateError?>(null)

    val uiState: StateFlow<ReservoirsUiState> = combine(
        combine(query, reservoirs.observeAll(), settings.observeLocation(), ::Triple),
        reservoirs.observeActive(),
        assistantSettings.observeKeyHint(),
        ai,
        coordinateError,
    ) { (q, all, location), active, hint, (running, last), error ->
        val found = Reservoirs.search(all, q, location.point)
        ReservoirsUiState(
            query = q,
            results = found.take(MAX_RESULTS).map { ReservoirRow(it, Geo.haversineKm(location.point, it.point)) },
            total = found.size,
            active = active,
            here = location.point,
            aiConfigured = hint != null,
            aiRunning = running,
            lastAi = last,
            coordinateError = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReservoirsUiState())

    fun setQuery(text: String) {
        query.value = text
    }

    fun select(reservoir: Reservoir) {
        viewModelScope.launch { reservoirs.select(reservoir) }
    }

    /** Embalse propio con las coordenadas tecleadas (o las de la ubicación actual). */
    fun addCustom(name: String, latitude: String, longitude: String): Boolean {
        if (name.isBlank()) return false
        return when (val input = parseCoordinates(latitude, longitude)) {
            is CoordinateInput.Invalid -> {
                coordinateError.value = input.error
                false
            }
            is CoordinateInput.Valid -> {
                coordinateError.value = null
                viewModelScope.launch { reservoirs.addCustom(name, input.point) }
                true
            }
        }
    }

    fun removeCustom(id: String) {
        viewModelScope.launch { reservoirs.removeCustom(id) }
    }

    fun setSpecies(species: FishSpecies, present: Boolean?) = editSheet { r, sheet -> ReservoirSheets.setSpecies(sheet, r.id, species, present, clock.instant()) }

    fun setGroundbait(allowed: Boolean?) = editSheet { r, sheet -> ReservoirSheets.setGroundbait(sheet, r.id, allowed, clock.instant()) }

    fun confirmAll() = editSheet { _, sheet -> sheet?.let { ReservoirSheets.confirmAll(it, clock.instant()) } }

    private fun editSheet(change: (Reservoir, ReservoirSheet?) -> ReservoirSheet?) {
        viewModelScope.launch {
            val active = reservoirs.observeActive().first() ?: return@launch
            change(active.reservoir, active.sheet)?.let { reservoirs.saveSheet(it) }
        }
    }

    /**
     * La IA prepara la ficha con CONOCIMIENTO.md: especies y notas sin confirmar (🟣). Lo que ya
     * confirmaste no se toca. Si la respuesta no vale, la ficha sigue como estaba.
     */
    fun generateSheet() {
        if (ai.value.first) return
        viewModelScope.launch {
            val key = assistantSettings.apiKey() ?: return@launch
            val active = reservoirs.observeActive().first() ?: return@launch
            ai.value = true to null
            val outcome = withContext(io) {
                assistant.sheet(assistantSettings.config(), key, active.reservoir, active.sheet, clock.instant(), knowledge.forPlan(brovales = false))
            }
            outcome.value?.let { reservoirs.saveSheet(it) }
            ai.value = false to outcome.exchange
        }
    }

    private companion object {
        const val MAX_RESULTS = 60
    }
}
