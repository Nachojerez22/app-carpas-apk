package com.nachojerez.carpstrategy.domain.derived

import java.time.Duration
import java.time.Instant

enum class WaterTempSource { MEASURED, ESTIMATED }

data class WaterTemperature(
    val valueC: Double,
    val source: WaterTempSource,
    /** Momento de la medida usada (solo si es medida). */
    val measuredAt: Instant? = null,
    /** Corrección aplicada a la estimación a partir de tus medidas (°C). */
    val calibrationOffsetC: Double? = null,
    val calibrationSamples: Int = 0,
    /** Variación de la estimación en 3 días (°C); positivo = calentándose. */
    val trend3dC: Double? = null,
)

/**
 * Temperatura del agua. La medida del usuario manda (CONOCIMIENTO.md §0.7 y §8); si no hay
 * ninguna reciente, se ESTIMA a partir del aire. **Es una estimación, no un dato real.**
 *
 * Fórmula (media móvil ponderada de la temperatura del aire):
 *   T_agua ≈ Σ w_k · T̄_k / Σ w_k,  k = 0…6,  w_k = 7 − k
 * donde T̄_k es la media del aire en el bloque de 24 h que termina k días antes del instante
 * evaluado (k = 0: últimas 24 h, peso 7; k = 6: hace 6–7 días, peso 1). Hacen falta al menos 5
 * bloques con ≥ 12 horas de dato. La inercia térmica del agua hace que responda a varios días de
 * aire y no al de hoy; los pesos son una hipótesis de trabajo, no un ajuste publicado.
 *
 * Calibración: con medidas de superficie de los últimos [CALIBRATION_WINDOW] se calcula el
 * desfase medio (medida − estimación en el momento de cada medida) y se suma a la estimación,
 * limitado a ±[MAX_OFFSET_C].
 */
object WaterTemperatureModel {
    const val WINDOW_DAYS = 7
    const val MIN_BLOCKS = 5
    const val MIN_HOURS_PER_BLOCK = 12
    const val MAX_OFFSET_C = 5.0
    val MEASUREMENT_MAX_AGE: Duration = Duration.ofHours(48)
    val CALIBRATION_WINDOW: Duration = Duration.ofDays(30)

    /** Estimación sin calibrar en [at]. */
    fun estimate(air: HourlySeries, at: Instant): Double? {
        var weighted = 0.0
        var weights = 0.0
        var blocks = 0
        for (k in 0 until WINDOW_DAYS) {
            val end = at.minus(Duration.ofDays(k.toLong()))
            val values = air.window(end.minus(Duration.ofDays(1)), end).map { it.value }
            if (values.size < MIN_HOURS_PER_BLOCK) continue
            val weight = (WINDOW_DAYS - k).toDouble()
            weighted += weight * values.average()
            weights += weight
            blocks++
        }
        return if (blocks >= MIN_BLOCKS) weighted / weights else null
    }

    /**
     * @param measurements medidas de superficie del usuario (instante, °C).
     */
    fun evaluate(air: HourlySeries, measurements: List<Point>, now: Instant): WaterTemperature? {
        val past = measurements.filter { !it.time.isAfter(now) }.sortedBy { it.time }

        val residuals = past
            .filter { Duration.between(it.time, now) <= CALIBRATION_WINDOW }
            .mapNotNull { m -> estimate(air, m.time)?.let { m.value - it } }
        val offset = residuals.takeIf { it.isNotEmpty() }?.average()?.coerceIn(-MAX_OFFSET_C, MAX_OFFSET_C)
        fun calibrated(at: Instant) = estimate(air, at)?.let { it + (offset ?: 0.0) }

        val nowEstimate = calibrated(now)
        val threeDaysAgo = calibrated(now.minus(Duration.ofDays(3)))
        val trend = if (nowEstimate != null && threeDaysAgo != null) nowEstimate - threeDaysAgo else null

        val latest = past.lastOrNull()
        if (latest != null && Duration.between(latest.time, now) <= MEASUREMENT_MAX_AGE) {
            return WaterTemperature(
                valueC = latest.value,
                source = WaterTempSource.MEASURED,
                measuredAt = latest.time,
                calibrationOffsetC = offset,
                calibrationSamples = residuals.size,
                trend3dC = trend,
            )
        }
        return nowEstimate?.let {
            WaterTemperature(
                valueC = it,
                source = WaterTempSource.ESTIMATED,
                calibrationOffsetC = offset,
                calibrationSamples = residuals.size,
                trend3dC = trend,
            )
        }
    }
}
