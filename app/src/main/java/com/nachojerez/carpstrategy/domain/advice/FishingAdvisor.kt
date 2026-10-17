package com.nachojerez.carpstrategy.domain.advice

import com.nachojerez.carpstrategy.domain.derived.LegalWindow
import com.nachojerez.carpstrategy.domain.derived.SunTimes
import com.nachojerez.carpstrategy.domain.guided.BaitCatalog
import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.Column
import com.nachojerez.carpstrategy.domain.guided.FishingPhase
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.GroundbaitLevel
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.SpotStructure
import com.nachojerez.carpstrategy.domain.guided.Spots
import com.nachojerez.carpstrategy.domain.guided.WindRelation
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirContext
import com.nachojerez.carpstrategy.domain.reservoir.SpeciesEffects
import com.nachojerez.carpstrategy.domain.reservoir.SpeciesRules
import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.time.Duration
import java.time.Instant

/** Qué distancia y profundidad buscar, sin metros inventados (la batimetría no está verificada, §6.3). */
enum class DistanceBand { CLOSE, MEDIUM, FAR }

enum class DepthBand { SHALLOW, MEDIUM, DEEP }

/** Un cebo recomendado: del equipo si lo llevas; [proven] si te ha funcionado en condiciones parecidas. */
data class BaitPick(
    val type: BaitType?,
    val gearName: String?,
    val proven: Stat<String>? = null,
    val evidence: Evidence,
)

/** Un puesto de «Mis puestos» con su puntuación para este momento y por qué. */
data class SpotPick(
    val spot: Spot,
    val score: Int,
    /** 0–3: cuánto encaja su estructura con la fase y la hora (§5.8, §5.9.4). */
    val structureFit: Int,
    val wind: WindRelation?,
    val windBonus: Boolean,
    val proven: Stat<String>? = null,
)

data class AdvisorInput(
    val now: Instant,
    val phase: FishingPhase,
    val waterC: Double?,
    val sun: SunTimes,
    val legal: LegalWindow?,
    val windFromDeg: Double? = null,
    val windKmh: Double? = null,
    val spots: List<Spot> = emptyList(),
    val gear: List<GearItem> = emptyList(),
    val experience: ExperienceSummary = ExperienceSummary.EMPTY,
    val lessons: List<Lesson> = emptyList(),
    /** Embalse activo con su ficha (especies, si se puede cebar). */
    val reservoir: ReservoirContext? = null,
)

/** «Qué hago ahora»: todo lo necesario para empezar a pescar, de una vez. */
data class FishingAdvice(
    /** Momento para el que se aconseja (ahora, o el próximo amanecer si es de noche). */
    val forTime: Instant,
    /** True si no es para ahora sino para el próximo amanecer. */
    val deferred: Boolean,
    val dayPart: DayPart,
    val baits: List<BaitPick>,
    val column: Column,
    /** Alternativa si ves carpas arriba (verano y calor). */
    val altColumn: Column?,
    val columnProven: Boolean,
    val spots: List<SpotPick>,
    val distance: DistanceBand,
    val depth: DepthBand,
    /** Null si en este embalse no se puede cebar (⚖ abastecimiento o plan del coto). */
    val groundbait: GroundbaitLevel?,
    /** Posible embalse de abastecimiento sin confirmar: confirma si se puede cebar. */
    val groundbaitDoubt: Boolean = false,
    /** Ajustes por las especies del embalse (§5.11). */
    val species: SpeciesEffects = SpeciesEffects(),
    /** Franjas del día que mejor te han ido en condiciones parecidas. */
    val bestDayParts: List<Stat<DayPart>>,
    val lessons: List<Lesson>,
    val experience: ExperienceSummary,
)

/**
 * Recomendación concreta para pescar más (fase 7.5): cebo, presentación, puesto, distancia,
 * profundidad y cebado según la fase (agua), la hora del día, el viento, tu equipo, tus puestos,
 * lo que te ha funcionado y tus lecciones aceptadas. Base: tablas de CONOCIMIENTO.md (§5.8,
 * §5.9.4, §5.9.5); tus datos pasan por delante cuando hay suficientes. Función pura.
 */
