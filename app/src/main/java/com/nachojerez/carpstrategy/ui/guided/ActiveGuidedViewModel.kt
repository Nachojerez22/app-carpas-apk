package com.nachojerez.carpstrategy.ui.guided

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Sesión guiada abierta: desde cuándo y el próximo aviso (null si no hay ninguna). */
data class ActiveGuided(val start: Instant, val nextCheckIn: Instant?)

/**
 * Para la banda «Sesión guiada en curso» de toda la app (revisión de UI, fase 7.28): con una sesión
 * abierta se vuelve a ella desde cualquier pestaña, sin depender de la notificación.
 */
@HiltViewModel
class ActiveGuidedViewModel @Inject constructor(manager: GuidedSessionManager) : ViewModel() {
    val active: StateFlow<ActiveGuided?> =
        combine(manager.observeActive(), manager.nextCheckIn) { session, next ->
            session?.let { ActiveGuided(it.start, next) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
