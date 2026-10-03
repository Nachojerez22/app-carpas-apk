package com.nachojerez.carpstrategy.ui.diary

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.data.manual.DocumentWriter
import com.nachojerez.carpstrategy.data.userdata.JournalJson
import com.nachojerez.carpstrategy.di.IoDispatcher
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.journal.SessionValidator
import com.nachojerez.carpstrategy.domain.model.DefaultLocation
import com.nachojerez.carpstrategy.domain.model.FishingLocation
import com.nachojerez.carpstrategy.domain.repository.JournalRepository
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import com.nachojerez.carpstrategy.domain.rules.RulesRepository
import com.nachojerez.carpstrategy.domain.usecase.ObserveRawWeatherUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Resultado de exportar el diario, para mostrarlo una vez. */
sealed interface ExportStatus {
    data object Idle : ExportStatus
    data class Done(val count: Int) : ExportStatus
    data class Failed(val message: String) : ExportStatus
}

@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val journal: JournalRepository,
    private val observeRawWeather: ObserveRawWeatherUseCase,
    private val rulesRepository: RulesRepository,
    private val writer: DocumentWriter,
    private val clock: Clock,
    settings: SettingsRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val location: StateFlow<FishingLocation> =
        settings.observeLocation().stateIn(viewModelScope, SharingStarted.Eagerly, DefaultLocation.value)
    private val editor = MutableStateFlow<SessionEditor?>(null)
    private val pendingDelete = MutableStateFlow<Session?>(null)
    private val export = MutableStateFlow<ExportStatus>(ExportStatus.Idle)

    /** Reloj de pantalla: el resumen del mes y la sesión en curso dependen de la hora. */
    private val ticker = flow {
        while (true) {
            emit(clock.instant())
            delay(60_000)
        }
    }

    val uiState: StateFlow<DiaryUiState> =
        combine(journal.observeSessions(), editor, ticker) { sessions, editor, now ->
            buildDiaryState(sessions, now).copy(editor = editor)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiaryUiState())

    val deleteRequest: StateFlow<Session?> = pendingDelete
    val exportStatus: StateFlow<ExportStatus> = export

    fun startNow() {
        editor.value = SessionEditor(SessionForm.startingNow(clock.instant()), original = null)
    }

    fun open(session: Session) {
        editor.value = SessionEditor(SessionForm.from(session), original = session)
    }

    /** Terminar una sesión en curso: hora de fin = ahora y se abre la ficha para el resultado. */
    fun finish(session: Session) {
        val form = SessionForm.from(session)
        val now = SessionForm.startingNow(clock.instant())
        editor.value = SessionEditor(form.copy(endTime = if (now.date == form.date) now.startTime else ""), original = session)
    }

    fun update(transform: (SessionForm) -> SessionForm) =
        editor.update { it?.copy(form = transform(it.form), fieldErrors = emptySet(), issues = emptyList(), warningsAcknowledged = false) }

    fun close() {
        editor.value = null
    }

    /**
     * Guarda si no hay errores. Los avisos (⚖ fuera de horario legal, más de 3 cañas) se
     * muestran primero; si se vuelve a pulsar Guardar, se guarda igualmente: es un registro.
     */
    fun save() {
        val current = editor.value ?: return
        viewModelScope.launch {
            val now = clock.instant()
            val parsed = current.form.toSession(current.original, location.value.point, now)
            val session = parsed.session
            if (session == null) {
                editor.value = current.copy(fieldErrors = parsed.fieldErrors)
                return@launch
            }
            val issues = SessionValidator.validate(session, now, legalWindowsFor(session))
            val blocking = issues.any { it.code.isError }
            if (blocking || (issues.isNotEmpty() && !current.warningsAcknowledged)) {
                editor.value = current.copy(issues = issues, warningsAcknowledged = !blocking)
                return@launch
            }
            val isNew = current.original == null
            val completed = withContext(ioDispatcher) {
                val raw = observeRawWeather(session.location).first()
                val rules = rulesRepository.load()
                val snapshots = if (isNew) journal.predictionsBetween(session.start.minusSeconds(86_400), session.start) else emptyList()
                completeSession(session, isNew, now, raw, rules, snapshots)
            }
            journal.save(completed)
            editor.value = null
        }
    }

    fun askDelete(session: Session) {
        pendingDelete.value = session
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    fun confirmDelete() {
        val session = pendingDelete.value ?: return
        pendingDelete.value = null
        editor.value = null
        viewModelScope.launch { journal.delete(session.id) }
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            export.value = try {
                val sessions = journal.observeSessions().first()
                withContext(ioDispatcher) { writer.write(uri, JournalJson.export(sessions, clock.instant())) }
                ExportStatus.Done(sessions.size)
            } catch (e: IOException) {
                ExportStatus.Failed(e.message.orEmpty())
            } catch (e: SecurityException) {
                ExportStatus.Failed(e.message.orEmpty())
            }
        }
    }

    fun dismissExport() {
        export.value = ExportStatus.Idle
    }
}
