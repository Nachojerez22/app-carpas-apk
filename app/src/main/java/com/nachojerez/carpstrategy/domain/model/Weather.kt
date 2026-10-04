package com.nachojerez.carpstrategy.domain.model

import java.time.Instant

/**
 * Modelos numéricos europeos pedidos a Open-Meteo. [apiId] es el valor exacto del parámetro
 * `models` y el sufijo de cada serie en la respuesta (p. ej. `temperature_2m_icon_eu`).
 */
enum class WeatherModel(val apiId: String) {
    ICON_EU("icon_eu"),
    ARPEGE_EUROPE("meteofrance_arpege_europe"),
    ECMWF_IFS025("ecmwf_ifs025"),
    ;

    companion object {
        fun fromApiId(apiId: String): WeatherModel? = entries.firstOrNull { it.apiId == apiId }
    }
}

/** Una hora de un modelo. Cualquier variable puede faltar (el modelo no cubre esa hora). */
data class HourlyWeather(
    val time: Instant,
    val temperatureC: Double?,
    val pressureMslHpa: Double?,
    val windSpeedKmh: Double?,
    val windDirectionDeg: Double?,
    val windGustsKmh: Double?,
    val cloudCoverPct: Double?,
    val precipitationMm: Double?,
    val shortwaveRadiationWm2: Double?,
    /**
     * Código WMO del tiempo presente (Open-Meteo `weather_code`): 95–99 = tormenta. Es una
     * previsión del modelo; lo que diga el usuario en el puesto manda.
     */
    val weatherCode: Int? = null,
)

/** Serie horaria de varios modelos para una ubicación. */
data class MultiModelForecast(
    val requested: GeoPoint,
    /** Punto de rejilla que Open-Meteo usó realmente. */
    val gridPoint: GeoPoint,
    val elevationM: Double?,
    val timezone: String,
    val series: Map<WeatherModel, List<HourlyWeather>>,
)

/** Estación de AEMET (inventario climatológico). */
data class WeatherStation(
    val id: String,
    val name: String,
    val province: String?,
    val point: GeoPoint,
    val altitudeM: Double?,
)

/** Observación horaria real de una estación de AEMET. Viento convertido a km/h. */
data class StationObservation(
    val stationId: String,
    /** Fin del periodo de observación (UTC). */
    val time: Instant,
    val temperatureC: Double?,
    val pressureMslHpa: Double?,
    val windSpeedKmh: Double?,
    val windDirectionDeg: Double?,
    val windGustKmh: Double?,
    val precipitationMm: Double?,
    val relativeHumidityPct: Double?,
)

/** Observaciones de la estación más cercana con datos. */
data class NearbyObservations(
    val station: WeatherStation,
    val distanceKm: Double,
    val observations: List<StationObservation>,
)

/** Dato guardado en caché junto con el instante de su descarga. */
data class Cached<T>(val data: T, val fetchedAt: Instant)
