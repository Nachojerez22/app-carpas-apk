package com.nachojerez.carpstrategy.domain.derived

import com.nachojerez.carpstrategy.domain.model.StationObservation
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/** Sesgo de temperatura de la media de modelos frente a la estación de AEMET. */
data class TemperatureBias(
    /** Modelo − observación (°C). Positivo: los modelos van más calientes. */
    val biasC: Double,
    val samples: Int,
    /** Corrección por altitud aplicada a la estación (°C). */
    val altitudeAdjustmentC: Double,
)

/**
 * Corrección de sesgo con las observaciones reales (AEMET solo da ~24 h).
 *
 * La estación está a otra altitud que el punto de rejilla del modelo, así que antes de comparar
 * su temperatura se lleva a la altitud de la rejilla con el gradiente estándar de
 * −6,5 °C/km. Con al menos [MIN_SAMPLES] horas coincidentes en las últimas [WINDOW], el sesgo es
 * la media de (modelo − estación ajustada), limitado a ±[MAX_BIAS_C]. Se resta a las
 * temperaturas que vienen de los modelos.
 */
object TemperatureBiasModel {
    const val LAPSE_RATE_C_PER_M = 0.0065
    const val MIN_SAMPLES = 6
    const val MAX_BIAS_C = 5.0
    const val INCOMPATIBLE_BIAS_C = 15.0
    val WINDOW: Duration = Duration.ofHours(48)

    fun estimate(
        ensemble: List<EnsembleHour>,
        observations: List<StationObservation>,
        stationAltitudeM: Double?,
        gridElevationM: Double?,
        now: Instant,
    ): TemperatureBias? {
        val adjustment = if (stationAltitudeM != null && gridElevationM != null) {
            (stationAltitudeM - gridElevationM) * LAPSE_RATE_C_PER_M
        } else {
            0.0
        }
        val models = ensemble.associate { it.time to it.temperatureC?.mean }
        val diffs = observations
            .filter { !it.time.isAfter(now) && Duration.between(it.time, now) <= WINDOW }
            .mapNotNull { obs ->
                val model = models[obs.time] ?: return@mapNotNull null
                val observed = obs.temperatureC ?: return@mapNotNull null
                model - (observed + adjustment)
            }
        if (diffs.size < MIN_SAMPLES) return null
        val bias = diffs.average()
        // Un sesgo enorme indica datos incompatibles (estación mal elegida, error de unidades).
        if (abs(bias) > INCOMPATIBLE_BIAS_C) return null
        return TemperatureBias(bias.coerceIn(-MAX_BIAS_C, MAX_BIAS_C), diffs.size, adjustment)
    }
}
