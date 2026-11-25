package com.nachojerez.carpstrategy.domain.advice

import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.RodKind
import com.nachojerez.carpstrategy.domain.guided.RodSetup
import com.nachojerez.carpstrategy.domain.reservoir.CarpWeight
import com.nachojerez.carpstrategy.domain.rules.Evidence

/**
 * Cómo amortigua la caña el tirón (CONOCIMIENTO.md §5.9.15): es lo que fija el anzuelo más pequeño
 * que aguanta. Fija rígida (sin elástico ni carrete), fija con elástico, carpfishing con cebador
 * (method o in-line, bajo corto) y carpfishing con plomo de 40–90 g.
 */
enum class HookProfile { FIXED_RIGID, FIXED_ELASTIC, CARP_FEEDER, CARP_LEAD }

/** Perfil de anzuelo de una caña; null si no se sabe el tipo. */
fun RodSetup?.hookProfile(): HookProfile? = when (this?.kind) {
    RodKind.FIXED -> if (elastic) HookProfile.FIXED_ELASTIC else HookProfile.FIXED_RIGID
    RodKind.CARP -> if (feeder) HookProfile.CARP_FEEDER else HookProfile.CARP_LEAD
    null -> null
}

/** Por qué la app propone ese anzuelo (CONOCIMIENTO.md §5.9.15). No puntúa: es equipo. */
enum class HookReason {
    /** Agua fría (< 12 °C): carpas poco activas y desconfiadas: anzuelo pequeño (🟡). */
    ACTIVITY_LOW,

    /** Agua templada (12–18 °C): actividad media (🟡). */
    ACTIVITY_MEDIUM,

    /** Agua de 18 a 26 °C: carpas activas que comen con ganas: anzuelo más grande (🟡). */
    ACTIVITY_HIGH,

    /** Agua de más de 26 °C: estrés térmico y menos oxígeno, actividad media (🟢 fisiología + 🟣 corte). */
    HEAT,

    /** Más de 26 °C y calma: poco oxígeno, actividad baja (🟡/🟣). */
    HEAT_CALM,

    /** Sin temperatura del agua: se supone actividad media (🟣). */
    ACTIVITY_UNKNOWN,

    /** Peso habitual de las carpas del embalse (ficha del embalse): pone el anzuelo más pequeño que aguanta. */
    WEIGHT,

    /** Sin peso habitual en la ficha del embalse: se suponen carpas de 5 a 10 kg (🟣). */
    WEIGHT_ASSUMED,

    /** El cebo no cabe en un anzuelo tan pequeño con la punta libre: uno más grande (🟡). */
    BAIT_FIT,

    /** Peces pequeños activos: anzuelo y cebo más grandes (🟡 + 🟢 #45). */
    SMALL_FISH,

    /** Lo ideal sería más pequeño, pero con esa caña y esas carpas no aguanta (🟡). */
    FLOOR,

    /** Fija rígida con carpas de 10 kg o más: no es recomendable, alto riesgo de rotura (🟡). */
    NOT_ADVISED,

    /** Fija con elástico y carpas de más de 15 kg: muy al límite, solo en zona limpia (🟡). */
    AT_LIMIT,

    /** El tamaño ideal no lo tienes: el más cercano de los tuyos (🟣). */
    NOT_OWNED,

    /** Ninguno de tus anzuelos aguanta con esa caña y esas carpas: el más grande que tienes, y te falta uno (🟡). */
    MISSING_STRONGER,
}

/** Actividad de las carpas que se deduce del agua (§5.1): cuanto menos comen, más pequeño el anzuelo. */
enum class CarpActivity { LOW, MEDIUM, HIGH }

/** Lo que hace falta para elegir anzuelo: cebo, caña, agua, viento, peso de las carpas y especies. */
data class HookInput(
    val bait: BaitType?,
    val profile: HookProfile?,
    val waterC: Double?,
    /** Viento de la hora (km/h): con calor y calma, menos oxígeno. */
    val windKmh: Double? = null,
    /** Peso habitual de las carpas del embalse (ficha del embalse); null = sin dato. */
    val carpWeight: CarpWeight? = null,
    /** Hay especies que se comen el cebo (barbos, bogas, alburnos…; §5.11). */
    val smallFish: Boolean = false,
)

