package com.nachojerez.carpstrategy.domain.rules

import com.nachojerez.carpstrategy.domain.derived.DerivedConditions
import com.nachojerez.carpstrategy.domain.derived.SeasonByWater
import com.nachojerez.carpstrategy.domain.derived.WaterTempSource
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** Traduce los parámetros derivados al contexto de las reglas. Función pura. */
object RuleContextBuilder {
    private val COMPASS = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")

    fun build(
        derived: DerivedConditions,
        divergentWindHours: Int,
        now: Instant,
        zone: ZoneId,
        holiday: Boolean = false,
    ): RuleContext {
        val numbers = mutableMapOf<RuleParameter, Double>()
        val booleans = mutableMapOf<RuleParameter, Boolean>()
        val texts = mutableMapOf<RuleParameter, String>()
        val placeholders = mutableMapOf<String, String>()

        derived.water?.let { w ->
            numbers[RuleParameter.WATER_TEMP] = w.valueC
            booleans[RuleParameter.WATER_MEASURED] = w.source == WaterTempSource.MEASURED
            w.trend3dC?.let { numbers[RuleParameter.WATER_TREND_3D] = it }
        }
        derived.season?.let {
            texts[RuleParameter.SEASON] = when (it.season) {
                SeasonByWater.WINTER -> "invierno"
                SeasonByWater.SPRING -> "primavera"
                SeasonByWater.SUMMER -> "verano"
                SeasonByWater.AUTUMN -> "otono"
            }
        }
        derived.airTrend?.let {
            numbers[RuleParameter.AIR_TEMP_24H] = it.last24hMeanC
            numbers[RuleParameter.AIR_TREND] = it.deltaC
        }
        numbers[RuleParameter.HOT_DAYS] = derived.streaks.hotDays.toDouble()
        numbers[RuleParameter.COLD_DAYS] = derived.streaks.coldDays.toDouble()

        derived.wind24h?.let { w ->
            numbers[RuleParameter.WIND_24H] = w.meanSpeedKmh
            numbers[RuleParameter.WIND_PERSISTENCE_24H] = w.persistence
            w.maxGustKmh?.let { numbers[RuleParameter.GUST_MAX_24H] = it }
            w.dominantDirectionDeg?.let { from ->
                placeholders["viento_desde"] = compass(from)
                placeholders["viento_hacia"] = compass(from + 180)
            }
        }
        numbers[RuleParameter.UNCERTAIN_WIND_HOURS] = divergentWindHours.toDouble()

        numbers[RuleParameter.RAIN_24H] = derived.rain.last24hMm
        numbers[RuleParameter.RAIN_72H] = derived.rain.last72hMm
        booleans[RuleParameter.RUNOFF] = derived.rain.runoffLikely

        derived.reservoir?.let { r ->
            r.delta7dHm3?.let { numbers[RuleParameter.LEVEL_DELTA_7D] = it }
            r.percent?.let { numbers[RuleParameter.LEVEL_PERCENT] = it }
        }
        derived.pressure.delta24hHpa?.let { numbers[RuleParameter.PRESSURE_DELTA_24H] = it }
        derived.cloudCover24hPct?.let { numbers[RuleParameter.CLOUD_COVER] = it }
        numbers[RuleParameter.MOON_ILLUMINATION] = derived.moon.illumination

        val legal = derived.legalToday
        booleans[RuleParameter.LEGAL_WINDOW_AVAILABLE] = legal != null
        booleans[RuleParameter.IN_LEGAL_HOURS] = legal != null && now in legal

        val local = now.atZone(zone)
        booleans[RuleParameter.WEEKEND] = holiday || local.dayOfWeek == DayOfWeek.SATURDAY || local.dayOfWeek == DayOfWeek.SUNDAY
        numbers[RuleParameter.MONTH] = local.monthValue.toDouble()

        return RuleContext(numbers, booleans, texts, placeholders)
    }

    fun compass(degrees: Double): String {
        val normalized = ((degrees % 360.0) + 360.0) % 360.0
        return COMPASS[((normalized + 22.5) / 45.0).toInt() % 8]
    }
}
