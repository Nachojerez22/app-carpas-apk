package com.nachojerez.carpstrategy.ui.guided

import com.nachojerez.carpstrategy.data.assistant.AiExtras
import com.nachojerez.carpstrategy.data.assistant.Assistant
import com.nachojerez.carpstrategy.data.assistant.AssistantPrompts
import com.nachojerez.carpstrategy.data.assistant.AssistantSettings
import com.nachojerez.carpstrategy.data.assistant.KnowledgeAsset
import com.nachojerez.carpstrategy.data.reservoir.ReservoirRepository
import com.nachojerez.carpstrategy.data.userdata.GuidedJson
import com.nachojerez.carpstrategy.data.userdata.LocalSettings
import com.nachojerez.carpstrategy.di.IoDispatcher
import com.nachojerez.carpstrategy.domain.advice.AdvisorInput
import com.nachojerez.carpstrategy.domain.advice.Experience
import com.nachojerez.carpstrategy.domain.advice.FishingAdvisor
import com.nachojerez.carpstrategy.domain.advice.Lessons
import com.nachojerez.carpstrategy.domain.derived.LegalWindow
import com.nachojerez.carpstrategy.domain.derived.SolarCalculator
import com.nachojerez.carpstrategy.domain.guided.AiCheckInPolicy
import com.nachojerez.carpstrategy.domain.guided.CheckIn
import com.nachojerez.carpstrategy.domain.guided.FieldCondition
import com.nachojerez.carpstrategy.domain.guided.GroundbaitLevel
import com.nachojerez.carpstrategy.domain.guided.GuidedEngine
import com.nachojerez.carpstrategy.domain.guided.GuidedEnv
import com.nachojerez.carpstrategy.domain.guided.GuidedSessions
import com.nachojerez.carpstrategy.domain.guided.GuidedUpdate
import com.nachojerez.carpstrategy.domain.guided.RejectReason
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.Spots
import com.nachojerez.carpstrategy.domain.guided.WeatherQuestion
import com.nachojerez.carpstrategy.domain.guided.WeatherSnapshot
import com.nachojerez.carpstrategy.domain.guided.today
import com.nachojerez.carpstrategy.domain.journal.FishingZone
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.manual.ManualOrigin
import com.nachojerez.carpstrategy.domain.manual.ManualRecordValidator
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.repository.JournalRepository
import com.nachojerez.carpstrategy.domain.repository.ManualDataRepository
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import com.nachojerez.carpstrategy.domain.reservoir.Reservoir
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirContext
import com.nachojerez.carpstrategy.domain.reservoir.Reservoirs
import com.nachojerez.carpstrategy.domain.rules.RulesRepository
import com.nachojerez.carpstrategy.domain.usecase.ObserveRawWeatherUseCase
import com.nachojerez.carpstrategy.domain.usecase.RefreshWeatherUseCase
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.diary.completeSession
import com.nachojerez.carpstrategy.ui.strategy.adviceFor
import com.nachojerez.carpstrategy.ui.strategy.buildStrategyState
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sesión guiada en curso (§5.9): aplica cada acción con [GuidedSessions], la guarda en el diario,
 * programa el siguiente aviso y actualiza la notificación. La usan la pantalla, la notificación
 * y la alarma, así que todo pasa por un único cerrojo.
 */
