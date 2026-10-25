package com.nachojerez.carpstrategy.ui.guided

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.data.assistant.AssistantSettings
import com.nachojerez.carpstrategy.data.reservoir.ReservoirRepository
import com.nachojerez.carpstrategy.data.userdata.LocalSettings
import com.nachojerez.carpstrategy.domain.guided.FieldCondition
import com.nachojerez.carpstrategy.domain.guided.GroundbaitLevel
import com.nachojerez.carpstrategy.domain.guided.GuidedSessions
import com.nachojerez.carpstrategy.domain.guided.RejectReason
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.Spots
import com.nachojerez.carpstrategy.domain.guided.WeatherQuestion
import com.nachojerez.carpstrategy.domain.guided.today
import com.nachojerez.carpstrategy.domain.journal.FishingZone
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.strategy.KEY_RODS
import com.nachojerez.carpstrategy.ui.strategy.chosenSpotKey
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GuidedViewModel @Inject constructor(
    private val manager: GuidedSessionManager,
    settings: SettingsRepository,
    reservoirs: ReservoirRepository,
    assistantSettings: AssistantSettings,
    localSettings: LocalSettings,
    private val clock: Clock,
) : ViewModel() {
    private val starting = MutableStateFlow(false)
    private val saved = MutableStateFlow<Instant?>(null)
    private val permissions = MutableStateFlow(manager.canNotify() to manager.canScheduleExact())

    /** Reloj de pantalla; de paso, reevalúa (las propuestas por tiempo aparecen solas). */
    private val ticker = flow {
        while (true) {
            emit(clock.instant())
            delay(30_000)
        }
    }

    init {
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                manager.refresh()
            }
        }
    }

    /** Puestos del embalse activo, el puesto y las cañas elegidos en Estrategia (fase 7.12). */
    private data class StartPrefs(val spots: List<Spot>, val spotId: Long?, val rods: Int?)

    /** Puestos del embalse activo y el elegido en Estrategia (viene marcado al empezar, fase 7.10). */
    private val spotsAndPreferred = reservoirs.observeActive().flatMapLatest { r ->
        val reservoirId = r?.reservoir?.id
        combine(settings.observeSpots(), localSettings.observe(chosenSpotKey(reservoirId)), localSettings.observe(KEY_RODS)) { all, chosen, rods ->
            val spots = Spots.of(all, reservoirId)
            StartPrefs(spots, chosen?.toLongOrNull()?.takeIf { id -> spots.any { it.id == id } }, rods?.toIntOrNull())
        }
    }

    val uiState: StateFlow<GuidedUiState> =
        combine(
            manager.observeActive(),
            combine(settings.observeGear().map { it.today() }, spotsAndPreferred, ::Pair),
            manager.nextCheckIn,
            ticker,
            combine(starting, saved, permissions, assistantSettings.observeKeyHint(), combine(manager.aiBusy, manager.chatBusy, ::Pair)) { st, sv, pm, hint, (ai, chat) ->
                AuxState(st, sv, pm, hint, ai, chat)
            },
        ) { session, (gear, prefs), next, now, aux ->
            val (isStarting, lastSaved, perms) = Triple(aux.starting, aux.saved, aux.permissions)
            buildGuidedState(session, gear, now, next, Formatting.MADRID, prefs.spots).copy(
                preferredSpotId = prefs.spotId,
                preferredRods = prefs.rods?.coerceIn(1, GuidedSessions.MAX_RODS),
                aiConfigured = aux.aiKeyHint != null,
                aiBusy = aux.aiBusy,
                chatBusy = aux.chatBusy,
                starting = isStarting,
                lastSaved = lastSaved,
                canNotify = perms.first,
                exactAlarms = perms.second,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuidedUiState())

    /** Al volver de los ajustes del sistema o del diálogo de permisos. */
    fun recheckPermissions() {
        permissions.value = manager.canNotify() to manager.canScheduleExact()
    }

    fun start(rodNames: List<String>, zone: FishingZone?, groundbait: GroundbaitLevel?, spot: Spot?) {
        if (starting.value) return
        starting.value = true
        viewModelScope.launch {
            try {
                manager.start(rodNames, zone, groundbait, spot)
            } finally {
                starting.value = false
            }
        }
    }

    fun checkIn(rodId: Int, draft: CheckInDraft) {
        viewModelScope.launch {
            manager.checkIn(rodId) { now -> draft.toCheckIn(now) }
            saved.value = clock.instant()
        }
    }

    fun nothingEverywhere() {
        viewModelScope.launch {
            manager.nothingEverywhere()
            saved.value = clock.instant()
        }
    }

    fun windChanged() {
        viewModelScope.launch {
            manager.windChanged()
            saved.value = clock.instant()
        }
    }

    fun conditionChanged(condition: FieldCondition, active: Boolean) {
        viewModelScope.launch {
            manager.conditionChanged(condition, active)
            saved.value = clock.instant()
        }
    }

    fun answerWeather(question: WeatherQuestion, answer: Boolean, heavy: Boolean = false) {
        viewModelScope.launch {
            manager.answerWeather(question, answer, heavy)
            saved.value = clock.instant()
        }
    }

    fun askAi() {
        viewModelScope.launch { manager.consultAi() }
    }

    /** Mensaje para la IA en el chat de la sesión. */
    fun sendChat(text: String) {
        viewModelScope.launch { manager.chat(text) }
    }

    fun accept(rodId: Int) {
        viewModelScope.launch { manager.accept(rodId) }
    }

    fun reject(rodId: Int, reason: RejectReason, comment: String) {
        viewModelScope.launch { manager.reject(rodId, reason, comment) }
    }

    fun finish(onFinished: (Long) -> Unit) {
        viewModelScope.launch { manager.finish()?.let(onFinished) }
    }
}

private data class AuxState(
    val starting: Boolean,
    val saved: Instant?,
    val permissions: Pair<Boolean, Boolean>,
    val aiKeyHint: String?,
    val aiBusy: Boolean,
    val chatBusy: Boolean,
)
