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
import com.nachojerez.carpstrategy.domain.advice.BaitShelf
import com.nachojerez.carpstrategy.domain.advice.BaitUsage
import com.nachojerez.carpstrategy.domain.advice.DayPlans
import com.nachojerez.carpstrategy.domain.advice.Lesson
import com.nachojerez.carpstrategy.domain.advice.LessonStatus
import com.nachojerez.carpstrategy.domain.advice.Lessons
import com.nachojerez.carpstrategy.domain.assistant.AiPlan
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.Spots
import com.nachojerez.carpstrategy.domain.guided.today
import com.nachojerez.carpstrategy.domain.journal.JournalStats
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.model.RefreshOutcome
import com.nachojerez.carpstrategy.domain.repository.JournalRepository
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import com.nachojerez.carpstrategy.domain.repository.WeatherRepository
import com.nachojerez.carpstrategy.domain.reservoir.Reservoir
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirContext
import com.nachojerez.carpstrategy.domain.reservoir.Reservoirs
import com.nachojerez.carpstrategy.domain.rules.RulesRepository
import com.nachojerez.carpstrategy.domain.usecase.ObserveRawWeatherUseCase
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.guided.BaitSheetResult
import com.nachojerez.carpstrategy.ui.guided.GearDraft
import com.nachojerez.carpstrategy.ui.guided.GearEditor
import com.nachojerez.carpstrategy.ui.guided.GuidedSessionManager
import com.nachojerez.carpstrategy.ui.guided.SheetUiState
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
import kotlinx.coroutines.flow.drop
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
    private val observeRawWeather: ObserveRawWeatherUseCase,
    rulesRepository: RulesRepository,
    private val weather: WeatherRepository,
    private val settings: SettingsRepository,
    private val journal: JournalRepository,
    private val clock: Clock,
    private val assistant: Assistant,
    private val assistantSettings: AssistantSettings,
    private val localSettings: LocalSettings,
    private val knowledge: KnowledgeAsset,
    private val reservoirs: ReservoirRepository,
    private val gearEditor: GearEditor,
    private val activation: PreparedActivation,
    private val inputsSource: StrategyInputsSource,
    private val preparer: OutingPreparer,
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

    private val adviceInputs = inputsSource.observe()

    private val placeAndRaw = location.flatMapLatest { place -> observeRawWeather(place.point).map { place to it } }

    val uiState: StateFlow<StrategyUiState> =
        combine(placeAndRaw, rules, journal.observeSessions(), ticker) { (place, raw), rules, sessions, now ->
            place to buildStrategyState(raw, rules, now, place.point, sessions)
        }.onEach { (place, state) ->
            // Cada valoración se guarda (una por hora) para copiarla en el diario como
            // "lo que dijo la app antes de salir", sin sesgo retrospectivo.
            state.result?.let { result -> viewModelScope.launch { journal.recordPrediction(JournalStats.snapshotOf(result, place.point, state.ruleContext, state.rulesFingerprint)) } }
        }.map { it.second }
            .combine(adviceInputs) { state, inputs -> withInputs(state, inputs, clock.instant()) }
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

    /** Marca o desmarca un cebo como «hoy lo llevo» (fase 7.13): no se borra del inventario. */
    fun toggleToday(id: String) {
        viewModelScope.launch { gearEditor.toggleToday(id) }
    }

    fun clearToday() {
        viewModelScope.launch { gearEditor.clearToday() }
    }

    fun addFromDiary(usage: BaitUsage) {
        viewModelScope.launch { gearEditor.addFromDiary(usage) }
    }

    fun saveGear(draft: GearDraft) {
        viewModelScope.launch { gearEditor.save(draft) }
    }

    fun deleteGear(id: String) {
        viewModelScope.launch { gearEditor.delete(id) }
    }

    private val sheet = MutableStateFlow(SheetUiState())
    val sheetState: StateFlow<SheetUiState> = sheet

    /** Ficha del cebo con la IA (Google y enlace); [onDone] recibe el borrador con la ficha. */
    fun fetchSheet(draft: GearDraft, onDone: (GearDraft) -> Unit) {
        if (sheet.value.running) return
        sheet.value = SheetUiState(running = true)
        viewModelScope.launch {
            val result = gearEditor.fetchSheet(draft)
            sheet.value = SheetUiState(result = result)
            if (result is BaitSheetResult.Ok) onDone(GearDraft.of(result.item))
        }
    }

    fun clearSheetState() {
        sheet.value = SheetUiState()
    }

    /** Tu cebado «normal» en kg por caña y sesión (null = sin medida). */
    fun setGroundbaitNormalKg(kg: Double?) {
        viewModelScope.launch { settings.setGroundbaitNormalKg(kg) }
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
            // 0.26.1: las cañas del plan (con las fijas que elija la IA) pasan a la sesión guiada.
            combine(uiState, planFlow) { state, p -> dayPlanFor(state, p)?.rods?.mapNotNull { it.setup?.name?.takeIf(String::isNotBlank) } }
                .distinctUntilChanged()
                .collect { names -> if (!names.isNullOrEmpty()) localSettings.put(KEY_PLAN_RODS, names.joinToString("\n")) }
        }
        viewModelScope.launch {
            // El último plan válido de hoy se vuelve a mostrar sin gastar otra consulta.
            restoreLastPlan()
            restorePreparedPlan()
            // Si se prepara desde Preparar, el plan nuevo llega aquí por el ajuste.
            launch { localSettings.observe(GuidedSessionManager.KEY_PREPARED_PLAN).drop(1).collect { restorePreparedPlan() } }
            // Opción A (fase 7.12): al elegir embalse, puesto y cañas, la IA se consulta sola una vez
            // por día, puesto, cañas y franja; esperar a que dejes de tocar evita consultas de más.
            uiState.map { planKey(it) to it }
                .distinctUntilChanged { a, b -> a.first == b.first }
                .debounce(AUTO_PLAN_DELAY_MS)
                .collect { (key, state) ->
                    if (key == null || key in attempted || planFlow.value.key == key) return@collect
                    // Con un plan preparado para esta salida no se pregunta sola (en el embalse
                    // puede no haber red y gastaría cuota): «Volver a preguntar» sigue a mano.
                    val p = planFlow.value
                    if (p.prepared?.value != null && outingKey(p.preparedKey) == outingKey(key)) return@collect
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

    private suspend fun restorePreparedPlan() {
        val prepared = GuidedJson.decodeAi(localSettings.get(GuidedSessionManager.KEY_PREPARED_PLAN)) ?: return
        val key = localSettings.get(KEY_PREPARED_KEY) ?: return
        val today = clock.instant().atZone(Formatting.MADRID).toLocalDate()
        // Vale para el día de la salida (o para mañana, si se preparó hoy).
        if (!prepared.valid || prepared.time.atZone(Formatting.MADRID).toLocalDate().isBefore(today)) return
        val plan = prepared.response?.let(AssistantJson::parsePlan) ?: return
        planFlow.value = planFlow.value.copy(prepared = AiOutcome(prepared, plan), preparedKey = key)
    }

    /** «Preparar la próxima salida» (fase 7.25: vive en Preparar; aquí solo se lee). */
    val prep: StateFlow<PrepUiState> = preparer.state

    fun prepareNextOuting() = preparer.prepare()

    /** La preparación que se puede activar ahora (fase 7.21): de hoy y de este embalse. */
    val preparedToday: StateFlow<PreparedOuting?> = activation.observeToday()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val activatingFlow = MutableStateFlow(false)
    val activating: StateFlow<Boolean> = activatingFlow

    // Fase 7.22: «Reevaluar» la salida preparada antes de activarla.
    private val reviewRunning = MutableStateFlow(false)
    private val reviewShown = MutableStateFlow(false)

    /** Qué ha cambiado desde que la preparaste (solo tras pulsar «Reevaluar»). */
    val review: StateFlow<ReviewUiState> = combine(reviewShown, reviewRunning, uiState, preparedToday, planFlow) { shown, running, state, p, plan ->
        ReviewUiState(running = running, review = if (shown && !running && p != null) reviewOuting(p, state, plan.preparedKey, planKey(state)) else null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

    /** Con cobertura, previsión y observaciones nuevas; sin ella, se compara con lo que ya hay. */
    fun reevaluatePrepared() {
        if (reviewRunning.value) return
        reviewRunning.value = true
        viewModelScope.launch {
            try {
                val place = location.first()
                weather.refreshForecast(place.point)
                weather.refreshObservations(place.point)
            } finally {
                reviewRunning.value = false
                reviewShown.value = true
            }
        }
    }

    fun closeReview() {
        reviewShown.value = false
    }

    /** «Pedir un plan nuevo»: plan de la IA para lo de ahora (aparece en «Tu plan»). */
    fun replacePrepared() {
        reviewShown.value = false
        requestPlan()
    }

    /** «Activar»: empieza la sesión guiada con lo preparado y, al tenerla, abre la pantalla. */
    fun activatePrepared(onStarted: () -> Unit) {
        val prepared = preparedToday.value ?: return
        if (activatingFlow.value) return
        activatingFlow.value = true
        viewModelScope.launch {
            try {
                activation.activate(prepared)
                onStarted()
            } finally {
                activatingFlow.value = false
            }
        }
    }

    /** La última preparación guardada («Ver la preparación»). */
    val preparedOuting: StateFlow<PreparedOuting?> = localSettings.observe(KEY_PREPARED_OUTING).map(PreparedOutingJson::decode)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
        val input = planInput(state, state.planRods, state.gear, now) ?: return
        attempted += planKey
        planFlow.value = planFlow.value.copy(running = true)
        try {
            val outcome = withContext(io) {
                assistant.plan(
                    assistantSettings.config(), apiKey, AssistantPrompts.planState(input), planContext(state, state.planRods, state.gear, now),
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


/** Ajustes locales (no se sincronizan) de Estrategia. */
const val KEY_RODS = "strategy_rods"

/** Nombres de las cañas del plan de Estrategia, uno por línea (0.26.1): la guiada empieza con ellas. */
const val KEY_PLAN_RODS = "strategy_plan_rods"
/** [planKey] del último plan pedido en Estrategia (la guiada lo usa para saber de qué embalse es). */
const val KEY_PLAN_KEY = "strategy_plan_key"


/** Espera tras el último cambio de embalse, puesto o cañas antes de consultar a la IA. */
private const val AUTO_PLAN_DELAY_MS = 1_500L

/** Clave local (no se sincroniza) del puesto elegido en Estrategia para cada embalse. */
fun chosenSpotKey(reservoirId: String?): String = "strategy_spot_" + (reservoirId ?: "sin_embalse")