object FishingAdvisor {
    /** 🟣 A partir de esta velocidad el viento cuenta para elegir orilla. */
    const val WIND_MIN_KMH = 6.0

    /** 🟣 Puntos extra de un puesto que te ha funcionado en condiciones parecidas. */
    const val PROVEN_SPOT_BONUS = 3

    /** Margen tras el inicio del día para aconsejar «el próximo amanecer». */
    private val NEXT_DAWN_OFFSET: Duration = Duration.ofMinutes(30)

    fun advise(input: AdvisorInput): FishingAdvice {
        val (forTime, dayPart) = moment(input)
        val phase = input.phase
        val exp = input.experience
        val spotNames = input.spots.map { it.name }
        val lessons = Lessons.matching(input.lessons, phase, input.waterC, dayPart, spotNames, input.reservoir?.reservoir?.id)
        val effects = SpeciesRules.effects(input.reservoir?.species.orEmpty())

        val baits = baits(input, exp, effects)
        val provenColumn = exp.columns.firstOrNull { exp.proven(it) }?.value
        val column = provenColumn ?: Column.BOTTOM
        val altColumn = if ((phase == FishingPhase.SUMMER || phase == FishingPhase.HEAT) && column != Column.ZIG) Column.ZIG else null

        val spots = input.spots.map { spot -> pick(spot, input, dayPart, exp, lessons) }
            .sortedWith(compareByDescending<SpotPick> { it.score }.thenByDescending { it.structureFit }.thenBy { it.spot.name })

        val (distance, depth) = distanceDepth(phase, dayPart)
        return FishingAdvice(
            forTime = forTime,
            deferred = forTime != input.now,
            dayPart = dayPart,
            baits = baits,
            column = column,
            altColumn = altColumn,
            columnProven = provenColumn != null,
            spots = spots,
            distance = distance,
            depth = depth,
            groundbait = if (input.reservoir?.groundbaitAllowed == false) null else groundbait(phase, dayPart),
            groundbaitDoubt = input.reservoir?.groundbaitDoubt == true,
            species = effects,
            bestDayParts = exp.dayParts.filter { exp.proven(it) && it.value != DayPart.NIGHT },
            lessons = lessons,
            experience = exp,
        )
    }

    /** Ahora si es de día; si no, el próximo amanecer (no se aconseja pescar de noche). */
    private fun moment(input: AdvisorInput): Pair<Instant, DayPart> {
        val now = input.now
        val part = DayParts.of(now, input.sun)
        val legal = input.legal
        val outside = legal != null && now !in legal
        if (part != DayPart.NIGHT && !outside) return now to part
        val start = legal?.start ?: input.sun.sunrise ?: return now to DayPart.DAWN
        val next = if (now.isBefore(start)) start else start.plus(Duration.ofDays(1))
        return next.plus(NEXT_DAWN_OFFSET) to DayPart.DAWN
    }

    /**
     * Dos cebos para comparar entre cañas: primero lo que te ha funcionado (por nombre y que lo
     * lleves), luego los tipos que te han funcionado y, por último, los de la fase (§5.9.5).
     */
    private fun baits(input: AdvisorInput, exp: ExperienceSummary, effects: SpeciesEffects): List<BaitPick> {
        val baitsInGear = input.gear.filter { it.category == GearCategory.BAIT }
        val picks = mutableListOf<BaitPick>()
        fun add(pick: BaitPick) {
            if (picks.size >= 2) return
            if (picks.any { (it.gearName != null && it.gearName == pick.gearName) || (it.gearName == null && pick.gearName == null && it.type == pick.type) }) return
            picks += pick
        }
        exp.baits.filter { exp.proven(it) }.forEach { stat ->
            val item = baitsInGear.firstOrNull { it.name.trim().equals(stat.value.trim(), ignoreCase = true) }
            if (item != null) add(BaitPick(item.baitType, item.name, stat, Evidence.PURPLE))
        }
        exp.baitTypes.filter { exp.proven(it) }.forEach { stat ->
            val item = baitsInGear.firstOrNull { it.baitType == stat.value }
            if (item != null) add(BaitPick(item.baitType, item.name, Stat(item.name, stat.rodHours, stat.catches, stat.sessions), Evidence.PURPLE))
        }
        val base = speciesOrder(BaitCatalog.initialBaits(input.phase), effects)
        base.indices.forEach { k ->
            if (picks.size >= 2) return@forEach
            val rotated = base.drop(k) + base.take(k)
            val excluded = picks.mapNotNull { it.type }.toSet()
            val choice = BaitCatalog.choose(rotated.filterNot { it in excluded }.ifEmpty { rotated }, input.gear.filterNot { it.baitType in excluded }, input.phase, crab = effects.hardBaits)
            add(BaitPick(choice.type, choice.item?.name, null, Evidence.YELLOW))
        }
        return picks
    }

