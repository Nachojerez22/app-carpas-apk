package com.nachojerez.carpstrategy.data.remote.aemet

import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.model.StationObservation
import com.nachojerez.carpstrategy.domain.model.WeatherStation
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray

/** Sobre de la primera llamada de AEMET. */
@Serializable
data class AemetEnvelope(
    val descripcion: String? = null,
    val estado: Int? = null,
    val datos: String? = null,
    val metadatos: String? = null,
)

/**
 * Lectura tolerante de los datos de AEMET:
 * - los decimales pueden llegar como número o como texto con coma ("18,4");
 * - "Ip" (precipitación inapreciable) se lee como 0;
 * - las coordenadas del inventario vienen en grados-minutos-segundos ("383218N", "065800W");
 * - `fint` es la hora UTC de fin del periodo, con o sin sufijo de zona.
 */
object AemetParser {
    private const val MS_TO_KMH = 3.6
    private val UTC_SUFFIX = Regex("""(Z|[+-]\d{2}:?\d{2})$""")
    private val OFFSET_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]XX")

    fun parseEnvelope(json: Json, text: String): AemetEnvelope = json.decodeFromString(AemetEnvelope.serializer(), text)

    fun parseStations(json: Json, text: String): List<WeatherStation> =
        json.parseToJsonElement(text).jsonArray.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val id = o.string("indicativo") ?: return@mapNotNull null
            val lat = o.string("latitud")?.let(::parseDms) ?: return@mapNotNull null
            val lon = o.string("longitud")?.let(::parseDms) ?: return@mapNotNull null
            runCatching {
                WeatherStation(
                    id = id,
                    name = o.string("nombre")?.trim().orEmpty().ifEmpty { id },
                    province = o.string("provincia")?.trim(),
                    point = GeoPoint(lat, lon),
                    altitudeM = parseNumber(o["altitud"]),
                )
            }.getOrNull()
        }

    fun parseObservations(json: Json, text: String): List<StationObservation> =
        json.parseToJsonElement(text).jsonArray.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val id = o.string("idema") ?: return@mapNotNull null
            val time = o.string("fint")?.let(::parseInstant) ?: return@mapNotNull null
            StationObservation(
                stationId = id,
                time = time,
                temperatureC = parseNumber(o["ta"]),
                pressureMslHpa = parseNumber(o["pres_nmar"]),
                windSpeedKmh = parseNumber(o["vv"])?.times(MS_TO_KMH),
                windDirectionDeg = parseNumber(o["dv"]),
                windGustKmh = parseNumber(o["vmax"])?.times(MS_TO_KMH),
                precipitationMm = parseNumber(o["prec"]),
                relativeHumidityPct = parseNumber(o["hr"]),
            )
        }.sortedBy { it.time }

    /** "383218N" → 38,5383; "065800W" → −6,9667. Formato [G]GGMMSS + hemisferio. */
    fun parseDms(value: String): Double? {
        val v = value.trim().uppercase()
        if (v.length < 6) return null
        val hemisphere = v.last()
        val digits = v.dropLast(1)
        if (!digits.all { it.isDigit() } || digits.length < 5) return null
        val seconds = digits.takeLast(2).toInt()
        val minutes = digits.dropLast(2).takeLast(2).toInt()
        val degrees = digits.dropLast(4).toInt()
        if (minutes >= 60 || seconds >= 60) return null
        val decimal = degrees + minutes / 60.0 + seconds / 3600.0
        return when (hemisphere) {
            'N', 'E' -> decimal
            'S', 'W', 'O' -> -decimal
            else -> null
        }
    }

    /** Número que puede venir como JSON numérico o como texto con coma decimal. */
    fun parseNumber(element: JsonElement?): Double? {
        val primitive = element as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        if (!primitive.isString) return primitive.doubleOrNull
        val text = primitive.content.trim()
        if (text.equals("Ip", ignoreCase = true)) return 0.0
        return text.replace(',', '.').toDoubleOrNull()
    }

    fun parseInstant(value: String): Instant? = runCatching {
        val v = value.trim()
        if (UTC_SUFFIX.containsMatchIn(v)) {
            OffsetDateTime.parse(v.replace("Z", "+0000").replace(Regex("""([+-]\d{2}):(\d{2})$"""), "$1$2"), OFFSET_FORMAT)
                .toInstant()
        } else {
            LocalDateTime.parse(v).toInstant(ZoneOffset.UTC)
        }
    }.getOrNull()

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
}
