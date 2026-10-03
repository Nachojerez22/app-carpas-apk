package com.nachojerez.carpstrategy.domain.guided

import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.time.Duration
import java.time.Instant

/**
 * Tiempos de partida por fase (CONOCIMIENTO.md §5.9.2). **Todos 🟣**: ningún estudio fija cada
 * cuánto cambiar (#48); se calibrarán con las sesiones del usuario.
 */
data class PhaseThresholds(
    /** Sin señales: primer peldaño (columna o distancia). */
    val step1: Duration,
    /** Sin señales: cambio de zona. */
    val step2: Duration,
    /** Señales sin picadas: cambio de presentación. */
    val presentation: Duration,
    val maxZoneChanges: Int,
    /** Intervalo entre avisos. */
    val checkInEvery: Duration,
)

/** Mensajes que acompañan a la evaluación (la UI los muestra con su etiqueta). */
enum class GuidedMessage {
    /** 🟢 Tras una captura: no cambiar nada, recebar poco; la capturabilidad puede bajar. */
    KEEP_AFTER_CATCH,

    /** 🟣 En frío comen poco y en pocos puntos: esperar. */
    WINTER_PATIENCE,

    /** Cambio forzado por el usuario en invierno: se propone, pero se recuerda la paciencia. */
    FORCED_IN_WINTER,

    /** ⚖ Faltan 15 min o menos para el fin del horario legal. */
    LEGAL_END_SOON,

    /** ⚖ Faltan 45 min o menos: solo cambios de presentación, no de zona. */
    NO_ZONE_CHANGES_LEGAL,

    /** Ya se hicieron los cambios de zona máximos de la fase. */
    ZONE_LIMIT_REACHED,

    /** Sin cebo equivalente en el equipo: se propone otro con menos selectividad. */
    NO_EQUIVALENT_BAIT,

    /** 🟡 Chufa o legumbres: siempre preparadas (remojo ≥ 24 h y hervor ≥ 30 min). */
    PREPARE_BAIT,
}

data class GuidedContext(
    val phase: FishingPhase,
    val now: Instant,
    /** Fin del horario legal de hoy; null si no se pudo calcular. */
    val legalEnd: Instant?,
    val inventory: List<GearItem>,
    /** Fin de semana u otros pescadores cerca: la presentación se adelanta 30 min. */
    val highPressure: Boolean = false,
)

data class Evaluation(val proposal: Proposal?, val messages: Set<GuidedMessage>)

/**
 * Sesión guiada (§5.9): diagnóstico en cada aviso y escalera de alternativas. Funciones puras:
 * el instante actual llega en [GuidedContext.now].
 */
object GuidedEngine {
    val FORCE_WINDOW: Duration = Duration.ofMinutes(30)
    const val FORCE_COUNT = 2
    val LEGAL_NO_ZONE: Duration = Duration.ofMinutes(45)
    val LEGAL_END_WARNING: Duration = Duration.ofMinutes(15)
    val HIGH_PRESSURE_ADVANCE: Duration = Duration.ofMinutes(30)
    val CATCH_PAUSE: Duration = Duration.ofMinutes(20)
    val WINTER_RECENT_ACTIVITY: Duration = Duration.ofHours(2)
    const val HEAT_THRESHOLD_C = 28.0

    val THRESHOLDS: Map<FishingPhase, PhaseThresholds> = mapOf(
        FishingPhase.WINTER to PhaseThresholds(Duration.ofMinutes(150), Duration.ofMinutes(240), Duration.ofMinutes(120), 1, Duration.ofMinutes(45)),
        FishingPhase.SPRING to PhaseThresholds(Duration.ofMinutes(90), Duration.ofMinutes(150), Duration.ofMinutes(75), 2, Duration.ofMinutes(30)),
        FishingPhase.SUMMER to PhaseThresholds(Duration.ofMinutes(60), Duration.ofMinutes(105), Duration.ofMinutes(60), 3, Duration.ofMinutes(30)),
        FishingPhase.HEAT to PhaseThresholds(Duration.ofMinutes(60), Duration.ofMinutes(90), Duration.ofMinutes(60), 2, Duration.ofMinutes(30)),
        FishingPhase.AUTUMN to PhaseThresholds(Duration.ofMinutes(90), Duration.ofMinutes(150), Duration.ofMinutes(90), 2, Duration.ofMinutes(30)),
    )

