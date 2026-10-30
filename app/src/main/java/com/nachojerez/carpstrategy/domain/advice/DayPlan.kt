package com.nachojerez.carpstrategy.domain.advice

import com.nachojerez.carpstrategy.domain.assistant.AiGroundbait
import com.nachojerez.carpstrategy.domain.assistant.AiPlan
import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.Column
import com.nachojerez.carpstrategy.domain.guided.FishingPhase
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.GroundbaitLevel
import com.nachojerez.carpstrategy.domain.guided.GuidedEngine
import com.nachojerez.carpstrategy.domain.guided.GuidedSessions
import com.nachojerez.carpstrategy.domain.guided.RodKind
import com.nachojerez.carpstrategy.domain.guided.RodRules
import com.nachojerez.carpstrategy.domain.guided.RodSetup
import com.nachojerez.carpstrategy.domain.guided.StepKind
import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.time.Duration

/** De dónde sale una línea del plan: la app (conocimiento y tus datos) o la IA (validada por la app). */
enum class PlanSource { APP, AI }

/** Qué lleva una caña. [reason] solo lo trae la IA; para la app, la UI lo explica con [proven] y el puesto. */
data class RodLine(
    val rod: Int,
    val spotName: String?,
    val baitType: BaitType?,
    val baitName: String?,
    val rigName: String? = null,
    val column: Column,
    /** Metros medidos del puesto (o los que dice la IA); si no, [distance]. */
    val distanceM: Double?,
    val distance: DistanceBand,
    val depth: DepthBand,
    val proven: Stat<String>? = null,
    val reason: String? = null,
    val evidence: Evidence,
    val source: PlanSource,
    /** La caña de Mi equipo (fase 7.18): nombre, tipo y longitud; null si eliges por número. */
    val setup: RodSetup? = null,
    /** Gramos del flotador o del plomo que dice la IA (dentro de [weightRange]). */
    val weightG: Double? = null,
) {
    /** Flotador (fija) o plomo (carpfishing) de tu rango; null sin tipo de caña. */
    val weightRange: IntRange? get() = RodRules.weightRange(setup?.kind)
}

/** Qué hacer si pasa algo durante la sesión (§5.9): lo mismo que propondrá la sesión guiada. */
enum class Contingency {
    /** Sin señales en [ContingencyLine.after]: primer peldaño de la escalera de la fase. */
    NO_SIGNALS,

    /** Sigue sin señales: cambiar de zona. */
    NO_SIGNALS_ZONE,

    /** Señales (pitidos, bobbins) sin picadas: cambiar la presentación. */
    SIGNALS_NO_BITES,

    /** Ves carpas arriba con calor: zig. */
    CARP_UP,

    /** Otras especies o cangrejo se comen el cebo: más selectivo o más duro. */
    OTHER_SPECIES,

    /** Aviso práctico del viento en tu puesto ([ContingencyLine.windTip]). */
    WIND,
}

data class ContingencyLine(
    val kind: Contingency,
    val after: Duration? = null,
    val step: StepKind? = null,
    val windTip: WindTip? = null,
    val evidence: Evidence,
)

/**
 * El plan del día (fase 7.12): un solo plan por caña para el puesto elegido. Sin IA lo hace la
 * app con [FishingAdvice] (conocimiento, tus datos y tus lecciones); con un plan de la IA ya
 * validado, la IA lo afina caña a caña y la app rellena lo que falte.
 */
data class DayPlan(
    /** Puesto para el que es el plan (el elegido o, en automático, el mejor de la app). */
    val spot: SpotPick?,
    val rods: List<RodLine>,
    val groundbait: GroundbaitLevel?,
    val contingencies: List<ContingencyLine>,
    /** Resumen de la IA (null si el plan es solo de la app). */
    val aiSummary: String? = null,
    /** Cebado en kilos con tu medida (fase 7.13); null sin medida o si no se ceba. */
    val groundbaitAmount: GroundbaitAmount? = null,
    /** Engodo con lo que llevas, si lo propone la IA (ya validado). */
    val aiGroundbait: AiGroundbait? = null,
) {
    val source: PlanSource get() = if (rods.any { it.source == PlanSource.AI }) PlanSource.AI else PlanSource.APP
}

/** Construye el plan del día. Funciones puras. */
object DayPlans {
    const val MAX_RODS = GuidedSessions.MAX_RODS

