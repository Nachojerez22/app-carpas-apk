package com.nachojerez.carpstrategy.ui.guided

import com.nachojerez.carpstrategy.domain.derived.DerivedConditions
import com.nachojerez.carpstrategy.domain.derived.WaterTempSource
import com.nachojerez.carpstrategy.domain.guided.WeatherSnapshot
import com.nachojerez.carpstrategy.domain.manual.DataSource
import com.nachojerez.carpstrategy.domain.manual.WeatherVariable
import com.nachojerez.carpstrategy.domain.usecase.RawWeather
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/** Distancia máxima entre el aviso y la hora de la serie que se usa. */
private val MAX_GAP: Duration = Duration.ofMinutes(90)

/**
 * El tiempo de la hora en curso para un aviso: serie combinada (tu prioridad de fuentes; el aire
 * de los modelos corregido con el sesgo frente a AEMET, como en los derivados), el código de
 * tiempo más severo de los modelos (si uno da tormenta, se pregunta) y agua, ocaso y fin legal
 * de los derivados. Null si no hay datos cerca de [now]. Función pura.
 */
fun weatherSnapshotAt(raw: RawWeather, derived: DerivedConditions?, now: Instant): WeatherSnapshot? {
    val hour = raw.merged
        .filter { abs(Duration.between(it.time, now).toMinutes()) <= MAX_GAP.toMinutes() }
        .minByOrNull { abs(Duration.between(it.time, now).toMinutes()) }
        ?: return null
    val bias = derived?.temperatureBias?.biasC
    val air = hour[WeatherVariable.AIR_TEMPERATURE]?.let { v ->
        if (v.source == DataSource.MODELS && bias != null) v.value - bias else v.value
    }
    val code = raw.forecast?.data?.series?.values
        ?.mapNotNull { list -> list.firstOrNull { it.time == hour.time }?.weatherCode }
        ?.maxOrNull()
    return WeatherSnapshot(
        time = now,
        airC = air,
        windKmh = hour[WeatherVariable.WIND_SPEED]?.value,
        windFromDeg = hour[WeatherVariable.WIND_DIRECTION]?.value,
        gustKmh = hour[WeatherVariable.WIND_GUSTS]?.value,
        cloudPct = hour[WeatherVariable.CLOUD_COVER]?.value,
        precipitationMm = hour[WeatherVariable.PRECIPITATION]?.value,
        weatherCode = code,
        pressureHpa = hour[WeatherVariable.PRESSURE_MSL]?.value,
        waterC = derived?.water?.valueC,
        waterMeasured = derived?.water?.source == WaterTempSource.MEASURED,
        sunset = derived?.sunToday?.sunset,
        legalEnd = derived?.legalToday?.end,
    )
}
