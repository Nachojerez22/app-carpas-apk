package com.nachojerez.carpstrategy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.domain.model.AppearanceSettings
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Apariencia guardada (Lugar → Apariencia), aplicada a toda la app. */
@HiltViewModel
class AppViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val appearance: StateFlow<AppearanceSettings> =
        settings.observeAppearance().stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceSettings())
}
