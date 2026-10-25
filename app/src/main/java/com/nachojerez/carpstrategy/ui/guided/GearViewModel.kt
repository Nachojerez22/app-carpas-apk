package com.nachojerez.carpstrategy.ui.guided

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class GearViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val editor: GearEditor,
) : ViewModel() {
    val gear: StateFlow<List<GearItem>> = settings.observeGear().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tu cebado «normal» en kg por caña y sesión (fase 7.13). */
    val groundbaitNormalKg: StateFlow<Double?> = settings.observeGroundbaitNormalKg().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Fichas hechas sin búsqueda que ya se pueden rehacer (fase 7.15). */
    val pendingSheets: StateFlow<Int> = editor.observePendingSheets().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val refreshing = MutableStateFlow<PendingRefresh>(PendingRefresh.Idle)
    val pendingRefresh: StateFlow<PendingRefresh> = refreshing.asStateFlow()

    fun refreshPendingSheets() {
        if (refreshing.value == PendingRefresh.Running) return
        refreshing.value = PendingRefresh.Running
        viewModelScope.launch { refreshing.value = PendingRefresh.Done(editor.refreshPendingSheets()) }
    }

    private val sheet = MutableStateFlow(SheetUiState())
    val sheetState: StateFlow<SheetUiState> = sheet.asStateFlow()

    fun save(draft: GearDraft) {
        viewModelScope.launch { editor.save(draft) }
    }

    fun delete(id: String) {
        viewModelScope.launch { editor.delete(id) }
    }

    fun toggleToday(id: String) {
        viewModelScope.launch { editor.toggleToday(id) }
    }

    fun setGroundbaitNormalKg(kg: Double?) {
        viewModelScope.launch { settings.setGroundbaitNormalKg(kg) }
    }

    /** Ficha del cebo con la IA; [onDone] recibe el borrador con la ficha para seguir editando. */
    fun fetchSheet(draft: GearDraft, onDone: (GearDraft) -> Unit) {
        if (sheet.value.running) return
        sheet.value = SheetUiState(running = true)
        viewModelScope.launch {
            val result = editor.fetchSheet(draft)
            sheet.value = SheetUiState(result = result)
            if (result is BaitSheetResult.Ok) onDone(GearDraft.of(result.item))
        }
    }

    fun clearSheetState() {
        sheet.value = SheetUiState()
    }
}

/** Rehacer las fichas pendientes: en marcha o hecho (cuántas salieron con búsqueda o enlace). */
sealed interface PendingRefresh {
    data object Idle : PendingRefresh
    data object Running : PendingRefresh
    data class Done(val improved: Int) : PendingRefresh
}

/** Estado de la consulta de la ficha de un cebo. */
data class SheetUiState(val running: Boolean = false, val result: BaitSheetResult? = null)
