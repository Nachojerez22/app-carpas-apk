package com.nachojerez.carpstrategy.ui.manual

import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.ManualIssue
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.manual.RawRecord
import com.nachojerez.carpstrategy.domain.manual.RawValue
import com.nachojerez.carpstrategy.domain.manual.RecordPeriod
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Estado del formulario de un registro. Todo como texto, tal como lo teclea el usuario. */
data class EditorState(
    val id: Long = 0,
    val daily: Boolean = false,
    val time: String,
    val date: String,
    val source: String = "",
    val values: Map<ManualField, String> = emptyMap(),
    val notes: String = "",
    val issues: List<ManualIssue> = emptyList(),
) {
    /** Campos que se muestran según el tipo de registro. */
    val fields: List<ManualField> get() = ManualField.entries.filter { it.allowedFor(daily) }

    /** Solo se envían los campos visibles; los del otro tipo se descartan. */
    fun toRawRecord(): RawRecord = RawRecord(
        time = if (daily) null else time,
        date = if (daily) date else null,
        source = source,
        values = fields.associate { it.key to values[it]?.let(RawValue::Text) },
        notes = notes,
    )

    companion object {
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        fun new(now: Instant, zone: ZoneId = Formatting.MADRID): EditorState {
            val local = now.atZone(zone)
            return EditorState(time = TIME_FORMAT.format(local), date = local.toLocalDate().toString())
        }

        fun from(record: ManualRecord, now: Instant, zone: ZoneId = Formatting.MADRID): EditorState {
            val base = new(now, zone)
            return when (val period = record.period) {
                is RecordPeriod.At -> base.copy(daily = false, time = TIME_FORMAT.format(period.time.atZone(zone)))
                is RecordPeriod.Day -> base.copy(daily = true, date = period.date.toString())
            }.copy(
                id = record.id,
                source = record.source,
                values = record.values.mapValues { (field, value) -> field.format(value) },
                notes = record.notes.orEmpty(),
            )
        }
    }
}
