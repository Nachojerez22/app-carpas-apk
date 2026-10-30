package com.nachojerez.carpstrategy.domain.guided

/**
 * Tipo de caña (CONOCIMIENTO.md §5.9.13, decisión del usuario):
 * - [FIXED]: caña fija (sin carrete) de 9, 8, 7 o 5 m. Pesca a fondo, a media agua o en
 *   superficie con flotador de 3 a 4 g, siempre al alcance de la caña. Nunca con boilies.
 * - [CARP]: caña de carpfishing (con carrete). Siempre a fondo (el pop-up corto también está
 *   anclado al fondo), de 7 a 100 m, con plomo de 40 a 90 g. Vale cualquier cebo.
 */
enum class RodKind { FIXED, CARP }

/** Una caña que llevas hoy: nombre, tipo y, si es fija, su longitud. */
data class RodSetup(
    val name: String,
    val kind: RodKind? = null,
    /** Longitud en metros de la caña fija; null en carpfishing. */
    val lengthM: Int? = null,
)

/** Qué permite cada tipo de caña. Funciones puras: lo usan el plan, la sesión guiada y la IA. */
object RodRules {
    /** Longitudes de tus cañas fijas. */
    val FIXED_LENGTHS_M: List<Int> = listOf(9, 8, 7, 5)

    /** Carpfishing: distancia de pesca. */
    const val CARP_MIN_M = 7.0
    const val CARP_MAX_M = 100.0

    /** Fija: flotador. */
    const val FLOAT_MIN_G = 3
    const val FLOAT_MAX_G = 4

    /**
     * 🟣 Viento fuerte (km/h, como `viento_fuerte_oleaje` de rules.json): la app propone las fijas
     * más cortas, que se manejan mejor.
     */
    const val STRONG_WIND_KMH = 15.0

    /** Carpfishing: plomo. */
    const val LEAD_MIN_G = 40
    const val LEAD_MAX_G = 90

    /** Cebos que no se pescan con la fija: los boilies (también el pop-up y los duros). */
    val FIXED_FORBIDDEN_BAITS: Set<BaitType> = setOf(BaitType.BOILIE, BaitType.BOILIE_HARD, BaitType.POPUP)

    /** Columnas de cada tipo; sin tipo, todas. La media agua de la fija es [Column.ZIG] (con flotador). */
    fun columns(kind: RodKind?): Set<Column> = when (kind) {
        RodKind.FIXED -> setOf(Column.BOTTOM, Column.ZIG, Column.SURFACE)
        RodKind.CARP -> setOf(Column.BOTTOM, Column.POPUP)
        null -> Column.entries.toSet()
    }

    fun columnAllowed(kind: RodKind?, column: Column): Boolean = column in columns(kind)

    fun baitAllowed(kind: RodKind?, type: BaitType?): Boolean = kind != RodKind.FIXED || type !in FIXED_FORBIDDEN_BAITS

    /**
     * Distancia de pesca en metros. La fija llega hasta su longitud (el flotador cae al alcance
     * de la punta); sin longitud, hasta la más larga.
     */
    fun distanceRange(kind: RodKind?, lengthM: Int?): ClosedFloatingPointRange<Double>? = when (kind) {
        RodKind.FIXED -> 0.0..(lengthM ?: FIXED_LENGTHS_M.max()).toDouble()
        RodKind.CARP -> CARP_MIN_M..CARP_MAX_M
        null -> null
    }

    /** Peso del flotador (fija) o del plomo (carpfishing) en gramos. */
    fun weightRange(kind: RodKind?): IntRange? = when (kind) {
        RodKind.FIXED -> FLOAT_MIN_G..FLOAT_MAX_G
        RodKind.CARP -> LEAD_MIN_G..LEAD_MAX_G
        null -> null
    }

    /** Distancia de una caña recortada a lo que permite su tipo (null si no hay distancia). */
    fun clampDistance(setup: RodSetup?, distanceM: Double?): Double? {
        distanceM ?: return null
        val range = distanceRange(setup?.kind, setup?.lengthM) ?: return distanceM
        return distanceM.coerceIn(range.start, range.endInclusive)
    }

    /** El equipo que vale para esta caña: sin boilies en la fija. */
    fun inventoryFor(kind: RodKind?, inventory: List<GearItem>): List<GearItem> =
        inventory.filter { it.category != GearCategory.BAIT || baitAllowed(kind, it.baitType) }

    /** Cebos preferidos que valen para esta caña; si no queda ninguno, maíz (vale en las dos). */
    fun baitsFor(kind: RodKind?, preferred: List<BaitType>): List<BaitType> =
        preferred.filter { baitAllowed(kind, it) }.ifEmpty { listOf(BaitType.MAIZE) }
}

