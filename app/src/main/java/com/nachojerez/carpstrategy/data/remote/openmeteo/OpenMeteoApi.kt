package com.nachojerez.carpstrategy.data.remote.openmeteo

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * API de previsión de Open-Meteo (https://open-meteo.com/en/docs), sin API key.
 * Con varios modelos, cada serie horaria llega con el sufijo del modelo
 * (`temperature_2m_icon_eu`), por eso la respuesta se lee como [JsonObject].
 */
interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String,
        @Query("models") models: String,
        @Query("past_days") pastDays: Int,
        @Query("forecast_days") forecastDays: Int,
        @Query("timezone") timezone: String,
        @Query("timeformat") timeFormat: String = "unixtime",
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
    ): JsonObject

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"
    }
}

/** Parámetros de la petición (verificados con una respuesta real, octubre de 2026). */
object OpenMeteoRequest {
    const val TEMPERATURE = "temperature_2m"
    const val PRESSURE_MSL = "pressure_msl"
    const val WIND_SPEED = "wind_speed_10m"
    const val WIND_DIRECTION = "wind_direction_10m"
    const val WIND_GUSTS = "wind_gusts_10m"
    const val CLOUD_COVER = "cloud_cover"
    const val PRECIPITATION = "precipitation"
    const val SHORTWAVE_RADIATION = "shortwave_radiation"
    const val WEATHER_CODE = "weather_code"

    val HOURLY_VARIABLES = listOf(
        TEMPERATURE, PRESSURE_MSL, WIND_SPEED, WIND_DIRECTION, WIND_GUSTS,
        CLOUD_COVER, PRECIPITATION, SHORTWAVE_RADIATION, WEATHER_CODE,
    )

    const val PAST_DAYS = 7
    const val FORECAST_DAYS = 3
    const val TIMEZONE = "Europe/Madrid"
}
