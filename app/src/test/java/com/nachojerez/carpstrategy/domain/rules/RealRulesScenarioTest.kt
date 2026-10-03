package com.nachojerez.carpstrategy.domain.rules

import com.nachojerez.carpstrategy.data.rules.RulesJson
import com.nachojerez.carpstrategy.domain.derived.AirTemperatureTrend
import com.nachojerez.carpstrategy.domain.derived.DerivedConditions
import com.nachojerez.carpstrategy.domain.derived.LegalWindow
import com.nachojerez.carpstrategy.domain.derived.LevelReading
import com.nachojerez.carpstrategy.domain.derived.MoonCalculator
import com.nachojerez.carpstrategy.domain.derived.PressureTrend
import com.nachojerez.carpstrategy.domain.derived.Rainfall
import com.nachojerez.carpstrategy.domain.derived.ReservoirLevel
import com.nachojerez.carpstrategy.domain.derived.SeasonByWater
import com.nachojerez.carpstrategy.domain.derived.SeasonClassification
import com.nachojerez.carpstrategy.domain.derived.SolarCalculator
import com.nachojerez.carpstrategy.domain.derived.TemperatureStreaks
import com.nachojerez.carpstrategy.domain.derived.WaterTempSource
import com.nachojerez.carpstrategy.domain.derived.WaterTemperature
import com.nachojerez.carpstrategy.domain.derived.WindSummary
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Escenarios con el rules.json real: comprueban que el motor sigue CONOCIMIENTO.md. */
class RealRulesScenarioTest {
    private val rules = (RulesJson.parse(File("src/main/assets/rules.json").readText()) as RuleLoadResult.Loaded).ruleSet
    private val madrid = ZoneId.of("Europe/Madrid")
    private val now = Instant.parse("2026-10-03T10:00:00Z") // sábado
    private val sun = SolarCalculator.sunTimes(LocalDate.parse("2026-10-03"), GeoPoint(38.35, -6.70))

    private fun derived(
        waterC: Double,
        measured: Boolean,
        season: SeasonByWater,
        trend: Double,
        levelDelta: Double? = null,
        runoff: Boolean = false,
        airDelta: Double = 0.0,
    ) = DerivedConditions(
        now = now,
        temperatureBias = null,
        airTrend = AirTemperatureTrend(20.0 + airDelta, 20.0),
        water = WaterTemperature(waterC, if (measured) WaterTempSource.MEASURED else WaterTempSource.ESTIMATED, trend3dC = trend),
        season = SeasonClassification(season, false),
        pressure = PressureTrend(-1.0, -4.0, -6.0, 2.0),
        wind24h = WindSummary(Duration.ofHours(24), 225.0, 14.0, 0.8, 30.0),
        wind48h = null,
        rain = Rainfall(0.0, if (runoff) 30.0 else 0.0, if (runoff) 15.0 else 0.0, null, runoff),
        streaks = TemperatureStreaks(0, 0),
        reservoir = levelDelta?.let { ReservoirLevel(LevelReading(now, 4.0, null, "x"), 4.0, 57.0, it) },
        sunToday = sun,
        legalToday = LegalWindow.of(sun),
        moon = MoonCalculator.moon(now),
        cloudCover24hPct = 50.0,
    )

    private fun evaluate(d: DerivedConditions) = RuleEngine.evaluate(rules, RuleContextBuilder.build(d, 0, now, madrid), now)

    @Test
    fun `otono con nivel bajando rapido - limita el habitat y se recomienda la primera caida`() {
        val result = evaluate(derived(18.0, measured = true, season = SeasonByWater.AUTUMN, trend = -0.6, levelDelta = -1.0))
        assertFalse(result.blocked)
        val ids = result.activeRules.map { it.rule.id }
        assertTrue("nivel_bajando_rapido" in ids && "estacion_otono" in ids && "presion_pesca_fin_de_semana" in ids)
        assertTrue("agua_estimada" !in ids)
        assertTrue(result.advice.getValue(StrategyField.WHERE).any { "primera caída" in it.text })
        assertEquals(FeedingDemand.MEDIUM, result.demand)
        // Ni la luna ni la presión cambian la valoración.
        assertTrue(result.activeRules.filter { it.rule.evidence == Evidence.RED }.all { it.appliedFactor == 1.0 })
    }

    @Test
    fun `agua fria - la valoracion es baja aunque todo lo demas acompane`() {
        val cold = evaluate(derived(8.0, measured = true, season = SeasonByWater.WINTER, trend = 0.0))
        val warm = evaluate(derived(25.0, measured = true, season = SeasonByWater.SUMMER, trend = 0.0))
        assertTrue(cold.favorability!! < 0.2, "frío ${cold.favorability}")
        assertEquals(FavorabilityBand.VERY_UNFAVORABLE, cold.band)
        assertEquals(RuleLevel.TEMPERATURE, cold.limitingLevel)
        assertTrue(warm.favorability!! > cold.favorability!! * 4)
    }

    @Test
    fun `agua estimada y escorrentia en invierno generan avisos y consejos`() {
        val result = evaluate(derived(8.0, measured = false, season = SeasonByWater.WINTER, trend = 0.0, runoff = true))
        val ids = result.activeRules.map { it.rule.id }
        assertTrue("agua_estimada" in ids)
        assertTrue("escorrentia_invierno" in ids)
        assertTrue(result.advice.getValue(StrategyField.AVOID).any { "entrada de agua fría" in it.text })
    }

    @Test
    fun `viento calido sostenido sustituye los rumbos en el consejo`() {
        val result = evaluate(derived(20.0, measured = true, season = SeasonByWater.AUTUMN, trend = 0.0, airDelta = 1.0))
        val text = result.advice.getValue(StrategyField.WHERE).first { it.ruleId == "viento_calido_sotavento" }.text
        assertTrue("del SO" in text && "hacia el NE" in text, text)
    }
}