/** La caña del equipo como [RodSetup] (null si no es una caña). */
fun GearItem.toRodSetup(): RodSetup? = if (category == GearCategory.ROD) {
    RodSetup(name.trim(), rodKind, rodLengthM.takeIf { rodKind == RodKind.FIXED })
} else {
    null
}

/** Todas las cañas que llevas hoy, en el orden de tu equipo (la de carpfishing, siempre). */
fun List<GearItem>.carriedRods(): List<RodSetup> =
    filter { it.category == GearCategory.ROD && it.carriedToday }.mapNotNull { it.toRodSetup() }

/**
 * Las cañas del plan de hoy (como mucho [GuidedSessions.MAX_RODS]), en el orden de tu equipo. La
 * de carpfishing siempre entra (§5.9.13, decisión del usuario). Si llevas más fijas de las que
 * caben, la app propone cuáles con [pickRods]; la IA puede cambiarlas ([withFixedChoice]).
 */
fun List<GearItem>.rodsToday(windKmh: Double? = null): List<RodSetup> = pickRods(carriedRods(), windKmh)

/**
 * Qué cañas monta hoy la app cuando llevas más de las que caben (§5.9.13, 🟣): primero las que no
 * son fijas (la de carpfishing) y, en los sitios que quedan, las fijas de más alcance; con viento
 * de [RodRules.STRONG_WIND_KMH] o más, las más cortas, que se manejan mejor. Sin pasar del máximo
 * no se quita ninguna.
 */
fun pickRods(carried: List<RodSetup>, windKmh: Double? = null): List<RodSetup> {
    val max = GuidedSessions.MAX_RODS
    if (carried.size <= max) return carried
    val indexed = carried.withIndex()
    val others = indexed.filter { it.value.kind != RodKind.FIXED }.take(max)
    val windy = windKmh != null && windKmh >= RodRules.STRONG_WIND_KMH
    val length = compareBy<IndexedValue<RodSetup>> { it.value.lengthM ?: 0 }
    val fixed = indexed.filter { it.value.kind == RodKind.FIXED }.sortedWith(if (windy) length else length.reversed())
    return (others + fixed.take(max - others.size)).sortedBy { it.index }.map { it.value }
}

/**
 * Las fijas que elige la IA (por nombre, en el orden de las fijas del plan) en lugar de las que
 * propone la app. Null si la elección no vale: otro número de fijas, una que no llevas o repetida.
 */
fun fixedChoice(setups: List<RodSetup>, carried: List<RodSetup>, names: List<String>?): List<RodSetup>? {
    names ?: return null
    val slots = setups.count { it.kind == RodKind.FIXED }
    if (slots == 0 || names.size != slots) return null
    val fixed = carried.filter { it.kind == RodKind.FIXED }
    val chosen = names.map { name -> fixed.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } ?: return null }
    return chosen.takeIf { it.distinct().size == it.size }
}

/** El plan de cañas con las fijas que eligió la IA; si su elección no vale, el de la app. */
fun withFixedChoice(setups: List<RodSetup>, carried: List<RodSetup>, names: List<String>?): List<RodSetup> {
    val chosen = fixedChoice(setups, carried, names)?.iterator() ?: return setups
    return setups.map { if (it.kind == RodKind.FIXED) chosen.next() else it }
}

/**
 * Las cañas del plan de Estrategia por nombre (para la sesión guiada), entre las que llevas hoy.
 * Null si alguna ya no está o no hay nombres.
 */
fun rodsByNames(carried: List<RodSetup>, names: List<String>): List<RodSetup>? {
    if (names.isEmpty() || names.size > GuidedSessions.MAX_RODS) return null
    val rods = names.map { name -> carried.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } ?: return null }
    return rods.takeIf { it.distinct().size == it.size }
}

/**
 * Las cañas con las que empieza la sesión guiada: las del último plan de Estrategia ([names], con
 * las fijas que eligiera la IA) si siguen en lo que llevas y está la de carpfishing; si no, las de hoy.
 */
fun List<GearItem>.plannedRods(names: List<String>): List<RodSetup> {
    val carried = carriedRods()
    val planned = rodsByNames(carried, names) ?: return rodsToday()
    val carpMissing = carried.any { it.kind == RodKind.CARP } && planned.none { it.kind == RodKind.CARP }
    return if (carpMissing) rodsToday() else planned
}

/**
 * Cañas del plan: las de tu equipo que llevas hoy o, si no tienes ninguna apuntada, [count]
 * cañas sin tipo (como antes).
 */
fun rodsFor(count: Int, gear: List<GearItem>): List<RodSetup> =
    gear.rodsToday().ifEmpty { List(count.coerceIn(1, GuidedSessions.MAX_RODS)) { RodSetup("") } }