    /** Orden de la escalera sin señales por fase (§5.9.2; en calor extremo se busca antes zona fresca). */
    private val NO_SIGNAL_LADDER: Map<FishingPhase, List<StepKind>> = mapOf(
        FishingPhase.WINTER to listOf(StepKind.DISTANCE, StepKind.COLUMN, StepKind.ZONE),
        FishingPhase.SPRING to listOf(StepKind.DISTANCE, StepKind.COLUMN, StepKind.ZONE),
        FishingPhase.SUMMER to listOf(StepKind.COLUMN, StepKind.DISTANCE, StepKind.ZONE),
        FishingPhase.HEAT to listOf(StepKind.ZONE, StepKind.COLUMN, StepKind.DISTANCE),
        FishingPhase.AUTUMN to listOf(StepKind.DISTANCE, StepKind.COLUMN, StepKind.ZONE),
    )

    /** Fase por el agua: estación de las reglas (por temperatura y tendencia) y calor extremo > 28 °C. */
    fun phase(waterTempC: Double?, season: String?): FishingPhase = when {
        waterTempC != null && waterTempC > HEAT_THRESHOLD_C -> FishingPhase.HEAT
        season == "invierno" -> FishingPhase.WINTER
        season == "primavera" -> FishingPhase.SPRING
        season == "verano" -> FishingPhase.SUMMER
        season == "otono" -> FishingPhase.AUTUMN
        waterTempC == null -> FishingPhase.SPRING
        waterTempC < 10.0 -> FishingPhase.WINTER
        waterTempC > 22.0 -> FishingPhase.SUMMER
        else -> FishingPhase.SPRING
    }

    /** Plan A: cebo de partida de la fase que lleve el usuario, a fondo. */
    fun initialProposal(ctx: GuidedContext, excluded: Set<BaitType> = emptySet()): Proposal {
        val preferred = BaitCatalog.initialBaits(ctx.phase).filterNot { it in excluded }.ifEmpty { BaitCatalog.initialBaits(ctx.phase) }
        val bait = BaitCatalog.choose(preferred, ctx.inventory.filterNot { it.baitType in excluded }, ctx.phase)
        return Proposal(
            kind = StepKind.INITIAL,
            situation = Situation.START,
            bait = bait.type,
            baitName = bait.item?.name,
            baitFallback = bait.fallback,
            column = Column.BOTTOM,
            rigName = BaitCatalog.rigFor(Column.BOTTOM, ctx.inventory)?.name,
            evidence = Evidence.YELLOW,
            createdAt = ctx.now,
        )
    }

    /** Dos «No funciona» en 30 min (contados desde la última propuesta) fuerzan el siguiente paso. */
    fun isForced(log: GuidedLog, now: Instant): Boolean {
        val lastProposal = log.proposals.lastOrNull()?.let { it.decidedAt ?: it.proposal.createdAt } ?: Instant.MIN
        val from = maxOf(now.minus(FORCE_WINDOW), lastProposal)
        return log.allCheckIns.count { it.notWorking && !it.time.isBefore(from) && !it.time.isAfter(now) } >= FORCE_COUNT
    }