@Singleton
class GuidedSessionManager @Inject constructor(
    private val journal: JournalRepository,
    private val settings: SettingsRepository,
    private val observeRawWeather: ObserveRawWeatherUseCase,
    private val refreshWeather: RefreshWeatherUseCase,
    private val rulesRepository: RulesRepository,
    private val scheduler: CheckInScheduler,
    private val notifier: GuidedNotifier,
    private val assistant: Assistant,
    private val assistantSettings: AssistantSettings,
    private val localSettings: LocalSettings,
    private val knowledge: KnowledgeAsset,
    private val reservoirs: ReservoirRepository,
    private val manualData: ManualDataRepository,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private val next = MutableStateFlow<Instant?>(null)
    private val aiRunning = MutableStateFlow(false)

    /** Hay una consulta a la IA en marcha. */
    val aiBusy: StateFlow<Boolean> = aiRunning

    /** Ámbito para el trabajo que lanzan los receptores (alarma y botones de la notificación). */
    val scope = CoroutineScope(SupervisorJob() + io)

    /** Hora del próximo aviso programado (null si no hay ninguno). */
    val nextCheckIn: StateFlow<Instant?> = next

    fun observeActive(): Flow<Session?> = journal.observeSessions().map(GuidedSessions::active)

    fun canScheduleExact(): Boolean = scheduler.canScheduleExact()

    fun canNotify(): Boolean = notifier.canNotify()

    /** Empieza ahora: valoración previa y contexto como cualquier sesión, y plan A pendiente en cada caña. */
    suspend fun start(rodNames: List<String>, zone: FishingZone?, groundbait: GroundbaitLevel?, spot: Spot? = null, waterC: Double? = null): Long = mutex.withLock {
        GuidedSessions.active(journal.observeSessions().first())?.let { return it.id }
        val now = clock.instant()
        val location = settings.observeLocation().first().point
        // Fase 7.16: el agua medida al empezar va a Datos antes de calcular: manda sobre la estimada.
        waterC?.let { saveWaterMeasurement(it, location, now) }
        val sessions = journal.observeSessions().first()
        val gear = settings.observeGear().first().today()
        val (reservoir, all) = reservoirAt(location)
        val lessons = Lessons.forReservoir(settings.observeLessons().first(), reservoir?.reservoir?.id)
        val (base, weather, advice) = withContext(io) {
            val raw = observeRawWeather(location).first()
            val rules = rulesRepository.load()
            val snapshots = journal.predictionsBetween(now.minusSeconds(86_400), now)
            val session = completeSession(Session(start = now, location = location, rods = rodNames.size, zone = zone, createdAt = now), true, now, raw, rules, snapshots)
            val derived = buildStrategyState(raw, rules, now, location).derived
            Triple(session, weatherSnapshotAt(raw, derived, now), adviceFor(derived, now, sessions, gear, listOfNotNull(spot), lessons, reservoir, all))
        }
        // Plan A con los cebos recomendados: los que te han funcionado primero (fase 7.5).
        val env = env(now).copy(preferredBaits = advice?.baits?.mapNotNull { it.type }.orEmpty())
        val started = GuidedSessions.start(base, rodNames, groundbait, env, spot)
        // El primer tiempo se guarda sin reevaluar: el plan A de cada caña sigue pendiente.
        val withWeather = weather?.let { w -> started.copy(session = started.session.copy(guided = started.session.guided?.withWeather(w))) } ?: started
        // El plan con IA de Estrategia (si es reciente) queda con la sesión para comparar después.
        // Si no hay uno reciente, el preparado con «Preparar mañana» para hoy (fase 7.15).
        val plan = GuidedJson.decodeAi(localSettings.get(KEY_LAST_PLAN))?.takeIf { Duration.between(it.time, now) <= PLAN_MAX_AGE && !it.time.isAfter(now) }
            ?: GuidedJson.decodeAi(localSettings.get(KEY_PREPARED_PLAN))?.takeIf {
                it.valid && it.time.atZone(Formatting.MADRID).toLocalDate() == now.atZone(Formatting.MADRID).toLocalDate()
            }
        val update = plan?.let { p -> withWeather.copy(session = withWeather.session.copy(guided = withWeather.session.guided?.let { it.copy(ai = it.ai + p) })) } ?: withWeather
        val id = journal.save(update.session)
        afterChange(update.copy(session = update.session.copy(id = id)), now, alert = false)
        id
    }

    private suspend fun saveWaterMeasurement(celsius: Double, location: GeoPoint, now: Instant) {
        val checked = ManualRecordValidator.validate(
            raw = waterMeasurementRecord(celsius, now, Formatting.MADRID),
            recordNumber = null,
            location = location,
            appLocation = location,
            origin = ManualOrigin.Typed,
            now = now,
            zone = Formatting.MADRID,
        )
        checked.record?.takeIf { checked.errors.isEmpty() }?.let { manualData.import(listOf(it)) }
    }

    /** Respuesta en una caña; con [rodId] null, en la única caña (o en la primera). */
    suspend fun checkIn(rodId: Int?, build: (Instant) -> CheckIn): Unit = change { s, env ->
        GuidedSessions.checkIn(s, rodId ?: s.guided?.rods?.firstOrNull()?.id ?: 1, build(env.now), env)
    }

    suspend fun nothingEverywhere(): Unit = change { s, env -> GuidedSessions.nothingEverywhere(s, env) }

    suspend fun windChanged(): Unit = change { s, env -> GuidedSessions.windChanged(s, env) }

    suspend fun conditionChanged(condition: FieldCondition, active: Boolean): Unit =
        change { s, env -> GuidedSessions.conditionChanged(s, condition, active, env) }

    /** «¿Llueve?» / «¿Hay tormenta?»: lo que dice el usuario manda sobre la previsión. */
    suspend fun answerWeather(question: WeatherQuestion, answer: Boolean, heavy: Boolean = false): Unit =
        change { s, env -> GuidedSessions.answerWeather(s, question, answer, env, heavy) }

    suspend fun accept(rodId: Int?): Unit = change { s, env ->
        val id = rodId ?: s.guided?.rods?.firstOrNull { it.log.pending != null }?.id ?: 1
        GuidedSessions.accept(s, id, env)
    }

    suspend fun reject(rodId: Int, reason: RejectReason, comment: String): Unit = change { s, env -> GuidedSessions.reject(s, rodId, reason, comment, env) }

    /** Reevalúa (las propuestas por tiempo aparecen sin que haya respuesta). */
    suspend fun refresh(): Unit = change(saveOnlyIfProposal = true) { s, env -> GuidedSessions.refresh(s, env) }

    /** Termina y devuelve la sesión para completar el resultado en su ficha. */
    suspend fun finish(): Long? = mutex.withLock {
        val session = active() ?: return null
        journal.save(GuidedSessions.finish(session, clock.instant()))
        scheduler.cancel()
        next.value = null
        notifier.dismiss()
        // La IA repasa la sesión y propone lecciones (tú decides si se quedan).
        if (assistantSettings.apiKey() != null) scope.launch { reviewSession(session.id) }
        session.id
    }

    /**
     * Repasa una sesión terminada con la IA y guarda las lecciones válidas como propuestas.
     * Devuelve cuántas lecciones nuevas hay (null si no se pudo consultar).
     */
    suspend fun reviewSession(sessionId: Long): Int? {
        val key = assistantSettings.apiKey() ?: return null
        val sessions = journal.observeSessions().first()
        val session = sessions.firstOrNull { it.id == sessionId && it.end != null } ?: return null
        val gear = settings.observeGear().first().today()
        val (reservoir, all) = reservoirAt(session.location)
        val reservoirId = reservoir?.reservoir?.id
        val spots = Spots.of(settings.observeSpots().first(), reservoirId)
        val now = clock.instant()
        val phase = GuidedSessions.phaseOf(session)
        val lessons = settings.observeLessons().first()
        val extras = AiExtras(
            experience = Experience.forReservoir(sessions.filter { it.id != sessionId }, reservoir?.reservoir, all, phase, session.context?.waterTempC, Formatting.MADRID),
            lessons = Lessons.forReservoir(lessons, reservoirId),
            reservoir = reservoir,
        )
        val state = AssistantPrompts.reviewState(session, phase, Formatting.MADRID, gear, spots, extras)
        val outcome = withContext(io) {
            assistant.review(assistantSettings.config(), key, state, spots, now, sessionId, knowledge.forPlan(reservoir?.isBrovales ?: true)) { UUID.randomUUID().toString() }
        }
        // Lo aprendido en un embalse vale para ese embalse.
        val new = outcome.value.orEmpty().map { it.copy(conditions = it.conditions.copy(reservoirId = reservoirId)) }
        val current = settings.observeLessons().first()
        val merged = Lessons.addNew(current, new)
        if (merged.size != current.size) settings.setLessons(merged)
        // El repaso queda también en la sesión guiada (fase 8: «¿IA o reglas?»).
        mutex.withLock {
            journal.observeSessions().first().firstOrNull { it.id == sessionId }?.let { s ->
                s.guided?.let { g -> journal.save(s.copy(guided = g.copy(ai = g.ai + outcome.exchange))) }
            }
        }
        return if (outcome.exchange.valid) merged.size - current.size else null
    }

    /** Tus datos en condiciones parecidas, tus lecciones y la recomendación de la app. */
    private suspend fun aiExtras(session: Session, now: Instant, spots: List<Spot>): AiExtras {
        val sessions = journal.observeSessions().first().filter { it.id != session.id }
        val phase = GuidedSessions.phaseOf(session)
        val water = session.guided?.weather?.lastOrNull()?.waterC ?: session.context?.waterTempC
        val (reservoir, all) = reservoirAt(session.location)
        val experience = Experience.forReservoir(sessions, reservoir?.reservoir, all, phase, water, Formatting.MADRID)
        val lessons = Lessons.forReservoir(settings.observeLessons().first(), reservoir?.reservoir?.id)
        val sun = SolarCalculator.sunTimes(now.atZone(Formatting.MADRID).toLocalDate(), session.location)
        val advice = FishingAdvisor.advise(
            AdvisorInput(
                now = now,
                phase = phase,
                waterC = water,
                sun = sun,
                legal = LegalWindow.of(sun),
                windFromDeg = session.guided?.weather?.lastOrNull()?.windFromDeg,
                windKmh = session.guided?.weather?.lastOrNull()?.windKmh,
                airC = session.guided?.weather?.lastOrNull()?.airC,
                gustKmh = session.guided?.weather?.lastOrNull()?.gustKmh,
                spots = spots,
                gear = settings.observeGear().first().today(),
                experience = experience,
                lessons = lessons,
                reservoir = reservoir,
            ),
        )
        return AiExtras(experience, advice.lessons, advice, reservoir)
    }

    /**
     * Ha saltado la alarma: se actualiza el tiempo (como mucho [ALARM_REFRESH_TIMEOUT_MS]; sin
     * cobertura se usa la caché), se anota el aviso con su tiempo, se avisa con vibración y se
     * programa el siguiente.
     */
    suspend fun onAlarm() {
        val location = active()?.location
        val weather = location?.let { fieldWeather(it, refresh = true) }
        onAlarmLocked(weather)
        // La IA puede tardar más de lo que dura el receptor: va aparte y avisa si propone algo.
        // Fase 7.15: por defecto solo si algo cambió desde la última consulta (ahorra cuota).
        val record = active()?.guided
        if (record != null && assistantSettings.apiKey() != null &&
            AiCheckInPolicy.shouldConsult(record, assistantSettings.config().checkInMode, clock.instant())
        ) {
            scope.launch { consultAi(fromAlarm = true) }
        }
    }

    /**
     * Pregunta a la IA MANTENER o CAMBIAR con el estado de la sesión. La respuesta se valida;
     * si no vale (o no hay red), se registra y siguen mandando las reglas.
     */
    suspend fun consultAi(fromAlarm: Boolean = false) {
        val key = assistantSettings.apiKey() ?: return
        if (!aiRunning.compareAndSet(expect = false, update = true)) return
        try {
            val config = assistantSettings.config()
            val session = active() ?: return
            val now = clock.instant()
            val env = env(now)
            val spots = Spots.of(settings.observeSpots().first(), reservoirAt(session.location).first?.reservoir?.id)
            val ctx = GuidedSessions.aiContext(session, env, spots)
            if (ctx.legalEnd != null && !now.isBefore(ctx.legalEnd)) return
            val extras = aiExtras(session, now, spots)
            val state = AssistantPrompts.checkInState(session, GuidedSessions.phaseOf(session), ctx.legalEnd, now, Formatting.MADRID, env.inventory, spots, ctx.legalStart, extras)
            val outcome = withContext(io) { assistant.checkIn(config, key, state, ctx, knowledge.forCheckIn()) }
            mutex.withLock {
                val current = active()?.takeIf { it.id == session.id } ?: return@withLock
                val at = clock.instant()
                val update = GuidedSessions.applyAi(current, outcome.exchange, outcome.value, env(at))
                journal.save(update.session)
                afterChange(update, at, alert = update.newProposals.isNotEmpty(), checkIn = fromAlarm, reschedule = false)
            }
        } finally {
            aiRunning.value = false
        }
    }

    private val chatRunning = MutableStateFlow(false)

    /** Hay un mensaje del chat esperando respuesta. */
    val chatBusy: StateFlow<Boolean> = chatRunning

    /**
     * Chat con la IA durante la sesión (fase 7.7). La IA recibe el estado completo (embalse,
     * puesto, cañas, estrategia del día, tus datos y la conversación) y el documento. Su
     * respuesta se valida; si propone cambios válidos quedan como propuestas pendientes.
     * Devuelve false si no hay clave, sesión o ya hay un mensaje en marcha.
     */
    suspend fun chat(message: String): Boolean {
        if (message.isBlank()) return false
        val key = assistantSettings.apiKey() ?: return false
        if (!chatRunning.compareAndSet(expect = false, update = true)) return false
        try {
            val session = active() ?: return false
            val asked = clock.instant()
            val env = env(asked)
            val (reservoir, _) = reservoirAt(session.location)
            val spots = Spots.of(settings.observeSpots().first(), reservoir?.reservoir?.id)
            val ctx = GuidedSessions.aiContext(session, env, spots)
            val extras = aiExtras(session, asked, spots)
            val state = AssistantPrompts.chatState(
                session, GuidedSessions.phaseOf(session), ctx.legalEnd, asked, Formatting.MADRID, env.inventory, spots, message, ctx.legalStart, extras,
            )
            val outcome = withContext(io) {
                assistant.chat(assistantSettings.config(), key, state, ctx, knowledge.forPlan(reservoir?.isBrovales ?: true))
            }
            mutex.withLock {
                val current = active()?.takeIf { it.id == session.id } ?: return@withLock
                val at = clock.instant()
                val update = GuidedSessions.applyChat(current, message, asked, outcome.exchange, outcome.value, env(at))
                journal.save(update.session)
                afterChange(update, at, alert = false, reschedule = false)
            }
            return true
        } finally {
            chatRunning.value = false
        }
    }

    private suspend fun onAlarmLocked(weather: WeatherSnapshot?): Unit = mutex.withLock {
        val session = active()
        if (session == null) {
            scheduler.cancel()
            notifier.dismiss()
            return@withLock
        }
        val now = clock.instant()
        val record = session.guided ?: return@withLock
        // Un giro claro del viento cuenta como cambio de viento antes de evaluar (una sola vez).
        val withAlarm = record.withAlarm(now).let { r -> weather?.let { r.withWeather(it.copy(time = now)) } ?: r }
        val update = GuidedSessions.refresh(session.copy(guided = withAlarm), env(now))
        journal.save(update.session)
        afterChange(update, now, alert = true, checkIn = true)
    }

    /** Al abrir la app: si hay sesión guiada y no hay aviso programado, se vuelve a programar. */
    suspend fun restore(): Unit = mutex.withLock {
        val session = active()
        if (session == null) {
            next.value = null
            return@withLock
        }
        val now = clock.instant()
        afterChange(GuidedUpdate(session), now, alert = false, reschedule = !scheduler.isScheduled())
    }

    /** El tiempo de ahora para la sesión; null si no hay datos (nunca rompe el aviso). */
    private suspend fun fieldWeather(location: GeoPoint, refresh: Boolean): WeatherSnapshot? = withContext(io) {
        try {
            if (refresh) withTimeoutOrNull(ALARM_REFRESH_TIMEOUT_MS) { refreshWeather(location) }
            val now = clock.instant()
            val raw = observeRawWeather(location).first()
            weatherSnapshotAt(raw, buildStrategyState(raw, rulesRepository.load(), now, location).derived, now)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** Embalse de un punto (el de la sesión o la ubicación) con su ficha, y todos los embalses. */
    private suspend fun reservoirAt(point: GeoPoint): Pair<ReservoirContext?, List<Reservoir>> {
        val all = reservoirs.observeAll().first()
        val sheets = reservoirs.observeSheets().first()
        return Reservoirs.nearest(point, all)?.let { r -> ReservoirContext(r, sheets.firstOrNull { it.reservoirId == r.id }) } to all
    }

    private suspend fun active(): Session? = GuidedSessions.active(journal.observeSessions().first())

    private suspend fun env(now: Instant) = GuidedEnv(now, settings.observeGear().first().today(), Formatting.MADRID)

    private suspend fun change(saveOnlyIfProposal: Boolean = false, action: (Session, GuidedEnv) -> GuidedUpdate): Unit = mutex.withLock {
        val session = active() ?: return@withLock
        val now = clock.instant()
        val update = action(session, env(now))
        if (saveOnlyIfProposal && update.newProposals.isEmpty()) return@withLock
        journal.save(update.session)
        afterChange(update, now, alert = update.newProposals.isNotEmpty() && saveOnlyIfProposal)
    }

    private fun afterChange(update: GuidedUpdate, now: Instant, alert: Boolean, checkIn: Boolean = false, reschedule: Boolean = true) {
        val session = update.session
        val record = session.guided ?: return
        val phase = GuidedSessions.phaseOf(session)
        val legalEnd = GuidedSessions.legalEnd(session, Formatting.MADRID)
        val at = GuidedEngine.nextCheckIn(record, phase, now, legalEnd)
        if (reschedule) {
            if (at == null) scheduler.cancel() else scheduler.schedule(at)
        }
        next.value = at
        notifier.show(session, phase, legalEnd, at, now, alert = alert, checkIn = checkIn)
    }

    companion object {
        /** El receptor de la alarma tiene poco tiempo: si la descarga tarda más, vale la caché. */
        private const val ALARM_REFRESH_TIMEOUT_MS = 7_000L

        /** Último plan con IA de Estrategia (ajuste local). */
        const val KEY_LAST_PLAN = "ai_last_plan"

        /** Plan preparado la noche antes para la salida (fase 7.15, ajuste local). */
        const val KEY_PREPARED_PLAN = "ai_prepared_plan"

        /** Un plan sirve para la sesión que empieza en las 3 h siguientes. */
        private val PLAN_MAX_AGE: Duration = Duration.ofHours(3)
    }
}
