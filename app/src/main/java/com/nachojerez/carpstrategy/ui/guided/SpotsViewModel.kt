package com.nachojerez.carpstrategy.ui.guided

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.data.reservoir.ReservoirRepository
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.Spots
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * «Mis puestos» del embalse activo: se guardan en el ajuste `spots` (se sincroniza con tu
 * cuenta) con el embalse al que pertenecen.
 */
@HiltViewModel
class SpotsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val reservoirs: ReservoirRepository,
) : ViewModel() {
    private val active = reservoirs.observeActive()

    val spots: StateFlow<List<Spot>> = combine(settings.observeSpots(), active) { spots, r -> Spots.of(spots, r?.reservoir?.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Nombre del embalse activo (null si la ubicación no es de ningún embalse conocido). */
    val reservoirName: StateFlow<String?> = active.map { it?.reservoir?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(draft: SpotDraft) {
        if (!draft.isValid) return
        viewModelScope.launch {
            val current = settings.observeSpots().first()
            val withReservoir = draft.copy(reservoirId = draft.reservoirId ?: active.first()?.reservoir?.id)
            settings.setSpots(current.upsertSpot(withReservoir.toSpot { Spots.nextId(current) }))
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            settings.setSpots(settings.observeSpots().first().filterNot { it.id == id })
        }
    }
}
