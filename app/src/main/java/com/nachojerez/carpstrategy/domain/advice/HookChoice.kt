package com.nachojerez.carpstrategy.domain.advice

import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.RodKind
import com.nachojerez.carpstrategy.domain.rules.Evidence

/** Por qué la app propone ese anzuelo (CONOCIMIENTO.md §5.9.15). No puntúa: es equipo. */
enum class HookReason {
    /** Agua fría (< 12 °C): carpas poco activas, comen poco y con desconfianza: anzuelo pequeño (🟡). */
    ACTIVITY_LOW,

    /** Agua templada (12–18 °C): actividad media (🟡/🟣). */
    ACTIVITY_MEDIUM,

    /** Agua cálida (≥ 18 °C): carpas activas que comen con ganas: anzuelo más grande (🟡). */
    ACTIVITY_HIGH,

    /** Sin temperatura del agua: se supone actividad media (🟣). */
    ACTIVITY_UNKNOWN,

    /** El cebo no cabe en un anzuelo tan pequeño (chufa, masilla, pan): uno más grande (🟡). */
    BAIT_FIT,

    /** Peces pequeños activos: anzuelo y cebo más grandes (🟡 + 🟢 #45). */
    SMALL_FISH,

    /** Carpfishing con plomo pesado: no más pequeño que [HookChooser.CARP_SMALLEST] (🟡). */
    CARP_LIMIT,

    /** Fija sin freno: no más pequeño que [HookChooser.FIXED_SMALLEST] (🟡). */
    FIXED_LIMIT,

    /** El tamaño ideal no lo tienes: el más cercano de los tuyos (🟣). */
    NOT_OWNED,
}

/** Actividad de las carpas que se deduce del agua (§5.1): cuanto menos comen, más pequeño el anzuelo. */
enum class CarpActivity { LOW, MEDIUM, HIGH }

/** Lo que hace falta para elegir anzuelo: el cebo, la caña y el agua. */
data class HookInput(
    val bait: BaitType?,
    val rodKind: RodKind?,
    val waterC: Double?,
    /** Hay especies que se comen el cebo (barbos, bogas, alburnos…; §5.11). */
    val smallFish: Boolean = false,
)

/**
 * Anzuelo recomendado: uno de [HookChooser.SIZES] (los que tienes; número mayor = anzuelo más
 * pequeño), con el siguiente más pequeño por si hay toques sin clavada ([smaller]) y el siguiente
 * más grande por si se lo comen los peces pequeños ([larger]), ya dentro del límite de la caña.
 * [ideal] es el tamaño que sale del cálculo, aunque no lo tengas.
 */
data class HookChoice(
    val size: Int,
    val smaller: Int?,
    val larger: Int?,
    val ideal: Int,
    val reasons: List<HookReason>,
    val evidence: Evidence = Evidence.PURPLE,
) {
    /** Bajo de línea orientativo (mm) para este anzuelo (🟣). */
    val leaderMm: ClosedFloatingPointRange<Double>? get() = HookChooser.leaderMm(size)
}

/**
 * Qué anzuelo montar (CONOCIMIENTO.md §5.9.15, decisión del usuario, nov. 2026). Regla de fondo
 * (🟡): **cuanto menos activas están las carpas, más pequeño el anzuelo** (agua fría, desconfianza,
 * toques sin clavada); con carpas activas, más grande, que sujeta mejor y no se suelta. El cebo solo
 * pone un límite: que quepa y deje la punta libre. Más grande con peces pequeños. Límite por caña:
 * carpfishing no más pequeño que el 14, fija no más pequeño que el 16. Los números son 🟣: se
 * ajustan con el diario. Función pura.
 */
object HookChooser {
    /** Los anzuelos que tiene el usuario (nov. 2026): del 18 (el más pequeño) al 10. */
    val SIZES: List<Int> = listOf(18, 16, 14, 12, 10)

    /** Escala de tamaños, de más pequeño a más grande (pares, como se venden). */
    private val SCALE: List<Int> = listOf(20, 18, 16, 14, 12, 10, 8, 6, 4, 2)

    /** 🟡 Carpfishing (plomo de 40–90 g): nunca más pequeño que este. */
    const val CARP_SMALLEST = 14

    /** 🟡 Fija (sin freno): nunca más pequeño que este; el 18 no es para la carpa. */
    const val FIXED_SMALLEST = 16

    /** 🟡 Por debajo, carpas poco activas (§5.1: comen y digieren mucho menos). */
    const val LOW_ACTIVITY_WATER_C = 12.0

    /** 🟡 Desde aquí, carpas activas. */
    const val HIGH_ACTIVITY_WATER_C = 18.0

