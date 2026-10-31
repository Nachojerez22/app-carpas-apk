package com.nachojerez.carpstrategy.domain.guided

import com.nachojerez.carpstrategy.domain.rules.Evidence

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

    /** Engodo o mix comercial para cebar (fase 7.13): atrae y retiene; no va en el anzuelo. */
    GROUNDBAIT_MIX(setOf(BaitFunction.ATTRACTION, BaitFunction.RETENTION), FishingPhase.entries.toSet(), 1, 1, hookBait = false),

    /** Harinas para hacer el engodo (maíz, galleta, girasol…). */
    FLOUR(setOf(BaitFunction.ATTRACTION), FishingPhase.entries.toSet(), 1, 1, hookBait = false),

    /** Líquidos y aditivos (boosters, melazas, aminoácidos). */
    LIQUID(setOf(BaitFunction.ATTRACTION), FishingPhase.entries.toSet(), 1, 1, hookBait = false),
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

/** Cebos, montajes y cañas (fase 7.20: fija o carpfishing, [RodKind]). */
enum class GearCategory { BAIT, RIG, ROD }

/** Elemento del equipo que lleva el usuario (se añade desde la app). */
data class GearItem(
    val id: String,
    val category: GearCategory,
    val name: String,
    val baitType: BaitType? = null,
    val rigType: RigType? = null,
    /** Marca (fase 7.13), escrita por el usuario. */
    val brand: String = "",
    /** Qué lleva: sabor, composición… escrito por el usuario (manda sobre la ficha de la IA). */
    val description: String = "",
    /** Enlace a una tienda o a la ficha del fabricante, para que la IA saque la descripción. */
    val url: String = "",
    /** Lo llevas hoy: los que no, se quedan en el inventario pero no entran en el plan. */
    val available: Boolean = true,
    /** Ficha que preparó la IA (🟣, compruébala). */
    val sheet: BaitSheet? = null,
    /** Caña (fase 7.20): fija o de carpfishing y, si es fija, su longitud en metros. */
    val rodKind: RodKind? = null,
    val rodLengthM: Int? = null,
) {
    /** «Sonubaits · Supercrush Green»: marca y nombre, para la pantalla y la IA. */
    val fullName: String get() = listOf(brand.trim(), name.trim()).filter { it.isNotEmpty() }.joinToString(" · ")
}

/** Solubilidad de un cebo o engodo (cuánto tarda en deshacerse y soltar atracción). */
enum class Solubility { HIGH, MEDIUM, LOW }

/**
 * Ficha de un cebo preparada por la IA (fase 7.13) a partir de la marca, el nombre y, si lo hay,
 * el enlace de la tienda (con búsqueda en Google si la clave lo permite). Es información 🟣 «según
 * la IA»: lo que escribe el usuario manda.
 */
data class BaitSheet(
    val type: BaitType?,
    val composition: String,
    val profile: String = "",
    val functions: Set<BaitFunction> = emptySet(),
    val solubility: Solubility? = null,
    val phases: Set<FishingPhase> = emptySet(),
    val notes: String = "",
    val sources: List<String> = emptyList(),
    val evidence: Evidence,
    val updatedAt: java.time.Instant,
    /** False si se hizo sin buscar en Google (la clave no lo permitía): solo lo que sabe la IA. */
    val searched: Boolean = true,
    /** Si Google no dejó buscar: «Quota» (cuota) u otro error, y lo que dijo (código y mensaje). */
    val searchError: String? = null,
    val searchErrorDetail: String? = null,
)

/**
 * Fichas hechas sin búsqueda porque Google no dejó (fase 7.15): se pueden rehacer. Las de cebos sin
 * enlace, solo si hoy quedan búsquedas ([searchAllowed]); con enlace basta leer la tienda.
 */
fun List<GearItem>.pendingSheets(searchAllowed: Boolean): List<GearItem> = filter { item ->
    val sheet = item.sheet ?: return@filter false
    !sheet.searched && sheet.searchError != null && (item.url.isNotBlank() || searchAllowed)
}

/**
 * Si lo llevas hoy. La caña de carpfishing siempre (decisión del usuario, §5.9.13): sale en todas
 * las salidas, aunque su casilla esté desmarcada.
 */
val GearItem.carriedToday: Boolean get() = available || (category == GearCategory.ROD && rodKind == RodKind.CARP)

/** Lo que llevas hoy (fase 7.13): solo eso entra en el plan, en la IA y en la sesión guiada. */
fun List<GearItem>.today(): List<GearItem> = filter { it.carriedToday }

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
