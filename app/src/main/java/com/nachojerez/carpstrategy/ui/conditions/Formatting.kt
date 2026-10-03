package com.nachojerez.carpstrategy.ui.conditions

import com.nachojerez.carpstrategy.domain.derived.DirectionStats
import com.nachojerez.carpstrategy.domain.derived.VariableStats
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formato español (coma decimal, hora de Madrid). Funciones puras. */
object Formatting {
    val SPANISH: Locale = Locale.forLanguageTag("es-ES")
    val MADRID: ZoneId = ZoneId.of("Europe/Madrid")
    private const val MISSING = "—"
    private val HOUR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d HH:mm", SPANISH)
    private val COMPASS = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")

    private fun decimals(digits: Int) = DecimalFormat(
        if (digits == 0) "0" else "0." + "0".repeat(digits),
        DecimalFormatSymbols(SPANISH),
    )

    fun number(value: Double?, digits: Int = 1): String = value?.let { decimals(digits).format(it) } ?: MISSING

    fun withSpread(stats: VariableStats?, digits: Int = 1): String = when {
        stats == null -> MISSING
        stats.count < 2 -> number(stats.mean, digits)
        else -> "${number(stats.mean, digits)} ±${number(stats.spread, digits)}"
    }

    /** Rumbo de procedencia del viento en 8 puntos (O = oeste). */
    fun compass(degrees: Double?): String {
        if (degrees == null) return MISSING
        val normalized = ((degrees % 360.0) + 360.0) % 360.0
        return COMPASS[((normalized + 22.5) / 45.0).toInt() % 8]
    }

    fun direction(stats: DirectionStats?): String = compass(stats?.meanDeg)

    fun hour(time: Instant, zone: ZoneId = MADRID): String = HOUR_FORMAT.format(time.atZone(zone))

    /** Edad en la unidad más útil. Devuelve (unidad, valor) para elegir el texto traducible. */
    fun ageParts(age: Duration): Pair<AgeUnit, Long> = when {
        age < Duration.ofMinutes(1) -> AgeUnit.NOW to 0
        age < Duration.ofHours(1) -> AgeUnit.MINUTES to age.toMinutes()
        age < Duration.ofDays(2) -> AgeUnit.HOURS to age.toHours()
        else -> AgeUnit.DAYS to age.toDays()
    }

    enum class AgeUnit { NOW, MINUTES, HOURS, DAYS }
}
