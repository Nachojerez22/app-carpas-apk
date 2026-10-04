package com.nachojerez.carpstrategy.domain.guided

import com.nachojerez.carpstrategy.domain.assistant.AiExchange
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
 * Condición del tiempo o del agua que el usuario ve durante la sesión (§5.6 «Por condición»).
 * Se activan y desactivan con su hora y pueden coincidir (lluvia fuerte + tormenta + agua turbia).
 * Lluvia ligera y fuerte se excluyen entre sí.
 */
enum class FieldCondition { LIGHT_RAIN, HEAVY_RAIN, STORM, MUDDY_INFLOW }

/** Dónde se vio actividad de carpa (para la IA y el aprendizaje, §9). */
enum class ActivityPlace { SHORE, SURFACE, MIDWATER, BOTTOM }

/** Saltos de carpa vistos desde el aviso anterior. */
enum class JumpCount { NONE, FEW, MANY }

/** Una condición empieza ([active] true) o termina en [time]. */
data class ConditionChange(val time: Instant, val condition: FieldCondition, val active: Boolean)

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
    /** Dónde se vio actividad (orilla, superficie, media agua, fondo). */
    val seenAt: ActivityPlace? = null,
    /** Saltos vistos: ninguno, 1–2 o muchos. */
    val jumps: JumpCount? = null,
) {
    val isCarpCatch: Boolean get() = activity == HookActivity.CATCH && (species == null || species == Species.CARP)

    /** Captura de otra especie (o pequeño sin identificar). */
    val isBycatch: Boolean get() = activity == HookActivity.CATCH && species != null && species != Species.CARP
}

/** Tipo de paso de la escalera (§5.9.1–§5.9.2). */
enum class StepKind { INITIAL, PRESENTATION, RIG, COLUMN, DISTANCE, ZONE, ANTI_CRAB, SELECTIVE, INFLOW }

/** Por qué se propone el paso: la situación diagnosticada. */
enum class Situation { START, NO_SIGNALS, SIGNALS_NO_BITES, TOUCHES, CRAB_OR_SMALL_FISH, OTHER_FISH, ASSISTANT }

/** Quién propone: el motor de reglas o el asistente de IA (fase 8: «¿IA o reglas?»). */
enum class ProposalSource { RULES, AI }

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
    val source: ProposalSource = ProposalSource.RULES,
    /** Qué hacer y por qué, en palabras de la IA (ya validado). */
    val note: String? = null,
    /** Puesto de «Mis puestos» al que propone ir (solo cambios de zona). */
    val spotName: String? = null,
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

    /** Cambios de zona aceptados (la boca del arroyo también es moverse); hay un máximo por fase. */
    val zoneChanges: Int get() = segments.count { it.kind == StepKind.ZONE || it.kind == StepKind.INFLOW }

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
    /** Lluvia, tormenta y entrada de agua turbia anotadas, en orden. */
    val conditions: List<ConditionChange> = emptyList(),
    /** Copia del puesto elegido al empezar (si luego lo editas, la sesión guarda cómo era). */
    val spot: Spot? = null,
    /** El tiempo en cada aviso (previsión + respuestas del usuario), en orden. */
    val weather: List<WeatherSnapshot> = emptyList(),
    /** Consultas al asistente de IA (válidas o no), en orden. */
    val ai: List<AiExchange> = emptyList(),
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

    /** Condiciones activas en [at]. */
    fun activeConditions(at: Instant): Set<FieldCondition> = conditions
        .filter { !it.time.isAfter(at) }
        .fold(emptySet()) { acc, change -> if (change.active) acc + change.condition else acc - change.condition }

    /** Activa o desactiva una condición; empezar una lluvia termina la otra intensidad. */
    fun withCondition(at: Instant, condition: FieldCondition, active: Boolean): GuidedRecord {
        val current = activeConditions(at)
        if ((condition in current) == active) return this
        val other = when (condition) {
            FieldCondition.LIGHT_RAIN -> FieldCondition.HEAVY_RAIN
            FieldCondition.HEAVY_RAIN -> FieldCondition.LIGHT_RAIN
            else -> null
        }
        val closeOther = other?.takeIf { active && it in current }?.let { ConditionChange(at, it, false) }
        return copy(conditions = conditions + listOfNotNull(closeOther) + ConditionChange(at, condition, active))
    }

    /**
     * Añade el tiempo de un aviso. Si el viento gira o sube claramente respecto al aviso anterior,
     * cuenta como cambio de viento (como si lo anotaras tú; queda el dato en [weather]).
     */
    fun withWeather(snapshot: WeatherSnapshot): GuidedRecord {
        val changed = FieldWeather.windChangedSince(weather.lastOrNull(), snapshot)
        val next = copy(weather = weather + snapshot)
        return if (changed) next.withWindChange(snapshot.time) else next
    }

    /** Guarda la respuesta a «¿Llueve?» o «¿Hay tormenta?» en el último aviso. */
    fun withWeatherAnswer(question: WeatherQuestion, answer: Boolean): GuidedRecord {
        val last = weather.lastOrNull() ?: return this
        val answered = when (question) {
            WeatherQuestion.RAIN -> last.copy(rainAnswer = answer)
            WeatherQuestion.STORM -> last.copy(stormAnswer = answer)
        }
        return copy(weather = weather.dropLast(1) + answered)
    }

    fun finish(at: Instant): GuidedRecord = copy(rods = rods.map { it.copy(log = it.log.finish(at)) })
}