    fun evaluate(log: GuidedLog, ctx: GuidedContext): Evaluation {
        val messages = mutableSetOf<GuidedMessage>()
        val toEnd = ctx.legalEnd?.let { Duration.between(ctx.now, it) }
        if (toEnd != null && toEnd <= LEGAL_END_WARNING) messages += GuidedMessage.LEGAL_END_SOON
        val noZone = toEnd != null && toEnd <= LEGAL_NO_ZONE
        if (noZone) messages += GuidedMessage.NO_ZONE_CHANGES_LEGAL
        log.pending?.let { return Evaluation(null, messages + baitMessages(it.proposal)) }

        // Plan A rechazado: otro plan A sin ese cebo.
        val last = log.proposals.lastOrNull()
        if (last != null && last.proposal.kind == StepKind.INITIAL && last.decision == Decision.REJECTED) {
            val excluded = log.proposals.filter { it.proposal.kind == StepKind.INITIAL && it.decision == Decision.REJECTED }.mapNotNull { it.proposal.bait }.toSet()
            val proposal = initialProposal(ctx, excluded + log.missingBaits)
            return Evaluation(proposal, messages + baitMessages(proposal))
        }

        val segment = log.current
        val checks = segment.checkIns
        val lastCheck = checks.lastOrNull()
        val since = Duration.between(segment.start, ctx.now)
        val forced = isForced(log, ctx.now)
        val th = THRESHOLDS.getValue(ctx.phase)
        val winter = ctx.phase == FishingPhase.WINTER
        val proposedHere = log.proposals.filter { !it.proposal.createdAt.isBefore(segment.start) }.map { it.proposal.kind }.toSet()

        fun result(proposal: Proposal?): Evaluation {
            if (proposal != null && forced && winter) messages += GuidedMessage.FORCED_IN_WINTER
            return Evaluation(proposal, messages + (proposal?.let(::baitMessages) ?: emptySet()))
        }

        // D. Captura: no cambiar nada (salvo que el usuario lo fuerce).
        if (lastCheck?.activity == HookActivity.CATCH && !forced) {
            messages += GuidedMessage.KEEP_AFTER_CATCH
            return Evaluation(null, messages)
        }

        // Cebo desaparecido en ≥ 2 revisiones: cangrejo, tortuga o peces pequeños.
        if (checks.count { it.baitState == BaitState.GONE } >= 2 && segment.kind != StepKind.ANTI_CRAB && StepKind.ANTI_CRAB !in proposedHere) {
            return result(antiCrab(ctx, log, forced))
        }

        // C. Toques o picadas falladas en dos avisos seguidos: cambiar montaje o sabor.
        val lastTwo = checks.takeLast(2)
        if (lastTwo.size == 2 && lastTwo.all { it.activity == HookActivity.TOUCHES || it.activity == HookActivity.MISSED } && StepKind.RIG !in proposedHere) {
            return result(rigChange(ctx, log, segment, forced))
        }

        // B. Señales sin actividad: cambiar presentación, no de zona.
        if (lastCheck != null && lastCheck.signals != SignalLevel.NONE && lastCheck.activity == HookActivity.NOTHING) {
            val wait = if (ctx.highPressure) th.presentation.minus(HIGH_PRESSURE_ADVANCE) else th.presentation
            if ((since >= wait || forced) && StepKind.PRESENTATION !in proposedHere) return result(presentation(ctx, log, segment, forced))
            if (winter) messages += GuidedMessage.WINTER_PATIENCE
            return Evaluation(null, messages)
        }

        // A. Sin señales. En invierno, paciencia si hubo actividad en las últimas 2 h.
        if (winter && !forced && hadActivity(log, ctx.now.minus(WINTER_RECENT_ACTIVITY))) {
            messages += GuidedMessage.WINTER_PATIENCE
            return Evaluation(null, messages)
        }
        if (since < th.step1 && !forced) {
            if (winter && checks.isNotEmpty()) messages += GuidedMessage.WINTER_PATIENCE
            return Evaluation(null, messages)
        }
        val kind = nextNoSignalStep(log, ctx, th, noZone, forced, messages, proposedHere) ?: return Evaluation(null, messages)
        return result(noSignalProposal(kind, ctx, segment, forced))
    }

    /** Siguiente peldaño sin señales: el primero de la escalera que no se haya probado desde el último cambio de zona. */
    private fun nextNoSignalStep(
        log: GuidedLog,
        ctx: GuidedContext,
        th: PhaseThresholds,
        noZone: Boolean,
        forced: Boolean,
        messages: MutableSet<GuidedMessage>,
        proposedHere: Set<StepKind>,
    ): StepKind? {
        val sinceZone = log.segments.indexOfLast { it.kind == StepKind.ZONE }.let { if (it < 0) 0 else it }
        // Lo ya probado o rechazado en este tramo no se vuelve a proponer.
        val tried = log.segments.drop(sinceZone).map { it.kind }.toSet() - StepKind.ZONE + (proposedHere - StepKind.ZONE)
        val zoneStart = log.segments[sinceZone].start
        val zoneAllowed = !noZone && log.zoneChanges < th.maxZoneChanges && StepKind.ZONE !in proposedHere &&
            (forced || Duration.between(zoneStart, ctx.now) >= th.step2)
        if (log.zoneChanges >= th.maxZoneChanges) messages += GuidedMessage.ZONE_LIMIT_REACHED
        val ladder = NO_SIGNAL_LADDER.getValue(ctx.phase)
        ladder.firstOrNull { it != StepKind.ZONE && it !in tried }?.let { candidate ->
            // En calor extremo la zona va primero si está permitida.
            if (ladder.first() == StepKind.ZONE && zoneAllowed) return StepKind.ZONE
            return candidate
        }
        if (zoneAllowed) return StepKind.ZONE
        // Todo probado y sin zona disponible: otra distancia sobre la misma estructura.
        return if (forced) StepKind.DISTANCE else null
    }

    private fun hadActivity(log: GuidedLog, since: Instant): Boolean = log.allCheckIns.any {
        !it.time.isBefore(since) && (it.signals != SignalLevel.NONE || it.activity != HookActivity.NOTHING)
    }

    private fun noSignalProposal(kind: StepKind, ctx: GuidedContext, segment: Segment, forced: Boolean): Proposal {
        val column = if (kind == StepKind.COLUMN) nextColumn(segment.column, ctx.phase) else null
        return Proposal(
            kind = kind,
            situation = Situation.NO_SIGNALS,
            column = column,
            rigName = column?.let { BaitCatalog.rigFor(it, ctx.inventory)?.name },
            forced = forced,
            evidence = Evidence.PURPLE,
            createdAt = ctx.now,
        )
    }

