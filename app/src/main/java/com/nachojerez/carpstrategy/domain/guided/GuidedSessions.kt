package com.nachojerez.carpstrategy.domain.guided

import com.nachojerez.carpstrategy.domain.derived.LegalWindow
import com.nachojerez.carpstrategy.domain.derived.SolarCalculator
import com.nachojerez.carpstrategy.domain.journal.Catch
import com.nachojerez.carpstrategy.domain.journal.Session
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** Resultado de una acción sobre la sesión guiada. [newProposal] es la que acaba de añadirse. */
data class GuidedUpdate(
    val session: Session,
    val messages: Set<GuidedMessage>,
    val newProposal: Proposal? = null,
)

/**
 * Operaciones sobre una [Session] con registro guiado (§5.9). Funciones puras: aplican la
 * acción, actualizan picadas y capturas de la ficha y vuelven a evaluar con [GuidedEngine].
 */
object GuidedSessions {
    /** Sesión guiada en curso (como mucho una: la más reciente). */
    fun active(sessions: List<Session>): Session? = sessions.filter { it.isOngoing && it.guided != null }.maxByOrNull { it.start }

    /** Fase con los parámetros guardados al empezar (temperatura del agua y estación de las reglas). */
    fun phaseOf(session: Session): FishingPhase {
        val features = session.context?.features
        val water = features?.numbers?.get("temp_agua_c") ?: session.context?.waterTempC
        return GuidedEngine.phase(water, features?.texts?.get("estacion"))
    }

    /** Fin del horario legal del día de la sesión. */
    fun legalEnd(session: Session, zone: ZoneId): Instant? = session.context?.legalEnd
        ?: LegalWindow.of(SolarCalculator.sunTimes(session.start.atZone(zone).toLocalDate(), session.location))?.end

    /** Presión de pesca alta: fin de semana u otros pescadores cerca (§5.9.2). */
    fun highPressure(session: Session, now: Instant, zone: ZoneId): Boolean {
        val day = now.atZone(zone).dayOfWeek
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY || (session.otherAnglers ?: 0) > 0
    }

    fun context(session: Session, now: Instant, inventory: List<GearItem>, zone: ZoneId) =
        GuidedContext(phaseOf(session), now, legalEnd(session, zone), inventory, highPressure(session, now, zone))

    /** Empieza el registro guiado con el plan A pendiente de aceptar o rechazar. */
    fun start(session: Session, ctx: GuidedContext): GuidedUpdate {
        val initial = GuidedEngine.initialProposal(ctx)
        val started = session.copy(guided = GuidedLog.start(ctx.now, initial))
        return GuidedUpdate(started, GuidedEngine.evaluate(started.guided!!, ctx).messages, initial)
    }

    /** Respuesta a un aviso: picada fallada o captura suman en la ficha; captura añade su hora. */
    fun checkIn(session: Session, checkIn: CheckIn, ctx: GuidedContext): GuidedUpdate {
        val log = session.guided ?: return GuidedUpdate(session, emptySet())
        val hooked = checkIn.activity == HookActivity.MISSED || checkIn.activity == HookActivity.CATCH
        val updated = session.copy(
            guided = log.withCheckIn(checkIn),
            bites = session.bites + if (hooked) 1 else 0,
            catches = if (checkIn.activity == HookActivity.CATCH) session.catches + Catch(time = checkIn.time) else session.catches,
        )
        return refresh(updated, ctx)
    }

    /** Acepta la propuesta pendiente; el cebo y el montaje con nombre pasan a la ficha. */
    fun accept(session: Session, ctx: GuidedContext): GuidedUpdate {
        val log = session.guided ?: return GuidedUpdate(session, emptySet())
        val proposal = log.pending?.proposal ?: return refresh(session, ctx)
        val updated = session.copy(
            guided = log.accept(ctx.now),
            bait = proposal.baitName ?: session.bait,
            rig = proposal.rigName ?: session.rig,
        )
        return refresh(updated, ctx)
    }

    /** Rechaza con motivo y comentario; si era el plan A, se propone otro. */
    fun reject(session: Session, reason: RejectReason, comment: String, ctx: GuidedContext): GuidedUpdate {
        val log = session.guided ?: return GuidedUpdate(session, emptySet())
        return refresh(session.copy(guided = log.reject(ctx.now, reason, comment)), ctx)
    }

    /** Vuelve a evaluar y, si toca, añade la siguiente propuesta (pendiente de decidir). */
    fun refresh(session: Session, ctx: GuidedContext): GuidedUpdate {
        val log = session.guided ?: return GuidedUpdate(session, emptySet())
        if (!session.isOngoing) return GuidedUpdate(session, emptySet())
        val evaluation = GuidedEngine.evaluate(log, ctx)
        val proposal = evaluation.proposal ?: return GuidedUpdate(session, evaluation.messages)
        return GuidedUpdate(session.copy(guided = log.withProposal(proposal)), evaluation.messages, proposal)
    }

    /** Termina: cierra el último tramo; el resultado se completa en la ficha de la sesión. */
    fun finish(session: Session, at: Instant): Session =
        session.copy(end = session.end ?: at, guided = session.guided?.finish(at))
}
