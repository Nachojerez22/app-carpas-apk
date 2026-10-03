package com.nachojerez.carpstrategy.ui.guided

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class GearViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val gear: StateFlow<List<GearItem>> = settings.observeGear().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(draft: GearDraft) {
        if (!draft.isValid) return
        viewModelScope.launch {
            val current = settings.observeGear().first()
            settings.setGear(current.upsert(draft.toItem { UUID.randomUUID().toString() }))
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            settings.setGear(settings.observeGear().first().filterNot { it.id == id })
        }
    }
}
