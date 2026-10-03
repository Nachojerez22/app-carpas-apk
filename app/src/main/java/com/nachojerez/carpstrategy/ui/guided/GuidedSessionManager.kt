package com.nachojerez.carpstrategy.ui.guided

import com.nachojerez.carpstrategy.di.IoDispatcher
import com.nachojerez.carpstrategy.domain.guided.CheckIn
import com.nachojerez.carpstrategy.domain.guided.GroundbaitLevel
import com.nachojerez.carpstrategy.domain.guided.GuidedEngine
import com.nachojerez.carpstrategy.domain.guided.GuidedEnv
import com.nachojerez.carpstrategy.domain.guided.GuidedSessions
import com.nachojerez.carpstrategy.domain.guided.GuidedUpdate
import com.nachojerez.carpstrategy.domain.guided.RejectReason
import com.nachojerez.carpstrategy.domain.journal.FishingZone
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.repository.JournalRepository
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import com.nachojerez.carpstrategy.domain.rules.RulesRepository
import com.nachojerez.carpstrategy.domain.usecase.ObserveRawWeatherUseCase
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.diary.completeSession
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    private val rulesRepository: RulesRepository,
    private val scheduler: CheckInScheduler,
    private val notifier: GuidedNotifier,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private val next = MutableStateFlow<Instant?>(null)

    /** Ámbito para el trabajo que lanzan los receptores (alarma y botones de la notificación). */
    val scope = CoroutineScope(SupervisorJob() + io)

    /** Hora del próximo aviso programado (null si no hay ninguno). */
    val nextCheckIn: StateFlow<Instant?> = next

    fun observeActive(): Flow<Session?> = journal.observeSessions().map(GuidedSessions::active)

    fun canScheduleExact(): Boolean = scheduler.canScheduleExact()

    fun canNotify(): Boolean = notifier.canNotify()

    /** Empieza ahora: valoración previa y contexto como cualquier sesión, y plan A pendiente en cada caña. */
    suspend fun start(rodNames: List<String>, zone: FishingZone?, groundbait: GroundbaitLevel?): Long = mutex.withLock {
        GuidedSessions.active(journal.observeSessions().first())?.let { return it.id }
        val now = clock.instant()
        val location = settings.observeLocation().first().point
        val base = withContext(io) {
            val raw = observeRawWeather(location).first()
            val rules = rulesRepository.load()
            val snapshots = journal.predictionsBetween(now.minusSeconds(86_400), now)
            completeSession(Session(start = now, location = location, rods = rodNames.size, zone = zone, createdAt = now), true, now, raw, rules, snapshots)
        }
        val update = GuidedSessions.start(base, rodNames, groundbait, env(now))
        val id = journal.save(update.session)
        afterChange(update.copy(session = update.session.copy(id = id)), now, alert = false)
        id
    }

    /** Respuesta en una caña; con [rodId] null, en la única caña (o en la primera). */
    suspend fun checkIn(rodId: Int?, build: (Instant) -> CheckIn): Unit = change { s, env ->
        GuidedSessions.checkIn(s, rodId ?: s.guided?.rods?.firstOrNull()?.id ?: 1, build(env.now), env)
    }

    suspend fun nothingEverywhere(): Unit = change { s, env -> GuidedSessions.nothingEverywhere(s, env) }

    suspend fun windChanged(): Unit = change { s, env -> GuidedSessions.windChanged(s, env) }

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
        session.id
    }

    /** Ha saltado la alarma: se anota el aviso, se avisa con vibración y se programa el siguiente. */
    suspend fun onAlarm(): Unit = mutex.withLock {
        val session = active()
        if (session == null) {
            scheduler.cancel()
            notifier.dismiss()
            return@withLock
        }
        val now = clock.instant()
        val record = session.guided ?: return@withLock
        val withAlarm = session.copy(guided = record.withAlarm(now))
        val update = GuidedSessions.refresh(withAlarm, env(now))
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
        afterChange(GuidedUpdate(session, emptySet()), now, alert = false, reschedule = !scheduler.isScheduled())
    }

    private suspend fun active(): Session? = GuidedSessions.active(journal.observeSessions().first())

    private suspend fun env(now: Instant) = GuidedEnv(now, settings.observeGear().first(), Formatting.MADRID)

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
}
