package com.nachojerez.carpstrategy.domain.guided

import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.time.Duration
import java.time.Instant

/** Señales de carpa en el agua (§5.9.1). */
enum class SignalLevel { NONE, INDIRECT, DIRECT }

/** Actividad en las cañas desde el aviso anterior. */
enum class HookActivity { NOTHING, TOUCHES, MISSED, CATCH }

/** Estado del cebo al recogerlo. */
enum class BaitState { NOT_CHECKED, INTACT, NIBBLED, GONE }

/** Variable que cambió el usuario por su cuenta (una por caña, §5.9.2). */
enum class ChangedVariable { BAIT, RIG, COLUMN, DISTANCE, ZONE }

/**
 * Respuesta a un aviso (cada 30 min o cuando el usuario quiera). [notWorking] es el botón
 * «No funciona»: dos en 30 min fuerzan la siguiente propuesta (§5.9.3).
 */
data class CheckIn(
    val time: Instant,
    val signals: SignalLevel = SignalLevel.NONE,
    val activity: HookActivity = HookActivity.NOTHING,
    val baitState: BaitState = BaitState.NOT_CHECKED,
    val notWorking: Boolean = false,
    val userChange: ChangedVariable? = null,
)

/** Tipo de paso de la escalera (§5.9.1–§5.9.2). */
enum class StepKind { INITIAL, PRESENTATION, RIG, COLUMN, DISTANCE, ZONE, ANTI_CRAB }

/** Por qué se propone el paso: la situación diagnosticada. */
enum class Situation { START, NO_SIGNALS, SIGNALS_NO_BITES, TOUCHES, CRAB_OR_SMALL_FISH }

/** Qué propone la app. Los textos salen de [kind], [bait] y [column] en la UI. */
data class Proposal(
    val kind: StepKind,
    val situation: Situation,
    val bait: BaitType? = null,
    /** Nombre del cebo del equipo del usuario, si lo lleva. */
    val baitName: String? = null,
    val baitFallback: Boolean = false,
    val column: Column? = null,
    val rigName: String? = null,
    /** Propuesta forzada por el usuario (dos «No funciona» en 30 min). */
    val forced: Boolean = false,
    val evidence: Evidence,
    val createdAt: Instant,
)

enum class Decision { PENDING, ACCEPTED, REJECTED }

enum class RejectReason { NO_BAIT, NOT_CONVINCED, ALREADY_TRIED, CONDITIONS_DIFFER, OTHER }

data class ProposalRecord(
    val proposal: Proposal,
    val decision: Decision = Decision.PENDING,
    val reason: RejectReason? = null,
    val comment: String = "",
    val decidedAt: Instant? = null,
)

/**
 * Tramo: tiempo pescado con una misma configuración. Al aceptar una propuesta empieza otro.
 * El aprendizaje se hace por tramo (§9): esfuerzo, picadas y capturas de cada uno.
 */
data class Segment(
    val start: Instant,
    val end: Instant? = null,
    /** Paso aplicado al empezar el tramo (INITIAL en el primero). */
    val kind: StepKind,
    val bait: BaitType? = null,
    val baitName: String? = null,
    val column: Column? = null,
    val forced: Boolean = false,
    val checkIns: List<CheckIn> = emptyList(),
) {
    fun duration(now: Instant): Duration = Duration.between(start, end ?: now)

    val bites: Int get() = checkIns.count { it.activity == HookActivity.MISSED || it.activity == HookActivity.CATCH }
    val catches: Int get() = checkIns.count { it.activity == HookActivity.CATCH }
}

/** Registro completo de una sesión guiada; va guardado con la sesión. */
data class GuidedLog(
    val segments: List<Segment>,
    val proposals: List<ProposalRecord> = emptyList(),
) {
    val current: Segment get() = segments.last()

    val pending: ProposalRecord? get() = proposals.lastOrNull()?.takeIf { it.decision == Decision.PENDING }

    val allCheckIns: List<CheckIn> get() = segments.flatMap { it.checkIns }

    /** Cambios de zona aceptados en la sesión (hay un máximo por fase). */
    val zoneChanges: Int get() = segments.count { it.kind == StepKind.ZONE }

    fun withCheckIn(checkIn: CheckIn): GuidedLog =
        copy(segments = segments.dropLast(1) + current.copy(checkIns = current.checkIns + checkIn))

    fun withProposal(proposal: Proposal): GuidedLog = copy(proposals = proposals + ProposalRecord(proposal))

    /** Aceptar la propuesta pendiente: cierra el tramo actual y abre uno nuevo. */
    fun accept(at: Instant): GuidedLog {
        val record = pending ?: return this
        val p = record.proposal
        val accepted = proposals.dropLast(1) + record.copy(decision = Decision.ACCEPTED, decidedAt = at)
        // El plan inicial (o su sustituto) configura el primer tramo sin abrir otro.
        if (p.kind == StepKind.INITIAL) {
            val first = current.copy(bait = p.bait, baitName = p.baitName, column = p.column)
            return copy(segments = segments.dropLast(1) + first, proposals = accepted)
        }
        val closed = segments.dropLast(1) + current.copy(end = at)
        val next = Segment(
            start = at,
            kind = p.kind,
            bait = p.bait ?: current.bait,
            baitName = if (p.bait != null) p.baitName else current.baitName,
            column = p.column ?: current.column,
            forced = p.forced,
        )
        return copy(segments = closed + next, proposals = accepted)
    }

    fun reject(at: Instant, reason: RejectReason, comment: String): GuidedLog {
        val record = pending ?: return this
        return copy(proposals = proposals.dropLast(1) + record.copy(decision = Decision.REJECTED, reason = reason, comment = comment.trim(), decidedAt = at))
    }

    /** Cebos que el usuario ha dicho no tener (rechazos «no tengo ese cebo»). */
    val missingBaits: Set<BaitType>
        get() = proposals.filter { it.decision == Decision.REJECTED && it.reason == RejectReason.NO_BAIT }.mapNotNull { it.proposal.bait }.toSet()

    /** Cierra el último tramo al terminar la sesión. */
    fun finish(at: Instant): GuidedLog = copy(segments = segments.dropLast(1) + current.copy(end = current.end ?: at))

    companion object {
        /** Empieza con el plan A pendiente de aceptar o rechazar. */
        fun start(at: Instant, initial: Proposal): GuidedLog = GuidedLog(
            segments = listOf(Segment(start = at, kind = StepKind.INITIAL, bait = initial.bait, baitName = initial.baitName, column = initial.column)),
            proposals = listOf(ProposalRecord(initial)),
        )
    }
}
