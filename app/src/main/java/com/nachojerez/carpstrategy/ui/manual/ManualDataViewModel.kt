package com.nachojerez.carpstrategy.ui.manual

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.data.manual.DocumentReader
import com.nachojerez.carpstrategy.data.manual.ImportPreview
import com.nachojerez.carpstrategy.data.manual.ManualDataJson
import com.nachojerez.carpstrategy.di.IoDispatcher
import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.model.DefaultLocation
import com.nachojerez.carpstrategy.domain.repository.ManualDataRepository
import com.nachojerez.carpstrategy.domain.usecase.SaveManualRecordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ImportState {
    data object Idle : ImportState
    data object Reading : ImportState
    data class Preview(val fileName: String, val preview: ImportPreview) : ImportState
    data class ReadError(val message: String) : ImportState
    data class Done(val count: Int) : ImportState
}

data class ManualDataUiState(
    val records: List<ManualRecord> = emptyList(),
    val editor: EditorState? = null,
    val import: ImportState = ImportState.Idle,
    val pendingDelete: ManualRecord? = null,
)

@HiltViewModel
class ManualDataViewModel @Inject constructor(
    private val repository: ManualDataRepository,
    private val saveRecord: SaveManualRecordUseCase,
    private val reader: DocumentReader,
    private val clock: Clock,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val location = DefaultLocation.value.point
    private val editor = MutableStateFlow<EditorState?>(null)
    private val import = MutableStateFlow<ImportState>(ImportState.Idle)
    private val pendingDelete = MutableStateFlow<ManualRecord?>(null)

    val uiState: StateFlow<ManualDataUiState> = combine(
        repository.observeRecords(location),
        editor,
        import,
        pendingDelete,
    ) { records, editor, import, pendingDelete ->
        ManualDataUiState(records, editor, import, pendingDelete)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManualDataUiState())

    // Formulario
    fun newRecord() {
        editor.value = EditorState.new(clock.instant())
    }

    fun edit(record: ManualRecord) {
        editor.value = EditorState.from(record, clock.instant())
    }

    fun updateEditor(transform: (EditorState) -> EditorState) = editor.update { it?.let(transform)?.copy(issues = emptyList()) }

    fun setValue(field: ManualField, text: String) = updateEditor { it.copy(values = it.values + (field to text)) }

    fun cancelEditor() {
        editor.value = null
    }

    fun saveEditor() {
        val current = editor.value ?: return
        viewModelScope.launch {
            val result = saveRecord(current.toRawRecord(), location, existingId = current.id)
            editor.value = if (result.record != null && result.errors.isEmpty()) null else current.copy(issues = result.issues)
        }
    }

    // Borrado con confirmación
    fun askDelete(record: ManualRecord) {
        pendingDelete.value = record
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    fun confirmDelete() {
        val record = pendingDelete.value ?: return
        pendingDelete.value = null
        viewModelScope.launch { repository.delete(record.id) }
    }

    // Importación
    fun onFilePicked(uri: Uri) {
        import.value = ImportState.Reading
        viewModelScope.launch {
            import.value = try {
                val document = withContext(ioDispatcher) { reader.read(uri) }
                val preview = withContext(ioDispatcher) {
                    ManualDataJson.parse(document.text, document.name, location, clock.instant())
                }
                ImportState.Preview(document.name, preview)
            } catch (e: IOException) {
                ImportState.ReadError(e.message.orEmpty())
            } catch (e: SecurityException) {
                ImportState.ReadError(e.message.orEmpty())
            }
        }
    }

    fun confirmImport() {
        val state = import.value as? ImportState.Preview ?: return
        if (!state.preview.canImport) return
        viewModelScope.launch {
            val count = repository.import(state.preview.records)
            import.value = ImportState.Done(count)
        }
    }

    fun dismissImport() {
        import.value = ImportState.Idle
    }

    /** Texto del ejemplo para copiarlo al portapapeles. */
    suspend fun exampleJson(): String = withContext(ioDispatcher) { reader.readExample() }
}
