package com.nachojerez.carpstrategy.domain.derived

import com.nachojerez.carpstrategy.domain.manual.DataSource
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.manual.RecordPeriod
import com.nachojerez.carpstrategy.domain.manual.SourcePriority
import com.nachojerez.carpstrategy.domain.manual.WeatherVariable
import com.nachojerez.carpstrategy.domain.model.StationObservation
import java.time.Instant

/** Valor elegido para una hora y variable, con la fuente de la que sale. */
data class SourcedValue(val value: Double, val source: DataSource)

data class MergedHour(
    val time: Instant,
    val values: Map<WeatherVariable, SourcedValue>,
) {
    operator fun get(variable: WeatherVariable): SourcedValue? = values[variable]
}

/**
 * Combina modelos (media), AEMET y datos manuales hora a hora. Para cada variable se toma la
 * primera fuente activa de [SourcePriority] que tenga valor en esa hora. Función pura.
 *
 * Alineación temporal:
 * - Modelos: hora en punto de Open-Meteo.
 * - AEMET: `fint` (fin del periodo de observación), ya en hora en punto.
 * - Manual: se redondea a la hora en punto más cercana (08:29 → 08:00, 08:30 → 09:00). Si hay
 *   varios registros manuales en la misma hora, gana el más reciente.
 * Los registros manuales por día (`fecha`) no entran aquí: son lluvia diaria y nivel del embalse.
 */
object SourceMerger {

    fun merge(
        ensemble: List<EnsembleHour>,
        observations: List<StationObservation>,
        manual: List<ManualRecord>,
        priority: SourcePriority,
    ): List<MergedHour> {
        val models: Map<Instant, Map<WeatherVariable, Double>> =
            if (priority.isEnabled(DataSource.MODELS)) ensemble.associate { it.time to it.modelValues() } else emptyMap()
        val aemet: Map<Instant, Map<WeatherVariable, Double>> =
            if (priority.isEnabled(DataSource.AEMET)) observations.associate { it.time to it.aemetValues() } else emptyMap()
        val typed: Map<Instant, Map<WeatherVariable, Double>> =
            if (priority.isEnabled(DataSource.MANUAL)) manualByHour(manual) else emptyMap()

        val bySource = mapOf(DataSource.MODELS to models, DataSource.AEMET to aemet, DataSource.MANUAL to typed)
        val hours = (models.keys + aemet.keys + typed.keys).sorted()

        return hours.map { time ->
            val values = buildMap {
                for (variable in WeatherVariable.entries) {
                    for (source in priority.order) {
                        val value = bySource.getValue(source)[time]?.get(variable) ?: continue
                        put(variable, SourcedValue(value, source))
                        break
                    }
                }
            }
            MergedHour(time, values)
        }.filter { it.values.isNotEmpty() }
    }

    /** Hora en punto más cercana. */
    fun roundToHour(time: Instant): Instant {
        val seconds = time.epochSecond
        return Instant.ofEpochSecond(Math.floorDiv(seconds + 1800, 3600L) * 3600L)
    }

    private fun manualByHour(records: List<ManualRecord>): Map<Instant, Map<WeatherVariable, Double>> {
        val result = mutableMapOf<Instant, MutableMap<WeatherVariable, Double>>()
        records
            .filter { it.period is RecordPeriod.At }
            .sortedBy { it.createdAt } // los más recientes sobrescriben
            .forEach { record ->
                val hour = roundToHour((record.period as RecordPeriod.At).time)
                val target = result.getOrPut(hour) { mutableMapOf() }
                record.values.forEach { (field, value) -> field.variable?.let { target[it] = value } }
            }
        return result
    }

    private fun EnsembleHour.modelValues(): Map<WeatherVariable, Double> = buildMap {
        temperatureC?.let { put(WeatherVariable.AIR_TEMPERATURE, it.mean) }
        pressureMslHpa?.let { put(WeatherVariable.PRESSURE_MSL, it.mean) }
        windSpeedKmh?.let { put(WeatherVariable.WIND_SPEED, it.mean) }
        windDirection?.let { put(WeatherVariable.WIND_DIRECTION, it.meanDeg) }
        windGustsKmh?.let { put(WeatherVariable.WIND_GUSTS, it.mean) }
        cloudCoverPct?.let { put(WeatherVariable.CLOUD_COVER, it.mean) }
        precipitationMm?.let { put(WeatherVariable.PRECIPITATION, it.mean) }
        shortwaveRadiationWm2?.let { put(WeatherVariable.SHORTWAVE_RADIATION, it.mean) }
    }

    private fun StationObservation.aemetValues(): Map<WeatherVariable, Double> = buildMap {
        temperatureC?.let { put(WeatherVariable.AIR_TEMPERATURE, it) }
        pressureMslHpa?.let { put(WeatherVariable.PRESSURE_MSL, it) }
        windSpeedKmh?.let { put(WeatherVariable.WIND_SPEED, it) }
        windDirectionDeg?.let { put(WeatherVariable.WIND_DIRECTION, it) }
        windGustKmh?.let { put(WeatherVariable.WIND_GUSTS, it) }
        precipitationMm?.let { put(WeatherVariable.PRECIPITATION, it) }
        relativeHumidityPct?.let { put(WeatherVariable.RELATIVE_HUMIDITY, it) }
    }
}
