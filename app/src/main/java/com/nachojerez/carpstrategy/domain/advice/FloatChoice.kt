package com.nachojerez.carpstrategy.domain.advice

import com.nachojerez.carpstrategy.domain.guided.Column
import com.nachojerez.carpstrategy.domain.guided.RodRules
import com.nachojerez.carpstrategy.domain.guided.WindRelation
import com.nachojerez.carpstrategy.domain.rules.Evidence

/** Por qué la app propone esa pluma (CONOCIMIENTO.md §5.9.14). No puntúa: es equipo. */
enum class FloatReason {
    /** Profundidad medida del puesto. */
    DEPTH,

    /** Sin profundidad medida: la del tramo que aconseja la app (poco, media o mucha). */
    DEPTH_BAND,

    /** A media agua o en superficie: pluma ligera para que el cebo caiga despacio (🟡). */
    OFF_BOTTOM,

    /** Sin viento: la más ligera que se mantenga quieta, para ver las picadas finas (🟡). */
    CALM,

    /** Viento y deriva: hace falta más pluma para que no se arrastre (🟡). */
    WIND,

    /** El viento te da de cara: oleaje y deriva hacia la orilla, un punto más (🟣). */
    WIND_FACING,

    /** Viento de espaldas: orilla resguardada, cuenta la mitad (🟣). */
    SHELTERED,

    /** Rachas fuertes. */
    GUSTS,

    /** Agua fría, carpas poco activas y picadas desconfiadas: más ligera (🟡). */
    COLD,

    /** Carpas activas y peces pequeños que se comen el cebo al caer: que baje rápido (🟣). */
    SMALL_FISH,

    /** Ni con la más pesada que tienes se queda quieta: busca resguardo o la fija más corta (🟣). */
    TOO_WINDY,
}

/** Lo que hace falta para elegir pluma: el puesto, la columna y el tiempo de la hora. */
data class FloatInput(
    /** Profundidad medida del puesto (m); null si no la tienes. */
    val depthM: Double?,
    /** Tramo de profundidad que aconseja la app, para cuando no hay medida. */
    val depthBand: DepthBand?,
    val column: Column,
    val windKmh: Double?,
    val gustKmh: Double?,
    /** Cómo le da el viento a tu orilla. */
    val wind: WindRelation?,
    val waterC: Double?,
    /** Hay especies que se comen el cebo (barbos, bogas, alburnos…; §5.11). */
    val smallFish: Boolean = false,
)

/**
 * Pluma recomendada para una caña fija: una de [RodRules.FLOATS_G] (las que tienes), con la de
 * menos peso por si las picadas son desconfiadas ([lighter]) y la de más por si deriva ([heavier]).
 * [targetG] es el peso ideal que sale del cálculo (antes de redondear a tus plumas).
 */
data class FloatChoice(
    val grams: Double,
    val lighter: Double?,
    val heavier: Double?,
    val targetG: Double,
    val reasons: List<FloatReason>,
    val evidence: Evidence = Evidence.PURPLE,
)

/**
 * Qué pluma montar en la fija (CONOCIMIENTO.md §5.9.14, decisión del usuario, nov. 2026). Regla de
 * fondo (🟡, prensa de pesca al coup): **la pluma más ligera que mantenga el cebo quieto y en su
 * sitio**. Sube con la profundidad, el viento, la deriva y los peces pequeños; baja a media agua,
 * con el agua en calma y con carpas poco activas (agua fría). Los números son 🟣: se ajustan con
 * el diario. Función pura.
 */
object FloatChooser {
    /** 🟣 Peso de partida (g) con 0 m y la pendiente por metro de agua con montaje de carpa. */
    const val BASE_G = 1.5
    const val G_PER_M = 0.5

    /** 🟣 Profundidad que se supone para cada tramo cuando el puesto no tiene medida (m). */
    const val SHALLOW_M = 1.5
    const val MEDIUM_M = 3.0
    const val DEEP_M = 4.5

    /** 🟡 A media agua o en superficie la pluma solo «ve» el bajo de línea: como si hubiera 1 m. */
    const val OFF_BOTTOM_M = 1.0

