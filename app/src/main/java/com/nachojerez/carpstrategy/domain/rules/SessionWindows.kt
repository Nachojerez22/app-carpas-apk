package com.nachojerez.carpstrategy.domain.rules

import com.nachojerez.carpstrategy.domain.derived.LegalWindow
import com.nachojerez.carpstrategy.domain.derived.SeasonByWater
import com.nachojerez.carpstrategy.domain.derived.SunTimes
import java.time.Duration
import java.time.Instant

enum class WindowKind { DAWN, MIDDAY, AFTERNOON_DUSK, DUSK }

data class TimeWindow(val start: Instant, val end: Instant, val kind: WindowKind, val evidence: Evidence)

/**
 * Ventanas horarias sugeridas según la estación del agua (§5.2 y fila "Cuándo" de §5.8).
 * Siempre recortadas al horario legal: nunca se proponen horas nocturnas.
 */
object SessionWindows {
    private val EDGE = Duration.ofHours(2)
    private val MIDDAY_HALF = Duration.ofMinutes(150)

    fun suggest(legal: LegalWindow?, sun: SunTimes, season: SeasonByWater?, waterTrend3dC: Double?): List<TimeWindow> {
        legal ?: return emptyList()
        val sunrise = sun.sunrise ?: return emptyList()
        val sunset = sun.sunset ?: return emptyList()
        val noon = sunrise.plus(Duration.between(sunrise, sunset).dividedBy(2))

        fun window(start: Instant, end: Instant, kind: WindowKind, evidence: Evidence): TimeWindow? {
            val s = maxOf(start, legal.start)
            val e = minOf(end, legal.end)
            return if (s.isBefore(e)) TimeWindow(s, e, kind, evidence) else null
        }
        val dawn = window(legal.start, sunrise.plus(EDGE), WindowKind.DAWN, Evidence.YELLOW)
        val dusk = window(sunset.minus(EDGE), legal.end, WindowKind.DUSK, Evidence.YELLOW)
        val midday = window(noon.minus(MIDDAY_HALF), noon.plus(MIDDAY_HALF), WindowKind.MIDDAY, Evidence.YELLOW)

        return when (season) {
            SeasonByWater.WINTER -> listOfNotNull(midday?.copy(evidence = Evidence.PURPLE))
            SeasonByWater.SPRING -> listOfNotNull(
                dawn.takeIf { (waterTrend3dC ?: 0.0) > 0.0 },
                window(noon.plus(Duration.ofHours(1)), legal.end, WindowKind.AFTERNOON_DUSK, Evidence.YELLOW),
            )
            SeasonByWater.SUMMER -> listOfNotNull(dawn, dusk)
            SeasonByWater.AUTUMN -> listOfNotNull(midday, dusk)
            null -> listOfNotNull(dawn, dusk)
        }
    }
}
