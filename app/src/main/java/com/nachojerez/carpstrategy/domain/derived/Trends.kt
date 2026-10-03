package com.nachojerez.carpstrategy.domain.derived

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** Valor horario de una serie. */
data class Point(val time: Instant, val value: Double)

/** Serie horaria ordenada con utilidades de ventana. Inmutable. */
class HourlySeries(points: List<Point>) {
    val points: List<Point> = points.sortedBy { it.time }

    /** Puntos con `from < t <= to`. */
    fun window(from: Instant, to: Instant): List<Point> = points.filter { it.time.isAfter(from) && !it.time.isAfter(to) }

    /** Último valor en `t` o antes, si no es más antiguo que [maxGap]. */
    fun valueAtOrBefore(t: Instant, maxGap: Duration = Duration.ofHours(2)): Double? =
        points.lastOrNull { !it.time.isAfter(t) }?.takeIf { Duration.between(it.time, t) <= maxGap }?.value

    fun isEmpty() = points.isEmpty()
}

data class PressureTrend(
    val delta3hHpa: Double?,
    val delta24hHpa: Double?,
    val delta72hHpa: Double?,
    /** Desviación típica de las últimas 48 h: estabilidad (baja = estable). */
    val stdDev48hHpa: Double?,
)

data class AirTemperatureTrend(
    val last24hMeanC: Double,
    val previous3DaysMeanC: Double,
) {
    val deltaC: Double get() = last24hMeanC - previous3DaysMeanC
}

data class WindSummary(
    val window: Duration,
    /** Dirección de procedencia dominante (media vectorial ponderada por velocidad). */
    val dominantDirectionDeg: Double?,
    val meanSpeedKmh: Double,
    /** 0 = direcciones dispersas, 1 = siempre del mismo rumbo. */
    val persistence: Double,
    val maxGustKmh: Double?,
)

data class Rainfall(
    val last24hMm: Double,
    val last72hMm: Double,
    /** Índice de precipitación antecedente: 7 bloques de 24 h previos a las últimas 72 h (factor 0,9 por día). */
    val antecedentIndexMm: Double,
    /** Horas desde la última hora con lluvia ≥ [Trends.RAIN_HOUR_MIN_MM]; null si no hubo en la serie. */
    val hoursSinceRain: Long?,
    /** Hipótesis operativa 🟣: lluvia que probablemente llega al embalse como escorrentía. */
    val runoffLikely: Boolean,
)

data class TemperatureStreaks(
    /** Días completos consecutivos (hasta ayer) con máxima ≥ umbral de calor. */
    val hotDays: Int,
    /** Días completos consecutivos (hasta ayer) con media ≤ umbral de frío. */
    val coldDays: Int,
)

/** Tendencias y resúmenes de las series horarias. Funciones puras; [now] siempre explícito. */
object Trends {
    const val RAIN_HOUR_MIN_MM = 0.2

    /** Umbrales de trabajo (no publicados para carpa): ajustables. */
    const val HOT_DAY_MAX_C = 30.0
    const val COLD_DAY_MEAN_C = 8.0

    /**
     * Escorrentía probable (🟣, CONOCIMIENTO.md §5.8): "20 mm sobre suelo seco no alteran el
     * agua". Se exige lluvia reciente abundante y suelo ya húmedo, o una lluvia muy intensa.
     */
    const val RUNOFF_RAIN_72H_MM = 20.0
    const val RUNOFF_ANTECEDENT_MM = 10.0
    const val RUNOFF_RAIN_24H_MM = 40.0
    private const val ANTECEDENT_DECAY = 0.9

    fun pressure(series: HourlySeries, now: Instant): PressureTrend {
        val current = series.valueAtOrBefore(now)
        fun delta(hours: Long) = current?.let { c -> series.valueAtOrBefore(now.minusSeconds(hours * 3600))?.let { c - it } }
        val last48 = series.window(now.minusSeconds(48 * 3600), now).map { it.value }
        return PressureTrend(delta(3), delta(24), delta(72), if (last48.size >= 24) stdDev(last48) else null)
    }

    fun airTemperature(series: HourlySeries, now: Instant): AirTemperatureTrend? {
        val last24 = series.window(now.minusSeconds(24 * 3600), now).map { it.value }
        val previous = series.window(now.minusSeconds(96 * 3600), now.minusSeconds(24 * 3600)).map { it.value }
        if (last24.size < 12 || previous.size < 36) return null
        return AirTemperatureTrend(last24.average(), previous.average())
    }

