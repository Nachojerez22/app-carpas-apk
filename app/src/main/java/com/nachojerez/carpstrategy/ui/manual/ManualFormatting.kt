package com.nachojerez.carpstrategy.ui.manual

import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.RecordPeriod
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import java.time.format.DateTimeFormatter

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d/MM/yyyy", Formatting.SPANISH)

fun RecordPeriod.display(): String = when (this) {
    is RecordPeriod.At -> Formatting.hour(time)
    is RecordPeriod.Day -> DAY_FORMAT.format(date)
}

/** Valor con los decimales justos (enteros sin decimales). */
fun ManualField.format(value: Double): String = Formatting.number(value, if (integer) 0 else 1)