    /** Actividad que se deduce de la temperatura del agua; null si no hay dato. */
    fun activity(waterC: Double?): CarpActivity? = when {
        waterC == null -> null
        waterC < LOW_ACTIVITY_WATER_C -> CarpActivity.LOW
        waterC < HIGH_ACTIVITY_WATER_C -> CarpActivity.MEDIUM
        else -> CarpActivity.HIGH
    }

    /**
     * 🟣 Anzuelo de partida según la actividad y la caña: la fija (cebo directo y sin freno) va un
     * punto más fina que la de carpfishing (pelo y plomo pesado).
     */
    fun baseSize(activity: CarpActivity, kind: RodKind?): Int = when (kind) {
        RodKind.CARP -> when (activity) {
            CarpActivity.LOW -> 14
            CarpActivity.MEDIUM -> 12
            CarpActivity.HIGH -> 10
        }
        else -> when (activity) {
            CarpActivity.LOW -> 16
            CarpActivity.MEDIUM -> 14
            CarpActivity.HIGH -> 12
        }
    }

    /**
     * 🟡 El anzuelo más pequeño en el que cabe el cebo con la punta libre; null si cabe en cualquiera
     * (maíz, lombriz). Con el pelo (carpfishing) el cebo va aparte y casi no limita.
     */
    fun smallestForBait(bait: BaitType?, kind: RodKind?): Int? = when (bait) {
        BaitType.TIGERNUT, BaitType.BOILIE, BaitType.BOILIE_HARD, BaitType.POPUP -> if (kind == RodKind.CARP) null else 12
        BaitType.PASTE, BaitType.BREAD -> 14
        else -> null
    }

    /** 🟣 Bajo de línea orientativo por anzuelo (mm); null fuera de la tabla. */
    fun leaderMm(size: Int): ClosedFloatingPointRange<Double>? = when (size) {
        18 -> 0.12..0.14
        16 -> 0.14..0.16
        14 -> 0.16..0.18
        12 -> 0.18..0.22
        10 -> 0.20..0.25
        else -> null
    }

    /** El anzuelo más pequeño que admite la caña (número más alto); null sin tipo. */
    fun smallestFor(kind: RodKind?): Int? = when (kind) {
        RodKind.CARP -> CARP_SMALLEST
        RodKind.FIXED -> FIXED_SMALLEST
        null -> null
    }

    /** True si [size] es uno de tus anzuelos y lo admite la caña [kind]. */
    fun allowed(size: Int, kind: RodKind?, sizes: List<Int> = SIZES): Boolean =
        size in sizes && smallestFor(kind)?.let { size <= it } != false

    fun choose(input: HookInput, sizes: List<Int> = SIZES): HookChoice? {
        if (input.bait != null && !input.bait.hookBait) return null
        val reasons = mutableListOf<HookReason>()
        val activity = activity(input.waterC)
        reasons += when (activity) {
            CarpActivity.LOW -> HookReason.ACTIVITY_LOW
            CarpActivity.MEDIUM -> HookReason.ACTIVITY_MEDIUM
            CarpActivity.HIGH -> HookReason.ACTIVITY_HIGH
            null -> HookReason.ACTIVITY_UNKNOWN
        }
        // Posición en la escala: +1 = un punto más grande (número menor).
        var step = SCALE.indexOf(baseSize(activity ?: CarpActivity.MEDIUM, input.rodKind))
        if (input.smallFish) {
            step += 1
            reasons += HookReason.SMALL_FISH
        }
        smallestForBait(input.bait, input.rodKind)?.let { fit ->
            if (step < SCALE.indexOf(fit)) {
                step = SCALE.indexOf(fit)
                reasons += HookReason.BAIT_FIT
            }
        }
        val smallest = smallestFor(input.rodKind)
        var ideal = SCALE[step.coerceIn(0, SCALE.lastIndex)]
        if (smallest != null && ideal > smallest) {
            ideal = smallest
            reasons += if (input.rodKind == RodKind.CARP) HookReason.CARP_LIMIT else HookReason.FIXED_LIMIT
        }
        // Tus anzuelos que admite la caña, de más pequeño a más grande.
        val usable = sizes.distinct().filter { smallest == null || it <= smallest }.sortedDescending()
        if (usable.isEmpty()) return null
        val idealIndex = SCALE.indexOf(ideal)
        val size = usable.minWith(compareBy<Int>({ kotlin.math.abs(SCALE.indexOf(it) - idealIndex) }, { -SCALE.indexOf(it) }))
        if (size != ideal) reasons += HookReason.NOT_OWNED
        val i = usable.indexOf(size)
        return HookChoice(
            size = size,
            smaller = usable.getOrNull(i - 1),
            larger = usable.getOrNull(i + 1),
            ideal = ideal,
            reasons = reasons,
        )
    }
}
