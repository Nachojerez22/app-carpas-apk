package com.nachojerez.carpstrategy.domain.advice

import com.nachojerez.carpstrategy.domain.derived.SolarCalculator
import com.nachojerez.carpstrategy.domain.derived.SunTimes
import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.Column
import com.nachojerez.carpstrategy.domain.guided.FishingPhase
import com.nachojerez.carpstrategy.domain.guided.GuidedSessions
import com.nachojerez.carpstrategy.domain.guided.SpotStructure
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.reservoir.Reservoir
import com.nachojerez.carpstrategy.domain.reservoir.Reservoirs
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * Lo pescado con una misma configuración: un tramo de una caña en la sesión guiada, o la sesión
 * entera si se anotó sin guiar. Es la unidad con la que se aprende (§9: capturas por hora-caña).
 */
data class FishingUnit(
    val sessionId: Long,
    val start: Instant,
    val end: Instant,
    val rods: Int,
    val baitType: BaitType? = null,
    val baitName: String? = null,
    val column: Column? = null,
    val spotName: String? = null,
    val structure: SpotStructure? = null,
    val catches: List<Instant?> = emptyList(),
    val phase: FishingPhase,
    val waterC: Double? = null,
) {
    val rodHours: Double get() = rods * Duration.between(start, end).toMinutes() / 60.0
}

/** Rendimiento de un valor (un cebo, un puesto…): esfuerzo, carpas y en cuántas sesiones. */
data class Stat<T>(val value: T, val rodHours: Double, val catches: Int, val sessions: Int) {
    val perRodHour: Double get() = if (rodHours > 0) catches / rodHours else 0.0
}

