package com.nachojerez.carpstrategy.ui.diary

import com.nachojerez.carpstrategy.domain.journal.Catch
import com.nachojerez.carpstrategy.domain.journal.FishingZone
import com.nachojerez.carpstrategy.domain.journal.Fulfilled
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.manual.RawRecord
import com.nachojerez.carpstrategy.domain.manual.RawValue
import com.nachojerez.carpstrategy.domain.manual.RecordPeriod
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.manual.format
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Campos de texto del formulario que pueden estar mal escritos. */
enum class FormField { DATE, START, END, DEPTH, RODS, ROD_HOURS, GROUNDBAIT, OTHER_ANGLERS, CATCH_TIME, CATCH_WEIGHT, CATCH_ROD }

/** Una captura tal como se teclea. */
data class CatchInput(
    val time: String = "",
    val weight: String = "",
    val rod: String = "",
    val species: String = "",
)

/**
 * Formulario de sesión: todo como texto, como lo teclea el usuario. La sesión se reconstruye
 * con [toSession]; la valoración previa y el contexto no se editan (se conservan de [base]).
 */
data class SessionForm(
    val id: Long = 0,
    val date: String,
    val startTime: String,
    /** Vacío = sesión en curso. */
    val endTime: String = "",
    val zone: FishingZone? = null,
    val zoneDetail: String = "",
    val depth: String = "",
    val rods: String = "2",
    /** Vacío = calcular cañas × duración. */
    val rodHours: String = "",
    val bait: String = "",
    val rig: String = "",
    val groundbait: String = "",
    val otherAnglers: String = "",
    val bites: Int = 0,
    val losses: Int = 0,
    val catches: List<CatchInput> = emptyList(),
    val blank: Boolean? = null,
    val fulfilled: Fulfilled? = null,
    val notes: String = "",
    /** Mediciones que se guardan también en Datos (calibran la estimación del agua). */
    val waterSurface: String = "",
    val turbidity: Int? = null,
    val reservoirPercent: String = "",
) {
    /**
     * Registro manual con las mediciones de la sesión a la hora de inicio, o null si no hay
     * ninguna. Se valida con el mismo validador que el formulario de Datos.
     */
    fun measurementRecord(): RawRecord? {
        val values = buildMap<String, RawValue> {
            if (waterSurface.isNotBlank()) put(ManualField.WATER_TEMP_SURFACE.key, RawValue.Text(waterSurface.trim()))
            turbidity?.let { put(ManualField.TURBIDITY.key, RawValue.Number(it.toDouble())) }
            if (reservoirPercent.isNotBlank()) put(ManualField.RESERVOIR_PERCENT.key, RawValue.Text(reservoirPercent.trim()))
        }
        if (values.isEmpty()) return null
        val start = parseTime(startTime)?.let { TIME.format(it) } ?: startTime.trim()
        return RawRecord(time = "${date.trim()} $start", source = MEASUREMENT_SOURCE, values = values, notes = null)
    }

    /** Rellena las mediciones con el registro de Datos que guardó esta sesión, si existe. */
    fun withMeasurementsFrom(records: List<ManualRecord>, start: Instant): SessionForm {
        val record = records.firstOrNull { it.source == MEASUREMENT_SOURCE && (it.period as? RecordPeriod.At)?.time == start } ?: return this
        return copy(
            waterSurface = record.values[ManualField.WATER_TEMP_SURFACE]?.let { ManualField.WATER_TEMP_SURFACE.format(it) }.orEmpty(),
            turbidity = record.values[ManualField.TURBIDITY]?.toInt(),
            reservoirPercent = record.values[ManualField.RESERVOIR_PERCENT]?.let { ManualField.RESERVOIR_PERCENT.format(it) }.orEmpty(),
        )
    }

    data class Result(val session: Session?, val fieldErrors: Set<FormField>)

    /** Convierte el formulario en sesión. Si algún campo no se entiende, [Result.session] es null. */
    fun toSession(base: Session?, location: GeoPoint, now: Instant, zoneId: ZoneId = Formatting.MADRID): Result {
        val errors = mutableSetOf<FormField>()
        val day = runCatching { LocalDate.parse(date.trim()) }.getOrNull().also { if (it == null) errors += FormField.DATE }
        fun time(text: String, field: FormField): LocalTime? =
            parseTime(text).also { if (it == null) errors += field }
        val startLocal = time(startTime, FormField.START)
        val endLocal = if (endTime.isBlank()) null else time(endTime, FormField.END)
        fun number(text: String, field: FormField): Double? {
            if (text.isBlank()) return null
            return text.trim().replace(',', '.').toDoubleOrNull().also { if (it == null) errors += field }
        }
        fun integer(text: String, field: FormField): Int? {
            if (text.isBlank()) return null
            return text.trim().toIntOrNull().also { if (it == null) errors += field }
        }
        val depthM = number(depth, FormField.DEPTH)
        val rodCount = integer(rods, FormField.RODS).also { if (rods.isBlank()) errors += FormField.RODS }
        val rodHoursValue = number(rodHours, FormField.ROD_HOURS)
        val groundbaitKg = number(groundbait, FormField.GROUNDBAIT)
        val anglers = integer(otherAnglers, FormField.OTHER_ANGLERS)
        val parsedCatches = catches.map { c ->
            Triple(
                if (c.time.isBlank()) null else time(c.time, FormField.CATCH_TIME),
                number(c.weight, FormField.CATCH_WEIGHT),
                integer(c.rod, FormField.CATCH_ROD),
            ) to c.species.trim()
        }
        if (errors.isNotEmpty() || day == null || startLocal == null || rodCount == null) return Result(null, errors)

        val start = day.atTime(startLocal).atZone(zoneId).toInstant()
        val end = endLocal?.let { day.atTime(it).atZone(zoneId).toInstant() }
        val session = Session(
            id = id,
            start = start,
            end = end,
            location = base?.location ?: location,
            zone = zone,
            zoneDetail = zoneDetail.trim(),
            depthM = depthM,
            rods = rodCount,
            rodHoursOverride = rodHoursValue,
            bait = bait.trim(),
            rig = rig.trim(),
            groundbaitKg = groundbaitKg,
            otherAnglers = anglers,
            bites = bites,
            losses = losses,
            catches = parsedCatches.map { (values, species) ->
                Catch(
                    time = values.first?.let { day.atTime(it).atZone(zoneId).toInstant() },
                    weightKg = values.second,
                    rod = values.third,
                    species = species.ifBlank { null },
                )
            },
            // Con capturas no hay bolo, aunque se hubiera marcado antes de anotarlas.
            blank = if (catches.isNotEmpty()) false else blank,
            prediction = base?.prediction,
            context = base?.context,
            fulfilled = fulfilled,
            notes = notes.trim(),
            createdAt = base?.createdAt ?: now,
        )
        return Result(session, emptySet())
    }

    companion object {
        /** Fuente de los registros de Datos creados desde el diario. */
        const val MEASUREMENT_SOURCE = "Diario de sesiones"

        private val DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /** Admite "7:20", "07:20" y "07.20". */
        fun parseTime(text: String): LocalTime? {
            val parts = text.trim().replace('.', ':').split(':')
            if (parts.size != 2) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            if (h !in 0..23 || m !in 0..59) return null
            return LocalTime.of(h, m)
        }

        /** Sesión nueva que empieza ahora (en curso). */
        fun startingNow(now: Instant, zoneId: ZoneId = Formatting.MADRID): SessionForm {
            val local = now.atZone(zoneId)
            return SessionForm(date = DATE.format(local), startTime = TIME.format(local))
        }

        fun from(session: Session, zoneId: ZoneId = Formatting.MADRID): SessionForm {
            fun clock(t: Instant) = TIME.format(t.atZone(zoneId))
            fun num(v: Double?) = when {
                v == null -> ""
                v % 1.0 == 0.0 -> Formatting.number(v, 0)
                else -> Formatting.number(v, 2).trimEnd('0').trimEnd(',')
            }
            return SessionForm(
                id = session.id,
                date = DATE.format(session.start.atZone(zoneId)),
                startTime = clock(session.start),
                endTime = session.end?.let(::clock).orEmpty(),
                zone = session.zone,
                zoneDetail = session.zoneDetail,
                depth = num(session.depthM),
                rods = "${session.rods}",
                rodHours = num(session.rodHoursOverride),
                bait = session.bait,
                rig = session.rig,
                groundbait = num(session.groundbaitKg),
                otherAnglers = session.otherAnglers?.toString().orEmpty(),
                bites = session.bites,
                losses = session.losses,
                catches = session.catches.map { c ->
                    CatchInput(c.time?.let(::clock).orEmpty(), num(c.weightKg), c.rod?.toString().orEmpty(), c.species.orEmpty())
                },
                blank = session.blank,
                fulfilled = session.fulfilled,
                notes = session.notes,
            )
        }
    }
}
