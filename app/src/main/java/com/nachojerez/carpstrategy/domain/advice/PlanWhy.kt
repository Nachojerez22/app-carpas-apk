package com.nachojerez.carpstrategy.domain.advice

import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.Column
import com.nachojerez.carpstrategy.domain.guided.RodKind
import com.nachojerez.carpstrategy.domain.guided.RodRules
import com.nachojerez.carpstrategy.domain.guided.RodSetup
import com.nachojerez.carpstrategy.domain.guided.fixedChoice
import com.nachojerez.carpstrategy.domain.guided.pickRods
import com.nachojerez.carpstrategy.domain.rules.Evidence

/**
 * El porqué de cada elección del plan (0.43.0): la app dice de dónde sale cada cosa (cebo,
 * columna, distancia y caña); la pluma y el anzuelo ya traen sus motivos ([FloatChoice.reasons],
 * [HookChoice.reasons]). Solo explica: no cambia nada del plan.
 */

/** Por qué ese cebo. */
enum class BaitWhy {
    /** Ese cebo (por nombre) te ha dado carpas en condiciones parecidas. */
    PROVEN_NAME,

    /** Ese tipo de cebo te ha dado carpas en condiciones parecidas. */
    PROVEN_TYPE,

    /** Es de los cebos de la fase (por el agua) de §5.9.5. */
    PHASE,

    /** De la fase, pero ordenado por las especies del embalse: selectivo o duro (§5.11). */
    SPECIES,

    /** Los cebos propuestos no valen en esta caña (la fija sin boilies): el que sí vale. */
    ROD_LIMIT,
}

/** Por qué esa columna. */
enum class ColumnWhy {
    /** Es la columna con la que más te ha funcionado en condiciones parecidas. */
    PROVEN,

    /** A fondo, lo de partida sin datos tuyos (§5.9.4). */
    BOTTOM_DEFAULT,

    /** 3.ª caña: prueba la alternativa (zig) porque con calor las carpas pueden subir (§5.9.4). */
    ALT_HEAT,

    /** La columna que tocaba no vale con esta caña (carpfishing solo a fondo). */
    ROD_LIMIT,
}

/** Por qué esa distancia. */
enum class DistanceWhy {
    /** Los metros que apuntaste en el puesto. */
    SPOT,

    /** Los metros del puesto no se alcanzan con esta caña: hasta donde llega. */
    ROD_REACH,

    /** Sin metros en el puesto: la franja de la fase y la hora (§5.8). */
    BAND,
}

/** Por qué esta caña entre las que llevas. */
enum class RodWhy {
    /** Llevas tantas como caben: van todas. */
    ALL_CARRIED,

    /** La de carpfishing siempre va (0.26.1). */
    CARP_ALWAYS,

    /** Llevas más fijas de las que caben: las de más alcance. */
    LONGEST,

    /** Con viento fuerte, las fijas más cortas (se manejan mejor). */
    SHORTEST_WIND,

    /** La eligió la IA entre las que llevas (su motivo va en el de la caña). */
    AI,
}

/** Lo que la app explica de una caña. */
data class AppWhy(
    val bait: BaitWhy? = null,
    val column: ColumnWhy? = null,
    val distance: DistanceWhy? = null,
    val rod: RodWhy? = null,
)

/** Cada elección de una caña de la que la IA puede dar su motivo (JSON `motivos` de cada caña). */
enum class PlanChoice(val key: String) {
    ROD("cana"),
    BAIT("cebo"),
    COLUMN("columna"),
    DISTANCE("distancia"),
    WEIGHT("pluma_o_plomo"),
    HOOK("anzuelo"),
    ;

    companion object {
        fun ofKey(key: String): PlanChoice? = entries.firstOrNull { it.key == key.trim().lowercase() }
    }
}

/** Lo que proponía la app en una caña cuando la IA lo cambia (para enseñar los dos). */
data class AppProposal(
    val baitName: String?,
    val baitType: BaitType?,
    val column: Column,
    val distanceM: Double?,
)

/** Funciones puras para el porqué. */
object PlanWhys {
    fun column(rod: Int, wanted: Column, shown: Column, advice: FishingAdvice): ColumnWhy = when {
        shown != wanted -> ColumnWhy.ROD_LIMIT
        rod == 3 && advice.altColumn != null && shown == advice.altColumn -> ColumnWhy.ALT_HEAT
        advice.columnProven -> ColumnWhy.PROVEN
        else -> ColumnWhy.BOTTOM_DEFAULT
    }

    fun distance(setup: RodSetup?, spotM: Double?): DistanceWhy = when {
        spotM == null -> DistanceWhy.BAND
        RodRules.clampDistance(setup, spotM) != spotM -> DistanceWhy.ROD_REACH
        else -> DistanceWhy.SPOT
    }

    /**
     * Por qué va esta caña: la de carpfishing siempre; las fijas, todas si caben, o las que elige
     * la IA, o las de más alcance (las más cortas con viento fuerte), como [pickRods].
     */
    fun rod(setup: RodSetup?, carried: List<RodSetup>, windKmh: Double?, aiFixed: List<String>?): RodWhy? {
        setup ?: return null
        if (setup.kind == RodKind.CARP) return if (carried.size > MAX_RODS) RodWhy.CARP_ALWAYS else RodWhy.ALL_CARRIED
        if (setup.kind != RodKind.FIXED) return RodWhy.ALL_CARRIED
        if (carried.size <= MAX_RODS) return RodWhy.ALL_CARRIED
        if (fixedChoice(pickRods(carried, windKmh), carried, aiFixed) != null) return RodWhy.AI
        return if (windKmh != null && windKmh >= RodRules.STRONG_WIND_KMH) RodWhy.SHORTEST_WIND else RodWhy.LONGEST
    }

    /** Etiqueta de evidencia de cada porqué de la app (§2). */
    fun evidence(why: ColumnWhy): Evidence = when (why) {
        ColumnWhy.PROVEN -> Evidence.PURPLE
        ColumnWhy.BOTTOM_DEFAULT, ColumnWhy.ALT_HEAT -> Evidence.YELLOW
        ColumnWhy.ROD_LIMIT -> Evidence.YELLOW
    }

    /** Tus metros y la franja de §5.8 son 🟣; el alcance de la caña es un hecho de tu equipo, también 🟣. */
    fun evidence(@Suppress("UNUSED_PARAMETER") why: DistanceWhy): Evidence = Evidence.PURPLE

    /** La elección de fijas es una regla práctica de la app (🟣). */
    fun evidence(@Suppress("UNUSED_PARAMETER") why: RodWhy): Evidence = Evidence.PURPLE

    private const val MAX_RODS = DayPlans.MAX_RODS
}
