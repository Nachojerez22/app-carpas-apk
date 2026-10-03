package com.nachojerez.carpstrategy.ui.guided

import com.nachojerez.carpstrategy.di.IoDispatcher
import com.nachojerez.carpstrategy.domain.guided.CheckIn
import com.nachojerez.carpstrategy.domain.guided.GuidedContext
import com.nachojerez.carpstrategy.domain.guided.GuidedEngine
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

    /** Empieza ahora: valoración previa y contexto como cualquier sesión, y plan A pendiente. */
    suspend fun start(rods: Int, zone: FishingZone?): Long = mutex.withLock {
        GuidedSessions.active(journal.observeSessions().first())?.let { return it.id }
        val now = clock.instant()
        val location = settings.observeLocation().first().point
        val base = withContext(io) {
            val raw = observeRawWeather(location).first()
            val rules = rulesRepository.load()
            val snapshots = journal.predictionsBetween(now.minusSeconds(86_400), now)
            completeSession(Session(start = now, location = location, rods = rods, zone = zone, createdAt = now), true, now, raw, rules, snapshots)
        }
        val update = GuidedSessions.start(base, context(base, now))
        val id = journal.save(update.session)
        afterChange(update.copy(session = update.session.copy(id = id)), now, alert = false)
        id
    }

    suspend fun checkIn(build: (Instant) -> CheckIn): Unit = change { s, ctx -> GuidedSessions.checkIn(s, build(ctx.now), ctx) }

    suspend fun accept(): Unit = change { s, ctx -> GuidedSessions.accept(s, ctx) }

    suspend fun reject(reason: RejectReason, comment: String): Unit = change { s, ctx -> GuidedSessions.reject(s, reason, comment, ctx) }

    /** Reevalúa (las propuestas por tiempo aparecen sin que haya respuesta). */
    suspend fun refresh(): Unit = change(saveOnlyIfProposal = true) { s, ctx -> GuidedSessions.refresh(s, ctx) }

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
        val log = session.guided ?: return@withLock
        val withAlarm = session.copy(guided = log.withAlarm(now))
        val update = GuidedSessions.refresh(withAlarm, context(withAlarm, now))
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

    private suspend fun context(session: Session, now: Instant): GuidedContext =
        GuidedSessions.context(session, now, settings.observeGear().first(), Formatting.MADRID)

    private suspend fun change(saveOnlyIfProposal: Boolean = false, action: (Session, GuidedContext) -> GuidedUpdate): Unit = mutex.withLock {
        val session = active() ?: return@withLock
        val now = clock.instant()
        val update = action(session, context(session, now))
        if (saveOnlyIfProposal && update.newProposal == null) return@withLock
        journal.save(update.session)
        afterChange(update, now, alert = update.newProposal != null && saveOnlyIfProposal)
    }

    private fun afterChange(update: GuidedUpdate, now: Instant, alert: Boolean, checkIn: Boolean = false, reschedule: Boolean = true) {
        val session = update.session
        val log = session.guided ?: return
        val phase = GuidedSessions.phaseOf(session)
        val legalEnd = GuidedSessions.legalEnd(session, Formatting.MADRID)
        val at = GuidedEngine.nextCheckIn(log, phase, now, legalEnd)
        if (reschedule) {
            if (at == null) scheduler.cancel() else scheduler.schedule(at)
        }
        next.value = at
        notifier.show(session, phase, legalEnd, at, now, alert = alert, checkIn = checkIn)
    }
}
