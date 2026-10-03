package com.nachojerez.carpstrategy.domain.manual

import com.nachojerez.carpstrategy.domain.derived.Geo
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/** Problema detectado en un registro. La UI lo traduce a texto con [code] y [args]. */
data class ManualIssue(
    /** Posición del registro (1, 2, …) en el archivo; null si afecta a todo el archivo. */
    val recordNumber: Int?,
    /** Campo JSON afectado, si lo hay. */
    val field: String?,
    val code: IssueCode,
    val args: List<String> = emptyList(),
)

enum class IssueCode(val isError: Boolean) {
    // Errores de archivo
    NOT_JSON(true),
    BAD_FORMAT(true),
    UNSUPPORTED_VERSION(true),
    INVALID_ZONE(true),
    INVALID_LOCATION(true),
    NO_RECORDS(true),
    TOO_MANY_RECORDS(true),
    RECORD_NOT_OBJECT(true),

    // Errores de registro
    MISSING_SOURCE(true),
    MISSING_PERIOD(true),
    BOTH_TIME_AND_DATE(true),
    INVALID_TIME(true),
    INVALID_DATE(true),
    FUTURE_PERIOD(true),
    NOT_A_NUMBER(true),
    OUT_OF_RANGE(true),
    NOT_AN_INTEGER(true),
    FIELD_NOT_ALLOWED(true),
    NO_VALUES(true),

    // Avisos (no impiden guardar)
    UNKNOWN_FIELD(false),
    FAR_FROM_LOCATION(false),
    BOTTOM_WITHOUT_DEPTH(false),
    DUPLICATE_IN_FILE(false),
}

/** Valor tal como llega del JSON o del formulario. */
sealed interface RawValue {
    data class Number(val value: Double) : RawValue
    data class Text(val value: String) : RawValue
}

/** Registro sin validar (mismo formato para JSON y formulario). */
data class RawRecord(
    val time: String? = null,
    val date: String? = null,
    val source: String? = null,
    val values: Map<String, RawValue?> = emptyMap(),
    val notes: String? = null,
)

data class ValidationResult(
    val record: ManualRecord?,
    val issues: List<ManualIssue>,
) {
    val errors: List<ManualIssue> get() = issues.filter { it.code.isError }
    val warnings: List<ManualIssue> get() = issues.filterNot { it.code.isError }
}

/**
 * Validación común para la entrada manual y la importación JSON. Función pura: el instante
 * actual y la zona horaria se pasan como parámetros.
 */
object ManualRecordValidator {
    /** Margen para relojes desajustados al rechazar horas futuras. */
    val FUTURE_TOLERANCE: Duration = Duration.ofHours(1)

    /** Distancia a partir de la cual se avisa de que el dato es de otro sitio. */
    const val FAR_LOCATION_KM = 10.0

    val DEFAULT_ZONE: ZoneId = ZoneId.of("Europe/Madrid")

