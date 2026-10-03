package com.nachojerez.carpstrategy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.data.diagnostics.CrashReporter
import com.nachojerez.carpstrategy.di.IoDispatcher
import com.nachojerez.carpstrategy.domain.model.AppearanceSettings
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Apariencia guardada (Lugar → Apariencia), aplicada a toda la app, e informe del último cierre. */
@HiltViewModel
class AppViewModel @Inject constructor(
    settings: SettingsRepository,
    private val crashReporter: CrashReporter,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    val appearance: StateFlow<AppearanceSettings> =
        settings.observeAppearance().stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceSettings())

    private val crash = MutableStateFlow<String?>(null)

    /** Detalle del error que cerró la app la última vez, para que el usuario pueda copiarlo. */
    val lastCrash: StateFlow<String?> = crash

    init {
        viewModelScope.launch { crash.value = withContext(ioDispatcher) { crashReporter.lastReport() } }
    }

    fun dismissCrash() {
        crash.value = null
        viewModelScope.launch { withContext(ioDispatcher) { crashReporter.clear() } }
    }
}
