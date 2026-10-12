package com.nachojerez.carpstrategy.ui.lessons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.data.assistant.AssistantSettings
import com.nachojerez.carpstrategy.domain.advice.Lesson
import com.nachojerez.carpstrategy.domain.advice.LessonConditions
import com.nachojerez.carpstrategy.domain.advice.LessonSource
import com.nachojerez.carpstrategy.domain.advice.LessonStatus
import com.nachojerez.carpstrategy.domain.advice.LessonTopic
import com.nachojerez.carpstrategy.domain.advice.Lessons
import com.nachojerez.carpstrategy.domain.guided.FishingPhase
import com.nachojerez.carpstrategy.domain.repository.JournalRepository
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import com.nachojerez.carpstrategy.domain.rules.Evidence
import com.nachojerez.carpstrategy.ui.guided.GuidedSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Resultado del último repaso pedido desde la pantalla. */
sealed interface ReviewState {
    data object Idle : ReviewState
    data object Running : ReviewState
    data class Done(val added: Int) : ReviewState
    data object Failed : ReviewState
    data object NoSession : ReviewState
}

data class LessonsUiState(
    val proposed: List<Lesson> = emptyList(),
    val accepted: List<Lesson> = emptyList(),
    val aiConfigured: Boolean = false,
    val review: ReviewState = ReviewState.Idle,
)

@HiltViewModel
class LessonsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val journal: JournalRepository,
    private val manager: GuidedSessionManager,
    assistantSettings: AssistantSettings,
    private val clock: Clock,
) : ViewModel() {
    private val review = MutableStateFlow<ReviewState>(ReviewState.Idle)

    val uiState: StateFlow<LessonsUiState> =
        combine(settings.observeLessons(), assistantSettings.observeKeyHint(), review) { lessons, hint, r ->
            LessonsUiState(
                proposed = lessons.filter { it.status == LessonStatus.PROPOSED }.sortedByDescending { it.createdAt },
                accepted = lessons.filter { it.status == LessonStatus.ACCEPTED }.sortedByDescending { it.createdAt },
                aiConfigured = hint != null,
                review = r,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LessonsUiState())

    private fun update(transform: (List<Lesson>) -> List<Lesson>) {
        viewModelScope.launch { settings.setLessons(transform(settings.observeLessons().first())) }
    }

    fun accept(id: String) = update { Lessons.setStatus(it, id, LessonStatus.ACCEPTED) }

    fun reject(id: String) = update { Lessons.setStatus(it, id, LessonStatus.REJECTED) }

    fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    /** Lección escrita por ti: entra ya aceptada. */
    fun add(topic: LessonTopic, text: String, phase: FishingPhase?) {
        val clean = text.trim().take(Lessons.MAX_TEXT)
        if (clean.isEmpty()) return
        update {
            Lessons.addNew(
                it,
                listOf(
                    Lesson(
                        id = UUID.randomUUID().toString(),
                        createdAt = clock.instant(),
                        topic = topic,
                        text = clean,
                        conditions = LessonConditions(phase = phase),
                        evidence = Evidence.PURPLE,
                        source = LessonSource.USER,
                        status = LessonStatus.ACCEPTED,
                    ),
                ),
            )
        }
    }

    /** La IA repasa tu última sesión terminada. */
    fun reviewLast() {
        if (review.value == ReviewState.Running) return
        review.value = ReviewState.Running
        viewModelScope.launch {
            val last = journal.observeSessions().first().filter { it.end != null }.maxByOrNull { it.end!! }
            review.value = when {
                last == null -> ReviewState.NoSession
                else -> manager.reviewSession(last.id)?.let { ReviewState.Done(it) } ?: ReviewState.Failed
            }
        }
    }
}
