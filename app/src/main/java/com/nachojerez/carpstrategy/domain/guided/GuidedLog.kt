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
 * Especie de una captura. En Brovales, según fuentes divulgativas: carpa, barbo, boga y black
 * bass (§6). Solo la carpa cuenta como captura de la sesión; las demás indican que otros peces
 * se comen el cebo antes que la carpa.
 */
enum class Species { CARP, BARBEL, NASE, BLACK_BASS, SMALL }

/** Cantidad de cebado o recebado, sin gramos (§5.5: no hay datos para fijarlos). */
enum class GroundbaitLevel { LOW, NORMAL, HIGH }

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
    /** Especie si [activity] es CATCH; null = carpa. */
    val species: Species? = null,
    /** Recebado anotado en este momento. */
    val rebait: GroundbaitLevel? = null,
) {
    val isCarpCatch: Boolean get() = activity == HookActivity.CATCH && (species == null || species == Species.CARP)

    /** Captura de otra especie (o pequeño sin identificar). */
    val isBycatch: Boolean get() = activity == HookActivity.CATCH && species != null && species != Species.CARP
}

/** Tipo de paso de la escalera (§5.9.1–§5.9.2). */
enum class StepKind { INITIAL, PRESENTATION, RIG, COLUMN, DISTANCE, ZONE, ANTI_CRAB, SELECTIVE }

/** Por qué se propone el paso: la situación diagnosticada. */
enum class Situation { START, NO_SIGNALS, SIGNALS_NO_BITES, TOUCHES, CRAB_OR_SMALL_FISH, OTHER_FISH }

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
    /** Montaje del equipo propuesto al empezar el tramo. */
    val rigName: String? = null,
) {
    fun duration(now: Instant): Duration = Duration.between(start, end ?: now)

    /** Picadas de carpa (falladas o capturadas). */
    val bites: Int get() = checkIns.count { it.activity == HookActivity.MISSED || it.isCarpCatch }
    val catches: Int get() = checkIns.count { it.isCarpCatch }
    val bycatch: Int get() = checkIns.count { it.isBycatch }
}

/** Registro de una caña en la sesión guiada (§5.9.2: una variable por caña). */
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
            val first = current.copy(bait = p.bait, baitName = p.baitName, column = p.column, rigName = p.rigName ?: current.rigName)
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
            rigName = p.rigName ?: current.rigName,
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
            segments = listOf(Segment(start = at, kind = StepKind.INITIAL, bait = initial.bait, baitName = initial.baitName, column = initial.column, rigName = initial.rigName)),
            proposals = listOf(ProposalRecord(initial)),
        )
    }
}

/** Una caña con nombre («fija», «carrete») y su registro. */
data class RodTrack(val id: Int, val name: String, val log: GuidedLog)

/**
 * Registro completo de una sesión guiada: una pista por caña, los avisos mostrados (contestados o
 * no: la falta de respuesta también es un dato), los cambios de viento que anotó el usuario y el
 * cebado inicial. Va guardado con la sesión.
 */
data class GuidedRecord(
    val rods: List<RodTrack>,
    val alarms: List<Instant> = emptyList(),
    val windChanges: List<Instant> = emptyList(),
    val groundbait: GroundbaitLevel? = null,
) {
    fun rod(id: Int): RodTrack? = rods.firstOrNull { it.id == id }

    fun updateRod(id: Int, transform: (GuidedLog) -> GuidedLog): GuidedRecord =
        copy(rods = rods.map { if (it.id == id) it.copy(log = transform(it.log)) else it })

    val allCheckIns: List<CheckIn> get() = rods.flatMap { it.log.allCheckIns }.sortedBy { it.time }

    /** Cebos que el usuario ha dicho no tener, en cualquier caña. */
    val missingBaits: Set<BaitType> get() = rods.flatMap { it.log.missingBaits }.toSet()

    /** Recebado vigente en una caña: el último anotado o el inicial. */
    fun groundbaitOf(id: Int): GroundbaitLevel? =
        rod(id)?.log?.allCheckIns?.lastOrNull { it.rebait != null }?.rebait ?: groundbait

    fun withAlarm(at: Instant): GuidedRecord = copy(alarms = alarms + at)

    fun withWindChange(at: Instant): GuidedRecord = copy(windChanges = windChanges + at)

    fun finish(at: Instant): GuidedRecord = copy(rods = rods.map { it.copy(log = it.log.finish(at)) })
}