    /** B: cebo de anzuelo de alta atracción o contraste (maíz o pop-up), distinto del actual. */
    private fun presentation(ctx: GuidedContext, log: GuidedLog, segment: Segment, forced: Boolean): Proposal {
        val preferred = listOf(BaitType.MAIZE, BaitType.POPUP, BaitType.BOILIE, BaitType.PASTE).filterNot { it == segment.bait || it in log.missingBaits }
        val bait = BaitCatalog.choose(preferred.ifEmpty { listOf(BaitType.MAIZE) }, ctx.inventory, ctx.phase)
        return Proposal(StepKind.PRESENTATION, Situation.SIGNALS_NO_BITES, bait.type, bait.item?.name, bait.fallback, forced = forced, evidence = Evidence.GREEN, createdAt = ctx.now)
    }

    /** C: otro montaje y otro sabor o textura en el anzuelo; el cebado se mantiene. */
    private fun rigChange(ctx: GuidedContext, log: GuidedLog, segment: Segment, forced: Boolean): Proposal {
        val preferred = listOf(BaitType.MAIZE, BaitType.TIGERNUT, BaitType.BOILIE, BaitType.PASTE).filterNot { it == segment.bait || it in log.missingBaits || ctx.phase !in it.phases }
        val bait = BaitCatalog.choose(preferred.ifEmpty { listOf(BaitType.MAIZE) }, ctx.inventory, ctx.phase)
        return Proposal(StepKind.RIG, Situation.TOUCHES, bait.type, bait.item?.name, bait.fallback, forced = forced, evidence = Evidence.YELLOW, createdAt = ctx.now)
    }

    /** Cebo duro o separado del fondo (§5.9.1, 🟡). */
    private fun antiCrab(ctx: GuidedContext, log: GuidedLog, forced: Boolean): Proposal {
        val preferred = listOf(BaitType.TIGERNUT, BaitType.BOILIE_HARD, BaitType.POPUP).filterNot { it in log.missingBaits }
        val bait = BaitCatalog.choose(preferred.ifEmpty { listOf(BaitType.POPUP) }, ctx.inventory, ctx.phase, crab = true)
        return Proposal(
            StepKind.ANTI_CRAB, Situation.CRAB_OR_SMALL_FISH, bait.type, bait.item?.name, bait.fallback,
            column = Column.POPUP, rigName = BaitCatalog.rigFor(Column.POPUP, ctx.inventory)?.name,
            forced = forced, evidence = Evidence.YELLOW, createdAt = ctx.now,
        )
    }

    /** Fondo → pop-up → zig → superficie; zig y superficie solo con calor (§5.6). */
    fun nextColumn(current: Column?, phase: FishingPhase): Column {
        val warm = phase == FishingPhase.SUMMER || phase == FishingPhase.HEAT
        return when (current) {
            null, Column.BOTTOM -> Column.POPUP
            Column.POPUP -> if (warm) Column.ZIG else Column.BOTTOM
            Column.ZIG -> if (warm) Column.SURFACE else Column.BOTTOM
            Column.SURFACE -> Column.BOTTOM
        }
    }

    private fun baitMessages(p: Proposal): Set<GuidedMessage> = buildSet {
        if (p.baitFallback) add(GuidedMessage.NO_EQUIVALENT_BAIT)
        if (p.bait?.needsPreparation == true) add(GuidedMessage.PREPARE_BAIT)
    }

    /**
     * Próximo aviso (§5.9.7): cada 30 min (45 en invierno), 15 min más tras tres respuestas
     * seguidas sin nada, pausa de 20 min tras una captura, contado desde lo último registrado
     * (si registraste algo hace poco, se salta) y nunca después del aviso de fin legal.
     * Si el último aviso mostrado no se contestó, el siguiente cuenta desde él.
     */
    fun nextCheckIn(log: GuidedLog, phase: FishingPhase, now: Instant, legalEnd: Instant?): Instant? {
        var interval = THRESHOLDS.getValue(phase).checkInEvery
        val checks = log.allCheckIns
        val quiet = checks.takeLast(3).let { last3 ->
            last3.size == 3 && last3.all { it.signals == SignalLevel.NONE && it.activity == HookActivity.NOTHING && it.userChange == null }
        }
        if (quiet) interval = interval.plusMinutes(15)
        val lastEvent = checks.lastOrNull()
        if (lastEvent?.activity == HookActivity.CATCH) interval = interval.plus(CATCH_PAUSE)
        val from = listOfNotNull(lastEvent?.time ?: log.current.start, log.alarms.lastOrNull()).max()
        var next = maxOf(from.plus(interval), now.plusSeconds(60))
        if (legalEnd != null) {
            val reminder = legalEnd.minus(LEGAL_END_WARNING)
            if (!now.isBefore(legalEnd)) return null
            if (next.isAfter(reminder)) next = if (reminder.isAfter(now)) reminder else legalEnd
        }
        return next
    }
}
