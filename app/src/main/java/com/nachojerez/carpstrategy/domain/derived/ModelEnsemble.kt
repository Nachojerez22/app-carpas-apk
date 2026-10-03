package com.nachojerez.carpstrategy.domain.derived

import com.nachojerez.carpstrategy.domain.model.HourlyWeather
import com.nachojerez.carpstrategy.domain.model.WeatherModel
import java.time.Instant
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Media y dispersión (máximo − mínimo) de una variable entre los modelos disponibles. */
data class VariableStats(val mean: Double, val spread: Double, val count: Int)

/**
 * Dirección media circular y dispersión angular: la mayor diferencia entre dos modelos
 * (0–180°).
 */
data class DirectionStats(val meanDeg: Double, val spreadDeg: Double, val count: Int)

/** Combinación de todos los modelos para una hora. */
data class EnsembleHour(
    val time: Instant,
    val temperatureC: VariableStats?,
    val pressureMslHpa: VariableStats?,
    val windSpeedKmh: VariableStats?,
    val windDirection: DirectionStats?,
    val windGustsKmh: VariableStats?,
    val cloudCoverPct: VariableStats?,
    val precipitationMm: VariableStats?,
    val shortwaveRadiationWm2: VariableStats?,
    /** Divergencia alta del viento entre modelos = previsión de viento incierta. */
    val windDivergent: Boolean,
)

/**
 * Media y divergencia entre modelos. Funciones puras.
 *
 * Criterio de divergencia del viento (hipótesis operativa, ajustable):
 * - velocidad: diferencia entre modelos ≥ [WIND_SPEED_SPREAD_WARN_KMH], o
 * - dirección: diferencia ≥ [WIND_DIRECTION_SPREAD_WARN_DEG] cuando la velocidad media es
 *   ≥ [WIND_DIRECTION_MIN_SPEED_KMH] (con calma, la dirección no es significativa).
 */
object ModelEnsemble {
    const val WIND_SPEED_SPREAD_WARN_KMH = 10.0
    const val WIND_DIRECTION_SPREAD_WARN_DEG = 90.0
    const val WIND_DIRECTION_MIN_SPEED_KMH = 5.0

    fun combine(series: Map<WeatherModel, List<HourlyWeather>>): List<EnsembleHour> {
        val byTime: Map<Instant, List<HourlyWeather>> = series.values
            .flatten()
            .groupBy { it.time }
        return byTime.keys.sorted().map { time -> combineHour(time, byTime.getValue(time)) }
    }

    fun combineHour(time: Instant, hours: List<HourlyWeather>): EnsembleHour {
        val windSpeed = stats(hours.mapNotNull { it.windSpeedKmh })
        val windDirection = directionStats(hours.mapNotNull { it.windDirectionDeg })
        return EnsembleHour(
            time = time,
            temperatureC = stats(hours.mapNotNull { it.temperatureC }),
            pressureMslHpa = stats(hours.mapNotNull { it.pressureMslHpa }),
            windSpeedKmh = windSpeed,
            windDirection = windDirection,
            windGustsKmh = stats(hours.mapNotNull { it.windGustsKmh }),
            cloudCoverPct = stats(hours.mapNotNull { it.cloudCoverPct }),
            precipitationMm = stats(hours.mapNotNull { it.precipitationMm }),
            shortwaveRadiationWm2 = stats(hours.mapNotNull { it.shortwaveRadiationWm2 }),
            windDivergent = isWindDivergent(windSpeed, windDirection),
        )
    }

    fun isWindDivergent(speed: VariableStats?, direction: DirectionStats?): Boolean {
        val speedDivergent = speed != null && speed.count >= 2 &&
            speed.spread >= WIND_SPEED_SPREAD_WARN_KMH
        val directionDivergent = speed != null && direction != null && direction.count >= 2 &&
            speed.mean >= WIND_DIRECTION_MIN_SPEED_KMH &&
            direction.spreadDeg >= WIND_DIRECTION_SPREAD_WARN_DEG
        return speedDivergent || directionDivergent
    }

    fun stats(values: List<Double>): VariableStats? {
        if (values.isEmpty()) return null
        return VariableStats(
            mean = values.average(),
            spread = values.max() - values.min(),
            count = values.size,
        )
    }

    fun directionStats(degrees: List<Double>): DirectionStats? {
        if (degrees.isEmpty()) return null
        val sinSum = degrees.sumOf { sin(Math.toRadians(it)) }
        val cosSum = degrees.sumOf { cos(Math.toRadians(it)) }
        val mean = normalizeDegrees(Math.toDegrees(atan2(sinSum, cosSum)))
        var spread = 0.0
        for (i in degrees.indices) {
            for (j in i + 1 until degrees.size) {
                spread = maxOf(spread, angularDistance(degrees[i], degrees[j]))
            }
        }
        return DirectionStats(meanDeg = mean, spreadDeg = spread, count = degrees.size)
    }

    /** Distancia angular mínima entre dos direcciones (0–180°). */
    fun angularDistance(a: Double, b: Double): Double {
        val d = abs(normalizeDegrees(a) - normalizeDegrees(b))
        return if (d > 180.0) 360.0 - d else d
    }

    private fun normalizeDegrees(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0
}
