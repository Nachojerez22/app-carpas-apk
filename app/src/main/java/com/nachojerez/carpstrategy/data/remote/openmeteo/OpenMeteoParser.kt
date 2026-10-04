package com.nachojerez.carpstrategy.data.remote.openmeteo

import com.nachojerez.carpstrategy.data.remote.DataSourceException
import com.nachojerez.carpstrategy.data.remote.openmeteo.OpenMeteoRequest.CLOUD_COVER
import com.nachojerez.carpstrategy.data.remote.openmeteo.OpenMeteoRequest.PRECIPITATION
import com.nachojerez.carpstrategy.data.remote.openmeteo.OpenMeteoRequest.PRESSURE_MSL
import com.nachojerez.carpstrategy.data.remote.openmeteo.OpenMeteoRequest.SHORTWAVE_RADIATION
import com.nachojerez.carpstrategy.data.remote.openmeteo.OpenMeteoRequest.TEMPERATURE
import com.nachojerez.carpstrategy.data.remote.openmeteo.OpenMeteoRequest.WEATHER_CODE
import com.nachojerez.carpstrategy.data.remote.openmeteo.OpenMeteoRequest.WIND_DIRECTION
import com.nachojerez.carpstrategy.data.remote.openmeteo.OpenMeteoRequest.WIND_GUSTS
import com.nachojerez.carpstrategy.data.remote.openmeteo.OpenMeteoRequest.WIND_SPEED
import com.nachojerez.carpstrategy.domain.model.DataError
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.model.HourlyWeather
import com.nachojerez.carpstrategy.domain.model.MultiModelForecast
import com.nachojerez.carpstrategy.domain.model.WeatherModel
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Convierte la respuesta multimodelo de Open-Meteo (con `timeformat=unixtime`) al dominio. */
object OpenMeteoParser {

    fun parse(json: JsonObject, requested: GeoPoint, models: List<WeatherModel>): MultiModelForecast {
        if (json["error"]?.jsonPrimitive?.booleanOrNull == true) {
            throw DataSourceException(DataError.SERVER, json["reason"]?.jsonPrimitive?.contentOrNull)
        }
        val hourly = json["hourly"]?.jsonObject ?: invalid("Falta 'hourly'")
        val times = hourly["time"]?.jsonArray?.map {
            val epoch = it.jsonPrimitive.longOrNull ?: invalid("Hora no numérica: $it (¿falta timeformat=unixtime?)")
            Instant.ofEpochSecond(epoch)
        } ?: invalid("Falta 'hourly.time'")

        val series = models.mapNotNull { model ->
            val columns = Columns(hourly, model, singleModel = models.size == 1, size = times.size)
            if (!columns.hasAny) return@mapNotNull null
            val hours = times.mapIndexed { i, time ->
                HourlyWeather(
                    time = time,
                    temperatureC = columns.value(TEMPERATURE, i),
                    pressureMslHpa = columns.value(PRESSURE_MSL, i),
                    windSpeedKmh = columns.value(WIND_SPEED, i),
                    windDirectionDeg = columns.value(WIND_DIRECTION, i),
                    windGustsKmh = columns.value(WIND_GUSTS, i),
                    cloudCoverPct = columns.value(CLOUD_COVER, i),
                    precipitationMm = columns.value(PRECIPITATION, i),
                    shortwaveRadiationWm2 = columns.value(SHORTWAVE_RADIATION, i),
                    weatherCode = columns.value(WEATHER_CODE, i)?.toInt(),
                )
            }.filterNot { it.isEmpty() }
            model to hours
        }.toMap()

        return MultiModelForecast(
            requested = requested,
            gridPoint = GeoPoint(
                latitude = json.double("latitude") ?: requested.latitude,
                longitude = json.double("longitude") ?: requested.longitude,
            ),
            elevationM = json.double("elevation"),
            timezone = json["timezone"]?.jsonPrimitive?.contentOrNull ?: OpenMeteoRequest.TIMEZONE,
            series = series,
        )
    }

    private class Columns(
        hourly: JsonObject,
        model: WeatherModel,
        singleModel: Boolean,
        size: Int,
    ) {
        private val arrays: Map<String, JsonArray> = OpenMeteoRequest.HOURLY_VARIABLES.mapNotNull { variable ->
            // Con un solo modelo Open-Meteo no añade sufijo.
            val array = hourly["${variable}_${model.apiId}"]?.jsonArray
                ?: if (singleModel) hourly[variable]?.jsonArray else null
            array?.let {
                if (it.size != size) invalid("Serie $variable de ${model.apiId} con ${it.size} valores (esperados $size)")
                variable to it
            }
        }.toMap()

        val hasAny: Boolean get() = arrays.isNotEmpty()

        fun value(variable: String, index: Int): Double? {
            val element = arrays[variable]?.get(index) ?: return null
            return if (element is JsonNull) null else element.jsonPrimitive.doubleOrNull
        }
    }

    private fun HourlyWeather.isEmpty() = listOf(
        temperatureC, pressureMslHpa, windSpeedKmh, windDirectionDeg, windGustsKmh,
        cloudCoverPct, precipitationMm, shortwaveRadiationWm2, weatherCode?.toDouble(),
    ).all { it == null }

    private fun JsonObject.double(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull

    private fun invalid(message: String): Nothing =
        throw DataSourceException(DataError.INVALID_RESPONSE, message)
}
