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
import com.nachojerez.carpstrategy.domain.advice.DayPlans
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
import com.nachojerez.carpstrategy.domain.reservoir.Reservoirs
import com.nachojerez.carpstrategy.domain.rules.RulesRepository
import com.nachojerez.carpstrategy.domain.usecase.ObserveRawWeatherUseCase
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.guided.GuidedSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
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

    /** Puesto elegido para el embalse activo (se recuerda por embalse, solo en este móvil). */
    private val chosenSpot = reservoirs.observeActive().map { it?.reservoir?.id }.distinctUntilChanged()
        .flatMapLatest { id -> localSettings.observe(chosenSpotKey(id)).map { it?.toLongOrNull() } }

    private val rods = localSettings.observe(KEY_RODS).map { it?.toIntOrNull()?.coerceIn(1, DayPlans.MAX_RODS) ?: DEFAULT_RODS }

    private val adviceInputs = combine(
        combine(settings.observeSpots(), settings.observeGear(), settings.observeLessons(), journal.observeSessions()) { spots, gear, lessons, sessions ->
            AdviceInputs(spots, gear, lessons, sessions)
        },
        reservoirs.observeActive(),
        reservoirs.observeAll(),
        chosenSpot,
        rods,
    ) { inputs, active, all, chosen, n -> inputs.copy(reservoir = active, reservoirs = all, chosenSpotId = chosen, rods = n) }

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
                // Un puesto borrado o de otro embalse vuelve a «automático».
                val chosenId = inputs.chosenSpotId?.takeIf { id -> spots.any { it.id == id } }
                state.copy(
                    spots = spots,
                    chosenSpotId = chosenId,
                    rods = inputs.rods,
                    gear = inputs.gear,
                    myReservoirs = Reservoirs.withSpots(inputs.spots, inputs.reservoirs, inputs.reservoir?.reservoir),
                    lessons = accepted,
                    pendingLessons = inputs.lessons.count { it.status == LessonStatus.PROPOSED },
                    reservoir = inputs.reservoir,
                    advice = adviceFor(
                        state.derived, state.result?.evaluatedAt ?: clock.instant(), inputs.sessions, inputs.gear, spots, accepted,
                        inputs.reservoir, inputs.reservoirs, chosenId,
                    ),
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StrategyUiState())

    private val planFlow = MutableStateFlow(PlanUiState())

    /** Cambia de embalse desde Estrategia: es cambiar la ubicación, como en Lugar. */
    fun chooseReservoir(reservoir: Reservoir) {
        viewModelScope.launch { reservoirs.select(reservoir) }
    }

    /** Elige el puesto para el embalse activo; null = automático (la app ordena todos). */
    fun chooseSpot(id: Long?) {
        viewModelScope.launch {
            val reservoirId = reservoirs.observeActive().first()?.reservoir?.id
            localSettings.put(chosenSpotKey(reservoirId), id?.toString())
        }
    }

    /** Cañas que vas a pescar (se recuerda en este móvil). */
    fun chooseRods(n: Int) {
        viewModelScope.launch { localSettings.put(KEY_RODS, n.coerceIn(1, DayPlans.MAX_RODS).toString()) }
    }

    /** Planes ya pedidos en esta pantalla: si uno falla, no se vuelve a pedir solo (no gastar cuota). */
    private val attempted = mutableSetOf<String>()

    private var planJob: Job? = null

    init {
        viewModelScope.launch {
            // El último plan válido de hoy se vuelve a mostrar sin gastar otra consulta.
            restoreLastPlan()
            // Opción A (fase 7.12): al elegir embalse, puesto y cañas, la IA se consulta sola una vez
            // por día, puesto, cañas y franja; esperar a que dejes de tocar evita consultas de más.
            uiState.map { planKey(it) to it }
                .distinctUntilChanged { a, b -> a.first == b.first }
                .debounce(AUTO_PLAN_DELAY_MS)
                .collect { (key, state) ->
                    if (key == null || key in attempted || planFlow.value.key == key) return@collect
                    if (assistantSettings.apiKey() == null) return@collect
                    startRequest(state, key)
                }
        }
    }

    private suspend fun restoreLastPlan() {
        val last = GuidedJson.decodeAi(localSettings.get(GuidedSessionManager.KEY_LAST_PLAN)) ?: return
        val today = clock.instant().atZone(Formatting.MADRID).toLocalDate()
        if (!last.valid || last.time.atZone(Formatting.MADRID).toLocalDate() != today) return
        val plan = last.response?.let(AssistantJson::parsePlan) ?: return
        if (planFlow.value.outcome == null) planFlow.value = PlanUiState(outcome = AiOutcome(last, plan), key = localSettings.get(KEY_PLAN_KEY))
    }

    /** Plan con IA: solo si hay clave; la respuesta se valida y, si no vale, mandan las reglas. */
    val plan: StateFlow<PlanUiState> = combine(planFlow, assistantSettings.observeKeyHint()) { p, hint -> p.copy(configured = hint != null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    /** «Volver a preguntar»: nueva consulta para lo elegido ahora. */
    fun requestPlan() {
        val state = uiState.value
        val key = planKey(state) ?: return
        startRequest(state, key)
    }

    /** Una consulta a la vez: si cambias de puesto mientras pregunta, manda la última elección. */
    private fun startRequest(state: StrategyUiState, planKey: String) {
        planJob?.cancel()
        planJob = viewModelScope.launch { request(state, planKey) }
    }

    private suspend fun request(state: StrategyUiState, planKey: String) {
        val apiKey = assistantSettings.apiKey() ?: return
        val now = clock.instant()
        val input = planInput(state, state.rods, state.gear, now) ?: return
        attempted += planKey
        planFlow.value = planFlow.value.copy(running = true)
        try {
            val outcome = withContext(io) {
                assistant.plan(
                    assistantSettings.config(), apiKey, AssistantPrompts.planState(input), planContext(state, state.rods, state.gear, now),
                    knowledge.forPlan(state.reservoir?.isBrovales ?: true),
                )
            }
            // Se guarda para copiarlo en la sesión guiada si empieza en las próximas horas.
            localSettings.put(GuidedSessionManager.KEY_LAST_PLAN, GuidedJson.encodeAi(outcome.exchange))
            localSettings.put(KEY_PLAN_KEY, planKey)
            planFlow.value = PlanUiState(outcome = outcome, key = planKey)
        } catch (e: CancellationException) {
            // Cancelada por otra elección: si vuelves a esta, se podrá pedir.
            attempted -= planKey
            throw e
        } finally {
            if (planFlow.value.running) planFlow.value = planFlow.value.copy(running = false)
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
    val chosenSpotId: Long? = null,
    val rods: Int = DEFAULT_RODS,
)

/** Ajustes locales (no se sincronizan) de Estrategia. */
const val KEY_RODS = "strategy_rods"
private const val KEY_PLAN_KEY = "strategy_plan_key"

/** Espera tras el último cambio de embalse, puesto o cañas antes de consultar a la IA. */
private const val AUTO_PLAN_DELAY_MS = 1_500L

/** Clave local (no se sincroniza) del puesto elegido en Estrategia para cada embalse. */
fun chosenSpotKey(reservoirId: String?): String = "strategy_spot_" + (reservoirId ?: "sin_embalse")