    fun validate(
        raw: RawRecord,
        recordNumber: Int?,
        location: GeoPoint,
        appLocation: GeoPoint,
        origin: ManualOrigin,
        now: Instant,
        zone: ZoneId = DEFAULT_ZONE,
        existingId: Long = 0,
    ): ValidationResult {
        val issues = mutableListOf<ManualIssue>()
        fun issue(code: IssueCode, field: String? = null, vararg args: String) {
            issues += ManualIssue(recordNumber, field, code, args.toList())
        }

        val source = raw.source?.trim().orEmpty()
        if (source.isEmpty()) issue(IssueCode.MISSING_SOURCE, "fuente")

        val hasTime = !raw.time.isNullOrBlank()
        val hasDate = !raw.date.isNullOrBlank()
        val period: RecordPeriod? = when {
            hasTime && hasDate -> {
                issue(IssueCode.BOTH_TIME_AND_DATE)
                null
            }
            hasTime -> {
                val text = raw.time.orEmpty()
                val time = parseTime(text, zone)
                when {
                    time == null -> { issue(IssueCode.INVALID_TIME, "hora", text); null }
                    time.isAfter(now.plus(FUTURE_TOLERANCE)) -> { issue(IssueCode.FUTURE_PERIOD, "hora", text); null }
                    else -> RecordPeriod.At(time)
                }
            }
            hasDate -> {
                val text = raw.date.orEmpty()
                val date = parseDate(text)
                when {
                    date == null -> { issue(IssueCode.INVALID_DATE, "fecha", text); null }
                    date.isAfter(now.atZone(zone).toLocalDate()) -> { issue(IssueCode.FUTURE_PERIOD, "fecha", text); null }
                    else -> RecordPeriod.Day(date)
                }
            }
            else -> {
                issue(IssueCode.MISSING_PERIOD)
                null
            }
        }
        val daily = hasDate && !hasTime

        val values = mutableMapOf<ManualField, Double>()
        for ((key, rawValue) in raw.values) {
            val field = ManualField.fromKey(key)
            if (field == null) {
                issue(IssueCode.UNKNOWN_FIELD, key)
                continue
            }
            if (rawValue == null) continue
            val number: Double? = when (rawValue) {
                is RawValue.Number -> rawValue.value
                is RawValue.Text -> {
                    val text = rawValue.value.trim()
                    if (text.isEmpty()) continue // campo vacío del formulario
                    text.replace(',', '.').toDoubleOrNull()
                }
            }
            if (number == null || number.isNaN() || number.isInfinite()) {
                val shown = when (rawValue) {
                    is RawValue.Text -> rawValue.value
                    is RawValue.Number -> rawValue.value.toString()
                }
                issue(IssueCode.NOT_A_NUMBER, key, shown)
                continue
            }
            if (!field.allowedFor(daily)) {
                issue(IssueCode.FIELD_NOT_ALLOWED, key, if (daily) "fecha" else "hora")
                continue
            }
            if (number < field.min || number > field.max) {
                issue(IssueCode.OUT_OF_RANGE, key, format(number), format(field.min), format(field.max))
                continue
            }
            if (field.integer && number != floor(number)) {
                issue(IssueCode.NOT_AN_INTEGER, key, format(number))
                continue
            }
            values[field] = number
        }

        if (values.isEmpty() && issues.none { it.code in VALUE_ERRORS }) issue(IssueCode.NO_VALUES)
        if (ManualField.WATER_TEMP_BOTTOM in values && ManualField.BOTTOM_DEPTH !in values) {
            issue(IssueCode.BOTTOM_WITHOUT_DEPTH, ManualField.WATER_TEMP_BOTTOM.key)
        }
        val distance = Geo.haversineKm(location, appLocation)
        if (distance > FAR_LOCATION_KM) issue(IssueCode.FAR_FROM_LOCATION, "ubicacion", format(distance))

        val hasErrors = issues.any { it.code.isError }
        val record = if (hasErrors || period == null) {
            null
        } else {
            ManualRecord(
                id = existingId,
                period = period,
                location = location,
                source = source,
                origin = origin,
                values = values,
                notes = raw.notes?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = now,
            )
        }
        return ValidationResult(record, issues)
    }

    private val VALUE_ERRORS = setOf(
        IssueCode.NOT_A_NUMBER, IssueCode.OUT_OF_RANGE, IssueCode.NOT_AN_INTEGER, IssueCode.FIELD_NOT_ALLOWED,
    )

    /**
     * Acepta "2026-10-03T08:00", con segundos, con espacio en vez de "T" y con zona explícita
     * ("Z", "+02:00"). Sin zona, se interpreta en [zone] (hora local de Madrid por defecto).
     */
    fun parseTime(text: String, zone: ZoneId): Instant? {
        val t = text.trim().replace(' ', 'T')
        return try {
            OffsetDateTime.parse(t).toInstant()
        } catch (_: DateTimeParseException) {
            try {
                LocalDateTime.parse(t).atZone(zone).toInstant()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text.trim())
    } catch (_: DateTimeParseException) {
        null
    }

    private fun format(value: Double): String =
        if (value == floor(value) && abs(value) < 1e9) value.toLong().toString() else String.format(Locale.ROOT, "%.2f", value)
}