    /**
     * 🟡 Con especies que se comen los cebos pequeños o blandos (barbo, boga, carpín, pez gato,
     * cangrejo…), primero los selectivos y duros de la fase; los poco selectivos, al final (§5.11).
     */
    fun speciesOrder(base: List<BaitType>, effects: SpeciesEffects): List<BaitType> {
        if (!effects.selective && !effects.hardBaits) return base
        val extra = buildList {
            if (effects.hardBaits) addAll(listOf(BaitType.BOILIE_HARD, BaitType.TIGERNUT))
            add(BaitType.BOILIE)
            add(BaitType.POPUP)
        }
        // Con especies que vacían el cebo, fuera los poco selectivos (maíz, pasta, pan…).
        val all = (extra + base).distinct().let { list -> if (effects.selective) list.filter { it.selectivity >= 2 }.ifEmpty { list } else list }
        return all.sortedWith(
            compareByDescending<BaitType> { if (effects.hardBaits) it.crabResistance else 0 }
                .thenByDescending { it.selectivity },
        )
    }

    private fun pick(spot: Spot, input: AdvisorInput, dayPart: DayPart, exp: ExperienceSummary, lessons: List<Lesson>): SpotPick {
        val fit = structureFit(spot.structure, input.phase, dayPart)
        val wind = input.windKmh?.takeIf { it >= WIND_MIN_KMH }?.let { Spots.windRelation(spot.facingDeg, input.windFromDeg, it) }
        // 🟡 Viento cálido de cara acumula agua cálida y alimento; en invierno, mejor resguardado.
        val windBonus = when (input.phase) {
            FishingPhase.WINTER -> wind == WindRelation.BEHIND
            else -> wind == WindRelation.FACING
        }
        val proven = exp.spots.firstOrNull { it.value.trim().equals(spot.name.trim(), ignoreCase = true) }?.takeIf { exp.proven(it) }
        val lessonBonus = lessons.any { it.topic == LessonTopic.WHERE && it.conditions.spotName?.trim()?.equals(spot.name.trim(), ignoreCase = true) == true }
        // Tus datos pesan más que la tabla general (máximo 3): un puesto que te ha funcionado gana.
        val score = fit + (if (windBonus) 1 else 0) + (if (proven != null) PROVEN_SPOT_BONUS else 0) + (if (lessonBonus) 1 else 0)
        return SpotPick(spot, score, fit, wind, windBonus, proven)
    }