/**
 * Anzuelo recomendado: uno de los que tienes (número mayor = anzuelo más pequeño), con el siguiente
 * más pequeño por si hay toques sin clavada ([smaller]) y el siguiente más grande por si se lo comen
 * los peces pequeños ([larger]), ya dentro del límite de la caña y del peso de las carpas. [ideal] es
 * el tamaño que sale del cálculo, aunque no lo tengas; [floor], el más pequeño que aguanta.
 */
data class HookChoice(
    val size: Int,
    val smaller: Int?,
    val larger: Int?,
    val ideal: Int,
    val reasons: List<HookReason>,
    val evidence: Evidence = Evidence.PURPLE,
    val floor: Int? = null,
    /** Grado de elástico orientativo (solo fija con elástico). */
    val elasticGrade: IntRange? = null,
) {
    /** Bajo de línea orientativo (mm) para este anzuelo (🟣). */
    val leaderMm: ClosedFloatingPointRange<Double>? get() = HookChooser.leaderMm(size)
}

/** Tus anzuelos (Mi equipo → Montajes); sin ninguno apuntado, los de [HookChooser.SIZES]. */
fun List<GearItem>.hookSizes(): List<Int> =
    filter { it.category == GearCategory.HOOK }.mapNotNull { it.hookSize }.distinct().sortedDescending().ifEmpty { HookChooser.SIZES }

/**
 * Qué anzuelo montar (CONOCIMIENTO.md §5.9.15, estudio del usuario, nov. 2026). Cuatro piezas:
 * 1. **La caña** ([HookProfile]) y **el peso habitual de las carpas** ([CarpWeight]) ponen el suelo:
 *    el anzuelo más pequeño que aguanta el combate con esa amortiguación.
 * 2. **La actividad** (agua; con más de 26 °C vuelve a bajar) mueve el anzuelo dentro del rango:
 *    menos activas, más pequeño.
 * 3. **El cebo** solo obliga a uno más grande si no cabe con la punta libre.
 * 4. Peces pequeños activos: uno más grande.
 * Los números son 🟣 (tablas del estudio del usuario, sin ensayos con anzuelos del 10 al 18).
 * Función pura.
 */
object HookChooser {
    /** Los anzuelos que dijo tener el usuario (nov. 2026) si no ha apuntado ninguno en Mi equipo. */
    val SIZES: List<Int> = listOf(18, 16, 14, 12, 10)

    /** Tamaños que se pueden apuntar en Mi equipo, del más pequeño al más grande. */
    val CHOICES: List<Int> = listOf(20, 18, 16, 14, 12, 10, 8, 6, 4, 2)

    /** Escala de tamaños, de más pequeño a más grande (pares, como se venden). */
    private val SCALE: List<Int> = CHOICES

    /** 🟡 Por debajo, carpas poco activas (§5.1: comen y digieren mucho menos). */
    const val LOW_ACTIVITY_WATER_C = 12.0

    /** 🟡 Desde aquí, carpas activas. */
    const val HIGH_ACTIVITY_WATER_C = 18.0

    /** 🟢/🟣 Por encima, estrés térmico y menos oxígeno: vuelve a actividad media (o baja con calma). */
    const val HEAT_WATER_C = 26.0

    /** 🟣 Por debajo, calma: con calor, poco oxígeno. */
    const val CALM_KMH = 6.0

    /** 🟣 Sin peso en la ficha: carpas de 5 a 10 kg (lo habitual que cita el estudio para Extremadura). */
    val ASSUMED_WEIGHT: CarpWeight = CarpWeight.FROM_5_TO_10

    /** Anzuelo de partida (baja, media, alta actividad) y el más pequeño que aguanta, por caña y peso. */
    private data class Row(val low: Int, val medium: Int, val high: Int, val floor: Int)

