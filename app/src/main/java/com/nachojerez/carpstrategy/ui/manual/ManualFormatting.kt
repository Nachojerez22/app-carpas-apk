package com.nachojerez.carpstrategy.ui.manual

import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.manual.RecordPeriod
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d/MM/yyyy", Formatting.SPANISH)

fun RecordPeriod.display(): String = when (this) {
    is RecordPeriod.At -> Formatting.hour(time)
    is RecordPeriod.Day -> DAY_FORMAT.format(date)
}

/** Valor con los decimales justos (enteros sin decimales). */
fun ManualField.format(value: Double): String = Formatting.number(value, if (integer) 0 else 1)

/** Filtro del control segmentado de la pestaña Datos. */
enum class RecordFilter { ALL, HOURLY, DAILY }

/** Día local al que pertenece el registro (los diarios, su fecha). */
fun RecordPeriod.localDate(zone: ZoneId = Formatting.MADRID): LocalDate = when (this) {
    is RecordPeriod.At -> time.atZone(zone).toLocalDate()
    is RecordPeriod.Day -> date
}

/**
 * Registros filtrados y agrupados por día, del más reciente al más antiguo. Dentro de cada día,
 * primero el registro diario y después los horarios de más reciente a más antiguo.
 */
fun groupRecordsByDay(
    records: List<ManualRecord>,
    filter: RecordFilter,
    zone: ZoneId = Formatting.MADRID,
): List<Pair<LocalDate, List<ManualRecord>>> = records
    .filter {
        when (filter) {
            RecordFilter.ALL -> true
            RecordFilter.HOURLY -> it.period is RecordPeriod.At
            RecordFilter.DAILY -> it.period is RecordPeriod.Day
        }
    }
    .groupBy { it.period.localDate(zone) }
    .toSortedMap(Comparator.reverseOrder())
    .map { (day, list) ->
        day to list.sortedWith(
            compareBy<ManualRecord> { it.period is RecordPeriod.At }
                .thenByDescending { (it.period as? RecordPeriod.At)?.time },
        )
    }

/** Dirección en grados para cada uno de los 8 rumbos del selector (N = 0°, NE = 45°…). */
val COMPASS_POINTS_DEG: List<Double> = List(8) { it * 45.0 }
