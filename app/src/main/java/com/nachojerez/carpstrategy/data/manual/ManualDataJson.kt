package com.nachojerez.carpstrategy.data.manual

import com.nachojerez.carpstrategy.domain.manual.IssueCode
import com.nachojerez.carpstrategy.domain.manual.ManualIssue
import com.nachojerez.carpstrategy.domain.manual.ManualOrigin
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.manual.ManualRecordValidator
import com.nachojerez.carpstrategy.domain.manual.RawRecord
import com.nachojerez.carpstrategy.domain.manual.RawValue
import com.nachojerez.carpstrategy.domain.manual.RecordPeriod
import com.nachojerez.carpstrategy.domain.model.FishingLocation
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Resultado de leer un archivo: solo se importa si no hay ningún error. */
data class ImportPreview(
    val records: List<ManualRecord>,
    val issues: List<ManualIssue>,
    val fileLocation: GeoPoint?,
) {
    val errors: List<ManualIssue> get() = issues.filter { it.code.isError }
    val warnings: List<ManualIssue> get() = issues.filterNot { it.code.isError }
    val canImport: Boolean get() = errors.isEmpty() && records.isNotEmpty()
}

/**
 * Formato de importación `carpstrategy-datos` versión 1 (docs/FORMATO_DATOS.md).
 * Todo o nada: si un registro tiene errores, no se importa ninguno y se listan todos.
 */
object ManualDataJson {
    const val FORMAT = "carpstrategy-datos"
    const val VERSION = 1
    const val MAX_RECORDS = 5_000

    private val RESERVED_KEYS = setOf("hora", "fecha", "fuente", "notas")
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String, fileName: String, appLocation: GeoPoint, now: Instant): ImportPreview {
        fun fileError(code: IssueCode, field: String? = null, vararg args: String) =
            ImportPreview(emptyList(), listOf(ManualIssue(null, field, code, args.toList())), null)

        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: return fileError(IssueCode.NOT_JSON)

        if (root.string("formato") != FORMAT) return fileError(IssueCode.BAD_FORMAT, "formato", FORMAT)
        val version = (root["version"] as? JsonPrimitive)?.intOrNull
        if (version != VERSION) return fileError(IssueCode.UNSUPPORTED_VERSION, "version", version?.toString() ?: "?")

        val zone = root.string("zona_horaria")?.let {
            try {
                ZoneId.of(it)
            } catch (_: DateTimeException) {
                return fileError(IssueCode.INVALID_ZONE, "zona_horaria", it)
            }
        } ?: ManualRecordValidator.DEFAULT_ZONE

        val fileLocation = when (val u = root["ubicacion"]) {
            null, is JsonNull -> null
            is JsonObject -> {
                val lat = (u["lat"] as? JsonPrimitive)?.doubleOrNull
                val lon = (u["lon"] as? JsonPrimitive)?.doubleOrNull
                if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                    return fileError(IssueCode.INVALID_LOCATION, "ubicacion")
                }
                GeoPoint(lat, lon)
            }
            else -> return fileError(IssueCode.INVALID_LOCATION, "ubicacion")
        }
        val location = fileLocation ?: appLocation

        val items = try {
            root["registros"]?.jsonArray
        } catch (_: IllegalArgumentException) {
            null
        }
        if (items.isNullOrEmpty()) return fileError(IssueCode.NO_RECORDS, "registros")
        if (items.size > MAX_RECORDS) return fileError(IssueCode.TOO_MANY_RECORDS, "registros", MAX_RECORDS.toString())

        val origin = ManualOrigin.Imported(fileName)
        val issues = mutableListOf<ManualIssue>()
        val records = linkedMapOf<Pair<RecordPeriod, String>, ManualRecord>()
        var farWarned = false
        items.forEachIndexed { index, element ->
            val number = index + 1
            val obj = element as? JsonObject
            if (obj == null) {
                issues += ManualIssue(number, null, IssueCode.RECORD_NOT_OBJECT)
                return@forEachIndexed
            }
            val result = ManualRecordValidator.validate(
                raw = obj.toRawRecord(),
                recordNumber = number,
                location = location,
                appLocation = appLocation,
                origin = origin,
                now = now,
                zone = zone,
            )
            // El aviso de ubicación lejana es del archivo, no de cada registro.
            result.issues.forEach { issue ->
                if (issue.code == IssueCode.FAR_FROM_LOCATION) {
                    if (!farWarned) issues += issue.copy(recordNumber = null)
                    farWarned = true
                } else {
                    issues += issue
                }
            }
            result.record?.let { record ->
                val key = record.period to record.source
                if (key in records) issues += ManualIssue(number, null, IssueCode.DUPLICATE_IN_FILE)
                records[key] = record
            }
        }
        val hasErrors = issues.any { it.code.isError }
        return ImportPreview(
            records = if (hasErrors) emptyList() else records.values.toList(),
            issues = issues,
            fileLocation = fileLocation,
        )
    }

    private fun JsonObject.toRawRecord(): RawRecord = RawRecord(
        time = string("hora"),
        date = string("fecha"),
        source = string("fuente"),
        notes = string("notas"),
        values = filterKeys { it !in RESERVED_KEYS }.mapValues { (_, v) ->
            when {
                v is JsonNull -> null
                v is JsonPrimitive && v.isString -> RawValue.Text(v.content)
                v is JsonPrimitive -> v.doubleOrNull?.let { RawValue.Number(it) } ?: RawValue.Text(v.content)
                else -> RawValue.Text(v.toString())
            }
        },
    )

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull

    private val prettyJson = Json { prettyPrint = true }
    private val LOCAL_HOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    /**
     * Copia de seguridad de tus registros en este mismo formato, para volver a importarlos (por
     * ejemplo, tras reinstalar la app). Al reimportarlos quedan marcados como importados.
     */
    fun export(records: List<ManualRecord>, location: FishingLocation, zone: ZoneId = ZoneId.of("Europe/Madrid")): String {
        val root = buildJsonObject {
            put("formato", FORMAT)
            put("version", VERSION)
            putJsonObject("ubicacion") {
                put("nombre", location.name)
                put("lat", location.point.latitude)
                put("lon", location.point.longitude)
            }
            put("zona_horaria", zone.id)
            putJsonArray("registros") {
                records.sortedBy { record ->
                    when (val period = record.period) {
                        is RecordPeriod.At -> period.time
                        is RecordPeriod.Day -> period.date.atStartOfDay(zone).toInstant()
                    }
                }.forEach { record ->
                    addJsonObject {
                        when (val period = record.period) {
                            is RecordPeriod.At -> put("hora", LOCAL_HOUR.format(period.time.atZone(zone)))
                            is RecordPeriod.Day -> put("fecha", period.date.toString())
                        }
                        put("fuente", record.source)
                        record.values.toSortedMap().forEach { (field, value) ->
                            if (field.integer) put(field.key, value.toLong()) else put(field.key, value)
                        }
                        record.notes?.let { put("notas", it) }
                    }
                }
            }
        }
        return prettyJson.encodeToString(JsonObject.serializer(), root)
    }
}
