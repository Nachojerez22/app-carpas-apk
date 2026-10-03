package com.nachojerez.carpstrategy.domain.guided

/**
 * Funciones de un cebo (CONOCIMIENTO.md §5.9.5). Un sustituto debe cumplir la misma función,
 * no tener el mismo aspecto: 🟢 el olor atrae y el gusto decide (#46).
 */
enum class BaitFunction { ATTRACTION, RETENTION, SELECTIVITY, VISIBILITY }

/** Fase de la sesión guiada según el agua (§5.9.2); el calor extremo se separa del verano. */
enum class FishingPhase { WINTER, SPRING, SUMMER, HEAT, AUTUMN }

/**
 * Tipos de cebo de la tabla de sustitución (§5.9.5). [selectivity] y [crabResistance] van de 1
 * a 3. [phases] son las fases en que el cebo es razonable. Todos están permitidos en
 * Extremadura (⚖ §7): los prohibidos (cangrejo, almejas, mejillones, peces) no existen aquí.
 */
enum class BaitType(
    val functions: Set<BaitFunction>,
    val phases: Set<FishingPhase>,
    val selectivity: Int,
    val crabResistance: Int,
    /** Debe prepararse antes (chufa y legumbres: remojo ≥ 24 h y hervor ≥ 30 min). */
    val needsPreparation: Boolean = false,
    /** Cebo de anzuelo (frente a cebo solo para cebar, como el pellet o el cañamón). */
    val hookBait: Boolean = true,
) {
    BOILIE(setOf(BaitFunction.SELECTIVITY, BaitFunction.ATTRACTION), FishingPhase.entries.toSet(), 2, 1),
    BOILIE_HARD(setOf(BaitFunction.SELECTIVITY), setOf(FishingPhase.SUMMER, FishingPhase.HEAT, FishingPhase.AUTUMN), 3, 3),
    PELLET(setOf(BaitFunction.ATTRACTION), setOf(FishingPhase.SPRING, FishingPhase.SUMMER, FishingPhase.HEAT, FishingPhase.AUTUMN), 1, 1, hookBait = false),
    MAIZE(setOf(BaitFunction.ATTRACTION, BaitFunction.RETENTION), FishingPhase.entries.toSet(), 1, 2),
    HEMP(setOf(BaitFunction.RETENTION), setOf(FishingPhase.SPRING, FishingPhase.SUMMER, FishingPhase.HEAT, FishingPhase.AUTUMN), 1, 1, hookBait = false),
    TIGERNUT(setOf(BaitFunction.SELECTIVITY), setOf(FishingPhase.SUMMER, FishingPhase.HEAT, FishingPhase.AUTUMN), 3, 3, needsPreparation = true),
    PASTE(setOf(BaitFunction.ATTRACTION), setOf(FishingPhase.WINTER, FishingPhase.SPRING), 1, 1),
    BREAD(setOf(BaitFunction.VISIBILITY), setOf(FishingPhase.SUMMER, FishingPhase.HEAT), 1, 1),
    POPUP(setOf(BaitFunction.VISIBILITY, BaitFunction.SELECTIVITY), FishingPhase.entries.toSet(), 2, 2),
    PVA(setOf(BaitFunction.ATTRACTION, BaitFunction.RETENTION), FishingPhase.entries.toSet(), 1, 1, hookBait = false),
    WORM(setOf(BaitFunction.ATTRACTION), setOf(FishingPhase.WINTER, FishingPhase.SPRING), 1, 1),
    OTHER(emptySet(), FishingPhase.entries.toSet(), 1, 1),
}

/** Posición en la columna de agua (§5.6, §5.9.2). */
enum class Column { BOTTOM, POPUP, ZIG, SURFACE }

/** Montajes del inventario; cada uno pesca en una columna. */
enum class RigType(val column: Column?) {
    HAIR_BOTTOM(Column.BOTTOM),
    POPUP(Column.POPUP),
    ZIG(Column.ZIG),
    SURFACE(Column.SURFACE),
    METHOD(Column.BOTTOM),
    OTHER(null),
}

enum class GearCategory { BAIT, RIG }

/** Elemento del equipo que lleva el usuario (se añade desde la app). */
data class GearItem(
    val id: String,
    val category: GearCategory,
    val name: String,
    val baitType: BaitType? = null,
    val rigType: RigType? = null,
)

/** Cebo elegido para una propuesta: del equipo del usuario si lo tiene. */
data class BaitChoice(
    val type: BaitType,
    /** Elemento del inventario; null si el usuario no lleva ninguno de ese tipo. */
    val item: GearItem?,
    /** True si no se pudo respetar la función pedida (se avisa: menos selectividad). */
    val fallback: Boolean = false,
)

/** Selección de cebos según lo disponible (§5.9.5). Funciones puras. */
object BaitCatalog {
    /**
     * Elige el primer cebo de [preferred] que el usuario lleve; si no lleva ninguno, el que
     * comparta función con el primero preferido y valga para la fase; si tampoco, cualquier cebo
     * de anzuelo que lleve (con aviso). Sin inventario de cebos se propone el primero preferido.
     */
    fun choose(preferred: List<BaitType>, inventory: List<GearItem>, phase: FishingPhase, crab: Boolean = false): BaitChoice {
        val baits = inventory.filter { it.category == GearCategory.BAIT && it.baitType != null }
        val wanted = if (crab) preferred.sortedByDescending { it.crabResistance } else preferred
        if (baits.isEmpty()) return BaitChoice(wanted.first(), null)
        wanted.forEach { type -> baits.firstOrNull { it.baitType == type }?.let { return BaitChoice(type, it) } }
        val target = wanted.first().functions
        val sameFunction = baits
            .filter { val t = it.baitType!!; t.hookBait && t.functions.any { f -> f in target } && phase in t.phases }
            .sortedWith(compareByDescending<GearItem> { if (crab) it.baitType!!.crabResistance else 0 }.thenByDescending { it.baitType!!.selectivity })
            .firstOrNull()
        if (sameFunction != null) return BaitChoice(sameFunction.baitType!!, sameFunction)
        val any = baits.firstOrNull { it.baitType!!.hookBait } ?: return BaitChoice(wanted.first(), null)
        return BaitChoice(any.baitType!!, any, fallback = true)
    }

    /** Montaje del inventario para una columna; null si no lleva ninguno. */
    fun rigFor(column: Column, inventory: List<GearItem>): GearItem? =
        inventory.firstOrNull { it.category == GearCategory.RIG && it.rigType?.column == column }

    /** Cebos de anzuelo de partida por fase (§5.8 y §5.9.5). */
    fun initialBaits(phase: FishingPhase): List<BaitType> = when (phase) {
        FishingPhase.WINTER -> listOf(BaitType.MAIZE, BaitType.PASTE, BaitType.BOILIE)
        FishingPhase.SPRING -> listOf(BaitType.MAIZE, BaitType.BOILIE, BaitType.PASTE)
        FishingPhase.SUMMER -> listOf(BaitType.BOILIE, BaitType.MAIZE, BaitType.TIGERNUT)
        FishingPhase.HEAT -> listOf(BaitType.MAIZE, BaitType.BOILIE, BaitType.TIGERNUT)
        FishingPhase.AUTUMN -> listOf(BaitType.BOILIE, BaitType.MAIZE, BaitType.TIGERNUT)
    }
}