    /**
     * 🟣/🟡 Cuánto encaja cada estructura (0–3) según la tabla «Dónde» de §5.8: en invierno caída y
     * cauce (someras solo con sol a mediodía); en primavera someras y reculas; en verano someras
     * al amanecer y anochecer y caída en las horas centrales; con calor, lo más hondo y las entradas;
     * en otoño, litoral nuevo, reculas y caída.
     */
    fun structureFit(structure: SpotStructure, phase: FishingPhase, dayPart: DayPart): Int {
        val edgeHours = dayPart == DayPart.DAWN || dayPart == DayPart.DUSK
        return when (phase) {
            FishingPhase.WINTER -> when (structure) {
                SpotStructure.FIRST_DROP, SpotStructure.OLD_CHANNEL -> 3
                SpotStructure.SHALLOW_EDGE -> if (dayPart == DayPart.MIDDAY) 2 else 0
                SpotStructure.POINT, SpotStructure.FLAT -> 1
                SpotStructure.INLET_BAY -> 0
                SpotStructure.OTHER -> 1
            }
            FishingPhase.SPRING -> when (structure) {
                SpotStructure.SHALLOW_EDGE, SpotStructure.INLET_BAY -> 3
                SpotStructure.POINT, SpotStructure.FLAT -> 2
                SpotStructure.FIRST_DROP -> 1
                SpotStructure.OLD_CHANNEL -> 0
                SpotStructure.OTHER -> 1
            }
            FishingPhase.SUMMER -> when (structure) {
                SpotStructure.SHALLOW_EDGE -> if (edgeHours) 3 else 0
                SpotStructure.FIRST_DROP -> if (edgeHours) 2 else 3
                SpotStructure.OLD_CHANNEL -> if (edgeHours) 1 else 2
                SpotStructure.POINT -> 2
                SpotStructure.INLET_BAY, SpotStructure.FLAT -> 1
                SpotStructure.OTHER -> 1
            }
            FishingPhase.HEAT -> when (structure) {
                SpotStructure.FIRST_DROP, SpotStructure.OLD_CHANNEL -> 3
                SpotStructure.INLET_BAY -> 2
                SpotStructure.SHALLOW_EDGE -> if (edgeHours) 1 else 0
                SpotStructure.POINT -> 1
                SpotStructure.FLAT -> 0
                SpotStructure.OTHER -> 1
            }
            FishingPhase.AUTUMN -> when (structure) {
                SpotStructure.INLET_BAY, SpotStructure.FIRST_DROP -> 3
                SpotStructure.SHALLOW_EDGE, SpotStructure.POINT -> 2
                SpotStructure.OLD_CHANNEL, SpotStructure.FLAT -> 1
                SpotStructure.OTHER -> 1
            }
        }
    }

    /** 🟣 Distancia y profundidad por fase y hora (cerca y somero en las horas de luz baja en verano…). */
    fun distanceDepth(phase: FishingPhase, dayPart: DayPart): Pair<DistanceBand, DepthBand> {
        val edgeHours = dayPart == DayPart.DAWN || dayPart == DayPart.DUSK
        return when (phase) {
            FishingPhase.WINTER -> if (dayPart == DayPart.MIDDAY) DistanceBand.MEDIUM to DepthBand.MEDIUM else DistanceBand.FAR to DepthBand.DEEP
            FishingPhase.SPRING -> if (dayPart == DayPart.MIDDAY) DistanceBand.MEDIUM to DepthBand.MEDIUM else DistanceBand.CLOSE to DepthBand.SHALLOW
            FishingPhase.SUMMER -> when {
                edgeHours -> DistanceBand.CLOSE to DepthBand.SHALLOW
                dayPart == DayPart.MIDDAY -> DistanceBand.FAR to DepthBand.DEEP
                else -> DistanceBand.MEDIUM to DepthBand.MEDIUM
            }
            FishingPhase.HEAT -> if (edgeHours) DistanceBand.MEDIUM to DepthBand.MEDIUM else DistanceBand.FAR to DepthBand.DEEP
            FishingPhase.AUTUMN -> if (dayPart == DayPart.DUSK) DistanceBand.CLOSE to DepthBand.SHALLOW else DistanceBand.MEDIUM to DepthBand.MEDIUM
        }
    }

    /** 🟢 Cebado según la demanda (§5.8): mínimo en invierno, abundante en verano, poco con calor a mediodía. */
    fun groundbait(phase: FishingPhase, dayPart: DayPart): GroundbaitLevel = when (phase) {
        FishingPhase.WINTER -> GroundbaitLevel.LOW
        FishingPhase.SPRING, FishingPhase.AUTUMN -> GroundbaitLevel.NORMAL
        FishingPhase.SUMMER -> GroundbaitLevel.HIGH
        FishingPhase.HEAT -> if (dayPart == DayPart.MIDDAY) GroundbaitLevel.LOW else GroundbaitLevel.NORMAL
    }
}
