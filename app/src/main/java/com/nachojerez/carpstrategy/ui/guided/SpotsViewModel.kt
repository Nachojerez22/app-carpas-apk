package com.nachojerez.carpstrategy.ui.guided

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.Spots
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** «Mis puestos»: se guardan en el ajuste `spots` (se sincroniza con tu cuenta). */
@HiltViewModel
class SpotsViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val spots: StateFlow<List<Spot>> = settings.observeSpots().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(draft: SpotDraft) {
        if (!draft.isValid) return
        viewModelScope.launch {
            val current = settings.observeSpots().first()
            settings.setSpots(current.upsertSpot(draft.toSpot { Spots.nextId(current) }))
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            settings.setSpots(settings.observeSpots().first().filterNot { it.id == id })
        }
    }
}