/** Lo que te ha funcionado en condiciones parecidas a las de ahora. */
data class ExperienceSummary(
    val sessions: Int,
    val rodHours: Double,
    val catches: Int,
    val baits: List<Stat<String>>,
    val baitTypes: List<Stat<BaitType>>,
    val spots: List<Stat<String>>,
    val columns: List<Stat<Column>>,
    val dayParts: List<Stat<DayPart>>,
    /** True si no había bastante en este embalse y se usan sesiones de otros. */
    val otherReservoirs: Boolean = false,
) {
    val perRodHour: Double get() = if (rodHours > 0) catches / rodHours else 0.0

    /**
     * 🟣 «Te ha funcionado»: al menos [MIN_CATCHES] carpas en [MIN_SESSIONS] sesiones y un
     * rendimiento claramente mejor que tu media en estas condiciones.
     */
    fun <T> proven(stat: Stat<T>): Boolean =
        stat.catches >= MIN_CATCHES && stat.sessions >= MIN_SESSIONS && stat.perRodHour >= perRodHour * BETTER_THAN_AVERAGE

    companion object {
        const val MIN_CATCHES = 2
        const val MIN_SESSIONS = 2
        const val BETTER_THAN_AVERAGE = 1.25
        val EMPTY = ExperienceSummary(0, 0.0, 0, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/** Aprender del diario. Funciones puras. */
object Experience {
    /** 🟣 Agua «parecida»: ± 3 °C. */
    const val SIMILAR_WATER_C = 3.0

    /** Paso para repartir el esfuerzo entre las franjas del día. */
    private val STEP: Duration = Duration.ofMinutes(15)

    /** Unidades de las sesiones terminadas (la guiada, por tramos de cada caña). */
    fun units(sessions: List<Session>): List<FishingUnit> = sessions.filter { it.end != null }.flatMap { s ->
        val end = s.end!!
        val phase = GuidedSessions.phaseOf(s)
        val water = s.context?.waterTempC
        val record = s.guided
        if (record != null && record.rods.isNotEmpty()) {
            record.rods.flatMap { rod ->
                rod.log.segments.map { seg ->
                    FishingUnit(
                        sessionId = s.id,
                        start = seg.start,
                        end = seg.end ?: end,
                        rods = 1,
                        baitType = seg.bait,
                        baitName = seg.baitName,
                        column = seg.column,
                        spotName = record.spot?.name,
                        structure = record.spot?.structure,
                        catches = seg.checkIns.filter { it.isCarpCatch }.map { it.time },
                        phase = phase,
                        waterC = water,
                    )
                }
            }
        } else {
            listOf(
                FishingUnit(
                    sessionId = s.id,
                    start = s.start,
                    end = end,
                    rods = s.rods.coerceAtLeast(1),
                    baitName = s.bait.trim().ifEmpty { null },
                    spotName = s.zoneDetail.trim().ifEmpty { null },
                    catches = s.catches.map { it.time },
                    phase = phase,
                    waterC = water,
                ),
            )
        }
    }.filter { it.end.isAfter(it.start) }

    /** Condiciones parecidas: la misma fase o el agua a ± [SIMILAR_WATER_C]. */
    fun similar(units: List<FishingUnit>, phase: FishingPhase, waterC: Double?): List<FishingUnit> = units.filter { u ->
        u.phase == phase || (waterC != null && u.waterC != null && abs(u.waterC - waterC) <= SIMILAR_WATER_C)
    }

    fun summarize(units: List<FishingUnit>, sessions: List<Session>, zone: ZoneId): ExperienceSummary {
        if (units.isEmpty()) return ExperienceSummary.EMPTY
        val locations = sessions.associate { it.id to it.location }
        fun <T : Any> stats(key: (FishingUnit) -> T?): List<Stat<T>> = units.mapNotNull { u -> key(u)?.let { it to u } }
            .groupBy({ it.first }, { it.second })
            .map { (value, list) -> Stat(value, list.sumOf { it.rodHours }, list.sumOf { it.catches.size }, list.map { it.sessionId }.distinct().size) }
            .sortedByDescending { it.perRodHour }

        // Franjas: el esfuerzo se reparte cada 15 min y las capturas van a la franja de su hora.
        val partHours = mutableMapOf<DayPart, Double>()
        val partCatches = mutableMapOf<DayPart, Int>()
        val partSessions = mutableMapOf<DayPart, MutableSet<Long>>()
        // Las horas del sol se calculan una vez por sesión y día.
        val sunCache = mutableMapOf<Pair<Long, LocalDate>, SunTimes>()
        units.forEach { u ->
            val point = locations[u.sessionId] ?: return@forEach
            fun partOf(t: Instant): DayPart {
                val date = t.atZone(zone).toLocalDate()
                return DayParts.of(t, sunCache.getOrPut(u.sessionId to date) { SolarCalculator.sunTimes(date, point) })
            }
            var t = u.start
            while (t.isBefore(u.end)) {
                val part = partOf(t)
                partHours[part] = (partHours[part] ?: 0.0) + u.rods * STEP.toMinutes() / 60.0
                partSessions.getOrPut(part) { mutableSetOf() } += u.sessionId
                t = t.plus(STEP)
            }
            u.catches.filterNotNull().forEach { c -> partOf(c).let { partCatches[it] = (partCatches[it] ?: 0) + 1 } }
        }
        val dayParts = partHours.map { (part, hours) -> Stat(part, hours, partCatches[part] ?: 0, partSessions[part]?.size ?: 0) }
            .sortedByDescending { it.perRodHour }

        return ExperienceSummary(
            sessions = units.map { it.sessionId }.distinct().size,
            rodHours = units.sumOf { it.rodHours },
            catches = units.sumOf { it.catches.size },
            baits = stats { it.baitName },
            baitTypes = stats { it.baitType },
            spots = stats { it.spotName },
            columns = stats { it.column },
            dayParts = dayParts,
        )
    }

    /** Sesiones parecidas en el embalse con las que basta para fiarse de él solo. */
    const val MIN_OWN_SESSIONS = 2

    /**
     * Lo que te ha funcionado en este embalse; si aún no hay [MIN_OWN_SESSIONS] sesiones parecidas
     * en él, en todos tus embalses (marcado con [ExperienceSummary.otherReservoirs]).
     */
    fun forReservoir(
        sessions: List<Session>,
        reservoir: Reservoir?,
        all: List<Reservoir>,
        phase: FishingPhase,
        waterC: Double?,
        zone: ZoneId,
    ): ExperienceSummary {
        if (reservoir == null) return forConditions(sessions, phase, waterC, zone)
        val own = forConditions(Reservoirs.sessionsAt(sessions, reservoir, all), phase, waterC, zone)
        if (own.sessions >= MIN_OWN_SESSIONS) return own
        val everywhere = forConditions(sessions, phase, waterC, zone)
        return if (everywhere.sessions > own.sessions) everywhere.copy(otherReservoirs = true) else own
    }

    /** Resumen de tus sesiones en condiciones parecidas a las de ahora. */
    fun forConditions(sessions: List<Session>, phase: FishingPhase, waterC: Double?, zone: ZoneId): ExperienceSummary =
        summarize(similar(units(sessions), phase, waterC), sessions, zone)
}