    /**
     * [ai]: plan de la IA ya validado y hecho para este mismo puesto y estas cañas (si no, null).
     * La 3.ª caña prueba la columna alternativa (zig con calor) si la hay (§5.9.4). [spot]: el
     * elegido o, en automático, el mejor de la app (o el que recomienda la IA).
     */
    fun build(
        advice: FishingAdvice,
        rods: Int,
        phase: FishingPhase,
        gear: List<GearItem>,
        ai: AiPlan? = null,
        spot: SpotPick? = advice.chosen ?: advice.spots.firstOrNull(),
        groundbaitNormalKg: Double? = null,
        setups: List<RodSetup> = emptyList(),
    ): DayPlan {
        val count = (if (setups.isNotEmpty()) setups.size else rods).coerceIn(1, MAX_RODS)
        val app = (1..count).map { rod ->
            val setup = setups.getOrNull(rod - 1)
            val kind = setup?.kind
            // Fase 7.18: solo cebos y columnas que valen para esta caña (la fija sin boilies; carpfishing a fondo).
            val baits = advice.baits.filter { RodRules.baitAllowed(kind, it.type) }
            val bait = baits.takeIf { it.isNotEmpty() }?.let { it[(rod - 1) % it.size] }
                ?: BaitPick(BaitType.MAIZE, null, evidence = Evidence.YELLOW).takeIf { advice.baits.isNotEmpty() }
            val wanted = if (rod == 3 && advice.altColumn != null) advice.altColumn else advice.column
            RodLine(
                rod = rod,
                spotName = spot?.spot?.name,
                baitType = bait?.type,
                baitName = bait?.gearName,
                column = columnFor(kind, wanted, advice.column),
                distanceM = RodRules.clampDistance(setup, spot?.spot?.distanceM),
                distance = advice.distance,
                depth = advice.depth,
                proven = bait?.proven,
                evidence = bait?.evidence ?: Evidence.YELLOW,
                source = PlanSource.APP,
                setup = setup,
            )
        }
        val lines = if (ai == null) app else app.map { line -> merge(line, ai, gear) }
        // Kilos: los que dice la IA (validados con tu medida) o los de tu medida y el nivel.
        val amount = advice.groundbait?.let { level ->
            val base = GroundbaitAmounts.amount(level, groundbaitNormalKg, count)
            val aiKg = ai?.groundbait?.perRodKg
            if (base != null && aiKg != null) {
                val initial = aiKg * GroundbaitAmounts.INITIAL_SHARE
                base.copy(perRodKg = aiKg, totalKg = aiKg * count, initialPerRodKg = initial, rebaitPerRodKg = aiKg - initial)
            } else {
                base
            }
        }
        return DayPlan(
            spot, lines, advice.groundbait, contingencies(advice, phase, spot), ai?.summary?.takeIf { it.isNotBlank() },
            groundbaitAmount = amount,
            aiGroundbait = ai?.groundbait?.takeIf { advice.groundbait != null },
        )
    }

    /**
     * Columna que vale para el tipo de caña: [wanted] si se puede; si no, la principal; y si
     * tampoco, a fondo (vale en las dos).
     */
    fun columnFor(kind: RodKind?, wanted: Column, main: Column): Column = when {
        RodRules.columnAllowed(kind, wanted) -> wanted
        RodRules.columnAllowed(kind, main) -> main
        else -> Column.BOTTOM
    }

    /** La IA manda en lo que dice; lo que no dice lo pone la app. */
    private fun merge(line: RodLine, ai: AiPlan, gear: List<GearItem>): RodLine {
        val r = ai.rods.firstOrNull { it.rod == line.rod } ?: return line
        val baitItem = r.baitName?.let { name ->
            gear.firstOrNull { it.category == GearCategory.BAIT && (it.name.trim().equals(name.trim(), ignoreCase = true) || it.fullName.equals(name.trim(), ignoreCase = true)) }
        }
        return line.copy(
            spotName = r.spotName ?: line.spotName,
            baitType = baitItem?.baitType ?: if (r.baitName != null) null else line.baitType,
            baitName = r.baitName ?: line.baitName,
            rigName = r.rigName ?: line.rigName,
            column = r.column ?: line.column,
            distanceM = r.distanceM ?: line.distanceM,
            weightG = r.weightG ?: line.weightG,
            proven = if (r.baitName == null) line.proven else null,
            reason = r.reason.takeIf { it.isNotBlank() },
            evidence = r.evidence ?: line.evidence,
            source = PlanSource.AI,
        )
    }

    /**
     * Lo que hará la sesión guiada si pasa algo (§5.9.2–§5.9.4, tiempos 🟣 de [GuidedEngine.THRESHOLDS])
     * y los avisos prácticos del viento en el puesto (§5.9.12).
     */
    fun contingencies(advice: FishingAdvice, phase: FishingPhase, spot: SpotPick?): List<ContingencyLine> = buildList {
        val th = GuidedEngine.THRESHOLDS.getValue(phase)
        val ladder = GuidedEngine.noSignalLadder(phase)
        ladder.firstOrNull { it != StepKind.ZONE }?.let { add(ContingencyLine(Contingency.NO_SIGNALS, th.step1, it, evidence = Evidence.PURPLE)) }
        add(ContingencyLine(Contingency.NO_SIGNALS_ZONE, th.step2, StepKind.ZONE, evidence = Evidence.PURPLE))
        add(ContingencyLine(Contingency.SIGNALS_NO_BITES, th.presentation, StepKind.PRESENTATION, evidence = Evidence.YELLOW))
        if (advice.altColumn == Column.ZIG && advice.column != Column.ZIG) add(ContingencyLine(Contingency.CARP_UP, step = StepKind.COLUMN, evidence = Evidence.YELLOW))
        if (advice.species.selective || advice.species.hardBaits) {
            add(ContingencyLine(Contingency.OTHER_SPECIES, step = if (advice.species.hardBaits) StepKind.ANTI_CRAB else StepKind.SELECTIVE, evidence = Evidence.YELLOW))
        }
        spot?.windTips?.forEach { add(ContingencyLine(Contingency.WIND, windTip = it, evidence = Evidence.YELLOW)) }
    }
}