    /** 🟣 Tablas del estudio del usuario (§5.9.15). */
    private val TABLE: Map<HookProfile, Map<CarpWeight, Row>> = mapOf(
        HookProfile.FIXED_RIGID to mapOf(
            CarpWeight.UNDER_2 to Row(16, 14, 14, 16),
            CarpWeight.FROM_2_TO_5 to Row(16, 14, 12, 16),
            CarpWeight.FROM_5_TO_10 to Row(14, 12, 10, 14),
            CarpWeight.FROM_10_TO_15 to Row(10, 10, 10, 10),
            CarpWeight.OVER_15 to Row(10, 10, 10, 10),
        ),
        HookProfile.FIXED_ELASTIC to mapOf(
            CarpWeight.UNDER_2 to Row(18, 16, 16, 18),
            CarpWeight.FROM_2_TO_5 to Row(18, 16, 14, 18),
            CarpWeight.FROM_5_TO_10 to Row(16, 14, 12, 16),
            CarpWeight.FROM_10_TO_15 to Row(12, 12, 10, 12),
            CarpWeight.OVER_15 to Row(10, 10, 8, 10),
        ),
        HookProfile.CARP_FEEDER to mapOf(
            CarpWeight.UNDER_2 to Row(14, 14, 12, 14),
            CarpWeight.FROM_2_TO_5 to Row(14, 12, 10, 14),
            CarpWeight.FROM_5_TO_10 to Row(12, 12, 10, 12),
            CarpWeight.FROM_10_TO_15 to Row(10, 10, 8, 10),
            CarpWeight.OVER_15 to Row(10, 8, 6, 10),
        ),
        HookProfile.CARP_LEAD to mapOf(
            CarpWeight.UNDER_2 to Row(14, 12, 12, 14),
            CarpWeight.FROM_2_TO_5 to Row(12, 12, 10, 12),
            CarpWeight.FROM_5_TO_10 to Row(10, 10, 8, 10),
            CarpWeight.FROM_10_TO_15 to Row(8, 8, 6, 8),
            CarpWeight.OVER_15 to Row(8, 6, 6, 8),
        ),
    )

    /** Sin tipo de caña se usa la fija rígida (la más prudente con anzuelos pequeños). */
    private val DEFAULT_PROFILE = HookProfile.FIXED_RIGID

    /** Actividad que se deduce del agua (y del viento con calor); null si no hay dato. */
    fun activity(waterC: Double?, windKmh: Double? = null): CarpActivity? = when {
        waterC == null -> null
        waterC < LOW_ACTIVITY_WATER_C -> CarpActivity.LOW
        waterC < HIGH_ACTIVITY_WATER_C -> CarpActivity.MEDIUM
        waterC <= HEAT_WATER_C -> CarpActivity.HIGH
        windKmh != null && windKmh < CALM_KMH -> CarpActivity.LOW
        else -> CarpActivity.MEDIUM
    }

    /**
     * 🟡 El anzuelo más pequeño en el que cabe el cebo con la punta libre; null si cabe en cualquiera
     * (maíz). En la fija va clavado directo; en carpfishing, al pelo o con banda.
     */
    fun smallestForBait(bait: BaitType?, profile: HookProfile?): Int? {
        val fixed = profile == null || profile == HookProfile.FIXED_RIGID || profile == HookProfile.FIXED_ELASTIC
        return when (bait) {
            BaitType.WORM -> if (fixed) 14 else 12
            BaitType.PASTE, BaitType.BREAD -> if (fixed) 14 else 12
            BaitType.TIGERNUT, BaitType.BOILIE, BaitType.BOILIE_HARD, BaitType.POPUP -> if (fixed) 12 else 10
            else -> null
        }
    }

    /** El anzuelo más pequeño que aguanta esa caña con esas carpas (número más alto). */
    fun floorFor(profile: HookProfile?, weight: CarpWeight?): Int =
        row(profile ?: DEFAULT_PROFILE, weight ?: ASSUMED_WEIGHT).floor

    /** Grado de elástico orientativo de la fija con elástico (🟣); null en las demás. */
    fun elasticGrade(profile: HookProfile?, weight: CarpWeight?, activity: CarpActivity?): IntRange? {
        if (profile != HookProfile.FIXED_ELASTIC) return null
        return when (weight ?: ASSUMED_WEIGHT) {
            CarpWeight.UNDER_2 -> 6..10
            CarpWeight.FROM_2_TO_5 -> if (activity == CarpActivity.LOW) 8..10 else 10..14
            CarpWeight.FROM_5_TO_10 -> 12..16
            CarpWeight.FROM_10_TO_15 -> 16..20
            CarpWeight.OVER_15 -> 18..25
        }
    }

