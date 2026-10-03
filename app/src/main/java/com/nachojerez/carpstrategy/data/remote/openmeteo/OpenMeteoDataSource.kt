package com.nachojerez.carpstrategy.data.remote.openmeteo

import com.nachojerez.carpstrategy.data.remote.DataSourceException
import com.nachojerez.carpstrategy.data.remote.classifyErrors
import com.nachojerez.carpstrategy.domain.model.DataError
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.model.MultiModelForecast
import com.nachojerez.carpstrategy.domain.model.WeatherModel
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

class OpenMeteoDataSource @Inject constructor(
    private val api: OpenMeteoApi,
    private val json: Json,
) {
    suspend fun fetchForecast(
        location: GeoPoint,
        models: List<WeatherModel> = WeatherModel.entries,
    ): MultiModelForecast = classifyErrors {
        val response = try {
            api.forecast(
                latitude = location.latitude,
                longitude = location.longitude,
                hourly = OpenMeteoRequest.HOURLY_VARIABLES.joinToString(","),
                models = models.joinToString(",") { it.apiId },
                pastDays = OpenMeteoRequest.PAST_DAYS,
                forecastDays = OpenMeteoRequest.FORECAST_DAYS,
                timezone = OpenMeteoRequest.TIMEZONE,
            )
        } catch (e: HttpException) {
            val reason = e.response()?.errorBody()?.string()?.let(::errorReason)
            val error = if (e.code() == 429) DataError.RATE_LIMITED else DataError.SERVER
            throw DataSourceException(error, "HTTP ${e.code()}${reason?.let { ": $it" }.orEmpty()}", e)
        }
        OpenMeteoParser.parse(response, location, models)
    }

    private fun errorReason(body: String): String? = runCatching {
        json.decodeFromString<JsonObject>(body)["reason"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()
}
