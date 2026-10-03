package com.nachojerez.carpstrategy.data.remote.aemet

import com.nachojerez.carpstrategy.data.remote.DataSourceException
import com.nachojerez.carpstrategy.data.remote.classifyErrors
import com.nachojerez.carpstrategy.domain.model.DataError
import com.nachojerez.carpstrategy.domain.model.StationObservation
import com.nachojerez.carpstrategy.domain.model.WeatherStation
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Named
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import retrofit2.Response

class AemetDataSource @Inject constructor(
    private val api: AemetApi,
    private val json: Json,
    @param:Named(AEMET_API_KEY) private val apiKey: String,
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    suspend fun fetchStations(): List<WeatherStation> = classifyErrors {
        AemetParser.parseStations(json, fetchTwoStep { api.stationInventory() })
    }

    suspend fun fetchObservations(stationId: String): List<StationObservation> = classifyErrors {
        AemetParser.parseObservations(json, fetchTwoStep { api.stationObservations(stationId) })
    }

    private suspend fun fetchTwoStep(firstCall: suspend () -> Response<ResponseBody>): String {
        if (!isConfigured) throw DataSourceException(DataError.MISSING_API_KEY)

        val first = firstCall()
        val firstText = (first.body() ?: first.errorBody())?.decodeText()
        val envelope = firstText?.let { runCatching { AemetParser.parseEnvelope(json, it) }.getOrNull() }
        // AEMET indica el resultado en `estado` del sobre, a veces con HTTP 200.
        val status = envelope?.estado ?: first.code()
        failOnStatus(status, envelope?.descripcion)
        if (!first.isSuccessful) failOnStatus(first.code(), envelope?.descripcion)

        val dataUrl = envelope?.datos
            ?: throw DataSourceException(DataError.INVALID_RESPONSE, "Sobre de AEMET sin 'datos'")
        val second = api.download(dataUrl)
        if (!second.isSuccessful) failOnStatus(second.code(), null)
        return second.body()?.decodeText()
            ?: throw DataSourceException(DataError.INVALID_RESPONSE, "Respuesta de datos vacía")
    }

    private fun failOnStatus(status: Int, description: String?) {
        val error = when {
            status in 200..299 -> return
            status == 401 || status == 403 -> DataError.UNAUTHORIZED
            status == 404 -> DataError.NO_DATA
            status == 429 -> DataError.RATE_LIMITED
            else -> DataError.SERVER
        }
        throw DataSourceException(error, "AEMET $status${description?.let { ": $it" }.orEmpty()}")
    }

    companion object {
        const val AEMET_API_KEY = "aemetApiKey"

        /** Codificación que usa AEMET cuando la cabecera no declara ninguna. */
        val DEFAULT_CHARSET: Charset = Charset.forName("ISO-8859-15")

        internal fun ResponseBody.decodeText(): String = use { body ->
            val charset = body.contentType()?.charset() ?: DEFAULT_CHARSET
            body.source().readString(charset)
        }
    }
}
