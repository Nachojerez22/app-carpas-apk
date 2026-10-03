package com.nachojerez.carpstrategy.ui.today

import com.nachojerez.carpstrategy.domain.derived.EnsembleHour
import com.nachojerez.carpstrategy.domain.derived.HourlySeries
import com.nachojerez.carpstrategy.domain.derived.MergedHour
import com.nachojerez.carpstrategy.domain.derived.Point
import com.nachojerez.carpstrategy.domain.derived.VariableStats
import com.nachojerez.carpstrategy.domain.derived.WaterTemperatureModel
import com.nachojerez.carpstrategy.domain.manual.WeatherVariable
import java.time.Duration
import java.time.Instant

/** Datos de una gráfica de tendencia: observado hasta ahora y previsión con banda entre modelos. */
data class ChartData(
    val from: Instant,
    val to: Instant,
    val now: Instant,
    /** Serie combinada hasta ahora (línea sólida). */
    val observed: List<Point>,
    /** Media de modelos desde ahora (línea discontinua). */
    val forecast: List<Point>,
    /** Mínimo y máximo entre modelos en la previsión (banda de discrepancia). */
    val bandLow: List<Point>,
    val bandHigh: List<Point>,
    /** Serie secundaria (agua), opcional. */
    val secondary: List<Point> = emptyList(),
) {
    val isEmpty: Boolean get() = observed.isEmpty() && forecast.isEmpty()

    /** Rango vertical con un pequeño margen. */
    fun valueRange(): ClosedFloatingPointRange<Double>? {
        val values = (observed + forecast + bandLow + bandHigh + secondary).map { it.value }
        if (values.isEmpty()) return null
        val lo = values.min()
        val hi = values.max()
        val pad = ((hi - lo) * 0.1).coerceAtLeast(0.5)
        return (lo - pad)..(hi + pad)
    }
}

/**
 * Construcción de las gráficas de Hoy (5 días observados + 3 de previsión, §4 del diseño).
 * Funciones puras.
 */
object TodayCharts {
    val PAST: Duration = Duration.ofDays(5)
    val FUTURE: Duration = Duration.ofDays(3)

    fun pressure(merged: List<MergedHour>, ensemble: List<EnsembleHour>, now: Instant): ChartData =
        build(merged, ensemble, now, WeatherVariable.PRESSURE_MSL) { it.pressureMslHpa }

    /** Aire (serie principal) y agua estimada día a día (secundaria). */
    fun temperature(merged: List<MergedHour>, ensemble: List<EnsembleHour>, now: Instant): ChartData {
        val base = build(merged, ensemble, now, WeatherVariable.AIR_TEMPERATURE) { it.temperatureC }
        val air = HourlySeries(merged.mapNotNull { h -> h[WeatherVariable.AIR_TEMPERATURE]?.let { Point(h.time, it.value) } })
        val water = (0..PAST.toDays().toInt()).mapNotNull { k ->
            val at = now.minus(Duration.ofDays(k.toLong()))
            WaterTemperatureModel.estimate(air, at)?.let { Point(at, it) }
        }.sortedBy { it.time }
        return base.copy(secondary = water)
    }

    private fun build(
        merged: List<MergedHour>,
        ensemble: List<EnsembleHour>,
        now: Instant,
        variable: WeatherVariable,
        stats: (EnsembleHour) -> VariableStats?,
    ): ChartData {
        val from = now.minus(PAST)
        val to = now.plus(FUTURE)
        val observed = merged
            .filter { !it.time.isBefore(from) && !it.time.isAfter(now) }
            .mapNotNull { h -> h[variable]?.let { Point(h.time, it.value) } }
        val future = ensemble.filter { !it.time.isBefore(now) && !it.time.isAfter(to) }
            .mapNotNull { h -> stats(h)?.let { h.time to it } }
        return ChartData(
            from = from,
            to = to,
            now = now,
            observed = observed,
            forecast = future.map { (t, s) -> Point(t, s.mean) },
            bandLow = future.map { (t, s) -> Point(t, s.min) },
            bandHigh = future.map { (t, s) -> Point(t, s.max) },
        )
    }
}
