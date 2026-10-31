package com.nachojerez.carpstrategy.ui.strategy

import com.nachojerez.carpstrategy.domain.derived.WaterTempSource
import com.nachojerez.carpstrategy.domain.rules.FavorabilityBand
import com.nachojerez.carpstrategy.domain.rules.RuleLevel
import kotlin.math.abs

/*
 * Reevaluar la salida preparada (fase 7.22): ya en el puesto, antes de activarla, qué ha cambiado
 * desde que la preparaste (escala, nivel que limita, agua, aire, viento y rachas) y si cambiaste lo
 * elegido (puesto, cañas, cebos o tu medida de cebado). Solo informa: tú decides si mantienes el
 * plan o pides uno nuevo.
 */

/** Diferencias que se avisan (🟣: criterio de la app, sin estudio detrás). */
object ReviewThresholds {
    /** °C de agua: un grado ya mueve el nivel de temperatura. 🟣 */
    const val WATER_C = 1.0

    /** °C de aire (media de 24 h). 🟣 */
    const val AIR_C = 3.0

    /** km/h de viento medio: lo que cambia el oleaje y qué orilla recibe. 🟣 */
    const val WIND_KMH = 8.0

    /** km/h de rachas. 🟣 */
    const val GUST_KMH = 15.0
}

/** Un valor de antes (al preparar) y de ahora. */
data class Change<T>(val before: T, val after: T)

data class OutingReview(
    /** Ahora hay filtro duro (fuera de horario o sin condiciones mínimas): no se debe activar. */
    val blockedNow: Boolean,
    val band: Change<FavorabilityBand?>? = null,
    val limiting: Change<RuleLevel?>? = null,
    val waterC: Change<Double>? = null,
    /** El agua de ahora es medida por ti (manda sobre la estimada de anoche). */
    val waterMeasuredNow: Boolean = false,
    val airC: Change<Double>? = null,
    val windKmh: Change<Double>? = null,
    val gustKmh: Change<Double>? = null,
    /** Cambiaste puesto, cañas, cebos o tu medida: el plan de la IA preparado ya no es para lo elegido. */
    val choicesChanged: Boolean = false,
) {
    val nothingChanged: Boolean
        get() = !blockedNow && band == null && limiting == null && waterC == null && airC == null && windKmh == null && gustKmh == null && !choicesChanged
}

/**
 * Compara lo preparado con lo de ahora. Función pura. [preparedKey] es el [planKey] guardado al
 * preparar (solo con plan de la IA); [currentKey], el de lo elegido ahora.
 */
fun reviewOuting(
    prepared: PreparedOuting,
    now: StrategyUiState,
    preparedKey: String?,
    currentKey: String?,
): OutingReview {
    val before = prepared.conditions
    val result = now.result
    val d = now.derived
    fun <T : Any> diff(a: T?, b: T?): Change<T?>? = if (a != b) Change(a, b) else null
    fun num(a: Double?, b: Double?, limit: Double): Change<Double>? =
        if (a != null && b != null && abs(a - b) >= limit) Change(a, b) else null
    val waterNow = d?.water?.valueC
    return OutingReview(
        blockedNow = result?.blocked == true,
        band = result?.let { diff(before?.band, it.band) },
        limiting = result?.let { diff(before?.limitingLevel, it.limitingLevel) },
        waterC = num(before?.waterC, waterNow, ReviewThresholds.WATER_C),
        waterMeasuredNow = d?.water?.source == WaterTempSource.MEASURED && before?.waterSource != WaterTempSource.MEASURED,
        airC = num(before?.airC, d?.airTrend?.last24hMeanC, ReviewThresholds.AIR_C),
        windKmh = num(before?.windKmh, d?.wind24h?.meanSpeedKmh, ReviewThresholds.WIND_KMH),
        gustKmh = num(before?.gustKmh, d?.wind24h?.maxGustKmh, ReviewThresholds.GUST_KMH),
        choicesChanged = preparedKey != null && currentKey != null && outingKey(preparedKey) != outingKey(currentKey),
    )
}