    /** 🟣 Viento: por debajo, calma; luego +0,4 / +0,8 / +1,2 g. */
    const val CALM_KMH = 6.0
    const val BREEZE_KMH = 12.0
    const val WINDY_KMH = 20.0
    const val BREEZE_G = 0.4
    const val MODERATE_G = 0.8
    const val WINDY_G = 1.2

    /** 🟣 De cara ×1,5 (oleaje y deriva hacia ti); de espaldas ×0,5 (resguardo). */
    const val FACING_FACTOR = 1.5
    const val SHELTERED_FACTOR = 0.5

    /** 🟣 Rachas a partir de las que se añade un poco más. */
    const val GUST_KMH = 35.0
    const val GUST_G = 0.3

    /** 🟡 Agua fría (carpas poco activas, picadas finas): más ligera. */
    const val COLD_WATER_C = 12.0
    const val COLD_G = -0.5

    /** 🟣 Con agua templada los peces pequeños están activos: que el cebo baje rápido. */
    const val ACTIVE_WATER_C = 18.0
    const val SMALL_FISH_G = 0.4

    /** 🟣 Margen para quedarse con una pluma un poco más ligera que el peso ideal. */
    const val ROUND_DOWN_G = 0.25

    /** 🟣 Si el ideal pasa de tu pluma más pesada en este margen, avisa de que no basta. */
    const val TOO_WINDY_MARGIN_G = 0.4

    fun choose(input: FloatInput, floats: List<Double> = RodRules.FLOATS_G): FloatChoice? {
        val sorted = floats.distinct().sorted().ifEmpty { return null }
        val reasons = mutableListOf<FloatReason>()
        val bottomDepth = input.depthM?.also { reasons += FloatReason.DEPTH }
            ?: input.depthBand?.let { band ->
                reasons += FloatReason.DEPTH_BAND
                when (band) {
                    DepthBand.SHALLOW -> SHALLOW_M
                    DepthBand.MEDIUM -> MEDIUM_M
                    DepthBand.DEEP -> DEEP_M
                }
            } ?: MEDIUM_M
        val offBottom = input.column == Column.ZIG || input.column == Column.SURFACE
        val depth = if (offBottom) {
            reasons += FloatReason.OFF_BOTTOM
            minOf(bottomDepth, OFF_BOTTOM_M)
        } else {
            bottomDepth
        }
        var target = BASE_G + G_PER_M * depth.coerceAtLeast(0.0)
        target += wind(input, reasons)
        val water = input.waterC
        if (water != null && water < COLD_WATER_C) {
            target += COLD_G
            reasons += FloatReason.COLD
        }
        if (input.smallFish && water != null && water >= ACTIVE_WATER_C && !offBottom) {
            target += SMALL_FISH_G
            reasons += FloatReason.SMALL_FISH
        }
        val index = sorted.indexOfFirst { it >= target - ROUND_DOWN_G }.let { if (it < 0) sorted.lastIndex else it }
        if (target > sorted.last() + TOO_WINDY_MARGIN_G) reasons += FloatReason.TOO_WINDY
        return FloatChoice(
            grams = sorted[index],
            lighter = sorted.getOrNull(index - 1),
            heavier = sorted.getOrNull(index + 1),
            targetG = Math.round(target * 100) / 100.0,
            reasons = reasons,
        )
    }

    /** Gramos de más por el viento (0 en calma) y sus motivos. */
    private fun wind(input: FloatInput, reasons: MutableList<FloatReason>): Double {
        val kmh = input.windKmh ?: return 0.0
        var add = when {
            kmh < CALM_KMH -> {
                reasons += FloatReason.CALM
                return 0.0
            }
            kmh < BREEZE_KMH -> BREEZE_G
            kmh < WINDY_KMH -> MODERATE_G
            else -> WINDY_G
        }
        reasons += FloatReason.WIND
        when (input.wind) {
            WindRelation.FACING -> {
                add *= FACING_FACTOR
                reasons += FloatReason.WIND_FACING
            }
            WindRelation.BEHIND -> {
                add *= SHELTERED_FACTOR
                reasons += FloatReason.SHELTERED
            }
            else -> Unit
        }
        val gust = input.gustKmh
        if (gust != null && gust >= GUST_KMH && input.wind != WindRelation.BEHIND) {
            add += GUST_G
            reasons += FloatReason.GUSTS
        }
        return add
    }
}
