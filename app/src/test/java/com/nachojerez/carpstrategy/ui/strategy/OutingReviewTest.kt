package com.nachojerez.carpstrategy.ui.strategy

import com.nachojerez.carpstrategy.data.rules.RulesJson
import com.nachojerez.carpstrategy.domain.derived.WaterTempSource
import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.SpotStructure
import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.ManualOrigin
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.manual.RecordPeriod
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.rules.FavorabilityBand
import com.nachojerez.carpstrategy.domain.usecase.RawWeather
import java.io.File
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OutingReviewTest {
    private val now = Instant.parse("2026-10-03T10:00:00Z")
    private val point = GeoPoint(38.35, -6.70)
    private val rules = RulesJson.parse(File("src/main/assets/rules.json").readText())
    private val result = PrepResult(now, forecastOk = true, plan = PrepPlan.READY)

    private fun state(): StrategyUiState {
        val water = ManualRecord(
            period = RecordPeriod.At(now.minus(Duration.ofHours(1))), location = point, source = "termómetro",
            origin = ManualOrigin.Typed, values = mapOf(ManualField.WATER_TEMP_SURFACE to 18.0), createdAt = now,
        )
        val base = buildStrategyState(RawWeather(null, emptyList(), null, manualRecords = listOf(water)), rules, now, point)
        val spots = listOf(Spot(1, "Punta", structure = SpotStructure.SHALLOW_EDGE))
        val gear = listOf(GearItem("1", GearCategory.BAIT, "Chufa", baitType = BaitType.TIGERNUT))
        return base.copy(spots = spots, gear = gear, advice = adviceFor(base.derived, now, emptyList(), gear, spots, emptyList()))
    }

    private fun prepared(state: StrategyUiState) = preparedOutingOf(state, dayPlanFor(state, PlanUiState()), emptyList(), result, now)

    @Test
    fun `si nada ha cambiado, el plan sigue valiendo`() {
        val state = state()
        val key = planKey(state)
        val review = reviewOuting(prepared(state), state, key, key)
        assertTrue(review.nothingChanged)
        assertFalse(review.blockedNow)
    }

    @Test
    fun `avisa de la escala, el agua y el viento cuando cambian lo bastante`() {
        val state = state()
        val p = prepared(state)
        val c = p.conditions!!
        val before = c.copy(
            band = FavorabilityBand.entries.first { it != c.band },
            waterC = 18.0 + ReviewThresholds.WATER_C + 0.5,
            waterSource = WaterTempSource.ESTIMATED,
            windKmh = (state.derived?.wind24h?.meanSpeedKmh ?: 0.0) + ReviewThresholds.WIND_KMH + 1,
        )
        val review = reviewOuting(p.copy(conditions = before), state, null, planKey(state))
        assertEquals(before.band, review.band?.before)
        assertEquals(state.result?.band, review.band?.after)
        assertEquals(18.0, review.waterC?.after)
        assertTrue(review.waterMeasuredNow)
        // Sin viento en la serie de ahora no hay con qué comparar: no se inventa.
        if (state.derived?.wind24h?.meanSpeedKmh == null) assertNull(review.windKmh) else assertTrue(review.windKmh != null)
        assertFalse(review.nothingChanged)
    }

    @Test
    fun `por debajo del umbral no se avisa`() {
        val state = state()
        val p = prepared(state)
        val review = reviewOuting(p.copy(conditions = p.conditions!!.copy(waterC = 18.0 + ReviewThresholds.WATER_C / 2)), state, null, null)
        assertNull(review.waterC)
    }

    @Test
    fun `si cambias lo elegido, se avisa en vez de descartar el plan en silencio`() {
        val state = state()
        val key = planKey(state)!!
        val other = key.split("|").toMutableList().also { it[2] = "99" }.joinToString("|")
        val review = reviewOuting(prepared(state), state, other, key)
        assertTrue(review.choicesChanged)
        assertFalse(review.nothingChanged)
        // Otra franja del mismo día no es cambiar lo elegido.
        val otherPart = key.split("|").toMutableList().also { it[4] = "OTRA" }.joinToString("|")
        assertFalse(reviewOuting(prepared(state), state, otherPart, key).choicesChanged)
    }
}
