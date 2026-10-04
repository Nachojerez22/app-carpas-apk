package com.nachojerez.carpstrategy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.BuildConfig
import com.nachojerez.carpstrategy.data.diagnostics.CrashReporter
import com.nachojerez.carpstrategy.data.sync.SyncManager
import com.nachojerez.carpstrategy.data.update.AvailableUpdate
import com.nachojerez.carpstrategy.data.update.UpdateChecker
import com.nachojerez.carpstrategy.data.userdata.LocalSettings
import com.nachojerez.carpstrategy.di.IoDispatcher
import com.nachojerez.carpstrategy.domain.model.AppearanceSettings
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Apariencia guardada (Lugar → Apariencia), aplicada a toda la app, informe del último cierre y
 * aviso de versión nueva publicada en GitHub.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    settings: SettingsRepository,
    private val crashReporter: CrashReporter,
    private val updateChecker: UpdateChecker,
    private val localSettings: LocalSettings,
    private val syncManager: SyncManager,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    val appearance: StateFlow<AppearanceSettings> =
        settings.observeAppearance().stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceSettings())

    private val crash = MutableStateFlow<String?>(null)

    /** Detalle del error que cerró la app la última vez, para que el usuario pueda copiarlo. */
    val lastCrash: StateFlow<String?> = crash

    private val availableUpdate = MutableStateFlow<AvailableUpdate?>(null)

    /** Versión nueva sin descartar; null si no hay (o en la versión de prueba). */
    val update: StateFlow<AvailableUpdate?> = availableUpdate

    /** Hay cuenta de Google: los datos están en Drive antes de actualizar. */
    val syncEnabled: StateFlow<Boolean> = syncManager.state.map { it.enabled }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch { crash.value = withContext(ioDispatcher) { crashReporter.lastReport() } }
        // La versión de prueba no se actualiza desde Releases (otro paquete).
        if (!BuildConfig.DEBUG) {
            viewModelScope.launch {
                val found = withContext(ioDispatcher) { updateChecker.check(BuildConfig.VERSION_CODE) }
                val dismissed = localSettings.get(KEY_UPDATE_DISMISSED)?.toIntOrNull()
                if (found != null && found.versionCode != dismissed) {
                    availableUpdate.value = found
                    // Antes de descargar, que lo último esté ya en Drive.
                    syncManager.syncNow()
                }
            }
        }
    }

    fun dismissUpdate() {
        val update = availableUpdate.value ?: return
        availableUpdate.value = null
        viewModelScope.launch { localSettings.put(KEY_UPDATE_DISMISSED, update.versionCode.toString()) }
    }

    fun updateOpened() {
        availableUpdate.value = null
    }

    fun dismissCrash() {
        crash.value = null
        viewModelScope.launch { withContext(ioDispatcher) { crashReporter.clear() } }
    }

    companion object {
        private const val KEY_UPDATE_DISMISSED = "update_dismissed"
    }
}