    /** 🟣 Bajo de línea orientativo por anzuelo (mm); null fuera de la tabla. */
    fun leaderMm(size: Int): ClosedFloatingPointRange<Double>? = when (size) {
        18 -> 0.12..0.14
        16 -> 0.15..0.17
        14 -> 0.17..0.19
        12 -> 0.18..0.22
        10 -> 0.20..0.25
        8, 6 -> 0.25..0.30
        else -> null
    }

    /** True si [size] es uno de tus anzuelos y aguanta con esa caña y esas carpas. */
    fun allowed(size: Int, profile: HookProfile?, weight: CarpWeight? = null, sizes: List<Int> = SIZES): Boolean =
        size in sizes && size <= floorFor(profile, weight)

    private fun row(profile: HookProfile, weight: CarpWeight): Row = TABLE.getValue(profile).getValue(weight)

    fun choose(input: HookInput, sizes: List<Int> = SIZES): HookChoice? {
        if (input.bait != null && !input.bait.hookBait) return null
        val owned = sizes.distinct().filter { it in SCALE }.ifEmpty { return null }
        val profile = input.profile ?: DEFAULT_PROFILE
        val weight = input.carpWeight ?: ASSUMED_WEIGHT
        val reasons = mutableListOf<HookReason>()
        val activity = activity(input.waterC, input.windKmh)
        val water = input.waterC
        reasons += when {
            activity == null -> HookReason.ACTIVITY_UNKNOWN
            water != null && water > HEAT_WATER_C -> if (activity == CarpActivity.LOW) HookReason.HEAT_CALM else HookReason.HEAT
            activity == CarpActivity.LOW -> HookReason.ACTIVITY_LOW
            activity == CarpActivity.MEDIUM -> HookReason.ACTIVITY_MEDIUM
            else -> HookReason.ACTIVITY_HIGH
        }
        reasons += if (input.carpWeight == null) HookReason.WEIGHT_ASSUMED else HookReason.WEIGHT
        val r = row(profile, weight)
        if (profile == HookProfile.FIXED_RIGID && (weight == CarpWeight.FROM_10_TO_15 || weight == CarpWeight.OVER_15)) reasons += HookReason.NOT_ADVISED
        if (profile == HookProfile.FIXED_ELASTIC && weight == CarpWeight.OVER_15) reasons += HookReason.AT_LIMIT
        // Posición en la escala: +1 = un punto más grande (número menor).
        var step = SCALE.indexOf(
            when (activity ?: CarpActivity.MEDIUM) {
                CarpActivity.LOW -> r.low
                CarpActivity.MEDIUM -> r.medium
                CarpActivity.HIGH -> r.high
            },
        )
        if (input.smallFish) {
            step += 1
            reasons += HookReason.SMALL_FISH
        }
        smallestForBait(input.bait, profile)?.let { fit ->
            if (step < SCALE.indexOf(fit)) {
                step = SCALE.indexOf(fit)
                reasons += HookReason.BAIT_FIT
            }
        }
        if (step < SCALE.indexOf(r.floor)) {
            step = SCALE.indexOf(r.floor)
            reasons += HookReason.FLOOR
        }
        val ideal = SCALE[step.coerceIn(0, SCALE.lastIndex)]
        // Tus anzuelos que aguantan, de más pequeño a más grande.
        val usable = owned.filter { it <= r.floor }.sortedDescending()
        val choice = if (usable.isEmpty()) {
            reasons += HookReason.MISSING_STRONGER
            owned.min()
        } else {
            val idealIndex = SCALE.indexOf(ideal)
            usable.minWith(compareBy<Int>({ kotlin.math.abs(SCALE.indexOf(it) - idealIndex) }, { -SCALE.indexOf(it) }))
        }
        if (choice != ideal && HookReason.MISSING_STRONGER !in reasons) reasons += HookReason.NOT_OWNED
        val i = usable.indexOf(choice)
        return HookChoice(
            size = choice,
            smaller = if (i >= 0) usable.getOrNull(i - 1) else null,
            larger = if (i >= 0) usable.getOrNull(i + 1) else null,
            ideal = ideal,
            reasons = reasons,
            floor = r.floor,
            elasticGrade = elasticGrade(profile, weight, activity),
        )
    }
}
