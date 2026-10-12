package com.nachojerez.carpstrategy.domain.advice

import com.nachojerez.carpstrategy.domain.derived.SunTimes
import java.time.Duration
import java.time.Instant

/**
 * Momento del día según el sol (§5.8 «Cuándo»): amanecer y anochecer son las franjas de más
 * actividad en verano; en invierno, las horas centrales. Fuera de la luz del día: NIGHT.
 */
enum class DayPart { DAWN, MORNING, MIDDAY, AFTERNOON, DUSK, NIGHT }

object DayParts {
    /** 🟣 Amanecer: de 1 h antes a 1 h 30 min después de la salida del sol. */
    val DAWN_BEFORE: Duration = Duration.ofHours(1)
    val DAWN_AFTER: Duration = Duration.ofMinutes(90)

    /** 🟣 Anochecer: de 1 h 30 min antes a 1 h después de la puesta. */
    val DUSK_BEFORE: Duration = Duration.ofMinutes(90)
    val DUSK_AFTER: Duration = Duration.ofHours(1)

    /** 🟣 Horas centrales: ± 2 h alrededor del mediodía solar. */
    val MIDDAY_HALF: Duration = Duration.ofHours(2)

    fun of(time: Instant, sun: SunTimes): DayPart {
        val rise = sun.sunrise ?: return DayPart.MIDDAY
        val set = sun.sunset ?: return DayPart.MIDDAY
        val noon = rise.plus(Duration.between(rise, set).dividedBy(2))
        return when {
            time.isBefore(rise.minus(DAWN_BEFORE)) || time.isAfter(set.plus(DUSK_AFTER)) -> DayPart.NIGHT
            time.isBefore(rise.plus(DAWN_AFTER)) -> DayPart.DAWN
            !time.isBefore(set.minus(DUSK_BEFORE)) -> DayPart.DUSK
            !time.isBefore(noon.minus(MIDDAY_HALF)) && !time.isAfter(noon.plus(MIDDAY_HALF)) -> DayPart.MIDDAY
            time.isBefore(noon) -> DayPart.MORNING
            else -> DayPart.AFTERNOON
        }
    }
}
