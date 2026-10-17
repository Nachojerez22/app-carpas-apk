package com.nachojerez.carpstrategy.ui.strategy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.data.assistant.AiOutcome
import com.nachojerez.carpstrategy.data.assistant.Assistant
import com.nachojerez.carpstrategy.data.assistant.AssistantJson
import com.nachojerez.carpstrategy.data.assistant.AssistantPrompts
import com.nachojerez.carpstrategy.data.assistant.AssistantSettings
import com.nachojerez.carpstrategy.data.assistant.KnowledgeAsset
import com.nachojerez.carpstrategy.data.reservoir.ReservoirRepository
import com.nachojerez.carpstrategy.data.userdata.GuidedJson
import com.nachojerez.carpstrategy.data.userdata.LocalSettings
import com.nachojerez.carpstrategy.di.IoDispatcher
import com.nachojerez.carpstrategy.domain.advice.Lesson
import com.nachojerez.carpstrategy.domain.advice.LessonStatus
import com.nachojerez.carpstrategy.domain.advice.Lessons
import com.nachojerez.carpstrategy.domain.assistant.AiPlan
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.Spots
import com.nachojerez.carpstrategy.domain.journal.JournalStats
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.repository.JournalRepository
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import com.nachojerez.carpstrategy.domain.reservoir.Reservoir
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirContext
import com.nachojerez.carpstrategy.domain.rules.RulesRepository
import com.nachojerez.carpstrategy.domain.usecase.ObserveRawWeatherUseCase
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.guided.GuidedSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StrategyViewModel @Inject constructor(
    observeRawWeather: ObserveRawWeatherUseCase,
    rulesRepository: RulesRepository,
    private val settings: SettingsRepository,
    private val journal: JournalRepository,
    private val clock: Clock,
    private val assistant: Assistant,
    private val assistantSettings: AssistantSettings,
    private val localSettings: LocalSettings,
    private val knowledge: KnowledgeAsset,
    private val reservoirs: ReservoirRepository,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val location = settings.observeLocation()
    private val rules = flow { emit(rulesRepository.load()) }

    /** Se reevalúa cada 5 minutos: la hora cambia las ventanas y el "ahora es legal". */
    private val ticker = flow {
        while (true) {
            emit(clock.instant())
            delay(5 * 60_000)
        }
    }

    private val adviceInputs = combine(
        combine(settings.observeSpots(), settings.observeGear(), settings.observeLessons(), journal.observeSessions()) { spots, gear, lessons, sessions ->
            AdviceInputs(spots, gear, lessons, sessions)
        },
        reservoirs.observeActive(),
        reservoirs.observeAll(),
    ) { inputs, active, all -> inputs.copy(reservoir = active, reservoirs = all) }

    private val placeAndRaw = location.flatMapLatest { place -> observeRawWeather(place.point).map { place to it } }

    val uiState: StateFlow<StrategyUiState> =
        combine(placeAndRaw, rules, journal.observeSessions(), ticker) { (place, raw), rules, sessions, now ->
            place to buildStrategyState(raw, rules, now, place.point, sessions)
        }.onEach { (place, state) ->
            // Cada valoración se guarda (una por hora) para copiarla en el diario como
            // "lo que dijo la app antes de salir", sin sesgo retrospectivo.
            state.result?.let { result -> viewModelScope.launch { journal.recordPrediction(JournalStats.snapshotOf(result, place.point, state.ruleContext, state.rulesFingerprint)) } }
        }.map { it.second }
            .combine(adviceInputs) { state, inputs ->
                val reservoirId = inputs.reservoir?.reservoir?.id
                val accepted = Lessons.forReservoir(inputs.lessons, reservoirId)
                val spots = Spots.of(inputs.spots, reservoirId)
                state.copy(
                    spots = spots,
                    lessons = accepted,
                    pendingLessons = inputs.lessons.count { it.status == LessonStatus.PROPOSED },
                    reservoir = inputs.reservoir,
                    advice = adviceFor(
                        state.derived, state.result?.evaluatedAt ?: clock.instant(), inputs.sessions, inputs.gear, spots, accepted,
                        inputs.reservoir, inputs.reservoirs,
                    ),
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StrategyUiState())

    private val planFlow = MutableStateFlow(PlanUiState())

    init {
        // El último plan válido de hoy se vuelve a mostrar sin gastar otra consulta.
        viewModelScope.launch {
            val last = GuidedJson.decodeAi(localSettings.get(GuidedSessionManager.KEY_LAST_PLAN)) ?: return@launch
            val today = clock.instant().atZone(Formatting.MADRID).toLocalDate()
            if (!last.valid || last.time.atZone(Formatting.MADRID).toLocalDate() != today) return@launch
            val plan = last.response?.let(AssistantJson::parsePlan) ?: return@launch
            if (planFlow.value.outcome == null) planFlow.value = PlanUiState(outcome = AiOutcome(last, plan))
        }
    }

    /** Plan con IA: solo si hay clave; la respuesta se valida y, si no vale, mandan las reglas. */
    val plan: StateFlow<PlanUiState> = combine(planFlow, assistantSettings.observeKeyHint()) { p, hint -> p.copy(configured = hint != null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    fun requestPlan(rods: Int) {
        if (planFlow.value.running) return
        val state = uiState.value
        viewModelScope.launch {
            val key = assistantSettings.apiKey() ?: return@launch
            val now = clock.instant()
            val gear = settings.observeGear().first()
            val input = planInput(state, rods, gear, now) ?: return@launch
            planFlow.value = PlanUiState(running = true)
            val outcome = withContext(io) {
                assistant.plan(assistantSettings.config(), key, AssistantPrompts.planState(input), planContext(state, rods, gear, now), knowledge.forPlan(state.reservoir?.isBrovales ?: true))
            }
            // Se guarda para copiarlo en la sesión guiada si empieza en las próximas horas.
            localSettings.put(GuidedSessionManager.KEY_LAST_PLAN, GuidedJson.encodeAi(outcome.exchange))
            planFlow.value = PlanUiState(outcome = outcome)
        }
    }
}

private data class AdviceInputs(
    val spots: List<Spot>,
    val gear: List<GearItem>,
    val lessons: List<Lesson>,
    val sessions: List<Session>,
    val reservoir: ReservoirContext? = null,
    val reservoirs: List<Reservoir> = emptyList(),
)

data class PlanUiState(
    val configured: Boolean = false,
    val running: Boolean = false,
    val outcome: AiOutcome<AiPlan>? = null,
)