    fun wind(speed: HourlySeries, direction: HourlySeries, gusts: HourlySeries, now: Instant, window: Duration): WindSummary? {
        val from = now.minus(window)
        val speeds = speed.window(from, now)
        if (speeds.isEmpty()) return null
        val directions = direction.window(from, now).associateBy { it.time }
        var x = 0.0
        var y = 0.0
        var weight = 0.0
        speeds.forEach { s ->
            val d = directions[s.time]?.value ?: return@forEach
            x += s.value * sin(Math.toRadians(d))
            y += s.value * cos(Math.toRadians(d))
            weight += s.value
        }
        val resultant = hypot(x, y)
        val dominant = if (weight > 0 && resultant > 1e-9) (Math.toDegrees(atan2(x, y)) + 360.0) % 360.0 else null
        return WindSummary(
            window = window,
            dominantDirectionDeg = dominant,
            meanSpeedKmh = speeds.map { it.value }.average(),
            persistence = if (weight > 0) (resultant / weight).coerceIn(0.0, 1.0) else 0.0,
            maxGustKmh = gusts.window(from, now).maxOfOrNull { it.value },
        )
    }

    /**
     * [dailyOverrides]: lluvia diaria introducida a mano (fecha local → mm). Sustituye a la suma
     * horaria de ese día en el índice antecedente.
     */
    fun rainfall(
        series: HourlySeries,
        now: Instant,
        zone: ZoneId,
        dailyOverrides: Map<LocalDate, Double> = emptyMap(),
    ): Rainfall {
        fun sum(from: Instant, to: Instant) = series.window(from, to).sumOf { it.value }
        val last24 = sum(now.minusSeconds(24 * 3600), now)
        val last72 = sum(now.minusSeconds(72 * 3600), now)

        // Índice antecedente: 7 bloques de 24 h inmediatamente anteriores a la ventana de 72 h.
        // Si hay lluvia diaria manual para el día local del centro del bloque, la sustituye.
        val windowStart = now.minusSeconds(72 * 3600)
        val antecedent = (1..7).sumOf { k ->
            val blockEnd = windowStart.minusSeconds((k - 1) * 24 * 3600L)
            val blockStart = blockEnd.minusSeconds(24 * 3600L)
            val day = blockStart.plusSeconds(12 * 3600L).atZone(zone).toLocalDate()
            val mm = dailyOverrides[day] ?: sum(blockStart, blockEnd)
            mm * Math.pow(ANTECEDENT_DECAY, (k - 1).toDouble())
        }
        val lastRain = series.points.lastOrNull { !it.time.isAfter(now) && it.value >= RAIN_HOUR_MIN_MM }
        val runoff = (last72 >= RUNOFF_RAIN_72H_MM && antecedent >= RUNOFF_ANTECEDENT_MM) || last24 >= RUNOFF_RAIN_24H_MM
        return Rainfall(
            last24hMm = last24,
            last72hMm = last72,
            antecedentIndexMm = antecedent,
            hoursSinceRain = lastRain?.let { Duration.between(it.time, now).toHours() },
            runoffLikely = runoff,
        )
    }

    /** Rachas contadas hacia atrás desde ayer (día local). Un día necesita ≥ 18 horas con dato. */
    fun streaks(
        series: HourlySeries,
        now: Instant,
        zone: ZoneId,
        hotMaxC: Double = HOT_DAY_MAX_C,
        coldMeanC: Double = COLD_DAY_MEAN_C,
    ): TemperatureStreaks {
        val byDay = series.points.groupBy { it.time.atZone(zone).toLocalDate() }
        val yesterday = now.atZone(zone).toLocalDate().minusDays(1)
        fun count(condition: (List<Double>) -> Boolean): Int {
            var n = 0
            var day = yesterday
            while (true) {
                val values = byDay[day]?.map { it.value } ?: break
                if (values.size < 18 || !condition(values)) break
                n++
                day = day.minusDays(1)
            }
            return n
        }
        return TemperatureStreaks(
            hotDays = count { it.max() >= hotMaxC },
            coldDays = count { it.average() <= coldMeanC },
        )
    }

    fun stdDev(values: List<Double>): Double {
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }
}
