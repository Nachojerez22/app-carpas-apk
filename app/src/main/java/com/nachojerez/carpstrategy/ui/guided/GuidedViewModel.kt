package com.nachojerez.carpstrategy.ui.guided

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.domain.guided.GroundbaitLevel
import com.nachojerez.carpstrategy.domain.guided.RejectReason
import com.nachojerez.carpstrategy.domain.journal.FishingZone
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class GuidedViewModel @Inject constructor(
    private val manager: GuidedSessionManager,
    settings: SettingsRepository,
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

    val uiState: StateFlow<GuidedUiState> =
        combine(manager.observeActive(), settings.observeGear(), manager.nextCheckIn, ticker, combine(starting, saved, permissions, ::Triple)) { session, gear, next, now, (isStarting, lastSaved, perms) ->
            buildGuidedState(session, gear, now, next, Formatting.MADRID).copy(
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

    fun start(rodNames: List<String>, zone: FishingZone?, groundbait: GroundbaitLevel?) {
        if (starting.value) return
        starting.value = true
        viewModelScope.launch {
            try {
                manager.start(rodNames, zone, groundbait)
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
